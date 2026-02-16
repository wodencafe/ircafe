package cafe.woden.ircclient.ui.chat.embed;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InstagramLinkPreviewResolver implements LinkPreviewResolver {

  private static final String IG_ORIGIN = "https://www.instagram.com/";

  private static final Pattern SHORTCODE_PATH =
      Pattern.compile("^/(p|reel|tv)/([^/]+)(?:/.*)?$", Pattern.CASE_INSENSITIVE);

  private final int maxHtmlBytes;

  InstagramLinkPreviewResolver(int maxHtmlBytes) {
    this.maxHtmlBytes = maxHtmlBytes;
  }

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) throws Exception {
    if (!InstagramPreviewUtil.isInstagramPostUri(uri)) {
      return null;
    }

    LinkPreview primary = tryFetchAndParse(uri, originalUrl, http);
    if (!isGood(primary)) {
      // Instagram often serves a login/consent interstitial. The public embed endpoints are
      // frequently accessible even when the canonical post HTML is not.
      for (URI embed : embedCandidates(uri)) {
        LinkPreview p = tryFetchAndParse(embed, originalUrl, http);
        if (isGood(p)) {
          primary = p;
          break;
        }
        if (primary == null && p != null) {
          primary = p;
        }
      }
    }

    // For /p/ photo posts, this endpoint often returns the full uncropped media even when
    // OG/display URLs are thumbnail-like. Prefer it when available.
    String mediaUrl = tryLegacyMediaEndpoint(uri, http);
    if (mediaUrl != null && (primary == null || safe(primary.imageUrl()) == null)) {
      if (primary == null) {
        primary = new LinkPreview(originalUrl, "Instagram post", null, "Instagram", mediaUrl, 1);
      } else {
        primary = new LinkPreview(
            primary.url(),
            primary.title(),
            primary.description(),
            primary.siteName(),
            mediaUrl,
            Math.max(1, primary.mediaCount()));
      }
    }

    // If what we got is clearly the interstitial "Instagram / Instagram" shape, don't lock
    // the chain to a useless preview; let other resolvers try.
    if (primary != null && looksHopeless(primary)) {
      return null;
    }

    return primary;
  }

  private LinkPreview tryFetchAndParse(URI fetchUri, String originalUrl, PreviewHttp http) throws Exception {
    if (fetchUri == null) return null;

    Map<String, String> headers = PreviewHttp.headers(
        "User-Agent", PreviewHttp.BROWSER_USER_AGENT,
        "Accept-Language", PreviewHttp.ACCEPT_LANGUAGE,
        "Referer", IG_ORIGIN
    );

    var resp = http.getStream(
        fetchUri,
        "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
        headers
    );

    int status = resp.statusCode();
    if (status < 200 || status >= 300) {
      return null;
    }

    String ct = resp.headers().firstValue("content-type").orElse("");
    if (!PreviewHttp.looksLikeHtml(ct)) {
      return null;
    }

    byte[] bytes = PreviewHttp.readUpToBytes(resp.body(), maxHtmlBytes);
    var doc = org.jsoup.Jsoup.parse(new ByteArrayInputStream(bytes), null, originalUrl);
    LinkPreview p = InstagramPreviewUtil.parsePostDocument(doc, originalUrl);

    // If this looks like the login wall and we didn't get media, don't stop the chain.
    if (p != null && !isGood(p) && looksHopeless(p)) {
      return null;
    }

    return p;
  }

  private String tryLegacyMediaEndpoint(URI originalUri, PreviewHttp http) {
    try {
      URI mediaUri = legacyMediaUri(originalUri);
      if (mediaUri == null) return null;

      Map<String, String> headers = PreviewHttp.headers(
          "User-Agent", PreviewHttp.BROWSER_USER_AGENT,
          "Accept-Language", PreviewHttp.ACCEPT_LANGUAGE,
          "Referer", IG_ORIGIN
      );

      var resp = http.getStream(mediaUri, "image/*,*/*;q=0.8", headers);
      int status = resp.statusCode();
      if (status < 200 || status >= 300) return null;

      String ct = resp.headers().firstValue("content-type").orElse("");
      // Consume and close body, we only care if this endpoint resolves to an image.
      byte[] bytes = PreviewHttp.readUpToBytes(resp.body(), 64 * 1024);
      if (bytes.length == 0) return null;
      if (ct != null && ct.toLowerCase(Locale.ROOT).startsWith("image/")) {
        return mediaUri.toString();
      }
      return null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static URI legacyMediaUri(URI original) {
    try {
      if (original == null) return null;
      String path = original.getPath();
      if (path == null) return null;
      Matcher m = SHORTCODE_PATH.matcher(path);
      if (!m.matches()) return null;

      String kind = m.group(1).toLowerCase(Locale.ROOT);
      String code = m.group(2);
      if (!"p".equals(kind) || code == null || code.isBlank()) {
        return null;
      }
      return URI.create("https://www.instagram.com/p/" + code + "/media/?size=l");
    } catch (Exception ignored) {
      return null;
    }
  }

  private static boolean isGood(LinkPreview p) {
    return p != null && p.imageUrl() != null && !p.imageUrl().isBlank();
  }

  private static boolean looksHopeless(LinkPreview p) {
    if (p == null) return true;

    String site = safe(p.siteName());
    String title = safe(p.title());
    String desc = safe(p.description());

    boolean siteIsIg = site != null && site.equalsIgnoreCase("instagram");
    boolean titleIsIg = title != null && title.equalsIgnoreCase("instagram");

    if (siteIsIg && titleIsIg) {
      return true;
    }

    if (siteIsIg && desc != null) {
      String d = desc.toLowerCase(Locale.ROOT);
      if (d.contains("log in") || d.contains("sign up") || d.contains("create an account")) {
        return true;
      }
    }

    return false;
  }

  private static String safe(String s) {
    if (s == null) return null;
    String t = s.strip();
    return t.isEmpty() ? null : t;
  }

  private static List<URI> embedCandidates(URI original) {
    try {
      if (original == null) return List.of();
      String path = original.getPath();
      if (path == null) return List.of();

      Matcher m = SHORTCODE_PATH.matcher(path);
      if (!m.matches()) return List.of();

      String kind = m.group(1).toLowerCase(Locale.ROOT);
      String code = m.group(2);
      if (code == null || code.isBlank()) return List.of();

      String base = "https://www.instagram.com";

      Set<String> paths = new LinkedHashSet<>();
      paths.add("/" + kind + "/" + code + "/embed/captioned/");
      paths.add("/" + kind + "/" + code + "/embed/");

      if (!"p".equals(kind)) {
        paths.add("/p/" + code + "/embed/captioned/");
        paths.add("/p/" + code + "/embed/");
      }

      List<URI> out = new ArrayList<>();
      for (String p : paths) {
        try {
          out.add(URI.create(base + p));
        } catch (Exception ignored) {
        }
      }
      return out;
    } catch (Exception ignored) {
      return List.of();
    }
  }
}

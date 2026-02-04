package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Generic oEmbed-based link preview resolver.
 *
 * <p>This consolidates multiple "provider" resolvers (Spotify, Mastodon, etc.) that all follow the
 * same pattern: detect URL, call an oEmbed endpoint, map the response into a {@link LinkPreview}.
 */
final class OEmbedLinkPreviewResolver implements LinkPreviewResolver {

  
  record OEmbedFields(String title, String authorName, String providerName, String thumbnailUrl, String html) {}

  /**
   * Provider definition:
   * <ul>
   *   <li>{@code matches}: whether the provider applies to the target URL</li>
   *   <li>{@code endpointBuilder}: builds the oEmbed endpoint for the target URL</li>
   *   <li>{@code defaultSiteName}: fallback site name when provider_name is absent</li>
   *   <li>{@code titleFallback}: builds a fallback title when oEmbed title is absent</li>
   * </ul>
   */
  record Provider(
      String id,
      Predicate<URI> matches,
      BiFunction<URI, String, URI> endpointBuilder,
      String defaultSiteName,
      Function<OEmbedFields, String> titleFallback
  ) {
    Provider {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(matches, "matches");
      Objects.requireNonNull(endpointBuilder, "endpointBuilder");
      Objects.requireNonNull(titleFallback, "titleFallback");
    }
  }

  private final List<Provider> providers;

  OEmbedLinkPreviewResolver(List<Provider> providers) {
    this.providers = providers == null ? List.of() : List.copyOf(providers);
  }

  static List<Provider> defaultProviders() {
    return List.of(spotifyProvider(), mastodonProvider());
  }

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) {
    try {
      if (uri == null || originalUrl == null || http == null) return null;

      Provider p = firstMatchingProvider(uri);
      if (p == null) return null;

      URI api = p.endpointBuilder.apply(uri, originalUrl);
      if (api == null) return null;

      // Some providers (including Mastodon) advertise oEmbed as application/json+oembed.
      var resp = http.getString(api, "application/json+oembed,application/json", null);
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        return null;
      }

      String json = resp.body();
      if (json == null || json.isBlank()) return null;

      OEmbedFields fields = new OEmbedFields(
          stripToNull(TinyJson.findString(json, "title")),
          stripToNull(TinyJson.findString(json, "author_name")),
          stripToNull(TinyJson.findString(json, "provider_name")),
          stripToNull(TinyJson.findString(json, "thumbnail_url")),
          stripToNull(TinyJson.findString(json, "html"))
      );

      String siteName = firstNonBlank(fields.providerName(), stripToNull(p.defaultSiteName));
      String title = firstNonBlank(fields.title(), stripToNull(p.titleFallback.apply(fields)), siteName);

      // Keep descriptions short and stable.
      String description = null;

      // Mastodon: the oEmbed title is often generic; the HTML field contains the actual post.
      if ("mastodon".equals(p.id()) && fields.html() != null) {
        description = mastodonDescFromOEmbedHtml(fields.html());
      }

      // Generic fallback: use author_name.
      if ((description == null || description.isBlank()) && fields.authorName() != null) {
        description = "by " + fields.authorName();
      }

      if (title == null) return null;
      int mediaCount = (fields.thumbnailUrl() != null && !fields.thumbnailUrl().isBlank()) ? 1 : 0;
      return new LinkPreview(originalUrl, title, description, siteName, fields.thumbnailUrl(), mediaCount);
    } catch (Exception ignored) {
      return null;
    }
  }

  private Provider firstMatchingProvider(URI uri) {
    for (Provider p : providers) {
      try {
        if (p.matches.test(uri)) return p;
      } catch (Exception ignored) {
      }
    }
    return null;
  }

  // ---- Providers ----

  private static Provider spotifyProvider() {
    Predicate<URI> match = u -> {
      String host = u.getHost();
      if (host == null || host.isBlank()) return false;
      String h = host.toLowerCase(Locale.ROOT);
      return h.equals("open.spotify.com") || h.equals("spotify.link") || h.endsWith(".spotify.com");
    };

    BiFunction<URI, String, URI> endpoint = (u, originalUrl) -> {
      String enc = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
      // Spotify's public oEmbed endpoint lives on open.spotify.com.
      return URI.create("https://open.spotify.com/oembed?url=" + enc);
    };

    Function<OEmbedFields, String> titleFallback = f -> "Spotify";
    return new Provider("spotify", match, endpoint, "Spotify", titleFallback);
  }

  private static final Pattern MASTODON_AT_STYLE = Pattern.compile("^/@[^/]+/\\d+(/.*)?$");
  private static final Pattern MASTODON_USERS_STYLE = Pattern.compile("^/users/[^/]+/statuses/\\d+(/.*)?$");
  private static final Pattern MASTODON_WEB_STYLE = Pattern.compile("^/web/statuses/\\d+(/.*)?$");

  private static Provider mastodonProvider() {
    Predicate<URI> match = u -> {
      String host = u.getHost();
      if (host == null || host.isBlank()) return false;
      String path = u.getPath() == null ? "" : u.getPath();
      return looksLikeMastodonStatusPath(path);
    };

    BiFunction<URI, String, URI> endpoint = (u, originalUrl) -> {
      // Use the instance that served the URL (scheme + authority).
      if (u.getScheme() == null || u.getAuthority() == null) return null;
      String base = u.getScheme() + "://" + u.getAuthority();
      String enc = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
      // Some deployments expect an explicit format.
      return URI.create(base + "/api/oembed?format=json&url=" + enc);
    };

    Function<OEmbedFields, String> titleFallback = f -> {
      if (f.authorName() != null) return "Post by " + f.authorName();
      return "Mastodon post";
    };

    // provider_name may be the instance name; we keep it when present.
    return new Provider("mastodon", match, endpoint, "Mastodon", titleFallback);
  }

  private static boolean looksLikeMastodonStatusPath(String path) {
    if (path == null) return false;
    String p = path.strip();
    if (p.isEmpty()) return false;
    while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
    return MASTODON_AT_STYLE.matcher(p).matches()
        || MASTODON_USERS_STYLE.matcher(p).matches()
        || MASTODON_WEB_STYLE.matcher(p).matches();
  }

  private static String mastodonDescFromOEmbedHtml(String html) {
    try {
      if (html == null || html.isBlank()) return null;
      org.jsoup.nodes.Document d = org.jsoup.Jsoup.parseBodyFragment(html);
      // Avoid script placeholder text.
      d.select("script,style").remove();

      // Prefer paragraphs inside the blockquote (post content), which tends to be cleaner.
      var ps = d.select("blockquote p");
      String text;
      if (ps != null && !ps.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (org.jsoup.nodes.Element p : ps) {
          String t = p.text();
          if (t == null || t.isBlank()) continue;
          if (sb.length() > 0) sb.append("\n");
          sb.append(t.strip());
        }
        text = sb.toString();
      } else {
        var bq = d.selectFirst("blockquote");
        text = bq != null ? bq.text() : d.text();
      }

      text = stripToNull(text);
      if (text == null) return null;
      // Keep it short; UI will also clamp, but this prevents huge tooltips and layout thrash.
      int max = 320;
      if (text.length() > max) {
        text = text.substring(0, max).strip() + "…";
      }
      return text;
    } catch (Exception ignored) {
      return null;
    }
  }

  // ---- helpers ----

  private static String stripToNull(String s) {
    if (s == null) return null;
    String v = s.strip();
    return v.isEmpty() ? null : v;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v.strip();
    }
    return null;
  }
}

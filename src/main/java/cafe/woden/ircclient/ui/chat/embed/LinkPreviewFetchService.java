package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Fetch and parse link preview metadata.
 *
 * <p>Implementation strategy:
 * <ul>
 *   <li>GET the URL (http/https) with a small timeout</li>
 *   <li>Read up to {@link #MAX_BYTES} of HTML</li>
 *   <li>Parse OpenGraph/Twitter tags with jsoup</li>
 *   <li>Cache results and de-dupe inflight fetches</li>
 * </ul>
 */
@Component
@Lazy
public class LinkPreviewFetchService {

  private static final Logger log = LoggerFactory.getLogger(LinkPreviewFetchService.class);

  /** Avoid downloading giant pages just to find OG tags. */
  private static final int MAX_BYTES = 1024 * 1024; // 1 MiB
  private static final Duration TIMEOUT = Duration.ofSeconds(8);

  private static final String USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";

  private final HttpClient client = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(TIMEOUT)
      .build();

  private final ConcurrentMap<String, java.lang.ref.SoftReference<LinkPreview>> cache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Single<LinkPreview>> inflight = new ConcurrentHashMap<>();

  public Single<LinkPreview> fetch(String url) {
    if (url == null || url.isBlank()) {
      return Single.error(new IllegalArgumentException("url is blank"));
    }

    // Normalize early so cache keys are stable.
    final String normalized = ChatRichTextRenderer.normalizeUrl(url.trim());

    // Cache hit
    var ref = cache.get(normalized);
    if (ref != null) {
      var v = ref.get();
      if (v != null) return Single.just(v);
      cache.remove(normalized, ref);
    }

    // Inflight de-dupe
    Single<LinkPreview> existing = inflight.get(normalized);
    if (existing != null) return existing;

    Single<LinkPreview> created = Single.fromCallable(() -> load(normalized))
        .subscribeOn(Schedulers.io())
        .doFinally(() -> inflight.remove(normalized));

    Single<LinkPreview> prev = inflight.putIfAbsent(normalized, created);
    return prev != null ? prev : created;
  }

  private LinkPreview load(String url) throws Exception {
    URI uri = URI.create(url);
    String scheme = String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new IllegalArgumentException("unsupported scheme: " + scheme);
    }
    if (isDefinitelyLocalOrPrivateHost(uri.getHost())) {
      throw new IllegalArgumentException("refusing to fetch local/private host: " + uri.getHost());
    }

    // Special-case: Wikipedia articles. OpenGraph descriptions are often a single sentence.
    // The REST summary endpoint lets us show a slightly longer extract while still staying compact.
    LinkPreview wiki = tryLoadWikipediaPreview(uri);
    if (wiki != null) {
      cache.put(url, new java.lang.ref.SoftReference<>(wiki));
      return wiki;
    }

    LinkPreview yt = tryLoadYouTubePreview(uri);
    if (yt != null) {
      cache.put(url, new java.lang.ref.SoftReference<>(yt));
      return yt;
    }

    LinkPreview x = tryLoadXPreview(uri);
    if (x != null) {
      cache.put(url, new java.lang.ref.SoftReference<>(x));
      return x;
    }

    LinkPreview gh = tryLoadGitHubPreview(uri);
    if (gh != null) {
      cache.put(url, new java.lang.ref.SoftReference<>(gh));
      return gh;
    }

    HttpRequest req = HttpRequest.newBuilder(uri)
        .timeout(TIMEOUT)
        .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
        .header("User-Agent", USER_AGENT)
        .header("Accept-Language", ACCEPT_LANGUAGE)
        .GET()
        .build();

    HttpResponse<java.io.InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
    int status = resp.statusCode();
    if (status < 200 || status >= 300) {
      throw new IllegalStateException("HTTP " + status);
    }

    // Best-effort content type gate: still allow parsing when missing.
    String ct = resp.headers().firstValue("content-type").orElse("");
    if (!ct.isBlank()) {
      String lower = ct.toLowerCase(Locale.ROOT);
      if (!lower.contains("text/html") && !lower.contains("application/xhtml") && !lower.contains("xml")) {
        // Most non-HTML targets won't have OG tags worth previewing.
        throw new IllegalStateException("content-type not html: " + ct);
      }
    }

    byte[] bytes = readUpTo(resp.body(), MAX_BYTES);
    Document doc = Jsoup.parse(new ByteArrayInputStream(bytes), null, url);
    LinkPreview preview = LinkPreviewParser.parse(doc, url);
    cache.put(url, new java.lang.ref.SoftReference<>(preview));
    return preview;
  }

  private LinkPreview tryLoadWikipediaPreview(URI articleUri) {
    try {
      if (!WikipediaPreviewUtil.isWikipediaArticleUri(articleUri)) return null;
      URI api = WikipediaPreviewUtil.toSummaryApiUri(articleUri);
      if (api == null) return null;

      HttpRequest req = HttpRequest.newBuilder(api)
          .timeout(TIMEOUT)
          .header("Accept", "application/json")
          .header("User-Agent", USER_AGENT)
          .header("Accept-Language", ACCEPT_LANGUAGE)
          .GET()
          .build();

      HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        return null; // fall back to OG parser
      }

      LinkPreview p = WikipediaPreviewUtil.parseSummaryJson(resp.body(), articleUri);
      return p;
    } catch (Exception ignored) {
      return null;
    }
  }

  private LinkPreview tryLoadYouTubePreview(URI videoUri) {
    try {
      if (videoUri == null) return null;
      if (!YouTubePreviewUtil.isYouTubeVideoUri(videoUri)) return null;

      // Canonicalize so open/copy feels consistent.
      String id = YouTubePreviewUtil.extractVideoId(videoUri);
      URI canonical = id != null ? YouTubePreviewUtil.canonicalWatchUri(id) : null;
      URI target = canonical != null ? canonical : videoUri;

      URI api = YouTubePreviewUtil.oEmbedUri(target);
      if (api == null) return null;

      HttpRequest req = HttpRequest.newBuilder(api)
          .timeout(TIMEOUT)
          .header("Accept", "application/json")
          .header("User-Agent", USER_AGENT)
          .header("Accept-Language", ACCEPT_LANGUAGE)
          .GET()
          .build();

      HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        return null;
      }

      // Enrich with yt-dlp metadata (duration/views/likes/description) if available.
      // This avoids scraping the YouTube watch page, which is often consent-gated.
      var meta = YouTubePreviewUtil.tryFetchYtDlpMeta(target.toString(), Duration.ofSeconds(6));

      LinkPreview p = YouTubePreviewUtil.parseOEmbedJson(resp.body(), target, null, meta);
      return p;
    } catch (Exception ignored) {
      return null;
    }
  }

  private LinkPreview tryLoadXPreview(URI statusUri) {
	try {
    if (statusUri == null) return null;
    String id = XPreviewUtil.extractStatusId(statusUri);
    if (id == null) return null;

    // First try the public syndication JSON endpoint (fast when it works).
    URI api = XPreviewUtil.syndicationApiUri(id);
    if (api != null) {
      HttpRequest req = HttpRequest.newBuilder(api)
          .timeout(TIMEOUT)
          .header("Accept", "application/json")
          // Some endpoints are picky about UA/Referer; provide a plausible web embed context.
          .header("User-Agent", USER_AGENT)
          .header("Accept-Language", ACCEPT_LANGUAGE)
          .header("Referer", "https://platform.twitter.com/")
          .header("Origin", "https://platform.twitter.com")
          .GET()
          .build();

      HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
        LinkPreview parsed = XPreviewUtil.parseSyndicationJson(resp.body(), statusUri);
        if (parsed != null) return parsed;
        log.debug("X syndication JSON parsed empty for {}", statusUri);
      } else {
        log.debug("X syndication endpoint returned {} for {}", resp.statusCode(), statusUri);
      }
    }


	  // If syndication is blocked/broken, try X's oEmbed endpoint (stable, no JS scraping).
	  LinkPreview oembed = tryLoadXPreviewViaOEmbed(statusUri, id);
	  if (oembed != null) return oembed;

	  // Finally, fall back to HTML-based unfurl proxies (Nitter, FixupX, FxTwitter, ...).
	  return tryLoadXPreviewViaProxy(statusUri);

  } catch (Exception ignored) {
    return null;
  }
	}

	private LinkPreview tryLoadXPreviewViaOEmbed(URI statusUri, String statusId) {
	  try {
	    if (statusUri == null) return null;
	    if (statusId == null || statusId.isBlank()) return null;
	    URI api = XPreviewUtil.oEmbedApiUri(statusId);
	    if (api == null) return null;

	    HttpRequest req = HttpRequest.newBuilder(api)
	        .timeout(TIMEOUT)
	        .header("Accept", "application/json")
	        .header("User-Agent", USER_AGENT)
	        .header("Accept-Language", ACCEPT_LANGUAGE)
	        .header("Referer", "https://publish.x.com/")
	        .GET()
	        .build();

	    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
	    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
	      log.debug("X oEmbed endpoint returned {} for {}", resp.statusCode(), statusUri);
	      return null;
	    }
	    LinkPreview p = XPreviewUtil.parseOEmbedJson(resp.body(), statusUri, statusId);
	    if (p == null) log.debug("X oEmbed JSON parsed empty for {}", statusUri);
	    return p;
	  } catch (Exception e) {
	    log.debug("X oEmbed unfurl failed: {}", e.toString());
	    return null;
	  }
	}

  private LinkPreview tryLoadXPreviewViaProxy(URI statusUri) {
  try {
    if (statusUri == null) return null;

    for (URI proxy : XPreviewUtil.proxyUnfurlCandidates(statusUri)) {
      if (proxy == null) continue;

      HttpRequest req = HttpRequest.newBuilder(proxy)
          .timeout(TIMEOUT)
          .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
          .header("User-Agent", USER_AGENT)
          .header("Accept-Language", ACCEPT_LANGUAGE)
          .GET()
          .build();

      HttpResponse<java.io.InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        log.debug("X proxy unfurl returned {} for {}", resp.statusCode(), proxy);
        continue;
      }

      byte[] bytes = readUpTo(resp.body(), MAX_BYTES);
      org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(new java.io.ByteArrayInputStream(bytes), null, proxy.toString());
      LinkPreview p = LinkPreviewParser.parse(doc, proxy.toString());
      if (p == null) {
        log.debug("X proxy unfurl parsed empty for {}", proxy);
        continue;
      }

      String title = p.title();
      String desc = p.description();
      String image = p.imageUrl();

      String site = "X";

      return new LinkPreview(statusUri.toString(), title, desc, site, image);
    }

    return null;
  } catch (Exception ignored) {
    return null;
  }
}

  private LinkPreview tryLoadGitHubPreview(URI githubUri) {
    try {
      if (githubUri == null) return null;
      GitHubPreviewUtil.GitHubLink link = GitHubPreviewUtil.parse(githubUri);
      if (link == null) return null;

      URI api = GitHubPreviewUtil.apiUri(link);
      if (api == null) return null;

      HttpRequest req = HttpRequest.newBuilder(api)
          .timeout(TIMEOUT)
          .header("Accept", "application/vnd.github+json")
          .header("X-GitHub-Api-Version", "2022-11-28")
          .header("User-Agent", USER_AGENT)
          .header("Accept-Language", ACCEPT_LANGUAGE)
          .GET()
          .build();

      HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        return null;
      }

      return GitHubPreviewUtil.parseApiJson(resp.body(), link, githubUri);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static byte[] readUpTo(java.io.InputStream in, int maxBytes) throws Exception {
    if (in == null) return new byte[0];
    byte[] buf = new byte[Math.min(8192, Math.max(1024, maxBytes))];
    int total = 0;
    try (in) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      while (true) {
        int toRead = Math.min(buf.length, maxBytes - total);
        if (toRead <= 0) break;
        int n = in.read(buf, 0, toRead);
        if (n < 0) break;
        out.write(buf, 0, n);
        total += n;
      }
      return out.toByteArray();
    }
  }

  private static boolean isDefinitelyLocalOrPrivateHost(String host) {
    if (host == null || host.isBlank()) return false;
    String h = host.toLowerCase(Locale.ROOT).trim();

    if (h.equals("localhost") || h.equals("localhost.localdomain") || h.equals("0.0.0.0") || h.equals("::1")) {
      return true;
    }
    // If it's an IPv4 literal, block common private ranges.
    if (h.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
      String[] parts = h.split("\\.");
      if (parts.length == 4) {
        int a = parseInt(parts[0]);
        int b = parseInt(parts[1]);
        if (a == 10) return true;
        if (a == 127) return true;
        if (a == 192 && b == 168) return true;
        if (a == 172 && b >= 16 && b <= 31) return true;
      }
    }
    // IPv6: block loopback + unique local + link-local.
    if (h.contains(":")) {
      if (h.equals("::1")) return true;
      if (h.startsWith("fc") || h.startsWith("fd")) return true; // fc00::/7 (very rough)
      if (h.startsWith("fe80")) return true; // link-local
    }

    return false;
  }

  private static int parseInt(String s) {
    try {
      return Integer.parseInt(s);
    } catch (Exception ignored) {
      return -1;
    }
  }
}

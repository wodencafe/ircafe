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

  /** Avoid downloading giant pages just to find OG tags. */
  private static final int MAX_BYTES = 1024 * 1024; // 1 MiB
  private static final Duration TIMEOUT = Duration.ofSeconds(8);

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

    HttpRequest req = HttpRequest.newBuilder(uri)
        .timeout(TIMEOUT)
        .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
        .header("User-Agent", "IRCafe LinkPreview/0.1")
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

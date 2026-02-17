package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.net.HttpLite;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.Proxy;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ImageFetchService {

  private static final Logger log = LoggerFactory.getLogger(ImageFetchService.class);

  // Safety guardrails: stop reading after this many bytes.
  // IMDb/Amazon posters and some modern sites regularly exceed 8 MiB. We still keep a ceiling to
  // avoid runaway memory usage, but allow larger images.
  public static final int MAX_BYTES = 20 * 1024 * 1024; // 20 MiB

  private final ConcurrentMap<String, SoftReference<byte[]>> cache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Single<byte[]>> inflight = new ConcurrentHashMap<>();

  private final ServerProxyResolver proxyResolver;

  public ImageFetchService(ServerProxyResolver proxyResolver) {
    this.proxyResolver = proxyResolver;
  }

  public Single<byte[]> fetch(String serverId, String url) {
    String base = normalizeKey(url);
    if (base.isEmpty()) {
      return Single.error(new IllegalArgumentException("Empty URL"));
    }

    String sid = Objects.toString(serverId, "").trim();
    // Images can vary by proxy (blocked/geo/CDN variants), so isolate cache by server.
    String key = sid + "|" + base;

    byte[] cached = getCached(key);
    if (cached != null) {
      return Single.just(cached);
    }

    // Deduplicate concurrent requests.
    return inflight.computeIfAbsent(key, k ->
        Single.fromCallable(() -> download(sid, base))
            .subscribeOn(Schedulers.io())
            .doOnSuccess(bytes -> cache.put(k, new SoftReference<>(bytes)))
            .doOnError(err -> log.warn("Image fetch failed for {}: {}", safeForLog(base), summarizeErr(err)))
            .doFinally(() -> inflight.remove(k))
            // cache() turns this into a replaying Single so late subscribers get the same outcome.
            .cache()
    );
  }

  private byte[] getCached(String key) {
    SoftReference<byte[]> ref = cache.get(key);
    return ref != null ? ref.get() : null;
  }

  private static String normalizeKey(String url) {
    if (url == null) return "";
    return Objects.toString(url, "").trim();
  }

  // Back-compat for any callers not yet server-aware.
  public Single<byte[]> fetch(String url) {
    return fetch(null, url);
  }

  private byte[] download(String serverId, String url) throws IOException, InterruptedException {
    return download(serverId, url, 0);
  }

  private byte[] download(String serverId, String url, int attempt) throws IOException, InterruptedException {
    URI uri = URI.create(url);
    String scheme = uri.getScheme();
    if (scheme == null) throw new IOException("URL has no scheme: " + url);
    scheme = scheme.toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new IOException("Unsupported URL scheme for image embed: " + scheme);
    }

    // Use HttpURLConnection (via HttpLite) so SOCKS proxies work.
    // java.net.http.HttpClient does not support SOCKS proxies.
    Map<String, String> headers = new HashMap<>();
    headers.put("User-Agent", needsInstagramReferer(uri) ? PreviewHttp.BROWSER_USER_AGENT : PreviewHttp.USER_AGENT);
    headers.put("Accept-Language", PreviewHttp.ACCEPT_LANGUAGE);
    headers.put("Accept-Encoding", "gzip");
    // IMPORTANT: Do NOT advertise AVIF by default.
    // Some CDNs will pick AVIF whenever it's present in Accept (ignoring q=), and ImageIO
    // can't decode it without a native/plugin decoder. If you later add AVIF support,
    // you can add image/avif back into this header.
    headers.put("Accept", "image/jpeg,image/png,image/webp,image/gif,image/*;q=0.5,*/*;q=0.4");
    // Some CDNs are picky; these headers help us look like a browser fetching an image.
    headers.put("Sec-Fetch-Dest", "image");
    headers.put("Sec-Fetch-Mode", "no-cors");

    // Some IMDb/Amazon image endpoints can be picky without a referer.
    if (needsImdbReferer(uri)) {
      headers.put("Referer", "https://www.imdb.com/");
    }

    // Instagram CDN endpoints can return bot-check HTML without a referer.
    if (!headers.containsKey("Referer") && needsInstagramReferer(uri)) {
      headers.put("Referer", "https://www.instagram.com/");
    }

    ProxyPlan plan = (proxyResolver != null) ? proxyResolver.planForServer(serverId) : ProxyPlan.direct();
    Proxy proxy = (plan.proxy() != null) ? plan.proxy() : Proxy.NO_PROXY;
    HttpLite.Response<InputStream> res = HttpLite.getStream(
        uri,
        headers,
        proxy,
        plan.connectTimeoutMs(),
        plan.readTimeoutMs()
    );
    int code = res.statusCode();
    String contentType = res.headers().firstValue("content-type").orElse("");
    long contentLength = res.headers().firstValueAsLong("content-length").orElse(-1L);
    if (code < 200 || code >= 300) {
      log.warn("Image fetch HTTP {} for {} (content-type={}, content-length={})",
          code, safeForLog(url), safeForLog(contentType), contentLength);

      // Ensure we don't leak the connection.
      try (InputStream ignored = res.body()) {
        // no-op
      }

      // Amazon CDN sometimes doesn't like certain sized variants. If we're on a sized URL and it
      // fails, retry once with the unsized original.
      if (attempt == 0) {
        String fallback = maybeUnsizedAmazonUrl(url);
        if (!fallback.equals(url)) {
          log.warn("Retrying Amazon image without size token after HTTP {}: {}", code, safeForLog(fallback));
          return download(serverId, fallback, attempt + 1);
        }
      }

      throw new IOException("HTTP " + code + " for " + url);
    }

    // If the server tells us it's too big, try a sized Amazon variant once (when applicable).
    if (contentLength > MAX_BYTES) {
      // Ensure we don't leak the connection.
      try (InputStream ignored = res.body()) {
        // no-op
      }
      if (attempt == 0) {
        String sized = maybeSizedAmazonUrl(url, 512);
        if (!sized.equals(url)) {
          log.warn("Image too large by content-length ({} bytes > {}), retrying sized Amazon URL: {}",
              contentLength, MAX_BYTES, safeForLog(sized));
          return download(serverId, sized, attempt + 1);
        }
      }
      throw new IOException("Image too large (" + contentLength + " bytes > " + MAX_BYTES + ")");
    }

    try (InputStream in = res.body(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      int n;
      int total = 0;
      int sampleCap = 4096;
      byte[] sample = new byte[sampleCap];
      int sampleN = 0;
      while ((n = in.read(buf)) >= 0) {
        if (n == 0) continue;
        total += n;
        if (total > MAX_BYTES) {
          if (attempt == 0) {
            String sized = maybeSizedAmazonUrl(url, 512);
            if (!sized.equals(url)) {
              log.warn("Image too large while streaming (> {} bytes), retrying sized Amazon URL: {}",
                  MAX_BYTES, safeForLog(sized));
              return download(serverId, sized, attempt + 1);
            }
          }
          throw new IOException("Image too large (streamed > " + MAX_BYTES + " bytes)");
        }
        out.write(buf, 0, n);

        // Capture a small sample to help diagnose CDN blocks returning HTML.
        if (sampleN < sampleCap) {
          int toCopy = Math.min(n, sampleCap - sampleN);
          System.arraycopy(buf, 0, sample, sampleN, toCopy);
          sampleN += toCopy;
        }
      }

      byte[] bytes = out.toByteArray();

      // Many CDNs (including IMDb/Amazon) sometimes return an HTML bot-check page with a 200.
      // If that happens, ImageIO will fail and the user just sees a missing thumbnail.
      // Detect that early and log a useful warning.
      if (looksLikeHtmlResponse(contentType, sample, sampleN)) {
        String sampleText = safeSampleText(sample, sampleN);
        log.warn("Image fetch got HTML instead of an image for {} (content-type={}, bytes={}) sample={}",
            safeForLog(url), safeForLog(contentType), bytes.length, safeForLog(sampleText));
        throw new IOException("Image endpoint returned HTML (likely blocked)");
      }

      return bytes;
    }
  }

  private static String maybeSizedAmazonUrl(String url, int widthPx) {
    if (url == null || url.isBlank()) return url;
    if (widthPx <= 0) return url;

    String lower = url.toLowerCase(Locale.ROOT);
    if (!(lower.contains("media-amazon.com") || lower.contains("images-amazon.com"))) return url;

    String marker = "@._V1_";
    int idx = url.indexOf(marker);
    if (idx < 0) return url;

    String after = url.substring(idx + marker.length());
    if (after.startsWith("UX") || after.startsWith("UY") || after.startsWith("SX") || after.startsWith("SY")) {
      return url;
    }

    return url.substring(0, idx) + marker + "UX" + widthPx + "_" + after;
  }

  private static String maybeUnsizedAmazonUrl(String url) {
    if (url == null || url.isBlank()) return url;
    String lower = url.toLowerCase(Locale.ROOT);
    if (!(lower.contains("media-amazon.com") || lower.contains("images-amazon.com"))) return url;

    String marker = "@._V1_";
    int idx = url.indexOf(marker);
    if (idx < 0) return url;

    String after = url.substring(idx + marker.length());
    // Remove a leading size token like UX256_ or UY512_ if present.
    // This is intentionally simple and only targets the immediate token after @._V1_.
    String stripped = after.replaceFirst("^(U[XY]|S[XY])\\d+_", "");
    if (stripped.equals(after)) return url;

    return url.substring(0, idx) + marker + stripped;
  }

  private static boolean needsImdbReferer(URI uri) {
    if (uri == null) return false;
    String host = uri.getHost();
    if (host == null) return false;
    host = host.toLowerCase(Locale.ROOT);
    return host.contains("media-amazon.com")
        || host.contains("images-amazon.com")
        || host.contains("amazonaws.com");
  }

  private static boolean needsInstagramReferer(URI uri) {
    if (uri == null) return false;
    String host = uri.getHost();
    if (host == null || host.isBlank()) return false;
    String h = host.toLowerCase(Locale.ROOT);
    if (h.startsWith("www.")) h = h.substring(4);

    if (h.equals("instagram.com") || h.endsWith(".instagram.com") || h.equals("instagr.am")) {
      return true;
    }

    // Common direct media hosts.
    if (h.contains("cdninstagram.com")) return true;
    if (h.endsWith("fbcdn.net") && h.contains("instagram")) return true;
    return false;
  }

  private static boolean looksLikeHtmlResponse(String contentType, byte[] sample, int sampleN) {
    try {
      if (contentType != null && !contentType.isBlank()) {
        // If the server explicitly says it's HTML/XML, treat it as a block page.
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("text/html") || ct.contains("application/xhtml") || ct.contains("xml")) return true;
        if (ct.startsWith("image/")) return false;
      }
      if (sample == null || sampleN <= 0) return false;

      // Heuristic sniff: HTML typically starts with '<' or contains known bot-check text.
      int i = 0;
      while (i < sampleN && (sample[i] == '\n' || sample[i] == '\r' || sample[i] == '\t' || sample[i] == ' ')) i++;
      if (i < sampleN && sample[i] == '<') return true;
      String s = safeSampleText(sample, sampleN).toLowerCase(Locale.ROOT);
      return s.contains("not a robot") || s.contains("verify") || s.contains("javascript is disabled")
          || s.contains("access denied") || s.contains("captcha");
    } catch (Exception ignored) {
      return false;
    }
  }

  private static String safeSampleText(byte[] sample, int n) {
    if (sample == null || n <= 0) return "";
    try {
      int len = Math.min(n, sample.length);
      String s = new String(sample, 0, len, java.nio.charset.StandardCharsets.UTF_8);
      s = s.replaceAll("\\s+", " ").trim();
      if (s.length() > 220) s = s.substring(0, 220) + "…";
      return s;
    } catch (Exception ignored) {
      return "";
    }
  }

  private static String safeForLog(String s) {
    if (s == null) return "";
    String t = s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    if (t.length() > 500) t = t.substring(0, 500) + "…";
    return t;
  }

  private static String summarizeErr(Throwable t) {
    if (t == null) return "";
    String msg = t.getMessage();
    if (msg == null || msg.isBlank()) msg = t.getClass().getSimpleName();
    return safeForLog(msg);
  }
}

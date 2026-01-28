package cafe.woden.ircclient.ui.chat.embed;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.lang.ref.SoftReference;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Fetches remote image bytes for inline transcript embeds.
 *
 */
@Component
@Lazy
public class ImageFetchService {

  // Safety guardrails: stop reading after this many bytes.
  public static final int MAX_BYTES = 8 * 1024 * 1024; // 8 MiB

  private final HttpClient client = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  private final ConcurrentMap<String, SoftReference<byte[]>> cache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Single<byte[]>> inflight = new ConcurrentHashMap<>();

  public Single<byte[]> fetch(String url) {
    String key = normalizeKey(url);
    if (key.isEmpty()) {
      return Single.error(new IllegalArgumentException("Empty URL"));
    }

    byte[] cached = getCached(key);
    if (cached != null) {
      return Single.just(cached);
    }

    // Deduplicate concurrent requests.
    return inflight.computeIfAbsent(key, u ->
        Single.fromCallable(() -> download(u))
            .subscribeOn(Schedulers.io())
            .doOnSuccess(bytes -> cache.put(u, new SoftReference<>(bytes)))
            .doFinally(() -> inflight.remove(u))
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

  private byte[] download(String url) throws IOException, InterruptedException {
    URI uri = URI.create(url);
    String scheme = uri.getScheme();
    if (scheme == null) throw new IOException("URL has no scheme: " + url);
    scheme = scheme.toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new IOException("Unsupported URL scheme for image embed: " + scheme);
    }

    HttpRequest req = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(20))
        .header("User-Agent", "IRCafe/0.0.1")
        .GET()
        .build();

    HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
    int code = res.statusCode();
    if (code < 200 || code >= 300) {
      throw new IOException("HTTP " + code + " for " + url);
    }

    long contentLength = res.headers().firstValueAsLong("content-length").orElse(-1L);
    if (contentLength > MAX_BYTES) {
      throw new IOException("Image too large (" + contentLength + " bytes > " + MAX_BYTES + ")");
    }

    try (InputStream in = res.body(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      int n;
      int total = 0;
      while ((n = in.read(buf)) >= 0) {
        if (n == 0) continue;
        total += n;
        if (total > MAX_BYTES) {
          throw new IOException("Image too large (streamed > " + MAX_BYTES + " bytes)");
        }
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    }
  }
}

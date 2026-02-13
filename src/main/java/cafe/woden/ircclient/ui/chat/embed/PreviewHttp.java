package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.net.HttpLite;
import cafe.woden.ircclient.net.ProxyPlan;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP helper used by link preview resolvers.
 *
 * <p>Uses {@link java.net.HttpURLConnection} via {@link HttpLite} so that SOCKS proxies
 * can be applied (the JDK {@code java.net.http.HttpClient} does not support SOCKS).
 */
final class PreviewHttp {

  // Package-visible so other embed helpers (e.g., ImageFetchService) can share the same headers.
  static final String USER_AGENT = "ircafe-link-preview/1.0";
  // Some sites (notably IMDb) increasingly block non-browser user agents.
  // Use this when we need a browser-ish UA to avoid being served interstitial pages.
  static final String BROWSER_USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
  static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";
  private static final Map<String, String> BASE_HEADERS = Map.of(
      "User-Agent", USER_AGENT,
      "Accept-Language", ACCEPT_LANGUAGE,
      "Accept-Encoding", "gzip"
  );

  private final Proxy proxy;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;

  PreviewHttp(ProxyPlan plan) {
    ProxyPlan p = plan != null ? plan : ProxyPlan.direct();
    this.proxy = (p.proxy() != null) ? p.proxy() : Proxy.NO_PROXY;
    this.connectTimeoutMs = Math.max(1, p.connectTimeoutMs());
    this.readTimeoutMs = Math.max(1, p.readTimeoutMs());
  }

  public HttpLite.Response<InputStream> getStream(URI uri, String accept) throws IOException {
    return getStream(uri, accept, Map.of());
  }

  public HttpLite.Response<InputStream> getStream(URI uri, String accept, Map<String, String> extraHeaders) throws IOException {
    Map<String, String> headers = new HashMap<>(BASE_HEADERS);
    headers.put("Accept", accept);
    if (extraHeaders != null) headers.putAll(extraHeaders);

    return HttpLite.getStream(uri, headers, proxy, connectTimeoutMs, readTimeoutMs);
  }

  public HttpLite.Response<String> getString(URI uri) throws IOException {
    return getString(uri, Map.of());
  }

  public HttpLite.Response<String> getString(URI uri, Map<String, String> extraHeaders) throws IOException {
    return getString(
        uri,
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        extraHeaders);
  }

  /**
   * Legacy-compatible overload used by many preview resolvers.
   *
   * @param uri target URI
   * @param accept explicit Accept header (e.g. application/json)
   * @param extraHeaders optional extra headers (may be null)
   */
  public HttpLite.Response<String> getString(URI uri, String accept, Map<String, String> extraHeaders) throws IOException {
    Map<String, String> headers = new HashMap<>(BASE_HEADERS);
    if (accept != null && !accept.isBlank()) {
      headers.put("Accept", accept);
    } else {
      headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    }
    if (extraHeaders != null) headers.putAll(extraHeaders);

    return HttpLite.getString(uri, headers, proxy, connectTimeoutMs, readTimeoutMs);
  }

  public static Optional<String> header(HttpLite.Response<?> response, String name) {
    return response.headers().firstValue(name);
  }

  public static Map<String, String> headers(Object... keyValues) {
    Map<String, String> m = new HashMap<>();
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      Object k = keyValues[i];
      Object v = keyValues[i + 1];
      if (k instanceof String ks && v instanceof String vs) {
        m.put(ks, vs);
      }
    }
    return m;
  }

  public static boolean looksLikeHtml(String contentType) {
    if (contentType == null) return false;
    String ct = contentType.toLowerCase();
    return ct.contains("text/html") || ct.contains("application/xhtml+xml");
  }

  public static String readUpTo(InputStream in, int maxBytes) throws IOException {
    try (in) {
      ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 16 * 1024));
      byte[] buf = new byte[8 * 1024];
      int remaining = maxBytes;
      while (remaining > 0) {
        int read = in.read(buf, 0, Math.min(buf.length, remaining));
        if (read < 0) break;
        out.write(buf, 0, read);
        remaining -= read;
      }
      return out.toString(StandardCharsets.UTF_8);
    }
  }

/**
 * Read up to {@code maxBytes} bytes from a stream, returning raw bytes.
 * <p>
 * This is used by HTML-based resolvers that want to hand a byte-limited body to jsoup.
 * We intentionally swallow IO failures and return an empty array to keep resolvers robust.
 */
public static byte[] readUpToBytes(InputStream in, int maxBytes) {
  if (in == null || maxBytes <= 0) return new byte[0];
  try (in) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 16 * 1024));
    byte[] buf = new byte[8 * 1024];
    int remaining = maxBytes;
    while (remaining > 0) {
      int n = in.read(buf, 0, Math.min(buf.length, remaining));
      if (n < 0) break;
      out.write(buf, 0, n);
      remaining -= n;
    }
    return out.toByteArray();
  } catch (IOException e) {
    return new byte[0];
  }
}



  /**
   * Read up to {@code maxBytes} bytes from a previously fetched String body.
   * This keeps older resolvers that expect a byte-limited body working.
   */
  public static byte[] readUpToBytes(String body, int maxBytes) {
    if (body == null || maxBytes <= 0) return new byte[0];
    byte[] all = body.getBytes(StandardCharsets.UTF_8);
    if (all.length <= maxBytes) return all;
    byte[] out = new byte[maxBytes];
    System.arraycopy(all, 0, out, 0, maxBytes);
    return out;
  }
}

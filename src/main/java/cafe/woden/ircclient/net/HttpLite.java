package cafe.woden.ircclient.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.zip.GZIPInputStream;

/**
 * Very small HTTP helper for GET requests that works with SOCKS proxies.
 *
 * <p>We intentionally use {@link HttpURLConnection} here (instead of {@code
 * java.net.http.HttpClient}) because the JDK HttpClient does not support SOCKS proxies.
 */
public final class HttpLite {

  public static final int DEFAULT_MAX_REDIRECTS = 5;

  private HttpLite() {}

  public static final class Headers {
    private final Map<String, List<String>> raw;

    public Headers(Map<String, List<String>> raw) {
      this.raw = raw;
    }

    public Map<String, List<String>> raw() {
      return raw;
    }

    public Optional<String> firstValue(String name) {
      if (name == null || name.isBlank()) return Optional.empty();
      String target = name.toLowerCase(Locale.ROOT);
      for (Map.Entry<String, List<String>> e : raw.entrySet()) {
        String k = e.getKey();
        if (k == null) continue;
        if (k.toLowerCase(Locale.ROOT).equals(target)) {
          List<String> vals = e.getValue();
          if (vals == null || vals.isEmpty()) return Optional.empty();
          return Optional.ofNullable(vals.getFirst());
        }
      }
      return Optional.empty();
    }

    public OptionalLong firstValueAsLong(String name) {
      Optional<String> v = firstValue(name);
      if (v.isEmpty()) return OptionalLong.empty();
      try {
        return OptionalLong.of(Long.parseLong(v.get().trim()));
      } catch (NumberFormatException ignored) {
        return OptionalLong.empty();
      }
    }
  }

  public record Response<T>(int statusCode, Headers headers, T body) {}

  public static Response<InputStream> getStream(
      URI uri,
      Map<String, String> requestHeaders,
      Proxy proxy,
      int connectTimeoutMs,
      int readTimeoutMs)
      throws IOException {
    return getStream(
        uri, requestHeaders, proxy, connectTimeoutMs, readTimeoutMs, DEFAULT_MAX_REDIRECTS);
  }

  public static Response<InputStream> getStream(
      URI uri,
      Map<String, String> requestHeaders,
      Proxy proxy,
      int connectTimeoutMs,
      int readTimeoutMs,
      int maxRedirects)
      throws IOException {
    URI current = uri;
    for (int i = 0; i <= maxRedirects; i++) {
      HttpURLConnection conn = open(current, proxy, connectTimeoutMs, readTimeoutMs);
      conn.setInstanceFollowRedirects(false);
      conn.setRequestMethod("GET");

      if (requestHeaders != null) {
        for (Map.Entry<String, String> e : requestHeaders.entrySet()) {
          if (e.getKey() != null && e.getValue() != null) {
            conn.setRequestProperty(e.getKey(), e.getValue());
          }
        }
      }

      int code = conn.getResponseCode();

      if (isRedirect(code)) {
        String loc = conn.getHeaderField("Location");
        // Ensure we don't leak the connection.
        closeQuietly(conn);
        if (loc == null || loc.isBlank()) {
          return new Response<>(
              code, new Headers(conn.getHeaderFields()), InputStream.nullInputStream());
        }
        current = current.resolve(loc);
        continue;
      }

      InputStream body = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
      if (body == null) body = InputStream.nullInputStream();

      String encoding = conn.getHeaderField("Content-Encoding");
      if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip")) {
        body = new GZIPInputStream(body);
      }

      return new Response<>(code, new Headers(conn.getHeaderFields()), body);
    }

    throw new IOException("Too many redirects for " + uri);
  }

  public static Response<String> getString(
      URI uri,
      Map<String, String> requestHeaders,
      Proxy proxy,
      int connectTimeoutMs,
      int readTimeoutMs)
      throws IOException {
    Response<InputStream> r =
        getStream(uri, requestHeaders, proxy, connectTimeoutMs, readTimeoutMs);
    byte[] bytes;
    try (InputStream in = r.body()) {
      bytes = in.readAllBytes();
    }
    Charset charset = charsetFromContentType(r.headers().firstValue("Content-Type").orElse(null));
    return new Response<>(r.statusCode(), r.headers(), new String(bytes, charset));
  }

  private static Charset charsetFromContentType(String contentType) {
    if (contentType == null) return StandardCharsets.UTF_8;
    String[] parts = contentType.split(";");
    for (String p : parts) {
      String s = p.trim().toLowerCase(Locale.ROOT);
      if (s.startsWith("charset=")) {
        String cs = s.substring("charset=".length()).trim();
        try {
          return Charset.forName(cs);
        } catch (Exception ignored) {
          return StandardCharsets.UTF_8;
        }
      }
    }
    return StandardCharsets.UTF_8;
  }

  private static boolean isRedirect(int code) {
    return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
  }

  private static HttpURLConnection open(
      URI uri, Proxy proxy, int connectTimeoutMs, int readTimeoutMs) throws IOException {
    URL url = uri.toURL();
    URLConnection uc =
        (proxy == null || proxy == Proxy.NO_PROXY)
            ? url.openConnection()
            : url.openConnection(proxy);
    // If the user has enabled \"trust all certificates\", apply the relaxed TLS settings
    // for HTTPS connections (used by link previews, image embeds, etc).
    if (NetTlsContext.trustAllCertificates()
        && uc instanceof javax.net.ssl.HttpsURLConnection https) {
      https.setSSLSocketFactory(NetTlsContext.sslSocketFactory());
      https.setHostnameVerifier(NetTlsContext.hostnameVerifier());
    }
    if (!(uc instanceof HttpURLConnection conn)) {
      throw new IOException("Not an HTTP URL: " + uri);
    }
    conn.setConnectTimeout(connectTimeoutMs);
    conn.setReadTimeout(readTimeoutMs);
    return conn;
  }

  private static void closeQuietly(HttpURLConnection conn) {
    try {
      InputStream in = conn.getInputStream();
      if (in != null) in.close();
    } catch (IOException ignored) {
      try {
        InputStream in = conn.getErrorStream();
        if (in != null) in.close();
      } catch (IOException ignored2) {
        // ignore
      }
    }
    conn.disconnect();
  }
}

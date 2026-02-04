package cafe.woden.ircclient.ui.chat.embed;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class PreviewHttp {

  static final Duration TIMEOUT = Duration.ofSeconds(8);

  // Keep UA realistic; some sites block unknown UAs.
  static final String USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";

  private final HttpClient client;

  PreviewHttp() {
    this(HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(TIMEOUT)
        .build());
  }

  PreviewHttp(HttpClient client) {
    this.client = client;
  }

  HttpResponse<InputStream> getStream(URI uri, String accept, Map<String, String> extraHeaders)
      throws Exception {
    HttpRequest req = request(uri, accept, extraHeaders).GET().build();
    return client.send(req, HttpResponse.BodyHandlers.ofInputStream());
  }

  HttpResponse<String> getString(URI uri, String accept, Map<String, String> extraHeaders)
      throws Exception {
    HttpRequest req = request(uri, accept, extraHeaders).GET().build();
    return client.send(req, HttpResponse.BodyHandlers.ofString());
  }

  HttpRequest.Builder request(URI uri, String accept, Map<String, String> extraHeaders) {
    HttpRequest.Builder b = HttpRequest.newBuilder(uri)
        .timeout(TIMEOUT)
        .header("User-Agent", USER_AGENT)
        .header("Accept-Language", ACCEPT_LANGUAGE);

    if (accept != null && !accept.isBlank()) {
      b.header("Accept", accept);
    }

    if (extraHeaders != null && !extraHeaders.isEmpty()) {
      for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
        if (e.getKey() == null || e.getKey().isBlank()) continue;
        if (e.getValue() == null) continue;
        b.header(e.getKey(), e.getValue());
      }
    }
    return b;
  }

  static Map<String, String> headers(String k1, String v1) {
    Map<String, String> m = new LinkedHashMap<>();
    if (k1 != null && v1 != null) m.put(k1, v1);
    return m;
  }

  static Map<String, String> headers(String k1, String v1, String k2, String v2) {
    Map<String, String> m = new LinkedHashMap<>();
    if (k1 != null && v1 != null) m.put(k1, v1);
    if (k2 != null && v2 != null) m.put(k2, v2);
    return m;
  }

  static Map<String, String> headers(String k1, String v1, String k2, String v2, String k3, String v3) {
    Map<String, String> m = new LinkedHashMap<>();
    if (k1 != null && v1 != null) m.put(k1, v1);
    if (k2 != null && v2 != null) m.put(k2, v2);
    if (k3 != null && v3 != null) m.put(k3, v3);
    return m;
  }

  static byte[] readUpTo(InputStream in, int maxBytes) throws Exception {
    if (in == null) return new byte[0];
    byte[] buf = new byte[Math.min(8192, Math.max(1024, maxBytes))];
    int total = 0;
    try (in) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
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

  static boolean looksLikeHtml(String contentType) {
    if (contentType == null || contentType.isBlank()) return true; // missing -> allow
    String lower = contentType.toLowerCase(Locale.ROOT);
    return lower.contains("text/html")
        || lower.contains("application/xhtml")
        || lower.contains("xml");
  }
}

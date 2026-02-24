package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;
import java.util.Locale;

final class RottenTomatoesPreviewUtil {

  private RottenTomatoesPreviewUtil() {}

  static boolean isRottenTomatoesTitleUrl(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      return isRottenTomatoesTitleUri(URI.create(url));
    } catch (Exception ignored) {
      return false;
    }
  }

  static boolean isRottenTomatoesTitleUri(URI uri) {
    if (uri == null) return false;

    String host = hostLower(uri);
    if (host == null || host.isBlank()) return false;
    if (!(host.equals("rottentomatoes.com") || host.endsWith(".rottentomatoes.com"))) return false;

    String path = uri.getPath() == null ? "" : uri.getPath();
    if (path.isBlank()) return false;

    // Movies: /m/<slug>
    if (path.startsWith("/m/")) return path.length() > 3;

    // TV: /tv/<slug> or /tv/<slug>/sXX
    if (path.startsWith("/tv/")) return path.length() > 4;

    return false;
  }

  static URI canonicalize(URI uri) {
    if (uri == null) return null;
    try {
      String scheme = uri.getScheme();
      String host = uri.getHost();
      String path = uri.getPath();
      if (host == null || path == null) return uri;

      // Force https and strip query/fragment.
      String h = host.toLowerCase(Locale.ROOT);
      if (h.startsWith("www.")) h = h.substring(4);
      return new URI("https", null, h, -1, path, null, null);
    } catch (Exception ignored) {
      return uri;
    }
  }

  private static String hostLower(URI uri) {
    String host = uri.getHost();
    if (host == null) return null;
    return host.toLowerCase(Locale.ROOT);
  }
}

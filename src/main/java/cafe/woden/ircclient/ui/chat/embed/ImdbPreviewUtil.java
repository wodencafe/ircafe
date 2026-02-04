package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small helpers for recognizing and canonicalizing IMDb title links. */
final class ImdbPreviewUtil {

  private ImdbPreviewUtil() {}

  
  private static final Pattern TITLE_ID = Pattern.compile("\\b(tt\\d{5,12})\\b", Pattern.CASE_INSENSITIVE);

  static boolean isImdbTitleUrl(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      return isImdbTitleUri(URI.create(url));
    } catch (Exception ignored) {
      return false;
    }
  }

  static boolean isImdbTitleUri(URI uri) {
    if (uri == null) return false;
    String host = hostLower(uri);
    if (host == null || host.isBlank()) return false;
    if (!(host.equals("imdb.com") || host.endsWith(".imdb.com"))) return false;
    String path = uri.getPath() == null ? "" : uri.getPath();
    return path.contains("/title/") && extractTitleId(uri) != null;
  }

  static String extractTitleId(URI uri) {
    if (uri == null) return null;
    String path = uri.getPath() == null ? "" : uri.getPath();
    Matcher m = TITLE_ID.matcher(path);
    if (m.find()) return m.group(1).toLowerCase(Locale.ROOT);
    // Sometimes the id is in the query (rare), try raw string as a last resort.
    String raw = uri.toString();
    m = TITLE_ID.matcher(raw);
    if (m.find()) return m.group(1).toLowerCase(Locale.ROOT);
    return null;
  }

  static URI canonicalTitleUri(String titleId) {
    if (titleId == null || titleId.isBlank()) return null;
    String id = titleId.strip().toLowerCase(Locale.ROOT);
    if (!id.startsWith("tt")) return null;
    return URI.create("https://www.imdb.com/title/" + id + "/");
  }

  static String yearFromDatePublished(String datePublished) {
    if (datePublished == null) return null;
    String t = datePublished.strip();
    if (t.length() >= 4) {
      String y = t.substring(0, 4);
      if (y.chars().allMatch(Character::isDigit)) return y;
    }
    return null;
  }

  static Duration parseIsoDuration(String iso8601) {
    if (iso8601 == null || iso8601.isBlank()) return null;
    try {
      // IMDb JSON-LD uses ISO-8601 durations like PT2H22M.
      return Duration.parse(iso8601.strip());
    } catch (Exception ignored) {
      return null;
    }
  }

  static String formatRuntime(Duration d) {
    if (d == null) return null;
    long seconds = Math.max(0, d.getSeconds());
    long minutes = seconds / 60;
    long hours = minutes / 60;
    long remMin = minutes % 60;

    if (hours <= 0) {
      if (minutes <= 0) return null;
      return minutes + "m";
    }
    if (remMin <= 0) return hours + "h";
    return hours + "h " + remMin + "m";
  }

  /**
   * IMDb posters are commonly hosted on Amazon's image CDN and the default URLs can be huge.
   * Amazon's CDN supports "sized" variants by inserting a token after "@._V1_".
   *
   * <p>Example:
   * <pre>
   * ...@._V1_.jpg  ->  ...@._V1_UX256_.jpg
   * </pre>
   *
   * <p>If the URL is already sized (contains UX/UY/etc immediately after @._V1_), the URL is
   * returned unchanged.
   */
  static String maybeSizeAmazonPosterUrl(String url, int widthPx) {
    if (url == null || url.isBlank()) return url;
    if (widthPx <= 0) return url;

    String lower = url.toLowerCase(Locale.ROOT);
    // Only touch Amazon image CDN URLs.
    if (!(lower.contains("media-amazon.com") || lower.contains("images-amazon.com"))) return url;

    String marker = "@._V1_";
    int idx = url.indexOf(marker);
    if (idx < 0) return url;

    String after = url.substring(idx + marker.length());
    // If already sized, leave it alone.
    if (after.startsWith("UX") || after.startsWith("UY") || after.startsWith("SX") || after.startsWith("SY")) {
      return url;
    }

    // Insert a width token. Amazon will serve a smaller JPEG for typical posters.
    return url.substring(0, idx) + marker + "UX" + widthPx + "_" + after;
  }

  private static String hostLower(URI uri) {
    String host = uri.getHost();
    if (host == null) return null;
    return host.toLowerCase(Locale.ROOT);
  }
}

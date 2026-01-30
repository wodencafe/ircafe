package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort extraction of normal web URLs from chat text.
 *
 * <p>We intentionally keep this permissive (like {@link ImageUrlExtractor}): the transcript already
 * shows the raw URL text; this is only used to decide if we should append a preview card.
 */
final class LinkUrlExtractor {

  // Keep in sync with ChatRichTextRenderer and ImageUrlExtractor.
  private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+|www\\.\\S+)");

  private LinkUrlExtractor() {}

  static List<String> extractUrls(String text) {
    if (text == null || text.isBlank()) return List.of();

    Matcher m = URL_PATTERN.matcher(text);
    Set<String> out = new LinkedHashSet<>();
    while (m.find()) {
      String raw = m.group(1);
      UrlParts parts = splitUrlTrailingPunct(raw);
      String url = ChatRichTextRenderer.normalizeUrl(parts.url);
      if (isLikelyHttpUrl(url) && !looksLikeDirectImage(url)) {
        out.add(url);
      }
    }
    return new ArrayList<>(out);
  }

  private static boolean isLikelyHttpUrl(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme();
      if (scheme == null) return false;
      scheme = scheme.toLowerCase(Locale.ROOT);
      return scheme.equals("http") || scheme.equals("https");
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean looksLikeDirectImage(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      URI uri = URI.create(url);
      String path = uri.getPath();
      if (path == null) return false;
      String p = path.toLowerCase(Locale.ROOT);
      return p.endsWith(".png")
          || p.endsWith(".jpg")
          || p.endsWith(".jpeg")
          || p.endsWith(".gif")
          || p.endsWith(".webp");
    } catch (Exception ignored) {
      return false;
    }
  }

  /**
   * Strip common trailing punctuation that tends to cling to URLs in chat.
   */
  private static UrlParts splitUrlTrailingPunct(String raw) {
    if (raw == null || raw.isEmpty()) return new UrlParts("", "");
    int end = raw.length();
    while (end > 0) {
      char c = raw.charAt(end - 1);
      // Common in chat: https://example.com), https://example.com\" , <https://example.com>
      if (c == '.'
          || c == ','
          || c == ')'
          || c == ']'
          || c == '}'
          || c == '>'
          || c == '!'
          || c == '?'
          || c == ';'
          || c == ':'
          || c == '\''
          || c == '"') {
        end--;
      } else {
        break;
      }
    }
    if (end == raw.length()) return new UrlParts(raw, "");
    return new UrlParts(raw.substring(0, end), raw.substring(end));
  }

  private record UrlParts(String url, String trailing) {}
}

package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Best-effort Wikipedia article enrichment.
 *
 * <p>We call Wikipedia's REST Summary endpoint to get a longer extract than OG tags typically provide.
 * This keeps previews compact while still feeling "Wikipedia-aware".
 */
final class WikipediaPreviewUtil {

  private WikipediaPreviewUtil() {}

  static boolean isWikipediaArticleUrl(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      return isWikipediaArticleUri(URI.create(url));
    } catch (Exception ignored) {
      return false;
    }
  }

  static boolean isWikipediaArticleUri(URI uri) {
    if (uri == null) return false;
    String host = safeLower(uri.getHost());
    if (host == null || !host.endsWith("wikipedia.org")) return false;
    String path = uri.getPath();
    return path != null && path.startsWith("/wiki/") && path.length() > "/wiki/".length();
  }

  static URI toSummaryApiUri(URI articleUri) {
    if (articleUri == null) return null;
    if (!isWikipediaArticleUri(articleUri)) return null;

    String host = normalizeWikipediaHost(articleUri.getHost());
    if (host == null || host.isBlank()) host = "en.wikipedia.org";

    String title = extractTitle(articleUri);
    if (title == null || title.isBlank()) return null;

    String encodedTitle = encodePathSegment(title);
    return URI.create("https://" + host + "/api/rest_v1/page/summary/" + encodedTitle);
  }

  static LinkPreview parseSummaryJson(String json, URI originalArticleUri) {
    if (json == null || json.isBlank()) return null;

    // Some error payloads still contain these keys, so we require at least title+extract.
    String title = MiniJson.findString(json, "title");
    String extract = MiniJson.findString(json, "extract");
    if (title == null || title.isBlank() || extract == null || extract.isBlank()) return null;

    String imageUrl = null;
    String thumbObj = MiniJson.findObject(json, "thumbnail");
    if (thumbObj != null) {
      imageUrl = MiniJson.findString(thumbObj, "source");
    }

    String pageUrl = null;
    String contentUrls = MiniJson.findObject(json, "content_urls");
    if (contentUrls != null) {
      String desktop = MiniJson.findObject(contentUrls, "desktop");
      if (desktop != null) {
        pageUrl = MiniJson.findString(desktop, "page");
      }
    }

    if (pageUrl == null || pageUrl.isBlank()) {
      pageUrl = originalArticleUri != null ? originalArticleUri.toString() : null;
    }

    // Keep the extract reasonably sized; the UI will clamp to lines and prefer sentence boundaries.
    String desc = PreviewTextUtil.trimToSentence(extract, 4000);

    return new LinkPreview(pageUrl, title, desc, "Wikipedia", imageUrl);
  }

  // (No trim helper here: PreviewTextUtil handles sentence-aware trimming.)

  private static String normalizeWikipediaHost(String host) {
    if (host == null) return null;
    String h = host.toLowerCase(Locale.ROOT).trim();
    // m.wikipedia.org and xx.m.wikipedia.org should map to wikipedia.org / xx.wikipedia.org.
    if (h.endsWith(".m.wikipedia.org")) {
      h = h.replace(".m.wikipedia.org", ".wikipedia.org");
    } else if (h.equals("m.wikipedia.org")) {
      h = "wikipedia.org";
    }
    return h;
  }

  private static String extractTitle(URI articleUri) {
    String path = articleUri.getPath();
    if (path == null || !path.startsWith("/wiki/")) return null;
    String seg = path.substring("/wiki/".length());
    if (seg.isBlank()) return null;
    // Decode percent-encoded UTF-8, then convert underscores to spaces.
    String decoded = URLDecoder.decode(seg, StandardCharsets.UTF_8);
    return decoded.replace('_', ' ').trim();
  }

  private static String encodePathSegment(String s) {
    // URLEncoder is for form encoding; but it's still handy if we fix the "space => +" behavior.
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static String safeLower(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t.toLowerCase(Locale.ROOT);
  }

  /**
   * Tiny JSON helper for Wikipedia's predictable summary payload.
   *
   * <p>This is intentionally minimal (no arrays, no numbers) because we only need a few string keys.
   */
  static final class MiniJson {

    private MiniJson() {}

    static String findString(String json, String key) {
      if (json == null || key == null) return null;
      int i = indexOfKey(json, key);
      while (i >= 0) {
        int colon = skipToValueStart(json, i + key.length() + 2);
        if (colon < 0) return null;
        int p = skipWs(json, colon);
        if (p < json.length() && json.charAt(p) == '"') {
          return parseString(json, p);
        }
        i = indexOfKey(json, key, i + key.length() + 2);
      }
      return null;
    }

    static String findObject(String json, String key) {
      if (json == null || key == null) return null;
      int i = indexOfKey(json, key);
      while (i >= 0) {
        int colon = skipToValueStart(json, i + key.length() + 2);
        if (colon < 0) return null;
        int p = skipWs(json, colon);
        if (p < json.length() && json.charAt(p) == '{') {
          return captureObject(json, p);
        }
        i = indexOfKey(json, key, i + key.length() + 2);
      }
      return null;
    }

    private static int indexOfKey(String json, String key) {
      return indexOfKey(json, key, 0);
    }

    private static int indexOfKey(String json, String key, int from) {
      String needle = "\"" + key + "\"";
      return json.indexOf(needle, Math.max(0, from));
    }

    private static int skipToValueStart(String json, int afterKeyQuote) {
      // Find ':' after the key.
      int p = afterKeyQuote;
      while (p < json.length()) {
        char c = json.charAt(p);
        if (c == ':') return p + 1;
        p++;
      }
      return -1;
    }

    private static int skipWs(String s, int i) {
      int p = Math.max(0, i);
      while (p < s.length()) {
        char c = s.charAt(p);
        if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
        p++;
      }
      return p;
    }

    private static String parseString(String json, int quotePos) {
      // quotePos must point at opening '"'
      StringBuilder out = new StringBuilder();
      int i = quotePos + 1;
      while (i < json.length()) {
        char c = json.charAt(i);
        if (c == '"') {
          return out.toString();
        }
        if (c == '\\') {
          if (i + 1 >= json.length()) break;
          char e = json.charAt(i + 1);
          switch (e) {
            case '"' -> out.append('"');
            case '\\' -> out.append('\\');
            case '/' -> out.append('/');
            case 'b' -> out.append('\b');
            case 'f' -> out.append('\f');
            case 'n' -> out.append('\n');
            case 'r' -> out.append('\r');
            case 't' -> out.append('\t');
            case 'u' -> {
              if (i + 6 <= json.length()) {
                String hex = json.substring(i + 2, i + 6);
                try {
                  out.append((char) Integer.parseInt(hex, 16));
                } catch (Exception ignored) {
                  // ignore bad unicode escapes
                }
                i += 4;
              }
            }
            default -> out.append(e);
          }
          i += 2;
          continue;
        }
        out.append(c);
        i++;
      }
      return out.toString();
    }

    private static String captureObject(String json, int bracePos) {
      int depth = 0;
      boolean inString = false;
      boolean escaped = false;
      for (int i = bracePos; i < json.length(); i++) {
        char c = json.charAt(i);

        if (inString) {
          if (escaped) {
            escaped = false;
          } else if (c == '\\') {
            escaped = true;
          } else if (c == '"') {
            inString = false;
          }
          continue;
        }

        if (c == '"') {
          inString = true;
          continue;
        }

        if (c == '{') depth++;
        if (c == '}') {
          depth--;
          if (depth == 0) {
            return json.substring(bracePos, i + 1);
          }
        }
      }
      return null;
    }
  }
}

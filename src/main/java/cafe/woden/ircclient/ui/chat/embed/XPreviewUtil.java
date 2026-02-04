package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

final class XPreviewUtil {

  private XPreviewUtil() {}

  static boolean isXStatusUrl(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      return extractStatusId(URI.create(url)) != null;
    } catch (Exception ignored) {
      return false;
    }
  }

  static boolean isXStatusUri(URI uri) {
    return extractStatusId(uri) != null;
  }

  

static boolean isXLikeHost(String host) {
  if (host == null) return false;
  String h = host.toLowerCase(Locale.ROOT);
  if (h.endsWith("twitter.com") || h.endsWith("x.com")) return true;
  // Common unfurl proxies (domain-swap services)
  if (h.endsWith("fixupx.com") || h.endsWith("fxtwitter.com") || h.endsWith("vxtwitter.com") || h.endsWith("fixvx.com")) return true;
  // Nitter instances typically use nitter.<domain> or nitter.net
  if (h.equals("nitter.net") || h.startsWith("nitter.")) return true;
  return false;
}

static String extractStatusId(String url) {
    if (url == null || url.isBlank()) return null;
    try {
      return extractStatusId(URI.create(url));
    } catch (Exception ignored) {
      return null;
    }
  }

  static String extractStatusId(URI uri) {
    if (uri == null) return null;
    String host = hostLower(uri);
    if (host == null) return null;
    if (!(host.equals("x.com") || host.endsWith(".x.com")
        || host.equals("twitter.com") || host.endsWith(".twitter.com"))) {
      return null;
    }

    String path = uri.getPath() == null ? "" : uri.getPath();
    // Common patterns:
    //   /{user}/status/{id}
    //   /i/web/status/{id}
    //   /{user}/status/{id}/photo/1
    String id = pathSegmentAfter(path, "/status/");
    if (id == null) id = pathSegmentAfter(path, "/statuses/");
    if (id == null) id = pathSegmentAfter(path, "/i/web/status/");
    if (id == null) id = pathSegmentAfter(path, "/i/status/");
    id = cleanNumeric(id);
    return id;
  }

  static URI syndicationApiUri(String statusId) {
    if (statusId == null || statusId.isBlank()) return null;
    // Public syndication JSON used by embedded tweets.
    // Keep lang=en for stable formatting; this is only used for metadata.
    // As of 2025+, many clients include a `token` parameter. It appears to be lightly validated
    // (and sometimes not at all), but including it increases reliability.
    //
    // We mirror the token formula used by react-tweet (Vercel):
    //   ((Number(id) / 1e15) * Math.PI).toString(36).replace(/(0+|\.)/g, '')
    //
    // If we fail to compute it for any reason, fall back to a simple constant.
    String enc = URLEncoder.encode(statusId, StandardCharsets.UTF_8);
    String token = safeToken(statusId);
    String tEnc = URLEncoder.encode(token, StandardCharsets.UTF_8);
    return URI.create("https://cdn.syndication.twimg.com/tweet-result?id=" + enc + "&token=" + tEnc + "&lang=en");
  }

  
  static URI oEmbedApiUri(String statusId) {
    if (statusId == null || statusId.isBlank()) return null;

    // Use a canonical tweet URL that doesn't require knowing the handle.
    String tweetUrl = "https://x.com/i/web/status/" + statusId;
    String enc = URLEncoder.encode(tweetUrl, StandardCharsets.UTF_8);
    return URI.create(
        "https://publish.x.com/oembed?url=" + enc + "&omit_script=true&dnt=true");
  }

  /** Parse oEmbed JSON into a regular {@link LinkPreview}. */
  static LinkPreview parseOEmbedJson(String json, URI originalStatusUri, String statusId) {
    if (json == null || json.isBlank()) return null;
    if (originalStatusUri == null) return null;

    try {
      String authorName = MiniJson.findString(json, "author_name");
      String authorUrl = MiniJson.findString(json, "author_url");
      String htmlRaw = MiniJson.findString(json, "html");
      String thumb = MiniJson.findString(json, "thumbnail_url");

      String handle = extractHandleFromAuthorUrl(authorUrl);
      String title = buildAuthorTitle(authorName, handle);

      String tweetText = null;
      if (htmlRaw != null && !htmlRaw.isBlank()) {
        String html = unescapeJsonString(htmlRaw);
        Document doc = Jsoup.parse(html);
        Element p = doc.selectFirst("p");
        if (p != null) tweetText = p.text();
        if (tweetText == null || tweetText.isBlank()) tweetText = doc.text();
      }

      String desc = (tweetText == null || tweetText.isBlank()) ? null : tweetText.strip();
      if (desc != null && desc.length() > 900) {
        // Keep runaway embeds reasonable; UI will clamp lines anyway.
        desc = PreviewTextUtil.trimToSentence(desc, 900);
      }

      // Provider name is not always present; normalize to X.
      String siteName = "X";

      String url = originalStatusUri.toString();
      if (title == null || title.isBlank()) {
        title = "X post";
        if (handle != null) title = title + " (@" + handle + ")";
      }

      return new LinkPreview(url, title, desc, siteName, thumb, thumb != null ? 1 : 0);
    } catch (Exception ignored) {
      return null;
    }
  }
  private static String extractHandleFromAuthorUrl(String authorUrl) {
    if (authorUrl == null || authorUrl.isBlank()) return null;
    try {
      URI u = URI.create(authorUrl);
      String path = u.getPath();
      if (path == null || path.isBlank()) return null;
      String p = path;
      while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
      int idx = p.lastIndexOf('/');
      String seg = idx >= 0 ? p.substring(idx + 1) : p;
      seg = seg.strip();
      if (seg.isBlank()) return null;
      // Handles can contain underscores; keep it permissive.
      return seg;
    } catch (Exception ignored) {
      return null;
    }
  }

  
  private static String unescapeJsonString(String s) {
    if (s == null) return null;
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c != '\\' || i + 1 >= s.length()) {
        out.append(c);
        continue;
      }

      char n = s.charAt(++i);
      switch (n) {
        case '"' -> out.append('"');
        case '\\' -> out.append('\\');
        case '/' -> out.append('/');
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'u' -> {
          if (i + 4 < s.length()) {
            String hex = s.substring(i + 1, i + 5);
            try {
              out.append((char) Integer.parseInt(hex, 16));
              i += 4;
            } catch (NumberFormatException ex) {
              out.append('u');
            }
          } else {
            out.append('u');
          }
        }
        default -> out.append(n);
      }
    }
    return out.toString();
  }

  private static String safeToken(String statusId) {
    try {
      // We only need a *string* token. The endpoint appears tolerant, but we compute one anyway.
      double id = Double.parseDouble(statusId);
      double n = (id / 1.0e15d) * Math.PI;
      return tokenFromNumber(n);
    } catch (Exception ignored) {
      return "a";
    }
  }

  private static String tokenFromNumber(double n) {
    if (!(n > 0)) return "a";

    long whole = (long) Math.floor(n);
    double frac = n - whole;

    StringBuilder sb = new StringBuilder();
    sb.append(Long.toString(whole, 36));
    if (frac > 0) {
      sb.append('.');
      // Generate a modest number of fractional digits; the token is not security-critical.
      for (int i = 0; i < 18 && frac > 0; i++) {
        frac *= 36.0d;
        int digit = (int) Math.floor(frac + 1e-12);
        if (digit < 0) digit = 0;
        if (digit > 35) digit = 35;
        sb.append(Character.forDigit(digit, 36));
        frac -= digit;
      }
    }

    // Remove zeros and dots.
    String s = sb.toString();
    s = s.replace(".", "").replace("0", "");
    return s.isBlank() ? "a" : s;
  }

  static LinkPreview parseSyndicationJson(String json, URI originalStatusUri) {
    if (json == null || json.isBlank()) return null;

    String text = firstNonBlank(
        MiniJson.findString(json, "text"),
        MiniJson.findString(json, "full_text"),
        MiniJson.findString(json, "raw_text")
    );

    String userObj = MiniJson.findObject(json, "user");
    String name = userObj != null ? MiniJson.findString(userObj, "name") : null;
    String handle = userObj != null ? MiniJson.findString(userObj, "screen_name") : null;
    String avatar = userObj != null ? firstNonBlank(
        MiniJson.findString(userObj, "profile_image_url_https"),
        MiniJson.findString(userObj, "profile_image_url")
    ) : null;

    Long likes = MiniJson.findLong(json, "favorite_count");
    Long reposts = firstNonBlankLong(
        MiniJson.findLong(json, "retweet_count"),
        MiniJson.findLong(json, "repost_count")
    );
    Long replies = MiniJson.findLong(json, "reply_count");
    Long quotes = MiniJson.findLong(json, "quote_count");

    // Media: try photos[] first, then mediaDetails[], then video.poster.
    String mediaUrl = null;

    String photosArr = MiniJson.findArray(json, "photos");
    if (photosArr != null) {
      String firstObj = MiniJson.firstObjectInArray(photosArr);
      if (firstObj != null) {
        mediaUrl = firstNonBlank(
            MiniJson.findString(firstObj, "url"),
            MiniJson.findString(firstObj, "backgroundImage")
        );
      }
    }

    if (mediaUrl == null) {
      String mediaArr = MiniJson.findArray(json, "mediaDetails");
      if (mediaArr != null) {
        String firstObj = MiniJson.firstObjectInArray(mediaArr);
        if (firstObj != null) {
          mediaUrl = firstNonBlank(
              MiniJson.findString(firstObj, "media_url_https"),
              MiniJson.findString(firstObj, "media_url"),
              MiniJson.findString(firstObj, "url"),
              MiniJson.findString(firstObj, "preview_url")
          );
        }
      }
    }

    if (mediaUrl == null) {
      String videoObj = MiniJson.findObject(json, "video");
      if (videoObj != null) {
        mediaUrl = firstNonBlank(
            MiniJson.findString(videoObj, "poster"),
            MiniJson.findString(videoObj, "preview_url")
        );
      }
    }

    if (mediaUrl == null) {
      // Fallback to avatar so X cards still look "rich".
      mediaUrl = avatar;
    }

    String title = buildAuthorTitle(name, handle);

    String details = buildDetailsLine(likes, reposts, replies, quotes);
    String body = text != null ? PreviewTextUtil.trimToSentence(text, 1200) : null;
    String desc = joinLines(details, body);

    String id = originalStatusUri != null ? extractStatusId(originalStatusUri) : null;
    String url = buildCanonicalUrl(handle, id, originalStatusUri);

    // siteName: keep "X" so the UI looks neat.
    return new LinkPreview(url, title, desc, "X", mediaUrl, mediaUrl != null ? 1 : 0);
  }

  private static String buildCanonicalUrl(String handle, String id, URI fallback) {
    if (id != null && !id.isBlank()) {
      if (handle != null && !handle.isBlank()) {
        return "https://x.com/" + handle + "/status/" + id;
      }
      return "https://x.com/i/web/status/" + id;
    }
    return fallback != null ? fallback.toString() : null;
  }

  private static String buildAuthorTitle(String name, String handle) {
    String n = safe(name);
    String h = safe(handle);
    if (n != null && h != null) return n + " (@" + h + ")";
    if (h != null) return "@" + h;
    return n;
  }

  private static String buildDetailsLine(Long likes, Long reposts, Long replies, Long quotes) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    if (likes != null && likes >= 0) {
      sb.append(formatCount(likes)).append(" likes");
      first = false;
    }
    if (reposts != null && reposts >= 0) {
      if (!first) sb.append(" • ");
      sb.append(formatCount(reposts)).append(" reposts");
      first = false;
    }
    if (replies != null && replies >= 0) {
      if (!first) sb.append(" • ");
      sb.append(formatCount(replies)).append(" replies");
      first = false;
    }
    if (quotes != null && quotes >= 0) {
      if (!first) sb.append(" • ");
      sb.append(formatCount(quotes)).append(" quotes");
      first = false;
    }
    return sb.toString();
  }

  private static String formatCount(long n) {
    if (n < 0) return null;
    // compact-ish formatting: 1234 -> 1.2K
    if (n >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fB", n / 1_000_000_000d).replace(".0", "");
    if (n >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000d).replace(".0", "");
    if (n >= 1_000L) return String.format(Locale.ROOT, "%.1fK", n / 1_000d).replace(".0", "");
    return NumberFormat.getIntegerInstance(Locale.ROOT).format(n);
  }

  private static String joinLines(String a, String b) {
    String aa = safe(a);
    String bb = safe(b);
    if (aa == null) return bb;
    if (bb == null) return aa;
    return aa + "\n" + bb;
  }

  private static String safe(String s) {
    if (s == null) return null;
    String t = s.strip();
    return t.isEmpty() ? null : t;
  }

  private static String hostLower(URI uri) {
    if (uri == null) return null;
    String h = uri.getHost();
    if (h == null) return null;
    String t = h.trim();
    return t.isEmpty() ? null : t.toLowerCase(Locale.ROOT);
  }

  private static String pathSegmentAfter(String path, String marker) {
    if (path == null || marker == null) return null;
    int i = path.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
    if (i < 0) return null;
    String rest = path.substring(i + marker.length());
    int slash = rest.indexOf('/');
    return slash >= 0 ? rest.substring(0, slash) : rest;
  }

  private static String cleanNumeric(String s) {
    if (s == null) return null;
    String t = s.strip();
    if (t.isEmpty()) return null;
    // Strip query fragments if any accidentally got included.
    int q = t.indexOf('?');
    if (q >= 0) t = t.substring(0, q);
    int h = t.indexOf('#');
    if (h >= 0) t = t.substring(0, h);
    // Numeric only.
    int end = 0;
    while (end < t.length() && Character.isDigit(t.charAt(end))) end++;
    if (end == 0) return null;
    return t.substring(0, end);
  }

  private static String firstNonBlank(String... s) {
    if (s == null) return null;
    for (String v : s) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  private static Long firstNonBlankLong(Long... v) {
    if (v == null) return null;
    for (Long x : v) {
      if (x != null) return x;
    }
    return null;
  }

  static final class MiniJson {
    private MiniJson() {}

    static String findString(String json, String key) {
      if (json == null || key == null) return null;
      int i = indexOfKey(json, key, 0);
      while (i >= 0) {
        int p = skipToValue(json, i + key.length() + 2);
        if (p < 0) return null;
        p = skipWs(json, p);
        if (p < json.length() && json.charAt(p) == '"') {
          return parseString(json, p);
        }
        i = indexOfKey(json, key, i + key.length() + 2);
      }
      return null;
    }

    static Long findLong(String json, String key) {
      if (json == null || key == null) return null;
      int i = indexOfKey(json, key, 0);
      while (i >= 0) {
        int p = skipToValue(json, i + key.length() + 2);
        if (p < 0) return null;
        p = skipWs(json, p);
        int end = p;
        if (end < json.length() && (json.charAt(end) == '-' || Character.isDigit(json.charAt(end)))) {
          end++;
          while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
          try {
            return Long.parseLong(json.substring(p, end));
          } catch (Exception ignored) {
            return null;
          }
        }
        i = indexOfKey(json, key, i + key.length() + 2);
      }
      return null;
    }

    static String findObject(String json, String key) {
      if (json == null || key == null) return null;
      int i = indexOfKey(json, key, 0);
      while (i >= 0) {
        int p = skipToValue(json, i + key.length() + 2);
        if (p < 0) return null;
        p = skipWs(json, p);
        if (p < json.length() && json.charAt(p) == '{') {
          return captureBalanced(json, p, '{', '}');
        }
        i = indexOfKey(json, key, i + key.length() + 2);
      }
      return null;
    }

    static String findArray(String json, String key) {
      if (json == null || key == null) return null;
      int i = indexOfKey(json, key, 0);
      while (i >= 0) {
        int p = skipToValue(json, i + key.length() + 2);
        if (p < 0) return null;
        p = skipWs(json, p);
        if (p < json.length() && json.charAt(p) == '[') {
          return captureBalanced(json, p, '[', ']');
        }
        i = indexOfKey(json, key, i + key.length() + 2);
      }
      return null;
    }

    static String firstObjectInArray(String arrayJson) {
      if (arrayJson == null) return null;
      int p = arrayJson.indexOf('{');
      if (p < 0) return null;
      return captureBalanced(arrayJson, p, '{', '}');
    }

    private static int indexOfKey(String json, String key, int from) {
      String needle = "\"" + key + "\"";
      return json.indexOf(needle, Math.max(0, from));
    }

    private static int skipToValue(String json, int from) {
      int p = Math.max(0, from);
      while (p < json.length()) {
        if (json.charAt(p) == ':') return p + 1;
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
      StringBuilder out = new StringBuilder();
      int i = quotePos + 1;
      while (i < json.length()) {
        char c = json.charAt(i);
        if (c == '"') return out.toString();
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

    private static String captureBalanced(String s, int start, char open, char close) {
      int depth = 0;
      boolean inStr = false;
      boolean esc = false;
      for (int i = start; i < s.length(); i++) {
        char c = s.charAt(i);
        if (inStr) {
          if (esc) {
            esc = false;
          } else if (c == '\\') {
            esc = true;
          } else if (c == '"') {
            inStr = false;
          }
          continue;
        }
        if (c == '"') {
          inStr = true;
          continue;
        }
        if (c == open) depth++;
        if (c == close) {
          depth--;
          if (depth == 0) {
            return s.substring(start, i + 1);
          }
        }
      }
      return null;
    }
  }

static java.util.List<URI> proxyUnfurlCandidates(URI originalStatusUri) {
  if (originalStatusUri == null) return java.util.List.of();
  String id = extractStatusId(originalStatusUri);
  if (id == null) return java.util.List.of();

  String handle = extractHandle(originalStatusUri);
  String preferredPath = (handle != null)
      ? "/" + handle + "/status/" + id
      : "/i/status/" + id;

  java.util.ArrayList<URI> out = new java.util.ArrayList<>();

  // 1) Nitter instances (best-effort; many are intermittent).
  // Keep list short to avoid slow fallbacks.
  for (String base : NITTER_BASES) {
    tryAdd(out, base + preferredPath);
    if (handle != null) {
      // Some instances accept /{user}/status/{id}/photo/1 formats in shared links; normalize.
      tryAdd(out, base + "/" + handle + "/status/" + id);
    } else {
      tryAdd(out, base + "/i/status/" + id);
    }
  }

  // 2) Domain-swap embed proxies (often more reliable than Nitter and provide full OG tags).
  // These are third-party services; if unavailable, we'll just fall back to OG parsing.
  for (String base : UNFURL_PROXY_BASES) {
    tryAdd(out, base + preferredPath);
    if (handle != null) tryAdd(out, base + "/" + handle + "/status/" + id);
  }

  return out;
}

private static final String[] NITTER_BASES = {
    "https://nitter.net",
    "https://nitter.poast.org",
    "https://nitter.batsense.net",
    "https://nitter.blahaj.land",
    "https://nitter.cabletemple.net"
};

private static final String[] UNFURL_PROXY_BASES = {
    "https://fixupx.com",
    "https://fxtwitter.com",
    "https://vxtwitter.com"
};

static String extractHandle(URI uri) {
  try {
    if (uri == null) return null;
    String path = uri.getPath();
    if (path == null) return null;
    int idx = path.indexOf("/status/");
    if (idx <= 1) return null;
    // path like /{handle}/status/{id}
    String before = path.substring(1, idx);
    if (before.isBlank()) return null;
    // guard against /i/web/status and other special routes
    if (before.equals("i") || before.equals("intent") || before.equals("home")) return null;
    // strip any extra segments if present
    int slash = before.indexOf('/');
    if (slash > 0) before = before.substring(0, slash);
    return before;
  } catch (Exception ignored) {
    return null;
  }
}

private static void tryAdd(java.util.List<URI> out, String raw) {
  try {
    if (raw == null || raw.isBlank()) return;
    out.add(URI.create(raw));
  } catch (Exception ignored) {
    // ignore bad URIs
  }
}

}

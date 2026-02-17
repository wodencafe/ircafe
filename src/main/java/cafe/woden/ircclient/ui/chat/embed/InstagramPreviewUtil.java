package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Instagram preview parsing.
 *
 * <p>Instagram is hostile to scraping and often serves login/consent interstitial pages. This
 * helper tries multiple extraction strategies and keeps the result stable and short.
 */
final class InstagramPreviewUtil {

  private InstagramPreviewUtil() {}

  private static final Pattern IG_POST_PATH = Pattern.compile("^/(p|reel|tv)/[^/]+/?$", Pattern.CASE_INSENSITIVE);

  // Example title pattern: "A post shared by Name (@handle) on Instagram".
  private static final Pattern SHARED_BY = Pattern.compile("A post shared by\\s+(.+?)\\s+\\(@([A-Za-z0-9_.]+)\\)",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern ON_INSTAGRAM = Pattern.compile("\\(@([A-Za-z0-9_.]+)\\)\\s+on\\s+Instagram",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern SIMPLE_ON_INSTAGRAM =
      Pattern.compile("([A-Za-z0-9._]+)\\s+on\\s+Instagram", Pattern.CASE_INSENSITIVE);
  private static final Pattern CAPTION_AFTER_INSTAGRAM =
      Pattern.compile("on\\s+Instagram:\\s*[\"'“”]?(.+?)[\"'“”]?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LIKES_COMMENTS_PREFIX =
      Pattern.compile("^[\\d.,]+\\s+likes?(?:\\s*,\\s*[\\d.,]+\\s+comments?)?\\s*-\\s*", Pattern.CASE_INSENSITIVE);
  private static final Pattern LONG_FIELD =
      Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*([0-9]{6,})");
  private static final Pattern JSON_SRC_FIELD =
      Pattern.compile("\"src\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
  private static final Pattern EDGE_CAPTION_TEXT_FIELD =
      Pattern.compile("\"edge_media_to_caption\"\\s*:\\s*\\{.*?\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern CAPTION_FIELD =
      Pattern.compile("\"caption\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern EDGE_CAPTION_TEXT_FALLBACK_FIELD =
      Pattern.compile("\"edge_media_to_caption_text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern ACCESSIBILITY_CAPTION_FIELD =
      Pattern.compile("\"accessibility_caption\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
          Pattern.CASE_INSENSITIVE);

  static boolean isInstagramPostUrl(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      return isInstagramPostUri(URI.create(url));
    } catch (Exception ignored) {
      return false;
    }
  }

  static boolean isInstagramPostUri(URI uri) {
    if (uri == null) return false;
    String host = uri.getHost();
    if (host == null) return false;
    String h = host.toLowerCase(Locale.ROOT);
    if (h.startsWith("www.")) h = h.substring(4);
    if (!(h.equals("instagram.com") || h.endsWith(".instagram.com") || h.equals("instagr.am"))) return false;

    String path = uri.getPath();
    if (path == null) return false;
    // Allow trailing slash.
    String p = path.strip();
    if (p.isEmpty()) return false;
    if (!p.endsWith("/")) p = p + "/";

    // Only match canonical-ish post URLs.
    // (Embed endpoints are handled as alternates by the resolver.)
    return IG_POST_PATH.matcher(p.substring(0, p.length() - 1)).matches();
  }

  static LinkPreview parsePostDocument(Document doc, String originalUrl) {
    if (doc == null) {
      return new LinkPreview(originalUrl, "Instagram post", null, "Instagram", null, 0);
    }

    // Use the normal OG parser as a baseline.
    LinkPreview base = LinkPreviewParser.parse(doc, originalUrl);

    String canonicalUrl = firstNonBlank(base.url(), originalUrl);

    // Pull LD+JSON (sometimes present on the canonical post HTML).
    String ldJson = extractLdJson(doc);
    // Pull script JSON payloads used by Instagram web app.
    String postJson = extractPostJson(doc);

    String author = extractAuthor(ldJson, postJson, base);
    LocalDate date = extractDate(ldJson, postJson, doc);

    // Title: prefer "@handle" when we have it; otherwise keep it generic.
    String title = firstNonBlank(
        author != null ? "Instagram post by @" + author : null,
        cleanTitle(base.title()),
        "Instagram post"
    );

    // Caption/description: try LD+JSON first, then OG description/title heuristics.
    String caption = extractCaption(ldJson, postJson, base);
    caption = cleanCaption(caption);

    // Image: try LD+JSON, then OG image, then scan <img> tags (useful for embed pages).
    String image = firstNonBlank(
        resolveAgainst(canonicalUrl, extractImage(ldJson, postJson)),
        resolveAgainst(canonicalUrl, base.imageUrl()),
        resolveAgainst(canonicalUrl, bestImageFromDoc(doc))
    );

    String description = buildDescription(author, date, caption);

    int mediaCount = (image != null && !image.isBlank()) ? 1 : 0;
    return new LinkPreview(
        canonicalUrl,
        title,
        description,
        "Instagram",
        image,
        mediaCount
    );
  }

  private static String extractLdJson(Document doc) {
    try {
      for (Element el : doc.select("script[type=application/ld+json]")) {
        String txt = el.data();
        if (txt == null || txt.isBlank()) txt = el.html();
        if (txt == null || txt.isBlank()) continue;
        // Heuristic: look for Instagram post-ish JSON.
        String lower = txt.toLowerCase(Locale.ROOT);
        if (lower.contains("@type") && (lower.contains("imageobject") || lower.contains("socialmediapost") || lower.contains("datepublished"))) {
          return txt;
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private static String extractPostJson(Document doc) {
    if (doc == null) return null;
    String best = null;
    int bestScore = Integer.MIN_VALUE;
    try {
      for (Element el : doc.select("script")) {
        String txt = el.data();
        if (txt == null || txt.isBlank()) txt = el.html();
        if (txt == null || txt.isBlank()) continue;
        String lower = txt.toLowerCase(Locale.ROOT);
        int score = 0;
        if (lower.contains("xdt_shortcode_media")) score += 12;
        if (lower.contains("shortcode_media")) score += 10;
        if (lower.contains("edge_media_to_caption")) score += 8;
        if (lower.contains("taken_at_timestamp")) score += 6;
        if (lower.contains("display_url")) score += 6;
        if (lower.contains("\"owner\"")) score += 4;
        if (score <= 0) continue;
        if (txt.length() > 600) score += 2;
        if (score > bestScore) {
          bestScore = score;
          best = txt;
        }
      }
    } catch (Exception ignored) {
    }
    return best;
  }

  private static String extractImage(String ldJson, String postJson) {
    String fromLd = extractImageFromLd(ldJson);
    if (fromLd != null) return fromLd;
    return extractImageFromPostJson(postJson);
  }

  private static String extractImageFromLd(String ldJson) {
    if (ldJson == null || ldJson.isBlank()) return null;

    // Common LD shapes:
    // - image: "https://...jpg"
    // - image: {"url": "https://..."}
    // - image: [{"url": "..."}]
    String imageObj = TinyJson.findObject(ldJson, "image");
    String imageArr = TinyJson.findArray(ldJson, "image");

    String direct = firstNonBlank(
        TinyJson.findString(ldJson, "image"),
        TinyJson.findString(ldJson, "thumbnailUrl"),
        TinyJson.findString(ldJson, "contentUrl")
    );
    if (direct != null) return direct;

    String fromObj = firstNonBlank(
        TinyJson.findString(imageObj, "url"),
        TinyJson.findString(imageObj, "contentUrl"),
        TinyJson.findString(imageObj, "thumbnailUrl")
    );
    if (fromObj != null) return fromObj;

    if (imageArr != null) {
      String firstObj = TinyJson.firstObjectInArray(imageArr);
      String fromArrayObj = firstNonBlank(
          TinyJson.findString(firstObj, "url"),
          TinyJson.findString(firstObj, "contentUrl"),
          TinyJson.findString(firstObj, "thumbnailUrl")
      );
      if (fromArrayObj != null) return fromArrayObj;

      // Array of strings fallback.
      Matcher m = Pattern.compile("\"(https?://[^\"]+)\"").matcher(imageArr);
      if (m.find()) return safe(m.group(1));
    }

    return null;
  }

  private static String extractImageFromPostJson(String postJson) {
    if (postJson == null || postJson.isBlank()) return null;
    String image = extractImageFromPostJsonBody(postJson);
    if (image != null) return image;

    String unescaped = unescapeBackslashEscapes(postJson);
    if (unescaped != null && !unescaped.equals(postJson)) {
      return extractImageFromPostJsonBody(unescaped);
    }
    return null;
  }

  private static String extractImageFromPostJsonBody(String postJson) {
    if (postJson == null || postJson.isBlank()) return null;

    String media = firstNonBlank(
        TinyJson.findString(postJson, "display_url"),
        TinyJson.findString(postJson, "display_src"),
        TinyJson.findString(postJson, "display_uri"),
        TinyJson.findString(postJson, "image_url")
    );
    if (media != null) return media;

    String resources = TinyJson.findArray(postJson, "display_resources");
    if (resources != null) {
      String src = firstNonBlank(
          extractLargestDisplayResource(resources),
          TinyJson.findString(TinyJson.firstObjectInArray(resources), "src"),
          TinyJson.findString(TinyJson.firstObjectInArray(resources), "url"));
      if (src != null) return src;
    }

    // Keep this as a last resort; it is often square/thumbnail-like.
    String thumb = TinyJson.findString(postJson, "thumbnail_src");
    if (thumb != null && !thumb.isBlank()) return thumb;

    String genericSrc = TinyJson.findString(postJson, "src");
    if (genericSrc != null && !genericSrc.isBlank()) return genericSrc;

    return null;
  }

  private static String extractLargestDisplayResource(String resourcesJson) {
    if (resourcesJson == null || resourcesJson.isBlank()) return null;
    String best = null;
    Matcher m = JSON_SRC_FIELD.matcher(resourcesJson);
    while (m.find()) {
      String src = safe(unescapeBackslashEscapes(m.group(1)));
      if (src != null) best = src;
    }
    return best;
  }

  private static String extractAuthor(String ldJson, String postJson, LinkPreview base) {
    // Prefer @handle if we can find it.
    String handle = extractAuthorFromLd(ldJson);

    if (handle == null) {
      handle = extractAuthorFromPostJson(postJson);
    }

    if (handle == null) {
      handle = authorFromTitle(base.title());
    }

    if (handle == null) {
      handle = authorFromText(firstNonBlank(base.description(), base.title()));
    }

    return safe(handle);
  }

  private static String extractAuthorFromLd(String ldJson) {
    if (ldJson == null || ldJson.isBlank()) return null;
    String handle = null;

    String authorObj = TinyJson.findObject(ldJson, "author");
    String authorArr = TinyJson.findArray(ldJson, "author");
    String firstAuthorObj = TinyJson.firstObjectInArray(authorArr);

    String alt = firstNonBlank(
        TinyJson.findString(authorObj, "alternateName"),
        TinyJson.findString(firstAuthorObj, "alternateName"),
        TinyJson.findString(ldJson, "author_name")
    );
    if (alt != null) {
      alt = alt.strip();
      if (alt.startsWith("@")) alt = alt.substring(1);
      if (!alt.isBlank()) handle = alt;
    }

    // Fallback: author.name sometimes looks like "Name (@handle)".
    if (handle == null) {
      String name = firstNonBlank(
          TinyJson.findString(authorObj, "name"),
          TinyJson.findString(firstAuthorObj, "name"),
          TinyJson.findString(ldJson, "author")
      );
      if (name != null) {
        Matcher m = Pattern.compile("\\(@([A-Za-z0-9_.]+)\\)").matcher(name);
        if (m.find()) handle = m.group(1);
      }
    }
    return safe(handle);
  }

  private static String extractAuthorFromPostJson(String postJson) {
    if (postJson == null || postJson.isBlank()) return null;
    String ownerObj = TinyJson.findObject(postJson, "owner");
    String handle = firstNonBlank(
        TinyJson.findString(ownerObj, "username"),
        TinyJson.findString(postJson, "username"),
        TinyJson.findString(postJson, "owner_username"));
    if (handle != null && handle.startsWith("@")) handle = handle.substring(1);
    return safe(handle);
  }

  private static LocalDate extractDate(String ldJson, String postJson, Document doc) {
    LocalDate fromLd = parseLocalDate(firstNonBlank(
        TinyJson.findString(ldJson, "datePublished"),
        TinyJson.findString(ldJson, "uploadDate"),
        TinyJson.findString(ldJson, "dateCreated")));
    if (fromLd != null) return fromLd;

    LocalDate fromMeta = parseLocalDate(firstNonBlank(
        meta(doc, "property", "article:published_time"),
        meta(doc, "property", "article:modified_time"),
        meta(doc, "property", "og:updated_time"),
        meta(doc, "name", "parsely-pub-date"),
        firstTimeDateTime(doc)));
    if (fromMeta != null) return fromMeta;

    Long ts = findLongField(postJson, "taken_at_timestamp");
    if (ts == null) ts = findLongField(postJson, "taken_at");
    if (ts != null && ts > 0) {
      try {
        return Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalDate();
      } catch (Exception ignored) {
        // fall through
      }
    }

    return null;
  }

  private static String authorFromTitle(String title) {
    if (title == null || title.isBlank()) return null;
    String t = title.strip();
    Matcher m = SHARED_BY.matcher(t);
    if (m.find()) {
      return m.group(2);
    }
    Matcher m2 = ON_INSTAGRAM.matcher(t);
    if (m2.find()) {
      return m2.group(1);
    }
    return null;
  }

  private static String authorFromText(String text) {
    if (text == null || text.isBlank()) return null;
    String t = text.strip();
    Matcher m = SHARED_BY.matcher(t);
    if (m.find()) {
      return m.group(2);
    }
    Matcher m2 = ON_INSTAGRAM.matcher(t);
    if (m2.find()) {
      return m2.group(1);
    }
    Matcher m3 = SIMPLE_ON_INSTAGRAM.matcher(t);
    if (m3.find()) {
      return m3.group(1);
    }
    return null;
  }

  private static LocalDate parseLocalDate(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String iso = raw.strip();

    try {
      Instant inst = Instant.parse(iso);
      return inst.atZone(ZoneId.systemDefault()).toLocalDate();
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
    } catch (Exception ignored) {
      // fall through
    }
    try {
      return LocalDate.parse(iso);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String extractCaption(String ldJson, String postJson, LinkPreview base) {
    if (ldJson != null && !ldJson.isBlank()) {
      // caption/text/articleBody are all seen in the wild.
      String text = firstNonBlank(
          TinyJson.findString(ldJson, "caption"),
          TinyJson.findString(ldJson, "text"),
          TinyJson.findString(ldJson, "articleBody"),
          TinyJson.findString(ldJson, "description")
      );
      if (text != null && !text.isBlank()) {
        return stripWrappingQuotes(text);
      }
    }

    if (postJson != null && !postJson.isBlank()) {
      String fromPostJson = extractCaptionFromPostJson(postJson);
      if (fromPostJson != null && !fromPostJson.isBlank()) {
        return fromPostJson;
      }
    }

    return captionFromInstagramText(base.title(), base.description());
  }

  private static String extractCaptionFromPostJson(String postJson) {
    if (postJson == null || postJson.isBlank()) return null;
    String text = extractCaptionFromPostJsonBody(postJson);
    if (text != null) return text;

    String unescaped = unescapeBackslashEscapes(postJson);
    if (unescaped != null && !unescaped.equals(postJson)) {
      text = extractCaptionFromPostJsonBody(unescaped);
      if (text != null) return text;
    }
    return null;
  }

  private static String extractCaptionFromPostJsonBody(String postJson) {
    if (postJson == null || postJson.isBlank()) return null;

    String edge = TinyJson.findObject(postJson, "edge_media_to_caption");
    String edges = TinyJson.findArray(edge, "edges");
    String firstEdge = TinyJson.firstObjectInArray(edges);
    String node = TinyJson.findObject(firstEdge, "node");

    String text = firstNonBlank(
        TinyJson.findString(node, "text"),
        TinyJson.findString(postJson, "caption"),
        TinyJson.findString(postJson, "edge_media_to_caption_text"),
        TinyJson.findString(postJson, "accessibility_caption"),
        firstCaptured(postJson, EDGE_CAPTION_TEXT_FIELD),
        firstCaptured(postJson, CAPTION_FIELD),
        firstCaptured(postJson, EDGE_CAPTION_TEXT_FALLBACK_FIELD),
        firstCaptured(postJson, ACCESSIBILITY_CAPTION_FIELD));

    if (text == null || text.isBlank()) return null;
    text = unescapeBackslashEscapes(text);
    return stripWrappingQuotes(text);
  }

  private static String firstCaptured(String text, Pattern pattern) {
    if (text == null || text.isBlank() || pattern == null) return null;
    Matcher m = pattern.matcher(text);
    if (!m.find()) return null;
    return safe(m.group(1));
  }

  private static String captionFromInstagramText(String title, String description) {
    // Instagram often puts the "A post shared by ..." boilerplate into title/description.
    // If we find it, remove it and keep the rest.
    String t = firstNonBlank(description, title);
    if (t == null) return null;

    String s = t.strip();
    // Remove leading "A post shared by ..." segment if present.
    Matcher m = Pattern.compile("^A post shared by\\s+.+?\\)\\s*(.*)$", Pattern.CASE_INSENSITIVE).matcher(s);
    if (m.find()) {
      String rest = m.group(1);
      rest = rest == null ? null : rest.strip();
      if (rest != null && !rest.isBlank()) {
        return rest;
      }
      return null;
    }

    Matcher byInstagram = CAPTION_AFTER_INSTAGRAM.matcher(s);
    if (byInstagram.find()) {
      String cap = byInstagram.group(1);
      if (cap != null && !cap.isBlank()) {
        cap = stripWrappingQuotes(cap);
        cap = LIKES_COMMENTS_PREFIX.matcher(cap).replaceFirst("");
        return safe(cap);
      }
    }

    // Also strip "on Instagram" trailers.
    s = s.replaceAll("\\s+on\\s+Instagram\\s*$", "").strip();
    s = LIKES_COMMENTS_PREFIX.matcher(s).replaceFirst("");
    return safe(s);
  }

  private static String cleanCaption(String caption) {
    if (caption == null) return null;
    String t = caption.strip();
    if (t.isEmpty()) return null;

    // Drop obvious login-wall/interstitial copy.
    if (looksLikeLoginInterstitial(t)) {
      return null;
    }

    // Avoid the "quoted" JSON string look.
    t = stripWrappingQuotes(t);
    t = unescapeBackslashEscapes(t);
    t = stripWrappingQuotes(t);
    if (t == null || t.isBlank()) return null;
    if (t.matches("^[\"'\\\\]+$")) return null;

    // Keep it reasonably short; the UI also clamps, but we don't want huge tooltips.
    int max = 2200;
    if (t.length() > max) {
      t = t.substring(0, max).strip() + "…";
    }
    return safe(t);
  }

  private static String buildDescription(String author, LocalDate date, String caption) {
    StringBuilder sb = new StringBuilder();

    if (author != null && !author.isBlank()) {
      sb.append("Author: @").append(author.strip());
    }
    if (date != null) {
      if (sb.length() > 0) sb.append("\n");
      sb.append("Date: ").append(date);
    }
    if (caption != null && !caption.isBlank()) {
      String summary = trimSummary(caption.strip());
      if (summary != null && !summary.isBlank()) {
        if (sb.length() > 0) sb.append("\n\n");
        sb.append("Summary:\n").append(summary);
      }
    }

    String out = sb.toString().strip();
    return out.isEmpty() ? null : out;
  }

  private static String trimSummary(String caption) {
    if (caption == null) return null;
    String c = caption.strip();
    if (c.isEmpty()) return null;
    // Keep a few sentences, not a giant wall of text.
    return PreviewTextUtil.trimToSentence(c, 1500);
  }

  private static String cleanTitle(String title) {
    if (title == null) return null;
    String t = title.strip();
    if (t.isEmpty()) return null;

    // Strip the common " - Instagram" suffix.
    t = t.replaceAll("\\s+-\\s+Instagram\\s*$", "").strip();

    // If we still just have the generic brand name, treat it as missing.
    if (t.equalsIgnoreCase("instagram")) {
      return null;
    }

    // Remove the boilerplate "A post shared by..." prefix if present.
    Matcher m = Pattern.compile("^A post shared by\\s+.+?\\)\\s*$", Pattern.CASE_INSENSITIVE).matcher(t);
    if (m.find()) {
      return null;
    }

    return safe(t);
  }

  private static String stripWrappingQuotes(String s) {
    if (s == null) return null;
    String t = s.strip();
    if (t.length() >= 4 && t.startsWith("\\\"") && t.endsWith("\\\"")) {
      t = t.substring(2, t.length() - 2).strip();
    }
    if (t.length() >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
      t = t.substring(1, t.length() - 1).strip();
    }
    return t;
  }

  private static String unescapeBackslashEscapes(String s) {
    if (s == null || s.isBlank()) return s;
    String t = s;
    t = t.replace("\\\"", "\"");
    t = t.replace("\\n", "\n");
    t = t.replace("\\/", "/");
    t = t.replace("\\\\", "\\");
    return t;
  }

  private static boolean looksLikeLoginInterstitial(String text) {
    if (text == null) return false;
    String t = text.strip();
    if (t.isEmpty()) return false;

    String lower = t.toLowerCase(Locale.ROOT);
    boolean hasLogin = lower.contains("log in") || lower.contains("log-in") || lower.contains("login");
    boolean hasSignup = lower.contains("sign up") || lower.contains("create an account")
        || lower.contains("don't have an account") || lower.contains("dont have an account");
    boolean hasAppPrompt = lower.contains("open in app") || lower.contains("get the app") || lower.contains("use the app");

    // Only treat it as an interstitial when it's clearly account/login chatter.
    if (hasLogin && (hasSignup || hasAppPrompt)) {
      return true;
    }

    // Short generic copy on some interstitial pages.
    if (t.length() <= 50 && (lower.equals("instagram") || lower.equals("instagram post") || lower.equals("instagram photo"))) {
      return true;
    }

    return false;
  }

  private static String bestImageFromDoc(Document doc) {
    if (doc == null) return null;

    Element best = null;
    int bestScore = -1;

    for (Element img : doc.select("img[src]")) {
      String src = safe(img.attr("src"));
      if (src == null) continue;

      String lower = src.toLowerCase(Locale.ROOT);
      if (lower.startsWith("data:")) continue;

      // Exclude obvious non-content assets.
      if (lower.contains("sprite") || lower.contains("icon") || lower.contains("logo")) continue;

      boolean plausible = lower.contains("cdninstagram")
          || lower.contains("scontent")
          || lower.contains("fbcdn")
          || lower.contains("instagram");
      if (!plausible) continue;

      int w = parseInt(img.attr("width"));
      int h = parseInt(img.attr("height"));
      if (w > 0 && h > 0 && (w < 120 || h < 120)) continue;

      int area = (w > 0 && h > 0) ? (w * h) : 0;
      int score = area;
      if (score == 0) {
        // Prefer actual media hosts when dims are missing.
        if (lower.contains("scontent") || lower.contains("cdninstagram") || lower.contains("fbcdn")) score = 50;
        else score = 10;
      }

      if (score > bestScore) {
        bestScore = score;
        best = img;
      }
    }

    return best != null ? safe(best.attr("src")) : null;
  }

  private static String meta(Document doc, String attr, String key) {
    if (doc == null || attr == null || key == null) return null;
    try {
      Element el = doc.selectFirst("meta[" + attr + "='" + key + "']");
      if (el == null) return null;
      return safe(el.attr("content"));
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String firstTimeDateTime(Document doc) {
    if (doc == null) return null;
    try {
      Element t = doc.selectFirst("time[datetime]");
      if (t == null) return null;
      return safe(t.attr("datetime"));
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Long findLongField(String json, String key) {
    if (json == null || json.isBlank() || key == null || key.isBlank()) return null;
    try {
      Matcher m = LONG_FIELD.matcher(json);
      while (m.find()) {
        if (!key.equals(m.group(1))) continue;
        String digits = m.group(2);
        if (digits == null || digits.isBlank()) continue;
        return Long.parseLong(digits);
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  private static int parseInt(String s) {
    if (s == null || s.isBlank()) return -1;
    try {
      return Integer.parseInt(s.trim());
    } catch (Exception ignored) {
      return -1;
    }
  }

  private static String resolveAgainst(String baseUrl, String maybeUrl) {
    if (maybeUrl == null || maybeUrl.isBlank()) return null;
    try {
      URI u = URI.create(maybeUrl);
      if (u.isAbsolute()) return maybeUrl;
    } catch (Exception ignored) {
      // fall through and try resolve
    }
    try {
      URI base = URI.create(baseUrl);
      return base.resolve(maybeUrl).toString();
    } catch (Exception ignored) {
      return maybeUrl;
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v.strip();
    }
    return null;
  }

  private static String safe(String s) {
    if (s == null) return null;
    String t = s.strip();
    return t.isEmpty() ? null : t;
  }
}

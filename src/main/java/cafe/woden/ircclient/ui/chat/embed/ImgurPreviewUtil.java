package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

final class ImgurPreviewUtil {

  private ImgurPreviewUtil() {}

  private static final Pattern GALLERY_PATH =
      Pattern.compile("^/(gallery|a|t/[^/]+)/[^/]+(?:/.*)?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DIRECT_POST_PATH = Pattern.compile("^/[A-Za-z0-9]{5,}(?:/.*)?$");
  private static final Pattern LONG_FIELD =
      Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*([0-9]{6,})");

  private static final String[] NON_POST_PREFIXES = {
    "/about",
    "/account",
    "/apps",
    "/blog",
    "/community",
    "/download",
    "/help",
    "/privacy",
    "/random",
    "/register",
    "/rules",
    "/settings",
    "/signin",
    "/tos",
    "/upload",
    "/user"
  };

  static boolean isImgurUrl(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      return isImgurUri(URI.create(url));
    } catch (Exception ignored) {
      return false;
    }
  }

  static boolean isImgurUri(URI uri) {
    if (uri == null) return false;
    String host = normalizeHost(uri.getHost());
    if (host == null) return false;
    if (!(host.equals("imgur.com") || host.endsWith(".imgur.com"))) return false;
    if (host.equals("i.imgur.com")) return false; // direct image CDN links are handled elsewhere

    String path = uri.getPath();
    if (path == null) return false;
    String p = path.strip();
    if (p.isEmpty() || p.equals("/")) return false;

    String lower = p.toLowerCase(Locale.ROOT);
    for (String disallowed : NON_POST_PREFIXES) {
      if (lower.equals(disallowed) || lower.startsWith(disallowed + "/")) {
        return false;
      }
    }

    if (GALLERY_PATH.matcher(p).matches()) return true;
    return DIRECT_POST_PATH.matcher(p).matches();
  }

  static boolean looksLikeImgurDescription(String description) {
    if (description == null || description.isBlank()) return false;
    String d = description.toLowerCase(Locale.ROOT);
    boolean hasMeta = d.contains("submitter:") || d.contains("author:") || d.contains("date:");
    boolean hasSummary = d.contains("\nsummary:\n") || d.startsWith("summary:\n");
    return hasMeta && hasSummary;
  }

  static LinkPreview parsePostDocument(Document doc, String originalUrl) {
    if (doc == null) return null;

    LinkPreview base = LinkPreviewParser.parse(doc, originalUrl);
    String canonical = firstNonBlank(base.url(), originalUrl);

    List<String> ldJsonBlocks = extractLdJsonBlocks(doc);
    String postJson = extractBestPostJson(doc);

    String title =
        cleanTitle(
            firstNonBlank(
                TinyJson.findString(postJson, "title"),
                titleFromLdJson(ldJsonBlocks),
                base.title(),
                doc.title()));
    if (title == null) {
      title = "Imgur post";
    }

    String submitter =
        cleanSubmitter(
            firstNonBlank(
                submitterFromLdJson(ldJsonBlocks),
                TinyJson.findString(postJson, "account_url"),
                TinyJson.findString(postJson, "username"),
                TinyJson.findString(postJson, "author"),
                meta(doc, "author"),
                meta(doc, "article:author")));

    LocalDate date =
        firstNonNullDate(
            dateFromLdJson(ldJsonBlocks),
            parseLocalDate(
                firstNonBlank(
                    meta(doc, "article:published_time"),
                    meta(doc, "article:modified_time"),
                    meta(doc, "og:updated_time"),
                    meta(doc, "parsely-pub-date"),
                    meta(doc, "pubdate"),
                    meta(doc, "date"),
                    firstTimeDateTime(doc))),
            dateFromEpoch(findLongField(postJson, "datetime")),
            dateFromEpoch(findLongField(postJson, "created")));

    String caption =
        cleanCaption(
            firstNonBlank(
                captionFromLdJson(ldJsonBlocks),
                TinyJson.findString(postJson, "description"),
                base.description()),
            title);

    String image =
        resolveAgainst(
            canonical,
            firstNonBlank(
                imageFromLdJson(ldJsonBlocks),
                imageFromPostJson(postJson),
                base.imageUrl(),
                bestImageFromDoc(doc)));

    if (image == null && caption == null && submitter == null && date == null) {
      return null;
    }

    String description = buildDescription(submitter, date, caption);
    int mediaCount = (image != null && !image.isBlank()) ? 1 : 0;
    return new LinkPreview(canonical, title, description, "Imgur", image, mediaCount);
  }

  private static List<String> extractLdJsonBlocks(Document doc) {
    List<String> out = new ArrayList<>();
    if (doc == null) return out;
    for (Element script : doc.select("script[type=application/ld+json]")) {
      String text = safe(script.data());
      if (text == null) text = safe(script.html());
      if (text == null) continue;
      out.add(text);
    }
    return out;
  }

  private static String extractBestPostJson(Document doc) {
    if (doc == null) return null;
    String best = null;
    int bestScore = Integer.MIN_VALUE;
    for (Element script : doc.select("script")) {
      String text = safe(script.data());
      if (text == null) text = safe(script.html());
      if (text == null) continue;

      String lower = text.toLowerCase(Locale.ROOT);
      int score = 0;
      if (lower.contains("postdatajson")) score += 16;
      if (lower.contains("\"account_url\"")) score += 8;
      if (lower.contains("\"datetime\"")) score += 6;
      if (lower.contains("\"description\"")) score += 5;
      if (lower.contains("\"link\"")) score += 5;
      if (lower.contains("\"hash\"")) score += 3;
      if (score <= 0) continue;

      String candidate = extractLikelyJson(text);
      if (candidate == null || candidate.isBlank()) continue;

      String cl = candidate.toLowerCase(Locale.ROOT);
      if (cl.contains("\"account_url\"")) score += 6;
      if (cl.contains("\"datetime\"")) score += 4;
      if (cl.contains("\"link\"")) score += 4;
      if (cl.contains("\"description\"")) score += 3;
      if (cl.contains("\"hash\"")) score += 2;

      if (score > bestScore) {
        bestScore = score;
        best = candidate;
      }
    }
    return best;
  }

  private static String extractLikelyJson(String text) {
    if (text == null || text.isBlank()) return null;

    String lower = text.toLowerCase(Locale.ROOT);
    int anchor = lower.indexOf("postdatajson");
    if (anchor >= 0) {
      int start = text.indexOf('{', anchor);
      String obj = captureBalancedObject(text, start);
      if (looksLikePostJson(obj)) return obj;
    }

    int start = text.indexOf('{');
    while (start >= 0) {
      String obj = captureBalancedObject(text, start);
      if (looksLikePostJson(obj)) return obj;
      start = text.indexOf('{', start + 1);
    }
    return null;
  }

  private static boolean looksLikePostJson(String json) {
    if (json == null || json.isBlank()) return false;
    String lower = json.toLowerCase(Locale.ROOT);
    return lower.contains("\"account_url\"")
        || lower.contains("\"datetime\"")
        || lower.contains("\"hash\"")
        || lower.contains("\"link\"");
  }

  private static String captureBalancedObject(String s, int start) {
    if (s == null || start < 0 || start >= s.length() || s.charAt(start) != '{') return null;
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
      if (c == '{') depth++;
      if (c == '}') {
        depth--;
        if (depth == 0) return s.substring(start, i + 1);
      }
    }
    return null;
  }

  private static String titleFromLdJson(List<String> ldJsonBlocks) {
    if (ldJsonBlocks == null || ldJsonBlocks.isEmpty()) return null;
    for (String json : ldJsonBlocks) {
      String t =
          firstNonBlank(TinyJson.findString(json, "headline"), TinyJson.findString(json, "title"));
      if (t != null) return t;
    }
    return null;
  }

  private static String submitterFromLdJson(List<String> ldJsonBlocks) {
    if (ldJsonBlocks == null || ldJsonBlocks.isEmpty()) return null;
    for (String json : ldJsonBlocks) {
      String authorObj = TinyJson.findObject(json, "author");
      String authorArr = TinyJson.findArray(json, "author");
      String firstAuthorObj = TinyJson.firstObjectInArray(authorArr);
      String author =
          firstNonBlank(
              TinyJson.findString(authorObj, "alternateName"),
              TinyJson.findString(authorObj, "name"),
              TinyJson.findString(firstAuthorObj, "alternateName"),
              TinyJson.findString(firstAuthorObj, "name"),
              TinyJson.findString(json, "author"));
      if (author != null) return author;
    }
    return null;
  }

  private static LocalDate dateFromLdJson(List<String> ldJsonBlocks) {
    if (ldJsonBlocks == null || ldJsonBlocks.isEmpty()) return null;
    for (String json : ldJsonBlocks) {
      LocalDate d =
          parseLocalDate(
              firstNonBlank(
                  TinyJson.findString(json, "datePublished"),
                  TinyJson.findString(json, "uploadDate"),
                  TinyJson.findString(json, "dateCreated")));
      if (d != null) return d;
    }
    return null;
  }

  private static String captionFromLdJson(List<String> ldJsonBlocks) {
    if (ldJsonBlocks == null || ldJsonBlocks.isEmpty()) return null;
    for (String json : ldJsonBlocks) {
      String text =
          firstNonBlank(
              TinyJson.findString(json, "description"),
              TinyJson.findString(json, "articleBody"),
              TinyJson.findString(json, "text"),
              TinyJson.findString(json, "caption"));
      if (text != null) return text;
    }
    return null;
  }

  private static String imageFromLdJson(List<String> ldJsonBlocks) {
    if (ldJsonBlocks == null || ldJsonBlocks.isEmpty()) return null;
    for (String json : ldJsonBlocks) {
      String imageObj = TinyJson.findObject(json, "image");
      String imageArr = TinyJson.findArray(json, "image");
      String firstImageObj = TinyJson.firstObjectInArray(imageArr);

      String image =
          firstNonBlank(
              TinyJson.findString(json, "image"),
              TinyJson.findString(json, "thumbnailUrl"),
              TinyJson.findString(imageObj, "url"),
              TinyJson.findString(imageObj, "contentUrl"),
              TinyJson.findString(firstImageObj, "url"),
              TinyJson.findString(firstImageObj, "contentUrl"));
      if (image != null) return image;
    }
    return null;
  }

  private static String imageFromPostJson(String postJson) {
    if (postJson == null || postJson.isBlank()) return null;
    String link =
        firstNonBlank(
            TinyJson.findString(postJson, "link"),
            TinyJson.findString(postJson, "image_url"),
            TinyJson.findString(postJson, "image"),
            TinyJson.findString(postJson, "url"));
    if (link != null) return link;

    String coverHash = TinyJson.findString(postJson, "cover");
    String coverExt =
        firstNonBlank(
            TinyJson.findString(postJson, "cover_ext"),
            TinyJson.findString(postJson, "cover_extension"));
    String cover = imgurImageFromHash(coverHash, coverExt);
    if (cover != null) return cover;

    String hash = TinyJson.findString(postJson, "hash");
    String ext = extFromMime(TinyJson.findString(postJson, "type"));
    return imgurImageFromHash(hash, ext);
  }

  private static String imgurImageFromHash(String hash, String ext) {
    String h = safe(hash);
    String e = normalizeImageExt(ext);
    if (h == null || e == null) return null;
    return "https://i.imgur.com/" + h + e;
  }

  private static String normalizeImageExt(String ext) {
    if (ext == null || ext.isBlank()) return null;
    String e = ext.strip().toLowerCase(Locale.ROOT);
    if (!e.startsWith(".")) e = "." + e;
    return switch (e) {
      case ".jpg", ".jpeg" -> ".jpg";
      case ".png" -> ".png";
      case ".gif" -> ".gif";
      case ".webp" -> ".webp";
      default -> null;
    };
  }

  private static String extFromMime(String mime) {
    if (mime == null || mime.isBlank()) return null;
    String m = mime.toLowerCase(Locale.ROOT);
    if (m.contains("jpeg") || m.contains("jpg")) return ".jpg";
    if (m.contains("png")) return ".png";
    if (m.contains("gif")) return ".gif";
    if (m.contains("webp")) return ".webp";
    return null;
  }

  private static String bestImageFromDoc(Document doc) {
    if (doc == null) return null;
    String meta =
        firstNonBlank(
            meta(doc, "og:image"),
            meta(doc, "og:image:secure_url"),
            meta(doc, "twitter:image"),
            meta(doc, "twitter:image:src"));
    if (meta != null) return meta;

    Element best = null;
    int bestArea = -1;
    for (Element img : doc.select("img[src]")) {
      String src = safe(img.attr("src"));
      if (src == null) continue;
      String lower = src.toLowerCase(Locale.ROOT);
      if (!lower.contains("imgur")) continue;
      if (lower.startsWith("data:")) continue;

      int w = parseInt(img.attr("width"));
      int h = parseInt(img.attr("height"));
      if (w > 0 && h > 0 && (w < 120 || h < 120)) continue;
      int area = (w > 0 && h > 0) ? (w * h) : 1;
      if (area > bestArea) {
        bestArea = area;
        best = img;
      }
    }
    return best != null ? safe(best.attr("src")) : null;
  }

  private static String buildDescription(String submitter, LocalDate date, String caption) {
    StringBuilder sb = new StringBuilder();
    if (submitter != null && !submitter.isBlank()) {
      sb.append("Submitter: ").append(submitter);
    }
    if (date != null) {
      if (sb.length() > 0) sb.append("\n");
      sb.append("Date: ").append(date);
    }
    if (caption != null && !caption.isBlank()) {
      if (sb.length() > 0) sb.append("\n\n");
      sb.append("Summary:\n").append(caption);
    }
    String out = sb.toString().strip();
    return out.isEmpty() ? null : out;
  }

  private static String cleanTitle(String title) {
    if (title == null) return null;
    String t = title.strip();
    if (t.isEmpty()) return null;
    t = t.replaceAll("(?i)\\s+-\\s+imgur\\s*$", "").strip();
    t = t.replaceAll("(?i)\\s+-\\s+album\\s+on\\s+imgur\\s*$", "").strip();
    t = t.replaceAll("(?i)^imgur:\\s*", "").strip();
    String lower = t.toLowerCase(Locale.ROOT);
    if (lower.equals("imgur") || lower.equals("the magic of the internet")) return null;
    return safe(t);
  }

  private static String cleanSubmitter(String submitter) {
    if (submitter == null) return null;
    String s = submitter.strip();
    if (s.isEmpty()) return null;
    if (s.startsWith("@")) s = s.substring(1).strip();
    if (s.toLowerCase(Locale.ROOT).startsWith("by ")) s = s.substring(3).strip();
    return safe(s);
  }

  private static String cleanCaption(String caption, String resolvedTitle) {
    if (caption == null) return null;
    String c = caption.strip();
    if (c.isEmpty()) return null;
    c = c.replace('\u00A0', ' ');
    c = c.replaceAll("\\s+", " ").strip();
    if (c.isEmpty()) return null;

    String lower = c.toLowerCase(Locale.ROOT);
    if (lower.contains("discover the magic of the internet at imgur")) return null;
    if (lower.equals("imgur") || lower.equals("the magic of the internet")) return null;

    if (resolvedTitle != null && !resolvedTitle.isBlank()) {
      String t = resolvedTitle.strip();
      if (!t.isEmpty() && c.equalsIgnoreCase(t)) {
        return null;
      }
    }

    return safe(PreviewTextUtil.trimToSentence(c, 1500));
  }

  private static String meta(Document doc, String key) {
    if (doc == null || key == null || key.isBlank()) return null;
    try {
      Element p = doc.selectFirst("meta[property='" + key + "']");
      if (p != null) {
        String v = safe(p.attr("content"));
        if (v != null) return v;
      }
      Element n = doc.selectFirst("meta[name='" + key + "']");
      if (n != null) {
        String v = safe(n.attr("content"));
        if (v != null) return v;
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
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

  private static LocalDate parseLocalDate(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String iso = raw.strip();
    try {
      return Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate();
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

  private static LocalDate dateFromEpoch(Long epoch) {
    if (epoch == null || epoch <= 0) return null;
    try {
      Instant inst =
          (epoch > 10_000_000_000L) ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
      return inst.atZone(ZoneId.systemDefault()).toLocalDate();
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

  private static String resolveAgainst(String baseUrl, String maybeUrl) {
    if (maybeUrl == null || maybeUrl.isBlank()) return null;
    String u = maybeUrl.strip();
    if (u.startsWith("//")) return "https:" + u;
    try {
      URI uri = URI.create(u);
      if (uri.isAbsolute()) return u;
    } catch (Exception ignored) {
      // fall through and try resolve
    }
    try {
      URI base = URI.create(baseUrl);
      return base.resolve(u).toString();
    } catch (Exception ignored) {
      return u;
    }
  }

  private static int parseInt(String s) {
    if (s == null || s.isBlank()) return -1;
    try {
      return Integer.parseInt(s.trim());
    } catch (Exception ignored) {
      return -1;
    }
  }

  private static String normalizeHost(String host) {
    if (host == null) return null;
    String h = host.strip().toLowerCase(Locale.ROOT);
    if (h.isEmpty()) return null;
    if (h.startsWith("www.")) h = h.substring(4);
    if (h.startsWith("m.")) h = h.substring(2);
    return h;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v.strip();
    }
    return null;
  }

  private static LocalDate firstNonNullDate(LocalDate... values) {
    if (values == null) return null;
    for (LocalDate v : values) {
      if (v != null) return v;
    }
    return null;
  }

  private static String safe(String s) {
    if (s == null) return null;
    String t = s.strip();
    return t.isEmpty() ? null : t;
  }
}

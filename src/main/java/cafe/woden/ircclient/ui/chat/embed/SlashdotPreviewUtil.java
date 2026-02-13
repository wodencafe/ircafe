package cafe.woden.ircclient.ui.chat.embed;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.net.URI;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

final class SlashdotPreviewUtil {

  private SlashdotPreviewUtil() {}

  static boolean isSlashdotStoryUrl(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      return isSlashdotStoryUri(URI.create(url));
    } catch (Exception ignored) {
      return false;
    }
  }

  static boolean isSlashdotStoryUri(URI uri) {
    if (uri == null) return false;
    String host = uri.getHost();
    if (host == null || host.isBlank()) return false;
    String h = host.toLowerCase(Locale.ROOT);
    if (!(h.equals("slashdot.org") || h.endsWith(".slashdot.org"))) return false;

    String path = uri.getPath();
    if (path == null) return false;
    // Common story URLs:
    //   https://news.slashdot.org/story/07/01/29/1452212/...
    //   https://tech.slashdot.org/story/...
    return path.contains("/story/");
  }

  /**
   * Try to extract a longer story excerpt than OG/meta typically provides.
   *
   * <p>Slashdot markup has changed over the years, so this is intentionally heuristic.
   */
  static String bestStoryExcerpt(Document doc, String fallbackDescription) {
    if (doc == null) return fallbackDescription;

    // Prefer the main story body when we can identify it.
    String extracted = extractFromLikelyStoryContainer(doc);
    extracted = normalizeWhitespace(extracted);

    String fb = normalizeWhitespace(fallbackDescription);

    // If we couldn't find anything meaningful, keep whatever OG/meta gave us.
    if (extracted == null || extracted.isBlank()) {
      return fb;
    }

    // Avoid returning the same short meta blurb; prefer the longer excerpt.
    if (fb != null && !fb.isBlank()) {
      String fbNorm = fb.toLowerCase(Locale.ROOT);
      String exNorm = extracted.toLowerCase(Locale.ROOT);
      if (exNorm.contains(fbNorm) && extracted.length() <= fb.length() + 40) {
        return fb;
      }
    }

    // Hard cap; the component will line-clamp, but this prevents absurd payloads.
    return PreviewTextUtil.trimToSentence(extracted, 1600);
  }

  /** Extract submitter/date (when available) and a cleaned summary excerpt. */
  static StoryParts extractStoryParts(Document doc, String resolvedTitle, String fallbackDescription) {
    if (doc == null) return new StoryParts(null, null, normalizeWhitespace(fallbackDescription));

    String extracted = normalizeWhitespace(extractFromLikelyStoryContainer(doc));
    String fb = normalizeWhitespace(fallbackDescription);

    String base = (extracted == null || extracted.isBlank()) ? fb : extracted;
    if (base == null) base = "";

    Meta meta = parsePostedBy(base);

    String summary = stripPreamble(base, resolvedTitle);
    summary = normalizeWhitespace(summary);

    if (summary == null || summary.isBlank()) summary = fb;

    // Prefer a clean end-of-sentence and cap payload.
    summary = PreviewTextUtil.trimToSentence(summary, 2000);

    return new StoryParts(meta != null ? meta.submitter : null, meta != null ? meta.date : null, summary);
  }

  private static Meta parsePostedBy(String text) {
    if (text == null || text.isBlank()) return null;

    // Flatten line breaks; extracted containers often include header lines.
    String t = text.replace('\n', ' ').replace('\r', ' ');

    java.util.regex.Pattern p = java.util.regex.Pattern.compile(
        "(?i)\\bPosted by\\s+([^\\s]+)\\s+on\\s+(.+?)(?:\\s+from the\\s+|\\s*$)"
    );
    java.util.regex.Matcher m = p.matcher(t);
    if (!m.find()) return null;

    String submitter = safe(m.group(1));
    String date = safe(m.group(2));
    return new Meta(submitter, date);
  }

  private static String stripPreamble(String text, String resolvedTitle) {
    if (text == null) return null;
    String s = text.strip();

    // Remove "123456 story <title>" (common Slashdot header line in extracted text).
    if (resolvedTitle != null && !resolvedTitle.isBlank()) {
      String t = resolvedTitle.strip();
      s = s.replaceFirst("(?is)^\\s*\\d+\\s+story\\s+" + java.util.regex.Pattern.quote(t) + "\\s*", "");
      s = s.replaceFirst("(?is)^\\s*story\\s+" + java.util.regex.Pattern.quote(t) + "\\s*", "");
    } else {
      s = s.replaceFirst("(?is)^\\s*\\d+\\s+story\\s+", "");
    }

    // If the "Posted by ... from the ... dept." preamble is present, keep only the story text after it.
    String lower = s.toLowerCase(java.util.Locale.ROOT);
    int idxPosted = lower.indexOf("posted by ");
    if (idxPosted >= 0) {
      int idxDept = lower.indexOf(" dept", idxPosted);
      if (idxDept >= 0) {
        // Prefer cutting after the terminating "." in "dept." if present.
        int idxDot = s.indexOf('.', idxDept);
        int cut = (idxDot >= 0) ? (idxDot + 1) : (idxDept + " dept".length());
        while (cut < s.length() && Character.isWhitespace(s.charAt(cut))) cut++;
        if (cut >= 0 && cut < s.length()) {
          s = s.substring(cut).strip();
        }
      }
    }

    // Some variants can still leave a leading "dept"/"dept." token; drop it.
    s = s.replaceFirst("(?is)^\\s*dept\\.?\\s*", "");

    // If we still somehow have the title at the very front, drop it.
    if (resolvedTitle != null && !resolvedTitle.isBlank()) {
      String t = resolvedTitle.strip();
      if (s.startsWith(t)) {
        s = s.substring(Math.min(s.length(), t.length())).strip();
      }
    }

    return s;
  }

  record StoryParts(String submitter, String date, String summary) {}

  private record Meta(String submitter, String date) {}

  private static String safe(String s) {
    if (s == null) return null;
    String t = s.strip();
    return t.isEmpty() ? null : t;
  }


  private static String extractFromLikelyStoryContainer(Document doc) {
    try {
      // Strategy:
      //  1) Locate a likely headline.
      //  2) Walk upward to a reasonable container.
      //  3) Collect paragraph-ish text near the top.

      Element h1 = doc.selectFirst("h1");
      Element anchor = h1;
      if (anchor == null) {
        // Some pages use h2 for the story title.
        anchor = doc.selectFirst("h2");
      }

      Element container = null;
      if (anchor != null) {
        container = firstParentWithLotsOfText(anchor, 1200);
      }
      if (container == null) {
        // Fallback: pick the largest texty container among a few candidates.
        container = largestTextContainer(doc);
      }
      if (container == null) return null;

      // Pull paragraphs first; they read nicer.
      Elements ps = container.select("p");
      List<String> paras = new ArrayList<>();
      for (Element p : ps) {
        String t = normalizeWhitespace(p.text());
        if (t == null || t.isBlank()) continue;
        // Avoid grabbing comment UI / footer fragments.
        if (looksLikeCommentsOrFooter(t)) continue;
        if (t.length() < 40) continue;
        paras.add(t);
        if (paras.size() >= 6) break;
      }

      String joined;
      if (!paras.isEmpty()) {
        joined = String.join("\n", paras);
      } else {
        joined = normalizeWhitespace(container.text());
      }

      if (joined == null) return null;
      // Drop the title if it got duplicated into the body text.
      String title = normalizeWhitespace(doc.title());
      if (title != null && !title.isBlank()) {
        String tShort = title.replace(" - Slashdot", "").strip();
        if (!tShort.isBlank() && joined.startsWith(tShort)) {
          joined = joined.substring(Math.min(joined.length(), tShort.length())).strip();
        }
      }
      return joined;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Element firstParentWithLotsOfText(Element start, int minChars) {
    Element cur = start;
    int hops = 0;
    while (cur != null && hops++ < 8) {
      String t = normalizeWhitespace(cur.text());
      if (t != null && t.length() >= minChars) return cur;
      cur = cur.parent();
    }
    return null;
  }

  private static Element largestTextContainer(Document doc) {
    Elements candidates = doc.select(
        "article, main, div[id*=story], div[class*=story], div[id*=article], div[class*=article]"
    );
    Element best = null;
    int bestLen = 0;
    for (Element el : candidates) {
      String t = normalizeWhitespace(el.text());
      if (t == null) continue;
      int len = t.length();
      if (len > bestLen) {
        bestLen = len;
        best = el;
      }
    }
    return best;
  }

  private static boolean looksLikeCommentsOrFooter(String t) {
    String s = t.toLowerCase(Locale.ROOT);
    return s.contains("archived discussion")
        || s.contains("load all comments")
        || s.contains("log in/create")
        || s.contains("the fine print")
        || s.startsWith("share")
        || s.startsWith("score:");
  }

  private static String normalizeWhitespace(String s) {
    if (s == null) return null;
    String t = s.replace('\u00A0', ' ').strip();
    if (t.isEmpty()) return null;
    // Collapse runs of whitespace but keep newlines we intentionally inserted.
    t = t.replaceAll("[\\t\\r\\f]+", " ");
    t = t.replaceAll(" +", " ");
    return t.strip();
  }
}

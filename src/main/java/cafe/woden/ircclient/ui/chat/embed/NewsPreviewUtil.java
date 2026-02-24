package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;
import java.text.BreakIterator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Heuristics for richer previews on article-like news pages. */
final class NewsPreviewUtil {

  private NewsPreviewUtil() {}

  private static final HostProfile[] HOST_PROFILES = {
    new HostProfile("abcnews.com", "abc"),
    new HostProfile("reuters.com", "reuters"),
    new HostProfile("apnews.com", "ap"),
    new HostProfile("nytimes.com", "nyt"),
    new HostProfile("bbc.com", "bbc"),
    new HostProfile("bbc.co.uk", "bbc"),
    new HostProfile("cnn.com", "cnn"),
    new HostProfile("washingtonpost.com", "wapo"),
    new HostProfile("theguardian.com", "guardian"),
    new HostProfile("guardian.co.uk", "guardian"),
    new HostProfile("npr.org", "npr"),
    new HostProfile("wsj.com", "wsj")
  };

  private static final String[] GENERIC_PARAGRAPH_SELECTORS = {
    "article p",
    "main article p",
    "main p",
    "[itemprop='articleBody'] p",
    "section[name='articleBody'] p",
    "div[class*='article-body'] p",
    "div[class*='articleBody'] p",
    "div[data-component='text-block'] p",
    "div[data-testid*='article'] p"
  };

  private static final String[] GENERIC_BYLINE_SELECTORS = {
    "[rel='author']",
    "[itemprop='author']",
    "[class*='byline']",
    "[data-testid*='byline']",
    "meta[name='author']"
  };

  private static final String[] GENERIC_IMAGE_SELECTORS = {
    "meta[property='og:image']",
    "meta[property='og:image:secure_url']",
    "meta[name='twitter:image']",
    "meta[name='twitter:image:src']",
    "article img[src]",
    "main img[src]"
  };

  private static final String[] GENERIC_AUTHOR_META_KEYS = {
    "author", "article:author", "parsely-author", "dc.creator", "dcterms.creator", "byl"
  };

  private static final String[] GENERIC_DATE_META_KEYS = {
    "article:published_time",
    "article:modified_time",
    "og:published_time",
    "og:updated_time",
    "parsely-pub-date",
    "pubdate",
    "publish-date",
    "date",
    "dc.date",
    "dcterms.created",
    "dcterms.modified"
  };

  private static final Set<String> NEWS_SITE_HINTS =
      Set.of(
          "abc news",
          "associated press",
          "ap news",
          "bbc",
          "cnn",
          "guardian",
          "new york times",
          "npr",
          "reuters",
          "wall street journal",
          "washington post");

  private static final Set<String> TITLE_SUFFIXES =
      Set.of(
          "abc news",
          "associated press",
          "ap news",
          "bbc",
          "bbc news",
          "cnn",
          "the guardian",
          "guardian",
          "new york times",
          "nytimes",
          "npr",
          "reuters",
          "the wall street journal",
          "wall street journal",
          "washington post");

  private static final PublisherProfile DEFAULT_PROFILE =
      new PublisherProfile(
          "generic",
          "News",
          GENERIC_PARAGRAPH_SELECTORS,
          GENERIC_BYLINE_SELECTORS,
          GENERIC_IMAGE_SELECTORS,
          GENERIC_AUTHOR_META_KEYS,
          GENERIC_DATE_META_KEYS);

  private static final List<PublisherProfile> PUBLISHER_PROFILES =
      List.of(
          new PublisherProfile(
              "abc",
              "ABC News",
              new String[] {"article p", "main article p", "section article p"},
              new String[] {"[data-testid='byline']", "[class*='Byline']", "[class*='byline']"},
              GENERIC_IMAGE_SELECTORS,
              new String[] {"author", "article:author", "parsely-author"},
              new String[] {"article:published_time", "parsely-pub-date", "date"}),
          new PublisherProfile(
              "reuters",
              "Reuters",
              new String[] {
                "div[data-testid='Body'] p",
                "article[data-testid='Body'] p",
                "article[data-testid='ArticleBody'] p",
                "article p",
                "main article p"
              },
              new String[] {
                "[data-testid='AuthorName']",
                "a[data-testid='AuthorName']",
                "[class*='author-name']",
                "[class*='Byline']"
              },
              GENERIC_IMAGE_SELECTORS,
              new String[] {"author", "article:author", "parsely-author"},
              new String[] {"article:published_time", "parsely-pub-date", "date"}),
          new PublisherProfile(
              "ap",
              "AP News",
              new String[] {
                "div.RichTextStoryBody p",
                "article p",
                "main article p",
                "div[data-key='article'] p"
              },
              new String[] {"[class*='byline']", "[class*='Author']", "[data-key='byline']"},
              GENERIC_IMAGE_SELECTORS,
              new String[] {"author", "article:author", "parsely-author"},
              new String[] {"article:published_time", "parsely-pub-date", "date"}),
          new PublisherProfile(
              "nyt",
              "New York Times",
              new String[] {"section[name='articleBody'] p", "article section p", "article p"},
              new String[] {"[data-testid='byline']", "span[itemprop='name']", "[class*='byline']"},
              GENERIC_IMAGE_SELECTORS,
              new String[] {"byl", "author", "article:author", "parsely-author"},
              new String[] {"article:published_time", "ptime", "parsely-pub-date", "date"}),
          new PublisherProfile(
              "bbc",
              "BBC",
              new String[] {"article [data-component='text-block'] p", "article p", "main p"},
              new String[] {
                "[data-component='byline-block'] a",
                "[data-component='byline-block'] span",
                "[class*='byline']"
              },
              GENERIC_IMAGE_SELECTORS,
              new String[] {"byl", "author", "article:author"},
              new String[] {"article:published_time", "article:modified_time", "date"}),
          new PublisherProfile(
              "cnn",
              "CNN",
              new String[] {
                "div.article__content p",
                "div.article__main p",
                "article p",
                "[data-component-name='paragraph']"
              },
              new String[] {
                "[class*='byline']", "[data-editable='byline']", "[class*='metadata__byline']"
              },
              GENERIC_IMAGE_SELECTORS,
              new String[] {"author", "article:author", "parsely-author"},
              new String[] {
                "article:published_time", "og:updated_time", "parsely-pub-date", "date"
              }),
          new PublisherProfile(
              "wapo",
              "Washington Post",
              new String[] {
                "div[data-qa='article-body'] p",
                "article div[data-qa='article-body'] p",
                "article p"
              },
              new String[] {"[data-qa='author-name']", "[data-qa='byline']", "[class*='byline']"},
              GENERIC_IMAGE_SELECTORS,
              new String[] {"author", "article:author", "parsely-author"},
              new String[] {"article:published_time", "parsely-pub-date", "date"}),
          new PublisherProfile(
              "guardian",
              "The Guardian",
              new String[] {
                "div[data-gu-name='body'] p",
                "article div[data-gu-name='body'] p",
                "article div[class*='article-body'] p",
                "article p"
              },
              new String[] {"a[rel='author']", "[class*='byline']"},
              GENERIC_IMAGE_SELECTORS,
              new String[] {"author", "article:author", "parsely-author"},
              new String[] {"article:published_time", "parsely-pub-date", "date"}),
          new PublisherProfile(
              "npr",
              "NPR",
              new String[] {
                "div.storytext p",
                "article div.storytext p",
                "article [id*='storytext'] p",
                "article p",
                "main p"
              },
              new String[] {"[class*='byline']", "[itemprop='author']", "a[rel='author']"},
              GENERIC_IMAGE_SELECTORS,
              new String[] {"author", "article:author", "dc.creator", "parsely-author"},
              new String[] {"article:published_time", "parsely-pub-date", "date", "dc.date"}),
          new PublisherProfile(
              "wsj",
              "Wall Street Journal",
              new String[] {
                "div[data-module='ArticleBody'] p",
                "article [data-module='ArticleBody'] p",
                "article [class*='article-content'] p",
                "article p",
                "[itemprop='articleBody'] p"
              },
              new String[] {"[class*='author-name']", "a[rel='author']", "[class*='byline']"},
              GENERIC_IMAGE_SELECTORS,
              new String[] {"author", "article:author", "parsely-author"},
              new String[] {
                "article:published_time", "article:modified_time", "parsely-pub-date", "date"
              }));

  static boolean isLikelyNewsArticleUrl(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      return isLikelyNewsArticleUri(URI.create(url));
    } catch (Exception ignored) {
      return false;
    }
  }

  static boolean isLikelyNewsArticleUri(URI uri) {
    if (uri == null) return false;
    String host = normalizeHost(uri.getHost());
    if (host == null) return false;

    String path = safe(uri.getPath());
    String query = safe(uri.getQuery());
    boolean knownHost = publisherKeyForHost(host) != null;
    boolean genericNewsHost = host.contains("news");

    if (!knownHost && !genericNewsHost) {
      return false;
    }

    return isLikelyArticlePath(path, query);
  }

  static boolean isLikelyNewsSiteName(String siteName) {
    String s = normalizeText(siteName);
    if (s == null) return false;
    for (String hint : NEWS_SITE_HINTS) {
      if (s.contains(hint)) return true;
    }
    return s.endsWith(" news") || s.startsWith("news ");
  }

  static boolean looksLikeNewsDescription(String description) {
    if (description == null || description.isBlank()) return false;
    String lower = description.toLowerCase(Locale.ROOT);
    boolean hasSummary = lower.contains("\nsummary:\n") || lower.startsWith("summary:\n");
    boolean hasMeta =
        lower.contains("author:") || lower.contains("date:") || lower.contains("publisher:");
    return hasSummary && hasMeta;
  }

  static LinkPreview parseArticleDocument(Document doc, String originalUrl) {
    if (doc == null) return null;

    LinkPreview base = LinkPreviewParser.parse(doc, originalUrl);
    String canonical = firstNonBlank(base.url(), originalUrl);
    URI canonicalUri = safeUri(canonical);
    String host =
        canonicalUri != null ? normalizeHost(canonicalUri.getHost()) : hostOf(originalUrl);
    PublisherProfile profile = profileForHost(host);

    boolean likelyByUrl =
        isLikelyNewsArticleUri(canonicalUri) || isLikelyNewsArticleUrl(originalUrl);
    boolean likelyBySite = isLikelyNewsSiteName(base.siteName());
    boolean likelyByDoc = isLikelyNewsDocument(doc, profile);
    if (!likelyByUrl && !likelyBySite && !likelyByDoc) return null;

    List<String> ldJsonBlocks = extractLdJsonBlocks(doc);

    String publisher =
        firstNonBlank(
            normalizePublisherName(base.siteName()),
            profile.displayName(),
            publisherFromHost(host));

    String title = cleanTitle(firstNonBlank(base.title(), doc.title()), publisher);
    if (title == null) {
      title = "News article";
    }

    String author =
        cleanAuthor(
            firstNonBlank(
                authorFromMeta(doc, profile),
                authorFromLdJson(ldJsonBlocks),
                authorFromBylineSelectors(doc, profile)));

    LocalDate date =
        firstNonNull(
            dateFromMeta(doc, profile), dateFromLdJson(ldJsonBlocks), dateFromTimeElement(doc));

    String image =
        resolveAgainst(
            canonical,
            firstNonBlank(
                base.imageUrl(), imageFromLdJson(ldJsonBlocks), imageFromSelectors(doc, profile)));

    String summary = summaryFromDocument(doc, profile, title, base.description());
    String description = buildDescription(author, date, publisher, summary);

    int mediaCount = (image != null && !image.isBlank()) ? 1 : 0;
    String siteName = firstNonBlank(publisher, base.siteName(), hostOf(canonical), "News");
    return new LinkPreview(canonical, title, description, siteName, image, mediaCount);
  }

  private static boolean isLikelyArticlePath(String path, String query) {
    String p = normalizePath(path);
    if (p == null) return false;

    if (p.equals("/")) return false;
    if (p.equals("/news") || p.equals("/news/")) return false;

    if (p.contains("/article/") || p.contains("/story/") || p.contains("/stories/")) return true;
    if (p.contains("/news/") && segmentCount(p) >= 3) return true;
    if (p.matches(".*\\d{6,}.*")) return true;
    if (query != null && query.toLowerCase(Locale.ROOT).contains("id=") && segmentCount(p) >= 2)
      return true;
    if (p.contains("-") && segmentCount(p) >= 3) return true;
    return segmentCount(p) >= 4;
  }

  private static int segmentCount(String path) {
    if (path == null || path.isBlank()) return 0;
    String[] parts = path.split("/");
    int count = 0;
    for (String part : parts) {
      if (part != null && !part.isBlank()) count++;
    }
    return count;
  }

  private static boolean isLikelyNewsDocument(Document doc, PublisherProfile profile) {
    String ogType = safe(meta(doc, "og:type"));
    if (ogType != null && ogType.toLowerCase(Locale.ROOT).contains("article")) return true;

    if (firstNonBlank(meta(doc, "article:published_time"), meta(doc, "parsely-pub-date")) != null)
      return true;

    if (authorFromBylineSelectors(doc, profile) != null) return true;

    if (looksLikeNewsLdJson(doc)) return true;

    LinkedHashSet<String> paras = new LinkedHashSet<>();
    collectParagraphs(doc, paras, profile.paragraphSelectors(), null, 3, 1000);
    if (paras.size() < 2) {
      collectParagraphs(doc, paras, GENERIC_PARAGRAPH_SELECTORS, null, 3, 1000);
    }
    return paras.size() >= 2;
  }

  private static boolean looksLikeNewsLdJson(Document doc) {
    if (doc == null) return false;
    for (String json : extractLdJsonBlocks(doc)) {
      String low = json.toLowerCase(Locale.ROOT);
      if (low.contains("newsarticle")
          || low.contains("reportage")
          || low.contains("datepublished")) {
        return true;
      }
    }
    return false;
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

  private static String authorFromMeta(Document doc, PublisherProfile profile) {
    String v = firstMetaValue(doc, profile.authorMetaKeys());
    if (v != null) return v;
    return firstMetaValue(doc, GENERIC_AUTHOR_META_KEYS);
  }

  private static String authorFromBylineSelectors(Document doc, PublisherProfile profile) {
    String fromProfile = firstTextBySelectors(doc, profile.bylineSelectors(), 140);
    if (fromProfile != null) return fromProfile;
    return firstTextBySelectors(doc, GENERIC_BYLINE_SELECTORS, 140);
  }

  private static String authorFromLdJson(List<String> ldJsonBlocks) {
    if (ldJsonBlocks == null || ldJsonBlocks.isEmpty()) return null;
    for (String json : ldJsonBlocks) {
      String authorObj = TinyJson.findObject(json, "author");
      String authorArr = TinyJson.findArray(json, "author");
      String firstAuthorObj = TinyJson.firstObjectInArray(authorArr);
      String val =
          firstNonBlank(
              TinyJson.findString(authorObj, "name"),
              TinyJson.findString(authorObj, "alternateName"),
              TinyJson.findString(firstAuthorObj, "name"),
              TinyJson.findString(firstAuthorObj, "alternateName"),
              TinyJson.findString(json, "author"));
      val = cleanAuthor(val);
      if (val != null) return val;
    }
    return null;
  }

  private static LocalDate dateFromMeta(Document doc, PublisherProfile profile) {
    String v = firstMetaValue(doc, profile.dateMetaKeys());
    LocalDate d = parseLocalDate(v);
    if (d != null) return d;
    return parseLocalDate(firstMetaValue(doc, GENERIC_DATE_META_KEYS));
  }

  private static LocalDate dateFromLdJson(List<String> ldJsonBlocks) {
    if (ldJsonBlocks == null || ldJsonBlocks.isEmpty()) return null;
    for (String json : ldJsonBlocks) {
      String val =
          firstNonBlank(
              TinyJson.findString(json, "datePublished"),
              TinyJson.findString(json, "dateCreated"),
              TinyJson.findString(json, "uploadDate"),
              TinyJson.findString(json, "dateModified"));
      LocalDate d = parseLocalDate(val);
      if (d != null) return d;
    }
    return null;
  }

  private static LocalDate dateFromTimeElement(Document doc) {
    if (doc == null) return null;
    for (Element time : doc.select("time[datetime]")) {
      LocalDate d = parseLocalDate(safe(time.attr("datetime")));
      if (d != null) return d;
    }
    return null;
  }

  private static String imageFromLdJson(List<String> ldJsonBlocks) {
    if (ldJsonBlocks == null || ldJsonBlocks.isEmpty()) return null;
    for (String json : ldJsonBlocks) {
      String direct =
          firstNonBlank(
              TinyJson.findString(json, "image"),
              TinyJson.findString(json, "thumbnailUrl"),
              TinyJson.findString(json, "contentUrl"));
      if (direct != null) return direct;

      String imageObj = TinyJson.findObject(json, "image");
      String imageArr = TinyJson.findArray(json, "image");
      String firstObj = TinyJson.firstObjectInArray(imageArr);
      String fromObj =
          firstNonBlank(
              TinyJson.findString(imageObj, "url"),
              TinyJson.findString(firstObj, "url"),
              TinyJson.findString(imageObj, "contentUrl"),
              TinyJson.findString(firstObj, "contentUrl"));
      if (fromObj != null) return fromObj;
    }
    return null;
  }

  private static String imageFromSelectors(Document doc, PublisherProfile profile) {
    String fromProfile = firstImageBySelectors(doc, profile.imageSelectors());
    if (fromProfile != null) return fromProfile;
    return firstImageBySelectors(doc, GENERIC_IMAGE_SELECTORS);
  }

  private static String summaryFromDocument(
      Document doc, PublisherProfile profile, String title, String fallbackDescription) {
    LinkedHashSet<String> paragraphs = new LinkedHashSet<>();
    collectParagraphs(doc, paragraphs, profile.paragraphSelectors(), title, 8, 3600);
    if (paragraphs.size() < 2) {
      collectParagraphs(doc, paragraphs, GENERIC_PARAGRAPH_SELECTORS, title, 8, 3600);
    }

    String joined = joinParagraphs(paragraphs);
    String fallback = cleanSummary(fallbackDescription, title);
    String summary = cleanSummary(joined, title);

    if (summary == null) summary = fallback;
    if (summary != null
        && fallback != null
        && summary.length() < 180
        && fallback.length() > summary.length() + 40) {
      summary = fallback;
    }

    if (summary == null) return null;
    summary = reflowSummaryParagraphs(summary);
    return PreviewTextUtil.trimToSentence(summary, 2400);
  }

  private static void collectParagraphs(
      Document doc,
      LinkedHashSet<String> out,
      String[] selectors,
      String title,
      int maxParagraphs,
      int maxChars) {
    if (doc == null || selectors == null || selectors.length == 0) return;
    int chars = totalChars(out);
    for (String selector : selectors) {
      if (selector == null || selector.isBlank()) continue;
      for (Element el : doc.select(selector)) {
        String txt = normalizeParagraphText(el == null ? null : el.text());
        if (!looksLikeUsefulParagraph(txt, title)) continue;
        out.add(txt);
        chars = totalChars(out);
        if (out.size() >= maxParagraphs || chars >= maxChars) return;
      }
    }
  }

  private static int totalChars(LinkedHashSet<String> values) {
    int n = 0;
    for (String value : values) {
      if (value != null) n += value.length();
    }
    return n;
  }

  private static String joinParagraphs(LinkedHashSet<String> paragraphs) {
    if (paragraphs == null || paragraphs.isEmpty()) return null;
    StringBuilder sb = new StringBuilder();
    for (String p : paragraphs) {
      if (p == null || p.isBlank()) continue;
      if (sb.length() > 0) sb.append("\n\n");
      sb.append(p);
    }
    return safe(sb.toString());
  }

  private static String cleanSummary(String summary, String title) {
    String s = safe(summary);
    if (s == null) return null;

    if (title != null && !title.isBlank() && s.startsWith(title)) {
      s = safe(s.substring(Math.min(s.length(), title.length())));
      if (s == null) return null;
    }

    // Remove common article card boilerplate.
    String low = s.toLowerCase(Locale.ROOT);
    if (low.startsWith("read more")) return null;
    if (low.startsWith("watch:")) return null;
    if (low.startsWith("listen:")) return null;
    if (low.startsWith("advertisement")) return null;
    s = s.replace('\u00A0', ' ');
    s = s.replaceAll("[\\t\\r\\f]+", " ");
    s = s.replaceAll("\\s*\\n\\s*", "\n");
    s = s.replaceAll("\\n{3,}", "\n\n");
    return s;
  }

  private static String reflowSummaryParagraphs(String summary) {
    String s = safe(summary);
    if (s == null) return null;

    // Start from sentence-level text to avoid source markup causing one-line-per-sentence output.
    String linear = s.replace('\u00A0', ' ');
    linear = linear.replaceAll("[\\t\\r\\f]+", " ");
    linear = linear.replaceAll("\\s*\\n\\s*", " ");
    linear = linear.replaceAll("\\s{2,}", " ").trim();
    if (linear.isEmpty()) return null;

    List<String> sentences = splitSentences(linear);
    if (sentences.size() < 3) {
      return linear;
    }

    StringBuilder out = new StringBuilder(linear.length() + 32);
    int i = 0;
    while (i < sentences.size()) {
      int remaining = sentences.size() - i;
      int take;
      if (remaining <= 5) {
        take = remaining;
      } else {
        // Target 4 sentences/paragraph, but avoid ending with 1-2 sentence stragglers.
        int tailIfFour = remaining - 4;
        if (tailIfFour == 1) {
          take = 5;
        } else if (tailIfFour == 2) {
          take = 3;
        } else {
          take = 4;
        }
      }

      if (out.length() > 0) out.append("\n\n");
      for (int j = 0; j < take; j++) {
        if (j > 0) out.append(' ');
        out.append(sentences.get(i + j));
      }
      i += take;
    }

    return safe(out.toString());
  }

  private static List<String> splitSentences(String text) {
    String s = safe(text);
    if (s == null) return List.of();
    BreakIterator it = BreakIterator.getSentenceInstance(Locale.US);
    it.setText(s);
    List<String> out = new ArrayList<>();
    int start = it.first();
    for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
      String sentence = safe(s.substring(start, end));
      if (sentence != null) out.add(sentence);
    }
    if (out.isEmpty()) out.add(s);
    return out;
  }

  private static String buildDescription(
      String author, LocalDate date, String publisher, String summary) {
    StringBuilder sb = new StringBuilder();
    if (author != null) {
      sb.append("Author: ").append(author);
    }
    if (date != null) {
      if (sb.length() > 0) sb.append('\n');
      sb.append("Date: ").append(date);
    }
    if (publisher != null) {
      if (sb.length() > 0) sb.append('\n');
      sb.append("Publisher: ").append(publisher);
    }
    if (summary != null) {
      if (sb.length() > 0) sb.append("\n\n");
      sb.append("Summary:\n").append(summary);
    }
    return safe(sb.toString());
  }

  private static String cleanTitle(String title, String publisher) {
    String t = safe(normalizeText(title));
    if (t == null) return null;

    if (publisher != null) {
      String p = Pattern.quote(publisher);
      t = t.replaceFirst("(?i)\\s*[-:\\u2014|]\\s*" + p + "\\s*$", "").strip();
    }

    for (String suffix : TITLE_SUFFIXES) {
      t = t.replaceFirst("(?i)\\s*[-:\\u2014|]\\s*" + Pattern.quote(suffix) + "\\s*$", "").strip();
    }
    return safe(t);
  }

  private static String normalizePublisherName(String raw) {
    String t = normalizeText(raw);
    if (t == null) return null;
    String low = t.toLowerCase(Locale.ROOT);
    if (low.contains("reuters")) return "Reuters";
    if (low.contains("associated press") || low.equals("ap") || low.contains("ap news"))
      return "AP News";
    if (low.contains("new york times") || low.contains("nytimes")) return "New York Times";
    if (low.contains("bbc")) return "BBC";
    if (low.contains("cnn")) return "CNN";
    if (low.contains("washington post")) return "Washington Post";
    if (low.contains("guardian")) return "The Guardian";
    if (low.contains("npr")) return "NPR";
    if (low.contains("wall street journal") || low.equals("wsj")) return "Wall Street Journal";
    if (low.contains("abc news")) return "ABC News";
    return safe(t);
  }

  private static String publisherFromHost(String host) {
    PublisherProfile profile = profileForHost(host);
    if (profile == null) return null;
    return profile.displayName();
  }

  private static PublisherProfile profileForHost(String host) {
    String key = publisherKeyForHost(host);
    if (key == null) return DEFAULT_PROFILE;
    for (PublisherProfile profile : PUBLISHER_PROFILES) {
      if (Objects.equals(profile.key(), key)) return profile;
    }
    return DEFAULT_PROFILE;
  }

  private static String publisherKeyForHost(String host) {
    String h = normalizeHost(host);
    if (h == null) return null;
    for (HostProfile hp : HOST_PROFILES) {
      String suffix = hp.hostSuffix();
      if (h.equals(suffix) || h.endsWith("." + suffix)) {
        return hp.publisherKey();
      }
    }
    return null;
  }

  private static String firstImageBySelectors(Document doc, String[] selectors) {
    if (doc == null || selectors == null) return null;
    for (String selector : selectors) {
      if (selector == null || selector.isBlank()) continue;
      for (Element el : doc.select(selector)) {
        String value = null;
        if ("meta".equalsIgnoreCase(el.tagName())) {
          value = safe(el.attr("content"));
        } else {
          value = safe(el.attr("src"));
        }
        if (value == null) continue;
        String low = value.toLowerCase(Locale.ROOT);
        if (low.contains("sprite") || low.contains("logo") || low.endsWith(".svg")) continue;
        return value;
      }
    }
    return null;
  }

  private static String firstMetaValue(Document doc, String[] keys) {
    if (doc == null || keys == null) return null;
    for (String key : keys) {
      String value = meta(doc, key);
      if (value != null) return value;
    }
    return null;
  }

  private static String firstTextBySelectors(Document doc, String[] selectors, int maxLen) {
    if (doc == null || selectors == null) return null;
    for (String selector : selectors) {
      if (selector == null || selector.isBlank()) continue;
      for (Element el : doc.select(selector)) {
        if (el == null) continue;
        if ("meta".equalsIgnoreCase(el.tagName())) {
          String metaContent = safe(el.attr("content"));
          String cleaned = cleanAuthor(metaContent);
          if (cleaned != null) return cleaned;
          continue;
        }
        String txt = cleanAuthor(el.text());
        if (txt == null) continue;
        if (maxLen > 0 && txt.length() > maxLen) continue;
        return txt;
      }
    }
    return null;
  }

  private static String cleanAuthor(String author) {
    String t = normalizeText(author);
    if (t == null) return null;
    t = t.replaceFirst("(?i)^by\\s+", "");
    t = t.replaceFirst("(?i)^author:\\s*", "");
    t = t.replaceFirst("(?i)^updated\\s+", "");
    t = t.replaceFirst("(?i)^published\\s+", "");
    t = t.replaceAll("\\s*\\|\\s*.+$", "").strip();
    if (t.length() > 120) return null;

    String low = t.toLowerCase(Locale.ROOT);
    if (low.startsWith("http://") || low.startsWith("https://")) return null;
    if (low.contains("read more")) return null;
    if (low.contains("subscribe")) return null;
    if (low.matches(".*\\d{4}.*")) return null;
    return safe(t);
  }

  private static boolean looksLikeUsefulParagraph(String text, String title) {
    if (text == null) return false;
    if (text.length() < 35) return false;
    if (title != null && !title.isBlank() && text.equalsIgnoreCase(title.strip())) return false;

    String low = text.toLowerCase(Locale.ROOT);
    if (low.startsWith("by ")) return false;
    if (low.startsWith("read more")) return false;
    if (low.startsWith("watch:")) return false;
    if (low.startsWith("listen:")) return false;
    if (low.startsWith("sign up")) return false;
    if (low.startsWith("subscribe")) return false;
    if (low.startsWith("advertisement")) return false;
    if (low.startsWith("copyright")) return false;
    if (low.contains("all rights reserved")) return false;
    if (low.contains("cookie policy")) return false;
    if (low.contains("terms of use")) return false;
    return true;
  }

  private static LocalDate parseLocalDate(String raw) {
    String value = safe(raw);
    if (value == null) return null;

    // Unix epoch (seconds or milliseconds).
    if (value.matches("\\d{10,13}")) {
      try {
        long n = Long.parseLong(value);
        if (value.length() == 13) n = n / 1000L;
        return Instant.ofEpochSecond(n).atZone(ZoneId.systemDefault()).toLocalDate();
      } catch (Exception ignored) {
        // fall through
      }
    }

    try {
      return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate();
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate();
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return LocalDateTime.parse(value).toLocalDate();
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    // Some sites provide ISO date-time but LocalDate-friendly prefix.
    if (value.length() >= 10) {
      String prefix = value.substring(0, 10);
      try {
        return LocalDate.parse(prefix);
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
    return null;
  }

  private static String meta(Document doc, String key) {
    if (doc == null || key == null || key.isBlank()) return null;
    String value =
        firstNonBlank(
            contentOf(doc.selectFirst("meta[property='" + key + "']")),
            contentOf(doc.selectFirst("meta[name='" + key + "']")),
            contentOf(doc.selectFirst("meta[itemprop='" + key + "']")));
    return safe(value);
  }

  private static String contentOf(Element element) {
    if (element == null) return null;
    return safe(element.attr("content"));
  }

  private static String resolveAgainst(String baseUrl, String maybeUrl) {
    if (maybeUrl == null || maybeUrl.isBlank()) return null;
    try {
      URI u = URI.create(maybeUrl);
      if (u.isAbsolute()) return maybeUrl;
    } catch (Exception ignored) {
      // fall through
    }
    try {
      URI base = URI.create(Objects.toString(baseUrl, ""));
      if (base.isAbsolute()) {
        return base.resolve(maybeUrl).toString();
      }
    } catch (Exception ignored) {
      // fall through
    }
    return maybeUrl;
  }

  private static URI safeUri(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return URI.create(value);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String hostOf(String url) {
    URI uri = safeUri(url);
    if (uri == null) return null;
    return normalizeHost(uri.getHost());
  }

  private static String normalizeHost(String host) {
    String h = safe(host);
    if (h == null) return null;
    h = h.toLowerCase(Locale.ROOT);
    if (h.startsWith("www.")) h = h.substring(4);
    return safe(h);
  }

  private static String normalizePath(String path) {
    String p = safe(path);
    if (p == null) return null;
    p = p.replaceAll("/{2,}", "/");
    if (!p.startsWith("/")) p = "/" + p;
    return safe(p.toLowerCase(Locale.ROOT));
  }

  private static String normalizeParagraphText(String text) {
    String t = normalizeText(text);
    if (t == null) return null;
    return safe(t);
  }

  private static String normalizeText(String text) {
    if (text == null) return null;
    String t = text.replace('\u00A0', ' ');
    t = t.replaceAll("[\\t\\r\\n]+", " ");
    t = t.replaceAll("\\s{2,}", " ");
    return safe(t);
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  @SafeVarargs
  private static <T> T firstNonNull(T... values) {
    if (values == null) return null;
    for (T value : values) {
      if (value != null) return value;
    }
    return null;
  }

  private static String safe(String s) {
    if (s == null) return null;
    String t = s.strip();
    return t.isEmpty() ? null : t;
  }

  private record HostProfile(String hostSuffix, String publisherKey) {}

  private record PublisherProfile(
      String key,
      String displayName,
      String[] paragraphSelectors,
      String[] bylineSelectors,
      String[] imageSelectors,
      String[] authorMetaKeys,
      String[] dateMetaKeys) {}
}

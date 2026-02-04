package cafe.woden.ircclient.ui.chat.embed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RottenTomatoesLinkPreviewResolver implements LinkPreviewResolver {

  private static final Logger log = LoggerFactory.getLogger(RottenTomatoesLinkPreviewResolver.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  
  private static final int MAX_BYTES = 2 * 1024 * 1024; // 2 MiB

  // JSON-LD "@type" values we accept.
  private static final List<String> SUPPORTED_TYPES = List.of(
      "Movie",
      "TVSeries",
      "TVSeason",
      "TVEpisode"
  );

  private static final Pattern TOMATOMETER_ATTR = Pattern.compile(
      "tomatometerscore\\s*=\\s*\"(\\d{1,3})\"",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern AUDIENCE_ATTR = Pattern.compile(
      "audiencescore\\s*=\\s*\"(\\d{1,3})\"",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern TOMATOMETER_TEXT = Pattern.compile(
      "\\b(\\d{1,3})%\\s*Tomatometer\\b",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern POPCORN_TEXT = Pattern.compile(
      "\\b(\\d{1,3})%\\s*Popcornmeter\\b",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern RUNTIME = Pattern.compile(
      "\\b(\\d{1,2})h\\s*(\\d{1,2})m\\b",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern RUNTIME_MIN = Pattern.compile(
      "\\b(\\d{1,3})m\\b",
      Pattern.CASE_INSENSITIVE);

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) {
    try {
      if (!RottenTomatoesPreviewUtil.isRottenTomatoesTitleUri(uri)) return null;

      URI target = RottenTomatoesPreviewUtil.canonicalize(uri);

      var resp = http.getStream(target, "text/html,application/xhtml+xml", null);
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) return null;

      String ct = resp.headers().firstValue("content-type").orElse(null);
      if (!PreviewHttp.looksLikeHtml(ct)) return null;

      byte[] bytes = PreviewHttp.readUpTo(resp.body(), MAX_BYTES);
      if (bytes.length == 0) return null;

      // jsoup will sniff charset when charsetName is null.
      Document doc = Jsoup.parse(new String(bytes, StandardCharsets.UTF_8), target.toString());

      // Scores (tomatometer + popcornmeter)
      String tomato = null;
      String popcorn = null;
      Element sb = doc.selectFirst("score-board");
      if (sb != null) {
        tomato = blankToNull(sb.attr("tomatometerscore"));
        popcorn = blankToNull(sb.attr("audiencescore"));
      }
      if (tomato == null || popcorn == null) {
        String html = new String(bytes, StandardCharsets.UTF_8);
        if (tomato == null) tomato = firstGroup(TOMATOMETER_ATTR, html);
        if (popcorn == null) popcorn = firstGroup(AUDIENCE_ATTR, html);
      }
      // Last-resort: parse from visible text.
      String visible = doc.text();
      if (tomato == null) tomato = firstGroup(TOMATOMETER_TEXT, visible);
      if (popcorn == null) popcorn = firstGroup(POPCORN_TEXT, visible);

      // Title + synopsis + poster + year + runtime + rating + credits from JSON-LD when possible.
      JsonNode titleNode = findTitleNode(doc);

      String name = (titleNode != null) ? text(titleNode, "name") : null;
      if (name == null || name.isBlank()) {
        name = firstNonBlank(
            meta(doc, "property", "og:title"),
            meta(doc, "name", "twitter:title"),
            doc.title()
        );
        // og:title often looks like "Steve (2025) - Rotten Tomatoes"; trim suffix.
        if (name != null) {
          name = name.replace("| Rotten Tomatoes", "").replace("- Rotten Tomatoes", "").strip();
        }
      }
      if (name == null || name.isBlank()) return null;

      String year = null;
      String rating = null;
      String runtime = null;
      String summary = null;
      String director = null;
      String cast = null;

      if (titleNode != null) {
        year = ImdbPreviewUtil.yearFromDatePublished(text(titleNode, "datePublished"));
        rating = text(titleNode, "contentRating");
        Duration dur = ImdbPreviewUtil.parseIsoDuration(text(titleNode, "duration"));
        runtime = ImdbPreviewUtil.formatRuntime(dur);
        summary = text(titleNode, "description");
        director = joinPeople(titleNode.get("director"), 3);
        if (director == null) {
          // TV pages sometimes use "creator".
          director = joinPeople(titleNode.get("creator"), 3);
        }
        cast = joinPeople(titleNode.get("actor"), 6);
      }
      // Prefer the "Synopsis" from the Movie/Series Info section (much better than meta/marketing copy).
      String synopsis = firstNonBlank(
          textOf(doc.selectFirst("[data-qa=movie-info-synopsis]")),
          textOf(doc.selectFirst("#movieSynopsis")),
          textOf(doc.selectFirst("[data-qa=series-info-synopsis]")),
          textOf(doc.selectFirst("#seriesSynopsis")),
          extractSynopsisFromVisibleText(visible)
      );
      if (synopsis != null && !synopsis.isBlank()) summary = synopsis;

// Runtime fallback: parse "1h 33m" from visible header text.
      if (runtime == null) runtime = parseRuntime(visible);

      // Poster image.
      String imageUrl = null;
      if (titleNode != null) {
        imageUrl = normalizeImageUrl(target, firstImageUrl(titleNode.get("image")));
        if (imageUrl == null) {
          imageUrl = normalizeImageUrl(target, firstImageUrl(titleNode.get("thumbnailUrl")));
        }
      }
      if (imageUrl == null) {
        imageUrl = normalizeImageUrl(target,
            firstNonBlank(
                meta(doc, "property", "og:image"),
                meta(doc, "property", "og:image:url"),
                meta(doc, "property", "og:image:secure_url"),
                meta(doc, "name", "twitter:image"),
                meta(doc, "name", "twitter:image:src")
            ));
      }

      // Compose the description in the same multi-line format as IMDb so the UI can render it
      // with a bold meta line + credits + full synopsis.
      String details = joinDot(" • ",
          blankToNull(year),
          blankToNull(rating),
          blankToNull(runtime),
          (tomato != null) ? ("Tomatometer " + tomato + "%") : null,
          (popcorn != null) ? ("Popcornmeter " + popcorn + "%") : null
      );

      StringBuilder desc = new StringBuilder();
      if (details != null && !details.isBlank()) desc.append(details);

      if (director != null && !director.isBlank()) {
        if (!desc.isEmpty()) desc.append("\n");
        desc.append("Director: ").append(director);
      }
      if (cast != null && !cast.isBlank()) {
        if (!desc.isEmpty()) desc.append("\n");
        desc.append("Cast: ").append(cast);
      }
      if (summary != null && !summary.isBlank()) {
        if (!desc.isEmpty()) desc.append("\n");
        desc.append(summary.strip());
      }

      String finalDesc = desc.toString().strip();
      return new LinkPreview(
          target.toString(),
          name.strip(),
          finalDesc,
          "Rotten Tomatoes",
          imageUrl,
          (imageUrl != null && !imageUrl.isBlank()) ? 1 : 0
      );
    } catch (Exception ex) {
      log.warn("Rotten Tomatoes preview resolve failed for {}: {}", originalUrl, ex.toString());
      return null;
    }
  }

  private static String parseRuntime(String text) {
    if (text == null || text.isBlank()) return null;

    Matcher m = RUNTIME.matcher(text);
    if (m.find()) {
      try {
        int h = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));
        if (h <= 0 && min <= 0) return null;
        if (h <= 0) return min + "m";
        if (min <= 0) return h + "h";
        return h + "h " + min + "m";
      } catch (Exception ignored) {
        // fall through
      }
    }

    // Sometimes pages show only minutes (rare); avoid matching "95 Reviews" etc.
    m = RUNTIME_MIN.matcher(text);
    if (m.find()) {
      try {
        int min = Integer.parseInt(m.group(1));
        return (min > 0) ? (min + "m") : null;
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }

  private static String textOf(Element el) {
    if (el == null) return null;
    String t = el.text();
    return (t == null || t.isBlank()) ? null : t;
  }

  private static String meta(Document doc, String attr, String key) {
    if (doc == null || attr == null || key == null) return null;
    try {
      Element el = doc.selectFirst("meta[" + attr + "='" + key + "']");
      if (el == null) return null;
      String c = el.attr("content");
      return blankToNull(c);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String firstImageUrl(JsonNode imageNode) {
    if (imageNode == null || imageNode.isMissingNode() || imageNode.isNull()) return null;
    if (imageNode.isTextual()) return blankToNull(imageNode.asText());
    if (imageNode.isArray() && imageNode.size() > 0) {
      return firstImageUrl(imageNode.get(0));
    }
    if (imageNode.isObject()) {
      // Some JSON-LD uses {"@type":"ImageObject","url":"..."}
      String url = text(imageNode, "url");
      if (url != null) return url;
    }
    return null;
  }

  private static String normalizeImageUrl(URI base, String url) {
    String t = blankToNull(url);
    if (t == null) return null;
    if (t.startsWith("//")) t = "https:" + t;
    try {
      URI u = URI.create(t);
      if (!u.isAbsolute() && base != null) {
        t = base.resolve(t).toString();
      }
    } catch (Exception ignored) {
      return null;
    }
    return blankToNull(t);
  }

  private static JsonNode findTitleNode(Document doc) {
    if (doc == null) return null;
    for (Element el : doc.select("script[type=application/ld+json]")) {
      String data = el.data();
      if (data == null || data.isBlank()) data = el.html();
      if (data == null || data.isBlank()) continue;

      String lower = data.toLowerCase(Locale.ROOT);
      if (!lower.contains("\"@type\"") || !lower.contains("\"name\"")) continue;

      try {
        JsonNode root = MAPPER.readTree(data);
        JsonNode picked = pickTitleNode(root);
        if (picked != null) return picked;
      } catch (Exception ignored) {
        // continue
      }
    }
    return null;
  }

  private static JsonNode pickTitleNode(JsonNode root) {
    if (root == null) return null;

    if (root.isArray()) {
      for (JsonNode n : root) {
        JsonNode picked = pickTitleNode(n);
        if (picked != null) return picked;
      }
      return null;
    }

    JsonNode graph = root.get("@graph");
    if (graph != null && graph.isArray()) {
      for (JsonNode n : graph) {
        JsonNode picked = pickTitleNode(n);
        if (picked != null) return picked;
      }
      return null;
    }

    // @type can be a string or an array
    JsonNode typeNode = root.get("@type");
    if (typeNode != null) {
      if (typeNode.isTextual()) {
        String t = typeNode.asText();
        if (isSupportedType(t)) return root;
      } else if (typeNode.isArray()) {
        for (JsonNode t : typeNode) {
          if (t != null && t.isTextual() && isSupportedType(t.asText())) return root;
        }
      }
    }

    return null;
  }

  private static boolean isSupportedType(String t) {
    if (t == null) return false;
    for (String s : SUPPORTED_TYPES) {
      if (s.equalsIgnoreCase(t)) return true;
    }
    return false;
  }

  private static String text(JsonNode node, String field) {
    if (node == null || field == null) return null;
    JsonNode v = node.get(field);
    if (v == null || v.isNull() || v.isMissingNode()) return null;
    if (v.isTextual()) return blankToNull(v.asText());
    if (v.isNumber()) return blankToNull(v.asText());
    return null;
  }

  private static String joinPeople(JsonNode node, int max) {
    if (node == null || node.isNull() || node.isMissingNode()) return null;
    List<String> names = new ArrayList<>();

    if (node.isArray()) {
      for (JsonNode n : node) {
        String nm = personName(n);
        if (nm != null) names.add(nm);
        if (max > 0 && names.size() >= max) break;
      }
    } else {
      String nm = personName(node);
      if (nm != null) names.add(nm);
    }

    if (names.isEmpty()) return null;
    return String.join(", ", names);
  }

  private static String personName(JsonNode n) {
    if (n == null || n.isNull() || n.isMissingNode()) return null;
    if (n.isTextual()) return blankToNull(n.asText());
    if (n.isObject()) {
      String name = text(n, "name");
      if (name != null) return name;
    }
    return null;
  }

  private static String firstNonBlank(String... vals) {
    if (vals == null) return null;
    for (String v : vals) {
      String t = blankToNull(v);
      if (t != null) return t;
    }
    return null;
  }

  private static String firstGroup(Pattern p, String s) {
    if (p == null || s == null) return null;
    Matcher m = p.matcher(s);
    if (m.find()) return blankToNull(m.group(1));
    return null;
  }

  private static String joinDot(String delim, String... parts) {
    if (parts == null || parts.length == 0) return null;
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      String t = blankToNull(p);
      if (t == null) continue;
      if (!sb.isEmpty()) sb.append(delim);
      sb.append(t);
    }
    return sb.isEmpty() ? null : sb.toString();
  }

  /** Rotten Tomatoes pages sometimes put the real synopsis only in the rendered "Movie Info"/"Series Info" section, while meta/JSON-LD descrip… */
private static String extractSynopsisFromVisibleText(String visible) {
    String t = blankToNull(visible);
    if (t == null) return null;

    // Normalize whitespace to make substring/regex matching predictable.
    t = t.replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();

    String[] anchors = {
        "Movie Info Synopsis",
        "Series Info Synopsis",
        "Tv Info Synopsis",
        "TV Info Synopsis",
        "Show Info Synopsis"
    };

    for (String anchor : anchors) {
      int a = t.indexOf(anchor);
      if (a < 0) continue;
      int start = a + anchor.length();
      int end = findSynopsisEnd(t, start);
      if (end > start) {
        String syn = blankToNull(t.substring(start, end));
        if (syn != null) return syn;
      }
    }

    // Fallback: synopsis sometimes appears right under the score section (after Tomatometer/Popcornmeter).
    // Try to capture the block before obvious navigation headers.
    java.util.regex.Pattern p = java.util.regex.Pattern.compile(
        "\\bPopcornmeter\\b.*?\\bRatings\\b\\s+(.*?)(?=\\bWatch on\\b|\\bWhere to Watch\\b|\\bWhat to Know\\b|\\bReviews\\b|\\bCast & Crew\\b)",
        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL
    );
    java.util.regex.Matcher m = p.matcher(t);
    if (m.find()) {
      String syn = blankToNull(m.group(1));
      if (syn != null) return syn;
    }

    return null;
  }

  private static int findSynopsisEnd(String text, int from) {
    String[] stops = {
        " Director ",
        " Creator ",
        " Producer ",
        " Network ",
        " Distributor ",
        " Production Co ",
        " Rating ",
        " Genre ",
        " Original Language ",
        " Release Date ",
        " Runtime ",
        " Where to Watch ",
        " What to Know ",
        " Reviews ",
        " Cast & Crew "
    };
    int end = -1;
    for (String s : stops) {
      int i = text.indexOf(s, from);
      if (i >= 0 && (end < 0 || i < end)) end = i;
    }
    return end < 0 ? text.length() : end;
  }

  private static String blankToNull(String s) {
    if (s == null) return null;
    String t = s.strip();
    return t.isEmpty() ? null : t;
  }
}

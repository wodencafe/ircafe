package cafe.woden.ircclient.ui.chat.embed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

final class ImdbLinkPreviewResolver implements LinkPreviewResolver {

  private static final Logger log = LoggerFactory.getLogger(ImdbLinkPreviewResolver.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // JSON-LD "@type" values we treat as a title page.
  private static final List<String> SUPPORTED_TYPES = List.of(
      "Movie",
      "TVSeries",
      "TVEpisode",
      "VideoGame"
  );

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) {
    try {
      if (!ImdbPreviewUtil.isImdbTitleUri(uri)) return null;

      String id = ImdbPreviewUtil.extractTitleId(uri);
      URI canonical = id != null ? ImdbPreviewUtil.canonicalTitleUri(id) : null;
      URI target = canonical != null ? canonical : uri;

      // IMDb increasingly serves anti-bot/JS interstitial pages to non-browser clients.
      // Use a browser-ish UA to improve our odds of getting the real HTML with JSON-LD.
      var resp = http.getString(
          target,
          "text/html,application/xhtml+xml",
          PreviewHttp.headers("User-Agent", PreviewHttp.BROWSER_USER_AGENT)
      );
      int status = resp.statusCode();
      if (status < 200 || status >= 300) {
        log.debug("IMDb preview: HTTP {} for {}", status, target);
        return null;
      }

      String html = resp.body();
      if (html == null || html.isBlank()) return null;

      if (looksLikeBotOrJsInterstitial(html)) {
        String ct = PreviewHttp.header(resp, "content-type").orElse("");
        log.info("IMDb preview: blocked by bot/JS interstitial for {} (content-type: {})", target, ct);
        return null;
      }

      Document doc = Jsoup.parse(html, target.toString());
      JsonNode titleNode = findTitleNode(doc);
      if (titleNode == null) {
        log.debug("IMDb preview: no JSON-LD title node found for {}", target);
        return null;
      }

      String name = text(titleNode, "name");
      if (name == null || name.isBlank()) return null;

      String datePublished = text(titleNode, "datePublished");
      String year = ImdbPreviewUtil.yearFromDatePublished(datePublished);

      String contentRating = text(titleNode, "contentRating");

      String durIso = text(titleNode, "duration");
      Duration dur = ImdbPreviewUtil.parseIsoDuration(durIso);
      String runtime = ImdbPreviewUtil.formatRuntime(dur);

      String score = null;
      JsonNode agg = titleNode.path("aggregateRating");
      if (agg != null && !agg.isMissingNode()) {
        score = text(agg, "ratingValue");
      }

      String summary = text(titleNode, "description");
      if (summary != null) summary = summary.strip();

      String director = joinPeople(titleNode.get("director"), 3);
      String cast = joinPeople(titleNode.get("actor"), 6);

      String imageUrl = normalizeImageUrl(target, firstImageUrl(titleNode.get("image")));
      if (imageUrl == null) {
        // Some JSON-LD variants use thumbnailUrl instead of image.
        imageUrl = normalizeImageUrl(target, firstImageUrl(titleNode.get("thumbnailUrl")));
      }
      if (imageUrl == null) {
        // Fallback to OG/Twitter images if the JSON-LD doesn't include an image (or is shaped oddly).
        imageUrl = normalizeImageUrl(target,
            firstNonBlank(
                metaImage(doc, "meta[property=og:image]"),
                metaImage(doc, "meta[property=og:image:url]"),
                metaImage(doc, "meta[property=og:image:secure_url]"),
                metaImage(doc, "meta[name=twitter:image]"),
                metaImage(doc, "meta[property=twitter:image]")
            ));
      }

      if (imageUrl == null || imageUrl.isBlank()) {
        log.warn("IMDb preview: no poster image found for {}", target);
      } else {
        // IMDb posters often default to a huge Amazon CDN image. Prefer a sized variant to
        // reduce bandwidth/memory and stay under image size guardrails.
        imageUrl = ImdbPreviewUtil.maybeSizeAmazonPosterUrl(imageUrl, 256);
      }

      String details = joinDot(" • ",
          year,
          blankToNull(contentRating),
          runtime,
          (score != null && !score.isBlank()) ? ("IMDb " + score.strip() + "/10") : null
      );

      StringBuilder desc = new StringBuilder();
      if (details != null && !details.isBlank()) {
        desc.append(details);
      }
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
        desc.append(summary);
      }

      String finalDesc = desc.toString().strip();
      return new LinkPreview(
          target.toString(),
          name,
          finalDesc,
          "IMDb",
          imageUrl,
          imageUrl != null && !imageUrl.isBlank() ? 1 : 0
      );
    } catch (Exception ex) {
      // Keep resolver best-effort; log so we can diagnose broken pages/CDN blocks.
      log.warn("IMDb preview resolve failed for {}: {}", originalUrl, ex.toString());
      return null;
    }
  }

  private static boolean looksLikeBotOrJsInterstitial(String html) {
    if (html == null || html.isBlank()) return false;
    String lower = html.toLowerCase(Locale.ROOT);
    // What IMDb commonly serves to non-browser / blocked clients.
    if (lower.contains("verify that you're not a robot")) return true;
    if (lower.contains("javascript is disabled")) return true;
    if (lower.contains("enable javascript and then reload")) return true;
    if (lower.contains("robot check")) return true;
    return false;
  }

  private static String metaImage(Document doc, String cssQuery) {
    if (doc == null || cssQuery == null) return null;
    Element el = doc.selectFirst(cssQuery);
    if (el == null) return null;
    // Use abs:content so relative URLs resolve against the doc base URI.
    String abs = el.attr("abs:content");
    if (abs != null && !abs.isBlank()) return abs;
    String raw = el.attr("content");
    return (raw == null || raw.isBlank()) ? null : raw;
  }

  private static String firstNonBlank(String... vals) {
    if (vals == null) return null;
    for (String v : vals) {
      String t = blankToNull(v);
      if (t != null) return t;
    }
    return null;
  }

  private static String normalizeImageUrl(URI base, String url) {
    String t = blankToNull(url);
    if (t == null) return null;
    // Protocol-relative URLs.
    if (t.startsWith("//")) {
      t = "https:" + t;
    }
    try {
      URI u = URI.create(t);
      if (!u.isAbsolute() && base != null) {
        t = base.resolve(t).toString();
      }
    } catch (Exception ignored) {
      // If it's malformed, just drop it.
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

      // Fast prefilter so we don't attempt to parse unrelated LD.
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

    // Sometimes it's an array of objects.
    if (root.isArray()) {
      for (JsonNode n : root) {
        JsonNode picked = pickTitleNode(n);
        if (picked != null) return picked;
      }
      return null;
    }

    // Sometimes the actual nodes are inside @graph.
    JsonNode graph = root.get("@graph");
    if (graph != null && graph.isArray()) {
      for (JsonNode n : graph) {
        JsonNode picked = pickTitleNode(n);
        if (picked != null) return picked;
      }
      return null;
    }

    String type = text(root, "@type");
    if (type != null) {
      // @type can itself be an array in JSON-LD.
      if (SUPPORTED_TYPES.stream().anyMatch(t -> t.equalsIgnoreCase(type))) {
        return root;
      }
    }

    return null;
  }

  private static String firstImageUrl(JsonNode imageNode) {
    if (imageNode == null || imageNode.isMissingNode() || imageNode.isNull()) return null;
    if (imageNode.isTextual()) return blankToNull(imageNode.asText());
    if (imageNode.isArray() && imageNode.size() > 0) {
      return firstImageUrl(imageNode.get(0));
    }
    if (imageNode.isObject()) {
      // Common JSON-LD shapes: {"@type":"ImageObject","url":"..."}
      String u = text(imageNode, "url");
      if (u != null) return u;
      u = text(imageNode, "contentUrl");
      if (u != null) return u;
    }
    return null;
  }

  private static String joinPeople(JsonNode node, int limit) {
    if (node == null || node.isMissingNode() || node.isNull()) return null;
    List<String> names = new ArrayList<>();

    if (node.isArray()) {
      for (JsonNode n : node) {
        if (names.size() >= Math.max(1, limit)) break;
        String nm = personName(n);
        if (nm != null) names.add(nm);
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
    if (n.isObject()) return blankToNull(text(n, "name"));
    return null;
  }

  private static String text(JsonNode node, String key) {
    if (node == null || key == null) return null;
    JsonNode v = node.get(key);
    if (v == null || v.isMissingNode() || v.isNull()) return null;
    if (v.isTextual()) return blankToNull(v.asText());
    // In JSON-LD, @type can be an array. Use the first element.
    if (v.isArray() && v.size() > 0 && v.get(0).isTextual()) return blankToNull(v.get(0).asText());
    // Sometimes values are numbers.
    if (v.isNumber()) return blankToNull(v.asText());
    return null;
  }

  private static String blankToNull(String s) {
    if (s == null) return null;
    String t = s.strip();
    return t.isEmpty() ? null : t;
  }

  private static String joinDot(String sep, String... parts) {
    if (parts == null || parts.length == 0) return null;
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      String t = blankToNull(p);
      if (t == null) continue;
      if (!sb.isEmpty()) sb.append(sep);
      sb.append(t);
    }
    return sb.isEmpty() ? null : sb.toString();
  }
}
package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;
import java.util.Objects;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

final class LinkPreviewParser {

  private LinkPreviewParser() {}

  static LinkPreview parse(Document doc, String requestedUrl) {
    if (doc == null) {
      return new LinkPreview(requestedUrl, null, null, hostOf(requestedUrl), null, 0);
    }

    String baseUrl = firstNonBlank(
        meta(doc, "property", "og:url"),
        canonical(doc),
        requestedUrl
    );

    String title = firstNonBlank(
        meta(doc, "property", "og:title"),
        meta(doc, "name", "twitter:title"),
        safe(doc.title())
    );

    String description = firstNonBlank(
        meta(doc, "property", "og:description"),
        meta(doc, "name", "twitter:description"),
        meta(doc, "name", "description")
    );

    String siteName = firstNonBlank(
        meta(doc, "property", "og:site_name"),
        hostOf(baseUrl)
    );

    String image = firstNonBlank(
        meta(doc, "property", "og:image:secure_url"),
        meta(doc, "property", "og:image"),
        meta(doc, "name", "twitter:image"),
        meta(doc, "name", "twitter:image:src")
    );
    image = resolveAgainst(baseUrl, image);

    int mediaCount = (image != null && !image.isBlank()) ? 1 : 0;
    return new LinkPreview(baseUrl, title, description, siteName, image, mediaCount);
  }

  private static String canonical(Document doc) {
    try {
      Element el = doc.selectFirst("link[rel=canonical]");
      if (el == null) return null;
      return safe(el.attr("href"));
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String meta(Document doc, String attr, String key) {
    try {
      Element el = doc.selectFirst("meta[" + attr + "='" + key + "']");
      if (el == null) return null;
      return safe(el.attr("content"));
    } catch (Exception ignored) {
      return null;
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
      URI base = URI.create(Objects.toString(baseUrl, ""));
      if (base.isAbsolute()) {
        return base.resolve(maybeUrl).toString();
      }
    } catch (Exception ignored) {
    }
    return maybeUrl;
  }

  private static String hostOf(String url) {
    if (url == null || url.isBlank()) return null;
    try {
      URI u = URI.create(url);
      return safe(u.getHost());
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  private static String safe(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}

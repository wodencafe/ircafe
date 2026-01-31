package cafe.woden.ircclient.ui.chat.embed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;

/**
 * Mastodon status preview via the public instance API.
 *
 * <p>Many Mastodon instances return oEmbed HTML that is only a thin wrapper unless JS runs.
 * The REST API provides the actual status content (as HTML) without requiring auth for
 * public posts.
 */
final class MastodonStatusApiPreviewResolver implements LinkPreviewResolver {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) throws Exception {
    if (uri == null || originalUrl == null || http == null) return null;

    String host = uri.getHost();
    String path = uri.getPath();
    if (host == null || host.isBlank() || path == null || path.isBlank()) return null;

    // Only apply to URLs that look like Mastodon status pages.
    if (!looksLikeMastodonStatusPath(path)) return null;

    String statusId = extractNumericStatusId(path);
    if (statusId == null) return null;

    // Build instance API URL (same instance that served the link).
    if (uri.getScheme() == null || uri.getAuthority() == null) return null;
    String base = uri.getScheme() + "://" + uri.getAuthority();
    URI api = URI.create(base + "/api/v1/statuses/" + statusId);

    var resp = http.getString(api, "application/json", null);
    // Public posts are usually 200; private/deleted often 401/403/404.
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) return null;

    String json = resp.body();
    if (json == null || json.isBlank()) return null;

    JsonNode root = JSON.readTree(json);
    if (root == null || root.isNull()) return null;

    // Boosts/renotes: the interesting content is inside reblog.
    JsonNode status = root.hasNonNull("reblog") ? root.get("reblog") : root;

    String contentHtml = text(status.get("content"));
    String spoiler = text(status.get("spoiler_text"));
    boolean sensitive = status.path("sensitive").asBoolean(false);

    JsonNode account = status.get("account");
    String displayName = text(account == null ? null : account.get("display_name"));
    String acct = text(account == null ? null : account.get("acct"));
    String username = text(account == null ? null : account.get("username"));
    String author = firstNonBlank(displayName, acct, username);

    // Description: CW + plain-text content.
    String bodyText = null;
    if (contentHtml != null && !contentHtml.isBlank()) {
      bodyText = org.jsoup.Jsoup.parseBodyFragment(contentHtml).text();
      bodyText = stripToNull(bodyText);
    }

    String description = null;
    if (spoiler != null && !spoiler.isBlank()) {
      description = "CW: " + spoiler.strip();
      if (bodyText != null && !bodyText.isBlank()) {
        description = description + "\n" + bodyText;
      }
    } else {
      description = bodyText;
    }

    // Try to find a representative image.
    String imageUrl = null;
    int mediaCount = 0;
    JsonNode media = status.get("media_attachments");
    if (media != null && media.isArray() && media.size() > 0) {
      mediaCount = media.size();
      JsonNode first = media.get(0);
      imageUrl = firstNonBlank(text(first.get("preview_url")), text(first.get("url")));
    }
    // Some statuses include a card (OG-ish) with an image.
    if (imageUrl == null) {
      JsonNode card = status.get("card");
      if (card != null && card.isObject()) {
        imageUrl = stripToNull(text(card.get("image")));
      }
    }

    if (mediaCount == 0 && imageUrl != null && !imageUrl.isBlank()) {
      mediaCount = 1;
    }
    // If sensitive, don't show the image thumbnail by default.
    if (sensitive) {
      imageUrl = null;
    }

    String siteName = host.startsWith("www.") ? host.substring(4) : host;
    String title;
    if (author != null) {
      title = "Post by " + author;
    } else {
      title = "Mastodon post";
    }

    // If we still have no details, bail so oEmbed/OG can try.
    if ((description == null || description.isBlank()) && (imageUrl == null || imageUrl.isBlank())) {
      return null;
    }

    // Clamp description so we don't explode layout.
    description = clamp(description, 600);

    return new LinkPreview(originalUrl, title, description, siteName, imageUrl, mediaCount);
  }

  private static boolean looksLikeMastodonStatusPath(String path) {
    if (path == null) return false;
    String p = path.strip();
    if (p.isEmpty()) return false;
    while (p.endsWith("/")) p = p.substring(0, p.length() - 1);

    // Common Mastodon URL shapes:
    //   /@user/123456
    //   /users/user/statuses/123456
    //   /web/statuses/123456
    return p.matches("^/@[^/]+/\\d+(?:/.*)?$")
        || p.matches("^/users/[^/]+/statuses/\\d+(?:/.*)?$")
        || p.matches("^/web/statuses/\\d+(?:/.*)?$");
  }

  private static String extractNumericStatusId(String path) {
    if (path == null) return null;
    String p = path;
    while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
    String[] parts = p.split("/");
    for (int i = parts.length - 1; i >= 0; i--) {
      String seg = parts[i];
      if (seg == null || seg.isBlank()) continue;
      // IDs are numeric; accept fairly small IDs too.
      if (seg.chars().allMatch(Character::isDigit)) {
        return seg;
      }
    }
    return null;
  }

  private static String text(JsonNode n) {
    if (n == null || n.isNull()) return null;
    String v = n.asText(null);
    return stripToNull(v);
  }

  private static String stripToNull(String s) {
    if (s == null) return null;
    String v = s.strip();
    return v.isEmpty() ? null : v;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v.strip();
    }
    return null;
  }

  private static String clamp(String s, int max) {
    if (s == null) return null;
    String v = s.strip();
    if (v.length() <= max) return v;
    return v.substring(0, Math.max(0, max)).strip() + "…";
  }
}

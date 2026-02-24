package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;
import java.util.Locale;

/** Best-effort resolver for Reddit posts using the public {@code .json} endpoint. */
final class RedditLinkPreviewResolver implements LinkPreviewResolver {

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) {
    try {
      if (uri == null || originalUrl == null) return null;
      String host = uri.getHost();
      if (host == null || host.isBlank()) return null;

      String h = host.toLowerCase(Locale.ROOT);
      if (!(h.equals("reddit.com") || h.endsWith(".reddit.com"))) {
        // Note: redd.it shortlinks are redirects; we let OpenGraph handle them.
        return null;
      }

      if (!looksLikePostPath(uri.getPath())) {
        return null;
      }

      URI jsonUri = redditJsonUri(uri);
      var resp = http.getString(jsonUri, "application/json", null);
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        return null;
      }

      String json = resp.body();
      if (json == null || json.isBlank()) return null;

      // Top-level is an array: [postListing, commentsListing]. Grab the first listing.
      String listing0 = TinyJson.firstObjectInArray(json);
      if (listing0 == null) return null;
      String data0 = TinyJson.findObject(listing0, "data");
      if (data0 == null) return null;

      String children = TinyJson.findArray(data0, "children");
      if (children == null) return null;
      String child0 = TinyJson.firstObjectInArray(children);
      if (child0 == null) return null;
      String post = TinyJson.findObject(child0, "data");
      if (post == null) return null;

      String title = TinyJson.findString(post, "title");
      String subreddit =
          firstNonBlank(
              TinyJson.findString(post, "subreddit_name_prefixed"),
              TinyJson.findString(post, "subreddit"));
      if (subreddit != null && !subreddit.startsWith("r/")) subreddit = "r/" + subreddit;
      String author = TinyJson.findString(post, "author");
      String selfText = TinyJson.findString(post, "selftext");
      String permalink = TinyJson.findString(post, "permalink");

      String image = bestImageUrl(post);

      String canonical = canonicalUrl(permalink, originalUrl);

      String details = buildDetailsLine(subreddit, author);
      String snippet = null;
      if (selfText != null && !selfText.isBlank()) {
        snippet = PreviewTextUtil.trimToSentence(selfText, 900);
      }
      String desc = joinLines(details, snippet);

      if (title == null || title.isBlank()) {
        title = subreddit != null ? subreddit : "Reddit";
      }

      return new LinkPreview(canonical, title, desc, "Reddit", image, image != null ? 1 : 0);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static boolean looksLikePostPath(String path) {
    if (path == null || path.isBlank()) return false;
    // Common post permalinks:
    //   /r/<sub>/comments/<id>/<slug>/
    //   /comments/<id>/<slug>/
    String p = path;
    while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
    String[] seg = p.split("/");
    // seg[0] is "" (leading slash)
    if (seg.length >= 5 && "r".equals(seg[1]) && "comments".equals(seg[3])) {
      return true;
    }
    if (seg.length >= 3 && "comments".equals(seg[1])) {
      return true;
    }
    return false;
  }

  private static URI redditJsonUri(URI postUri) {
    String scheme = postUri.getScheme() == null ? "https" : postUri.getScheme();
    String host = postUri.getHost() == null ? "www.reddit.com" : postUri.getHost();
    String path = postUri.getPath() == null ? "" : postUri.getPath();
    String p = path;
    while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
    String base = scheme + "://" + host + p;
    if (!base.endsWith(".json")) base = base + ".json";
    return URI.create(base + "?raw_json=1");
  }

  private static String bestImageUrl(String post) {
    if (post == null) return null;

    // Prefer preview.images[0].source.url.
    String preview = TinyJson.findObject(post, "preview");
    if (preview != null) {
      String images = TinyJson.findArray(preview, "images");
      String img0 = TinyJson.firstObjectInArray(images);
      if (img0 != null) {
        String source = TinyJson.findObject(img0, "source");
        if (source != null) {
          String url = TinyJson.findString(source, "url");
          url = normalizeUrl(url);
          if (looksLikeHttpUrl(url)) return url;
        }
      }
    }

    // Fallback to thumbnail if it looks like a URL.
    String thumb = normalizeUrl(TinyJson.findString(post, "thumbnail"));
    if (looksLikeHttpUrl(thumb)) {
      // Reddit uses sentinel values here sometimes.
      String lower = thumb.toLowerCase(Locale.ROOT);
      if (!(lower.equals("self")
          || lower.equals("default")
          || lower.equals("nsfw")
          || lower.equals("spoiler")
          || lower.equals("image"))) {
        return thumb;
      }
    }
    return null;
  }

  private static String canonicalUrl(String permalink, String originalUrl) {
    try {
      if (permalink != null && !permalink.isBlank() && permalink.startsWith("/")) {
        return "https://www.reddit.com" + permalink;
      }
    } catch (Exception ignored) {
    }
    return originalUrl;
  }

  private static String normalizeUrl(String s) {
    if (s == null || s.isBlank()) return null;
    // Reddit preview URLs often come through as HTML-escaped (&amp;).
    try {
      return org.jsoup.parser.Parser.unescapeEntities(s, true);
    } catch (Exception ignored) {
      return s;
    }
  }

  private static boolean looksLikeHttpUrl(String s) {
    if (s == null || s.isBlank()) return false;
    String t = s.strip();
    return t.startsWith("http://") || t.startsWith("https://");
  }

  private static String buildDetailsLine(String subreddit, String author) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    if (subreddit != null && !subreddit.isBlank()) {
      sb.append(subreddit.strip());
      first = false;
    }
    if (author != null && !author.isBlank()) {
      if (!first) sb.append(" • ");
      sb.append("u/").append(author.strip());
      first = false;
    }
    return sb.toString();
  }

  private static String joinLines(String a, String b) {
    String aa = a != null ? a.strip() : "";
    String bb = b != null ? b.strip() : "";
    if (aa.isEmpty()) return bb.isEmpty() ? null : bb;
    if (bb.isEmpty()) return aa;
    return aa + "\n" + bb;
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a.strip();
    if (b != null && !b.isBlank()) return b.strip();
    return null;
  }
}

package cafe.woden.ircclient.ui.chat.embed;

import java.io.ByteArrayInputStream;
import java.net.URI;

/** Best-effort resolver for Slashdot story pages with a longer excerpt than OG/meta provides. */
final class SlashdotLinkPreviewResolver implements LinkPreviewResolver {

  private static final int MAX_HTML_BYTES = 1024 * 1024; // 1 MiB

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) throws Exception {
    if (!SlashdotPreviewUtil.isSlashdotStoryUri(uri)) return null;

    var resp =
        http.getStream(
            uri,
            "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
            PreviewHttp.headers(
                "User-Agent", PreviewHttp.BROWSER_USER_AGENT,
                "Accept-Language", PreviewHttp.ACCEPT_LANGUAGE));

    int status = resp.statusCode();
    if (status < 200 || status >= 300) {
      return null; // fall back to OG parser
    }

    String ct = resp.headers().firstValue("content-type").orElse("");
    if (!PreviewHttp.looksLikeHtml(ct)) {
      return null;
    }

    byte[] bytes = PreviewHttp.readUpToBytes(resp.body(), MAX_HTML_BYTES);
    if (bytes.length == 0) return null;

    var doc = org.jsoup.Jsoup.parse(new ByteArrayInputStream(bytes), null, originalUrl);

    LinkPreview base = LinkPreviewParser.parse(doc, originalUrl);

    String title = base.title();
    if (title != null) {
      title = title.replace(" - Slashdot", "").strip();
    }

    SlashdotPreviewUtil.StoryParts parts =
        SlashdotPreviewUtil.extractStoryParts(doc, title, base.description());
    String desc = parts.summary();
    if ((parts.submitter() != null && !parts.submitter().isBlank())
        || (parts.date() != null && !parts.date().isBlank())) {
      StringBuilder sb = new StringBuilder();
      if (parts.submitter() != null && !parts.submitter().isBlank()) {
        sb.append("Submitter: ").append(parts.submitter()).append("\n");
      }
      if (parts.date() != null && !parts.date().isBlank()) {
        sb.append("Date: ").append(parts.date()).append("\n");
      }
      sb.append("\n");
      if (desc != null) sb.append(desc);
      desc = sb.toString();
    }
    String site = "Slashdot";

    return new LinkPreview(base.url(), title, desc, site, base.imageUrl(), base.mediaCount());
  }
}

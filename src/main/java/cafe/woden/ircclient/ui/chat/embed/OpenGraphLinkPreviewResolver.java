package cafe.woden.ircclient.ui.chat.embed;

import java.io.ByteArrayInputStream;
import java.net.URI;

final class OpenGraphLinkPreviewResolver implements LinkPreviewResolver {

  private final int maxHtmlBytes;

  OpenGraphLinkPreviewResolver(int maxHtmlBytes) {
    this.maxHtmlBytes = maxHtmlBytes;
  }

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) throws Exception {
    var resp = http.getStream(
        uri,
        "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
        null);

    int status = resp.statusCode();
    if (status < 200 || status >= 300) {
      throw new IllegalStateException("HTTP " + status);
    }

    String ct = resp.headers().firstValue("content-type").orElse("");
    if (!PreviewHttp.looksLikeHtml(ct)) {
      throw new IllegalStateException("content-type not html: " + ct);
    }

    byte[] bytes = PreviewHttp.readUpTo(resp.body(), maxHtmlBytes);
    var doc = org.jsoup.Jsoup.parse(new ByteArrayInputStream(bytes), null, originalUrl);
    return LinkPreviewParser.parse(doc, originalUrl);
  }
}

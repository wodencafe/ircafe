package cafe.woden.ircclient.ui.chat.embed;

import java.io.ByteArrayInputStream;
import java.net.URI;

final class ImgurLinkPreviewResolver implements LinkPreviewResolver {

  private final int maxHtmlBytes;

  ImgurLinkPreviewResolver(int maxHtmlBytes) {
    this.maxHtmlBytes = maxHtmlBytes;
  }

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) throws Exception {
    if (!ImgurPreviewUtil.isImgurUri(uri)) {
      return null;
    }

    var resp = http.getStream(
        uri,
        "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
        PreviewHttp.headers(
            "User-Agent", PreviewHttp.BROWSER_USER_AGENT,
            "Accept-Language", PreviewHttp.ACCEPT_LANGUAGE,
            "Referer", "https://imgur.com/"
        )
    );

    int status = resp.statusCode();
    if (status < 200 || status >= 300) {
      return null;
    }

    String ct = resp.headers().firstValue("content-type").orElse("");
    if (!PreviewHttp.looksLikeHtml(ct)) {
      return null;
    }

    byte[] bytes = PreviewHttp.readUpToBytes(resp.body(), maxHtmlBytes);
    if (bytes.length == 0) return null;

    var doc = org.jsoup.Jsoup.parse(new ByteArrayInputStream(bytes), null, originalUrl);
    return ImgurPreviewUtil.parsePostDocument(doc, originalUrl);
  }
}

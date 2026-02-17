package cafe.woden.ircclient.ui.chat.embed;

import java.io.ByteArrayInputStream;
import java.net.URI;

final class XLinkPreviewResolver implements LinkPreviewResolver {

  // Keep in sync with LinkPreviewResolverConfig.DEFAULT_MAX_HTML_BYTES.
  private final int maxHtmlBytes;

  XLinkPreviewResolver(int maxHtmlBytes) {
    this.maxHtmlBytes = maxHtmlBytes;
  }

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) {
    try {
      if (uri == null) return null;
      String id = XPreviewUtil.extractStatusId(uri);
      if (id == null) return null;

      // 1) Public syndication JSON endpoint (fast when it works).
      URI api = XPreviewUtil.syndicationApiUri(id);
      if (api != null) {
        var resp = http.getString(api, "application/json",
            PreviewHttp.headers(
                "Referer", "https://platform.twitter.com/",
                "Origin", "https://platform.twitter.com"
            ));
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
          LinkPreview parsed = XPreviewUtil.parseSyndicationJson(resp.body(), uri);
          if (parsed != null) return parsed;
        }
      }

      // 2) X oEmbed endpoint (stable fallback; no JS scraping).
      LinkPreview oembed = tryOEmbed(uri, id, http);
      if (oembed != null) return oembed;

      // 3) HTML unfurl proxies (FixupX, FxTwitter, Nitter, ...)
      return tryProxy(uri, http);
    } catch (Exception ignored) {
      return null;
    }
  }

  private LinkPreview tryOEmbed(URI statusUri, String statusId, PreviewHttp http) {
    try {
      URI api = XPreviewUtil.oEmbedApiUri(statusId);
      if (api == null) return null;

      var resp = http.getString(api, "application/json",
          PreviewHttp.headers(
              "Referer", "https://publish.x.com/"
          ));
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) return null;

      return XPreviewUtil.parseOEmbedJson(resp.body(), statusUri, statusId);
    } catch (Exception ignored) {
      return null;
    }
  }

  private LinkPreview tryProxy(URI statusUri, PreviewHttp http) {
    try {
      for (URI proxy : XPreviewUtil.proxyUnfurlCandidates(statusUri)) {
        if (proxy == null) continue;

        var resp = http.getStream(
            proxy,
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            null);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) continue;

        byte[] bytes = PreviewHttp.readUpToBytes(resp.body(), maxHtmlBytes);
        var doc = org.jsoup.Jsoup.parse(new ByteArrayInputStream(bytes), null, proxy.toString());
        LinkPreview p = LinkPreviewParser.parse(doc, proxy.toString());
        if (p == null) continue;

        return new LinkPreview(
            statusUri.toString(),
            p.title(),
            p.description(),
            "X",
            p.imageUrl(),
            p.mediaCount()
        );
      }
      return null;
    } catch (Exception ignored) {
      return null;
    }
  }
}

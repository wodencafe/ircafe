package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;

/** Best-effort resolver for Wikipedia article URLs via the REST summary endpoint. */
final class WikipediaLinkPreviewResolver implements LinkPreviewResolver {

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) {
    try {
      if (!WikipediaPreviewUtil.isWikipediaArticleUri(uri)) return null;
      URI api = WikipediaPreviewUtil.toSummaryApiUri(uri);
      if (api == null) return null;

      var resp = http.getString(api, "application/json", null);
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        return null; // fall back to OG parser
      }
      return WikipediaPreviewUtil.parseSummaryJson(resp.body(), uri);
    } catch (Exception ignored) {
      return null;
    }
  }
}

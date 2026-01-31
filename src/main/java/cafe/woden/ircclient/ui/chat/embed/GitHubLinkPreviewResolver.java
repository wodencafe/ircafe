package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;

/** Best-effort resolver for GitHub links using the GitHub REST API. */
final class GitHubLinkPreviewResolver implements LinkPreviewResolver {

  @Override
  public LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) {
    try {
      GitHubPreviewUtil.GitHubLink link = GitHubPreviewUtil.parse(uri);
      if (link == null) return null;

      URI api = GitHubPreviewUtil.apiUri(link);
      if (api == null) return null;

      var resp = http.getString(api, "application/vnd.github+json",
          PreviewHttp.headers(
              "X-GitHub-Api-Version", "2022-11-28"
          ));

      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        return null;
      }

      return GitHubPreviewUtil.parseApiJson(resp.body(), link, uri);
    } catch (Exception ignored) {
      return null;
    }
  }
}

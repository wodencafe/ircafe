package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class GitHubPreviewUtilTest {

  @Test
  void parseRecognizesRepoIssueCommitAndReleasePaths() {
    GitHubPreviewUtil.GitHubLink repo =
        GitHubPreviewUtil.parse(URI.create("https://github.com/octocat/Hello-World"));
    GitHubPreviewUtil.GitHubLink issue =
        GitHubPreviewUtil.parse(URI.create("https://github.com/octocat/Hello-World/issues/42"));
    GitHubPreviewUtil.GitHubLink commit =
        GitHubPreviewUtil.parse(
            URI.create("https://github.com/octocat/Hello-World/commit/abcdef1234567890"));
    GitHubPreviewUtil.GitHubLink release =
        GitHubPreviewUtil.parse(
            URI.create("https://github.com/octocat/Hello-World/releases/tag/v1.2.3"));

    assertEquals(GitHubPreviewUtil.Kind.REPO, repo.kind());
    assertEquals(GitHubPreviewUtil.Kind.ISSUE_OR_PR, issue.kind());
    assertEquals("42", issue.numberOrShaOrTag());
    assertEquals(GitHubPreviewUtil.Kind.COMMIT, commit.kind());
    assertEquals(GitHubPreviewUtil.Kind.RELEASE, release.kind());
    assertEquals("v1.2.3", release.numberOrShaOrTag());
  }

  @Test
  void apiUriMapsParsedLinksToExpectedGithubApiEndpoints() {
    GitHubPreviewUtil.GitHubLink issue =
        new GitHubPreviewUtil.GitHubLink(
            GitHubPreviewUtil.Kind.ISSUE_OR_PR, "octocat", "Hello-World", "42");
    GitHubPreviewUtil.GitHubLink commit =
        new GitHubPreviewUtil.GitHubLink(
            GitHubPreviewUtil.Kind.COMMIT, "octocat", "Hello-World", "abcdef");

    assertEquals(
        "https://api.github.com/repos/octocat/Hello-World/issues/42",
        GitHubPreviewUtil.apiUri(issue).toString());
    assertEquals(
        "https://api.github.com/repos/octocat/Hello-World/commits/abcdef",
        GitHubPreviewUtil.apiUri(commit).toString());
  }

  @Test
  void parseApiJsonBuildsRepoAndPullRequestPreviews() {
    GitHubPreviewUtil.GitHubLink repoLink =
        new GitHubPreviewUtil.GitHubLink(
            GitHubPreviewUtil.Kind.REPO, "octocat", "Hello-World", null);
    String repoJson =
        """
        {
          "full_name":"octocat/Hello-World",
          "description":"Sample repository description",
          "stargazers_count":1200,
          "forks_count":30,
          "language":"Java",
          "updated_at":"2026-02-25T10:00:00Z",
          "html_url":"https://github.com/octocat/Hello-World",
          "owner":{"avatar_url":"https://cdn.example/avatar.png"}
        }
        """;

    LinkPreview repo =
        GitHubPreviewUtil.parseApiJson(
            repoJson, repoLink, URI.create("https://github.com/octocat/Hello-World"));

    assertNotNull(repo);
    assertEquals("octocat/Hello-World", repo.title());
    assertTrue(repo.description().contains("⭐ 1.2K"));
    assertTrue(repo.description().contains("Forks 30"));
    assertEquals("GitHub", repo.siteName());

    GitHubPreviewUtil.GitHubLink prLink =
        new GitHubPreviewUtil.GitHubLink(
            GitHubPreviewUtil.Kind.ISSUE_OR_PR, "octocat", "Hello-World", "7");
    String prJson =
        """
        {
          "title":"Improve parser stability",
          "state":"open",
          "comments":5,
          "updated_at":"2026-02-25T10:00:00Z",
          "body":"Adds edge-case handling and tests.",
          "html_url":"https://github.com/octocat/Hello-World/pull/7",
          "user":{"login":"alice","avatar_url":"https://cdn.example/alice.png"},
          "pull_request":{}
        }
        """;

    LinkPreview pr =
        GitHubPreviewUtil.parseApiJson(
            prJson, prLink, URI.create("https://github.com/octocat/Hello-World/pull/7"));

    assertNotNull(pr);
    assertEquals("Improve parser stability", pr.title());
    assertTrue(pr.description().contains("PR #7"));
    assertTrue(pr.description().contains("5 comments"));
  }
}

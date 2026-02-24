package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class GitHubPreviewUtil {

  private GitHubPreviewUtil() {}

  enum Kind {
    REPO,
    ISSUE_OR_PR,
    COMMIT,
    RELEASE
  }

  record GitHubLink(Kind kind, String owner, String repo, String numberOrShaOrTag) {}

  static boolean isGitHubUrl(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      return parse(URI.create(url)) != null;
    } catch (Exception ignored) {
      return false;
    }
  }

  static boolean isGitHubUri(URI uri) {
    return parse(uri) != null;
  }

  static GitHubLink parse(URI uri) {
    if (uri == null) return null;
    String host = hostLower(uri);
    if (host == null) return null;
    if (!(host.equals("github.com") || host.endsWith(".github.com"))) return null;

    String path = uri.getPath() == null ? "" : uri.getPath();
    String[] seg = path.split("/");
    // path begins with '/', so seg[0] is "".
    if (seg.length < 3) return null;
    String owner = safe(seg[1]);
    String repo = safe(seg[2]);
    if (owner == null || repo == null) return null;

    // If it's exactly /owner/repo or /owner/repo/... treat as repo unless it matches a known type.
    if (seg.length >= 5) {
      String t = safe(seg[3]);
      String v = safe(seg[4]);
      if (t != null && v != null) {
        if (t.equalsIgnoreCase("issues") || t.equalsIgnoreCase("pull")) {
          return new GitHubLink(Kind.ISSUE_OR_PR, owner, repo, v);
        }
        if (t.equalsIgnoreCase("commit")) {
          return new GitHubLink(Kind.COMMIT, owner, repo, v);
        }
        if (t.equalsIgnoreCase("releases") && seg.length >= 6) {
          String t2 = safe(seg[4]);
          String tag = safe(seg[5]);
          if (t2 != null && t2.equalsIgnoreCase("tag") && tag != null) {
            return new GitHubLink(Kind.RELEASE, owner, repo, tag);
          }
        }
      }
    }

    return new GitHubLink(Kind.REPO, owner, repo, null);
  }

  static URI apiUri(GitHubLink link) {
    if (link == null) return null;
    return switch (link.kind) {
      case REPO -> URI.create("https://api.github.com/repos/" + link.owner + "/" + link.repo);
      case ISSUE_OR_PR ->
          URI.create(
              "https://api.github.com/repos/"
                  + link.owner
                  + "/"
                  + link.repo
                  + "/issues/"
                  + link.numberOrShaOrTag);
      case COMMIT ->
          URI.create(
              "https://api.github.com/repos/"
                  + link.owner
                  + "/"
                  + link.repo
                  + "/commits/"
                  + link.numberOrShaOrTag);
      case RELEASE ->
          URI.create(
              "https://api.github.com/repos/"
                  + link.owner
                  + "/"
                  + link.repo
                  + "/releases/tags/"
                  + link.numberOrShaOrTag);
    };
  }

  static LinkPreview parseApiJson(String json, GitHubLink link, URI originalUri) {
    if (json == null || json.isBlank() || link == null) return null;

    return switch (link.kind) {
      case REPO -> parseRepo(json, link, originalUri);
      case ISSUE_OR_PR -> parseIssueOrPr(json, link, originalUri);
      case COMMIT -> parseCommit(json, link, originalUri);
      case RELEASE -> parseRelease(json, link, originalUri);
    };
  }

  private static LinkPreview parseRepo(String json, GitHubLink link, URI originalUri) {
    String fullName =
        firstNonBlank(MiniJson.findString(json, "full_name"), link.owner + "/" + link.repo);
    String desc = MiniJson.findString(json, "description");
    Long stars = MiniJson.findLong(json, "stargazers_count");
    Long forks = MiniJson.findLong(json, "forks_count");
    String lang = MiniJson.findString(json, "language");
    String updated = MiniJson.findString(json, "updated_at");

    String ownerObj = MiniJson.findObject(json, "owner");
    String avatar = ownerObj != null ? MiniJson.findString(ownerObj, "avatar_url") : null;

    String details = buildRepoDetails(stars, forks, lang, updated);
    String snippet = desc != null ? PreviewTextUtil.trimToSentence(desc, 800) : null;
    String body = joinLines(details, snippet);

    String htmlUrl = MiniJson.findString(json, "html_url");
    if (htmlUrl == null) htmlUrl = originalUri != null ? originalUri.toString() : null;
    return new LinkPreview(htmlUrl, fullName, body, "GitHub", avatar, avatar != null ? 1 : 0);
  }

  private static LinkPreview parseIssueOrPr(String json, GitHubLink link, URI originalUri) {
    String title = MiniJson.findString(json, "title");
    String state = MiniJson.findString(json, "state");
    Long comments = MiniJson.findLong(json, "comments");
    String updated = MiniJson.findString(json, "updated_at");
    String bodyText = MiniJson.findString(json, "body");

    String userObj = MiniJson.findObject(json, "user");
    String user = userObj != null ? MiniJson.findString(userObj, "login") : null;
    String avatar = userObj != null ? MiniJson.findString(userObj, "avatar_url") : null;

    boolean isPr = MiniJson.findObject(json, "pull_request") != null;
    String kind = isPr ? "PR" : "Issue";

    String details = buildIssueDetails(kind, link.numberOrShaOrTag, state, comments, user, updated);
    String snippet = bodyText != null ? PreviewTextUtil.trimToSentence(bodyText, 900) : null;
    String desc = joinLines(details, snippet);

    String htmlUrl = MiniJson.findString(json, "html_url");
    if (htmlUrl == null) htmlUrl = originalUri != null ? originalUri.toString() : null;

    String shownTitle = title != null ? title : (kind + " #" + link.numberOrShaOrTag);
    String site = "GitHub";
    return new LinkPreview(htmlUrl, shownTitle, desc, site, avatar, avatar != null ? 1 : 0);
  }

  private static LinkPreview parseCommit(String json, GitHubLink link, URI originalUri) {
    String sha = MiniJson.findString(json, "sha");

    String commitObj = MiniJson.findObject(json, "commit");
    String message = commitObj != null ? MiniJson.findString(commitObj, "message") : null;
    String firstLine = message != null ? firstLine(message) : null;

    String authorName = null;
    String date = null;
    if (commitObj != null) {
      String authorObj = MiniJson.findObject(commitObj, "author");
      if (authorObj != null) {
        authorName = MiniJson.findString(authorObj, "name");
        date = MiniJson.findString(authorObj, "date");
      }
    }

    String authorObjTop = MiniJson.findObject(json, "author");
    String avatar = authorObjTop != null ? MiniJson.findString(authorObjTop, "avatar_url") : null;
    String login = authorObjTop != null ? MiniJson.findString(authorObjTop, "login") : null;

    String details =
        buildCommitDetails(
            link.owner + "/" + link.repo,
            sha != null ? sha : link.numberOrShaOrTag,
            login != null ? login : authorName,
            date);
    String desc = details;

    String htmlUrl = MiniJson.findString(json, "html_url");
    if (htmlUrl == null) htmlUrl = originalUri != null ? originalUri.toString() : null;

    String shownTitle =
        firstNonBlank(firstLine, "Commit " + shortSha(sha != null ? sha : link.numberOrShaOrTag));
    return new LinkPreview(htmlUrl, shownTitle, desc, "GitHub", avatar, avatar != null ? 1 : 0);
  }

  private static LinkPreview parseRelease(String json, GitHubLink link, URI originalUri) {
    String name =
        firstNonBlank(
            MiniJson.findString(json, "name"),
            MiniJson.findString(json, "tag_name"),
            link.numberOrShaOrTag);
    String bodyText = MiniJson.findString(json, "body");
    String published = MiniJson.findString(json, "published_at");

    String authorObj = MiniJson.findObject(json, "author");
    String author = authorObj != null ? MiniJson.findString(authorObj, "login") : null;
    String avatar = authorObj != null ? MiniJson.findString(authorObj, "avatar_url") : null;

    String details =
        buildReleaseDetails(link.owner + "/" + link.repo, link.numberOrShaOrTag, author, published);
    String snippet = bodyText != null ? PreviewTextUtil.trimToSentence(bodyText, 900) : null;
    String desc = joinLines(details, snippet);

    String htmlUrl = MiniJson.findString(json, "html_url");
    if (htmlUrl == null) htmlUrl = originalUri != null ? originalUri.toString() : null;
    return new LinkPreview(htmlUrl, name, desc, "GitHub", avatar, avatar != null ? 1 : 0);
  }

  private static String buildRepoDetails(Long stars, Long forks, String lang, String updatedIso) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    if (stars != null) {
      sb.append("⭐ ").append(formatCount(stars));
      first = false;
    }
    if (forks != null) {
      if (!first) sb.append(" • ");
      sb.append("Forks ").append(formatCount(forks));
      first = false;
    }
    if (lang != null && !lang.isBlank()) {
      if (!first) sb.append(" • ");
      sb.append(lang.strip());
      first = false;
    }
    String d = formatIsoDate(updatedIso);
    if (d != null) {
      if (!first) sb.append(" • ");
      sb.append("Updated ").append(d);
      first = false;
    }
    return sb.toString();
  }

  private static String buildIssueDetails(
      String kind, String num, String state, Long comments, String user, String updatedIso) {
    StringBuilder sb = new StringBuilder();
    sb.append(kind);
    if (num != null) sb.append(" #").append(num);
    boolean had = true;
    if (state != null && !state.isBlank()) {
      sb.append(" • ").append(state.strip());
    }
    if (comments != null) {
      sb.append(" • ").append(formatCount(comments)).append(" comments");
    }
    if (user != null && !user.isBlank()) {
      sb.append(" • by ").append(user.strip());
    }
    String d = formatIsoDate(updatedIso);
    if (d != null) {
      sb.append(" • updated ").append(d);
    }
    return sb.toString();
  }

  private static String buildCommitDetails(String repo, String sha, String who, String dateIso) {
    StringBuilder sb = new StringBuilder();
    sb.append(repo).append("@").append(shortSha(sha));
    if (who != null && !who.isBlank()) {
      sb.append(" • ").append(who.strip());
    }
    String d = formatIsoDate(dateIso);
    if (d != null) sb.append(" • ").append(d);
    return sb.toString();
  }

  private static String buildReleaseDetails(String repo, String tag, String who, String dateIso) {
    StringBuilder sb = new StringBuilder();
    sb.append(repo);
    if (tag != null && !tag.isBlank()) sb.append(" • ").append(tag.strip());
    if (who != null && !who.isBlank()) sb.append(" • ").append(who.strip());
    String d = formatIsoDate(dateIso);
    if (d != null) sb.append(" • ").append(d);
    return sb.toString();
  }

  private static String formatIsoDate(String iso) {
    try {
      if (iso == null || iso.isBlank()) return null;
      Instant inst = Instant.parse(iso.strip());
      return DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ROOT)
          .withZone(ZoneId.systemDefault())
          .format(inst);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String shortSha(String sha) {
    if (sha == null) return "";
    String t = sha.strip();
    if (t.length() <= 7) return t;
    return t.substring(0, 7);
  }

  private static String firstLine(String s) {
    if (s == null) return null;
    String t = s.replace("\r", "");
    int i = t.indexOf('\n');
    return i >= 0 ? t.substring(0, i).trim() : t.trim();
  }

  private static String formatCount(long n) {
    if (n < 0) return "0";
    if (n >= 1_000_000_000L)
      return String.format(Locale.ROOT, "%.1fB", n / 1_000_000_000d).replace(".0", "");
    if (n >= 1_000_000L)
      return String.format(Locale.ROOT, "%.1fM", n / 1_000_000d).replace(".0", "");
    if (n >= 1_000L) return String.format(Locale.ROOT, "%.1fK", n / 1_000d).replace(".0", "");
    return NumberFormat.getIntegerInstance(Locale.ROOT).format(n);
  }

  private static String joinLines(String a, String b) {
    String aa = safe(a);
    String bb = safe(b);
    if (aa == null) return bb;
    if (bb == null) return aa;
    return aa + "\n" + bb;
  }

  private static String firstNonBlank(String... s) {
    if (s == null) return null;
    for (String v : s) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  private static String safe(String s) {
    if (s == null) return null;
    String t = s.strip();
    return t.isEmpty() ? null : t;
  }

  private static String hostLower(URI uri) {
    if (uri == null) return null;
    String h = uri.getHost();
    if (h == null) return null;
    String t = h.trim();
    return t.isEmpty() ? null : t.toLowerCase(Locale.ROOT);
  }

  static final class MiniJson {
    private MiniJson() {}

    static String findString(String json, String key) {
      if (json == null || key == null) return null;
      int i = indexOfKey(json, key, 0);
      while (i >= 0) {
        int p = skipToValue(json, i + key.length() + 2);
        if (p < 0) return null;
        p = skipWs(json, p);
        if (p < json.length() && json.charAt(p) == '"') {
          return parseString(json, p);
        }
        i = indexOfKey(json, key, i + key.length() + 2);
      }
      return null;
    }

    static Long findLong(String json, String key) {
      if (json == null || key == null) return null;
      int i = indexOfKey(json, key, 0);
      while (i >= 0) {
        int p = skipToValue(json, i + key.length() + 2);
        if (p < 0) return null;
        p = skipWs(json, p);
        int end = p;
        if (end < json.length()
            && (json.charAt(end) == '-' || Character.isDigit(json.charAt(end)))) {
          end++;
          while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
          try {
            return Long.parseLong(json.substring(p, end));
          } catch (Exception ignored) {
            return null;
          }
        }
        i = indexOfKey(json, key, i + key.length() + 2);
      }
      return null;
    }

    static String findObject(String json, String key) {
      if (json == null || key == null) return null;
      int i = indexOfKey(json, key, 0);
      while (i >= 0) {
        int p = skipToValue(json, i + key.length() + 2);
        if (p < 0) return null;
        p = skipWs(json, p);
        if (p < json.length() && json.charAt(p) == '{') {
          return captureBalanced(json, p, '{', '}');
        }
        i = indexOfKey(json, key, i + key.length() + 2);
      }
      return null;
    }

    private static int indexOfKey(String json, String key, int from) {
      String needle = "\"" + key + "\"";
      return json.indexOf(needle, Math.max(0, from));
    }

    private static int skipToValue(String json, int from) {
      int p = Math.max(0, from);
      while (p < json.length()) {
        if (json.charAt(p) == ':') return p + 1;
        p++;
      }
      return -1;
    }

    private static int skipWs(String s, int i) {
      int p = Math.max(0, i);
      while (p < s.length()) {
        char c = s.charAt(p);
        if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
        p++;
      }
      return p;
    }

    private static String parseString(String json, int quotePos) {
      StringBuilder out = new StringBuilder();
      int i = quotePos + 1;
      while (i < json.length()) {
        char c = json.charAt(i);
        if (c == '"') return out.toString();
        if (c == '\\') {
          if (i + 1 >= json.length()) break;
          char e = json.charAt(i + 1);
          switch (e) {
            case '"' -> out.append('"');
            case '\\' -> out.append('\\');
            case '/' -> out.append('/');
            case 'b' -> out.append('\b');
            case 'f' -> out.append('\f');
            case 'n' -> out.append('\n');
            case 'r' -> out.append('\r');
            case 't' -> out.append('\t');
            case 'u' -> {
              if (i + 6 <= json.length()) {
                String hex = json.substring(i + 2, i + 6);
                try {
                  out.append((char) Integer.parseInt(hex, 16));
                } catch (Exception ignored) {
                }
                i += 4;
              }
            }
            default -> out.append(e);
          }
          i += 2;
          continue;
        }
        out.append(c);
        i++;
      }
      return out.toString();
    }

    private static String captureBalanced(String s, int start, char open, char close) {
      int depth = 0;
      boolean inStr = false;
      boolean esc = false;
      for (int i = start; i < s.length(); i++) {
        char c = s.charAt(i);
        if (inStr) {
          if (esc) {
            esc = false;
          } else if (c == '\\') {
            esc = true;
          } else if (c == '"') {
            inStr = false;
          }
          continue;
        }
        if (c == '"') {
          inStr = true;
          continue;
        }
        if (c == open) depth++;
        if (c == close) {
          depth--;
          if (depth == 0) return s.substring(start, i + 1);
        }
      }
      return null;
    }
  }
}

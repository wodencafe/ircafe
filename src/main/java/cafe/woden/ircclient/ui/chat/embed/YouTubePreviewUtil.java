package cafe.woden.ircclient.ui.chat.embed;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class YouTubePreviewUtil {

  private YouTubePreviewUtil() {}

  static boolean isYouTubeVideoUrl(String url) {
    return extractVideoId(url) != null;
  }

  static boolean isYouTubeVideoUri(URI uri) {
    return extractVideoId(uri) != null;
  }

  static String extractVideoId(String url) {
    if (url == null || url.isBlank()) return null;
    try {
      return extractVideoId(URI.create(url));
    } catch (Exception ignored) {
      return null;
    }
  }

  static String extractVideoId(URI uri) {
    if (uri == null) return null;

    String host = hostLower(uri);
    if (host == null) return null;

    // youtu.be/<id>
    if (host.equals("youtu.be") || host.endsWith(".youtu.be")) {
      String seg = firstPathSegment(uri);
      return cleanId(seg);
    }

    // youtube.* domains
    if (!(host.contains("youtube.")
        || host.endsWith("youtube.com")
        || host.endsWith("youtube-nocookie.com"))) {
      return null;
    }

    String path = uri.getPath() == null ? "" : uri.getPath();

    // /watch?v=<id>
    if (path.equalsIgnoreCase("/watch")) {
      String v = queryParam(uri.getRawQuery(), "v");
      return cleanId(v);
    }

    // /shorts/<id>, /embed/<id>, /v/<id>
    String id = pathSegmentAfter(path, "/shorts/");
    if (id == null) id = pathSegmentAfter(path, "/embed/");
    if (id == null) id = pathSegmentAfter(path, "/v/");
    return cleanId(id);
  }

  static URI canonicalWatchUri(String videoId) {
    if (videoId == null || videoId.isBlank()) return null;
    return URI.create("https://www.youtube.com/watch?v=" + videoId);
  }

  static URI oEmbedUri(URI videoUri) {
    String id = extractVideoId(videoUri);
    if (id == null) return null;

    URI canonical = canonicalWatchUri(id);
    String encoded = URLEncoder.encode(canonical.toString(), StandardCharsets.UTF_8);
    return URI.create("https://www.youtube.com/oembed?format=json&url=" + encoded);
  }

  static LinkPreview parseOEmbedJson(
      String json, URI originalVideoUri, String ogDescriptionFallback, YtMeta meta) {
    if (json == null || json.isBlank() || originalVideoUri == null) return null;

    String id = extractVideoId(originalVideoUri);

    String title = MiniJson.findString(json, "title");
    String author = MiniJson.findString(json, "author_name");
    String thumb = MiniJson.findString(json, "thumbnail_url");
    String provider = MiniJson.findString(json, "provider_name");

    if (provider == null || provider.isBlank()) provider = "YouTube";

    String detailsLine = buildDetailsLine(author, meta);

    String snippet = null;
    if (meta != null && meta.description() != null && !meta.description().isBlank()) {
      snippet = PreviewTextUtil.trimToSentence(firstParagraph(meta.description()), 900);
    } else if (ogDescriptionFallback != null && !ogDescriptionFallback.isBlank()) {
      snippet = PreviewTextUtil.trimToSentence(ogDescriptionFallback, 900);
    }

    String desc = detailsLine;
    if (snippet != null && !snippet.isBlank()) {
      desc = (desc != null && !desc.isBlank()) ? (desc + "\n" + snippet) : snippet;
    }

    // Prefer a stable, compact 16:9 thumbnail when possible.
    if ((thumb == null || thumb.isBlank()) && id != null) {
      thumb = "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
    }

    String url = originalVideoUri.toString();
    return new LinkPreview(url, title, desc, provider, thumb, thumb != null ? 1 : 0);
  }

  /** Attempt to enrich via yt-dlp if it is installed on PATH. */
  static YtMeta tryFetchYtDlpMeta(String url, Duration timeout) {
    if (url == null || url.isBlank()) return null;

    // Try common executable names.
    String[] cmds = new String[] {"yt-dlp", "yt-dlp.exe", "youtube-dl", "youtube-dl.exe"};

    for (String exe : cmds) {
      try {
        String json = runProcessJson(exe, url, timeout != null ? timeout : Duration.ofSeconds(6));
        if (json == null || json.isBlank()) continue;

        Integer duration = MiniJson.findInt(json, "duration");
        Long views = MiniJson.findLong(json, "view_count");
        Long likes = MiniJson.findLong(json, "like_count");
        String description = MiniJson.findString(json, "description");

        if (duration == null
            && views == null
            && likes == null
            && (description == null || description.isBlank())) {
          continue;
        }
        return new YtMeta(duration, views, likes, description);
      } catch (Exception ignored) {
        // best-effort, try next executable name
      }
    }

    return null;
  }

  private static String runProcessJson(String exe, String url, Duration timeout) throws Exception {
    // yt-dlp -J/--dump-single-json prints JSON for the video without downloading.
    ProcessBuilder pb =
        new ProcessBuilder(exe, "-J", "--no-playlist", "--no-warnings", "--skip-download", url);
    pb.redirectErrorStream(true);

    Process p = pb.start();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    // Cap output size so we can't be DOSed by huge text.
    final int maxBytes = 2 * 1024 * 1024; // 2 MiB

    try (InputStream in = p.getInputStream()) {
      byte[] buf = new byte[8192];
      int total = 0;
      while (true) {
        int n = in.read(buf);
        if (n < 0) break;
        int toWrite = Math.min(n, maxBytes - total);
        if (toWrite > 0) {
          out.write(buf, 0, toWrite);
          total += toWrite;
        }
        if (total >= maxBytes) break;
      }
    }

    boolean finished = p.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
    if (!finished) {
      p.destroyForcibly();
      return null;
    }

    if (p.exitValue() != 0) {
      return null;
    }

    // yt-dlp emits UTF-8.
    return out.toString(StandardCharsets.UTF_8);
  }

  private static String buildDetailsLine(String author, YtMeta meta) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;

    if (author != null && !author.isBlank()) {
      sb.append("Channel: ").append(author.strip());
      first = false;
    }

    if (meta != null) {
      String dur = formatDuration(meta.durationSeconds());
      if (dur != null) {
        if (!first) sb.append(" • ");
        sb.append(dur);
        first = false;
      }

      if (meta.viewCount() != null && meta.viewCount() >= 0) {
        if (!first) sb.append(" • ");
        sb.append(formatCount(meta.viewCount())).append(" views");
        first = false;
      }

      if (meta.likeCount() != null && meta.likeCount() >= 0) {
        if (!first) sb.append(" • ");
        sb.append(formatCount(meta.likeCount())).append(" likes");
        first = false;
      }
    }

    String s = sb.toString().strip();
    return s.isBlank() ? null : s;
  }

  private static String formatDuration(Integer seconds) {
    if (seconds == null || seconds < 0) return null;
    int s = seconds;
    int h = s / 3600;
    int m = (s % 3600) / 60;
    int sec = s % 60;
    if (h > 0) {
      return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec);
    }
    return String.format(Locale.ROOT, "%d:%02d", m, sec);
  }

  private static String formatCount(long n) {
    try {
      NumberFormat nf = NumberFormat.getIntegerInstance(Locale.getDefault());
      nf.setGroupingUsed(true);
      return nf.format(n);
    } catch (Exception ignored) {
      return Long.toString(n);
    }
  }

  private static String firstParagraph(String description) {
    if (description == null) return null;
    String d = description.replace("\r\n", "\n").replace("\r", "\n").strip();
    if (d.isEmpty()) return d;

    // Prefer text before the first blank line.
    int blank = d.indexOf("\n\n");
    if (blank > 0) {
      return d.substring(0, blank).strip();
    }

    // Otherwise, keep the first few non-empty lines.
    String[] lines = d.split("\n");
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      if (line == null) continue;
      String t = line.strip();
      if (t.isEmpty()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(t);
      if (sb.length() >= 480) break;
    }
    return sb.toString().strip();
  }

  record YtMeta(Integer durationSeconds, Long viewCount, Long likeCount, String description) {}

  private static String hostLower(URI uri) {
    try {
      String h = uri.getHost();
      if (h == null) return null;
      return h.toLowerCase(Locale.ROOT);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String firstPathSegment(URI uri) {
    String path = uri.getPath();
    if (path == null) return null;
    String p = path.startsWith("/") ? path.substring(1) : path;
    if (p.isEmpty()) return null;
    int slash = p.indexOf('/');
    return slash >= 0 ? p.substring(0, slash) : p;
  }

  private static String pathSegmentAfter(String path, String prefix) {
    if (path == null) return null;
    String p = path;
    if (!p.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) return null;
    String rest = p.substring(prefix.length());
    if (rest.isEmpty()) return null;
    int slash = rest.indexOf('/');
    return slash >= 0 ? rest.substring(0, slash) : rest;
  }

  private static String queryParam(String rawQuery, String key) {
    if (rawQuery == null || rawQuery.isBlank() || key == null) return null;
    String[] parts = rawQuery.split("&");
    for (String part : parts) {
      if (part == null || part.isEmpty()) continue;
      int eq = part.indexOf('=');
      String k = eq >= 0 ? part.substring(0, eq) : part;
      if (!k.equals(key)) continue;
      String v = eq >= 0 ? part.substring(eq + 1) : "";
      return decode(v);
    }
    return null;
  }

  private static String decode(String s) {
    if (s == null) return null;
    try {
      return URLDecoder.decode(s, StandardCharsets.UTF_8);
    } catch (Exception ignored) {
      return s;
    }
  }

  private static String cleanId(String id) {
    if (id == null) return null;
    String t = id.strip();
    if (t.isEmpty()) return null;

    // Strip query fragments or extras if someone passed a full segment.
    int q = t.indexOf('?');
    if (q >= 0) t = t.substring(0, q);
    int amp = t.indexOf('&');
    if (amp >= 0) t = t.substring(0, amp);
    int hash = t.indexOf('#');
    if (hash >= 0) t = t.substring(0, hash);

    t = t.strip();
    if (t.isEmpty()) return null;

    // YouTube IDs are typically 11 chars, but be permissive.
    for (int i = 0; i < t.length(); i++) {
      char c = t.charAt(i);
      boolean ok =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '_'
              || c == '-';
      if (!ok) return null;
    }
    if (t.length() < 6) return null;
    return t;
  }

  static final class MiniJson {
    private MiniJson() {}

    static String findString(String json, String key) {
      if (json == null || key == null) return null;
      int i = json.indexOf("\"" + key + "\"");
      while (i >= 0) {
        int colon = json.indexOf(':', i);
        if (colon < 0) return null;
        int p = colon + 1;
        while (p < json.length()) {
          char c = json.charAt(p);
          if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
          p++;
        }
        if (p < json.length() && json.charAt(p) == '"') {
          return parseString(json, p);
        }
        i = json.indexOf("\"" + key + "\"", i + key.length() + 2);
      }
      return null;
    }

    static Long findLong(String json, String key) {
      String raw = findRawNumber(json, key);
      if (raw == null) return null;
      try {
        return Long.parseLong(raw);
      } catch (Exception ignored) {
        return null;
      }
    }

    static Integer findInt(String json, String key) {
      String raw = findRawNumber(json, key);
      if (raw == null) return null;
      try {
        return Integer.parseInt(raw);
      } catch (Exception ignored) {
        return null;
      }
    }

    private static String findRawNumber(String json, String key) {
      if (json == null || key == null) return null;
      int i = json.indexOf("\"" + key + "\"");
      while (i >= 0) {
        int colon = json.indexOf(':', i);
        if (colon < 0) return null;
        int p = colon + 1;
        while (p < json.length()) {
          char c = json.charAt(p);
          if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
          p++;
        }
        if (p >= json.length()) return null;
        char c = json.charAt(p);
        if (c == 'n') {
          // null
          return null;
        }
        int start = p;
        boolean neg = false;
        if (c == '-') {
          neg = true;
          p++;
        }
        while (p < json.length()) {
          char d = json.charAt(p);
          if (d < '0' || d > '9') break;
          p++;
        }
        if (p > start + (neg ? 1 : 0)) {
          return json.substring(start, p);
        }
        i = json.indexOf("\"" + key + "\"", i + key.length() + 2);
      }
      return null;
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
  }
}

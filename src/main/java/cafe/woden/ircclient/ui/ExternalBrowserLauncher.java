package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.util.VirtualThreads;
import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@InterfaceLayer
public class ExternalBrowserLauncher {

  private static final Logger log = LoggerFactory.getLogger(ExternalBrowserLauncher.class);
  private static final long PLATFORM_OPEN_EXIT_CHECK_TIMEOUT_MS = 450L;
  private static final Pattern URL_TOKEN_PATTERN =
      Pattern.compile("(?i)(https?://[^\\s<>\"\\u0000-\\u001F]+|www\\.[^\\s<>\"\\u0000-\\u001F]+)");
  private static final List<String> KNOWN_LINUX_BROWSERS =
      List.of(
          "librewolf",
          "zen-browser",
          "firefox",
          "google-chrome",
          "chromium",
          "chromium-browser",
          "brave-browser",
          "microsoft-edge",
          "opera",
          "vivaldi");

  public void openAsync(String rawUrl) {
    String url = sanitizeUrl(rawUrl);
    if (url == null) return;

    VirtualThreads.start(
        "ircafe-open-url",
        () -> {
          if (!openNormalized(url)) {
            log.warn("Could not open URL in browser: {}", url);
          }
        });
  }

  public boolean open(String rawUrl) {
    String url = sanitizeUrl(rawUrl);
    return url != null && openNormalized(url);
  }

  private boolean openNormalized(String url) {
    String os = currentOsLowerCase();
    if (os.contains("linux")) {
      return tryLinuxOpen(url);
    }
    if (tryDesktopBrowse(url)) return true;
    return tryPlatformOpen(os, url);
  }

  private boolean tryLinuxOpen(String url) {
    return tryStart("xdg-open", url)
        || tryStart("gio", "open", url)
        || tryStart("sensible-browser", url)
        || tryStart("x-www-browser", url)
        || tryDesktopBrowse(url)
        || tryKnownLinuxBrowser(url)
        || tryStart("gnome-open", url)
        || tryStart("kde-open", url);
  }

  private boolean tryPlatformOpen(String os, String url) {
    if (os.contains("mac") || os.contains("darwin")) {
      return tryStart("open", url);
    }
    if (os.contains("win")) {
      return tryStart("rundll32", "url.dll,FileProtocolHandler", url)
          || tryStart("cmd", "/c", "start", "", url);
    }
    return false;
  }

  protected boolean tryDesktopBrowse(String url) {
    try {
      if (!Desktop.isDesktopSupported()) return false;
      Desktop desktop = Desktop.getDesktop();
      if (desktop == null) return false;
      if (!desktop.isSupported(Desktop.Action.BROWSE)) return false;
      desktop.browse(URI.create(url));
      return true;
    } catch (Exception e) {
      log.debug("Desktop browse failed for {}", url, e);
      return false;
    }
  }

  protected boolean tryKnownLinuxBrowser(String url) {
    for (String browser : KNOWN_LINUX_BROWSERS) {
      if (tryStart(browser, url)) return true;
    }
    return false;
  }

  protected boolean tryStart(String... cmd) {
    if (cmd == null || cmd.length == 0) return false;
    try {
      Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      if (process == null) return false;
      try {
        if (process.waitFor(PLATFORM_OPEN_EXIT_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
          return process.exitValue() == 0;
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  protected String currentOsLowerCase() {
    return Objects.toString(System.getProperty("os.name", ""), "").toLowerCase(Locale.ROOT);
  }

  static String sanitizeUrl(String rawUrl) {
    String s = Objects.toString(rawUrl, "").trim();
    if (s.isEmpty()) return null;

    Matcher matcher = URL_TOKEN_PATTERN.matcher(s);
    if (matcher.find()) {
      s = Objects.toString(matcher.group(1), "").trim();
    }

    s = trimEdgeNoise(s);
    if (s.isEmpty()) return null;

    if (s.startsWith("<") && s.endsWith(">") && s.length() > 2) {
      s = s.substring(1, s.length() - 1).trim();
    }

    int ws = firstWhitespaceIndex(s);
    if (ws > 0) {
      s = s.substring(0, ws).trim();
    }

    while (!s.isEmpty()) {
      char c = s.charAt(s.length() - 1);
      if (c == '.'
          || c == ','
          || c == ')'
          || c == ']'
          || c == '}'
          || c == '>'
          || c == '!'
          || c == '?'
          || c == ';'
          || c == ':'
          || c == '\''
          || c == '"') {
        s = s.substring(0, s.length() - 1).trim();
        continue;
      }
      break;
    }
    s = trimEdgeNoise(s);

    if (s.isBlank()) return null;

    String lower = s.toLowerCase(Locale.ROOT);
    if (lower.startsWith("www.")) {
      s = "https://" + s;
      lower = s.toLowerCase(Locale.ROOT);
    }

    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
      return null;
    }

    try {
      URI uri = new URI(s);
      String scheme = Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT);
      if (!scheme.equals("http") && !scheme.equals("https")) return null;
      if (Objects.toString(uri.getHost(), "").isBlank()) return null;
      return uri.toASCIIString();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String trimEdgeNoise(String s) {
    if (s == null || s.isEmpty()) return "";
    int start = 0;
    int end = s.length();
    while (start < end && isEdgeNoise(s.charAt(start))) start++;
    while (end > start && isEdgeNoise(s.charAt(end - 1))) end--;
    return (start == 0 && end == s.length()) ? s : s.substring(start, end);
  }

  private static boolean isEdgeNoise(char c) {
    return Character.isWhitespace(c) || Character.isISOControl(c);
  }

  private static int firstWhitespaceIndex(String s) {
    if (s == null || s.isEmpty()) return -1;
    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) return i;
    }
    return -1;
  }
}

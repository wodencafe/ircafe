package cafe.woden.ircclient.ui.shell;

import cafe.woden.ircclient.config.api.UiShellRuntimeConfigPort;
import cafe.woden.ircclient.net.HttpLite;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.util.AppVersion;
import cafe.woden.ircclient.util.VirtualThreads;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@InterfaceLayer
@Lazy
public class UpdateNotifierService {

  private static final Logger log = LoggerFactory.getLogger(UpdateNotifierService.class);
  private static final String RELEASES_URL = "https://github.com/wodencafe/ircafe/releases";
  private static final URI LATEST_RELEASE_API_URI =
      URI.create("https://api.github.com/repos/wodencafe/ircafe/releases/latest");
  private static final long INITIAL_CHECK_DELAY_SECONDS = 7L;
  private static final long CHECK_INTERVAL_HOURS = 6L;
  private static final long PLATFORM_OPEN_EXIT_CHECK_TIMEOUT_MS = 450L;
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

  private static final Pattern TAG_NAME_PATTERN =
      Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern HTML_URL_PATTERN =
      Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern VERSION_PREFIX_PATTERN =
      Pattern.compile("(?i)^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?.*");

  private final UiShellRuntimeConfigPort runtimeConfig;
  private final StatusBar statusBar;
  private final ScheduledExecutorService scheduler;

  private final AtomicBoolean enabled = new AtomicBoolean(true);
  private final AtomicBoolean checkInProgress = new AtomicBoolean(false);
  private final AtomicReference<ScheduledFuture<?>> periodicCheckTask = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> startupCheckTask = new AtomicReference<>();

  private volatile String lastAlertedTag;

  public UpdateNotifierService(UiShellRuntimeConfigPort runtimeConfig, StatusBar statusBar) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    this.statusBar = Objects.requireNonNull(statusBar, "statusBar");
    this.scheduler = VirtualThreads.newSingleThreadScheduledExecutor("ircafe-update-notifier");
  }

  @PostConstruct
  void start() {
    statusBar.setUpdateNotifierActions(
        this::checkNow, this::openReleasesPage, this::disableFromContextMenu);
    boolean initialEnabled = runtimeConfig.readUpdateNotifierEnabled(true);
    applyEnabled(initialEnabled, false);
  }

  @PreDestroy
  void shutdown() {
    cancelTask(periodicCheckTask);
    cancelTask(startupCheckTask);
    scheduler.shutdownNow();
  }

  public void setEnabled(boolean on) {
    applyEnabled(on, false);
  }

  public void checkNow() {
    if (!enabled.get()) return;
    statusBar.setUpdateNotifierChecking();
    scheduler.execute(this::checkForUpdatesSafely);
  }

  private synchronized void applyEnabled(boolean on, boolean persist) {
    enabled.set(on);

    if (persist) {
      runtimeConfig.rememberUpdateNotifierEnabled(on);
    }

    if (!on) {
      lastAlertedTag = null;
      cancelTask(periodicCheckTask);
      cancelTask(startupCheckTask);
      statusBar.setUpdateNotifierEnabled(false);
      return;
    }

    statusBar.setUpdateNotifierEnabled(true);
    statusBar.setUpdateNotifierStatus(
        "Checking for IRCafe updates...", StatusBar.UpdateNotifierState.IDLE);
    scheduleChecksIfNeeded();
  }

  private void disableFromContextMenu() {
    applyEnabled(false, true);
    statusBar.enqueueNotification("Update notifier disabled. Re-enable it in Preferences.", null);
  }

  private void scheduleChecksIfNeeded() {
    ScheduledFuture<?> periodic = periodicCheckTask.get();
    if (periodic == null || periodic.isCancelled() || periodic.isDone()) {
      ScheduledFuture<?> next =
          scheduler.scheduleWithFixedDelay(
              this::checkForUpdatesSafely,
              CHECK_INTERVAL_HOURS,
              CHECK_INTERVAL_HOURS,
              TimeUnit.HOURS);
      periodicCheckTask.set(next);
    }

    ScheduledFuture<?> startup = startupCheckTask.get();
    if (startup == null || startup.isCancelled() || startup.isDone()) {
      startupCheckTask.set(
          scheduler.schedule(
              this::checkForUpdatesSafely, INITIAL_CHECK_DELAY_SECONDS, TimeUnit.SECONDS));
    }
  }

  private void checkForUpdatesSafely() {
    if (!enabled.get()) return;
    if (!checkInProgress.compareAndSet(false, true)) return;

    try {
      if (!enabled.get()) return;
      ReleaseInfo latestRelease = fetchLatestRelease();
      if (!enabled.get()) return;
      if (latestRelease == null) {
        statusBar.setUpdateNotifierStatus(
            "Could not check for updates right now. Right-click to visit releases.",
            StatusBar.UpdateNotifierState.ERROR);
        return;
      }

      String latestTag = Objects.toString(latestRelease.tag(), "").trim();
      String currentVersion = Objects.toString(AppVersion.version(), "").trim();
      String currentDisplay = currentVersion.isBlank() ? "unknown" : currentVersion;

      boolean newer =
          !currentVersion.isBlank() && compareVersionsForTest(latestTag, currentVersion) > 0;
      if (!enabled.get()) return;

      if (newer) {
        statusBar.setUpdateNotifierStatus(
            "New IRCafe version available: "
                + latestTag
                + " (you are on "
                + currentDisplay
                + "). Right-click for actions.",
            StatusBar.UpdateNotifierState.UPDATE_AVAILABLE);
        if (!latestTag.equalsIgnoreCase(Objects.toString(lastAlertedTag, ""))) {
          statusBar.showUpdateNotifierTooltipAlert(
              "A newer IRCafe release is available: "
                  + latestTag
                  + " (current: "
                  + currentDisplay
                  + ")");
          lastAlertedTag = latestTag;
        }
      } else {
        statusBar.setUpdateNotifierStatus(
            "IRCafe " + currentDisplay + ". Right-click to visit releases.",
            StatusBar.UpdateNotifierState.IDLE);
      }
    } catch (Exception e) {
      log.debug("update notifier check failed", e);
      statusBar.setUpdateNotifierStatus(
          "Could not check for updates right now. Right-click to visit releases.",
          StatusBar.UpdateNotifierState.ERROR);
    } finally {
      checkInProgress.set(false);
    }
  }

  private ReleaseInfo fetchLatestRelease() {
    try {
      Map<String, String> headers =
          Map.of(
              "Accept", "application/vnd.github+json",
              "Accept-Encoding", "gzip",
              "X-GitHub-Api-Version", "2022-11-28",
              "User-Agent", "ircafe-update-notifier/1.0");

      HttpLite.Response<String> response =
          HttpLite.getString(LATEST_RELEASE_API_URI, headers, NetProxyContext.proxy(), 4000, 7000);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return null;
      }

      String body = response.body();
      String tag = findFirstGroup(TAG_NAME_PATTERN, body);
      if (tag == null || tag.isBlank()) {
        return null;
      }

      String html = findFirstGroup(HTML_URL_PATTERN, body);
      if (html == null || html.isBlank()) {
        html = RELEASES_URL;
      }
      return new ReleaseInfo(tag.trim(), html.trim());
    } catch (Exception e) {
      log.debug("update notifier fetch failed", e);
      return null;
    }
  }

  static int compareVersionsForTest(String left, String right) {
    int[] a = parseVersionPrefix(left);
    int[] b = parseVersionPrefix(right);
    if (a == null || b == null) return 0;

    int max = Math.max(a.length, b.length);
    for (int i = 0; i < max; i++) {
      int av = i < a.length ? a[i] : 0;
      int bv = i < b.length ? b[i] : 0;
      if (av != bv) return Integer.compare(av, bv);
    }

    boolean aSnapshot = containsSnapshotToken(left);
    boolean bSnapshot = containsSnapshotToken(right);
    if (aSnapshot != bSnapshot) {
      return aSnapshot ? -1 : 1;
    }

    return 0;
  }

  private static int[] parseVersionPrefix(String raw) {
    String v = Objects.toString(raw, "").trim();
    if (v.isEmpty()) return null;

    Matcher m = VERSION_PREFIX_PATTERN.matcher(v);
    if (!m.matches()) return null;

    int[] out = new int[4];
    int count = 0;
    for (int i = 1; i <= 4; i++) {
      String part = m.group(i);
      if (part == null || part.isBlank()) {
        out[count++] = 0;
      } else {
        try {
          out[count++] = Integer.parseInt(part);
        } catch (NumberFormatException ignored) {
          out[count++] = 0;
        }
      }
    }
    return out;
  }

  private static boolean containsSnapshotToken(String raw) {
    String value = Objects.toString(raw, "").toLowerCase(Locale.ROOT);
    return value.contains("snapshot") || value.contains("-dev") || value.contains("+dev");
  }

  private static String findFirstGroup(Pattern pattern, String text) {
    if (pattern == null || text == null || text.isBlank()) return null;
    Matcher matcher = pattern.matcher(text);
    if (!matcher.find()) return null;
    String value = matcher.groupCount() >= 1 ? matcher.group(1) : null;
    return value == null ? null : value.trim();
  }

  void openReleasesPage() {
    try {
      executeBackground(this::openReleasesPageOnWorker);
    } catch (RejectedExecutionException ex) {
      log.warn(
          "Could not schedule opening releases page in browser: {} (worker unavailable)",
          RELEASES_URL,
          ex);
      statusBar.enqueueNotification("Could not open browser for updates.", null);
    } catch (Exception ex) {
      log.warn("Could not schedule opening releases page in browser: {}", RELEASES_URL, ex);
      statusBar.enqueueNotification("Could not open browser for updates.", null);
    }
  }

  protected void executeBackground(Runnable task) {
    if (task == null) return;
    scheduler.execute(task);
  }

  protected void openReleasesPageOnWorker() {
    String url = RELEASES_URL;
    try {
      if (isLinux() && tryPlatformOpen(url)) return;
      if (tryDesktopBrowse(url)) return;
      if (!isLinux() && tryPlatformOpen(url)) return;
      throw new UnsupportedOperationException("No browser launch strategy succeeded");
    } catch (Exception e) {
      log.warn("Could not open releases page in browser: {}", url, e);
      statusBar.enqueueNotification("Could not open browser for updates.", null);
    }
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

  protected boolean tryPlatformOpen(String url) {
    String os = currentOsLowerCase();
    if (os.contains("linux")) {
      return tryKnownLinuxBrowser(url)
          || tryStart("xdg-open", url)
          || tryStart("gio", "open", url)
          || tryStart("sensible-browser", url)
          || tryStart("x-www-browser", url)
          || tryStart("gnome-open", url)
          || tryStart("kde-open", url);
    }
    if (os.contains("mac") || os.contains("darwin")) {
      return tryStart("open", url);
    }
    if (os.contains("win")) {
      return tryStart("rundll32", "url.dll,FileProtocolHandler", url)
          || tryStart("cmd", "/c", "start", "", url);
    }
    return false;
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

  protected boolean isLinux() {
    return currentOsLowerCase().contains("linux");
  }

  private static void cancelTask(AtomicReference<ScheduledFuture<?>> ref) {
    if (ref == null) return;
    ScheduledFuture<?> future = ref.getAndSet(null);
    if (future == null) return;
    try {
      future.cancel(false);
    } catch (Exception ignored) {
    }
  }

  private record ReleaseInfo(String tag, String htmlUrl) {}
}

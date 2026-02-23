package cafe.woden.ircclient.ui.tray;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.notifications.IrcEventNotificationRule;
import cafe.woden.ircclient.ui.MainFrame;
import cafe.woden.ircclient.ui.StatusBar;
import cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import dorkbox.notify.Notify;
import dorkbox.notify.Position;
import dorkbox.notify.Theme;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.awt.Frame;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import javax.swing.SwingUtilities;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Desktop notifications for tray users.
 *
 * <p>We intentionally avoid hard dependencies here. On Linux we try {@code notify-send},
 * on macOS we try {@code osascript}. If neither is available we fall back to a simple beep.
 */
@Component
public class TrayNotificationService {
  private static final Logger log = LoggerFactory.getLogger(TrayNotificationService.class);

  private static final Duration CONTENT_DEDUPE_WINDOW = Duration.ofSeconds(2);
  private static final Duration RATE_WINDOW = Duration.ofSeconds(10);
  private static final int PER_TARGET_MAX_PER_WINDOW = 3;
  private static final int GLOBAL_MAX_PER_WINDOW = 10;
  private static final Duration CONTENT_KEY_TTL = Duration.ofMinutes(2);
  private static final int MAX_BODY_LEN = 220;

  private final UiSettingsBus settingsBus;
  private final ObjectProvider<TrayService> trayServiceProvider;
  private final ObjectProvider<MainFrame> mainFrameProvider;
  private final ObjectProvider<StatusBar> statusBarProvider;
  private final ObjectProvider<TargetCoordinator> targetCoordinatorProvider;
  private final ObjectProvider<cafe.woden.ircclient.ui.ServerTreeDockable> serverTreeProvider;
  private final ObjectProvider<GnomeDbusNotificationBackend> gnomeDbusProvider;
  private final NotificationSoundService soundService;
  private final FlowableProcessor<NotificationRequest> requests;
  private final CompositeDisposable disposables = new CompositeDisposable();
  private final Map<String, Long> lastContentAtMs = new ConcurrentHashMap<>();

  public TrayNotificationService(
      UiSettingsBus settingsBus,
      ObjectProvider<TrayService> trayServiceProvider,
      ObjectProvider<MainFrame> mainFrameProvider,
      ObjectProvider<StatusBar> statusBarProvider,
      ObjectProvider<TargetCoordinator> targetCoordinatorProvider,
      ObjectProvider<cafe.woden.ircclient.ui.ServerTreeDockable> serverTreeProvider,
      ObjectProvider<GnomeDbusNotificationBackend> gnomeDbusProvider,
      NotificationSoundService soundService
  ) {
    this.settingsBus = settingsBus;
    this.trayServiceProvider = trayServiceProvider;
    this.mainFrameProvider = mainFrameProvider;
    this.statusBarProvider = statusBarProvider;
    this.targetCoordinatorProvider = targetCoordinatorProvider;
    this.serverTreeProvider = serverTreeProvider;
    this.gnomeDbusProvider = gnomeDbusProvider;
    this.soundService = soundService;

    this.requests = PublishProcessor.<NotificationRequest>create().toSerialized();
    installRateLimiterPipeline();
  }

  @PreDestroy
  void shutdown() {
    try {
      disposables.dispose();
    } catch (Exception ignored) {
    }
  }

  public void notifyHighlight(String serverId, String channel, String fromNick, String message) {
    UiSettings s = settingsBus.get();
    if (!s.trayEnabled() || !s.trayNotifyHighlights()) return;
    String title = "Highlight" + (channel != null && !channel.isBlank() ? " in " + channel : "");
    String body = (fromNick != null && !fromNick.isBlank() ? fromNick + ": " : "") + safeBody(message);
    TargetRef target = safeTargetRef(serverId, channel, "status");
    if (!passesNotifyConditions(target)) return;
    String targetKey = targetKey(serverId, channel);
    notifyAsync(targetKey, contentKey(targetKey, title, body), title, body, () -> openTarget(target));
  }

  public void notifyPrivateMessage(String serverId, String fromNick, String message) {
    UiSettings s = settingsBus.get();
    if (!s.trayEnabled() || !s.trayNotifyPrivateMessages()) return;
    String title = "PM" + (fromNick != null && !fromNick.isBlank() ? " from " + fromNick : "");
    String body = safeBody(message);
    TargetRef target = safeTargetRef(serverId, fromNick, "status");
    if (!passesNotifyConditions(target)) return;
    String targetKey = targetKey(serverId, fromNick);
    notifyAsync(targetKey, contentKey(targetKey, title, body), title, body, () -> openTarget(target));
  }

  public void notifyInvite(String serverId, String channel, String fromNick, String reason) {
    UiSettings s = settingsBus.get();
    if (!s.trayEnabled() || !s.trayNotifyPrivateMessages()) return;

    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    String from = Objects.toString(fromNick, "").trim();
    String rsn = Objects.toString(reason, "").trim();

    String title = "Invite" + (!ch.isBlank() ? " to " + ch : "");
    StringBuilder body = new StringBuilder();
    if (!from.isBlank()) {
      body.append(from).append(" invited you");
    } else {
      body.append("Channel invitation");
    }
    if (!sid.isBlank()) body.append(" on ").append(sid);
    if (!rsn.isBlank()) body.append(": ").append(safeBody(rsn));

    TargetRef target = safeTargetRef(serverId, "status", "status");
    if (!passesNotifyConditions(target)) return;
    String targetKey = targetKey(serverId, ch.isBlank() ? "status" : ch);
    notifyAsync(targetKey, contentKey(targetKey, title, body.toString()), title, body.toString(), () -> openTarget(target));
  }

  public void notifyConnectionState(String serverId, String state, String detail) {
    UiSettings s = settingsBus.get();
    if (!s.trayEnabled() || !s.trayNotifyConnectionState()) return;
    String title = "Connection" + (serverId != null && !serverId.isBlank() ? " (" + serverId + ")" : "");
    String body = (state != null && !state.isBlank() ? state : "") +
        (detail != null && !detail.isBlank() ? (state != null && !state.isBlank() ? ": " : "") + safeBody(detail) : "");
    TargetRef target = safeTargetRef(serverId, "status", "status");
    if (!passesNotifyConditions(target)) return;
    String targetKey = targetKey(serverId, "status");
    notifyAsync(targetKey, contentKey(targetKey, title, body), title, body, () -> openTarget(target));
  }

  /**
   * Sends a test notification from the preferences UI.
   */
  public void notifyTest() {
    UiSettings s = settingsBus.get();
    if (s == null || !s.trayEnabled()) return;
    String title = "IRCafe";
    String body = "Test notification (click to open IRCafe)";
    String targetKey = targetKey("", "");
    notifyAsync(targetKey, contentKey(targetKey, title, body), title, body, this::showMainWindowOnly);
  }

  /**
   * One-time discoverability hint shown when IRCafe is first hidden to tray.
   */
  public void notifyCloseToTrayHint() {
    UiSettings s = settingsBus.get();
    if (s == null || !s.trayEnabled()) return;

    String title = "IRCafe is still running";
    String body = "IRCafe was hidden to the system tray. Use the tray icon/menu to reopen it.";
    String targetKey = targetKey("ircafe", "tray");
    notifyAsync(
        targetKey,
        contentKey(targetKey, "tray-close-hint", body),
        title,
        body,
        this::showMainWindowOnly,
        true,
        true,
        SoundDirective.none());
  }

  /**
   * Sends a custom notification driven by user-configured IRC event rules.
   */
  public void notifyCustom(
      String serverId,
      String target,
      String title,
      String body,
      boolean showToast,
      boolean showStatusBar,
      IrcEventNotificationRule.FocusScope focusScope,
      boolean playSound,
      String soundId,
      boolean soundUseCustom,
      String soundCustomPath
  ) {
    UiSettings s = settingsBus.get();
    boolean trayEnabled = s != null && s.trayEnabled();
    StatusBar statusBar = statusBarProvider != null ? statusBarProvider.getIfAvailable() : null;
    if (!trayEnabled && (!showStatusBar || statusBar == null)) return;

    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    String tgt = Objects.toString(target, "").trim();
    if (tgt.isEmpty()) tgt = "status";

    TargetRef openTarget = safeTargetRef(sid, tgt, "status");
    String finalTitle = Objects.toString(title, "").trim();
    if (finalTitle.isEmpty()) finalTitle = "IRCafe";
    String finalBody = safeBody(body);

    IrcEventNotificationRule.FocusScope effectiveFocusScope =
        focusScope != null ? focusScope : IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY;
    boolean focusedNow = isMainWindowFocused();
    boolean scopedShowToast = showToast;
    boolean scopedPlaySound = playSound;
    if (effectiveFocusScope == IrcEventNotificationRule.FocusScope.FOREGROUND_ONLY && !focusedNow) {
      scopedShowToast = false;
      scopedPlaySound = false;
    } else if (effectiveFocusScope == IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY && focusedNow) {
      scopedShowToast = false;
      scopedPlaySound = false;
    }

    boolean effectiveShowToast = false;
    boolean effectivePlaySound = false;
    boolean allowWhenFocused = effectiveFocusScope != IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY;
    if (trayEnabled && (scopedShowToast || scopedPlaySound)) {
      boolean trayAllowed = passesNotifyConditions(openTarget, allowWhenFocused);
      effectiveShowToast = trayAllowed && scopedShowToast;
      effectivePlaySound = trayAllowed && scopedPlaySound;
    }

    boolean effectiveShowStatusBar = showStatusBar && statusBar != null;
    if (!effectiveShowStatusBar && !effectiveShowToast && !effectivePlaySound) return;

    String targetKey = targetKey(sid, tgt);
    String routeKey;
    if (effectiveShowToast) {
      routeKey = "toast";
    } else if (effectiveShowStatusBar) {
      routeKey = "status";
    } else {
      routeKey = "sound";
    }
    String contentKey = contentKey(
        targetKey,
        "event",
        routeKey + "|" + finalTitle + "|" + finalBody);

    SoundDirective soundDirective = effectivePlaySound
        ? SoundDirective.override(soundId, soundUseCustom, soundCustomPath)
        : SoundDirective.none();

    Runnable onClick = () -> openTarget(openTarget);
    notifyAsync(
        targetKey,
        contentKey,
        finalTitle,
        finalBody,
        onClick,
        effectiveShowToast,
        effectiveShowStatusBar,
        soundDirective);
  }

  private boolean passesNotifyConditions(TargetRef target) {
    return passesNotifyConditions(target, false);
  }

  private boolean passesNotifyConditions(TargetRef target, boolean allowWhenFocused) {
    UiSettings s = settingsBus.get();
    if (s == null || !s.trayEnabled()) return false;

    if (!allowWhenFocused && s.trayNotifyOnlyWhenUnfocused() && isMainWindowFocused()) {
      return false;
    }

    if (s.trayNotifyOnlyWhenMinimizedOrHidden() && !isMainWindowMinimizedOrHidden()) {
      return false;
    }

    if (s.trayNotifySuppressWhenTargetActive() && isTargetActive(target)) {
      return false;
    }

    return true;
  }

  private boolean isMainWindowFocused() {
    MainFrame frame = mainFrameProvider != null ? mainFrameProvider.getIfAvailable() : null;
    if (frame == null) return false;
    return frame.isVisible() && frame.isActive();
  }

  private boolean isMainWindowMinimizedOrHidden() {
    MainFrame frame = mainFrameProvider != null ? mainFrameProvider.getIfAvailable() : null;
    if (frame == null) return true;
    if (!frame.isVisible()) return true;
    int st = frame.getExtendedState();
    return (st & Frame.ICONIFIED) != 0;
  }

  private boolean isTargetActive(TargetRef target) {
    try {
      TargetCoordinator tc = targetCoordinatorProvider != null ? targetCoordinatorProvider.getIfAvailable() : null;
      if (tc == null) return false;
      TargetRef active = tc.getActiveTarget();
      return active != null && active.equals(target);
    } catch (Exception ignored) {
      return false;
    }
  }

  private void notifyAsync(String targetKey, String contentKey, String title, String body, Runnable onClick) {
    // Push into the Rx pipeline; rate limiting and dedupe happen there.
    notifyAsync(targetKey, contentKey, title, body, onClick, true, true, SoundDirective.global());
  }

  private void notifyAsync(
      String targetKey,
      String contentKey,
      String title,
      String body,
      Runnable onClick,
      boolean showToast,
      boolean showStatusBar,
      SoundDirective soundDirective
  ) {
    requests.onNext(new NotificationRequest(
        targetKey,
        contentKey,
        title,
        body,
        onClick,
        showToast,
        showStatusBar,
        soundDirective != null ? soundDirective : SoundDirective.global()));
  }

  private void installRateLimiterPipeline() {
    // Periodic cleanup of dedupe keys so long-running sessions don't accumulate unlimited entries.
    disposables.add(
        io.reactivex.rxjava3.core.Flowable.interval(1, 1, TimeUnit.MINUTES, RxVirtualSchedulers.computation())
            .subscribe(tick -> cleanupContentKeys(), err -> log.debug("[ircafe] notify cleanup failed", err))
    );

    disposables.add(
        requests
            .onBackpressureDrop(req -> log.debug("[ircafe] dropping tray notification due to backpressure: {}", req.targetKey()))
            .filter(this::allowByContentDedupe)
            // Per-target: allow up to N per window.
            .groupBy(NotificationRequest::targetKey)
            .flatMap(group -> group
                .window(RATE_WINDOW.toMillis(), TimeUnit.MILLISECONDS)
                .flatMap(win -> win.take(PER_TARGET_MAX_PER_WINDOW))
            )
            // Global: allow up to N per window.
            .window(RATE_WINDOW.toMillis(), TimeUnit.MILLISECONDS)
            .flatMap(win -> win.take(GLOBAL_MAX_PER_WINDOW))
            .observeOn(RxVirtualSchedulers.io())
            .subscribe(this::sendNow, err -> log.debug("[ircafe] tray notify stream failed", err))
    );
  }

  private boolean allowByContentDedupe(NotificationRequest req) {
    if (req == null) return false;
    String k = req.contentKey();
    if (k == null || k.isBlank()) return true;

    long now = System.currentTimeMillis();
    Long last = lastContentAtMs.put(k, now);
    return last == null || (now - last) >= CONTENT_DEDUPE_WINDOW.toMillis();
  }

  private void cleanupContentKeys() {
    long cutoff = System.currentTimeMillis() - CONTENT_KEY_TTL.toMillis();
    lastContentAtMs.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < cutoff);
  }

  private void sendNow(NotificationRequest req) {
    if (req == null) return;
    try {
      if (req.showStatusBar()) {
        enqueueStatusBarNotice(req);
      }

      if (soundService != null && req.soundDirective() != null) {
        switch (req.soundDirective().mode()) {
          case GLOBAL -> soundService.play();
          case NONE -> {
          }
          case OVERRIDE -> soundService.playOverride(
              req.soundDirective().soundId(),
              req.soundDirective().soundUseCustom(),
              req.soundDirective().soundCustomPath());
        }
      }

      if (!req.showToast()) return;

      if (tryWindowsToastPopup(req.title(), req.body(), req.onClick())) return;
      if (tryLinuxNotifySend(req.title(), req.body(), req.onClick())) return;
      if (tryMacOsascript(req.title(), req.body())) return;

      // Last-resort fallback: don't crash the app because a desktop notification couldn't be shown.
      Toolkit.getDefaultToolkit().beep();
    } catch (Exception e) {
      log.debug("[ircafe] tray notify failed", e);
    }
  }

  private void enqueueStatusBarNotice(NotificationRequest req) {
    if (req == null) return;
    StatusBar statusBar = statusBarProvider != null ? statusBarProvider.getIfAvailable() : null;
    if (statusBar == null) return;

    String title = Objects.toString(req.title(), "").trim();
    String body = Objects.toString(req.body(), "").trim();
    String text;
    if (title.isEmpty()) {
      text = body;
    } else if (body.isEmpty()) {
      text = title;
    } else {
      text = title + ": " + body;
    }
    if (text.isBlank()) return;
    statusBar.enqueueNotification(text, req.onClick());
  }

  private boolean tryWindowsToastPopup(String title, String body, Runnable onClick) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (!os.contains("win")) return false;

    // dorkbox/Notify uses its own lightweight, toast-like popup window. It's not a native Windows Action Center
    // notification, but it's reliable and requires no extra system components.
    try {
      UiSettings s = settingsBus.get();
      boolean light = s != null && s.theme() != null && s.theme().toLowerCase(Locale.ROOT).contains("light");

      Runnable show = () -> {
        try {
          Function1<Notify, Unit> click = n -> {
            if (onClick != null) {
              try {
                onClick.run();
              } catch (Throwable ignored) {
              }
            }
            return Unit.INSTANCE;
          };

          Notify.Companion.create()
              .title(title)
              .text(body)
              .position(Position.BOTTOM_RIGHT)
              .hideAfter(5_000)
              .theme(light ? Theme.Companion.getDefaultLight() : Theme.Companion.getDefaultDark())
              .onClickAction(click)
              .showWarning();
        } catch (Throwable t) {
          // If this fails for any reason, we fall back to other mechanisms.
          throw new RuntimeException(t);
        }
      };

      // Keep it Swing-safe.
      if (SwingUtilities.isEventDispatchThread()) {
        show.run();
      } else {
        SwingUtilities.invokeLater(show);
      }
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private void openTarget(TargetRef target) {
    if (target == null) return;

    Runnable r = () -> {
      try {
        TrayService tray = trayServiceProvider.getIfAvailable();
        if (tray == null) {
          tray = trayServiceProvider.getObject();
        }
        if (tray != null) {
          tray.showMainWindow();
        }
      } catch (Throwable ignored) {
      }

      try {
        cafe.woden.ircclient.ui.ServerTreeDockable serverTree = serverTreeProvider.getIfAvailable();
        if (serverTree == null) {
          serverTree = serverTreeProvider.getObject();
        }
        if (serverTree != null) {
          serverTree.selectTarget(target);
        }
      } catch (Throwable ignored) {
      }
    };

    // Jumping to a buffer is a UI action.
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
    } else {
      SwingUtilities.invokeLater(r);
    }
  }

  private void showMainWindowOnly() {
    Runnable r = () -> {
      try {
        TrayService tray = trayServiceProvider.getIfAvailable();
        if (tray == null) {
          tray = trayServiceProvider.getObject();
        }
        if (tray != null) {
          tray.showMainWindow();
        }
      } catch (Throwable ignored) {
      }
    };

    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
    } else {
      SwingUtilities.invokeLater(r);
    }
  }

  private static TargetRef safeTargetRef(String serverId, String target, String fallbackTarget) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return null;

    String t = Objects.toString(target, "").trim();
    if (t.isEmpty()) {
      t = Objects.toString(fallbackTarget, "").trim();
    }
    if (t.isEmpty()) return null;

    try {
      return new TargetRef(sid, t);
    } catch (Exception ignored) {
      return null;
    }
  }

  private boolean tryLinuxNotifySend(String title, String body, Runnable onClick) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (!os.contains("linux")) return false;

    // Step G2: if the notifications server supports actions, prefer D-Bus Notify().
    // Click handling (routing back into IRCafe) comes in G3.
    boolean preferDbus = false;
    try {
      UiSettings s = settingsBus.get();
      preferDbus = s != null && s.trayLinuxDbusActionsEnabled();
    } catch (Exception ignored) {
    }

    if (preferDbus) {
      try {
        GnomeDbusNotificationBackend backend = gnomeDbusProvider.getIfAvailable();
        if (backend != null) {
          GnomeDbusNotificationBackend.ProbeResult pr = backend.probe();
          if (pr != null && pr.sessionBusReachable() && pr.actionsSupported()) {
            GnomeDbusNotificationBackend.NotifyResult nr = backend.notifyWithDefaultAction(title, body, 5_000, onClick);
            if (nr != null && nr.sent()) {
              log.debug("[ircafe] Sent notification via DBus (id={})", nr.id());
              return true;
            }
          }
        }
      } catch (Throwable ignored) {
        // Best-effort only.
      }
    }

    // Very common on GNOME/KDE/etc. If missing, we'll silently fall back.
    ProcessBuilder pb = new ProcessBuilder(
        "notify-send",
        "--app-name=IRCafe",
        "--expire-time=5000",
        title,
        body
    );
    pb.redirectErrorStream(true);
    try {
      Process p = pb.start();
      drain(p);
      return p.waitFor() == 0;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean tryMacOsascript(String title, String body) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (!(os.contains("mac") || os.contains("darwin"))) return false;

    String script = "display notification \"" + escapeApple(body) + "\" with title \"" + escapeApple(title) + "\"";
    ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
    pb.redirectErrorStream(true);
    try {
      Process p = pb.start();
      drain(p);
      return p.waitFor() == 0;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static void drain(Process p) {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
      while (br.readLine() != null) {
        // discard
      }
    } catch (Exception ignored) {
    }
  }

  private static String escapeApple(String s) {
    if (s == null) return "";
    // AppleScript uses backslash escaping inside quoted strings.
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
  }

  private static String safeBody(String s) {
    if (s == null) return "";
    String v = s.replace('\n', ' ').replace('\r', ' ').trim();
    if (v.length() <= MAX_BODY_LEN) return v;
    return v.substring(0, MAX_BODY_LEN - 1) + "…";
  }

  private static String targetKey(String serverId, String target) {
    return Objects.toString(serverId, "").trim() + "|" + Objects.toString(target, "").trim();
  }

  private static String contentKey(String targetKey, String title, String body) {
    // Body is already bounded and sanitized; keep the key readable for debug.
    return Objects.toString(targetKey, "") + "|" + Objects.toString(title, "") + "|" + Objects.toString(body, "");
  }

  private record NotificationRequest(
      String targetKey,
      String contentKey,
      String title,
      String body,
      Runnable onClick,
      boolean showToast,
      boolean showStatusBar,
      SoundDirective soundDirective
  ) {
  }

  private enum SoundMode {
    GLOBAL,
    NONE,
    OVERRIDE
  }

  private record SoundDirective(
      SoundMode mode,
      String soundId,
      boolean soundUseCustom,
      String soundCustomPath
  ) {
    static SoundDirective global() {
      return new SoundDirective(SoundMode.GLOBAL, null, false, null);
    }

    static SoundDirective none() {
      return new SoundDirective(SoundMode.NONE, null, false, null);
    }

    static SoundDirective override(String soundId, boolean soundUseCustom, String soundCustomPath) {
      return new SoundDirective(SoundMode.OVERRIDE, soundId, soundUseCustom, soundCustomPath);
    }
  }
}

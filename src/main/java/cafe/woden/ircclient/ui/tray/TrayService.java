package cafe.woden.ircclient.ui.tray;

import cafe.woden.ircclient.app.ApplicationShutdownCoordinator;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.MainFrame;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** System tray integration (HexChat-style options). */
@Component
@Lazy
public class TrayService {

  private static final Logger log = LoggerFactory.getLogger(TrayService.class);
  private static final int CLOSE_HINT_STATUS_DURATION_MS = 8_000;

  private final UiSettingsBus settingsBus;
  private final ObjectProvider<MainFrame> frameProvider;
  private final ObjectProvider<TrayNotificationService> trayNotificationServiceProvider;
  private final ApplicationShutdownCoordinator shutdownCoordinator;
  private final RuntimeConfigStore runtimeConfigStore;

  private final AtomicBoolean installed = new AtomicBoolean(false);
  private final AtomicBoolean exitRequested = new AtomicBoolean(false);
  private final AtomicBoolean closeBalloonShown = new AtomicBoolean(false);
  private final javax.swing.Timer closeHintStatusClearTimer =
      new javax.swing.Timer(CLOSE_HINT_STATUS_DURATION_MS, e -> clearTrayStatus());

  private volatile SystemTray systemTray;
  private volatile MenuItem showHideItem;

  public TrayService(
      UiSettingsBus settingsBus,
      ObjectProvider<MainFrame> frameProvider,
      ObjectProvider<TrayNotificationService> trayNotificationServiceProvider,
      ApplicationShutdownCoordinator shutdownCoordinator,
      RuntimeConfigStore runtimeConfigStore) {
    this.settingsBus = settingsBus;
    this.frameProvider = frameProvider;
    this.trayNotificationServiceProvider = trayNotificationServiceProvider;
    this.shutdownCoordinator = shutdownCoordinator;
    this.runtimeConfigStore = runtimeConfigStore;
    this.closeHintStatusClearTimer.setRepeats(false);
    try {
      closeBalloonShown.set(runtimeConfigStore.readTrayCloseToTrayHintShown(false));
    } catch (Exception ignored) {
      closeBalloonShown.set(false);
    }
  }

  @PreDestroy
  void shutdown() {
    remove();
  }

  public boolean isTraySupported() {
    // Avoid eagerly initializing native tray plumbing just to "check".
    // In practice, if we installed successfully then it is supported.
    return isTrayActive();
  }

  public boolean isTrayActive() {
    return systemTray != null;
  }

  public boolean isEnabled() {
    return settingsBus.get().trayEnabled();
  }

  public boolean shouldCloseToTray() {
    var s = settingsBus.get();
    return s.trayEnabled() && s.trayCloseToTray() && isTrayActive();
  }

  public boolean shouldMinimizeToTray() {
    var s = settingsBus.get();
    return s.trayEnabled() && s.trayMinimizeToTray() && isTrayActive();
  }

  public boolean startMinimized() {
    var s = settingsBus.get();
    return s.trayEnabled() && s.trayStartMinimized();
  }

  public boolean isExitRequested() {
    return exitRequested.get();
  }

  /** Applies current tray preferences: install/remove tray and keep the app reachable. */
  public void applySettings() {
    if (!isEnabled()) {
      // If the window is currently hidden (tray entry point), bring it back before removing the
      // tray.
      MainFrame frame = frameProvider.getIfAvailable();
      if (frame != null && !frame.isVisible()) {
        try {
          frame.setVisible(true);
          frame.setExtendedState(frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
          frame.toFront();
        } catch (Throwable ignored) {
        }
      }
      remove();
      return;
    }

    installIfEnabled();
    updateShowHideMenuItemLabel();
  }

  /** Installs the tray icon once. Safe to call repeatedly. */
  public void installIfEnabled() {
    if (!isEnabled()) {
      return;
    }
    if (!installed.compareAndSet(false, true)) {
      return;
    }

    try {
      SystemTray tray = SystemTray.get("IRCafe");
      if (tray == null) {
        log.warn("[tray] SystemTray.get() returned null (tray not available)");
        installed.set(false);
        return;
      }

      tray.setTooltip("IRCafe");
      tray.setImage(TrayIconFactory.createDefaultTrayIconPngStream());

      // Keep the menu intentionally simple (HexChat-ish)
      showHideItem =
          tray.getMenu()
              .add(
                  new MenuItem(
                      "Show IRCafe", e -> SwingUtilities.invokeLater(this::toggleMainWindow)));
      tray.getMenu().add(new MenuItem("Exit", e -> SwingUtilities.invokeLater(this::requestExit)));
      systemTray = tray;
      updateShowHideMenuItemLabel();
      log.info("[tray] installed");
    } catch (Throwable t) {
      log.warn("[tray] failed to install system tray icon", t);
      installed.set(false);
    }
  }

  public void remove() {
    stopCloseHintStatusClearTimer();
    SystemTray tray = this.systemTray;
    this.systemTray = null;
    this.showHideItem = null;

    if (tray != null) {
      try {
        tray.shutdown();
      } catch (Throwable ignored) {
      }
    }
    installed.set(false);
  }

  public void showMainWindow() {
    MainFrame frame = frameProvider.getIfAvailable();
    if (frame == null) return;
    frame.setVisible(true);
    frame.setExtendedState(frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
    frame.toFront();
    frame.requestFocus();
    updateShowHideMenuItemLabel();
  }

  public void hideMainWindow() {
    MainFrame frame = frameProvider.getIfAvailable();
    if (frame == null) return;
    frame.setVisible(false);
    updateShowHideMenuItemLabel();
  }

  public void toggleMainWindow() {
    MainFrame frame = frameProvider.getIfAvailable();
    if (frame == null) return;
    if (frame.isVisible()) {
      hideMainWindow();
    } else {
      showMainWindow();
    }
  }

  private void updateShowHideMenuItemLabel() {
    MenuItem item = this.showHideItem;
    MainFrame frame = frameProvider.getIfAvailable();
    if (item == null || frame == null) return;
    try {
      item.setText(frame.isVisible() ? "Hide IRCafe" : "Show IRCafe");
    } catch (Throwable ignored) {
    }
  }

  /** Called by the frame close handler when we hide-to-tray. */
  public void maybeShowCloseBalloon() {
    SystemTray tray = this.systemTray;
    if (tray == null) return;
    if (!closeBalloonShown.compareAndSet(false, true)) {
      return;
    }

    try {
      if (runtimeConfigStore != null) {
        runtimeConfigStore.rememberTrayCloseToTrayHintShown(true);
      }
    } catch (Exception ignored) {
    }

    try {
      TrayNotificationService notificationService =
          trayNotificationServiceProvider != null
              ? trayNotificationServiceProvider.getIfAvailable()
              : null;
      if (notificationService != null) {
        notificationService.notifyCloseToTrayHint();
      }
    } catch (Exception ignored) {
    }

    // SystemTray doesn't provide cross-platform "balloon" notifications.
    // Set a short-lived status hint instead (non-intrusive, works everywhere).
    try {
      tray.setStatus("Still running in tray");
      restartCloseHintStatusClearTimer();
    } catch (Throwable ignored) {
    }
  }

  public void requestExit() {
    if (!exitRequested.compareAndSet(false, true)) {
      return;
    }

    try {
      MainFrame frame = frameProvider.getIfAvailable();
      if (frame != null) {
        try {
          frame.setVisible(false);
          frame.dispose();
        } catch (Throwable ignored) {
        }
      }
      remove();
    } finally {
      shutdownCoordinator.shutdown();
    }
  }

  private void restartCloseHintStatusClearTimer() {
    if (SwingUtilities.isEventDispatchThread()) {
      closeHintStatusClearTimer.restart();
      return;
    }
    SwingUtilities.invokeLater(closeHintStatusClearTimer::restart);
  }

  private void stopCloseHintStatusClearTimer() {
    if (SwingUtilities.isEventDispatchThread()) {
      closeHintStatusClearTimer.stop();
      return;
    }
    SwingUtilities.invokeLater(closeHintStatusClearTimer::stop);
  }

  private void clearTrayStatus() {
    SystemTray tray = this.systemTray;
    if (tray == null) return;
    try {
      tray.setStatus(null);
    } catch (Throwable ignored) {
    }
  }
}

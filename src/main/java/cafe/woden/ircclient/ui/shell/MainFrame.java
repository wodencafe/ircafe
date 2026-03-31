package cafe.woden.ircclient.ui.shell;

import cafe.woden.ircclient.app.ApplicationShutdownCoordinator;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.config.api.UiShellRuntimeConfigPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.UserListDockable;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.docking.DockingHeaderContextMenuInstaller;
import cafe.woden.ircclient.ui.docking.DockingTuner;
import cafe.woden.ircclient.ui.icons.AppIcons;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.tray.TrayService;
import cafe.woden.ircclient.util.AppVersion;
import io.github.andrewauclair.moderndocking.Dockable;
import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.app.RootDockingPanel;
import jakarta.annotation.PreDestroy;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@InterfaceLayer
@Lazy
public class MainFrame extends JFrame {

  private static final Logger log = LoggerFactory.getLogger(MainFrame.class);

  // Default side-dock sizes on first open. These are only applied once (best-effort)
  // and then preserved by the split-pane "lock" logic.
  private static final int DEFAULT_SERVER_DOCK_WIDTH_PX = 280;
  private static final int DEFAULT_USERS_DOCK_WIDTH_PX = 240;

  private final UiShellRuntimeConfigPort runtimeConfigStore;
  private final ServerTreeDockable serverTree;
  private final LagIndicatorService lagIndicatorService;
  private final AtomicBoolean selectedTargetPersistedOnShutdown = new AtomicBoolean(false);
  private final boolean preserveDockLayoutEnabled;
  private volatile boolean layoutSnapshotPersistedOnWindowClosing;

  public MainFrame(
      AppMenuBar menuBar,
      UiProperties uiProps,
      UiShellRuntimeConfigPort runtimeConfigStore,
      TrayService trayService,
      ServerTreeDockable serverTree,
      ChatDockable chat,
      UserListDockable users,
      ChatDockManager chatDockManager,
      UpdateNotifierService updateNotifierService,
      LagIndicatorService lagIndicatorService,
      StatusBar statusBar,
      ApplicationShutdownCoordinator shutdownCoordinator) {
    super(AppVersion.windowTitle());
    this.runtimeConfigStore = runtimeConfigStore;
    this.serverTree = serverTree;
    this.lagIndicatorService = lagIndicatorService;

    // Window/taskbar icon (best-effort, cross-platform).
    try {
      setIconImages(AppIcons.windowIcons());
      AppIcons.tryInstallTaskbarIcon();
    } catch (Throwable ignored) {
    }

    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    setSize(1100, 700);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    setJMenuBar(menuBar);

    // Global find shortcut: Ctrl+F opens the chat transcript find bar.
    KeyStroke ctrlF = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlF, "chatFind");
    getRootPane()
        .getActionMap()
        .put(
            "chatFind",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                chat.toggleFindBar();
              }
            });

    resetDockingRuntime("startup");
    DockingHeaderContextMenuInstaller.install();
    Docking.initialize(this);
    // DockingUI.initialize(); // TODO: Investigate this.

    RootDockingPanel root = new RootDockingPanel(this);
    add(root, BorderLayout.CENTER);
    add(statusBar, BorderLayout.SOUTH);

    // Force initialization so lag polling starts even when the service bean is lazy.
    this.lagIndicatorService.setEnabled(runtimeConfigStore.readLagIndicatorEnabled(true));

    registerDockableIfNeeded(chat);
    registerDockableIfNeeded(serverTree);
    registerDockableIfNeeded(users);
    Docking.setUserDynamicDockableCreationListener(
        (persistentId, className, displayText, tabText, properties) ->
            chatDockManager.dynamicDockableForPersistentId(persistentId));

    boolean preserveDockLayout =
        uiProps != null
            && uiProps.layout() != null
            && Boolean.TRUE.equals(uiProps.layout().preserveDockLayout());
    this.preserveDockLayoutEnabled = preserveDockLayout;
    boolean restoredDockLayout = false;
    try {
      var dockingApi = Docking.getSingleInstance();
      if (dockingApi != null) {
        var appState = dockingApi.getAppState();
        if (appState != null) {
          File persistFile = resolveDockLayoutPersistFile(runtimeConfigStore);
          appState.setPersistFile(persistFile);
          appState.setAutoPersist(preserveDockLayout);
          if (preserveDockLayout && persistFile.isFile()) {
            restoredDockLayout = appState.restore();
          }
        }
      }
    } catch (Exception e) {
      log.warn("docking: failed to restore persisted layout; falling back to startup layout", e);
      restoredDockLayout = false;
    }
    if (restoredDockLayout && !Docking.isDocked(chat)) {
      restoredDockLayout = false;
    }

    if (!restoredDockLayout) {
      ensureStartupDockLayout(chat, serverTree, users);
    }
    final java.util.concurrent.atomic.AtomicBoolean initialSideSizesApplied =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    final java.util.concurrent.atomic.AtomicBoolean restoredSideLocksSeeded =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    // Startup stabilization: during the first ~1s, ModernDocking may perform additional layout
    // passes that can override divider locations. We allow "initial size nudging" only during
    // this short window to avoid oscillation/jitter during user-driven resize.
    final long startupStabilizationDeadlineNanos =
        System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(900);

    // Persist user-resized dock widths (debounced) so they survive restart.
    final int[] pendingServerDockWidth = new int[] {-1};
    final int[] pendingUsersDockWidth = new int[] {-1};

    int initialServerDockWidth = DEFAULT_SERVER_DOCK_WIDTH_PX;
    int initialUsersDockWidth = DEFAULT_USERS_DOCK_WIDTH_PX;
    if (uiProps != null && uiProps.layout() != null) {
      initialServerDockWidth = uiProps.layout().serverDockWidthPx();
      initialUsersDockWidth = uiProps.layout().userDockWidthPx();
    }
    final int[] lastSavedServerDockWidth = new int[] {initialServerDockWidth};
    final int[] lastSavedUsersDockWidth = new int[] {initialUsersDockWidth};

    final Timer persistServerDockTimer =
        new Timer(
            350,
            e -> {
              int w = pendingServerDockWidth[0];
              if (w <= 0) return;
              // Clamp to keep configs sane.
              int clamped = Math.max(120, Math.min(1200, w));
              if (Math.abs(clamped - lastSavedServerDockWidth[0]) < 2) return;
              lastSavedServerDockWidth[0] = clamped;
              if (runtimeConfigStore != null) {
                runtimeConfigStore.rememberServerDockWidthPx(clamped);
              }
            });
    persistServerDockTimer.setRepeats(false);

    final Timer persistUsersDockTimer =
        new Timer(
            350,
            e -> {
              int w = pendingUsersDockWidth[0];
              if (w <= 0) return;
              int clamped = Math.max(120, Math.min(1200, w));
              if (Math.abs(clamped - lastSavedUsersDockWidth[0]) < 2) return;
              lastSavedUsersDockWidth[0] = clamped;
              if (runtimeConfigStore != null) {
                runtimeConfigStore.rememberUserDockWidthPx(clamped);
              }
            });
    persistUsersDockTimer.setRepeats(false);

    // If the app closes quickly after a divider drag, the debounce timers might not have fired yet.
    // Flush any pending widths on close so the user's last drag is not lost.
    Runnable flushPendingDockWidths =
        () -> {
          try {
            persistServerDockTimer.stop();
            persistUsersDockTimer.stop();
          } catch (Exception ignored) {
          }

          int sw = pendingServerDockWidth[0];
          if (sw > 0) {
            int clamped = Math.max(120, Math.min(1200, sw));
            if (Math.abs(clamped - lastSavedServerDockWidth[0]) >= 2) {
              lastSavedServerDockWidth[0] = clamped;
              if (runtimeConfigStore != null) {
                runtimeConfigStore.rememberServerDockWidthPx(clamped);
              }
            }
          }

          int uw = pendingUsersDockWidth[0];
          if (uw > 0) {
            int clamped = Math.max(120, Math.min(1200, uw));
            if (Math.abs(clamped - lastSavedUsersDockWidth[0]) >= 2) {
              lastSavedUsersDockWidth[0] = clamped;
              if (runtimeConfigStore != null) {
                runtimeConfigStore.rememberUserDockWidthPx(clamped);
              }
            }
          }

          // Persist the final on-screen widths as a fallback, even if no drag event was captured.
          int currentServerDockWidth = stableDockWidthSeed(serverTree, lastSavedServerDockWidth[0]);
          if (Math.abs(currentServerDockWidth - lastSavedServerDockWidth[0]) >= 2) {
            lastSavedServerDockWidth[0] = currentServerDockWidth;
            if (runtimeConfigStore != null) {
              runtimeConfigStore.rememberServerDockWidthPx(currentServerDockWidth);
            }
          }

          int currentUsersDockWidth = stableDockWidthSeed(users, lastSavedUsersDockWidth[0]);
          if (Math.abs(currentUsersDockWidth - lastSavedUsersDockWidth[0]) >= 2) {
            lastSavedUsersDockWidth[0] = currentUsersDockWidth;
            if (runtimeConfigStore != null) {
              runtimeConfigStore.rememberUserDockWidthPx(currentUsersDockWidth);
            }
          }
        };

    final boolean enforceConfiguredSideDockWidths = !preserveDockLayout || !restoredDockLayout;
    Runnable applyDockLocks =
        () -> {
          int serverPx = DEFAULT_SERVER_DOCK_WIDTH_PX;
          int usersPx = DEFAULT_USERS_DOCK_WIDTH_PX;
          if (uiProps != null && uiProps.layout() != null) {
            serverPx = uiProps.layout().serverDockWidthPx();
            usersPx = uiProps.layout().userDockWidthPx();
          }

          if (enforceConfiguredSideDockWidths) {
            // On first open, ModernDocking can initially lay out side docks wider than desired.
            // Nudge them to a reasonable default once split panes exist, then lock those sizes.
            int frameW = Math.max(1, getWidth());
            boolean sideDocksAreHuge =
                serverTree.getWidth() > (int) (frameW * 0.45)
                    || users.getWidth() > (int) (frameW * 0.45);
            boolean inStartupStabilization = System.nanoTime() < startupStabilizationDeadlineNanos;
            boolean shouldNudge =
                !initialSideSizesApplied.get() || (inStartupStabilization && sideDocksAreHuge);
            if (shouldNudge) {
              boolean west = DockingTuner.applyInitialWestDockWidth(this, serverTree, serverPx);
              boolean east = DockingTuner.applyInitialEastDockWidth(this, users, usersPx);

              if (west && east) {
                initialSideSizesApplied.set(true);
              }
            }
            // Give horizontal growth to the chat transcript instead of the side docks.
            // Seed locks with configured widths to avoid capturing transient startup geometry.
            DockingTuner.lockWestDockWidth(this, serverTree, serverPx);
            DockingTuner.lockEastDockWidth(this, users, usersPx);
          } else {
            if (!restoredSideLocksSeeded.get()) {
              // Restore mode: seed once from persisted widths, then keep locking without reseeding
              // to avoid capturing transient startup geometry.
              DockingTuner.lockWestDockWidth(this, serverTree, serverPx);
              DockingTuner.lockEastDockWidth(this, users, usersPx);
              restoredSideLocksSeeded.set(true);
            } else {
              DockingTuner.lockWestDockWidth(this, serverTree);
              DockingTuner.lockEastDockWidth(this, users);
            }
          }

          // Watch for user divider drags and persist the resulting widths.
          DockingTuner.watchDockWidthOnUserResize(
              this,
              serverTree,
              w -> {
                // Ignore transient startup wobble; real user drags are still captured.
                pendingServerDockWidth[0] = w;
                persistServerDockTimer.restart();
              });
          DockingTuner.watchDockWidthOnUserResize(
              this,
              users,
              w -> {
                pendingUsersDockWidth[0] = w;
                persistUsersDockTimer.restart();
              });
        };

    // Apply once after the initial docking layout.
    SwingUtilities.invokeLater(applyDockLocks);

    // Re-apply on resize/show (e.g., maximize) in case ModernDocking rebuilds split panes.
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentShown(ComponentEvent e) {
            SwingUtilities.invokeLater(applyDockLocks);
          }

          @Override
          public void componentResized(ComponentEvent e) {
            SwingUtilities.invokeLater(applyDockLocks);
          }
        });

    addWindowStateListener(
        e -> {
          // optionally minimize to tray instead of the taskbar.
          if (trayService == null) return;
          int newState = e.getNewState();
          if ((newState & java.awt.Frame.ICONIFIED) == java.awt.Frame.ICONIFIED) {
            if (trayService.shouldMinimizeToTray() && !trayService.isExitRequested()) {
              SwingUtilities.invokeLater(
                  () -> {
                    try {
                      setVisible(false);
                      trayService.maybeShowCloseBalloon();
                    } catch (Exception ignored) {
                    }
                  });
            }
          }
        });

    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            flushPendingDockWidths.run();
            layoutSnapshotPersistedOnWindowClosing =
                persistDockLayoutSnapshot(preserveDockLayoutEnabled, "windowClosing");
            // Optional "close-to-tray": close button hides to tray (when supported/enabled).
            if (trayService != null
                && trayService.shouldCloseToTray()
                && !trayService.isExitRequested()) {
              setVisible(false);
              trayService.maybeShowCloseBalloon();
              return;
            }
            persistSelectedTargetForShutdown();
            try {
              setVisible(false);
              dispose();
            } catch (Exception ignored) {
            }
            shutdownCoordinator.shutdown();
          }
        });

    // Note: the mediator self-starts via @PostConstruct when the UI beans are created.
  }

  @PreDestroy
  void shutdownDocking() {
    resetDockingRuntime("shutdown");
  }

  @Override
  public void dispose() {
    persistSelectedTargetForShutdown();

    if (!preserveDockLayoutEnabled) {
      super.dispose();
      return;
    }

    if (!layoutSnapshotPersistedOnWindowClosing && isShowing()) {
      persistDockLayoutSnapshot(true, "dispose(showing)");
    }
    super.dispose();
  }

  private void persistSelectedTargetForShutdown() {
    if (runtimeConfigStore == null || serverTree == null) return;
    if (!selectedTargetPersistedOnShutdown.compareAndSet(false, true)) return;

    TargetRef selected = readSelectedTargetOnEdt();
    if (selected == null) return;

    try {
      runtimeConfigStore.rememberLastSelectedTarget(selected.serverId(), selected.target());
    } catch (Exception ignored) {
    }
  }

  private TargetRef readSelectedTargetOnEdt() {
    if (SwingUtilities.isEventDispatchThread()) {
      return serverTree.selectedTargetForPersistence();
    }

    AtomicReference<TargetRef> selectedRef = new AtomicReference<>();
    try {
      SwingUtilities.invokeAndWait(
          () -> selectedRef.set(serverTree.selectedTargetForPersistence()));
    } catch (Exception ignored) {
    }
    return selectedRef.get();
  }

  private void registerDockableIfNeeded(Dockable dockable) {
    if (dockable == null) return;

    String persistentId;
    try {
      persistentId = dockable.getPersistentID();
    } catch (Exception e) {
      Docking.registerDockable(dockable);
      return;
    }

    if (persistentId != null
        && !persistentId.isBlank()
        && Docking.isDockableRegistered(persistentId)) {
      return;
    }

    try {
      Docking.registerDockable(dockable);
    } catch (RuntimeException e) {
      if (persistentId != null
          && !persistentId.isBlank()
          && Docking.isDockableRegistered(persistentId)) {
        log.debug("docking: duplicate registration ignored for id={}", persistentId);
        return;
      }
      throw e;
    }
  }

  private static File resolveDockLayoutPersistFile(UiShellRuntimeConfigPort runtimeConfigStore) {
    Path configPath = runtimeConfigStore != null ? runtimeConfigStore.runtimeConfigPath() : null;
    Path configDir = configPath != null ? configPath.getParent() : null;
    if (configDir == null) {
      String home = System.getProperty("user.home", ".");
      configDir = Paths.get(home);
    }
    return configDir.resolve("docking-layout.xml").toFile();
  }

  private void resetDockingRuntime(String phase) {
    try {
      Docking.uninitialize();
    } catch (Exception e) {
      log.debug("docking: uninitialize skipped during {} ({})", phase, e.toString());
    }
  }

  private boolean persistDockLayoutSnapshot(boolean enabled, String phase) {
    if (!enabled) return false;
    try {
      var dockingApi = Docking.getSingleInstance();
      if (dockingApi != null && dockingApi.getAppState() != null) {
        dockingApi.getAppState().persist();
        return true;
      }
      return false;
    } catch (Exception ex) {
      log.warn("docking: failed persist phase={}", phase, ex);
      return false;
    }
  }

  private void ensureStartupDockLayout(
      ChatDockable chat, ServerTreeDockable serverTree, UserListDockable users) {
    if (!Docking.isDocked(chat)) {
      safeDockRoot(chat);
    }

    Dockable anchor = Docking.isDocked(chat) ? chat : firstDockedAnchor(serverTree, users);
    if (anchor == null) {
      log.warn(
          "docking: could not establish startup anchor (chat/server/users not docked); side docks skipped");
      return;
    }

    if (!Docking.isDocked(serverTree)) {
      safeDockRelative(serverTree, anchor, DockingRegion.WEST, 0.22);
    }
    if (!Docking.isDocked(users)) {
      // ModernDocking's "size" parameter maps to the JSplitPane divider *location* proportion.
      // For EAST docking, that proportion is measured from the LEFT edge, so 0.18 means:
      //   left=18% (chat) / right=82% (users)  -> comically huge user list.
      // We want the *users* panel to be ~18% of the width, so the divider should be at ~82%.
      safeDockRelative(users, anchor, DockingRegion.EAST, 0.82);
    }
  }

  private void safeDockRoot(Dockable dockable) {
    try {
      Docking.dock(dockable, this);
    } catch (Exception ex) {
      log.warn("docking: failed root-docking {}", safePersistentId(dockable), ex);
    }
  }

  private void safeDockRelative(
      Dockable dockable, Dockable anchor, DockingRegion region, double proportion) {
    if (dockable == null || anchor == null) return;
    if (!Docking.isDocked(anchor)) return;
    try {
      Docking.dock(dockable, anchor, region, proportion);
    } catch (Exception ex) {
      log.warn(
          "docking: failed relative-docking {} against {} region={} proportion={}",
          safePersistentId(dockable),
          safePersistentId(anchor),
          region,
          proportion,
          ex);
    }
  }

  private Dockable firstDockedAnchor(Dockable... candidates) {
    if (candidates == null) return null;
    for (Dockable candidate : candidates) {
      if (candidate != null && Docking.isDocked(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private static String safePersistentId(Dockable dockable) {
    if (dockable == null) return "null";
    try {
      return dockable.getPersistentID();
    } catch (Exception ignored) {
      return dockable.getClass().getSimpleName();
    }
  }

  private static int stableDockWidthSeed(java.awt.Component dockable, int fallbackPx) {
    int width = 0;
    if (dockable != null) {
      width = dockable.getWidth();
      if (width <= 0 && dockable.getParent() != null) {
        width = dockable.getParent().getWidth();
      }
      if (width <= 0) {
        width = dockable.getPreferredSize().width;
      }
    }
    if (width <= 0) {
      width = fallbackPx;
    }
    return Math.max(120, Math.min(1200, width));
  }
}

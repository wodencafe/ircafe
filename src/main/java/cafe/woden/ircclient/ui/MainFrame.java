package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ApplicationShutdownCoordinator;
import cafe.woden.ircclient.app.AppVersion;
import cafe.woden.ircclient.app.IrcMediator;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.docking.DockingTuner;
import cafe.woden.ircclient.ui.icons.AppIcons;
import cafe.woden.ircclient.ui.tray.TrayService;
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
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class MainFrame extends JFrame {

  private static final Logger log = LoggerFactory.getLogger(MainFrame.class);

  // Default side-dock sizes on first open. These are only applied once (best-effort)
  // and then preserved by the split-pane "lock" logic.
  private static final int DEFAULT_SERVER_DOCK_WIDTH_PX = 280;
  private static final int DEFAULT_USERS_DOCK_WIDTH_PX = 240;

  private final StatusBar statusBar;
  private final UiProperties uiProps;
  private final RuntimeConfigStore runtimeConfigStore;
  private final TrayService trayService;

  // Dockables (Spring beans)
  private final ServerTreeDockable serverTree;
  private final ChatDockable chat;
  private final UserListDockable users;

  public MainFrame(
      IrcMediator controller,
      AppMenuBar menuBar,
      UiProperties uiProps,
      RuntimeConfigStore runtimeConfigStore,
      TrayService trayService,
      ServerTreeDockable serverTree,
      ChatDockable chat,
      UserListDockable users,
      ChatDockManager chatDockManager,
      StatusBar statusBar,
      ApplicationShutdownCoordinator shutdownCoordinator) {
    super(AppVersion.windowTitle());
    this.uiProps = uiProps;
    this.runtimeConfigStore = runtimeConfigStore;
    this.trayService = trayService;
    this.serverTree = serverTree;
    this.chat = chat;
    this.users = users;
    this.statusBar = statusBar;

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
    Docking.initialize(this);
    // DockingUI.initialize(); // TODO: Investigate this.

    RootDockingPanel root = new RootDockingPanel(this);
    add(root, BorderLayout.CENTER);
    add(statusBar, BorderLayout.SOUTH);

    registerDockableIfNeeded(chat);
    registerDockableIfNeeded(serverTree);
    registerDockableIfNeeded(users);

    // First dock must be to an empty root container.
    Docking.dock(chat, this);

    Docking.dock(serverTree, chat, DockingRegion.WEST, 0.22);
    // ModernDocking's "size" parameter maps to the JSplitPane divider *location* proportion.
    // For EAST docking, that proportion is measured from the LEFT edge, so 0.18 means:
    //   left=18% (chat) / right=82% (users)  -> comically huge user list.
    // We want the *users* panel to be ~18% of the width, so the divider should be at ~82%.
    Docking.dock(users, chat, DockingRegion.EAST, 0.82);
    final java.util.concurrent.atomic.AtomicBoolean initialSideSizesApplied =
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
        };

    Runnable applyDockLocks =
        () -> {
          int serverPx = DEFAULT_SERVER_DOCK_WIDTH_PX;
          int usersPx = DEFAULT_USERS_DOCK_WIDTH_PX;
          if (uiProps != null && uiProps.layout() != null) {
            serverPx = uiProps.layout().serverDockWidthPx();
            usersPx = uiProps.layout().userDockWidthPx();
          }

          // On first open, ModernDocking can initially lay out side docks wider than desired.
          // Nudge them to a reasonable default once split panes exist, then lock those sizes.
          //
          int frameW = Math.max(1, getWidth());
          boolean sideDocksAreHuge =
              serverTree.getWidth() > (int) (frameW * 0.45)
                  || users.getWidth() > (int) (frameW * 0.45);
          boolean inStartupStabilization = System.nanoTime() < startupStabilizationDeadlineNanos;
          boolean shouldNudge =
              !initialSideSizesApplied.get() || (inStartupStabilization && sideDocksAreHuge);
          if (shouldNudge) {
            log.info(
                "dock-size: apply initial sizes? initialApplied={} huge={} targets(server={}, users={}) current(server={}, chat={}, users={}) frame={}x{}",
                initialSideSizesApplied.get(),
                sideDocksAreHuge,
                serverPx,
                usersPx,
                serverTree.getWidth(),
                chat.getWidth(),
                users.getWidth(),
                getWidth(),
                getHeight());
            boolean west = DockingTuner.applyInitialWestDockWidth(this, serverTree, serverPx);
            boolean east = DockingTuner.applyInitialEastDockWidth(this, users, usersPx);

            if (west && east) {
              initialSideSizesApplied.set(true);
            }

            log.info(
                "dock-size: init apply results west={} east={} -> now(initialApplied={}) current(server={}, chat={}, users={})",
                west,
                east,
                initialSideSizesApplied.get(),
                serverTree.getWidth(),
                chat.getWidth(),
                users.getWidth());
          }
          // Give horizontal growth to the chat transcript instead of the side docks.
          // Seed the WEST lock with the configured server dock width so it doesn't "capture" a
          // transient
          // (often too-wide) startup layout and then fight our initial sizing.
          DockingTuner.lockWestDockWidth(this, serverTree, serverPx);
          DockingTuner.lockEastDockWidth(this, users, usersPx);

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
            // Optional "close-to-tray": close button hides to tray (when supported/enabled).
            if (trayService != null
                && trayService.shouldCloseToTray()
                && !trayService.isExitRequested()) {
              setVisible(false);
              trayService.maybeShowCloseBalloon();
              return;
            }
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

  private void resetDockingRuntime(String phase) {
    try {
      Docking.uninitialize();
    } catch (Exception e) {
      log.debug("docking: uninitialize skipped during {} ({})", phase, e.toString());
    }
  }
}

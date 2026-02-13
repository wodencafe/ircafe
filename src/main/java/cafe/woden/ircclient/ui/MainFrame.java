package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.IrcMediator;
import cafe.woden.ircclient.app.AppVersion;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.docking.DockingTuner;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.app.RootDockingPanel;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

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

  // Dockables (Spring beans)
  private final ServerTreeDockable serverTree;
  private final ChatDockable chat;
  private final UserListDockable users;
  private final TerminalDockable terminal;

  public MainFrame(
      IrcMediator controller,
      AppMenuBar menuBar,
      UiProperties uiProps,
      ServerTreeDockable serverTree,
      ChatDockable chat,
      UserListDockable users,
      TerminalDockable terminal,
      ChatDockManager chatDockManager,
      StatusBar statusBar
  ) {
    super(AppVersion.appNameWithVersion());
    this.uiProps = uiProps;
    this.serverTree = serverTree;
    this.chat = chat;
    this.users = users;
    this.terminal = terminal;
    this.statusBar = statusBar;

    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setSize(1100, 700);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    setJMenuBar(menuBar);

    // Global find shortcut: Ctrl+F opens the chat transcript find bar.
    KeyStroke ctrlF = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlF, "chatFind");
    getRootPane().getActionMap().put("chatFind", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        chat.toggleFindBar();
      }
    });

    Docking.initialize(this);
    // DockingUI.initialize(); // TODO: Investigate this.

    RootDockingPanel root = new RootDockingPanel(this);
    add(root, BorderLayout.CENTER);
    add(statusBar, BorderLayout.SOUTH);

    Docking.registerDockable(chat);
    Docking.registerDockable(serverTree);
    Docking.registerDockable(users);
    Docking.registerDockable(terminal);

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

    Runnable applyDockLocks = () -> {
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
      boolean sideDocksAreHuge = serverTree.getWidth() > (int) (frameW * 0.45)
          || users.getWidth() > (int) (frameW * 0.45);
      boolean inStartupStabilization = System.nanoTime() < startupStabilizationDeadlineNanos;
      boolean shouldNudge = !initialSideSizesApplied.get() || (inStartupStabilization && sideDocksAreHuge);
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
            getHeight()
        );boolean west = DockingTuner.applyInitialWestDockWidth(this, serverTree, serverPx);
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
            users.getWidth()
        );}
      // Give horizontal growth to the chat transcript instead of the side docks.
      // Seed the WEST lock with the configured server dock width so it doesn't "capture" a transient
      // (often too-wide) startup layout and then fight our initial sizing.
      DockingTuner.lockWestDockWidth(this, serverTree, serverPx);
      DockingTuner.lockEastDockWidth(this, users, usersPx);
    };

    // Apply once after the initial docking layout.
    SwingUtilities.invokeLater(applyDockLocks);

    // Re-apply on resize/show (e.g., maximize) in case ModernDocking rebuilds split panes.
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        SwingUtilities.invokeLater(applyDockLocks);
      }

      @Override
      public void componentResized(ComponentEvent e) {
        SwingUtilities.invokeLater(applyDockLocks);
      }
    });

    // Note: the mediator self-starts via @PostConstruct when the UI beans are created.
  }
}

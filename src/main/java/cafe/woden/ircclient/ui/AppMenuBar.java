package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ApplicationShutdownCoordinator;
import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.settings.PreferencesDialog;
import cafe.woden.ircclient.ui.settings.ThemeSelectionDialog;
import cafe.woden.ircclient.ui.docking.DockingTuner;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import io.github.andrewauclair.moderndocking.Dockable;
import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.app.Docking;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class AppMenuBar extends JMenuBar {

  // Default proportions used when docking.
  //
  // ModernDocking interprets "proportion" as the share of the split given to the *newly docked* dockable.
  // We compute proportions from our configured px widths when possible (see proportionForSideDock).
  // These constants are only a fallback if the window isn't sized yet.
  private static final double DEFAULT_SERVER_DOCK_PROPORTION = 0.22;
  private static final double DEFAULT_USERS_DOCK_PROPORTION = 0.18;

  private static final int DEFAULT_SERVER_DOCK_WIDTH_PX = 280;
  private static final int DEFAULT_USERS_DOCK_WIDTH_PX = 240;
  private static final int DEFAULT_TERMINAL_DOCK_HEIGHT_PX = 220;

  private final UiProperties uiProps;
  private final ChatDockable chat;
  private final ServerTreeDockable serverTree;
  private final UserListDockable users;
  private final TerminalDockable terminal;

  public AppMenuBar(PreferencesDialog preferencesDialog,
                    NickColorOverridesDialog nickColorOverridesDialog,
                    IgnoreListDialog ignoreListDialog,
                    ThemeSelectionDialog themeSelectionDialog,
                    ServerDialogs serverDialogs,
                    UiProperties uiProps,
                    ChatDockable chat,
                    ServerTreeDockable serverTree,
                    UserListDockable users,
                    TerminalDockable terminal,
                    TargetCoordinator targetCoordinator,
                    ApplicationShutdownCoordinator shutdownCoordinator) {

    this.uiProps = uiProps;
    this.chat = chat;
    this.serverTree = serverTree;
    this.users = users;
    this.terminal = terminal;

    // File
    JMenu file = new JMenu("File");
    JMenuItem exit = new JMenuItem("Exit");
    exit.addActionListener(e -> shutdownCoordinator.shutdown());
    file.add(exit);

    // Edit (placeholder for future)
    JMenu edit = new JMenu("Edit");

    // Insert (placeholder for future)
    JMenu insert = new JMenu("Insert");

    // Settings
    JMenu settings = new JMenu("Settings");

    JMenuItem themeSelector = new JMenuItem("Theme Selector...");
    themeSelector.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      themeSelectionDialog.open(w);
    });
    settings.add(themeSelector);

    JMenuItem nickColors = new JMenuItem("Nick Colors...");
    nickColors.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      nickColorOverridesDialog.open(w);
    });

    JMenuItem prefs = new JMenuItem("Preferences...");
    prefs.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      preferencesDialog.open(w);
    });

    JMenuItem ignoreLists = new JMenuItem("Ignore Lists...");
    ignoreLists.addActionListener(e -> {
      if (ignoreListDialog == null) return;
      Window w = SwingUtilities.getWindowAncestor(this);
      TargetRef t = (targetCoordinator == null) ? null : targetCoordinator.getActiveTarget();
      String sid = (t != null && t.serverId() != null && !t.serverId().isBlank())
          ? t.serverId()
          : (targetCoordinator == null ? "default" : targetCoordinator.safeStatusTarget().serverId());
      ignoreListDialog.open(w, sid);
    });
    settings.addSeparator();
    settings.add(nickColors);
    settings.add(prefs);
    settings.add(ignoreLists);

    // Window (placeholder for future docking helpers)
    JMenu window = new JMenu("Window");

    JMenuItem reopenServersDock = new JMenuItem("Reopen Servers Dock");
    reopenServersDock.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    reopenServersDock.addActionListener(e -> ensureSideDockVisible(
        serverTree, DockingRegion.WEST));

    JMenuItem reopenUsersDock = new JMenuItem("Reopen Users Dock");
    reopenUsersDock.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_2,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    reopenUsersDock.addActionListener(e -> ensureSideDockVisible(
        users, DockingRegion.EAST));

    JMenuItem reopenTerminalDock = new JMenuItem("Open Terminal Dock");
    reopenTerminalDock.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_3,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    reopenTerminalDock.addActionListener(e -> ensureBottomDockVisible(
        terminal, DockingRegion.SOUTH));

    JMenuItem resetLayout = new JMenuItem("Reset Dock Layout");
    resetLayout.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    resetLayout.addActionListener(e -> resetDockLayout());

    window.add(reopenServersDock);
    window.add(reopenUsersDock);
    window.add(reopenTerminalDock);
    window.addSeparator();
    window.add(resetLayout);
    window.addSeparator();

    // Node actions come from the server tree controller.
    // Enabled/disabled state updates automatically based on the tree selection.
    window.add(new JMenuItem(serverTree.moveNodeUpAction()));
    window.add(new JMenuItem(serverTree.moveNodeDownAction()));
    window.addSeparator();
    window.add(new JMenuItem(serverTree.closeNodeAction()));

    // Servers
    JMenu servers = new JMenu("Servers");
    JMenuItem addServer = new JMenuItem("Add Server...");
    addServer.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      serverDialogs.openAddServer(w);
    });
    JMenuItem editServers = new JMenuItem("Edit Servers...");
    editServers.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      serverDialogs.openManageServers(w);
    });
    servers.add(addServer);
    servers.add(editServers);

    // Help
    JMenu help = new JMenu("Help");
    JMenuItem about = new JMenuItem("About");
    about.addActionListener(e -> javax.swing.JOptionPane.showMessageDialog(
        SwingUtilities.getWindowAncestor(this),
        "IRCafe\nA modern Java IRC client.",
        "About IRCafe",
        javax.swing.JOptionPane.INFORMATION_MESSAGE));
    help.add(about);

    add(file);
    add(servers);
    add(edit);
    add(insert);
    add(settings);
    add(window);
    add(help);
  }

  private void resetDockLayout() {
    Window root = SwingUtilities.getWindowAncestor(this);
    if (root == null) return;

    // Ensure the main chat dock exists, then restore the two side docks.
    if (isDetached(chat)) {
      dockSafe(chat, root);
    }
    ensureSideDockVisible(serverTree, DockingRegion.WEST);
    ensureSideDockVisible(users, DockingRegion.EAST);
  }

  private void ensureSideDockVisible(Dockable dockable, DockingRegion region) {
    Window root = SwingUtilities.getWindowAncestor(this);
    if (root == null) return;

    // If it's already visible somewhere (tabbed or floating), just bring it forward.
    if (!isDetached(dockable)) {
      bringToFront(dockable);
      return;
    }

    // Ensure the chat dock exists as an anchor.
    if (isDetached(chat)) {
      dockSafe(chat, root);
    }

    dockSafe(dockable, chat, region, proportionForSideDock(root, dockable, region));

    // After docking, apply our split-pane sizing/lock logic.
    SwingUtilities.invokeLater(() -> applySideDockLocks(root));
  }

  private void ensureBottomDockVisible(Dockable dockable, DockingRegion region) {
    Window root = SwingUtilities.getWindowAncestor(this);
    if (root == null) return;

    if (!isDetached(dockable)) {
      bringToFront(dockable);
      return;
    }

    if (isDetached(chat)) {
      dockSafe(chat, root);
    }

    dockSafe(dockable, chat, region, proportionForBottomDock(root));
  }

  private double proportionForBottomDock(Window root) {
    int base = Math.max(1, root.getHeight());
    if (base <= 1) {
      return 0.25;
    }
    double p = (double) DEFAULT_TERMINAL_DOCK_HEIGHT_PX / (double) base;
    return Math.max(0.12, Math.min(0.60, p));
  }

  private double proportionForSideDock(Window root, Dockable dockable, DockingRegion region) {
    int serverPx = DEFAULT_SERVER_DOCK_WIDTH_PX;
    int usersPx = DEFAULT_USERS_DOCK_WIDTH_PX;
    if (uiProps != null && uiProps.layout() != null) {
      serverPx = uiProps.layout().serverDockWidthPx();
      usersPx = uiProps.layout().userDockWidthPx();
    }

    int targetPx = (dockable == serverTree) ? serverPx : (dockable == users) ? usersPx : DEFAULT_USERS_DOCK_WIDTH_PX;

    int base = (region == DockingRegion.NORTH || region == DockingRegion.SOUTH)
        ? Math.max(1, root.getHeight())
        : Math.max(1, root.getWidth());

    // If the window isn't realized yet, fall back to sane proportions.
    if (base <= 1) {
      return (dockable == serverTree) ? DEFAULT_SERVER_DOCK_PROPORTION : DEFAULT_USERS_DOCK_PROPORTION;
    }

    double p = (double) targetPx / (double) base;
    // Keep side docks in a reasonable band; DockingTuner will enforce px sizes right after docking.
    return Math.max(0.10, Math.min(0.45, p));
  }

  private void dockSafe(Dockable dockable, Window root) {
    try {
      Docking.dock(dockable, root);
      return;
    } catch (Exception ignored) {
      // Fall through and try again after ensuring registration.
    }

    try {
      Docking.registerDockable(dockable);
    } catch (Exception ignored) {
    }
    Docking.dock(dockable, root);
  }

  private void dockSafe(Dockable dockable, Dockable anchor, DockingRegion region, double proportion) {
    try {
      Docking.dock(dockable, anchor, region, proportion);
      return;
    } catch (Exception ignored) {
      // Fall through and try again after ensuring registration.
    }

    try {
      Docking.registerDockable(dockable);
    } catch (Exception ignored) {
    }
    Docking.dock(dockable, anchor, region, proportion);
  }

  private void applySideDockLocks(Window root) {
    int serverPx = DEFAULT_SERVER_DOCK_WIDTH_PX;
    int usersPx = DEFAULT_USERS_DOCK_WIDTH_PX;
    if (uiProps != null && uiProps.layout() != null) {
      serverPx = uiProps.layout().serverDockWidthPx();
      usersPx = uiProps.layout().userDockWidthPx();
    }

    // Best-effort: nudge to our configured defaults, then lock the dividers.
    DockingTuner.applyInitialWestDockWidth(root, (java.awt.Component) serverTree, serverPx);
    DockingTuner.applyInitialEastDockWidth(root, (java.awt.Component) users, usersPx);
    DockingTuner.lockWestDockWidth(root, (java.awt.Component) serverTree, serverPx);
    DockingTuner.lockEastDockWidth(root, (java.awt.Component) users, usersPx);
  }

  private boolean isDetached(Dockable dockable) {
    if (!(dockable instanceof java.awt.Component c)) return true;
    return SwingUtilities.getWindowAncestor(c) == null;
  }

  private void bringToFront(Dockable dockable) {
    if (!(dockable instanceof java.awt.Component c)) return;
    Window w = SwingUtilities.getWindowAncestor(c);
    if (w != null) {
      w.toFront();
      w.requestFocus();
    }
    c.requestFocusInWindow();
  }
}

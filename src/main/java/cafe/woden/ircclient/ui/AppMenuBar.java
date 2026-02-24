package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ApplicationShutdownCoordinator;
import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.settings.PreferencesDialog;
import cafe.woden.ircclient.ui.settings.ThemeIdUtils;
import cafe.woden.ircclient.ui.settings.ThemeManager;
import cafe.woden.ircclient.ui.settings.ThemeSelectionDialog;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.docking.DockingTuner;
import cafe.woden.ircclient.ui.icons.AppIcons;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import io.github.andrewauclair.moderndocking.Dockable;
import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.app.Docking;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
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
  private static final String IRC_BOLD = String.valueOf((char) 0x02);
  private static final String IRC_COLOR = String.valueOf((char) 0x03);
  private static final String IRC_RESET = String.valueOf((char) 0x0F);
  private static final String IRC_REVERSE = String.valueOf((char) 0x16);
  private static final String IRC_ITALIC = String.valueOf((char) 0x1D);
  private static final String IRC_UNDERLINE = String.valueOf((char) 0x1F);
  private static final String[] IRC_COLOR_NAMES = new String[] {
      "White",
      "Black",
      "Navy",
      "Green",
      "Red",
      "Maroon",
      "Purple",
      "Orange",
      "Yellow",
      "Light Green",
      "Teal",
      "Light Cyan",
      "Light Blue",
      "Pink",
      "Gray",
      "Light Gray"
  };

  private final UiProperties uiProps;
  private final ChatDockable chat;
  private final ServerTreeDockable serverTree;
  private final UserListDockable users;
  private final TerminalDockable terminal;

  public AppMenuBar(PreferencesDialog preferencesDialog,
                    NickColorOverridesDialog nickColorOverridesDialog,
                    IgnoreListDialog ignoreListDialog,
                    ThemeSelectionDialog themeSelectionDialog,
                    ThemeManager themeManager,
                    UiSettingsBus settingsBus,
                    RuntimeConfigStore runtimeConfig,
                    ServerDialogs serverDialogs,
                    UiProperties uiProps,
                    ChatDockable chat,
                    ServerTreeDockable serverTree,
                    UserListDockable users,
                    TerminalDockable terminal,
                    ActiveInputRouter activeInputRouter,
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
    exit.setIcon(SvgIcons.action("exit", 16));
    exit.setDisabledIcon(SvgIcons.actionDisabled("exit", 16));
    exit.addActionListener(e -> shutdownCoordinator.shutdown());
    file.add(exit);

    int menuMask = menuShortcutMask();

    // Edit
    JMenu edit = new JMenu("Edit");
    JMenuItem undo = new JMenuItem("Undo");
    undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask));
    undo.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::undo)) {
        beep();
      }
    });

    JMenuItem redo = new JMenuItem("Redo");
    redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | InputEvent.SHIFT_DOWN_MASK));
    redo.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::redo)) {
        beep();
      }
    });

    JMenuItem cut = new JMenuItem("Cut");
    cut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask));
    cut.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::cutSelection)) {
        beep();
      }
    });

    JMenuItem copy = new JMenuItem("Copy");
    copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask));
    copy.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::copySelection)) {
        beep();
      }
    });

    JMenuItem paste = new JMenuItem("Paste");
    paste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
    paste.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::pasteFromClipboard)) {
        beep();
      }
    });

    JMenuItem delete = new JMenuItem("Delete");
    delete.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
    delete.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::deleteForward)) {
        beep();
      }
    });

    JMenuItem selectAll = new JMenuItem("Select All");
    selectAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask));
    selectAll.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::selectAllInput)) {
        beep();
      }
    });

    JMenuItem clearInput = new JMenuItem("Clear Input");
    clearInput.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));
    clearInput.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::clearInput)) {
        beep();
      }
    });

    JMenuItem findInCurrentBuffer = new JMenuItem("Find in Current Buffer");
    findInCurrentBuffer.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK));
    findInCurrentBuffer.addActionListener(e -> chat.openFindBar());

    JMenuItem findNext = new JMenuItem("Find Next");
    findNext.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
    findNext.addActionListener(e -> chat.findNextInTranscript());

    JMenuItem findPrevious = new JMenuItem("Find Previous");
    findPrevious.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK));
    findPrevious.addActionListener(e -> chat.findPreviousInTranscript());

    JMenu commandHistory = new JMenu("Command History");
    JMenuItem commandHistoryPrev = new JMenuItem("Previous");
    commandHistoryPrev.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
    commandHistoryPrev.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::historyPrev)) {
        beep();
      }
    });

    JMenuItem commandHistoryNext = new JMenuItem("Next");
    commandHistoryNext.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
    commandHistoryNext.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::historyNext)) {
        beep();
      }
    });

    JMenuItem commandHistoryClear = new JMenuItem("Clear");
    commandHistoryClear.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::clearCommandHistory)) {
        beep();
      }
    });

    commandHistory.add(commandHistoryPrev);
    commandHistory.add(commandHistoryNext);
    commandHistory.addSeparator();
    commandHistory.add(commandHistoryClear);

    edit.add(undo);
    edit.add(redo);
    edit.addSeparator();
    edit.add(cut);
    edit.add(copy);
    edit.add(paste);
    edit.add(delete);
    edit.add(selectAll);
    edit.addSeparator();
    edit.add(clearInput);
    edit.addSeparator();
    edit.add(findInCurrentBuffer);
    edit.add(findNext);
    edit.add(findPrevious);
    edit.addSeparator();
    edit.add(commandHistory);
    edit.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        MessageInputPanel panel = activeInputPanel(activeInputRouter);
        boolean hasInput = panel != null;

        undo.setEnabled(hasInput && panel.canUndo());
        redo.setEnabled(hasInput && panel.canRedo());
        cut.setEnabled(hasInput && panel.canCut());
        copy.setEnabled(hasInput && panel.canCopy());
        paste.setEnabled(hasInput && panel.canPaste());
        delete.setEnabled(hasInput && panel.canDeleteForward());
        selectAll.setEnabled(hasInput && panel.canSelectAllInput());
        clearInput.setEnabled(hasInput && panel.canClearInput());

        commandHistory.setEnabled(hasInput && panel.isHistoryMenuEnabled());
        commandHistoryPrev.setEnabled(hasInput && panel.canHistoryPrev());
        commandHistoryNext.setEnabled(hasInput && panel.canHistoryNext());
        commandHistoryClear.setEnabled(hasInput && panel.canClearCommandHistory());

        findInCurrentBuffer.setEnabled(chat != null);
        findNext.setEnabled(chat != null);
        findPrevious.setEnabled(chat != null);
      }

      @Override
      public void menuDeselected(MenuEvent e) {
      }

      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });

    // Insert
    JMenu insert = new JMenu("Insert");
    JMenu formatting = new JMenu("Formatting");
    JMenu nickTarget = new JMenu("Nick/Target");

    JMenuItem insertBold = new JMenuItem("Bold");
    insertBold.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, panel -> panel.insertPrefixOrWrapSelection(IRC_BOLD, IRC_BOLD))) {
        beep();
      }
    });
    JMenuItem insertItalic = new JMenuItem("Italic");
    insertItalic.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, panel -> panel.insertPrefixOrWrapSelection(IRC_ITALIC, IRC_ITALIC))) {
        beep();
      }
    });
    JMenuItem insertUnderline = new JMenuItem("Underline");
    insertUnderline.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, panel -> panel.insertPrefixOrWrapSelection(IRC_UNDERLINE, IRC_UNDERLINE))) {
        beep();
      }
    });
    JMenuItem insertReverse = new JMenuItem("Reverse");
    insertReverse.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, panel -> panel.insertPrefixOrWrapSelection(IRC_REVERSE, IRC_REVERSE))) {
        beep();
      }
    });
    JMenuItem insertColor = new JMenuItem("Color...");
    insertColor.addActionListener(e -> {
      MessageInputPanel panel = activeInputPanel(activeInputRouter);
      if (panel == null || !panel.isInputEditable()) {
        beep();
        return;
      }
      IrcColorSelection color = promptIrcColorSelection();
      if (color == null) return;

      boolean ok;
      if (color.foreground() == null) {
        ok = panel.insertTextAtCaret(IRC_COLOR);
      } else {
        ok = panel.insertPrefixOrWrapSelection(buildIrcColorPrefix(color), IRC_COLOR);
      }
      if (!ok) beep();
    });
    JMenuItem insertResetFormatting = new JMenuItem("Reset Formatting");
    insertResetFormatting.addActionListener(e -> {
      if (!insertIntoActiveInput(activeInputRouter, panel -> panel.insertTextAtCaret(IRC_RESET))) {
        beep();
      }
    });

    formatting.add(insertBold);
    formatting.add(insertItalic);
    formatting.add(insertUnderline);
    formatting.add(insertReverse);
    formatting.addSeparator();
    formatting.add(insertColor);
    formatting.add(insertResetFormatting);

    JMenuItem insertSelectedNick = new JMenuItem("Insert Selected Nick");
    insertSelectedNick.addActionListener(e -> {
      String nick = resolveSelectedNick(users, targetCoordinator);
      if (nick.isEmpty() || !insertIntoActiveInput(activeInputRouter, panel -> panel.insertTextAtCaret(nick))) {
        beep();
      }
    });
    JMenuItem insertCurrentChannel = new JMenuItem("Insert Current Channel");
    insertCurrentChannel.addActionListener(e -> {
      String channel = resolveCurrentChannel(targetCoordinator);
      if (channel.isEmpty() || !insertIntoActiveInput(activeInputRouter, panel -> panel.insertTextAtCaret(channel))) {
        beep();
      }
    });
    JMenuItem insertCurrentServer = new JMenuItem("Insert Current Server");
    insertCurrentServer.addActionListener(e -> {
      String sid = resolveCurrentServerId(targetCoordinator);
      if (sid.isEmpty() || !insertIntoActiveInput(activeInputRouter, panel -> panel.insertTextAtCaret(sid))) {
        beep();
      }
    });

    nickTarget.add(insertSelectedNick);
    nickTarget.add(insertCurrentChannel);
    nickTarget.add(insertCurrentServer);

    insert.add(formatting);
    insert.add(nickTarget);
    insert.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        MessageInputPanel panel = activeInputPanel(activeInputRouter);
        boolean editable = panel != null && panel.isInputEditable();
        formatting.setEnabled(editable);
        nickTarget.setEnabled(editable);
        insertSelectedNick.setEnabled(editable && !resolveSelectedNick(users, targetCoordinator).isEmpty());
        insertCurrentChannel.setEnabled(editable && !resolveCurrentChannel(targetCoordinator).isEmpty());
        insertCurrentServer.setEnabled(editable && !resolveCurrentServerId(targetCoordinator).isEmpty());
      }

      @Override
      public void menuDeselected(MenuEvent e) {
      }

      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });

    // Settings
    JMenu settings = new JMenu("Settings");

    JMenu themeMenu = new JMenu("Theme");
    ButtonGroup themeGroup = new ButtonGroup();
    Map<String, JRadioButtonMenuItem> themeItems = new LinkedHashMap<>();

    for (ThemeManager.ThemeOption opt : themeManager.featuredThemes()) {
      if (opt == null) continue;
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(opt.label());
      item.addActionListener(e -> applyThemeQuick(opt.id(), themeManager, settingsBus, runtimeConfig));
      themeGroup.add(item);
      themeMenu.add(item);
      themeItems.put(opt.id(), item);
    }

    themeMenu.addSeparator();
    JMenuItem themeSelector = new JMenuItem("More Themes...");
    themeSelector.setIcon(SvgIcons.action("theme", 16));
    themeSelector.setDisabledIcon(SvgIcons.actionDisabled("theme", 16));
    themeSelector.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      themeSelectionDialog.open(w);
    });
    themeMenu.add(themeSelector);

    Runnable syncThemeChecks = () -> {
      String currentTheme = ThemeIdUtils.normalizeThemeId(settingsBus.get() != null ? settingsBus.get().theme() : null);
      themeItems.forEach((id, mi) -> mi.setSelected(ThemeIdUtils.normalizeThemeId(id).equals(currentTheme)));
    };
    syncThemeChecks.run();
    PropertyChangeListener themeListener = evt -> {
      if (UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) {
        syncThemeChecks.run();
      }
    };
    settingsBus.addListener(themeListener);

    settings.add(themeMenu);

    JMenuItem nickColors = new JMenuItem("Nick Colors...");
    nickColors.setIcon(SvgIcons.action("palette", 16));
    nickColors.setDisabledIcon(SvgIcons.actionDisabled("palette", 16));
    nickColors.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      nickColorOverridesDialog.open(w);
    });

    JMenuItem prefs = new JMenuItem("Preferences...");
    prefs.setIcon(SvgIcons.action("settings", 16));
    prefs.setDisabledIcon(SvgIcons.actionDisabled("settings", 16));
    prefs.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      preferencesDialog.open(w);
    });

    JMenuItem ignoreLists = new JMenuItem("Ignore Lists...");
    ignoreLists.setIcon(SvgIcons.action("ban", 16));
    ignoreLists.setDisabledIcon(SvgIcons.actionDisabled("ban", 16));
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
    reopenServersDock.setIcon(SvgIcons.action("dock-left", 16));
    reopenServersDock.setDisabledIcon(SvgIcons.actionDisabled("dock-left", 16));
    reopenServersDock.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    reopenServersDock.addActionListener(e -> ensureSideDockVisible(
        serverTree, DockingRegion.WEST));

    JMenuItem reopenUsersDock = new JMenuItem("Reopen Users Dock");
    reopenUsersDock.setIcon(SvgIcons.action("dock-right", 16));
    reopenUsersDock.setDisabledIcon(SvgIcons.actionDisabled("dock-right", 16));
    reopenUsersDock.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_2,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    reopenUsersDock.addActionListener(e -> ensureSideDockVisible(
        users, DockingRegion.EAST));

    JMenuItem reopenTerminalDock = new JMenuItem("Open Terminal Dock");
    reopenTerminalDock.setIcon(SvgIcons.action("dock-bottom", 16));
    reopenTerminalDock.setDisabledIcon(SvgIcons.actionDisabled("dock-bottom", 16));
    reopenTerminalDock.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_3,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    reopenTerminalDock.addActionListener(e -> ensureBottomDockVisible(
        terminal, DockingRegion.SOUTH));

    JMenuItem resetLayout = new JMenuItem("Reset Dock Layout");
    resetLayout.setIcon(SvgIcons.action("refresh", 16));
    resetLayout.setDisabledIcon(SvgIcons.actionDisabled("refresh", 16));
    resetLayout.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    resetLayout.addActionListener(e -> resetDockLayout());

    JCheckBoxMenuItem showChannelListNodes = new JCheckBoxMenuItem("Show Channel List Nodes");
    showChannelListNodes.setSelected(serverTree.isChannelListNodesVisible());
    showChannelListNodes.addActionListener(e ->
        serverTree.setChannelListNodesVisible(showChannelListNodes.isSelected()));
    serverTree.addPropertyChangeListener(ServerTreeDockable.PROP_CHANNEL_LIST_NODES_VISIBLE, evt ->
        showChannelListNodes.setSelected(Boolean.TRUE.equals(evt.getNewValue())));

    JCheckBoxMenuItem showDccNodes = new JCheckBoxMenuItem("Show DCC Transfers Nodes");
    showDccNodes.setSelected(serverTree.isDccTransfersNodesVisible());
    showDccNodes.addActionListener(e ->
        serverTree.setDccTransfersNodesVisible(showDccNodes.isSelected()));
    serverTree.addPropertyChangeListener(ServerTreeDockable.PROP_DCC_TRANSFERS_NODES_VISIBLE, evt ->
        showDccNodes.setSelected(Boolean.TRUE.equals(evt.getNewValue())));

    JCheckBoxMenuItem showLogViewerNodes = new JCheckBoxMenuItem("Show Log Viewer Nodes");
    showLogViewerNodes.setSelected(serverTree.isLogViewerNodesVisible());
    showLogViewerNodes.addActionListener(e ->
        serverTree.setLogViewerNodesVisible(showLogViewerNodes.isSelected()));
    serverTree.addPropertyChangeListener(ServerTreeDockable.PROP_LOG_VIEWER_NODES_VISIBLE, evt ->
        showLogViewerNodes.setSelected(Boolean.TRUE.equals(evt.getNewValue())));

    JCheckBoxMenuItem showNotificationsNodes = new JCheckBoxMenuItem("Show Notifications Nodes");
    showNotificationsNodes.setSelected(serverTree.isNotificationsNodesVisible());
    showNotificationsNodes.addActionListener(e ->
        serverTree.setNotificationsNodesVisible(showNotificationsNodes.isSelected()));
    serverTree.addPropertyChangeListener(ServerTreeDockable.PROP_NOTIFICATIONS_NODES_VISIBLE, evt ->
        showNotificationsNodes.setSelected(Boolean.TRUE.equals(evt.getNewValue())));

    JCheckBoxMenuItem showMonitorNodes = new JCheckBoxMenuItem("Show Monitor Nodes");
    showMonitorNodes.setSelected(serverTree.isMonitorNodesVisible());
    showMonitorNodes.addActionListener(e ->
        serverTree.setMonitorNodesVisible(showMonitorNodes.isSelected()));
    serverTree.addPropertyChangeListener(ServerTreeDockable.PROP_MONITOR_NODES_VISIBLE, evt ->
        showMonitorNodes.setSelected(Boolean.TRUE.equals(evt.getNewValue())));

    JCheckBoxMenuItem showInterceptorsNodes = new JCheckBoxMenuItem("Show Interceptors Nodes");
    showInterceptorsNodes.setSelected(serverTree.isInterceptorsNodesVisible());
    showInterceptorsNodes.addActionListener(e ->
        serverTree.setInterceptorsNodesVisible(showInterceptorsNodes.isSelected()));
    serverTree.addPropertyChangeListener(ServerTreeDockable.PROP_INTERCEPTORS_NODES_VISIBLE, evt ->
        showInterceptorsNodes.setSelected(Boolean.TRUE.equals(evt.getNewValue())));

    JCheckBoxMenuItem showApplicationRoot = new JCheckBoxMenuItem("Show Application Root");
    showApplicationRoot.setSelected(serverTree.isApplicationRootVisible());
    showApplicationRoot.addActionListener(e ->
        serverTree.setApplicationRootVisible(showApplicationRoot.isSelected()));
    serverTree.addPropertyChangeListener(ServerTreeDockable.PROP_APPLICATION_ROOT_VISIBLE, evt ->
        showApplicationRoot.setSelected(Boolean.TRUE.equals(evt.getNewValue())));

    JMenuItem openSelectedNodeDock = new JMenuItem("Open Selected Node in Chat Dock");
    openSelectedNodeDock.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    openSelectedNodeDock.addActionListener(e -> serverTree.openSelectedNodeInChatDock());

    window.add(reopenServersDock);
    window.add(reopenUsersDock);
    window.add(reopenTerminalDock);
    window.addSeparator();
    window.add(resetLayout);
    window.addSeparator();
    window.add(showChannelListNodes);
    window.add(showDccNodes);
    window.add(showLogViewerNodes);
    window.add(showNotificationsNodes);
    window.add(showMonitorNodes);
    window.add(showInterceptorsNodes);
    window.add(showApplicationRoot);
    window.addSeparator();
    window.add(openSelectedNodeDock);
    window.addSeparator();

    // Node actions come from the server tree controller.
    // Enabled/disabled state updates automatically based on the tree selection.
    JMenuItem moveNodeUp = new JMenuItem(serverTree.moveNodeUpAction());
    moveNodeUp.setIcon(SvgIcons.action("arrow-up", 16));
    moveNodeUp.setDisabledIcon(SvgIcons.actionDisabled("arrow-up", 16));
    moveNodeUp.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    JMenuItem moveNodeDown = new JMenuItem(serverTree.moveNodeDownAction());
    moveNodeDown.setIcon(SvgIcons.action("arrow-down", 16));
    moveNodeDown.setDisabledIcon(SvgIcons.actionDisabled("arrow-down", 16));
    moveNodeDown.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    JMenuItem closeNode = new JMenuItem(serverTree.closeNodeAction());
    closeNode.setIcon(SvgIcons.action("close", 16));
    closeNode.setDisabledIcon(SvgIcons.actionDisabled("close", 16));
    closeNode.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));
    window.add(moveNodeUp);
    window.add(moveNodeDown);
    window.addSeparator();
    window.add(closeNode);

    // Servers
    JMenu servers = new JMenu("Servers");
    JMenuItem addServer = new JMenuItem("Add Server...");
    addServer.setIcon(SvgIcons.action("plus", 16));
    addServer.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    addServer.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      serverDialogs.openAddServer(w);
    });
    JMenuItem editServers = new JMenuItem("Edit Servers...");
    editServers.setIcon(SvgIcons.action("edit", 16));
    editServers.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
    editServers.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      serverDialogs.openManageServers(w);
    });
    servers.add(addServer);
    servers.add(editServers);

    // Help
    JMenu help = new JMenu("Help");
    JMenuItem about = new JMenuItem("About");
    about.setIcon(SvgIcons.action("info", 16));
    about.setDisabledIcon(SvgIcons.actionDisabled("info", 16));
    about.addActionListener(e -> javax.swing.JOptionPane.showMessageDialog(
        SwingUtilities.getWindowAncestor(this),
        "IRCafe\nA modern Java IRC client.",
        "About IRCafe",
        javax.swing.JOptionPane.INFORMATION_MESSAGE,
        AppIcons.aboutIcon()));
    help.add(about);

    add(file);
    add(servers);
    add(edit);
    add(insert);
    add(settings);
    add(window);
    add(help);

    installMenuPopupThemeSync(file, servers, edit, insert, settings, window, help);
  }

  private static void installMenuPopupThemeSync(JMenu... menus) {
    if (menus == null || menus.length == 0) return;
    for (JMenu menu : menus) {
      if (menu == null) continue;
      menu.addMenuListener(new MenuListener() {
        @Override
        public void menuSelected(MenuEvent e) {
          try {
            PopupMenuThemeSupport.prepareForDisplay(menu.getPopupMenu());
          } catch (Exception ignored) {
          }
        }

        @Override
        public void menuDeselected(MenuEvent e) {}

        @Override
        public void menuCanceled(MenuEvent e) {}
      });
    }
  }

  private void resetDockLayout() {
    Window root = SwingUtilities.getWindowAncestor(this);
    if (root == null) return;

    // Reset to startup-style layout:
    // chat in the center, servers on the left, users on the right.
    restoreStartupLayout(root);
  }

  private void restoreStartupLayout(Window root) {
    if (root == null) return;

    // First dock must be chat-to-root (same anchor style as startup).
    dockSafe(chat, root);

    try {
      Docking.display(chat);
    } catch (Exception ignored) {
      try {
        Docking.display(chat);
      } catch (Exception ignoredAgain) {
      }
    }

    // Force a fresh side-dock arrangement so stale split geometry doesn't leak into reset.
    try {
      if (Docking.isDocked(serverTree)) {
        Docking.undock(serverTree);
      }
    } catch (Exception ignored) {
    }
    try {
      if (Docking.isDocked(users)) {
        Docking.undock(users);
      }
    } catch (Exception ignored) {
    }

    // Mirror startup proportions from MainFrame:
    // west=0.22, east=0.82 (east is divider location, not width share).
    dockSafe(serverTree, chat, DockingRegion.WEST, DEFAULT_SERVER_DOCK_PROPORTION);
    dockSafe(users, chat, DockingRegion.EAST, 1.0 - DEFAULT_USERS_DOCK_PROPORTION);
    try {
      Docking.display(serverTree);
      Docking.display(users);
    } catch (Exception ignored) {
    }

    // Re-apply split-pane sizing/locks (px-based) after re-docking.
    // Run a short stabilization loop because ModernDocking may rebuild split panes over several EDT ticks.
    SwingUtilities.invokeLater(() -> applySideDockLocksWithStabilization(root));
    bringToFront(chat);
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

  private boolean applySideDockLocks(Window root) {
    int serverPx = DEFAULT_SERVER_DOCK_WIDTH_PX;
    int usersPx = DEFAULT_USERS_DOCK_WIDTH_PX;
    if (uiProps != null && uiProps.layout() != null) {
      serverPx = uiProps.layout().serverDockWidthPx();
      usersPx = uiProps.layout().userDockWidthPx();
    }

    // Best-effort: nudge to our configured defaults, then lock the dividers.
    boolean west = DockingTuner.applyInitialWestDockWidth(root, (java.awt.Component) serverTree, serverPx);
    boolean east = DockingTuner.applyInitialEastDockWidth(root, (java.awt.Component) users, usersPx);
    DockingTuner.lockWestDockWidth(root, (java.awt.Component) serverTree, serverPx);
    DockingTuner.lockEastDockWidth(root, (java.awt.Component) users, usersPx);
    return west && east;
  }

  private void applySideDockLocksWithStabilization(Window root) {
    if (root == null) return;

    boolean done = applySideDockLocks(root);
    if (done) return;

    final int[] passes = new int[] { 0 };
    javax.swing.Timer settle = new javax.swing.Timer(110, null);
    settle.addActionListener(e -> {
      passes[0]++;
      boolean stable = applySideDockLocks(root);
      if (stable || passes[0] >= 10) {
        settle.stop();
      }
    });
    settle.start();
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

  private MessageInputPanel activeInputPanel(ActiveInputRouter activeInputRouter) {
    if (activeInputRouter == null) return null;
    return activeInputRouter.active();
  }

  private static boolean insertIntoActiveInput(
      ActiveInputRouter activeInputRouter,
      java.util.function.Function<MessageInputPanel, Boolean> operation) {
    if (activeInputRouter == null || operation == null) return false;
    MessageInputPanel panel = activeInputRouter.active();
    if (panel == null) return false;
    try {
      return Boolean.TRUE.equals(operation.apply(panel));
    } catch (Exception ignored) {
      return false;
    }
  }

  private static String resolveSelectedNick(UserListDockable users, TargetCoordinator targetCoordinator) {
    String nick = users == null ? "" : Objects.toString(users.selectedNick(), "").trim();
    if (!nick.isEmpty()) return nick;

    TargetRef at = targetCoordinator == null ? null : targetCoordinator.getActiveTarget();
    if (at != null && !at.isStatus() && !at.isUiOnly() && !at.isChannel()) {
      return Objects.toString(at.target(), "").trim();
    }
    return "";
  }

  private static String resolveCurrentChannel(TargetCoordinator targetCoordinator) {
    TargetRef at = targetCoordinator == null ? null : targetCoordinator.getActiveTarget();
    if (at == null || !at.isChannel()) return "";
    return Objects.toString(at.target(), "").trim();
  }

  private static String resolveCurrentServerId(TargetCoordinator targetCoordinator) {
    TargetRef at = targetCoordinator == null ? null : targetCoordinator.getActiveTarget();
    String sid = at == null ? "" : Objects.toString(at.serverId(), "").trim();
    if (!sid.isEmpty()) return sid;

    if (targetCoordinator != null) {
      TargetRef status = targetCoordinator.safeStatusTarget();
      if (status != null) sid = Objects.toString(status.serverId(), "").trim();
    }
    return sid;
  }

  private IrcColorSelection promptIrcColorSelection() {
    Window owner = SwingUtilities.getWindowAncestor(this);

    String[] fgOptions = new String[IRC_COLOR_NAMES.length + 1];
    fgOptions[0] = "(Clear Colors)";
    for (int i = 0; i < IRC_COLOR_NAMES.length; i++) {
      fgOptions[i + 1] = formatIrcColorOption(i);
    }

    String[] bgOptions = new String[IRC_COLOR_NAMES.length + 1];
    bgOptions[0] = "(No Background)";
    for (int i = 0; i < IRC_COLOR_NAMES.length; i++) {
      bgOptions[i + 1] = formatIrcColorOption(i);
    }

    JComboBox<String> fgCombo = new JComboBox<>(fgOptions);
    JComboBox<String> bgCombo = new JComboBox<>(bgOptions);
    fgCombo.setSelectedIndex(4 + 1); // red default
    bgCombo.setSelectedIndex(0);

    java.util.function.Consumer<Boolean> setBgEnabled = enabled -> {
      bgCombo.setEnabled(Boolean.TRUE.equals(enabled));
      if (!Boolean.TRUE.equals(enabled)) {
        bgCombo.setSelectedIndex(0);
      }
    };
    setBgEnabled.accept(true);
    fgCombo.addActionListener(e -> setBgEnabled.accept(fgCombo.getSelectedIndex() > 0));

    JPanel panel = new JPanel(new java.awt.GridLayout(0, 2, 8, 6));
    panel.add(new JLabel("Foreground:"));
    panel.add(fgCombo);
    panel.add(new JLabel("Background:"));
    panel.add(bgCombo);

    int result = JOptionPane.showConfirmDialog(
        owner != null ? owner : this,
        panel,
        "Insert IRC Color",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE);
    if (result != JOptionPane.OK_OPTION) return null;

    Integer fg = fgCombo.getSelectedIndex() <= 0 ? null : (fgCombo.getSelectedIndex() - 1);
    Integer bg = bgCombo.getSelectedIndex() <= 0 ? null : (bgCombo.getSelectedIndex() - 1);
    if (fg == null) bg = null;
    return new IrcColorSelection(fg, bg);
  }

  private static String buildIrcColorPrefix(IrcColorSelection color) {
    if (color == null || color.foreground() == null) return IRC_COLOR;
    StringBuilder sb = new StringBuilder(8);
    sb.append(IRC_COLOR);
    sb.append(String.format(Locale.ROOT, "%02d", color.foreground()));
    if (color.background() != null) {
      sb.append(',').append(String.format(Locale.ROOT, "%02d", color.background()));
    }
    return sb.toString();
  }

  private static String formatIrcColorOption(int idx) {
    if (idx < 0 || idx >= IRC_COLOR_NAMES.length) return "";
    return String.format(Locale.ROOT, "%02d %s", idx, IRC_COLOR_NAMES[idx]);
  }

  private static int menuShortcutMask() {
    try {
      return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    } catch (Exception ignored) {
      return InputEvent.CTRL_DOWN_MASK;
    }
  }

  private static void beep() {
    try {
      Toolkit.getDefaultToolkit().beep();
    } catch (Exception ignored) {
    }
  }

  private record IrcColorSelection(Integer foreground, Integer background) {}

  private static void applyThemeQuick(String themeId,
                                     ThemeManager themeManager,
                                     UiSettingsBus settingsBus,
                                     RuntimeConfigStore runtimeConfig) {
    String next = ThemeIdUtils.normalizeThemeId(themeId);
    UiSettings cur = settingsBus != null ? settingsBus.get() : null;
    if (cur == null) return;

    if (!ThemeIdUtils.normalizeThemeId(cur.theme()).equals(next)) {
      UiSettings updated = cur.withTheme(next);
      settingsBus.set(updated);
      if (runtimeConfig != null) {
        runtimeConfig.rememberUiSettings(updated.theme(), updated.chatFontFamily(), updated.chatFontSize());
      }
      if (themeManager != null) {
        themeManager.applyTheme(next);
      }
    }
  }

}

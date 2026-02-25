package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.ApplicationShutdownCoordinator;
import cafe.woden.ircclient.app.RuntimeJfrService;
import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.ui.docking.DockingTuner;
import cafe.woden.ircclient.ui.icons.AppIcons;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.settings.MemoryUsageDisplayMode;
import cafe.woden.ircclient.ui.settings.PreferencesDialog;
import cafe.woden.ircclient.ui.settings.ThemeIdUtils;
import cafe.woden.ircclient.ui.settings.ThemeManager;
import cafe.woden.ircclient.ui.settings.ThemeSelectionDialog;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import io.github.andrewauclair.moderndocking.Dockable;
import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.app.Docking;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import org.springframework.context.annotation.Lazy;

@org.springframework.stereotype.Component
@Lazy
public class AppMenuBar extends JMenuBar {
  // Default proportions used when docking.
  //
  // ModernDocking interprets "proportion" as the share of the split given to the *newly docked*
  // dockable.
  // We compute proportions from our configured px widths when possible (see proportionForSideDock).
  // These constants are only a fallback if the window isn't sized yet.
  private static final double DEFAULT_SERVER_DOCK_PROPORTION = 0.22;
  private static final double DEFAULT_USERS_DOCK_PROPORTION = 0.18;
  private static final int MEMORY_REFRESH_MS = 1000;
  private static final long MEMORY_WARNING_COOLDOWN_MS = 120_000L;
  private static final int MOON_ICON_SIZE = 16;
  private static final long MIB = 1024L * 1024L;

  private static final int DEFAULT_SERVER_DOCK_WIDTH_PX = 280;
  private static final int DEFAULT_USERS_DOCK_WIDTH_PX = 240;
  private static final String IRC_BOLD = String.valueOf((char) 0x02);
  private static final String IRC_COLOR = String.valueOf((char) 0x03);
  private static final String IRC_RESET = String.valueOf((char) 0x0F);
  private static final String IRC_REVERSE = String.valueOf((char) 0x16);
  private static final String IRC_ITALIC = String.valueOf((char) 0x1D);
  private static final String IRC_UNDERLINE = String.valueOf((char) 0x1F);
  private static final String[] IRC_COLOR_NAMES =
      new String[] {
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
  private final UiSettingsBus settingsBus;
  private final RuntimeConfigStore runtimeConfig;
  private final TrayNotificationService trayNotificationService;
  private final PushyNotificationService pushyNotificationService;
  private final NotificationSoundService notificationSoundService;
  private final RuntimeJfrService runtimeJfrService;
  private final ChatDockable chat;
  private final ServerTreeDockable serverTree;
  private final UserListDockable users;
  private final JButton memoryButton = new JButton();
  private final JButton memoryMoonButton = new JButton();
  private final JProgressBar memoryIndicator = new JProgressBar(0, 100);
  private final JPanel memoryWidget = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
  private final JPopupMenu memoryModePopup = new JPopupMenu();
  private final Timer memoryTimer = new Timer(MEMORY_REFRESH_MS, e -> refreshMemoryUsage());
  private MemoryUsageDisplayMode memoryUsageDisplayMode = MemoryUsageDisplayMode.LONG;
  private final Border memoryButtonDefaultBorder;
  private final Insets memoryButtonDefaultMargin;
  private final boolean memoryButtonDefaultContentAreaFilled;
  private final boolean memoryButtonDefaultOpaque;
  private JDialog memoryDialog;
  private JProgressBar memoryDialogGauge;
  private MemoryDialGauge memoryDialogDialGauge;
  private JLabel memoryDialogDetails;
  private Popup warningTooltipPopup;
  private Timer warningTooltipHideTimer;
  private long lastWarningAtMs;
  private boolean warningThresholdActive;

  public AppMenuBar(
      PreferencesDialog preferencesDialog,
      NickColorOverridesDialog nickColorOverridesDialog,
      IgnoreListDialog ignoreListDialog,
      ThemeSelectionDialog themeSelectionDialog,
      ThemeManager themeManager,
      UiSettingsBus settingsBus,
      RuntimeConfigStore runtimeConfig,
      TrayNotificationService trayNotificationService,
      PushyNotificationService pushyNotificationService,
      NotificationSoundService notificationSoundService,
      RuntimeJfrService runtimeJfrService,
      ServerDialogs serverDialogs,
      UiProperties uiProps,
      ChatDockable chat,
      ServerTreeDockable serverTree,
      UserListDockable users,
      ActiveInputRouter activeInputRouter,
      ActiveTargetPort targetCoordinator,
      ApplicationShutdownCoordinator shutdownCoordinator) {

    this.uiProps = uiProps;
    this.settingsBus = settingsBus;
    this.runtimeConfig = runtimeConfig;
    this.trayNotificationService = trayNotificationService;
    this.pushyNotificationService = pushyNotificationService;
    this.notificationSoundService = notificationSoundService;
    this.runtimeJfrService = runtimeJfrService;
    this.chat = chat;
    this.serverTree = serverTree;
    this.users = users;

    memoryButton.setFocusable(false);
    memoryButton.setMargin(new Insets(1, 6, 1, 6));
    memoryButton.setToolTipText("Show JVM memory usage details.");
    memoryButton.addActionListener(e -> openMemoryDialog());
    installMemoryContextMenuTrigger(memoryButton);
    memoryButtonDefaultBorder = memoryButton.getBorder();
    Insets defaultMargin = memoryButton.getMargin();
    memoryButtonDefaultMargin =
        defaultMargin == null
            ? new Insets(1, 6, 1, 6)
            : new Insets(
                defaultMargin.top, defaultMargin.left, defaultMargin.bottom, defaultMargin.right);
    memoryButtonDefaultContentAreaFilled = memoryButton.isContentAreaFilled();
    memoryButtonDefaultOpaque = memoryButton.isOpaque();

    memoryMoonButton.setFocusable(false);
    memoryMoonButton.setBorderPainted(false);
    memoryMoonButton.setContentAreaFilled(false);
    memoryMoonButton.setOpaque(false);
    memoryMoonButton.setMargin(new Insets(0, 4, 0, 4));
    memoryMoonButton.setToolTipText("Show JVM memory usage details.");
    memoryMoonButton.addActionListener(e -> openMemoryDialog());
    installMemoryContextMenuTrigger(memoryMoonButton);

    memoryIndicator.setStringPainted(false);
    memoryIndicator.setFocusable(false);
    memoryIndicator.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    memoryIndicator.setPreferredSize(new Dimension(86, 18));
    memoryIndicator.setToolTipText("Show JVM memory usage details.");
    memoryIndicator.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            openMemoryDialog();
          }

          @Override
          public void mousePressed(MouseEvent e) {
            maybeShowMemoryModePopup(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShowMemoryModePopup(e);
          }
        });

    memoryWidget.setOpaque(false);
    installMemoryContextMenuTrigger(memoryWidget);
    memoryWidget.add(memoryButton);
    memoryWidget.add(memoryIndicator);
    memoryWidget.add(memoryMoonButton);
    memoryTimer.setRepeats(true);
    buildMemoryModePopup();

    MemoryUsageDisplayMode initialMemoryMode =
        settingsBus.get() != null
            ? settingsBus.get().memoryUsageDisplayMode()
            : MemoryUsageDisplayMode.fromToken(
                uiProps != null ? uiProps.memoryUsageDisplayMode() : null);
    applyMemoryUsageDisplayMode(initialMemoryMode);
    refreshMemoryUsage();

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
    undo.addActionListener(
        e -> {
          if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::undo)) {
            beep();
          }
        });

    JMenuItem redo = new JMenuItem("Redo");
    redo.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | InputEvent.SHIFT_DOWN_MASK));
    redo.addActionListener(
        e -> {
          if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::redo)) {
            beep();
          }
        });

    JMenuItem cut = new JMenuItem("Cut");
    cut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask));
    cut.addActionListener(
        e -> {
          if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::cutSelection)) {
            beep();
          }
        });

    JMenuItem copy = new JMenuItem("Copy");
    copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask));
    copy.addActionListener(
        e -> {
          if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::copySelection)) {
            beep();
          }
        });

    JMenuItem paste = new JMenuItem("Paste");
    paste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
    paste.addActionListener(
        e -> {
          if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::pasteFromClipboard)) {
            beep();
          }
        });

    JMenuItem delete = new JMenuItem("Delete");
    delete.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
    delete.addActionListener(
        e -> {
          if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::deleteForward)) {
            beep();
          }
        });

    JMenuItem selectAll = new JMenuItem("Select All");
    selectAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask));
    selectAll.addActionListener(
        e -> {
          if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::selectAllInput)) {
            beep();
          }
        });

    JMenuItem clearInput = new JMenuItem("Clear Input");
    clearInput.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));
    clearInput.addActionListener(
        e -> {
          if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::clearInput)) {
            beep();
          }
        });

    JMenuItem findInCurrentBuffer = new JMenuItem("Find in Current Buffer");
    findInCurrentBuffer.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK));
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
    commandHistoryPrev.addActionListener(
        e -> {
          if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::historyPrev)) {
            beep();
          }
        });

    JMenuItem commandHistoryNext = new JMenuItem("Next");
    commandHistoryNext.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
    commandHistoryNext.addActionListener(
        e -> {
          if (!insertIntoActiveInput(activeInputRouter, MessageInputPanel::historyNext)) {
            beep();
          }
        });

    JMenuItem commandHistoryClear = new JMenuItem("Clear");
    commandHistoryClear.addActionListener(
        e -> {
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
    edit.addMenuListener(
        new MenuListener() {
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
          public void menuDeselected(MenuEvent e) {}

          @Override
          public void menuCanceled(MenuEvent e) {}
        });

    // Insert
    JMenu insert = new JMenu("Insert");
    JMenu formatting = new JMenu("Formatting");
    JMenu nickTarget = new JMenu("Nick/Target");

    JMenuItem insertBold = new JMenuItem("Bold");
    insertBold.addActionListener(
        e -> {
          if (!insertIntoActiveInput(
              activeInputRouter, panel -> panel.insertPrefixOrWrapSelection(IRC_BOLD, IRC_BOLD))) {
            beep();
          }
        });
    JMenuItem insertItalic = new JMenuItem("Italic");
    insertItalic.addActionListener(
        e -> {
          if (!insertIntoActiveInput(
              activeInputRouter,
              panel -> panel.insertPrefixOrWrapSelection(IRC_ITALIC, IRC_ITALIC))) {
            beep();
          }
        });
    JMenuItem insertUnderline = new JMenuItem("Underline");
    insertUnderline.addActionListener(
        e -> {
          if (!insertIntoActiveInput(
              activeInputRouter,
              panel -> panel.insertPrefixOrWrapSelection(IRC_UNDERLINE, IRC_UNDERLINE))) {
            beep();
          }
        });
    JMenuItem insertReverse = new JMenuItem("Reverse");
    insertReverse.addActionListener(
        e -> {
          if (!insertIntoActiveInput(
              activeInputRouter,
              panel -> panel.insertPrefixOrWrapSelection(IRC_REVERSE, IRC_REVERSE))) {
            beep();
          }
        });
    JMenuItem insertColor = new JMenuItem("Color...");
    insertColor.addActionListener(
        e -> {
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
    insertResetFormatting.addActionListener(
        e -> {
          if (!insertIntoActiveInput(
              activeInputRouter, panel -> panel.insertTextAtCaret(IRC_RESET))) {
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
    insertSelectedNick.addActionListener(
        e -> {
          String nick = resolveSelectedNick(users, targetCoordinator);
          if (nick.isEmpty()
              || !insertIntoActiveInput(
                  activeInputRouter, panel -> panel.insertTextAtCaret(nick))) {
            beep();
          }
        });
    JMenuItem insertCurrentChannel = new JMenuItem("Insert Current Channel");
    insertCurrentChannel.addActionListener(
        e -> {
          String channel = resolveCurrentChannel(targetCoordinator);
          if (channel.isEmpty()
              || !insertIntoActiveInput(
                  activeInputRouter, panel -> panel.insertTextAtCaret(channel))) {
            beep();
          }
        });
    JMenuItem insertCurrentServer = new JMenuItem("Insert Current Server");
    insertCurrentServer.addActionListener(
        e -> {
          String sid = resolveCurrentServerId(targetCoordinator);
          if (sid.isEmpty()
              || !insertIntoActiveInput(activeInputRouter, panel -> panel.insertTextAtCaret(sid))) {
            beep();
          }
        });

    nickTarget.add(insertSelectedNick);
    nickTarget.add(insertCurrentChannel);
    nickTarget.add(insertCurrentServer);

    insert.add(formatting);
    insert.add(nickTarget);
    insert.addMenuListener(
        new MenuListener() {
          @Override
          public void menuSelected(MenuEvent e) {
            MessageInputPanel panel = activeInputPanel(activeInputRouter);
            boolean editable = panel != null && panel.isInputEditable();
            formatting.setEnabled(editable);
            nickTarget.setEnabled(editable);
            insertSelectedNick.setEnabled(
                editable && !resolveSelectedNick(users, targetCoordinator).isEmpty());
            insertCurrentChannel.setEnabled(
                editable && !resolveCurrentChannel(targetCoordinator).isEmpty());
            insertCurrentServer.setEnabled(
                editable && !resolveCurrentServerId(targetCoordinator).isEmpty());
          }

          @Override
          public void menuDeselected(MenuEvent e) {}

          @Override
          public void menuCanceled(MenuEvent e) {}
        });

    // Settings
    JMenu settings = new JMenu("Settings");

    JMenu themeMenu = new JMenu("Theme");
    ButtonGroup themeGroup = new ButtonGroup();
    Map<String, JRadioButtonMenuItem> themeItems = new LinkedHashMap<>();

    for (ThemeManager.ThemeOption opt : themeManager.featuredThemes()) {
      if (opt == null) continue;
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(opt.label());
      item.addActionListener(
          e -> applyThemeQuick(opt.id(), themeManager, settingsBus, runtimeConfig));
      themeGroup.add(item);
      themeMenu.add(item);
      themeItems.put(opt.id(), item);
    }

    themeMenu.addSeparator();
    JMenuItem themeSelector = new JMenuItem("More Themes...");
    themeSelector.setIcon(SvgIcons.action("theme", 16));
    themeSelector.setDisabledIcon(SvgIcons.actionDisabled("theme", 16));
    themeSelector.addActionListener(
        e -> {
          Window w = SwingUtilities.getWindowAncestor(this);
          themeSelectionDialog.open(w);
        });
    themeMenu.add(themeSelector);

    Runnable syncThemeChecks =
        () -> {
          String currentTheme =
              ThemeIdUtils.normalizeThemeId(
                  settingsBus.get() != null ? settingsBus.get().theme() : null);
          themeItems.forEach(
              (id, mi) -> mi.setSelected(ThemeIdUtils.normalizeThemeId(id).equals(currentTheme)));
        };
    syncThemeChecks.run();
    PropertyChangeListener themeListener =
        evt -> {
          if (UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) {
            syncThemeChecks.run();
            UiSettings current = settingsBus.get();
            applyMemoryUsageDisplayMode(
                current != null
                    ? current.memoryUsageDisplayMode()
                    : MemoryUsageDisplayMode.fromToken(
                        uiProps != null ? uiProps.memoryUsageDisplayMode() : null));
            refreshMemoryUsage();
          }
        };
    settingsBus.addListener(themeListener);

    settings.add(themeMenu);

    JMenuItem nickColors = new JMenuItem("Nick Colors...");
    nickColors.setIcon(SvgIcons.action("palette", 16));
    nickColors.setDisabledIcon(SvgIcons.actionDisabled("palette", 16));
    nickColors.addActionListener(
        e -> {
          Window w = SwingUtilities.getWindowAncestor(this);
          nickColorOverridesDialog.open(w);
        });

    JMenuItem prefs = new JMenuItem("Preferences...");
    prefs.setIcon(SvgIcons.action("settings", 16));
    prefs.setDisabledIcon(SvgIcons.actionDisabled("settings", 16));
    prefs.addActionListener(
        e -> {
          Window w = SwingUtilities.getWindowAncestor(this);
          preferencesDialog.open(w);
        });

    JMenuItem ignoreLists = new JMenuItem("Ignore Lists...");
    ignoreLists.setIcon(SvgIcons.action("ban", 16));
    ignoreLists.setDisabledIcon(SvgIcons.actionDisabled("ban", 16));
    ignoreLists.addActionListener(
        e -> {
          if (ignoreListDialog == null) return;
          Window w = SwingUtilities.getWindowAncestor(this);
          TargetRef t = (targetCoordinator == null) ? null : targetCoordinator.getActiveTarget();
          String sid =
              (t != null && t.serverId() != null && !t.serverId().isBlank())
                  ? t.serverId()
                  : (targetCoordinator == null
                      ? "default"
                      : targetCoordinator.safeStatusTarget().serverId());
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
    reopenServersDock.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    reopenServersDock.addActionListener(e -> ensureSideDockVisible(serverTree, DockingRegion.WEST));

    JMenuItem reopenUsersDock = new JMenuItem("Reopen Users Dock");
    reopenUsersDock.setIcon(SvgIcons.action("dock-right", 16));
    reopenUsersDock.setDisabledIcon(SvgIcons.actionDisabled("dock-right", 16));
    reopenUsersDock.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    reopenUsersDock.addActionListener(e -> ensureSideDockVisible(users, DockingRegion.EAST));

    JMenuItem resetLayout = new JMenuItem("Reset Dock Layout");
    resetLayout.setIcon(SvgIcons.action("refresh", 16));
    resetLayout.setDisabledIcon(SvgIcons.actionDisabled("refresh", 16));
    resetLayout.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    resetLayout.addActionListener(e -> resetDockLayout());

    JCheckBoxMenuItem showChannelListNodes = new JCheckBoxMenuItem("Show Channel List Node");
    showChannelListNodes.setSelected(serverTree.isChannelListNodesVisible());
    showChannelListNodes.addActionListener(
        e -> serverTree.setChannelListNodesVisible(showChannelListNodes.isSelected()));
    serverTree.addPropertyChangeListener(
        ServerTreeDockable.PROP_CHANNEL_LIST_NODES_VISIBLE,
        evt -> showChannelListNodes.setSelected(Boolean.TRUE.equals(evt.getNewValue())));

    JCheckBoxMenuItem showDccNodes = new JCheckBoxMenuItem("Show DCC Transfers Node");
    showDccNodes.setSelected(serverTree.isDccTransfersNodesVisible());
    showDccNodes.addActionListener(
        e -> serverTree.setDccTransfersNodesVisible(showDccNodes.isSelected()));
    serverTree.addPropertyChangeListener(
        ServerTreeDockable.PROP_DCC_TRANSFERS_NODES_VISIBLE,
        evt -> showDccNodes.setSelected(Boolean.TRUE.equals(evt.getNewValue())));

    JMenu currentServerNodes = new JMenu("Current Server Nodes");
    JCheckBoxMenuItem showServerNode = new JCheckBoxMenuItem("Show Server Node");
    JCheckBoxMenuItem showNotificationsNodes = new JCheckBoxMenuItem("Show Notifications Node");
    JCheckBoxMenuItem showLogViewerNodes = new JCheckBoxMenuItem("Show Log Viewer Node");
    JCheckBoxMenuItem showMonitorNodes = new JCheckBoxMenuItem("Show Monitor Node");
    JCheckBoxMenuItem showInterceptorsNodes = new JCheckBoxMenuItem("Show Interceptors Node");

    Runnable refreshCurrentServerNodeItems =
        () -> {
          String sid = resolveCurrentServerId(targetCoordinator);
          boolean hasServer = !sid.isBlank();

          showServerNode.setEnabled(hasServer);
          showNotificationsNodes.setEnabled(hasServer);
          showLogViewerNodes.setEnabled(hasServer);
          showMonitorNodes.setEnabled(hasServer);
          showInterceptorsNodes.setEnabled(hasServer);

          if (!hasServer) {
            showServerNode.setSelected(false);
            showNotificationsNodes.setSelected(false);
            showLogViewerNodes.setSelected(false);
            showMonitorNodes.setSelected(false);
            showInterceptorsNodes.setSelected(false);
            return;
          }

          showServerNode.setSelected(serverTree.isServerNodeVisibleForServer(sid));
          showNotificationsNodes.setSelected(serverTree.isNotificationsNodeVisibleForServer(sid));
          showLogViewerNodes.setSelected(serverTree.isLogViewerNodeVisibleForServer(sid));
          showMonitorNodes.setSelected(serverTree.isMonitorNodeVisibleForServer(sid));
          showInterceptorsNodes.setSelected(serverTree.isInterceptorsNodeVisibleForServer(sid));
        };

    showServerNode.addActionListener(
        e -> {
          String sid = resolveCurrentServerId(targetCoordinator);
          if (sid.isBlank()) return;
          serverTree.setServerNodeVisibleForServer(sid, showServerNode.isSelected());
        });

    showNotificationsNodes.addActionListener(
        e -> {
          String sid = resolveCurrentServerId(targetCoordinator);
          if (sid.isBlank()) return;
          serverTree.setNotificationsNodeVisibleForServer(sid, showNotificationsNodes.isSelected());
        });

    showLogViewerNodes.addActionListener(
        e -> {
          String sid = resolveCurrentServerId(targetCoordinator);
          if (sid.isBlank()) return;
          serverTree.setLogViewerNodeVisibleForServer(sid, showLogViewerNodes.isSelected());
        });

    showMonitorNodes.addActionListener(
        e -> {
          String sid = resolveCurrentServerId(targetCoordinator);
          if (sid.isBlank()) return;
          serverTree.setMonitorNodeVisibleForServer(sid, showMonitorNodes.isSelected());
        });

    showInterceptorsNodes.addActionListener(
        e -> {
          String sid = resolveCurrentServerId(targetCoordinator);
          if (sid.isBlank()) return;
          serverTree.setInterceptorsNodeVisibleForServer(sid, showInterceptorsNodes.isSelected());
        });

    currentServerNodes.add(showServerNode);
    currentServerNodes.add(showNotificationsNodes);
    currentServerNodes.add(showLogViewerNodes);
    currentServerNodes.add(showMonitorNodes);
    currentServerNodes.add(showInterceptorsNodes);
    currentServerNodes.addSeparator();
    currentServerNodes.add(showChannelListNodes);
    currentServerNodes.add(showDccNodes);

    JCheckBoxMenuItem showApplicationRoot = new JCheckBoxMenuItem("Show Application Root");
    showApplicationRoot.setSelected(serverTree.isApplicationRootVisible());
    showApplicationRoot.addActionListener(
        e -> serverTree.setApplicationRootVisible(showApplicationRoot.isSelected()));
    serverTree.addPropertyChangeListener(
        ServerTreeDockable.PROP_APPLICATION_ROOT_VISIBLE,
        evt -> showApplicationRoot.setSelected(Boolean.TRUE.equals(evt.getNewValue())));

    JMenuItem openSelectedNodeDock = new JMenuItem("Open Selected Node in Chat Dock");
    openSelectedNodeDock.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    openSelectedNodeDock.addActionListener(e -> serverTree.openSelectedNodeInChatDock());

    window.add(reopenServersDock);
    window.add(reopenUsersDock);
    window.addSeparator();
    window.add(resetLayout);
    window.addSeparator();
    window.add(currentServerNodes);
    window.add(showApplicationRoot);
    window.addSeparator();
    window.add(openSelectedNodeDock);
    window.addSeparator();

    // Node actions come from the server tree controller.
    // Enabled/disabled state updates automatically based on the tree selection.
    JMenuItem moveNodeUp = new JMenuItem(serverTree.moveNodeUpAction());
    moveNodeUp.setIcon(SvgIcons.action("arrow-up", 16));
    moveNodeUp.setDisabledIcon(SvgIcons.actionDisabled("arrow-up", 16));
    moveNodeUp.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    JMenuItem moveNodeDown = new JMenuItem(serverTree.moveNodeDownAction());
    moveNodeDown.setIcon(SvgIcons.action("arrow-down", 16));
    moveNodeDown.setDisabledIcon(SvgIcons.actionDisabled("arrow-down", 16));
    moveNodeDown.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    JMenuItem closeNode = new JMenuItem(serverTree.closeNodeAction());
    closeNode.setIcon(SvgIcons.action("close", 16));
    closeNode.setDisabledIcon(SvgIcons.actionDisabled("close", 16));
    closeNode.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));
    window.add(moveNodeUp);
    window.add(moveNodeDown);
    window.addSeparator();
    window.add(closeNode);

    window.addMenuListener(
        new MenuListener() {
          @Override
          public void menuSelected(MenuEvent e) {
            refreshCurrentServerNodeItems.run();
          }

          @Override
          public void menuDeselected(MenuEvent e) {}

          @Override
          public void menuCanceled(MenuEvent e) {}
        });

    // Servers
    JMenu servers = new JMenu("Servers");
    JMenuItem addServer = new JMenuItem("Add Server...");
    addServer.setIcon(SvgIcons.action("plus", 16));
    addServer.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    addServer.addActionListener(
        e -> {
          Window w = SwingUtilities.getWindowAncestor(this);
          serverDialogs.openAddServer(w);
        });
    JMenuItem editServers = new JMenuItem("Edit Servers...");
    editServers.setIcon(SvgIcons.action("edit", 16));
    editServers.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
    editServers.addActionListener(
        e -> {
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
    about.addActionListener(
        e ->
            javax.swing.JOptionPane.showMessageDialog(
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
    add(Box.createHorizontalGlue());
    add(memoryWidget);

    installMenuPopupThemeSync(file, servers, edit, insert, settings, window, help);
  }

  private static void installMenuPopupThemeSync(JMenu... menus) {
    if (menus == null || menus.length == 0) return;
    for (JMenu menu : menus) {
      if (menu == null) continue;
      menu.addMenuListener(
          new MenuListener() {
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

  @Override
  public void addNotify() {
    super.addNotify();
    updateMemoryButtonStyleForCurrentMode();
    updateMemoryButtonHeightForCurrentMode();
    updateMemoryIndicatorSizeForCurrentMode();
    if (memoryUsageDisplayMode != MemoryUsageDisplayMode.HIDDEN && !memoryTimer.isRunning()) {
      memoryTimer.start();
    }
  }

  @Override
  public void removeNotify() {
    memoryTimer.stop();
    hideWarningTooltip();
    if (warningTooltipHideTimer != null) {
      warningTooltipHideTimer.stop();
    }
    if (memoryDialog != null) {
      memoryDialog.setVisible(false);
    }
    super.removeNotify();
  }

  private void applyMemoryUsageDisplayMode(MemoryUsageDisplayMode mode) {
    memoryUsageDisplayMode = mode != null ? mode : MemoryUsageDisplayMode.LONG;

    switch (memoryUsageDisplayMode) {
      case LONG, SHORT -> {
        memoryWidget.setVisible(true);
        memoryButton.setVisible(true);
        memoryIndicator.setVisible(false);
        memoryMoonButton.setVisible(false);
      }
      case INDICATOR -> {
        memoryWidget.setVisible(true);
        memoryButton.setVisible(false);
        memoryIndicator.setVisible(true);
        memoryMoonButton.setVisible(false);
      }
      case MOON -> {
        memoryWidget.setVisible(true);
        memoryButton.setVisible(false);
        memoryIndicator.setVisible(false);
        memoryMoonButton.setVisible(true);
      }
      case HIDDEN -> {
        memoryWidget.setVisible(false);
        memoryButton.setVisible(false);
        memoryIndicator.setVisible(false);
        memoryMoonButton.setVisible(false);
      }
    }

    updateMemoryButtonStyleForCurrentMode();
    syncMemoryModePopupSelection();
    if (memoryUsageDisplayMode == MemoryUsageDisplayMode.HIDDEN) {
      memoryTimer.stop();
      if (memoryDialog != null) memoryDialog.setVisible(false);
      hideWarningTooltip();
    } else if (isDisplayable() && !memoryTimer.isRunning()) {
      memoryTimer.start();
    }
    updateMemoryButtonHeightForCurrentMode();
    updateMemoryIndicatorSizeForCurrentMode();
    revalidate();
    repaint();
  }

  private void refreshMemoryUsage() {
    if (memoryUsageDisplayMode == MemoryUsageDisplayMode.HIDDEN) return;

    MemorySnapshot snapshot = readMemorySnapshot();
    Integer percentUsed =
        snapshot.maxBytes() > 0
            ? Math.max(
                0,
                Math.min(
                    100, (int) Math.round((snapshot.usedBytes() * 100.0d) / snapshot.maxBytes())))
            : null;
    double usageRatio =
        snapshot.maxBytes() > 0 ? snapshot.usedBytes() / (double) snapshot.maxBytes() : 0d;
    UiSettings currentSettings = settingsBus != null ? settingsBus.get() : null;
    int nearMaxPercent =
        currentSettings != null ? currentSettings.memoryUsageWarningNearMaxPercent() : 5;
    boolean isNearMax =
        snapshot.maxBytes() > 0 && usageRatio >= Math.max(0d, (100d - nearMaxPercent) / 100d);

    String longText =
        snapshot.maxBytes() > 0
            ? "Mem: " + toGib(snapshot.usedBytes()) + " / " + toGib(snapshot.maxBytes())
            : "Mem: " + toGib(snapshot.usedBytes());
    String shortText = percentUsed == null ? "n/a" : percentUsed + "%";
    String tooltip =
        longText
            + (snapshot.maxBytes() > 0
                ? " (" + toPercent(snapshot.usedBytes(), snapshot.maxBytes()) + ")"
                : "")
            + ". Click for details.";

    if (memoryUsageDisplayMode == MemoryUsageDisplayMode.LONG) {
      memoryButton.setText(longText);
    } else if (memoryUsageDisplayMode == MemoryUsageDisplayMode.SHORT) {
      memoryButton.setText(shortText);
    }
    updateMemoryButtonHeightForCurrentMode();
    memoryButton.setToolTipText(tooltip);

    if (percentUsed != null) {
      memoryIndicator.setIndeterminate(false);
      memoryIndicator.setValue(percentUsed);
      memoryIndicator.setStringPainted(true);
      memoryIndicator.setString(percentUsed + "%");
    } else {
      memoryIndicator.setIndeterminate(true);
      memoryIndicator.setStringPainted(false);
      memoryIndicator.setString(null);
    }
    updateMemoryIndicatorSizeForCurrentMode();
    memoryIndicator.setToolTipText(tooltip);
    updateMoonDisplay(snapshot, isNearMax);

    maybeEmitMemoryWarning(snapshot, isNearMax, currentSettings);

    if (memoryDialog != null && memoryDialog.isVisible()) {
      updateMemoryDialog(snapshot);
    }
  }

  private void openMemoryDialog() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::openMemoryDialog);
      return;
    }
    if (memoryUsageDisplayMode == MemoryUsageDisplayMode.HIDDEN) return;
    if (memoryDialog == null) {
      createMemoryDialog();
    }
    updateMemoryDialog(readMemorySnapshot());
    memoryDialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
    memoryDialog.setVisible(true);
    memoryDialog.toFront();
  }

  private void createMemoryDialog() {
    Window owner = SwingUtilities.getWindowAncestor(this);
    memoryDialog =
        owner instanceof Frame frame
            ? new JDialog(frame, "JVM Memory", false)
            : new JDialog((Frame) null, "JVM Memory", false);
    memoryDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

    memoryDialogGauge = new JProgressBar(0, 100);
    memoryDialogGauge.setStringPainted(true);
    memoryDialogGauge.setPreferredSize(new Dimension(240, 22));
    memoryDialogDialGauge = new MemoryDialGauge();
    memoryDialogDetails = new JLabel();

    JButton gcButton = new JButton("Run GC");
    gcButton.addActionListener(
        e -> {
          System.gc();
          refreshMemoryUsage();
        });
    JButton closeButton = new JButton("Close");
    closeButton.addActionListener(e -> memoryDialog.setVisible(false));

    JButton jfrStatusButton = new JButton("JFR Status");
    jfrStatusButton.addActionListener(e -> showJfrStatusDialog());

    JButton jfrSnapshotButton = new JButton("Capture JFR Snapshot");
    jfrSnapshotButton.addActionListener(e -> showJfrSnapshotDialog());

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    controls.add(jfrStatusButton);
    controls.add(jfrSnapshotButton);
    controls.add(gcButton);
    controls.add(closeButton);

    JPanel metrics = new JPanel(new BorderLayout(12, 0));
    metrics.setOpaque(false);
    metrics.add(memoryDialogDialGauge, BorderLayout.WEST);
    metrics.add(memoryDialogDetails, BorderLayout.CENTER);

    JPanel content = new JPanel(new BorderLayout(10, 10));
    content.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
    content.add(memoryDialogGauge, BorderLayout.NORTH);
    content.add(metrics, BorderLayout.CENTER);
    content.add(controls, BorderLayout.SOUTH);

    memoryDialog.setContentPane(content);
    memoryDialog.setSize(520, 280);
  }

  private void updateMemoryDialog(MemorySnapshot snapshot) {
    if (memoryDialogGauge == null || memoryDialogDialGauge == null || memoryDialogDetails == null)
      return;
    if (snapshot.maxBytes() > 0) {
      int percent = (int) Math.round((snapshot.usedBytes() * 100.0d) / snapshot.maxBytes());
      percent = Math.max(0, Math.min(100, percent));
      memoryDialogGauge.setIndeterminate(false);
      memoryDialogGauge.setValue(percent);
      memoryDialogGauge.setString(percent + "% used");
    } else {
      memoryDialogGauge.setIndeterminate(true);
      memoryDialogGauge.setString("max heap unknown");
    }
    memoryDialogDialGauge.setSnapshot(snapshot);

    memoryDialogDetails.setText(
        "<html>"
            + "Used heap: "
            + toMib(snapshot.usedBytes())
            + "<br>"
            + "Committed heap: "
            + toMib(snapshot.committedBytes())
            + "<br>"
            + "Free in committed heap: "
            + toMib(snapshot.freeBytes())
            + "<br>"
            + "Max heap: "
            + (snapshot.maxBytes() > 0 ? toMib(snapshot.maxBytes()) : "unknown")
            + "<br>"
            + "Usage: "
            + toPercent(snapshot.usedBytes(), snapshot.maxBytes())
            + "</html>");
  }

  private static MemorySnapshot readMemorySnapshot() {
    Runtime runtime = Runtime.getRuntime();
    long committed = Math.max(0L, runtime.totalMemory());
    long free = Math.max(0L, runtime.freeMemory());
    long used = Math.max(0L, committed - free);
    long max = runtime.maxMemory();
    if (max == Long.MAX_VALUE || max <= 0L) {
      max = 0L;
    }
    return new MemorySnapshot(used, committed, free, max);
  }

  private static String toMib(long bytes) {
    double mib = bytes / (double) MIB;
    return String.format(Locale.ROOT, "%.1f MiB", mib);
  }

  private static String toGib(long bytes) {
    double gib = bytes / (double) (1024L * 1024L * 1024L);
    return String.format(Locale.ROOT, "%.2f GiB", gib);
  }

  private static String toPercent(long part, long whole) {
    if (whole <= 0L) return "n/a";
    double pct = (part * 100.0d) / whole;
    return String.format(Locale.ROOT, "%.1f%%", pct);
  }

  private void updateMoonDisplay(MemorySnapshot snapshot, boolean nearMax) {
    if (memoryUsageDisplayMode != MemoryUsageDisplayMode.MOON) return;
    double usageRatio =
        snapshot.maxBytes() > 0 ? snapshot.usedBytes() / (double) snapshot.maxBytes() : 0d;
    MoonPhase phase = phaseForUsage(usageRatio, nearMax);
    memoryMoonButton.setIcon(new MoonPhaseIcon(phase, MOON_ICON_SIZE));
    memoryMoonButton.setText(null);
    memoryMoonButton.setPreferredSize(new Dimension(MOON_ICON_SIZE + 10, MOON_ICON_SIZE + 6));
  }

  private MoonPhase phaseForUsage(double usageRatio, boolean nearMax) {
    if (nearMax) return MoonPhase.WARNING;
    if (usageRatio < 0.05d) return MoonPhase.EMPTY;
    if (usageRatio < 0.25d) return MoonPhase.NEW;
    if (usageRatio < 0.45d) return MoonPhase.WAXING_CRESCENT;
    if (usageRatio < 0.65d) return MoonPhase.FIRST_QUARTER;
    if (usageRatio < 0.90d) return MoonPhase.WAXING_GIBBOUS;
    return MoonPhase.FULL;
  }

  private void maybeEmitMemoryWarning(
      MemorySnapshot snapshot, boolean nearMax, UiSettings currentSettings) {
    if (!nearMax) {
      warningThresholdActive = false;
      hideWarningTooltip();
      return;
    }

    long now = System.currentTimeMillis();
    if (warningThresholdActive && (now - lastWarningAtMs) < MEMORY_WARNING_COOLDOWN_MS) {
      return;
    }
    warningThresholdActive = true;
    lastWarningAtMs = now;

    String message =
        "Memory usage is close to JVM max: "
            + toMib(snapshot.usedBytes())
            + " / "
            + toMib(snapshot.maxBytes())
            + " ("
            + toPercent(snapshot.usedBytes(), snapshot.maxBytes())
            + ").";

    boolean tooltipEnabled =
        currentSettings == null || currentSettings.memoryUsageWarningTooltipEnabled();
    boolean toastEnabled =
        currentSettings != null && currentSettings.memoryUsageWarningToastEnabled();
    boolean pushyEnabled =
        currentSettings != null && currentSettings.memoryUsageWarningPushyEnabled();
    boolean soundEnabled =
        currentSettings != null && currentSettings.memoryUsageWarningSoundEnabled();

    if (tooltipEnabled) {
      showWarningTooltip(message);
    }
    if (toastEnabled && trayNotificationService != null) {
      trayNotificationService.notifyCustom(
          "local",
          "status",
          "IRCafe Memory Warning",
          message,
          true,
          false,
          IrcEventNotificationRule.FocusScope.ANY,
          false,
          null,
          false,
          null);
    }
    if (pushyEnabled && pushyNotificationService != null) {
      pushyNotificationService.notifyEvent(
          IrcEventNotificationRule.EventType.NOTICE_RECEIVED,
          "local",
          "status",
          "ircafe",
          false,
          "IRCafe Memory Warning",
          message);
    }
    if (soundEnabled && notificationSoundService != null) {
      notificationSoundService.play();
    }
  }

  private void showWarningTooltip(String text) {
    JComponent anchor = currentMemoryAnchor();
    if (anchor == null || !anchor.isShowing()) return;
    hideWarningTooltip();

    JToolTip tip = new JToolTip();
    tip.setTipText(text);
    java.awt.Point p = anchor.getLocationOnScreen();
    int x = p.x + Math.max(4, anchor.getWidth() / 2 - 140);
    int y = p.y + anchor.getHeight() + 6;
    warningTooltipPopup = PopupFactory.getSharedInstance().getPopup(anchor, tip, x, y);
    warningTooltipPopup.show();

    if (warningTooltipHideTimer == null) {
      warningTooltipHideTimer =
          new Timer(
              4500,
              e -> {
                hideWarningTooltip();
                if (warningTooltipHideTimer != null) warningTooltipHideTimer.stop();
              });
      warningTooltipHideTimer.setRepeats(false);
    }
    warningTooltipHideTimer.restart();
  }

  private void hideWarningTooltip() {
    if (warningTooltipHideTimer != null) {
      warningTooltipHideTimer.stop();
    }
    if (warningTooltipPopup != null) {
      try {
        warningTooltipPopup.hide();
      } catch (Exception ignored) {
      }
      warningTooltipPopup = null;
    }
  }

  private JComponent currentMemoryAnchor() {
    if (memoryButton.isVisible()) return memoryButton;
    if (memoryIndicator.isVisible()) return memoryIndicator;
    if (memoryMoonButton.isVisible()) return memoryMoonButton;
    return null;
  }

  private void updateMemoryButtonStyleForCurrentMode() {
    if (memoryUsageDisplayMode == MemoryUsageDisplayMode.SHORT) {
      Color border = javax.swing.UIManager.getColor("Component.borderColor");
      if (border == null) border = javax.swing.UIManager.getColor("Label.disabledForeground");
      if (border == null) border = new Color(120, 120, 120);
      memoryButton.setBorder(
          javax.swing.BorderFactory.createCompoundBorder(
              javax.swing.BorderFactory.createLineBorder(border),
              javax.swing.BorderFactory.createEmptyBorder(1, 8, 1, 8)));
      memoryButton.setMargin(new Insets(1, 8, 1, 8));
      memoryButton.setContentAreaFilled(false);
      memoryButton.setOpaque(false);
      return;
    }

    memoryButton.setBorder(memoryButtonDefaultBorder);
    memoryButton.setMargin(
        new Insets(
            memoryButtonDefaultMargin.top,
            memoryButtonDefaultMargin.left,
            memoryButtonDefaultMargin.bottom,
            memoryButtonDefaultMargin.right));
    memoryButton.setContentAreaFilled(memoryButtonDefaultContentAreaFilled);
    memoryButton.setOpaque(memoryButtonDefaultOpaque);
  }

  private void updateMemoryButtonHeightForCurrentMode() {
    if (memoryUsageDisplayMode != MemoryUsageDisplayMode.SHORT) {
      memoryButton.setPreferredSize(null);
      memoryButton.setMinimumSize(null);
      memoryButton.setMaximumSize(null);
      return;
    }

    int barH = targetShortMemoryButtonHeightPx();
    int width = targetShortMemoryButtonWidthPx();
    Dimension d = new Dimension(width, barH);
    memoryButton.setPreferredSize(d);
    memoryButton.setMinimumSize(d);
    memoryButton.setMaximumSize(d);
  }

  private int targetShortMemoryButtonWidthPx() {
    int width = 56;
    FontMetrics metrics = memoryButton.getFontMetrics(memoryButton.getFont());
    if (metrics == null) return width;
    width = Math.max(width, metrics.stringWidth("100%") + 22);
    String label = Objects.toString(memoryButton.getText(), "");
    if (!label.isBlank()) {
      width = Math.max(width, metrics.stringWidth(label) + 22);
    }
    return width;
  }

  private void updateMemoryIndicatorSizeForCurrentMode() {
    int barH = Math.max(18, targetShortMemoryButtonHeightPx() - 2);
    int width = 86;
    FontMetrics metrics = memoryIndicator.getFontMetrics(memoryIndicator.getFont());
    if (metrics != null) {
      width = Math.max(width, metrics.stringWidth("100%") + 30);
    }
    Dimension d = new Dimension(width, barH);
    memoryIndicator.setPreferredSize(d);
    memoryIndicator.setMinimumSize(d);
  }

  private int targetShortMemoryButtonHeightPx() {
    int menuHeight = 0;
    for (int i = 0; i < getMenuCount(); i++) {
      JMenu menu = getMenu(i);
      if (menu == null || !menu.isVisible()) continue;
      Dimension d = menu.getPreferredSize();
      if (d != null && d.height > menuHeight) {
        menuHeight = d.height;
      }
    }
    if (menuHeight <= 0) {
      Dimension pref = super.getPreferredSize();
      if (pref != null) {
        menuHeight = pref.height - getInsets().top - getInsets().bottom;
      }
    }
    return Math.max(20, menuHeight);
  }

  private void buildMemoryModePopup() {
    memoryModePopup.removeAll();
    ButtonGroup group = new ButtonGroup();
    for (MemoryUsageDisplayMode mode : MemoryUsageDisplayMode.values()) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(mode.toString());
      item.putClientProperty("memoryMode", mode);
      item.addActionListener(e -> setMemoryUsageDisplayModeFromUi(mode));
      group.add(item);
      memoryModePopup.add(item);
    }
  }

  private void syncMemoryModePopupSelection() {
    for (Component c : memoryModePopup.getComponents()) {
      if (!(c instanceof JRadioButtonMenuItem item)) continue;
      Object v = item.getClientProperty("memoryMode");
      item.setSelected(v == memoryUsageDisplayMode);
    }
  }

  private void setMemoryUsageDisplayModeFromUi(MemoryUsageDisplayMode mode) {
    if (mode == null) return;
    UiSettings current = settingsBus != null ? settingsBus.get() : null;
    if (current != null && settingsBus != null) {
      UiSettings updated = current.withMemoryUsageDisplayMode(mode);
      settingsBus.set(updated);
    } else {
      applyMemoryUsageDisplayMode(mode);
      refreshMemoryUsage();
    }
    if (runtimeConfig != null) {
      runtimeConfig.rememberMemoryUsageDisplayMode(mode.token());
    }
  }

  private void installMemoryContextMenuTrigger(JComponent component) {
    if (component == null) return;
    component.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            maybeShowMemoryModePopup(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShowMemoryModePopup(e);
          }
        });
  }

  private void maybeShowMemoryModePopup(MouseEvent e) {
    if (e == null || !e.isPopupTrigger()) return;
    if (!(e.getComponent() instanceof JComponent c)) return;
    syncMemoryModePopupSelection();
    PopupMenuThemeSupport.prepareForDisplay(memoryModePopup);
    memoryModePopup.show(c, e.getX(), e.getY());
  }

  private void showJfrStatusDialog() {
    String report =
        runtimeJfrService != null
            ? runtimeJfrService.statusReport()
            : "JFR service is unavailable.";
    showMultilineInfoDialog("JFR Status", report);
  }

  private void showJfrSnapshotDialog() {
    String report;
    if (runtimeJfrService != null) {
      RuntimeJfrService.SnapshotReport snapshot = runtimeJfrService.captureSnapshot();
      report = snapshot != null ? snapshot.summary() : "No snapshot data.";
    } else {
      report = "JFR service is unavailable.";
    }
    showMultilineInfoDialog("JFR Snapshot", report);
  }

  private void showMultilineInfoDialog(String title, String body) {
    JTextArea text = new JTextArea(Objects.toString(body, ""));
    text.setEditable(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setCaretPosition(0);
    JScrollPane scroll = new JScrollPane(text);
    scroll.setPreferredSize(new Dimension(760, 420));
    JOptionPane.showMessageDialog(
        SwingUtilities.getWindowAncestor(this), scroll, title, JOptionPane.INFORMATION_MESSAGE);
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
    // Run a short stabilization loop because ModernDocking may rebuild split panes over several EDT
    // ticks.
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

  private double proportionForSideDock(Window root, Dockable dockable, DockingRegion region) {
    int serverPx = DEFAULT_SERVER_DOCK_WIDTH_PX;
    int usersPx = DEFAULT_USERS_DOCK_WIDTH_PX;
    if (uiProps != null && uiProps.layout() != null) {
      serverPx = uiProps.layout().serverDockWidthPx();
      usersPx = uiProps.layout().userDockWidthPx();
    }

    int targetPx =
        (dockable == serverTree)
            ? serverPx
            : (dockable == users) ? usersPx : DEFAULT_USERS_DOCK_WIDTH_PX;

    int base =
        (region == DockingRegion.NORTH || region == DockingRegion.SOUTH)
            ? Math.max(1, root.getHeight())
            : Math.max(1, root.getWidth());

    // If the window isn't realized yet, fall back to sane proportions.
    if (base <= 1) {
      return (dockable == serverTree)
          ? DEFAULT_SERVER_DOCK_PROPORTION
          : DEFAULT_USERS_DOCK_PROPORTION;
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

  private void dockSafe(
      Dockable dockable, Dockable anchor, DockingRegion region, double proportion) {
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
    boolean west =
        DockingTuner.applyInitialWestDockWidth(root, (java.awt.Component) serverTree, serverPx);
    boolean east =
        DockingTuner.applyInitialEastDockWidth(root, (java.awt.Component) users, usersPx);
    DockingTuner.lockWestDockWidth(root, (java.awt.Component) serverTree, serverPx);
    DockingTuner.lockEastDockWidth(root, (java.awt.Component) users, usersPx);
    return west && east;
  }

  private void applySideDockLocksWithStabilization(Window root) {
    if (root == null) return;

    boolean done = applySideDockLocks(root);
    if (done) return;

    final int[] passes = new int[] {0};
    javax.swing.Timer settle = new javax.swing.Timer(110, null);
    settle.addActionListener(
        e -> {
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

  private static String resolveSelectedNick(
      UserListDockable users, ActiveTargetPort targetCoordinator) {
    String nick = users == null ? "" : Objects.toString(users.selectedNick(), "").trim();
    if (!nick.isEmpty()) return nick;

    TargetRef at = targetCoordinator == null ? null : targetCoordinator.getActiveTarget();
    if (at != null && !at.isStatus() && !at.isUiOnly() && !at.isChannel()) {
      return Objects.toString(at.target(), "").trim();
    }
    return "";
  }

  private static String resolveCurrentChannel(ActiveTargetPort targetCoordinator) {
    TargetRef at = targetCoordinator == null ? null : targetCoordinator.getActiveTarget();
    if (at == null || !at.isChannel()) return "";
    return Objects.toString(at.target(), "").trim();
  }

  private static String resolveCurrentServerId(ActiveTargetPort targetCoordinator) {
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

    java.util.function.Consumer<Boolean> setBgEnabled =
        enabled -> {
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

    int result =
        JOptionPane.showConfirmDialog(
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

  private enum MoonPhase {
    EMPTY,
    NEW,
    WAXING_CRESCENT,
    FIRST_QUARTER,
    WAXING_GIBBOUS,
    FULL,
    WARNING
  }

  private static final class MoonPhaseIcon implements javax.swing.Icon {
    private final MoonPhase phase;
    private final int size;

    private MoonPhaseIcon(MoonPhase phase, int size) {
      this.phase = phase == null ? MoonPhase.EMPTY : phase;
      this.size = Math.max(10, size);
    }

    @Override
    public int getIconWidth() {
      return size;
    }

    @Override
    public int getIconHeight() {
      return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      if (!(g instanceof Graphics2D g2)) return;
      Object aa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      Color fg = javax.swing.UIManager.getColor("Label.foreground");
      Color bg = javax.swing.UIManager.getColor("Panel.background");
      if (fg == null) fg = Color.WHITE;
      if (bg == null) bg = Color.BLACK;

      int d = size - 2;
      int ox = x + 1;
      int oy = y + 1;

      if (phase == MoonPhase.WARNING) {
        int[] xs = {ox + d / 2, ox + d, ox};
        int[] ys = {oy, oy + d, oy + d};
        g2.setColor(new Color(246, 186, 42));
        g2.fillPolygon(xs, ys, 3);
        g2.setColor(new Color(60, 44, 10));
        g2.drawPolygon(xs, ys, 3);
        g2.drawLine(ox + d / 2, oy + d / 3, ox + d / 2, oy + (d * 2 / 3));
        g2.fillOval(ox + d / 2 - 1, oy + (d * 3 / 4), 3, 3);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        return;
      }

      g2.setColor(fg);
      g2.drawOval(ox, oy, d, d);

      switch (phase) {
        case EMPTY -> {
          // Outline only.
        }
        case NEW -> {
          g2.fillOval(ox, oy, d, d);
        }
        case WAXING_CRESCENT -> {
          g2.fillOval(ox, oy, d, d);
          g2.setColor(bg);
          g2.fillOval(ox + (d / 3), oy, d, d);
        }
        case FIRST_QUARTER -> {
          g2.fillOval(ox, oy, d, d);
          g2.setColor(bg);
          g2.fillRect(ox, oy, d / 2, d + 1);
        }
        case WAXING_GIBBOUS -> {
          g2.fillOval(ox, oy, d, d);
          g2.setColor(bg);
          g2.fillOval(ox - (d / 5), oy, d / 2, d);
        }
        case FULL -> {
          g2.setColor(new Color(246, 246, 232));
          g2.fillOval(ox, oy, d, d);
          g2.setColor(fg);
          g2.drawOval(ox, oy, d, d);
        }
        default -> {}
      }
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }
  }

  private static final class MemoryDialGauge extends JComponent {
    private int percentUsed = -1;
    private long usedBytes;
    private long maxBytes;

    private MemoryDialGauge() {
      setOpaque(false);
      setPreferredSize(new Dimension(176, 176));
      setMinimumSize(new Dimension(150, 150));
      setToolTipText("Heap usage gauge");
    }

    private void setSnapshot(MemorySnapshot snapshot) {
      if (snapshot == null) {
        percentUsed = -1;
        usedBytes = 0L;
        maxBytes = 0L;
        repaint();
        return;
      }
      usedBytes = Math.max(0L, snapshot.usedBytes());
      maxBytes = Math.max(0L, snapshot.maxBytes());
      if (maxBytes > 0L) {
        percentUsed = (int) Math.round((usedBytes * 100.0d) / maxBytes);
        percentUsed = Math.max(0, Math.min(100, percentUsed));
      } else {
        percentUsed = -1;
      }
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (!(g instanceof Graphics2D g2)) return;

      Object oldAa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      Stroke oldStroke = g2.getStroke();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int pad = 12;
      int size = Math.min(getWidth(), getHeight()) - (pad * 2);
      if (size <= 10) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
        return;
      }
      int x = (getWidth() - size) / 2;
      int y = (getHeight() - size) / 2;
      int strokeW = Math.max(10, size / 9);

      Color track = uiColor("ProgressBar.background", new Color(228, 228, 228));
      Color text = uiColor("Label.foreground", new Color(33, 33, 33));
      Color arcStart = new Color(38, 166, 91);
      Color arcEnd = new Color(220, 73, 56);

      g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.setColor(track);
      g2.draw(new Arc2D.Double(x, y, size, size, 225, -270, Arc2D.OPEN));

      if (percentUsed >= 0) {
        double extent = -270.0d * percentUsed / 100.0d;
        GradientPaint paint =
            new GradientPaint(
                (float) x, (float) (y + size), arcStart, (float) (x + size), (float) y, arcEnd);
        g2.setPaint(paint);
        g2.draw(new Arc2D.Double(x, y, size, size, 225, extent, Arc2D.OPEN));
      }

      java.awt.Font baseFont = getFont();
      if (baseFont == null) {
        baseFont = javax.swing.UIManager.getFont("Label.font");
      }
      if (baseFont == null) {
        baseFont = new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12);
      }

      String pctLabel = percentUsed >= 0 ? percentUsed + "%" : "n/a";
      g2.setColor(text);
      g2.setFont(baseFont.deriveFont(java.awt.Font.BOLD, Math.max(18f, size * 0.18f)));
      FontMetrics pctMetrics = g2.getFontMetrics();
      int pctX = (getWidth() - pctMetrics.stringWidth(pctLabel)) / 2;
      int pctY = (getHeight() / 2) + (pctMetrics.getAscent() / 3);
      g2.drawString(pctLabel, pctX, pctY);

      String detailLabel =
          maxBytes > 0L ? toMib(usedBytes) + " / " + toMib(maxBytes) : toMib(usedBytes);
      g2.setFont(baseFont.deriveFont(java.awt.Font.PLAIN, Math.max(11f, size * 0.075f)));
      FontMetrics detailMetrics = g2.getFontMetrics();
      int detailX = (getWidth() - detailMetrics.stringWidth(detailLabel)) / 2;
      int detailY = pctY + detailMetrics.getHeight() + 2;
      g2.drawString(detailLabel, detailX, detailY);

      g2.setStroke(oldStroke);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
    }

    private static Color uiColor(String key, Color fallback) {
      Color c = javax.swing.UIManager.getColor(key);
      return c != null ? c : fallback;
    }
  }

  private record MemorySnapshot(
      long usedBytes, long committedBytes, long freeBytes, long maxBytes) {}

  private record IrcColorSelection(Integer foreground, Integer background) {}

  private static void applyThemeQuick(
      String themeId,
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
        runtimeConfig.rememberUiSettings(
            updated.theme(), updated.chatFontFamily(), updated.chatFontSize());
      }
      if (themeManager != null) {
        themeManager.applyTheme(next);
      }
    }
  }
}

package cafe.woden.ircclient.ui.shell;

import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@InterfaceLayer
@Lazy
public class StatusBar extends JPanel {
  public enum UpdateNotifierState {
    IDLE,
    UPDATE_AVAILABLE,
    ERROR
  }

  private static final int NOTICE_ROTATE_MS = 5500;
  private static final int NOTICE_FADE_MS = 420;
  private static final int NOTICE_FADE_TICK_MS = 35;
  private static final int MAX_NOTICE_HISTORY = 400;
  private static final int HISTORY_FLASH_TOGGLES = 6;
  private static final int HISTORY_FLASH_TICK_MS = 180;
  private static final int UPDATE_NOTIFIER_TOOLTIP_AUTO_HIDE_MS = 12_000;
  private static final double NOTICE_MIN_CONTRAST = 4.5;
  private static final DateTimeFormatter NOTICE_HISTORY_TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
  private static final String SERVER_DISCONNECTED_TEXT = "(disconnected)";

  // TODO: Make these their own individual Spring components.
  private final JLabel channelLabel = new JLabel("Channel: -");
  private final JLabel usersLabel = new JLabel("Users: 0");
  private final JLabel opsLabel = new JLabel("Ops: 0");
  private final JLabel serverLabel = new JLabel(SERVER_DISCONNECTED_TEXT);
  private final JLabel lagLabel = new JLabel("Lag: --");
  private final JLabel noticeLabel = new JLabel();
  private final JButton historyButton = new JButton("Notices");
  private final JButton updateNotifierButton = new JButton();
  private final Icon historyIcon = SvgIcons.action("info", 14);
  private final Icon updateNotifierIdleIcon = SvgIcons.action("refresh", 14);
  private final Icon updateNotifierAvailableIcon = SvgIcons.action("arrow-down", 14);
  private final Icon updateNotifierErrorIcon = SvgIcons.action("close", 14);
  private final Color historyFlashBg = new Color(255, 223, 128, 170);

  private final Deque<StatusNotice> noticeQueue = new ArrayDeque<>();
  private final List<StatusNotice> noticeHistory = new ArrayList<>();
  private final NoticeHistoryTableModel noticeHistoryModel = new NoticeHistoryTableModel();
  private StatusNotice activeNotice;
  private final Timer noticeTimer;
  private final Timer noticeFadeTimer;
  private long noticeFadeStartedAtMs;
  private final Timer historyFlashTimer;
  private int historyFlashTogglesRemaining;
  private Color noticeBaseForeground;
  private JDialog noticeHistoryDialog;
  private JTable noticeHistoryTable;
  private JButton historyOpenButton;
  private JButton historyClearSelectedButton;
  private JButton historyClearButton;
  private JPopupMenu historyPopupMenu;
  private JMenuItem historyPopupOpenItem;
  private JMenuItem historyPopupClearItem;
  private JPopupMenu updateNotifierPopupMenu;
  private JMenuItem updateNotifierCheckNowItem;
  private JMenuItem updateNotifierVisitUpdatesItem;
  private JMenuItem updateNotifierDisableItem;
  private Runnable updateNotifierCheckNowAction;
  private Runnable updateNotifierVisitAction;
  private Runnable updateNotifierDisableAction;
  private Popup updateNotifierTooltipPopup;
  private Timer updateNotifierTooltipHideTimer;
  private Color lagBaseForeground;

  private record StatusNotice(
      String displayText, String fullText, Runnable onClick, long atEpochMs) {}

  private record ServerLabelDisplay(String label, String tooltip) {}

  public StatusBar() {
    super(new BorderLayout(12, 0));
    setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
    left.add(channelLabel);
    left.add(usersLabel);
    left.add(opsLabel);

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 3));
    right.add(serverLabel);
    right.add(lagLabel);
    right.add(updateNotifierButton);

    JPanel center = new JPanel(new BorderLayout(10, 0));
    center.setOpaque(false);
    center.setBorder(new EmptyBorder(3, 0, 3, 0));
    noticeLabel.setText("");
    noticeLabel.setToolTipText(null);
    noticeLabel.setVisible(false);
    noticeLabel.setHorizontalAlignment(SwingConstants.LEFT);
    historyButton.setIcon(historyIcon);
    historyButton.setText(null);
    historyButton.setBorderPainted(false);
    historyButton.setContentAreaFilled(false);
    historyButton.setOpaque(false);
    historyButton.setFocusable(false);
    historyButton.setFocusPainted(false);
    historyButton.setMargin(new Insets(1, 8, 1, 8));
    historyButton.setToolTipText("No recent status-bar notices.");
    historyButton.setEnabled(false);
    historyButton.addActionListener(e -> openHistoryDialog());
    updateNotifierButton.setIcon(updateNotifierIdleIcon);
    updateNotifierButton.setText(null);
    updateNotifierButton.setBorderPainted(false);
    updateNotifierButton.setContentAreaFilled(false);
    updateNotifierButton.setOpaque(false);
    updateNotifierButton.setFocusable(false);
    updateNotifierButton.setFocusPainted(false);
    updateNotifierButton.setMargin(new Insets(1, 6, 1, 6));
    updateNotifierButton.setToolTipText(
        "IRCafe update notifier is disabled. Enable it from Preferences.");
    updateNotifierButton.setVisible(false);
    updateNotifierButton.addActionListener(e -> runUpdateNotifierVisitAction());
    updateNotifierButton.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            maybeShowUpdateNotifierPopup(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShowUpdateNotifierPopup(e);
          }
        });
    center.add(noticeLabel, BorderLayout.CENTER);
    center.add(historyButton, BorderLayout.EAST);
    noticeBaseForeground = resolveNoticeForeground();
    lagLabel.setToolTipText("Current measured server lag for the active server.");
    lagLabel.setVisible(false);
    lagBaseForeground = lagLabel.getForeground();

    noticeLabel.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
              openHistoryDialog();
              return;
            }
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            StatusNotice current = activeNotice;
            if (current == null) return;
            Runnable click = current.onClick();
            if (click != null) {
              try {
                click.run();
              } catch (Exception ignored) {
              }
            }
            dismissCurrentNotice();
          }
        });

    noticeTimer = new Timer(NOTICE_ROTATE_MS, e -> onNoticeTimer());
    noticeTimer.setRepeats(false);
    noticeFadeTimer = new Timer(NOTICE_FADE_TICK_MS, e -> onNoticeFadeTick());
    noticeFadeTimer.setRepeats(true);
    historyFlashTimer = new Timer(HISTORY_FLASH_TICK_MS, e -> onHistoryFlashTick());
    historyFlashTimer.setRepeats(true);

    add(left, BorderLayout.WEST);
    add(center, BorderLayout.CENTER);
    add(right, BorderLayout.EAST);

    addPropertyChangeListener(
        "UI", e -> SwingUtilities.invokeLater(this::applyUiDefaultsFromTheme));
    applyUiDefaultsFromTheme();
  }

  public void setUpdateNotifierActions(
      Runnable checkNowAction, Runnable visitAction, Runnable disableAction) {
    if (SwingUtilities.isEventDispatchThread()) {
      setUpdateNotifierActionsOnEdt(checkNowAction, visitAction, disableAction);
    } else {
      SwingUtilities.invokeLater(
          () -> setUpdateNotifierActionsOnEdt(checkNowAction, visitAction, disableAction));
    }
  }

  public void setUpdateNotifierEnabled(boolean enabled) {
    if (SwingUtilities.isEventDispatchThread()) {
      setUpdateNotifierEnabledOnEdt(enabled);
    } else {
      SwingUtilities.invokeLater(() -> setUpdateNotifierEnabledOnEdt(enabled));
    }
  }

  public void setUpdateNotifierStatus(String tooltip, UpdateNotifierState state) {
    if (SwingUtilities.isEventDispatchThread()) {
      setUpdateNotifierStatusOnEdt(tooltip, state);
    } else {
      SwingUtilities.invokeLater(() -> setUpdateNotifierStatusOnEdt(tooltip, state));
    }
  }

  public void showUpdateNotifierTooltipAlert(String text) {
    if (SwingUtilities.isEventDispatchThread()) {
      showUpdateNotifierTooltipAlertOnEdt(text);
    } else {
      SwingUtilities.invokeLater(() -> showUpdateNotifierTooltipAlertOnEdt(text));
    }
  }

  public void setUpdateNotifierChecking() {
    if (SwingUtilities.isEventDispatchThread()) {
      setUpdateNotifierCheckingOnEdt();
    } else {
      SwingUtilities.invokeLater(this::setUpdateNotifierCheckingOnEdt);
    }
  }

  public void setLagIndicatorEnabled(boolean enabled) {
    if (SwingUtilities.isEventDispatchThread()) {
      setLagIndicatorEnabledOnEdt(enabled);
    } else {
      SwingUtilities.invokeLater(() -> setLagIndicatorEnabledOnEdt(enabled));
    }
  }

  public void setLagIndicatorReading(Long lagMs, String tooltip) {
    if (SwingUtilities.isEventDispatchThread()) {
      setLagIndicatorReadingOnEdt(lagMs, tooltip);
    } else {
      SwingUtilities.invokeLater(() -> setLagIndicatorReadingOnEdt(lagMs, tooltip));
    }
  }

  public void setChannel(String channel) {
    channelLabel.setText("Channel: " + (channel == null ? "-" : channel));
  }

  public void setCounts(int users, int ops) {
    usersLabel.setText("Users: " + users);
    opsLabel.setText("Ops: " + ops);
  }

  public void setServer(String serverText) {
    ServerLabelDisplay display = serverLabelDisplay(serverText);
    serverLabel.setText(display.label());
    serverLabel.setToolTipText(display.tooltip());
  }

  /** Enqueue a status-bar notice and cycle through notices one at a time. */
  public void enqueueNotification(String text, Runnable onClick) {
    if (SwingUtilities.isEventDispatchThread()) {
      enqueueNotificationOnEdt(text, onClick);
    } else {
      SwingUtilities.invokeLater(() -> enqueueNotificationOnEdt(text, onClick));
    }
  }

  public void clearNotifications() {
    if (SwingUtilities.isEventDispatchThread()) {
      clearNotificationsOnEdt();
    } else {
      SwingUtilities.invokeLater(this::clearNotificationsOnEdt);
    }
  }

  private void enqueueNotificationOnEdt(String text, Runnable onClick) {
    String fullText = Objects.toString(text, "").trim();
    if (fullText.isEmpty()) return;
    stopNoticeFade();
    String displayText = fullText;
    if (displayText.length() > 220) {
      displayText = displayText.substring(0, 220) + "...";
    }
    StatusNotice notice =
        new StatusNotice(displayText, fullText, onClick, System.currentTimeMillis());
    noticeQueue.offerLast(notice);
    appendHistory(notice);
    triggerHistoryFlash();
    if (activeNotice == null) {
      advanceNotice();
    } else {
      refreshNoticeLabel();
    }
  }

  private void clearNotificationsOnEdt() {
    noticeQueue.clear();
    activeNotice = null;
    noticeTimer.stop();
    stopNoticeFade();
    refreshNoticeLabel();
  }

  private void onNoticeTimer() {
    if (activeNotice == null) {
      noticeTimer.stop();
      return;
    }
    if (noticeQueue.isEmpty()) {
      activeNotice = null;
      startNoticeFadeOut();
      noticeTimer.stop();
      return;
    }
    advanceNotice();
  }

  private void dismissCurrentNotice() {
    if (activeNotice == null) return;
    if (noticeQueue.isEmpty()) {
      activeNotice = null;
      stopNoticeFade();
      refreshNoticeLabel();
      noticeTimer.stop();
      return;
    }
    advanceNotice();
  }

  private void advanceNotice() {
    stopNoticeFade();
    activeNotice = noticeQueue.pollFirst();
    refreshNoticeLabel();
    if (activeNotice == null) {
      noticeTimer.stop();
      return;
    }
    noticeTimer.restart();
  }

  private void refreshNoticeLabel() {
    noticeBaseForeground = resolveNoticeForeground();
    if (activeNotice == null) {
      noticeLabel.setText("");
      noticeLabel.setToolTipText(null);
      noticeLabel.setVisible(false);
      applyNoticeForegroundWithAlpha(255);
      noticeLabel.setCursor(Cursor.getDefaultCursor());
      return;
    }

    int pending = noticeQueue.size();
    String base = activeNotice.displayText();
    if (pending > 0) {
      base = base + " (+" + pending + ")";
    }
    noticeLabel.setText(base);
    String tooltip = activeNotice.fullText();
    if (activeNotice.onClick() != null) {
      tooltip = tooltip + "  (left-click to open)";
    }
    tooltip = tooltip + "  (right-click for history)";
    noticeLabel.setToolTipText(tooltip);
    noticeLabel.setVisible(true);
    applyNoticeForegroundWithAlpha(255);
    noticeLabel.setCursor(
        activeNotice.onClick() != null
            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            : Cursor.getDefaultCursor());
  }

  private void startNoticeFadeOut() {
    if (noticeLabel.getText() == null || noticeLabel.getText().isBlank()) {
      refreshNoticeLabel();
      return;
    }
    if (!noticeLabel.isShowing()) {
      // Skip animation work while the status bar isn't visible.
      refreshNoticeLabel();
      return;
    }
    noticeBaseForeground = resolveNoticeForeground();
    noticeFadeStartedAtMs = System.currentTimeMillis();
    noticeFadeTimer.restart();
  }

  private void stopNoticeFade() {
    noticeFadeTimer.stop();
    noticeBaseForeground = resolveNoticeForeground();
    applyNoticeForegroundWithAlpha(255);
  }

  private void onNoticeFadeTick() {
    if (noticeLabel.getText() == null || noticeLabel.getText().isBlank()) {
      noticeFadeTimer.stop();
      return;
    }
    if (!noticeLabel.isShowing()) {
      noticeFadeTimer.stop();
      refreshNoticeLabel();
      return;
    }
    long elapsed = Math.max(0L, System.currentTimeMillis() - noticeFadeStartedAtMs);
    float progress = Math.min(1f, elapsed / (float) NOTICE_FADE_MS);
    if (progress >= 1f) {
      noticeFadeTimer.stop();
      refreshNoticeLabel();
      return;
    }

    Color base = noticeBaseForeground != null ? noticeBaseForeground : resolveNoticeForeground();
    int alpha = Math.max(0, Math.min(255, Math.round((1f - progress) * 255f)));
    noticeLabel.setForeground(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
  }

  private void appendHistory(StatusNotice notice) {
    if (notice == null) return;
    noticeHistory.add(notice);
    int overflow = noticeHistory.size() - MAX_NOTICE_HISTORY;
    if (overflow > 0) {
      noticeHistory.subList(0, overflow).clear();
    }
    noticeHistoryModel.fireTableDataChanged();
    refreshHistoryButton();
    updateHistoryDialogButtons();
    if (noticeHistoryTable != null && noticeHistoryTable.getRowCount() > 0) {
      int last = noticeHistoryTable.getRowCount() - 1;
      noticeHistoryTable.getSelectionModel().setSelectionInterval(last, last);
      noticeHistoryTable.scrollRectToVisible(noticeHistoryTable.getCellRect(last, 0, true));
    }
  }

  private void clearHistoryOnEdt() {
    noticeHistory.clear();
    noticeHistoryModel.fireTableDataChanged();
    refreshHistoryButton();
    updateHistoryDialogButtons();
  }

  private void clearSelectedHistoryEntry() {
    int modelRow = selectedHistoryModelRow();
    if (modelRow < 0 || modelRow >= noticeHistory.size()) return;
    noticeHistory.remove(modelRow);
    noticeHistoryModel.fireTableDataChanged();
    refreshHistoryButton();
    updateHistoryDialogButtons();

    if (noticeHistoryTable != null && !noticeHistory.isEmpty()) {
      int nextModel = Math.min(modelRow, noticeHistory.size() - 1);
      int nextView = nextModel;
      try {
        nextView = noticeHistoryTable.convertRowIndexToView(nextModel);
      } catch (Exception ignored) {
      }
      if (nextView >= 0 && nextView < noticeHistoryTable.getRowCount()) {
        noticeHistoryTable.getSelectionModel().setSelectionInterval(nextView, nextView);
      }
    }
  }

  private void refreshHistoryButton() {
    int count = noticeHistory.size();
    if (count <= 0) {
      historyButton.setEnabled(false);
      historyButton.setToolTipText("No recent status-bar notices.");
      return;
    }
    historyButton.setEnabled(true);
    String badge = count > 999 ? "999+" : Integer.toString(count);
    historyButton.setToolTipText("Show recent status-bar notices (" + badge + ").");
  }

  private void openHistoryDialog() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::openHistoryDialog);
      return;
    }
    if (noticeHistoryDialog == null) {
      createHistoryDialog();
    }
    updateHistoryDialogButtons();
    noticeHistoryDialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
    noticeHistoryDialog.setVisible(true);
    noticeHistoryDialog.toFront();
  }

  private void createHistoryDialog() {
    Window owner = SwingUtilities.getWindowAncestor(this);
    noticeHistoryDialog =
        owner instanceof Frame frame
            ? new JDialog(frame, "Status Notices", false)
            : new JDialog((Frame) null, "Status Notices", false);
    noticeHistoryDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

    noticeHistoryTable = new JTable(noticeHistoryModel);
    noticeHistoryTable.setFillsViewportHeight(true);
    noticeHistoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    noticeHistoryTable.setAutoCreateRowSorter(true);
    noticeHistoryTable.setRowHeight(Math.max(22, noticeHistoryTable.getRowHeight()));
    noticeHistoryTable.getColumnModel().getColumn(0).setPreferredWidth(160);
    noticeHistoryTable.getColumnModel().getColumn(0).setMaxWidth(190);
    noticeHistoryTable.getColumnModel().getColumn(2).setPreferredWidth(70);
    noticeHistoryTable.getColumnModel().getColumn(2).setMaxWidth(90);
    noticeHistoryTable
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e != null && e.getValueIsAdjusting()) return;
              updateHistoryDialogButtons();
            });
    noticeHistoryTable.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
              runSelectedHistoryAction();
            }
          }

          @Override
          public void mousePressed(MouseEvent e) {
            maybeShowHistoryPopup(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShowHistoryPopup(e);
          }
        });

    historyOpenButton = new JButton("Open Selected");
    historyOpenButton.addActionListener(e -> runSelectedHistoryAction());

    historyClearSelectedButton = new JButton("Clear Selected");
    historyClearSelectedButton.addActionListener(e -> clearSelectedHistoryEntry());

    historyClearButton = new JButton("Clear History");
    historyClearButton.addActionListener(e -> clearHistoryOnEdt());

    JButton closeButton = new JButton("Close");
    closeButton.addActionListener(e -> noticeHistoryDialog.setVisible(false));

    historyPopupMenu = new JPopupMenu();
    historyPopupOpenItem = new JMenuItem("Open Selected");
    historyPopupOpenItem.addActionListener(e -> runSelectedHistoryAction());
    historyPopupClearItem = new JMenuItem("Clear Selected");
    historyPopupClearItem.addActionListener(e -> clearSelectedHistoryEntry());
    historyPopupMenu.add(historyPopupOpenItem);
    historyPopupMenu.add(historyPopupClearItem);

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    controls.add(historyClearButton);
    controls.add(historyClearSelectedButton);
    controls.add(historyOpenButton);
    controls.add(closeButton);

    JPanel root = new JPanel(new BorderLayout(8, 8));
    root.setBorder(new EmptyBorder(10, 10, 10, 10));
    root.add(new JScrollPane(noticeHistoryTable), BorderLayout.CENTER);
    root.add(controls, BorderLayout.SOUTH);

    noticeHistoryDialog.setContentPane(root);
    noticeHistoryDialog.setSize(900, 340);
    applyUiFontsFromDefaults();
  }

  private StatusNotice selectedHistoryNotice() {
    int modelRow = selectedHistoryModelRow();
    if (modelRow < 0 || modelRow >= noticeHistory.size()) return null;
    return noticeHistory.get(modelRow);
  }

  private int selectedHistoryModelRow() {
    if (noticeHistoryTable == null) return -1;
    int viewRow = noticeHistoryTable.getSelectedRow();
    if (viewRow < 0) return -1;
    int modelRow = viewRow;
    try {
      modelRow = noticeHistoryTable.convertRowIndexToModel(viewRow);
    } catch (Exception ignored) {
    }
    return modelRow;
  }

  private void maybeShowHistoryPopup(MouseEvent e) {
    if (e == null || !e.isPopupTrigger()) return;
    if (noticeHistoryTable == null || historyPopupMenu == null) return;

    int viewRow = noticeHistoryTable.rowAtPoint(e.getPoint());
    if (viewRow >= 0 && viewRow < noticeHistoryTable.getRowCount()) {
      noticeHistoryTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
    }
    updateHistoryDialogButtons();
    if (historyPopupOpenItem != null) {
      StatusNotice selected = selectedHistoryNotice();
      historyPopupOpenItem.setEnabled(selected != null && selected.onClick() != null);
    }
    if (historyPopupClearItem != null) {
      historyPopupClearItem.setEnabled(selectedHistoryNotice() != null);
    }
    PopupMenuThemeSupport.prepareForDisplay(historyPopupMenu);
    historyPopupMenu.show(noticeHistoryTable, e.getX(), e.getY());
  }

  private void triggerHistoryFlash() {
    if (!historyButton.isShowing()) return;
    // Top up flash duration without constantly restarting the timer for bursty events.
    historyFlashTogglesRemaining = Math.max(historyFlashTogglesRemaining, HISTORY_FLASH_TOGGLES);
    if (!historyFlashTimer.isRunning()) {
      applyHistoryFlashStyle(true);
      historyFlashTimer.start();
    }
  }

  private void onHistoryFlashTick() {
    if (historyFlashTogglesRemaining <= 0) {
      historyFlashTimer.stop();
      applyHistoryFlashStyle(false);
      return;
    }
    boolean on = (historyFlashTogglesRemaining % 2) == 0;
    applyHistoryFlashStyle(on);
    historyFlashTogglesRemaining--;
  }

  private void applyHistoryFlashStyle(boolean on) {
    if (on) {
      historyButton.setOpaque(true);
      historyButton.setContentAreaFilled(true);
      historyButton.setBorderPainted(true);
      historyButton.setBackground(historyFlashBg);
    } else {
      historyButton.setOpaque(false);
      historyButton.setContentAreaFilled(false);
      historyButton.setBorderPainted(false);
      historyButton.setBackground(null);
    }
  }

  private void runSelectedHistoryAction() {
    StatusNotice selected = selectedHistoryNotice();
    if (selected == null) return;
    Runnable click = selected.onClick();
    if (click == null) return;
    try {
      click.run();
    } catch (Exception ignored) {
    }
  }

  private void updateHistoryDialogButtons() {
    if (historyClearButton != null) {
      historyClearButton.setEnabled(!noticeHistory.isEmpty());
    }
    if (historyClearSelectedButton != null) {
      historyClearSelectedButton.setEnabled(selectedHistoryNotice() != null);
    }
    if (historyOpenButton != null) {
      StatusNotice selected = selectedHistoryNotice();
      historyOpenButton.setEnabled(selected != null && selected.onClick() != null);
    }
  }

  private void applyUiDefaultsFromTheme() {
    applyUiFontsFromDefaults();
    refreshThemeAwareColors();
  }

  private void refreshThemeAwareColors() {
    noticeBaseForeground = resolveNoticeForeground();
    applyNoticeForegroundWithAlpha(currentNoticeAlpha());
    lagBaseForeground = statusTextForeground();
    if (lagBaseForeground != null && lagLabel.isVisible()) {
      lagLabel.setForeground(lagBaseForeground);
    }
  }

  private void applyUiFontsFromDefaults() {
    Font base = UIManager.getFont("Label.font");
    Font defaultFont = UIManager.getFont("defaultFont");
    if (defaultFont != null && (base == null || defaultFont.getSize2D() > base.getSize2D())) {
      base = defaultFont;
    }
    if (base == null) return;

    Font buttonFont = UIManager.getFont("Button.font");
    if (buttonFont == null) buttonFont = base;
    Font tableFont = UIManager.getFont("Table.font");
    if (tableFont == null) tableFont = base;
    Font headerFont = UIManager.getFont("TableHeader.font");
    if (headerFont == null) headerFont = buttonFont;

    setFont(base);
    channelLabel.setFont(base);
    usersLabel.setFont(base);
    opsLabel.setFont(base);
    serverLabel.setFont(base);
    lagLabel.setFont(base);
    noticeLabel.setFont(base);
    historyButton.setFont(buttonFont);
    updateNotifierButton.setFont(buttonFont);

    if (noticeHistoryTable != null) {
      noticeHistoryTable.setFont(tableFont);
      JTableHeader header = noticeHistoryTable.getTableHeader();
      if (header != null) header.setFont(headerFont);
      int rowHeight = Math.max(22, Math.round(tableFont.getSize2D() * 1.75f));
      noticeHistoryTable.setRowHeight(rowHeight);
    }
    if (historyOpenButton != null) historyOpenButton.setFont(buttonFont);
    if (historyClearSelectedButton != null) historyClearSelectedButton.setFont(buttonFont);
    if (historyClearButton != null) historyClearButton.setFont(buttonFont);
    if (historyPopupOpenItem != null) historyPopupOpenItem.setFont(buttonFont);
    if (historyPopupClearItem != null) historyPopupClearItem.setFont(buttonFont);
    if (updateNotifierCheckNowItem != null) updateNotifierCheckNowItem.setFont(buttonFont);
    if (updateNotifierVisitUpdatesItem != null) updateNotifierVisitUpdatesItem.setFont(buttonFont);
    if (updateNotifierDisableItem != null) updateNotifierDisableItem.setFont(buttonFont);
  }

  private int currentNoticeAlpha() {
    Color current = noticeLabel.getForeground();
    return current != null ? current.getAlpha() : 255;
  }

  private void applyNoticeForegroundWithAlpha(int alpha) {
    Color base = noticeBaseForeground != null ? noticeBaseForeground : resolveNoticeForeground();
    if (base == null) return;
    noticeLabel.setForeground(
        new Color(base.getRed(), base.getGreen(), base.getBlue(), clampAlpha(alpha)));
  }

  private void setUpdateNotifierActionsOnEdt(
      Runnable checkNowAction, Runnable visitAction, Runnable disableAction) {
    this.updateNotifierCheckNowAction = checkNowAction;
    this.updateNotifierVisitAction = visitAction;
    this.updateNotifierDisableAction = disableAction;
    ensureUpdateNotifierPopupMenu();
  }

  private void setLagIndicatorEnabledOnEdt(boolean enabled) {
    lagBaseForeground = statusTextForeground();
    if (!enabled) {
      lagLabel.setVisible(false);
      lagLabel.setText("Lag: --");
      lagLabel.setToolTipText(null);
      lagLabel.setForeground(lagBaseForeground);
      return;
    }
    lagLabel.setVisible(true);
    if (lagLabel.getToolTipText() == null || lagLabel.getToolTipText().isBlank()) {
      lagLabel.setToolTipText("Current measured server lag for the active server.");
    }
  }

  private void setLagIndicatorReadingOnEdt(Long lagMs, String tooltip) {
    setLagIndicatorEnabledOnEdt(true);
    lagBaseForeground = statusTextForeground();
    lagLabel.setForeground(lagBaseForeground);

    if (lagMs == null || lagMs < 0L) {
      lagLabel.setText("Lag: --");
    } else {
      long value = Math.max(0L, lagMs);
      lagLabel.setText("Lag: " + formatLag(value));
    }

    String tip = Objects.toString(tooltip, "").trim();
    if (!tip.isEmpty()) {
      lagLabel.setToolTipText(tip);
    }
  }

  private static String formatLag(long lagMs) {
    if (lagMs < 1000L) {
      return lagMs + " ms";
    }
    double seconds = lagMs / 1000.0d;
    return String.format(java.util.Locale.ROOT, "%.1f s", seconds);
  }

  private void setUpdateNotifierEnabledOnEdt(boolean enabled) {
    if (!enabled) {

      updateNotifierButton.setVisible(false);
      hideUpdateNotifierTooltipPopupOnEdt();
      return;
    }
    updateNotifierButton.setVisible(true);
    if (updateNotifierButton.getToolTipText() == null
        || updateNotifierButton.getToolTipText().isBlank()) {
      updateNotifierButton.setToolTipText("Checking for IRCafe updates…");
    }
  }

  private void setUpdateNotifierStatusOnEdt(String tooltip, UpdateNotifierState state) {
    setUpdateNotifierEnabledOnEdt(true);
    UpdateNotifierState nextState = state != null ? state : UpdateNotifierState.IDLE;
    boolean updateAvailable = nextState == UpdateNotifierState.UPDATE_AVAILABLE;

    updateNotifierButton.setIcon(
        switch (nextState) {
          case UPDATE_AVAILABLE -> updateNotifierAvailableIcon;
          case ERROR -> updateNotifierErrorIcon;
          default -> updateNotifierIdleIcon;
        });
    String tip = Objects.toString(tooltip, "").trim();
    if (!tip.isEmpty()) {
      updateNotifierButton.setToolTipText(tip);
    }
    ensureUpdateNotifierPopupMenu();
    if (updateNotifierVisitUpdatesItem != null) {
      updateNotifierVisitUpdatesItem.setText(
          updateAvailable ? "Visit updates (new version available)" : "Visit updates");
    }
    if (updateNotifierCheckNowItem != null) {
      updateNotifierCheckNowItem.setEnabled(updateNotifierCheckNowAction != null);
    }
  }

  private void setUpdateNotifierCheckingOnEdt() {
    setUpdateNotifierEnabledOnEdt(true);
    updateNotifierButton.setToolTipText("Checking for IRCafe updates...");
    if (updateNotifierCheckNowItem != null) {
      updateNotifierCheckNowItem.setEnabled(updateNotifierCheckNowAction != null);
    }
  }

  private void showUpdateNotifierTooltipAlertOnEdt(String text) {
    if (!updateNotifierButton.isShowing()) return;
    String tip = Objects.toString(text, "").trim();
    if (tip.isEmpty()) return;
    hideUpdateNotifierTooltipPopupOnEdt();

    JToolTip tooltip = createUpdateNotifierTooltipAlertOnEdt(tip);
    Point p = updateNotifierButton.getLocationOnScreen();
    int x = p.x + Math.max(4, updateNotifierButton.getWidth() / 2 - 150);
    int y = p.y - 32;
    updateNotifierTooltipPopup =
        PopupFactory.getSharedInstance().getPopup(updateNotifierButton, tooltip, x, y);
    updateNotifierTooltipPopup.show();

    ensureUpdateNotifierTooltipHideTimerOnEdt();
    updateNotifierTooltipHideTimer.restart();
  }

  private JToolTip createUpdateNotifierTooltipAlertOnEdt(String tip) {
    JToolTip tooltip = new JToolTip();
    tooltip.setTipText(tip);
    tooltip.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            hideUpdateNotifierTooltipPopupOnEdt();
          }
        });
    return tooltip;
  }

  private void ensureUpdateNotifierTooltipHideTimerOnEdt() {
    if (updateNotifierTooltipHideTimer != null) return;
    updateNotifierTooltipHideTimer =
        new Timer(UPDATE_NOTIFIER_TOOLTIP_AUTO_HIDE_MS, e -> hideUpdateNotifierTooltipPopupOnEdt());
    updateNotifierTooltipHideTimer.setRepeats(false);
  }

  private void hideUpdateNotifierTooltipPopupOnEdt() {
    if (updateNotifierTooltipHideTimer != null) {
      updateNotifierTooltipHideTimer.stop();
    }
    if (updateNotifierTooltipPopup != null) {
      try {
        updateNotifierTooltipPopup.hide();
      } catch (Exception ignored) {
      }
      updateNotifierTooltipPopup = null;
    }
  }

  private void ensureUpdateNotifierPopupMenu() {
    if (updateNotifierPopupMenu != null) return;
    updateNotifierPopupMenu = new JPopupMenu();
    updateNotifierCheckNowItem = new JMenuItem("Check now");
    updateNotifierCheckNowItem.addActionListener(e -> runUpdateNotifierCheckNowAction());
    updateNotifierVisitUpdatesItem = new JMenuItem("Visit updates");
    updateNotifierVisitUpdatesItem.addActionListener(e -> runUpdateNotifierVisitAction());
    updateNotifierDisableItem = new JMenuItem("Disable update notifier");
    updateNotifierDisableItem.addActionListener(e -> runUpdateNotifierDisableAction());
    updateNotifierPopupMenu.add(updateNotifierCheckNowItem);
    updateNotifierPopupMenu.addSeparator();
    updateNotifierPopupMenu.add(updateNotifierVisitUpdatesItem);
    updateNotifierPopupMenu.add(updateNotifierDisableItem);
  }

  private void maybeShowUpdateNotifierPopup(MouseEvent e) {
    if (e == null || !e.isPopupTrigger()) return;
    ensureUpdateNotifierPopupMenu();
    if (updateNotifierPopupMenu == null) return;
    PopupMenuThemeSupport.prepareForDisplay(updateNotifierPopupMenu);
    updateNotifierPopupMenu.show(updateNotifierButton, e.getX(), e.getY());
  }

  private void runUpdateNotifierVisitAction() {
    Runnable action = updateNotifierVisitAction;
    if (action == null) return;
    try {
      action.run();
    } catch (Exception ignored) {
    }
  }

  private void runUpdateNotifierCheckNowAction() {
    Runnable action = updateNotifierCheckNowAction;
    if (action == null) return;
    try {
      action.run();
    } catch (Exception ignored) {
    }
  }

  private void runUpdateNotifierDisableAction() {
    Runnable action = updateNotifierDisableAction;
    if (action == null) return;
    try {
      action.run();
    } catch (Exception ignored) {
    }
  }

  private Color statusTextForeground() {
    Color serverForeground = serverLabel.getForeground();
    if (serverForeground != null) return serverForeground;
    return lagLabel.getForeground();
  }

  private Color resolveNoticeForeground() {
    Color bg = statusBarBackground();
    boolean darkUi = isDarkUi(bg);
    Color fallback = darkUi ? new Color(0xDE, 0xE8, 0xF8) : new Color(0x15, 0x4A, 0x8A);
    Color candidate =
        firstNonNull(
            UIManager.getColor("Component.linkColor"),
            UIManager.getColor("@accentColor"),
            UIManager.getColor("Component.accentColor"),
            statusTextForeground(),
            UIManager.getColor("Label.foreground"));
    return ensureReadableTextColor(candidate, bg, darkUi, NOTICE_MIN_CONTRAST, fallback);
  }

  private Color statusBarBackground() {
    Color bg = getBackground();
    if (bg == null) bg = UIManager.getColor("Panel.background");
    if (bg == null) bg = noticeLabel.getBackground();
    return bg != null ? new Color(bg.getRed(), bg.getGreen(), bg.getBlue()) : Color.WHITE;
  }

  private static Color ensureReadableTextColor(
      Color candidate, Color bg, boolean darkUi, double minContrast, Color fallback) {
    if (bg == null) return candidate != null ? candidate : fallback;

    Color base = candidate != null ? candidate : fallback;
    if (base == null) base = darkUi ? Color.WHITE : Color.BLACK;

    if (isReadableDirection(base, bg, darkUi) && contrastRatio(base, bg) >= minContrast) {
      return base;
    }

    Color target = darkUi ? Color.WHITE : Color.BLACK;
    for (int i = 1; i <= 24; i++) {
      double weight = i / 24.0;
      Color adjusted = mix(target, base, weight);
      if (isReadableDirection(adjusted, bg, darkUi) && contrastRatio(adjusted, bg) >= minContrast) {
        return adjusted;
      }
    }

    return target;
  }

  private static boolean isReadableDirection(Color fg, Color bg, boolean darkUi) {
    if (fg == null || bg == null) return true;
    double fgLum = relativeLuminance(fg);
    double bgLum = relativeLuminance(bg);
    return darkUi ? fgLum > bgLum : fgLum < bgLum;
  }

  private static boolean isDarkUi(Color bg) {
    if (bg == null) return false;
    return relativeLuminance(bg) < 0.45;
  }

  private static Color mix(Color a, Color b, double t) {
    if (a == null) return b;
    if (b == null) return a;

    double ratio = Math.max(0.0, Math.min(1.0, t));
    int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * ratio);
    int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * ratio);
    int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * ratio);
    return new Color(r, g, bl);
  }

  private static double contrastRatio(Color fg, Color bg) {
    if (fg == null || bg == null) return 0.0;

    double l1 = relativeLuminance(fg);
    double l2 = relativeLuminance(bg);
    if (l1 < l2) {
      double t = l1;
      l1 = l2;
      l2 = t;
    }
    return (l1 + 0.05) / (l2 + 0.05);
  }

  private static double relativeLuminance(Color c) {
    double r = srgbToLinear(c.getRed());
    double g = srgbToLinear(c.getGreen());
    double b = srgbToLinear(c.getBlue());
    return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
  }

  private static double srgbToLinear(int channel) {
    double v = channel / 255.0;
    return (v <= 0.04045) ? (v / 12.92) : Math.pow((v + 0.055) / 1.055, 2.4);
  }

  @SafeVarargs
  private static <T> T firstNonNull(T... values) {
    if (values == null) return null;
    for (T value : values) {
      if (value != null) return value;
    }
    return null;
  }

  private static int clampAlpha(int alpha) {
    return Math.max(0, Math.min(255, alpha));
  }

  private static ServerLabelDisplay serverLabelDisplay(String serverText) {
    String normalized = Objects.toString(serverText, "").trim();
    if (normalized.startsWith("Server:")) {
      normalized = normalized.substring("Server:".length()).trim();
    }
    if (normalized.isEmpty()) {
      return new ServerLabelDisplay(SERVER_DISCONNECTED_TEXT, null);
    }

    if (normalized.endsWith(")")) {
      int open = normalized.lastIndexOf(" (");
      if (open <= 0) {
        open = normalized.lastIndexOf('(');
      }
      if (open > 0 && open < normalized.length() - 1) {
        String label = normalized.substring(0, open).trim();
        String detail = normalized.substring(open + 1, normalized.length() - 1).trim();
        if (detail.startsWith("(")) {
          detail = detail.substring(1).trim();
        }
        if (!label.isEmpty() && !detail.isEmpty()) {
          return new ServerLabelDisplay(label, detail);
        }
      }
    }

    return new ServerLabelDisplay(normalized, null);
  }

  private final class NoticeHistoryTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = new String[] {"Time", "Notice", "Action"};

    @Override
    public int getRowCount() {
      return noticeHistory.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
      if (column < 0 || column >= COLUMNS.length) return "";
      return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < 0 || rowIndex >= noticeHistory.size()) return null;
      StatusNotice n = noticeHistory.get(rowIndex);
      return switch (columnIndex) {
        case 0 -> NOTICE_HISTORY_TIME_FMT.format(Instant.ofEpochMilli(n.atEpochMs()));
        case 1 -> n.fullText();
        case 2 -> n.onClick() != null ? "Open" : "";
        default -> "";
      };
    }
  }
}

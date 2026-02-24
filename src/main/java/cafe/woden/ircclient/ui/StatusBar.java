package cafe.woden.ircclient.ui;

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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class StatusBar extends JPanel {
  private static final int NOTICE_ROTATE_MS = 5500;
  private static final int NOTICE_FADE_MS = 420;
  private static final int NOTICE_FADE_TICK_MS = 35;
  private static final int MAX_NOTICE_HISTORY = 400;
  private static final int HISTORY_FLASH_TOGGLES = 6;
  private static final int HISTORY_FLASH_TICK_MS = 180;
  private static final DateTimeFormatter NOTICE_HISTORY_TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  // TODO: Make these their own individual Spring components.
  private final JLabel channelLabel = new JLabel("Channel: -");
  private final JLabel usersLabel = new JLabel("Users: 0");
  private final JLabel opsLabel = new JLabel("Ops: 0");
  private final JLabel serverLabel = new JLabel("Server: (disconnected)");
  private final JLabel noticeLabel = new JLabel();
  private final JButton historyButton = new JButton("Notices");
  private final Icon historyIcon = SvgIcons.action("info", 14);
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

  private record StatusNotice(
      String displayText, String fullText, Runnable onClick, long atEpochMs) {}

  public StatusBar() {
    super(new BorderLayout(12, 0));
    setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
    left.add(channelLabel);
    left.add(usersLabel);
    left.add(opsLabel);

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 3));
    right.add(serverLabel);

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
    center.add(noticeLabel, BorderLayout.CENTER);
    center.add(historyButton, BorderLayout.EAST);
    noticeBaseForeground = noticeLabel.getForeground();

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
        "UI", e -> SwingUtilities.invokeLater(this::applyUiFontsFromDefaults));
    applyUiFontsFromDefaults();
  }

  public void setChannel(String channel) {
    channelLabel.setText("Channel: " + (channel == null ? "-" : channel));
  }

  public void setCounts(int users, int ops) {
    usersLabel.setText("Users: " + users);
    opsLabel.setText("Ops: " + ops);
  }

  public void setServer(String serverText) {
    serverLabel.setText("Server: " + (serverText == null ? "(disconnected)" : serverText));
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
    if (noticeBaseForeground == null) {
      noticeBaseForeground = noticeLabel.getForeground();
    }
    if (activeNotice == null) {
      noticeLabel.setText("");
      noticeLabel.setToolTipText(null);
      noticeLabel.setVisible(false);
      noticeLabel.setForeground(noticeBaseForeground);
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
    noticeLabel.setForeground(noticeBaseForeground);
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
    if (noticeBaseForeground == null) {
      noticeBaseForeground = noticeLabel.getForeground();
    }
    noticeFadeStartedAtMs = System.currentTimeMillis();
    noticeFadeTimer.restart();
  }

  private void stopNoticeFade() {
    noticeFadeTimer.stop();
    if (noticeBaseForeground == null) {
      noticeBaseForeground = noticeLabel.getForeground();
    }
    if (noticeBaseForeground != null) {
      noticeLabel.setForeground(noticeBaseForeground);
    }
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

    Color base = noticeBaseForeground != null ? noticeBaseForeground : noticeLabel.getForeground();
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
    noticeLabel.setFont(base);
    historyButton.setFont(buttonFont);

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

package cafe.woden.ircclient.ui;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@Lazy
public class StatusBar extends JPanel {
  private static final int NOTICE_ROTATE_MS = 5500;
  private static final int MAX_NOTICE_HISTORY = 400;
  private static final DateTimeFormatter NOTICE_HISTORY_TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  // TODO: Make these their own individual Spring components.
  private final JLabel channelLabel = new JLabel("Channel: -");
  private final JLabel usersLabel   = new JLabel("Users: 0");
  private final JLabel opsLabel     = new JLabel("Ops: 0");
  private final JLabel serverLabel  = new JLabel("Server: (disconnected)");
  private final JLabel noticeLabel  = new JLabel();
  private final JButton historyButton = new JButton("Notices");

  private final Deque<StatusNotice> noticeQueue = new ArrayDeque<>();
  private final List<StatusNotice> noticeHistory = new ArrayList<>();
  private final NoticeHistoryTableModel noticeHistoryModel = new NoticeHistoryTableModel();
  private StatusNotice activeNotice;
  private final Timer noticeTimer;
  private JDialog noticeHistoryDialog;
  private JTable noticeHistoryTable;
  private JButton historyOpenButton;
  private JButton historyClearButton;

  private record StatusNotice(String displayText, String fullText, Runnable onClick, long atEpochMs) {}

  public StatusBar() {
    super(new BorderLayout(12, 0));
    setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
    left.add(channelLabel);
    left.add(usersLabel);
    left.add(opsLabel);

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 3));
    right.add(serverLabel);

    JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
    center.setOpaque(false);
    noticeLabel.setText("");
    noticeLabel.setToolTipText(null);
    noticeLabel.setVisible(false);
    historyButton.setFocusable(false);
    historyButton.setToolTipText("Show recent status-bar notices.");
    historyButton.setEnabled(false);
    historyButton.addActionListener(e -> openHistoryDialog());
    center.add(noticeLabel);
    center.add(historyButton);

    noticeLabel.addMouseListener(new MouseAdapter() {
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

    add(left, BorderLayout.WEST);
    add(center, BorderLayout.CENTER);
    add(right, BorderLayout.EAST);

    setPreferredSize(new Dimension(10, 26));
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

  /**
   * Enqueue a status-bar notice and cycle through notices one at a time.
   */
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
    String displayText = fullText;
    if (displayText.length() > 220) {
      displayText = displayText.substring(0, 220) + "...";
    }
    StatusNotice notice = new StatusNotice(displayText, fullText, onClick, System.currentTimeMillis());
    noticeQueue.offerLast(notice);
    appendHistory(notice);
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
    refreshNoticeLabel();
  }

  private void onNoticeTimer() {
    if (activeNotice == null) {
      noticeTimer.stop();
      return;
    }
    if (noticeQueue.isEmpty()) {
      activeNotice = null;
      refreshNoticeLabel();
      noticeTimer.stop();
      return;
    }
    advanceNotice();
  }

  private void dismissCurrentNotice() {
    if (activeNotice == null) return;
    if (noticeQueue.isEmpty()) {
      activeNotice = null;
      refreshNoticeLabel();
      noticeTimer.stop();
      return;
    }
    advanceNotice();
  }

  private void advanceNotice() {
    activeNotice = noticeQueue.pollFirst();
    refreshNoticeLabel();
    if (activeNotice == null) {
      noticeTimer.stop();
      return;
    }
    noticeTimer.restart();
  }

  private void refreshNoticeLabel() {
    if (activeNotice == null) {
      noticeLabel.setText("");
      noticeLabel.setToolTipText(null);
      noticeLabel.setVisible(false);
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
    noticeLabel.setCursor(activeNotice.onClick() != null
        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        : Cursor.getDefaultCursor());
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

  private void refreshHistoryButton() {
    int count = noticeHistory.size();
    if (count <= 0) {
      historyButton.setText("Notices");
      historyButton.setEnabled(false);
      return;
    }
    String badge = count > 999 ? "999+" : Integer.toString(count);
    historyButton.setText("Notices (" + badge + ")");
    historyButton.setEnabled(true);
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
    noticeHistoryTable.getSelectionModel().addListSelectionListener(e -> {
      if (e != null && e.getValueIsAdjusting()) return;
      updateHistoryDialogButtons();
    });
    noticeHistoryTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
          runSelectedHistoryAction();
        }
      }
    });

    historyOpenButton = new JButton("Open Selected");
    historyOpenButton.addActionListener(e -> runSelectedHistoryAction());

    historyClearButton = new JButton("Clear History");
    historyClearButton.addActionListener(e -> clearHistoryOnEdt());

    JButton closeButton = new JButton("Close");
    closeButton.addActionListener(e -> noticeHistoryDialog.setVisible(false));

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    controls.add(historyClearButton);
    controls.add(historyOpenButton);
    controls.add(closeButton);

    JPanel root = new JPanel(new BorderLayout(8, 8));
    root.setBorder(new EmptyBorder(10, 10, 10, 10));
    root.add(new JScrollPane(noticeHistoryTable), BorderLayout.CENTER);
    root.add(controls, BorderLayout.SOUTH);

    noticeHistoryDialog.setContentPane(root);
    noticeHistoryDialog.setSize(900, 340);
  }

  private StatusNotice selectedHistoryNotice() {
    if (noticeHistoryTable == null) return null;
    int viewRow = noticeHistoryTable.getSelectedRow();
    if (viewRow < 0) return null;
    int modelRow = viewRow;
    try {
      modelRow = noticeHistoryTable.convertRowIndexToModel(viewRow);
    } catch (Exception ignored) {
    }
    if (modelRow < 0 || modelRow >= noticeHistory.size()) return null;
    return noticeHistory.get(modelRow);
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
    if (historyOpenButton != null) {
      StatusNotice selected = selectedHistoryNotice();
      historyOpenButton.setEnabled(selected != null && selected.onClick() != null);
    }
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

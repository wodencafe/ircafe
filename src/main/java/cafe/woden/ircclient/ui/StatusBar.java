package cafe.woden.ircclient.ui;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

@Component
@Lazy
public class StatusBar extends JPanel {
  private static final int NOTICE_ROTATE_MS = 5500;

  // TODO: Make these their own individual Spring components.
  private final JLabel channelLabel = new JLabel("Channel: -");
  private final JLabel usersLabel   = new JLabel("Users: 0");
  private final JLabel opsLabel     = new JLabel("Ops: 0");
  private final JLabel serverLabel  = new JLabel("Server: (disconnected)");
  private final JLabel noticeLabel  = new JLabel();

  private final Deque<StatusNotice> noticeQueue = new ArrayDeque<>();
  private StatusNotice activeNotice;
  private final Timer noticeTimer;

  private record StatusNotice(String text, Runnable onClick) {}

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
    center.add(noticeLabel);

    noticeLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
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
    String normalized = Objects.toString(text, "").trim();
    if (normalized.isEmpty()) return;
    if (normalized.length() > 220) {
      normalized = normalized.substring(0, 220) + "...";
    }
    noticeQueue.offerLast(new StatusNotice(normalized, onClick));
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
    String base = activeNotice.text();
    if (pending > 0) {
      base = base + " (+" + pending + ")";
    }
    noticeLabel.setText(base);
    noticeLabel.setToolTipText(activeNotice.text());
    noticeLabel.setVisible(true);
    noticeLabel.setCursor(activeNotice.onClick() != null
        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        : Cursor.getDefaultCursor());
  }
}

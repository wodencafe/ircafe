package cafe.woden.ircclient.ui.util;

import cafe.woden.ircclient.ui.chat.fold.LoadOlderMessagesComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * "Infinite scroll" helper for chat transcripts.
 *
 */
public final class ChatAutoLoadOlderScrollDecorator implements AutoCloseable {

  private static final long DEFAULT_COOLDOWN_MS = 650;

  private final JScrollPane scroll;
  private final Component transcriptRoot;
  private final MouseWheelListener listener;

  private volatile long lastTriggeredAtMs = 0L;
  private volatile boolean waitingForScrollAway = false;

  private ChatAutoLoadOlderScrollDecorator(JScrollPane scroll, Component transcriptRoot) {
    this.scroll = scroll;
    this.transcriptRoot = transcriptRoot;
    this.listener = this::onWheel;
    this.scroll.addMouseWheelListener(listener);
  }

  public static ChatAutoLoadOlderScrollDecorator decorate(JScrollPane scroll, Component transcriptRoot) {
    if (scroll == null || transcriptRoot == null) return null;
    return new ChatAutoLoadOlderScrollDecorator(scroll, transcriptRoot);
  }

  private void onWheel(MouseWheelEvent e) {
    try {
      if (e == null) return;
      if (e.getWheelRotation() >= 0) return;

      JScrollBar bar = scroll.getVerticalScrollBar();
      if (bar == null) return;

      // If we already triggered at the very top, don't trigger again until the view moves away
      // from y=0 (which will naturally happen if we preserve scroll anchor during prepend).
      if (waitingForScrollAway) {
        if (bar.getValue() > 0) {
          waitingForScrollAway = false;
        } else {
          return;
        }
      }

      if (bar.getValue() > 0) return;

      long now = System.currentTimeMillis();
      if (now - lastTriggeredAtMs < DEFAULT_COOLDOWN_MS) return;

      LoadOlderMessagesComponent comp = findLoadOlderComponent(transcriptRoot);
      if (comp == null) return;
      if (comp.state() != LoadOlderMessagesComponent.State.READY) return;

      lastTriggeredAtMs = now;
      waitingForScrollAway = true;

      if (SwingUtilities.isEventDispatchThread()) {
        comp.requestLoad();
      } else {
        SwingUtilities.invokeLater(comp::requestLoad);
      }

      e.consume();
    } catch (Exception ignored) {
    }
  }

  private static LoadOlderMessagesComponent findLoadOlderComponent(Component root) {
    if (root == null) return null;
    if (root instanceof LoadOlderMessagesComponent l) return l;
    if (!(root instanceof Container c)) return null;

    Deque<Component> q = new ArrayDeque<>();
    for (Component child : c.getComponents()) {
      if (child != null) q.add(child);
    }

    while (!q.isEmpty()) {
      Component cur = q.removeFirst();
      if (cur instanceof LoadOlderMessagesComponent l) return l;
      if (cur instanceof Container cc) {
        for (Component child : cc.getComponents()) {
          if (child != null) q.add(child);
        }
      }
    }

    return null;
  }

  @Override
  public void close() {
    try {
      scroll.removeMouseWheelListener(listener);
    } catch (Exception ignored) {
    }
  }
}

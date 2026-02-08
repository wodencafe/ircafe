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
 * <p>When the user attempts to scroll up beyond the top of the transcript and a
 * {@link LoadOlderMessagesComponent} is present, this triggers the same load action
 * as clicking "Load older messages…".
 *
 * <p>This is intentionally conservative: it only fires on an explicit user scroll-up
 * gesture while already at the very top, and it applies a small cooldown to avoid
 * repeated triggers from high-resolution wheel events.
 */
public final class ChatAutoLoadOlderScrollDecorator implements AutoCloseable {

  private static final long DEFAULT_COOLDOWN_MS = 650;

  private final JScrollPane scroll;
  private final Component transcriptRoot;
  private final MouseWheelListener listener;

  private volatile long lastTriggeredAtMs = 0L;

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
      // Only consider "scroll up" gestures.
      if (e.getWheelRotation() >= 0) return;

      // Must be at (or extremely near) the top already.
      JScrollBar bar = scroll.getVerticalScrollBar();
      if (bar == null) return;
      if (bar.getValue() > 0) return;

      // Cooldown to prevent duplicate triggers from smooth/hi-res wheel events.
      long now = System.currentTimeMillis();
      if (now - lastTriggeredAtMs < DEFAULT_COOLDOWN_MS) return;

      LoadOlderMessagesComponent comp = findLoadOlderComponent(transcriptRoot);
      if (comp == null) return;
      if (comp.state() != LoadOlderMessagesComponent.State.READY) return;

      lastTriggeredAtMs = now;

      // Trigger on EDT.
      if (SwingUtilities.isEventDispatchThread()) {
        comp.requestLoad();
      } else {
        SwingUtilities.invokeLater(comp::requestLoad);
      }

      // We're at the top; consume to avoid any weird "bounce" behaviors.
      e.consume();
    } catch (Exception ignored) {
      // Best-effort only.
    }
  }

  private static LoadOlderMessagesComponent findLoadOlderComponent(Component root) {
    if (root == null) return null;
    if (root instanceof LoadOlderMessagesComponent l) return l;
    if (!(root instanceof Container c)) return null;

    // BFS to avoid deep recursion in complex transcript component trees.
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

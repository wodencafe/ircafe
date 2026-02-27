package cafe.woden.ircclient.ui.util;

import cafe.woden.ircclient.ui.chat.fold.LoadOlderMessagesComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntSupplier;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** "Infinite scroll" helper for chat transcripts. */
public final class ChatAutoLoadOlderScrollDecorator implements AutoCloseable {

  private static final long DEFAULT_COOLDOWN_MS = 2_000;

  private final JScrollPane scroll;
  private final Component transcriptRoot;
  private final IntSupplier cooldownMsSupplier;
  private final MouseWheelListener listener;

  private volatile long lastTriggeredAtMs = 0L;

  private ChatAutoLoadOlderScrollDecorator(
      JScrollPane scroll, Component transcriptRoot, IntSupplier cooldownMsSupplier) {
    this.scroll = scroll;
    this.transcriptRoot = transcriptRoot;
    this.cooldownMsSupplier =
        cooldownMsSupplier != null ? cooldownMsSupplier : () -> (int) DEFAULT_COOLDOWN_MS;
    this.listener = this::onWheel;
    this.scroll.addMouseWheelListener(listener);
  }

  public static ChatAutoLoadOlderScrollDecorator decorate(
      JScrollPane scroll, Component transcriptRoot) {
    return decorate(scroll, transcriptRoot, () -> (int) DEFAULT_COOLDOWN_MS);
  }

  public static ChatAutoLoadOlderScrollDecorator decorate(
      JScrollPane scroll, Component transcriptRoot, IntSupplier cooldownMsSupplier) {
    if (scroll == null || transcriptRoot == null) return null;
    return new ChatAutoLoadOlderScrollDecorator(scroll, transcriptRoot, cooldownMsSupplier);
  }

  private void onWheel(MouseWheelEvent e) {
    try {
      if (e == null) return;
      if (e.getWheelRotation() >= 0) return;

      JScrollBar bar = scroll.getVerticalScrollBar();
      if (bar == null) return;
      if (bar.getValue() > 0) return;

      long now = System.currentTimeMillis();
      int cooldownMs =
          (cooldownMsSupplier != null) ? cooldownMsSupplier.getAsInt() : (int) DEFAULT_COOLDOWN_MS;
      if (cooldownMs < 100) cooldownMs = 100;
      if (cooldownMs > 30_000) cooldownMs = 30_000;
      if (now - lastTriggeredAtMs < cooldownMs) return;

      LoadOlderMessagesComponent comp = findLoadOlderComponent(transcriptRoot);
      if (comp == null) return;
      if (comp.state() != LoadOlderMessagesComponent.State.READY) return;

      lastTriggeredAtMs = now;

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

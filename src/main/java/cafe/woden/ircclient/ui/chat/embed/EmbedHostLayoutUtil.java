package cafe.woden.ircclient.ui.chat.embed;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicTextUI;
import javax.swing.text.View;

/**
 * Utility methods for sizing and reflow of Swing components embedded inside a JTextPane StyledDocument.
 */
final class EmbedHostLayoutUtil {

  private EmbedHostLayoutUtil() {}

  static int computeMaxInlineWidth(java.awt.Component embed, int fallbackMaxW, int widthMarginPx, int minWidth) {
    if (embed == null) return fallbackMaxW;

    JTextPane pane = (JTextPane) SwingUtilities.getAncestorOfClass(JTextPane.class, embed);
    if (pane != null) {
      int w = pane.getVisibleRect().width;
      if (w <= 0) w = pane.getWidth();
      if (w > 0) return Math.max(minWidth, w - widthMarginPx);
    }

    JScrollPane scroller = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, embed);
    if (scroller != null) {
      int w = scroller.getViewport().getExtentSize().width;
      if (w > 0) return Math.max(minWidth, w - widthMarginPx);
    }

    return fallbackMaxW;
  }

  static java.awt.Component hookResizeListener(
      java.awt.Component embed,
      java.awt.event.ComponentListener listener,
      java.awt.Component currentTarget) {
    if (embed == null || listener == null) return currentTarget;

    java.awt.Component target = (JTextPane) SwingUtilities.getAncestorOfClass(JTextPane.class, embed);
    if (target == null) {
      JScrollPane scroller = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, embed);
      if (scroller != null) target = scroller.getViewport();
    }

    if (target != null && target != currentTarget) {
      unhookResizeListener(listener, currentTarget);
      target.addComponentListener(listener);
      return target;
    }

    return currentTarget;
  }

  static java.awt.Component unhookResizeListener(
      java.awt.event.ComponentListener listener,
      java.awt.Component currentTarget) {
    if (listener == null || currentTarget == null) return null;
    try {
      currentTarget.removeComponentListener(listener);
    } catch (Exception ignored) {
    }
    return null;
  }

  static void requestHostReflow(java.awt.Component embed) {
    if (embed == null) return;
    JTextPane pane = (JTextPane) SwingUtilities.getAncestorOfClass(JTextPane.class, embed);
    if (pane == null) return;

    SwingUtilities.invokeLater(() -> {
      try {
        if (pane.getUI() instanceof BasicTextUI btui) {
          View root = btui.getRootView(pane);
          if (root != null) {
            root.preferenceChanged(null, true, true);
          }
        }
      } catch (Exception ignored) {
        // best-effort
      }
      pane.revalidate();
      pane.repaint();
    });
  }
}

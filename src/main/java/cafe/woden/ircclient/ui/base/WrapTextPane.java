package cafe.woden.ircclient.ui;

import javax.swing.*;
import java.awt.*;

/**
 * JTextPane that word/line wraps when placed inside a JScrollPane.
 *
 * Default JTextPane sizing allows it to grow wider than the viewport,
 * which yields horizontal scrolling instead of wrapping.
 */
public class WrapTextPane extends JTextPane {

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension d = super.getPreferredSize();
    if (getParent() instanceof JViewport viewport) {
      int w = viewport.getWidth();
      if (w > 0) d.width = w;
    }
    return d;
  }
}

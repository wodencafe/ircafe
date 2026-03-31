package cafe.woden.ircclient.ui.settings;

import java.awt.Dimension;
import javax.swing.JTabbedPane;

final class DynamicTabbedPane extends JTabbedPane {
  @Override
  public Dimension getPreferredSize() {
    return computeSelectedSize(true);
  }

  @Override
  public Dimension getMinimumSize() {
    return computeSelectedSize(false);
  }

  private Dimension computeSelectedSize(boolean preferred) {
    Dimension base = preferred ? super.getPreferredSize() : super.getMinimumSize();
    if (getTabCount() == 0) return base;

    int maxW = 0;
    int maxH = 0;
    for (int i = 0; i < getTabCount(); i++) {
      java.awt.Component c = getComponentAt(i);
      if (c == null) continue;
      Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
      if (d == null) continue;
      maxW = Math.max(maxW, d.width);
      maxH = Math.max(maxH, d.height);
    }

    java.awt.Component selected = getSelectedComponent();
    if (selected == null) return base;
    Dimension sel = preferred ? selected.getPreferredSize() : selected.getMinimumSize();
    if (sel == null) return base;

    // base already includes tabs/header/insets; swap the "max tab" content for the selected tab
    // content.
    int w = base.width - maxW + sel.width;
    int h = base.height - maxH + sel.height;
    return new Dimension(Math.max(0, w), Math.max(0, h));
  }
}

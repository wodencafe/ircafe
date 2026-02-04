package cafe.woden.ircclient.ui.nickcolors;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

class ColorSwatchIcon implements Icon {
  private final Color color;
  private final int w;
  private final int h;

  ColorSwatchIcon(Color color, int w, int h) {
    this.color = color != null ? color : Color.GRAY;
    this.w = Math.max(6, w);
    this.h = Math.max(6, h);
  }

  @Override
  public int getIconWidth() {
    return w;
  }

  @Override
  public int getIconHeight() {
    return h;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Color old = g.getColor();
    try {
      g.setColor(color);
      g.fillRect(x, y, w, h);
      g.setColor(new Color(0, 0, 0, 80));
      g.drawRect(x, y, w - 1, h - 1);
    } finally {
      g.setColor(old);
    }
  }
}

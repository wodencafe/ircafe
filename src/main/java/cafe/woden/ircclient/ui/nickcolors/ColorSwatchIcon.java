package cafe.woden.ircclient.ui.nickcolors;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;
import javax.swing.UIManager;

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
      Color border = c != null ? c.getForeground() : null;
      if (border == null) border = UIManager.getColor("Component.borderColor");
      if (border == null) border = UIManager.getColor("Separator.foreground");
      if (border == null) border = Color.BLACK;
      border = new Color(border.getRed(), border.getGreen(), border.getBlue(), 120);
      g.setColor(border);
      g.drawRect(x, y, w - 1, h - 1);
    } finally {
      g.setColor(old);
    }
  }
}

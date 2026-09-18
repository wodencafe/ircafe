package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;

/** Paints a shared background behind a reply's quoted preview and its message paragraph. */
final class ReplyBlockParagraphView extends ParagraphView {
  ReplyBlockParagraphView(Element element) {
    super(element);
  }

  @Override
  public float getMinimumSpan(int axis) {
    return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
  }

  @Override
  public void paint(Graphics graphics, Shape allocation) {
    Object role = getAttributes().getAttribute(ChatStyles.ATTR_REPLY_BLOCK);
    if (role != null && getContainer() != null) {
      Rectangle bounds = allocation.getBounds();
      boolean quote = ChatStyles.REPLY_BLOCK_QUOTE.equals(role);
      int top = quote ? Math.round(StyleConstants.getSpaceAbove(getAttributes())) : 0;
      int bottom = quote ? 0 : 4;
      Color foreground = getContainer().getForeground();
      Color background = getContainer().getBackground();
      Graphics g = graphics.create();
      try {
        g.setColor(mix(background, foreground, 0.06));
        g.fillRect(
            bounds.x, bounds.y + top, bounds.width, Math.max(0, bounds.height - top - bottom));
        if (quote) {
          g.setColor(mix(background, foreground, 0.5));
          g.fillRect(bounds.x + 5, bounds.y + top, 3, Math.max(0, bounds.height - top));
        }
      } finally {
        g.dispose();
      }
    }
    super.paint(graphics, allocation);
  }

  private static Color mix(Color background, Color foreground, double amount) {
    return new Color(
        (int) (background.getRed() * (1 - amount) + foreground.getRed() * amount),
        (int) (background.getGreen() * (1 - amount) + foreground.getGreen() * amount),
        (int) (background.getBlue() * (1 - amount) + foreground.getBlue() * amount));
  }
}

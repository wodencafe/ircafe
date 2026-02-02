package cafe.woden.ircclient.ui.chat.fold;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.Objects;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * A purely-visual divider inserted into the transcript to separate loaded history from live messages.
 *
 * <p>This is rendered as an embedded Swing component inside the chat transcript document.
 */
public final class HistoryDividerComponent extends JPanel {

  private final JLabel label = new JLabel();

  public HistoryDividerComponent(String text) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    setOpaque(false);

    label.setOpaque(false);
    label.setText(text == null ? "" : text);

    // Match transcript fonts as closely as we can.
    Font base = UIManager.getFont("TextPane.font");
    if (base == null) base = UIManager.getFont("Label.font");
    setTranscriptFont(base);

    applyTheme();
    add(label);
  }

  public void setTranscriptFont(Font base) {
    if (base == null) return;
    // Slightly smaller + italic so this reads as a separator, not a normal message.
    float size = Math.max(9f, base.getSize2D() - 1f);
    label.setFont(base.deriveFont(Font.ITALIC, size));
  }

  public void setText(String text) {
    label.setText(Objects.requireNonNullElse(text, ""));
    revalidate();
    repaint();
  }

  public String getText() {
    return label.getText();
  }

  /**
   * JTextPane embeds Swing components using a baseline-aware view. Provide a stable baseline derived
   * from our label so the divider aligns with normal text.
   */
  @Override
  public int getBaseline(int width, int height) {
    Insets in = getInsets();
    int ascent = 0;
    try {
      if (label.getFont() != null) ascent = Math.max(ascent, getFontMetrics(label.getFont()).getAscent());
    } catch (Exception ignored) {
      // ignore
    }
    if (ascent <= 0) return -1;
    return in.top + ascent;
  }

  @Override
  public java.awt.Component.BaselineResizeBehavior getBaselineResizeBehavior() {
    return java.awt.Component.BaselineResizeBehavior.CONSTANT_ASCENT;
  }

  private void applyTheme() {
    var dim = UIManager.getColor("Label.disabledForeground");
    var fg = UIManager.getColor("TextPane.foreground");
    label.setForeground(dim != null ? dim : fg);
  }
}

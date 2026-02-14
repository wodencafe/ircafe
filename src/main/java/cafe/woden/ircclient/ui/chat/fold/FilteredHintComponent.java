package cafe.woden.ircclient.ui.chat.fold;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Tiny hint row shown when filtered lines are being suppressed but placeholders are disabled.
 *
 * <p>This exists to avoid the "unread but nothing visible" confusion.
 */
public class FilteredHintComponent extends JPanel {

  private int count = 0;

  // Optional base font from the transcript; if set, we derive italic variants from it.
  private Font transcriptBaseFont;

  private final JLabel label = new JLabel();

  public FilteredHintComponent() {
    setOpaque(false);
    setLayout(new BorderLayout());
    setBorder(new EmptyBorder(2, 0, 2, 0));

    label.setOpaque(false);
    label.setBorder(new EmptyBorder(0, 0, 0, 0));

    updateText();
    add(label, BorderLayout.CENTER);
  }

  public int count() {
    return count;
  }

  /** Allows the transcript owner to provide a consistent base font for embedded components. */
  public void setTranscriptFont(Font base) {
    this.transcriptBaseFont = base;
    applyDimItalic(label);
    revalidate();
    repaint();
  }

  /** Adds one more filtered line to this contiguous hint run. */
  public void addFilteredLine() {
    count++;
    updateText();
    revalidate();
    repaint();
  }

  private void updateText() {
    label.setText("Filtered lines: " + count);
    applyDimItalic(label);
  }

  private void applyDimItalic(JLabel l) {
    if (l == null) return;
    Color dim = UIManager.getColor("Label.disabledForeground");
    if (dim != null) l.setForeground(dim);

    Font base = (transcriptBaseFont != null) ? transcriptBaseFont : UIManager.getFont("Label.font");
    if (base != null) {
      l.setFont(base.deriveFont(Font.ITALIC));
    }
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension d = super.getPreferredSize();
    int w = availableWidth();
    if (w > 0) {
      return new Dimension(w, d.height);
    }
    return d;
  }

  @Override
  public Dimension getMaximumSize() {
    Dimension d = getPreferredSize();
    return new Dimension(Integer.MAX_VALUE, d.height);
  }

  private int availableWidth() {
    Container p = getParent();
    if (p == null) return -1;
    int w = p.getWidth();
    if (w <= 0) return -1;

    Insets insets = (p instanceof JComponent)
        ? ((JComponent) p).getInsets()
        : new Insets(0, 0, 0, 0);

    w = w - insets.left - insets.right;
    return Math.max(0, w);
  }
}

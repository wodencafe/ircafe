package cafe.woden.ircclient.ui.chat.fold;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * A single-line "spoiler" placeholder for soft-ignored messages.
 *
 * <p>We intentionally keep this component to ONE visual line so it aligns with normal transcript
 * lines. When clicked, it triggers an in-place replacement in the transcript document (the component
 * is removed and replaced with the original rendered line).</p>
 */
public class SpoilerMessageComponent extends JPanel {

  private final JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
  private final JLabel ts = new JLabel();
  private final JLabel from = new JLabel();
  private final JLabel pill = new JLabel();

  private volatile BooleanSupplier onReveal = () -> false;
  private boolean revealing = false;

  public SpoilerMessageComponent(String timestampText, String fromText) {
    super(new BorderLayout());
    setOpaque(false);

    header.setOpaque(false);

    ts.setText(Objects.toString(timestampText, ""));
    from.setText(Objects.toString(fromText, ""));

    // Keep it looking like a "chip" / covered pill.
    pill.setOpaque(true);
    pill.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
    pill.setText("soft ignored - click to reveal");
    applyPillColors();

    if (!ts.getText().isBlank()) header.add(ts);
    if (!from.getText().isBlank()) header.add(from);
    header.add(pill);

    java.awt.event.MouseAdapter reveal = new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
          revealOnce();
        }
      }

      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        header.setCursor(Cursor.getDefaultCursor());
      }
    };

    header.addMouseListener(reveal);
    pill.addMouseListener(reveal);
    ts.addMouseListener(reveal);
    from.addMouseListener(reveal);

    // Remove any left "prefix" so it aligns with normal transcript lines.
    add(header, BorderLayout.CENTER);

    setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
  }

  /** Set the reveal handler. Must be safe to call from the EDT. */
  public void setOnReveal(BooleanSupplier onReveal) {
    this.onReveal = Objects.requireNonNullElse(onReveal, () -> false);
  }

  public void setFromColor(Color c) {
    if (c == null) return;
    from.setForeground(c);
  }

  private void revealOnce() {
    if (revealing) return;
    revealing = true;

    pill.setText("revealing...");
    try {
      boolean ok = onReveal != null && onReveal.getAsBoolean();
      if (!ok) {
        // Reveal failed (often because the embedded component shifted due to folding/edits).
        // Allow retry.
        pill.setText("reveal failed - click to retry");
        revealing = false;
        repaint();
      }
      // If ok, the component will be removed from the transcript, so there's nothing else to do.
    } catch (Exception ex) {
      pill.setText("reveal failed - click to retry");
      revealing = false;
      repaint();
    }
  }

  private void applyPillColors() {
    Color bg = UIManager.getColor("TextPane.background");
    Color fg = UIManager.getColor("TextPane.foreground");
    Color dim = UIManager.getColor("Label.disabledForeground");

    if (bg == null) bg = Color.WHITE;
    if (fg == null) fg = Color.BLACK;
    if (dim == null) dim = mix(fg, bg, 0.35);

    // chip background: mix bg and fg, but very subtly so it's readable in both light/dark
    Color chipBg = mix(bg, fg, 0.92);
    Color chipFg = mix(fg, bg, 0.18);

    pill.setBackground(chipBg);
    pill.setForeground(chipFg);

    // Timestamp/nick should look like other transcript prefixes.
    ts.setForeground(dim);
  }

  @Override
  public Insets getInsets() {
    Insets i = super.getInsets();
    if (i == null) return new Insets(0, 0, 0, 0);
    return i;
  }

  private static Color mix(Color a, Color b, double aWeight) {
    if (a == null) return b;
    if (b == null) return a;

    double bw = 1.0 - aWeight;
    int r = clamp((int) Math.round(a.getRed() * aWeight + b.getRed() * bw));
    int g = clamp((int) Math.round(a.getGreen() * aWeight + b.getGreen() * bw));
    int bl = clamp((int) Math.round(a.getBlue() * aWeight + b.getBlue() * bw));
    return new Color(r, g, bl);
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }
}

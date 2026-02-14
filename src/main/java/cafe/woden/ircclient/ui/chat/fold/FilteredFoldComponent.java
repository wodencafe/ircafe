package cafe.woden.ircclient.ui.chat.fold;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Embedded Swing component that represents a contiguous run of filtered lines.
 *
 * <p>By default it shows a single summary line (e.g. {@code "▶ Filtered (7)"}).
 * Clicking toggles an optional preview list of up to {@code maxPreviewLines} samples.
 */
public class FilteredFoldComponent extends JPanel {

  private final int maxPreviewLines;
  private final List<String> previews = new ArrayList<>();

  private int count = 0;
  private boolean expanded;

  // Optional base font from the transcript; if set, we derive italic variants from it.
  private Font transcriptBaseFont;

  private final JLabel summary = new JLabel();
  private final JPanel details = new JPanel();

  public FilteredFoldComponent(boolean collapsedByDefault, int maxPreviewLines) {
    this.maxPreviewLines = Math.max(0, maxPreviewLines);
    this.expanded = !collapsedByDefault;

    setOpaque(false);
    setLayout(new BorderLayout());
    setBorder(new EmptyBorder(2, 0, 2, 0));

    summary.setOpaque(false);
    summary.setBorder(new EmptyBorder(0, 0, 0, 0));
    summary.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    details.setOpaque(false);
    details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
    details.setBorder(new EmptyBorder(2, 18, 2, 0));

    summary.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        toggle();
      }
    });

    updateSummaryText();
    rebuildDetails();
    details.setVisible(expanded);

    add(summary, BorderLayout.NORTH);
    add(details, BorderLayout.CENTER);
  }

  /**
   * Allows the transcript owner to provide a consistent base font for embedded components.
   * We keep a dim + italic styling, but derive from the supplied base font.
   */
  public void setTranscriptFont(Font base) {
    this.transcriptBaseFont = base;
    applyDimItalic(summary);
    if (expanded) rebuildDetails();
    revalidate();
    repaint();
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

  public int count() {
    return count;
  }

  /** Adds one more filtered line to this contiguous run (and optionally records a preview sample). */
  public void addFilteredLine(String preview) {
    count++;
    if (maxPreviewLines > 0 && preview != null && !preview.isBlank() && previews.size() < maxPreviewLines) {
      previews.add(preview);
    }

    updateSummaryText();
    if (expanded) rebuildDetails();
    setToolTipText(buildTooltipHtml(count, previews));

    revalidate();
    repaint();
  }

  private void toggle() {
    expanded = !expanded;
    details.setVisible(expanded);
    updateSummaryText();
    if (expanded) rebuildDetails();
    revalidate();
    repaint();
  }

  private void rebuildDetails() {
    details.removeAll();

    if (previews.isEmpty()) {
      JLabel l = new JLabel("(no preview)  —  edit filters or disable them to see hidden lines");
      l.setOpaque(false);
      applyDimItalic(l);
      details.add(l);
      return;
    }

    for (String p : previews) {
      JLabel l = new JLabel("• " + safe(p));
      l.setOpaque(false);
      applyDimItalic(l);
      details.add(l);
    }

    if (count > previews.size()) {
      JLabel more = new JLabel("…and " + (count - previews.size()) + " more");
      more.setOpaque(false);
      applyDimItalic(more);
      details.add(more);
    }
  }

  private void updateSummaryText() {
    String arrow = expanded ? "▼ " : "▶ ";
    summary.setText(arrow + "Filtered (" + count + ")");
    applyDimItalic(summary);
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

  private static String buildTooltipHtml(int count, List<String> previews) {
    if (count <= 0) return null;
    StringBuilder sb = new StringBuilder();
    sb.append("<html>");
    sb.append("Filtered (").append(count).append(")");
    if (previews != null && !previews.isEmpty()) {
      sb.append("<br/><br/>");
      int shown = 0;
      for (String p : previews) {
        if (p == null) continue;
        sb.append("• ").append(escapeHtml(p)).append("<br/>");
        shown++;
      }
      if (shown < count) {
        sb.append("…and ").append(count - shown).append(" more");
      }
    }
    sb.append("</html>");
    return sb.toString();
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  private static String escapeHtml(String s) {
    if (s == null) return "";
    return s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}

package cafe.woden.ircclient.ui.chat.fold;

import cafe.woden.ircclient.app.PresenceEvent;
import cafe.woden.ircclient.app.PresenceKind;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Embedded Swing component that shows a single collapsible summary line (collapsed by default)
 * for a run of join/part/quit/nick events.
 */
public class PresenceFoldComponent extends JPanel {

  private final List<PresenceEvent> entries = new ArrayList<>();

  private boolean expanded = false;

  private final JLabel summary = new JLabel();
  private final JPanel details = new JPanel();

  public PresenceFoldComponent(List<PresenceEvent> initialEntries) {
    if (initialEntries != null) this.entries.addAll(initialEntries);

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

    rebuildDetails();
    updateSummaryText();

    details.setVisible(false);

    add(summary, BorderLayout.NORTH);
    add(details, BorderLayout.CENTER);

    setToolTipText(buildTooltipHtml(this.entries));
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

  /**
   * Append a new presence event to this fold and refresh the summary + tooltip (and details if expanded).
   */
  public void addEntry(PresenceEvent e) {
    if (e == null) return;
    entries.add(e);
    rebuildDetails();
    updateSummaryText();
    setToolTipText(buildTooltipHtml(entries));

    revalidate();
    repaint();
  }

  /**
   * Replace all entries.
   */
  public void setEntries(List<PresenceEvent> newEntries) {
    entries.clear();
    if (newEntries != null) entries.addAll(newEntries);
    rebuildDetails();
    updateSummaryText();
    setToolTipText(buildTooltipHtml(entries));

    revalidate();
    repaint();
  }

  private void rebuildDetails() {
    details.removeAll();

    for (PresenceEvent e : entries) {
      JLabel l = new JLabel("• " + safe(e == null ? "" : e.displayText()));
      l.setOpaque(false);

      // mimic a "status" style: dim + italic
      Color dim = UIManager.getColor("Label.disabledForeground");
      if (dim != null) l.setForeground(dim);
      Font f = UIManager.getFont("Label.font");
      if (f != null) l.setFont(f.deriveFont(Font.ITALIC));

      details.add(l);
    }

    details.setVisible(expanded);
  }

  private void toggle() {
    expanded = !expanded;
    details.setVisible(expanded);
    updateSummaryText();
    revalidate();
    repaint();
  }

  private void updateSummaryText() {
    String arrow = expanded ? "▼ " : "▶ ";
    summary.setText(arrow + buildSummaryCounts(entries));

    // keep the "status" vibe
    Color dim = UIManager.getColor("Label.disabledForeground");
    if (dim != null) summary.setForeground(dim);
    Font f = UIManager.getFont("Label.font");
    if (f != null) summary.setFont(f.deriveFont(Font.ITALIC));
  }

  private static String buildSummaryCounts(List<PresenceEvent> entries) {
    Map<PresenceKind, Integer> counts = new EnumMap<>(PresenceKind.class);
    counts.put(PresenceKind.JOIN, 0);
    counts.put(PresenceKind.PART, 0);
    counts.put(PresenceKind.QUIT, 0);
    counts.put(PresenceKind.NICK, 0);

    for (PresenceEvent e : entries) {
      if (e == null) continue;
      counts.compute(e.kind(), (k, v) -> v == null ? 1 : v + 1);
    }

    List<String> parts = new ArrayList<>();
    int j = counts.getOrDefault(PresenceKind.JOIN, 0);
    int p = counts.getOrDefault(PresenceKind.PART, 0);
    int q = counts.getOrDefault(PresenceKind.QUIT, 0);
    int n = counts.getOrDefault(PresenceKind.NICK, 0);

    if (j > 0) parts.add(j + " joined");
    if (p > 0) parts.add(p + " left");
    if (q > 0) parts.add(q + " quit");
    if (n > 0) parts.add(n + (n == 1 ? " nick change" : " nick changes"));

    if (parts.isEmpty()) return "presence";
    return String.join(", ", parts);
  }

  private static String buildTooltipHtml(List<PresenceEvent> entries) {
    if (entries == null || entries.isEmpty()) return null;

    StringBuilder sb = new StringBuilder();
    sb.append("<html>");
    sb.append(escapeHtml(buildSummaryCounts(entries)));
    sb.append("<br/><br/>");

    int limit = 60; // avoid absurd tooltips
    int shown = 0;

    for (PresenceEvent e : entries) {
      if (e == null) continue;
      if (shown >= limit) break;
      sb.append("• ").append(escapeHtml(e.displayText())).append("<br/>");
      shown++;
    }

    if (shown < entries.size()) {
      sb.append("…and ").append(entries.size() - shown).append(" more");
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

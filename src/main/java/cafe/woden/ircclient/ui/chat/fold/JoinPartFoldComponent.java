package cafe.woden.ircclient.ui.chat.fold;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

/**
 * Collapsible join/part burst component inserted into the chat transcript StyledDocument.
 *
 */
public final class JoinPartFoldComponent extends JPanel {

  /** Item in the folded group. */
  public record Item(boolean isJoin, String nick, String reason) {}

  private static final int FALLBACK_MAX_W = 420;
  private static final int WIDTH_MARGIN_PX = 32;
  private static final int INDENT_PX = 18;

  private final List<Item> items;

  private final JLabel arrow = new JLabel("\u25B6"); // ▶
  private final JLabel summary = new JLabel();
  private final JTextArea details = new JTextArea();
  private final JPanel detailsWrap = new JPanel(new BorderLayout());

  private boolean collapsed = true;
  private volatile int lastMaxW = -1;

  private java.awt.Component resizeListeningOn;
  private final java.awt.event.ComponentListener resizeListener = new java.awt.event.ComponentAdapter() {
    @Override
    public void componentResized(java.awt.event.ComponentEvent e) {
      SwingUtilities.invokeLater(JoinPartFoldComponent.this::renderForCurrentWidth);
    }
  };

  public JoinPartFoldComponent(List<Item> items) {
    super(new BorderLayout());
    this.items = (items != null) ? new ArrayList<>(items) : List.of();

    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));

    JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    header.setOpaque(false);
    header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    arrow.setOpaque(false);
    summary.setOpaque(false);

    header.add(arrow);
    header.add(summary);

    header.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        toggle();
      }
    });

    add(header, BorderLayout.NORTH);

    details.setEditable(false);
    details.setOpaque(false);
    details.setLineWrap(true);
    details.setWrapStyleWord(true);
    details.setBorder(BorderFactory.createEmptyBorder(0, INDENT_PX, 0, 0));

    // Prevent caret from stealing focus when you click inside the transcript.
    details.setFocusable(false);

    detailsWrap.setOpaque(false);
    detailsWrap.add(details, BorderLayout.CENTER);
    detailsWrap.setVisible(false);
    add(detailsWrap, BorderLayout.CENTER);

    rebuildText();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    hookResizeListener();
    renderForCurrentWidth();
  }

  @Override
  public void removeNotify() {
    unhookResizeListener();
    super.removeNotify();
  }

  private void toggle() {
    collapsed = !collapsed;
    arrow.setText(collapsed ? "\u25B6" : "\u25BC"); // ▶ / ▼
    detailsWrap.setVisible(!collapsed);
    renderForCurrentWidth();
  }

  private void rebuildText() {
    int joins = 0;
    int parts = 0;
    List<String> joinNicks = new ArrayList<>();
    List<String> partBits = new ArrayList<>();

    for (Item it : items) {
      if (it == null) continue;
      String n = it.nick() == null ? "" : it.nick().trim();
      if (n.isEmpty()) continue;

      if (it.isJoin()) {
        joins++;
        joinNicks.add(n);
      } else {
        parts++;
        String r = it.reason() == null ? "" : it.reason().trim();
        partBits.add(r.isEmpty() ? n : (n + " (" + r + ")"));
      }
    }

    // Summary (collapsed line)
    StringBuilder s = new StringBuilder();
    if (joins > 0) {
      s.append(joins).append(joins == 1 ? " user joined" : " users joined");
    }
    if (parts > 0) {
      if (!s.isEmpty()) s.append(", ");
      s.append(parts).append(parts == 1 ? " user left" : " users left");
    }
    if (s.isEmpty()) {
      s.append("Join/part update");
    }
    summary.setText(s.toString());

    // Details (expanded)
    StringBuilder d = new StringBuilder();
    if (!joinNicks.isEmpty()) {
      d.append("Joined: ").append(String.join(", ", joinNicks));
    }
    if (!partBits.isEmpty()) {
      if (!d.isEmpty()) d.append("\n");
      d.append("Left: ").append(String.join(", ", partBits));
    }
    details.setText(d.toString());
  }

  private void renderForCurrentWidth() {
    int maxW = computeMaxInlineWidth();
    if (maxW <= 0) maxW = FALLBACK_MAX_W;

    if (Math.abs(maxW - lastMaxW) < 4 && lastMaxW > 0) {
      return;
    }
    lastMaxW = maxW;

    // Encourage wrapping in the details text area.
    int detailsW = Math.max(140, maxW - WIDTH_MARGIN_PX);
    details.setSize(new Dimension(detailsW, Short.MAX_VALUE));
    Dimension detailsPref = details.getPreferredSize();
    details.setPreferredSize(new Dimension(detailsW, detailsPref.height));

    // Update our preferred size so ComponentView lays it out nicely.
    int h = getHeaderHeight();
    if (!collapsed) {
      h += detailsPref.height;
    }
    setPreferredSize(new Dimension(detailsW, Math.max(18, h)));

    revalidate();
    repaint();
  }

  private int getHeaderHeight() {
    Dimension a = arrow.getPreferredSize();
    Dimension s = summary.getPreferredSize();
    int h = Math.max(a.height, s.height);
    // Add a smidge of padding from the FlowLayout.
    return h + 4;
  }

  private int computeMaxInlineWidth() {
    // Try to find the transcript viewport width (JTextPane preferred).
    JTextPane pane = (JTextPane) SwingUtilities.getAncestorOfClass(JTextPane.class, this);
    if (pane != null) {
      int w = pane.getVisibleRect().width;
      if (w <= 0) w = pane.getWidth();
      if (w > 0) return Math.max(160, w - WIDTH_MARGIN_PX);
    }

    JScrollPane scroller = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
    if (scroller != null) {
      int w = scroller.getViewport().getExtentSize().width;
      if (w > 0) return Math.max(160, w - WIDTH_MARGIN_PX);
    }

    return FALLBACK_MAX_W;
  }

  private void hookResizeListener() {
    java.awt.Component target = (java.awt.Component) SwingUtilities.getAncestorOfClass(JTextPane.class, this);
    if (target == null) {
      target = (java.awt.Component) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
    }
    if (target == null) return;

    if (resizeListeningOn == target) return;
    unhookResizeListener();
    resizeListeningOn = target;
    target.addComponentListener(resizeListener);
  }

  private void unhookResizeListener() {
    if (resizeListeningOn != null) {
      try {
        resizeListeningOn.removeComponentListener(resizeListener);
      } catch (Exception ignored) {
      }
      resizeListeningOn = null;
    }
  }
}

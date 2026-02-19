package cafe.woden.ircclient.ui.chat.fold;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * Inline reaction-summary row rendered under a message in the transcript.
 *
 * <p>Each reaction is shown as a small chip with count and a tooltip listing participating nicks.
 */
public final class MessageReactionsComponent extends JPanel {

  private final Map<String, ChipState> chipsByReaction = new LinkedHashMap<>();
  private Font transcriptBaseFont;

  public MessageReactionsComponent() {
    super(new FlowLayout(FlowLayout.LEFT, 6, 0));
    setOpaque(false);
  }

  public void setTranscriptFont(Font base) {
    this.transcriptBaseFont = base;
    refreshChipFonts();
  }

  public void setReactions(Map<String, ? extends Collection<String>> reactions) {
    chipsByReaction.clear();
    removeAll();
    if (reactions != null) {
      for (Map.Entry<String, ? extends Collection<String>> e : reactions.entrySet()) {
        String token = normalizeReactionToken(e.getKey());
        if (token.isEmpty()) continue;
        Set<String> nicks = normalizeNickSet(e.getValue());
        if (nicks.isEmpty()) continue;
        ChipState st = new ChipState(token);
        st.nicks.addAll(nicks);
        chipsByReaction.put(token, st);
      }
    }
    rebuild();
  }

  public void addReaction(String reaction, String nick) {
    String token = normalizeReactionToken(reaction);
    if (token.isEmpty()) return;
    String n = normalizeNick(nick);
    if (n.isEmpty()) return;
    ChipState st = chipsByReaction.computeIfAbsent(token, ChipState::new);
    st.nicks.add(n);
    rebuild();
  }

  private void rebuild() {
    removeAll();
    for (ChipState st : chipsByReaction.values()) {
      JLabel chip = buildChip(st);
      st.label = chip;
      add(chip);
    }
    revalidate();
    repaint();
  }

  private void refreshChipFonts() {
    for (ChipState st : chipsByReaction.values()) {
      if (st.label != null) {
        applyChipFont(st.label);
      }
    }
    revalidate();
    repaint();
  }

  private JLabel buildChip(ChipState st) {
    JLabel l = new JLabel(labelText(st));
    l.setOpaque(true);
    applyChipFont(l);
    l.setForeground(resolveChipForeground());
    l.setBackground(resolveChipBackground());
    l.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(resolveChipBorderColor()),
        BorderFactory.createEmptyBorder(1, 6, 1, 6)));
    l.setToolTipText(tooltip(st));
    return l;
  }

  private void applyChipFont(JLabel l) {
    Font base = transcriptBaseFont;
    if (base == null) {
      base = UIManager.getFont("TextPane.font");
      if (base == null) base = UIManager.getFont("Label.font");
    }
    if (base != null) {
      float size = Math.max(9f, base.getSize2D() - 1f);
      l.setFont(base.deriveFont(Font.PLAIN, size));
    }
  }

  private static String labelText(ChipState st) {
    int count = st.nicks.size();
    return (count > 1) ? (st.reaction + " " + count) : st.reaction;
  }

  private static String tooltip(ChipState st) {
    if (st.nicks.isEmpty()) return st.reaction;
    List<String> sorted = new ArrayList<>(st.nicks);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    return st.reaction + " by " + String.join(", ", sorted);
  }

  @Override
  public int getBaseline(int width, int height) {
    Insets in = getInsets();
    int ascent = 0;
    for (ChipState st : chipsByReaction.values()) {
      try {
        if (st.label != null && st.label.getFont() != null) {
          ascent = Math.max(ascent, getFontMetrics(st.label.getFont()).getAscent());
        }
      } catch (Exception ignored) {
      }
    }
    if (ascent <= 0) return -1;
    return in.top + ascent;
  }

  @Override
  public java.awt.Component.BaselineResizeBehavior getBaselineResizeBehavior() {
    return java.awt.Component.BaselineResizeBehavior.CONSTANT_ASCENT;
  }

  private static Set<String> normalizeNickSet(Collection<String> raw) {
    TreeSet<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    if (raw == null) return out;
    for (String s : raw) {
      String n = normalizeNick(s);
      if (!n.isEmpty()) out.add(n);
    }
    return out;
  }

  private static String normalizeNick(String raw) {
    String s = (raw == null) ? "" : raw.trim();
    return s;
  }

  private static String normalizeReactionToken(String raw) {
    String s = (raw == null) ? "" : raw.trim();
    if (s.isEmpty()) return "";
    // Keep visible tokens compact.
    if (s.length() > 32) {
      return s.substring(0, 32);
    }
    return s;
  }

  private static Color resolveChipForeground() {
    Color fg = UIManager.getColor("TextPane.foreground");
    if (fg == null) fg = UIManager.getColor("Label.foreground");
    return fg;
  }

  private static Color resolveChipBackground() {
    Color sel = UIManager.getColor("TextPane.selectionBackground");
    Color bg = UIManager.getColor("TextPane.background");
    if (sel == null && bg == null) {
      // Best-effort fallback to avoid hard-coded colors clashing with theme-pack themes.
      Color pbg = UIManager.getColor("Panel.background");
      if (pbg == null) pbg = UIManager.getColor("control");
      return pbg != null ? pbg : new Color(0xDDDDDD);
    }
    if (sel == null) return bg;
    if (bg == null) return sel;
    int r = (int) Math.round(sel.getRed() * 0.28 + bg.getRed() * 0.72);
    int g = (int) Math.round(sel.getGreen() * 0.28 + bg.getGreen() * 0.72);
    int b = (int) Math.round(sel.getBlue() * 0.28 + bg.getBlue() * 0.72);
    return new Color(clamp(r), clamp(g), clamp(b));
  }

  private static Color resolveChipBorderColor() {
    Color c = UIManager.getColor("Component.borderColor");
    if (c == null) c = UIManager.getColor("Separator.foreground");
    if (c == null) {
      Color fg = UIManager.getColor("Label.foreground");
      if (fg == null) fg = Color.DARK_GRAY;
      c = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 90);
    }
    return c;
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }

  private static final class ChipState {
    final String reaction;
    final Set<String> nicks = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    JLabel label;

    private ChipState(String reaction) {
      this.reaction = reaction == null ? "" : reaction.trim();
    }
  }
}

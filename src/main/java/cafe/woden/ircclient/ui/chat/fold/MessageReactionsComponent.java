package cafe.woden.ircclient.ui.chat.fold;

import cafe.woden.ircclient.ui.util.EmojiImageSupport;
import cafe.woden.ircclient.ui.util.EmojiShortcodeSupport;
import cafe.woden.ircclient.ui.util.EmojiTextSupport;
import cafe.woden.ircclient.ui.util.UiColorKeys;
import cafe.woden.ircclient.ui.util.UiFontKeys;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

/**
 * Inline reaction-summary row rendered under a message in the transcript.
 *
 * <p>Each reaction is shown as a small chip with count and a tooltip listing participating nicks.
 */
public final class MessageReactionsComponent extends JPanel {

  public static final String REACTION_TOKEN_PROPERTY = "ircafe.reactionToken";

  private final Map<String, ChipState> chipsByReaction = new LinkedHashMap<>();
  private Font transcriptBaseFont;
  private SwingWorker<List<ChipImage>, Void> imageWorker;
  private Consumer<String> onReactRequested = reaction -> {};
  private Consumer<String> onUnreactRequested = reaction -> {};

  public MessageReactionsComponent() {
    super(new FlowLayout(FlowLayout.LEFT, 6, 0));
    setOpaque(false);
  }

  public void setTranscriptFont(Font base) {
    this.transcriptBaseFont = base;
    refreshChipFonts();
  }

  public void setOnReactRequested(Consumer<String> onReactRequested) {
    this.onReactRequested = Objects.requireNonNullElse(onReactRequested, reaction -> {});
  }

  public void setOnUnreactRequested(Consumer<String> onUnreactRequested) {
    this.onUnreactRequested = Objects.requireNonNullElse(onUnreactRequested, reaction -> {});
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
    refreshChipImages();
  }

  private void refreshChipFonts() {
    for (ChipState st : chipsByReaction.values()) {
      if (st.label != null) {
        applyChipFont(st.label);
      }
    }
    revalidate();
    repaint();
    refreshChipImages();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    refreshChipImages();
  }

  @Override
  public void removeNotify() {
    cancelImageWorker();
    super.removeNotify();
  }

  private void cancelImageWorker() {
    if (imageWorker != null) {
      imageWorker.cancel(true);
      imageWorker = null;
    }
  }

  private void refreshChipImages() {
    cancelImageWorker();
    if (!isDisplayable() || chipsByReaction.isEmpty()) return;
    List<ChipImageRequest> requests = new ArrayList<>();
    for (ChipState st : chipsByReaction.values()) {
      JLabel label = st.label;
      int size = Math.clamp(label.getFontMetrics(label.getFont()).getHeight(), 12, 48);
      requests.add(new ChipImageRequest(label, st.reaction, st.nicks.size(), size));
    }
    imageWorker =
        new SwingWorker<>() {
          @Override
          protected List<ChipImage> doInBackground() {
            List<ChipImage> images = new ArrayList<>();
            for (ChipImageRequest request : requests) {
              if (isCancelled()) break;
              String emoji = EmojiShortcodeSupport.resolve(request.token());
              BufferedImage image =
                  EmojiTextSupport.containsEmoji(emoji)
                      ? EmojiImageSupport.imageFor(emoji, request.size())
                      : null;
              images.add(new ChipImage(request, emoji, image));
            }
            return images;
          }

          @Override
          protected void done() {
            if (isCancelled() || imageWorker != this) return;
            try {
              for (ChipImage result : get()) {
                ChipImageRequest request = result.request();
                JLabel label = request.label();
                label.setIcon(result.image() == null ? null : new ImageIcon(result.image()));
                String count = request.count() > 1 ? Integer.toString(request.count()) : "";
                label.setText(
                    result.image() == null
                        ? result.emoji() + (count.isEmpty() ? "" : " " + count)
                        : count);
              }
              revalidate();
              repaint();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException e) {
              // Keep the original text if an asset cannot be rendered.
            } finally {
              imageWorker = null;
            }
          }
        };
    imageWorker.execute();
  }

  private record ChipImageRequest(JLabel label, String token, int count, int size) {}

  private record ChipImage(ChipImageRequest request, String emoji, BufferedImage image) {}

  private JLabel buildChip(ChipState st) {
    JLabel l = new JLabel(labelText(st));
    l.putClientProperty("html.disable", Boolean.TRUE);
    l.putClientProperty(REACTION_TOKEN_PROPERTY, st.reaction);
    l.setOpaque(true);
    applyChipFont(l);
    l.setForeground(resolveChipForeground());
    l.setBackground(resolveChipBackground());
    l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    l.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(resolveChipBorderColor()),
            BorderFactory.createEmptyBorder(1, 6, 1, 6)));
    l.setToolTipText(tooltip(st));
    l.getAccessibleContext().setAccessibleName(tooltip(st));
    l.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            if (e != null && e.isPopupTrigger()) {
              dispatchUnreact(e);
            }
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            if (e == null) return;
            if (e.isPopupTrigger() || MouseEvent.BUTTON3 == e.getButton()) {
              dispatchUnreact(e);
              return;
            }
            if (MouseEvent.BUTTON1 == e.getButton()) {
              dispatchReact(e);
            }
          }

          private void dispatchReact(MouseEvent e) {
            String token = normalizeReactionToken(st.reaction);
            if (token.isEmpty()) return;
            onReactRequested.accept(token);
            e.consume();
          }

          private void dispatchUnreact(MouseEvent e) {
            String token = normalizeReactionToken(st.reaction);
            if (token.isEmpty()) return;
            onUnreactRequested.accept(token);
            e.consume();
          }
        });
    return l;
  }

  private void applyChipFont(JLabel l) {
    Font base = transcriptBaseFont;
    if (base == null) {
      base = UIManager.getFont(UiFontKeys.TEXT_PANE_FONT);
      if (base == null) base = UIManager.getFont(UiFontKeys.LABEL_FONT);
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
    String actionHint = " [Left click toggle, right click remove]";
    if (st.nicks.isEmpty()) return st.reaction;
    List<String> sorted = new ArrayList<>(st.nicks);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    return st.reaction + " by " + String.join(", ", sorted) + actionHint;
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
    Color fg = UIManager.getColor(UiColorKeys.TEXT_PANE_FOREGROUND);
    if (fg == null) fg = UIManager.getColor(UiColorKeys.LABEL_FOREGROUND);
    return fg;
  }

  private static Color resolveChipBackground() {
    Color sel = UIManager.getColor(UiColorKeys.TEXT_PANE_SELECTION_BACKGROUND);
    Color bg = UIManager.getColor(UiColorKeys.TEXT_PANE_BACKGROUND);
    if (sel == null && bg == null) {
      // Best-effort fallback to avoid hard-coded colors clashing with theme-pack themes.
      Color pbg = UIManager.getColor(UiColorKeys.PANEL_BACKGROUND);
      if (pbg == null) pbg = UIManager.getColor(UiColorKeys.CONTROL);
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
    Color c = UIManager.getColor(UiColorKeys.COMPONENT_BORDER_COLOR);
    if (c == null) c = UIManager.getColor(UiColorKeys.SEPARATOR_FOREGROUND);
    if (c == null) {
      Color fg = UIManager.getColor(UiColorKeys.LABEL_FOREGROUND);
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

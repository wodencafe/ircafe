package cafe.woden.ircclient.ui.chat.fold;

import cafe.woden.ircclient.ui.DocumentComponentProvider;
import cafe.woden.ircclient.ui.util.UiColorKeys;
import cafe.woden.ircclient.ui.util.UiFontKeys;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * A purely-visual divider inserted into the transcript.
 *
 * <p>This is rendered as an embedded Swing component inside the chat transcript document.
 */
public final class HistoryDividerComponent extends JPanel implements DocumentComponentProvider {

  private static final String PROP_TEXT = "dividerText";
  private static final String PROP_TRANSCRIPT_FONT = "dividerTranscriptFont";

  private final JLabel label = new JLabel();
  private final HistoryDividerComponent source;
  private Font transcriptFont;
  private final PropertyChangeListener sourceListener = evt -> syncFromSource();

  /**
   * Embedded components inside a JTextPane don't always get their colors refreshed when the
   * LookAndFeel is previewed/reverted (e.g., Preferences Cancel). Keep the divider's color synced
   * with UI defaults.
   */
  private final PropertyChangeListener uiDefaultsListener =
      evt -> {
        // Cheap enough to reapply for any UI defaults change.
        applyTheme();
      };

  public HistoryDividerComponent(String text) {
    this(text, null);
  }

  private HistoryDividerComponent(String text, HistoryDividerComponent source) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    this.source = source;
    setOpaque(false);

    label.setOpaque(false);
    label.setText(text == null ? "" : text);

    // Match transcript fonts as closely as we can.
    Font base =
        source != null ? source.transcriptFont : UIManager.getFont(UiFontKeys.TEXT_PANE_FONT);
    if (base == null) base = UIManager.getFont(UiFontKeys.LABEL_FONT);
    setTranscriptFont(base);

    applyTheme();
    add(label);
  }

  @Override
  public HistoryDividerComponent createComponent() {
    return new HistoryDividerComponent(getText(), this);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (source != null) {
      source.addPropertyChangeListener(PROP_TEXT, sourceListener);
      source.addPropertyChangeListener(PROP_TRANSCRIPT_FONT, sourceListener);
      syncFromSource();
    }
    try {
      UIManager.addPropertyChangeListener(uiDefaultsListener);
    } catch (Exception ignored) {
    }
    applyTheme();
  }

  @Override
  public void removeNotify() {
    if (source != null) {
      source.removePropertyChangeListener(PROP_TEXT, sourceListener);
      source.removePropertyChangeListener(PROP_TRANSCRIPT_FONT, sourceListener);
    }
    try {
      UIManager.removePropertyChangeListener(uiDefaultsListener);
    } catch (Exception ignored) {
    }
    super.removeNotify();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setOpaque(false);
    if (label != null) label.setOpaque(false);
    applyTheme();
  }

  public void setTranscriptFont(Font base) {
    if (base == null) return;
    Font previous = transcriptFont;
    transcriptFont = base;
    // Slightly smaller + italic so this reads as a separator, not a normal message.
    float size = Math.max(9f, base.getSize2D() - 1f);
    label.setFont(base.deriveFont(Font.ITALIC, size));
    firePropertyChange(PROP_TRANSCRIPT_FONT, previous, base);
  }

  public void setText(String text) {
    String previous = label.getText();
    label.setText(Objects.requireNonNullElse(text, ""));
    firePropertyChange(PROP_TEXT, previous, label.getText());
    revalidate();
    repaint();
  }

  public String getText() {
    return label.getText();
  }

  private void syncFromSource() {
    if (source == null) return;
    setText(source.getText());
    setTranscriptFont(source.transcriptFont);
  }

  /**
   * JTextPane embeds Swing components using a baseline-aware view. Provide a stable baseline
   * derived from our label so the divider aligns with normal text.
   */
  @Override
  public int getBaseline(int width, int height) {
    Insets in = getInsets();
    int ascent = 0;
    try {
      if (label.getFont() != null)
        ascent = Math.max(ascent, getFontMetrics(label.getFont()).getAscent());
    } catch (Exception ignored) {
    }
    if (ascent <= 0) return -1;
    return in.top + ascent;
  }

  @Override
  public java.awt.Component.BaselineResizeBehavior getBaselineResizeBehavior() {
    return java.awt.Component.BaselineResizeBehavior.CONSTANT_ASCENT;
  }

  private void applyTheme() {
    if (label == null) return;
    var dim = UIManager.getColor(UiColorKeys.LABEL_DISABLED_FOREGROUND);
    var fg = UIManager.getColor(UiColorKeys.TEXT_PANE_FOREGROUND);
    label.setForeground(dim != null ? dim : fg);
  }
}

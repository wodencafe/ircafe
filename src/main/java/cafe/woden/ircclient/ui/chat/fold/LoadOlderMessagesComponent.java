package cafe.woden.ircclient.ui.chat.fold;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * In-transcript control used to request loading older history.
 *
 * <p>This is rendered as an embedded Swing component inside the chat transcript document.
 */
public final class LoadOlderMessagesComponent extends JPanel {

  public enum State {
    READY,
    LOADING,
    EXHAUSTED
  }

  // Keep FlowLayout hgap at 0 so the control aligns with normal transcript lines.
  private final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
  private final JButton button = new JButton();

  private volatile BooleanSupplier onLoadRequested = () -> false;
  private volatile State state = State.READY;

  public LoadOlderMessagesComponent() {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    setOpaque(false);

    row.setOpaque(false);

    button.setOpaque(false);
    button.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    // Match transcript fonts as closely as we can.
    Font base = UIManager.getFont("TextPane.font");
    if (base == null) base = UIManager.getFont("Label.font");
    setTranscriptFont(base);

    applyTheme();
    setState(State.READY);

    button.addActionListener(e -> {
      if (state != State.READY) return;
      // Defensive: ignore non-left clicks or if disabled.
      if (!button.isEnabled()) return;
      requestLoadOnce();
    });

    row.add(button);
    add(row);

    setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
  }

  /** Apply the chat transcript font (typically the JTextPane font). */
  public void setTranscriptFont(Font base) {
    if (base == null) return;
    button.setFont(base);
  }

  /** Set the load handler. Must be safe to call from the EDT. */
  public void setOnLoadRequested(BooleanSupplier onLoadRequested) {
    this.onLoadRequested = Objects.requireNonNullElse(onLoadRequested, () -> false);
  }

  public State state() {
    return state;
  }

  public void setState(State s) {
    if (s == null) s = State.READY;
    this.state = s;

    switch (s) {
      case READY -> {
        button.setText("Load older messages…");
        button.setEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      case LOADING -> {
        button.setText("Loading…");
        button.setEnabled(false);
        button.setCursor(Cursor.getDefaultCursor());
      }
      case EXHAUSTED -> {
        button.setText("No older messages");
        button.setEnabled(false);
        button.setCursor(Cursor.getDefaultCursor());
      }
    }
    revalidate();
    repaint();
  }

  /**
   * JTextPane embeds Swing components using a baseline-aware view. Provide a stable baseline derived
   * from our button so the control aligns with normal text.
   */
  @Override
  public int getBaseline(int width, int height) {
    Insets in = getInsets();
    int ascent = 0;
    try {
      if (button.getFont() != null) ascent = Math.max(ascent, getFontMetrics(button.getFont()).getAscent());
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

  private void requestLoadOnce() {
    // Ensure this runs on EDT (the transcript click will already be EDT, but be safe).
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::requestLoadOnce);
      return;
    }
    try {
      boolean accepted = onLoadRequested != null && onLoadRequested.getAsBoolean();
      if (!accepted) {
        // If the handler declined (e.g., already loading), keep READY.
        setState(State.READY);
      }
    } catch (Exception ignored) {
      setState(State.READY);
    }
  }

  private void applyTheme() {
    Color fg = UIManager.getColor("TextPane.foreground");
    Color link = UIManager.getColor("Component.linkColor");
    if (link == null) link = UIManager.getColor("textHighlight");
    if (link == null) link = fg;
    button.setForeground(link);
  }
}

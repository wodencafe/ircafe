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
    EXHAUSTED,
    UNAVAILABLE
  }

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

    Font base = UIManager.getFont("TextPane.font");
    if (base == null) base = UIManager.getFont("Label.font");
    setTranscriptFont(base);

    applyTheme();
    setState(State.READY);

    button.addActionListener(e -> {
      if (state != State.READY) return;
      if (!button.isEnabled()) return;
      requestLoadOnce();
    });

    row.add(button);
    add(row);

    setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
  }

  
  public void setTranscriptFont(Font base) {
    if (base == null) return;
    button.setFont(base);
  }

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
        button.setToolTipText(null);
        button.setText("Load older messages…");
        button.setEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      case LOADING -> {
        button.setToolTipText(null);
        button.setText("Loading…");
        button.setEnabled(false);
        button.setCursor(Cursor.getDefaultCursor());
      }
      case EXHAUSTED -> {
        button.setToolTipText(null);
        button.setText("No older messages");
        button.setEnabled(false);
        button.setCursor(Cursor.getDefaultCursor());
      }
      case UNAVAILABLE -> {
        button.setText("History unavailable");
        button.setToolTipText("Server does not support IRCv3 CHATHISTORY.");
        button.setEnabled(false);
        button.setCursor(Cursor.getDefaultCursor());
      }
    }
    revalidate();
    repaint();
  }

  @Override
  public int getBaseline(int width, int height) {
    Insets in = getInsets();
    int ascent = 0;
    try {
      if (button.getFont() != null) ascent = Math.max(ascent, getFontMetrics(button.getFont()).getAscent());
    } catch (Exception ignored) {
    }
    if (ascent <= 0) return -1;
    return in.top + ascent;
  }

  @Override
  public java.awt.Component.BaselineResizeBehavior getBaselineResizeBehavior() {
    return java.awt.Component.BaselineResizeBehavior.CONSTANT_ASCENT;
  }

  private void requestLoadOnce() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::requestLoadOnce);
      return;
    }
    try {
      boolean accepted = onLoadRequested != null && onLoadRequested.getAsBoolean();
      if (!accepted) {
        setState(State.READY);
      }
    } catch (Exception ignored) {
      setState(State.READY);
    }
  }

  /**
   * Programmatically request a load as if the user clicked the control.
   *
   */
  public void requestLoad() {
    if (state != State.READY) return;
    requestLoadOnce();
  }

  private void applyTheme() {
    Color fg = UIManager.getColor("TextPane.foreground");
    Color link = UIManager.getColor("Component.linkColor");
    if (link == null) link = UIManager.getColor("textHighlight");
    if (link == null) link = fg;
    button.setForeground(link);
  }
}

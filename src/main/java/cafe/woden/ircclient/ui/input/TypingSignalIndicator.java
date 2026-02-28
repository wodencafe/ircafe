package cafe.woden.ircclient.ui.input;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import javax.swing.*;

/**
 * Small right-side telemetry icon showing typing-signal availability and sends.
 *
 * <p>Renders a subtle keyboard icon and themed chevron telemetry: active -> one double-chevron
 * group scanning left-to-right, paused -> ghost gray hold, done -> fade out.
 */
final class TypingSignalIndicator extends JComponent {

  private static final int FRAME_MS = 33;
  private static final int DONE_FADE_MS = 650;
  private static final long ACTIVE_SCAN_MS = 900L;
  private static final int CHEVRON_GROUPS = 5;
  private static final int CHEVRON_GROUP_STEP = 9;
  private static final int CHEVRON_WIDTH = 4;
  private static final int CHEVRON_PAIR_OFFSET = 4;
  private static final float PAUSED_ALPHA = 0.72f;

  private static final float KEYBOARD_ALPHA = 0.78f;
  private static final String KEYBOARD_TOOLTIP = "Typing indicators are enabled";

  private final Timer fadeTimer;

  private boolean available;
  private float arrowAlpha;
  private long modeStartMs;
  private float fadeStartAlpha;
  private Mode mode = Mode.IDLE;
  private SignalStyle style = SignalStyle.ACTIVE;

  TypingSignalIndicator() {
    setOpaque(false);
    setVisible(false);
    fadeTimer =
        new Timer(
            FRAME_MS,
            new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e) {
                onFadeTick();
              }
            });
    fadeTimer.setRepeats(true);
    // Enable per-region tooltips via getToolTipText(MouseEvent).
    setToolTipText("");
  }

  void setAvailable(boolean available) {
    this.available = available;
    if (!available) {
      fadeTimer.stop();
      arrowAlpha = 0f;
      mode = Mode.IDLE;
      setVisible(false);
    } else {
      setVisible(true);
    }
    repaint();
  }

  void pulse(String state) {
    if (!available) return;

    long now = System.currentTimeMillis();
    SignalEvent event = SignalEvent.fromState(state);
    switch (event) {
      case ACTIVE -> {
        style = SignalStyle.ACTIVE;
        mode = Mode.ACTIVE;
        modeStartMs = now;
        arrowAlpha = 1f;
        startTimerIfDisplayable();
      }
      case PAUSED -> {
        style = SignalStyle.GHOST;
        mode = Mode.PAUSED;
        arrowAlpha = PAUSED_ALPHA;
        fadeTimer.stop();
      }
      case DONE -> {
        if (arrowAlpha <= 0.01f) {
          mode = Mode.IDLE;
          fadeTimer.stop();
          break;
        }
        mode = Mode.FADING;
        modeStartMs = now;
        fadeStartAlpha = Math.max(0.01f, arrowAlpha);
        startTimerIfDisplayable();
      }
    }
    repaint();
  }

  boolean isArrowVisible() {
    return arrowAlpha > 0.01f;
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(66, 16);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (!available) return;

    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int h = getHeight();
      int y = Math.max(0, (h - 10) / 2);

      drawKeyboard(g2, 1, y, 14, 10);
      drawChevronWave(g2, 19, y + 1, 8);
    } finally {
      g2.dispose();
    }
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    if (!available || event == null) return null;
    Rectangle kb = keyboardBounds();
    return kb.contains(event.getPoint()) ? KEYBOARD_TOOLTIP : null;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (available && (mode == Mode.ACTIVE || mode == Mode.FADING) && !fadeTimer.isRunning()) {
      fadeTimer.start();
    }
  }

  @Override
  public void removeNotify() {
    fadeTimer.stop();
    super.removeNotify();
  }

  private void onFadeTick() {
    long now = System.currentTimeMillis();
    switch (mode) {
      case ACTIVE -> {
        // Active scan index is computed at paint time.
        arrowAlpha = 1f;
      }
      case FADING -> {
        long elapsed = now - modeStartMs;
        float t = Math.max(0f, Math.min(1f, (float) elapsed / (float) DONE_FADE_MS));
        float eased = 1f - (t * t);
        arrowAlpha = fadeStartAlpha * eased;
        if (t >= 1f) {
          arrowAlpha = 0f;
          mode = Mode.IDLE;
          fadeTimer.stop();
        }
      }
      default -> {
        fadeTimer.stop();
      }
    }
    repaint();
  }

  private void startTimerIfDisplayable() {
    if (isDisplayable() && !fadeTimer.isRunning()) {
      fadeTimer.start();
    }
  }

  private void drawKeyboard(Graphics2D g2, int x, int y, int w, int h) {
    Color base = keyboardColor();
    Color stroke = withAlpha(base, KEYBOARD_ALPHA);
    Color fill = withAlpha(base, 0.14f);

    RoundRectangle2D.Float body = new RoundRectangle2D.Float(x, y, w, h, 3, 3);
    g2.setColor(fill);
    g2.fill(body);
    g2.setColor(stroke);
    g2.setStroke(new BasicStroke(1f));
    g2.draw(body);

    int keyY1 = y + 3;
    int keyY2 = y + 6;
    int keyW = 2;
    int keyH = 1;
    int[] xs = {x + 3, x + 6, x + 9, x + 12};
    g2.setColor(withAlpha(base, 0.56f));
    for (int keyX : xs) {
      g2.fillRect(keyX - 1, keyY1, keyW, keyH);
    }
    g2.fillRect(x + 5, keyY2, 5, keyH);
  }

  private Rectangle keyboardBounds() {
    int h = getHeight();
    int y = Math.max(0, (h - 10) / 2);
    return new Rectangle(1, y, 14, 10);
  }

  private void drawChevronWave(Graphics2D g2, int x, int y, int h) {
    if (arrowAlpha <= 0.01f) return;

    Color core = (style == SignalStyle.ACTIVE) ? activeArrowColor() : ghostArrowColor();
    float glowFactor = (style == SignalStyle.ACTIVE) ? 0.40f : 0.14f;
    int hh = Math.max(6, h);
    int activeIndex = activeChevronIndex();

    for (int i = 0; i < CHEVRON_GROUPS; i++) {
      float scanAlpha = chevronScanAlpha(i, activeIndex);
      float groupAlpha = Math.max(0f, Math.min(1f, arrowAlpha * scanAlpha));
      if (groupAlpha <= 0.01f) continue;
      int gx = x + (i * CHEVRON_GROUP_STEP);
      Color glow = withAlpha(core, groupAlpha * glowFactor);
      Color stroke = withAlpha(core, groupAlpha);
      drawDoubleChevron(g2, gx, y, hh, glow, stroke);
    }
  }

  private int activeChevronIndex() {
    if (style != SignalStyle.ACTIVE || mode != Mode.ACTIVE) return -1;
    if (CHEVRON_GROUPS <= 0) return -1;
    long elapsed = Math.max(0L, System.currentTimeMillis() - modeStartMs);
    double phase = (elapsed % ACTIVE_SCAN_MS) / (double) ACTIVE_SCAN_MS;
    int idx = (int) Math.floor(phase * CHEVRON_GROUPS);
    if (idx < 0) idx = 0;
    if (idx >= CHEVRON_GROUPS) idx = CHEVRON_GROUPS - 1;
    return idx;
  }

  private float chevronScanAlpha(int index, int activeIndex) {
    if (style != SignalStyle.ACTIVE || mode != Mode.ACTIVE) return 1f;
    if (activeIndex < 0) return 0f;
    return index == activeIndex ? 1f : 0f;
  }

  private static void drawDoubleChevron(
      Graphics2D g2, int x, int y, int h, Color glow, Color stroke) {
    drawChevron(g2, x, y, h, glow, stroke);
    drawChevron(g2, x + CHEVRON_PAIR_OFFSET, y, h, glow, stroke);
  }

  private static void drawChevron(Graphics2D g2, int x, int y, int h, Color glow, Color stroke) {
    int mid = y + (h / 2);
    int right = x + CHEVRON_WIDTH;

    g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(glow);
    g2.drawLine(x, y, right, mid);
    g2.drawLine(x, y + h, right, mid);

    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(stroke);
    g2.drawLine(x, y, right, mid);
    g2.drawLine(x, y + h, right, mid);
  }

  private Color keyboardColor() {
    Color c = UIManager.getColor("Label.foreground");
    if (c == null) c = getForeground();
    if (c == null) c = Color.GRAY;
    return c;
  }

  private Color activeArrowColor() {
    Color accent = UIManager.getColor("@accentColor");
    if (accent == null) accent = UIManager.getColor("Component.accentColor");
    if (accent == null) accent = UIManager.getColor("Component.focusColor");
    if (accent == null) accent = new Color(0x4B8DE8);
    return themedChevronTint(accent);
  }

  private Color ghostArrowColor() {
    Color ghost = UIManager.getColor("Label.disabledForeground");
    if (ghost != null) return ghost;
    return new Color(0x9AA0A6);
  }

  private static Color withAlpha(Color c, float alpha) {
    int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
  }

  private static Color themedChevronTint(Color accent) {
    float[] hsb = Color.RGBtoHSB(accent.getRed(), accent.getGreen(), accent.getBlue(), null);
    float hue = hsb[0];
    // Warm red accents shift slightly toward magenta/pink for better neon readability.
    if (hue <= 0.08f || hue >= 0.92f) {
      hue = 0.92f;
    }
    float sat = clamp(0.26f, 0.60f, hsb[1] * 0.70f + 0.10f);
    float bri = clamp(0.90f, 1.00f, hsb[2] * 0.85f + 0.20f);
    Color base = Color.getHSBColor(hue, sat, bri);
    return mix(base, Color.WHITE, 0.14f);
  }

  private static Color mix(Color a, Color b, double t) {
    double p = Math.max(0d, Math.min(1d, t));
    int r = (int) Math.round(a.getRed() * (1d - p) + b.getRed() * p);
    int g = (int) Math.round(a.getGreen() * (1d - p) + b.getGreen() * p);
    int bb = (int) Math.round(a.getBlue() * (1d - p) + b.getBlue() * p);
    return new Color(r, g, bb);
  }

  private static float clamp(float min, float max, float v) {
    return Math.max(min, Math.min(max, v));
  }

  private enum SignalStyle {
    ACTIVE,
    GHOST
  }

  private enum SignalEvent {
    ACTIVE,
    PAUSED,
    DONE;

    static SignalEvent fromState(String state) {
      String s = (state == null) ? "" : state.trim().toLowerCase(Locale.ROOT);
      return switch (s) {
        case "paused" -> PAUSED;
        case "done", "inactive" -> DONE;
        default -> ACTIVE;
      };
    }
  }

  private enum Mode {
    IDLE,
    ACTIVE,
    PAUSED,
    FADING
  }
}

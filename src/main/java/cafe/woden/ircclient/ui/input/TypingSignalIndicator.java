package cafe.woden.ircclient.ui.input;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Path2D;
import java.util.Locale;
import javax.swing.*;

/**
 * Overlay icon that animates local typing-send state above the send button.
 *
 * <p>Behavior:
 *
 * <ul>
 *   <li>Unavailable/default: steady theme-aware arrow (dark on light themes, white on dark themes).
 *   <li>Idle (typing available): steady green arrow.
 *   <li>Active: glowing blue arrow pulse.
 *   <li>Paused: light gray arrow (fades from active).
 *   <li>Done: fade back to idle green.
 * </ul>
 */
final class TypingSignalIndicator extends JComponent {

  private static final int FRAME_MS = 33;
  private static final long ACTIVE_PULSE_MS = 1050L;
  private static final int PAUSE_FADE_MS = 240;
  private static final int RETURN_TO_IDLE_MS = 420;
  private static final float IDLE_GLOW_ALPHA = 0.12f;
  private static final Color DARK_THEME_FALLBACK_ARROW = new Color(0xFFFFFF);
  private static final Color LIGHT_THEME_FALLBACK_ARROW = new Color(0x2B313A);

  private final Timer fadeTimer;

  private boolean available;
  private long modeStartMs;
  private Color transitionFromColor = idleGreenColor();
  private float transitionFromGlow = 0f;
  private Mode mode = Mode.IDLE;

  TypingSignalIndicator() {
    setOpaque(false);
    setVisible(true);
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
  }

  void setAvailable(boolean available) {
    if (this.available == available) {
      if (!available) {
        fadeTimer.stop();
        mode = Mode.IDLE;
      }
      setVisible(true);
      repaint();
      return;
    }
    this.available = available;
    if (!available) {
      fadeTimer.stop();
      mode = Mode.IDLE;
    } else {
      mode = Mode.IDLE;
      modeStartMs = System.currentTimeMillis();
    }
    setVisible(true);
    repaint();
  }

  void pulse(String state) {
    if (!available) return;

    long now = System.currentTimeMillis();
    SignalEvent event = SignalEvent.fromState(state);
    switch (event) {
      case ACTIVE -> {
        mode = Mode.ACTIVE;
        modeStartMs = now;
        startTimerIfDisplayable();
      }
      case PAUSED -> {
        beginPauseTransition(now);
      }
      case DONE -> {
        beginReturnToIdle(now);
      }
    }
    repaint();
  }

  boolean isArrowVisible() {
    return isVisible();
  }

  @Override
  public boolean contains(int x, int y) {
    // Keep clicks/hover targeted to the underlying send button.
    return false;
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(18, 18);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      ArrowVisual visual = visualAt(System.currentTimeMillis());
      drawRightArrow(g2, visual);
    } finally {
      g2.dispose();
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (available
        && (mode == Mode.ACTIVE || mode == Mode.PAUSING || mode == Mode.RETURNING)
        && !fadeTimer.isRunning()) {
      fadeTimer.start();
    }
  }

  @Override
  public void removeNotify() {
    fadeTimer.stop();
    super.removeNotify();
  }

  private void onFadeTick() {
    if (!available) {
      fadeTimer.stop();
      mode = Mode.IDLE;
      return;
    }
    if (mode == Mode.PAUSING) {
      long elapsed = Math.max(0L, System.currentTimeMillis() - modeStartMs);
      if (elapsed >= PAUSE_FADE_MS) {
        mode = Mode.PAUSED;
        fadeTimer.stop();
      }
    } else if (mode == Mode.RETURNING) {
      long elapsed = Math.max(0L, System.currentTimeMillis() - modeStartMs);
      if (elapsed >= RETURN_TO_IDLE_MS) {
        mode = Mode.IDLE;
        fadeTimer.stop();
      }
    } else if (mode != Mode.ACTIVE) {
      fadeTimer.stop();
    }
    repaint();
  }

  private void startTimerIfDisplayable() {
    if (isDisplayable() && !fadeTimer.isRunning()) {
      fadeTimer.start();
    }
  }

  private void drawRightArrow(Graphics2D g2, ArrowVisual visual) {
    int w = Math.max(8, getWidth());
    int h = Math.max(8, getHeight());
    int left = Math.max(2, w / 6);
    int right = Math.max(left + 4, w - Math.max(3, w / 6));
    int midY = h / 2;
    int head = Math.max(3, Math.min(5, h / 4));
    int shaftEnd = Math.max(left + 1, right - head);

    Path2D.Float path = new Path2D.Float();
    path.moveTo(left, midY);
    path.lineTo(shaftEnd, midY);
    path.moveTo(shaftEnd - 1, midY - head);
    path.lineTo(right, midY);
    path.lineTo(shaftEnd - 1, midY + head);

    if (visual.glowAlpha() > 0.01f) {
      g2.setStroke(new BasicStroke(5.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.setColor(withAlpha(visual.color(), visual.glowAlpha()));
      g2.draw(path);
    }

    g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(withAlpha(visual.color(), 0.98f));
    g2.draw(path);
  }

  private void beginReturnToIdle(long now) {
    if (mode == Mode.IDLE) {
      fadeTimer.stop();
      return;
    }
    ArrowVisual current = visualAt(now);
    transitionFromColor = current.color();
    transitionFromGlow = current.glowAlpha();
    mode = Mode.RETURNING;
    modeStartMs = now;
    startTimerIfDisplayable();
  }

  private void beginPauseTransition(long now) {
    if (mode == Mode.PAUSED) {
      fadeTimer.stop();
      return;
    }
    ArrowVisual current = visualAt(now);
    transitionFromColor = current.color();
    transitionFromGlow = current.glowAlpha();
    mode = Mode.PAUSING;
    modeStartMs = now;
    startTimerIfDisplayable();
  }

  private ArrowVisual visualAt(long now) {
    if (!available) {
      return new ArrowVisual(unavailableFallbackColor(), 0f);
    }
    if (mode == Mode.PAUSING) {
      float t = clamp01((float) (Math.max(0L, now - modeStartMs) / (double) PAUSE_FADE_MS));
      float eased = easeOutCubic(t);
      Color c = mix(transitionFromColor, pausedGrayColor(), eased);
      float glow = transitionFromGlow * (1f - eased);
      return new ArrowVisual(c, glow);
    }
    if (mode == Mode.PAUSED) {
      return new ArrowVisual(pausedGrayColor(), 0f);
    }
    if (mode == Mode.ACTIVE) {
      float pulse = activePulse(now);
      Color c = mix(activeBlueBaseColor(), activeBluePeakColor(), 0.45f + (0.55f * pulse));
      float glow = 0.20f + (0.46f * pulse);
      return new ArrowVisual(c, glow);
    }
    if (mode == Mode.RETURNING) {
      float t = clamp01((float) (Math.max(0L, now - modeStartMs) / (double) RETURN_TO_IDLE_MS));
      float eased = easeOutCubic(t);
      Color c = mix(transitionFromColor, idleGreenColor(), eased);
      float glow = transitionFromGlow * (1f - eased);
      return new ArrowVisual(c, glow);
    }
    return new ArrowVisual(idleGreenColor(), IDLE_GLOW_ALPHA);
  }

  private float activePulse(long now) {
    long elapsed = Math.max(0L, now - modeStartMs);
    double phase = (elapsed % ACTIVE_PULSE_MS) / (double) ACTIVE_PULSE_MS;
    return (float) (0.5d + 0.5d * Math.sin(phase * Math.PI * 2d));
  }

  private static Color idleGreenColor() {
    return new Color(0x35C86E);
  }

  private Color unavailableFallbackColor() {
    Color bg = resolveFallbackBackgroundColor();
    if (isDark(bg)) return DARK_THEME_FALLBACK_ARROW;

    Color fg = UIManager.getColor("Label.foreground");
    if (fg == null) fg = UIManager.getColor("TextField.foreground");
    if (fg != null && isDark(fg)) return fg;
    return LIGHT_THEME_FALLBACK_ARROW;
  }

  private static Color activeBlueBaseColor() {
    return new Color(0x52A6FF);
  }

  private static Color activeBluePeakColor() {
    return new Color(0x7CC4FF);
  }

  private static Color pausedGrayColor() {
    return new Color(0xC6CDD5);
  }

  private static Color withAlpha(Color c, float alpha) {
    int a = (int) Math.round(clamp01(alpha) * 255f);
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
  }

  private static Color mix(Color a, Color b, double t) {
    double p = Math.max(0d, Math.min(1d, t));
    int r = (int) Math.round(a.getRed() * (1d - p) + b.getRed() * p);
    int g = (int) Math.round(a.getGreen() * (1d - p) + b.getGreen() * p);
    int bb = (int) Math.round(a.getBlue() * (1d - p) + b.getBlue() * p);
    return new Color(r, g, bb);
  }

  private static float clamp01(float v) {
    return Math.max(0f, Math.min(1f, v));
  }

  private static float easeOutCubic(float t) {
    float x = 1f - clamp01(t);
    return 1f - (x * x * x);
  }

  private Color resolveFallbackBackgroundColor() {
    for (Container c = getParent(); c != null; c = c.getParent()) {
      Color bg = c.getBackground();
      if (bg == null) continue;
      if (c instanceof JComponent jc && !jc.isOpaque()) continue;
      return bg;
    }

    Color bg = UIManager.getColor("TextField.background");
    if (bg == null) bg = UIManager.getColor("TextPane.background");
    if (bg == null) bg = UIManager.getColor("Panel.background");
    if (bg == null) bg = UIManager.getColor("control");
    return bg;
  }

  private static boolean isDark(Color c) {
    if (c == null) return true;
    return relativeLuminance(c) < 0.45;
  }

  private static double relativeLuminance(Color c) {
    double r = srgbToLinear(c.getRed() / 255.0);
    double g = srgbToLinear(c.getGreen() / 255.0);
    double b = srgbToLinear(c.getBlue() / 255.0);
    return 0.2126d * r + 0.7152d * g + 0.0722d * b;
  }

  private static double srgbToLinear(double v) {
    return (v <= 0.04045d) ? (v / 12.92d) : Math.pow((v + 0.055d) / 1.055d, 2.4d);
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
    PAUSING,
    PAUSED,
    RETURNING
  }

  private record ArrowVisual(Color color, float glowAlpha) {}

  Color debugArrowColorForTest() {
    return visualAt(System.currentTimeMillis()).color();
  }

  float debugArrowGlowForTest() {
    return visualAt(System.currentTimeMillis()).glowAlpha();
  }
}

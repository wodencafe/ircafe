package cafe.woden.ircclient.ui.input;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

/** Small animated three-dot indicator used for "is typing" activity. */
final class TypingDotsIndicator extends JComponent {

  private static final int DOT_COUNT = 3;
  private static final int FRAME_MS = 240;

  private final Timer animationTimer;
  private boolean animating;
  private int phase;

  TypingDotsIndicator() {
    setOpaque(false);
    setVisible(false);
    animationTimer =
        new Timer(
            FRAME_MS,
            new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e) {
                phase = (phase + 1) % DOT_COUNT;
                repaint();
              }
            });
    animationTimer.setRepeats(true);
  }

  void startAnimation() {
    if (animating) return;
    animating = true;
    phase = 0;
    if (isDisplayable()) {
      animationTimer.start();
    }
    repaint();
  }

  void stopAnimation() {
    animating = false;
    animationTimer.stop();
    phase = 0;
    repaint();
  }

  boolean isAnimating() {
    return animating;
  }

  @Override
  public Dimension getPreferredSize() {
    int d = dotDiameter();
    int gap = dotGap(d);
    int w = DOT_COUNT * d + (DOT_COUNT - 1) * gap;
    int h = Math.max(d, fontHeight());
    return new Dimension(w, h);
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

      int d = dotDiameter();
      int gap = dotGap(d);
      int total = DOT_COUNT * d + (DOT_COUNT - 1) * gap;
      int x = Math.max(0, (getWidth() - total) / 2);
      int y = Math.max(0, (getHeight() - d) / 2);

      Color base = resolveBaseColor();
      for (int i = 0; i < DOT_COUNT; i++) {
        float alpha = animating ? (i == phase ? 1.0f : 0.35f) : 0.35f;
        g2.setColor(withAlpha(base, alpha));
        g2.fillOval(x + i * (d + gap), y, d, d);
      }
    } finally {
      g2.dispose();
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (animating && !animationTimer.isRunning()) {
      animationTimer.start();
    }
  }

  @Override
  public void removeNotify() {
    animationTimer.stop();
    super.removeNotify();
  }

  private int fontHeight() {
    Font f = getFont();
    if (f == null) return 12;
    FontMetrics fm = getFontMetrics(f);
    return fm != null ? fm.getHeight() : 12;
  }

  private int dotDiameter() {
    int h = fontHeight();
    return Math.max(4, Math.min(8, (int) Math.round(h * 0.34)));
  }

  private static int dotGap(int dotDiameter) {
    return Math.max(2, dotDiameter / 2);
  }

  private Color resolveBaseColor() {
    Color fg = getForeground();
    if (fg == null) fg = UIManager.getColor("Label.foreground");
    if (fg == null) fg = Color.GRAY;
    return fg;
  }

  private static Color withAlpha(Color c, float alpha) {
    int a = Math.max(0, Math.min(255, Math.round(alpha * c.getAlpha())));
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
  }
}

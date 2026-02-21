package cafe.woden.ircclient.ui.chat;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Small inline status indicator used for outbound message delivery.
 *
 * <p>We keep this intentionally lightweight: it is embedded into the chat transcript as a
 * {@link javax.swing.text.StyleConstants#setComponent(javax.swing.text.MutableAttributeSet, java.awt.Component)}
 * and painted inline by the {@link javax.swing.JTextPane}.</p>
 */
public final class OutgoingSendIndicator {

  private OutgoingSendIndicator() {
  }

  /** Animated spinner that runs indefinitely while the component is displayable. */
  public static final class PendingSpinner extends JComponent {
    private static final int DEFAULT_SIZE = 12;
    private static final int INSET_L = 4;
    private static final int INSET_R = 2;

    private final Color color;
    private final Timer timer;
    private float angleDeg = 0f;

    public PendingSpinner(Color color) {
      this.color = color;
      setOpaque(false);
      // ~30 FPS is plenty for a tiny inline spinner.
      this.timer = new Timer(33, e -> {
        angleDeg += 18f;
        if (angleDeg >= 360f) angleDeg -= 360f;
        repaint();
      });
      this.timer.setRepeats(true);
    }

    private int inlineHeight() {
      try {
        if (getFont() != null) {
          int h = getFontMetrics(getFont()).getHeight();
          if (h > 0) return Math.max(DEFAULT_SIZE, h);
        }
      } catch (Exception ignored) {
      }
      return DEFAULT_SIZE;
    }

    private int inlineAscent() {
      try {
        if (getFont() != null) {
          int a = getFontMetrics(getFont()).getAscent();
          if (a > 0) return a;
        }
      } catch (Exception ignored) {
      }
      return -1;
    }

    @Override
    public Dimension getPreferredSize() {
      int h = inlineHeight();
      int w = DEFAULT_SIZE + INSET_L + INSET_R;
      return new Dimension(w, h);
    }

    @Override
    public int getBaseline(int width, int height) {
      int ascent = inlineAscent();
      if (ascent <= 0 || height <= 0) return -1;
      return Math.max(0, Math.min(height - 1, ascent));
    }

    @Override
    public java.awt.Component.BaselineResizeBehavior getBaselineResizeBehavior() {
      return java.awt.Component.BaselineResizeBehavior.CONSTANT_ASCENT;
    }

    @Override
    public float getAlignmentY() {
      int h = inlineHeight();
      int ascent = inlineAscent();
      if (h <= 0 || ascent <= 0) return super.getAlignmentY();
      return Math.max(0f, Math.min(1f, (float) ascent / (float) h));
    }

    @Override
    public void addNotify() {
      super.addNotify();
      if (!timer.isRunning()) timer.start();
    }

    @Override
    public void removeNotify() {
      try {
        timer.stop();
      } catch (Exception ignored) {
      }
      super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int h = getHeight();
        int size = Math.min(DEFAULT_SIZE, h - 2);
        int x = INSET_L;
        int y = (h - size) / 2;

        Color c = color != null ? color : getForeground();
        if (c == null) c = Color.GRAY;
        g2.setColor(c);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Draw an arc segment. Rotate by angleDeg to animate.
        int start = (int) angleDeg;
        g2.drawArc(x, y, size, size, start, 270);
      } finally {
        g2.dispose();
      }
    }
  }

  /**
   * Green dot that fades out, then runs an optional callback (e.g. to remove itself from a document).
   */
  public static final class ConfirmedDot extends JComponent {
    private static final int DEFAULT_SIZE = 10;
    private static final int INSET_L = 4;
    private static final int INSET_R = 2;

    private final Color dotColor;
    private final int holdMs;
    private final int fadeMs;
    private final Runnable onFinished;
    private Timer timer;

    private long startMs;
    private float alpha = 1f;

    public ConfirmedDot(Color dotColor, int holdMs, int fadeMs, Runnable onFinished) {
      this.dotColor = dotColor;
      this.holdMs = Math.max(0, holdMs);
      this.fadeMs = Math.max(1, fadeMs);
      this.onFinished = onFinished;
      setOpaque(false);
    }

    private int inlineHeight() {
      try {
        if (getFont() != null) {
          int h = getFontMetrics(getFont()).getHeight();
          if (h > 0) return Math.max(DEFAULT_SIZE, h);
        }
      } catch (Exception ignored) {
      }
      return DEFAULT_SIZE;
    }

    private int inlineAscent() {
      try {
        if (getFont() != null) {
          int a = getFontMetrics(getFont()).getAscent();
          if (a > 0) return a;
        }
      } catch (Exception ignored) {
      }
      return -1;
    }

    @Override
    public Dimension getPreferredSize() {
      int h = inlineHeight();
      int w = DEFAULT_SIZE + INSET_L + INSET_R;
      return new Dimension(w, h);
    }

    @Override
    public int getBaseline(int width, int height) {
      int ascent = inlineAscent();
      if (ascent <= 0 || height <= 0) return -1;
      return Math.max(0, Math.min(height - 1, ascent));
    }

    @Override
    public java.awt.Component.BaselineResizeBehavior getBaselineResizeBehavior() {
      return java.awt.Component.BaselineResizeBehavior.CONSTANT_ASCENT;
    }

    @Override
    public float getAlignmentY() {
      int h = inlineHeight();
      int ascent = inlineAscent();
      if (h <= 0 || ascent <= 0) return super.getAlignmentY();
      return Math.max(0f, Math.min(1f, (float) ascent / (float) h));
    }

    @Override
    public void addNotify() {
      super.addNotify();
      startAnimationIfNeeded();
    }

    private void startAnimationIfNeeded() {
      if (timer != null && timer.isRunning()) return;
      startMs = System.currentTimeMillis();
      timer = new Timer(33, new FadeTick());
      timer.setRepeats(true);
      timer.start();
    }

    @Override
    public void removeNotify() {
      try {
        if (timer != null) timer.stop();
      } catch (Exception ignored) {
      }
      super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));

        int h = getHeight();
        int size = Math.min(DEFAULT_SIZE, h - 2);
        int x = INSET_L;
        int y = (h - size) / 2;

        Color c = dotColor != null ? dotColor : new Color(0x2ecc71);
        g2.setColor(c);
        g2.fillOval(x, y, size, size);
      } finally {
        g2.dispose();
      }
    }

    private final class FadeTick implements ActionListener {
      @Override
      public void actionPerformed(ActionEvent e) {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - startMs);

        if (elapsed <= holdMs) {
          alpha = 1f;
          repaint();
          return;
        }

        long t = elapsed - holdMs;
        float p = Math.min(1f, (float) t / (float) fadeMs);
        alpha = 1f - p;
        repaint();

        if (p >= 1f) {
          try {
            timer.stop();
          } catch (Exception ignored) {
          }
          if (onFinished != null) {
            SwingUtilities.invokeLater(onFinished);
          }
        }
      }
    }
  }
}

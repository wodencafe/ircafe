package cafe.woden.ircclient.ui.chat.embed;

import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.Timer;

/**
 * Lightweight Swing Timer-based player for pre-decoded GIF frames.
 *
 */
final class AnimatedGifPlayer {

  private final JLabel target;
  private List<ImageIcon> frames = List.of();
  private int[] delaysMs = new int[0];

  private int idx = 0;
  private Timer timer;

  AnimatedGifPlayer(JLabel target) {
    this.target = target;
  }

  void setFrames(List<ImageIcon> frames, int[] delaysMs) {
    stop();

    this.frames = frames != null ? frames : List.of();
    this.delaysMs = delaysMs != null ? delaysMs : new int[0];
    this.idx = 0;

    if (!this.frames.isEmpty()) {
      target.setIcon(this.frames.get(0));
      target.setText("");
    }
  }

  void start() {
    if (frames.isEmpty()) return;
    if (timer != null && timer.isRunning()) return;

    int d0 = delayForIndex(0);
    timer = new Timer(d0, e -> {
      if (frames.isEmpty()) return;
      idx = (idx + 1) % frames.size();
      target.setIcon(frames.get(idx));
      timer.setDelay(delayForIndex(idx));
    });
    timer.setRepeats(true);
    timer.start();
  }

  void stop() {
    if (timer != null) {
      try {
        timer.stop();
      } catch (Exception ignored) {
      }
      timer = null;
    }
  }

  private int delayForIndex(int i) {
    if (delaysMs == null || delaysMs.length == 0) return 80;
    if (i < 0 || i >= delaysMs.length) return 80;
    int d = delaysMs[i];
    if (d <= 0) d = 80;
    if (d < 20) d = 20;
    return d;
  }
}

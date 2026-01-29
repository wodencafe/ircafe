package cafe.woden.ircclient.ui.util;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

/**
 * Adds consistent hover + click behavior to a chat transcript {@link JTextPane}.
 *
 * <p>Hover behavior:
 * <ul>
 *   <li>Sets hand cursor when the mouse is over a URL, channel token, or nick token</li>
 *   <li>Otherwise uses the provided text cursor</li>
 * </ul>
 *
 * <p>Click behavior (left click):
 * <ol>
 *   <li>If over URL: openUrl.accept(url)</li>
 *   <li>If over channel: onChannelClicked.test(channel) (if true, consume)</li>
 *   <li>If over nick: onNickClicked.test(nick) (if true, consume)</li>
 *   <li>Else: onTranscriptClicked.run()</li>
 * </ol>
 */
public final class ChatTranscriptMouseDecorator implements AutoCloseable {

  private final JTextPane transcript;
  private final MouseAdapter motion;
  private final MouseAdapter click;

  private ChatTranscriptMouseDecorator(
      JTextPane transcript,
      Cursor handCursor,
      Cursor textCursor,
      Function<Point, String> urlAt,
      Function<Point, String> channelAt,
      Function<Point, String> nickAt,
      Consumer<String> openUrl,
      Predicate<String> onChannelClicked,
      Predicate<String> onNickClicked,
      Runnable onTranscriptClicked
  ) {
    this.transcript = transcript;

    this.motion = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        if (transcript == null) return;
        String url = safeHit(urlAt, e.getPoint());
        String ch = safeHit(channelAt, e.getPoint());
        String nick = safeHit(nickAt, e.getPoint());
        transcript.setCursor((url != null || ch != null || nick != null) ? handCursor : textCursor);
      }
    };

    this.click = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (transcript == null) return;
        if (!SwingUtilities.isLeftMouseButton(e)) return;

        String url = safeHit(urlAt, e.getPoint());
        if (url != null) {
          if (openUrl != null) openUrl.accept(url);
          return;
        }

        String ch = safeHit(channelAt, e.getPoint());
        if (ch != null && onChannelClicked != null) {
          try {
            if (onChannelClicked.test(ch)) return;
          } catch (Exception ignored) {
          }
        }

        String nick = safeHit(nickAt, e.getPoint());
        if (nick != null && onNickClicked != null) {
          try {
            if (onNickClicked.test(nick)) return;
          } catch (Exception ignored) {
          }
        }

        if (onTranscriptClicked != null) {
          try {
            onTranscriptClicked.run();
          } catch (Exception ignored) {
          }
        }
      }
    };

    transcript.addMouseMotionListener(this.motion);
    transcript.addMouseListener(this.click);
  }

  public static ChatTranscriptMouseDecorator decorate(
      JTextPane transcript,
      Cursor handCursor,
      Cursor textCursor,
      Function<Point, String> urlAt,
      Function<Point, String> channelAt,
      Function<Point, String> nickAt,
      Consumer<String> openUrl,
      Predicate<String> onChannelClicked,
      Predicate<String> onNickClicked,
      Runnable onTranscriptClicked
  ) {
    if (transcript == null) {
      throw new IllegalArgumentException("transcript must not be null");
    }
    Cursor hand = (handCursor != null) ? handCursor : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    Cursor text = (textCursor != null) ? textCursor : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
    return new ChatTranscriptMouseDecorator(
        transcript,
        hand,
        text,
        urlAt,
        channelAt,
        nickAt,
        openUrl,
        onChannelClicked,
        onNickClicked,
        onTranscriptClicked
    );
  }

  private static String safeHit(Function<Point, String> f, Point p) {
    if (f == null || p == null) return null;
    try {
      return f.apply(p);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public void close() {
    try {
      transcript.removeMouseMotionListener(motion);
    } catch (Exception ignored) {
    }
    try {
      transcript.removeMouseListener(click);
    } catch (Exception ignored) {
    }
  }
}

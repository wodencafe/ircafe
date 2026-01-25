package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory transcripts for each IRC target.
 *
 * <p>Store-only: no Swing components, so multiple chat views can share the same
 * document instance.
 */
@Component
@Lazy
public class ChatTranscriptStore {

  private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("HH:mm");

  private final Map<TargetRef, StyledDocument> docs = new ConcurrentHashMap<>();
  private final ChatStyles styles;
  private final ChatRichTextRenderer renderer;

  public ChatTranscriptStore(ChatStyles styles, ChatRichTextRenderer renderer) {
    this.styles = styles;
    this.renderer = renderer;
  }

  public void ensureTargetExists(TargetRef target) {
    if (target == null) return;
    docs.computeIfAbsent(target, t -> new DefaultStyledDocument());
  }

  public StyledDocument document(TargetRef target) {
    ensureTargetExists(target);
    return docs.get(target);
  }

  public void closeTarget(TargetRef target) {
    if (target == null) return;
    docs.remove(target);
  }

  public void appendChat(TargetRef target, String from, String text) {
    appendLine(target, from, text, styles.from(), styles.message());
  }

  public void appendNotice(TargetRef target, String from, String text) {
    appendLine(target, from, text, styles.noticeFrom(), styles.noticeMessage());
  }

  public void appendStatus(TargetRef target, String from, String text) {
    appendLine(target, from, text, null, styles.status());
  }

  public void appendError(TargetRef target, String from, String text) {
    appendLine(target, from, text, styles.error(), styles.error());
  }

  private void appendLine(TargetRef target, String from, String msg,
                          javax.swing.text.AttributeSet fromStyle,
                          javax.swing.text.AttributeSet msgStyle) {
    if (target == null) return;
    String f = Objects.toString(from, "").trim();
    String m = Objects.toString(msg, "");

    StyledDocument doc = document(target);

    try {
      String ts = LocalTime.now().format(TS_FMT);
      doc.insertString(doc.getLength(), "[" + ts + "] ", styles.timestamp());

      if (!f.isEmpty()) {
        doc.insertString(doc.getLength(), f + ": ", fromStyle != null ? fromStyle : styles.from());
      }

      renderer.insertRichText(doc, target.serverId(), m, msgStyle != null ? msgStyle : styles.message());
      doc.insertString(doc.getLength(), "\n", styles.timestamp());

    } catch (BadLocationException e) {
      // Fail soft: insert raw text.
      try {
        doc.insertString(doc.getLength(), "[render-error] " + e + "\n", styles.error());
      } catch (BadLocationException ignored) {
      }
    }
  }
}

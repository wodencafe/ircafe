package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.embed.ChatImageEmbedder;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.util.HashMap;
import java.util.Map;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyleConstants;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Holds the StyledDocument transcripts for each (serverId, target) pair.
 */
@Component
@Lazy
public class ChatTranscriptStore {

  private final ChatStyles styles;
  private final ChatRichTextRenderer renderer;
  private final ChatTimestampFormatter ts;
  private final NickColorService nickColors;
  private final ChatImageEmbedder imageEmbeds;
  private final UiSettingsBus uiSettings;

  private final Map<TargetRef, StyledDocument> docs = new HashMap<>();

  public ChatTranscriptStore(
      ChatStyles styles,
      ChatRichTextRenderer renderer,
      ChatTimestampFormatter ts,
      NickColorService nickColors,
      ChatImageEmbedder imageEmbeds,
      UiSettingsBus uiSettings
  ) {
    this.styles = styles;
    this.renderer = renderer;
    this.ts = ts;
    this.nickColors = nickColors;
    this.imageEmbeds = imageEmbeds;
    this.uiSettings = uiSettings;
  }

  public synchronized void ensureTargetExists(TargetRef ref) {
    docs.computeIfAbsent(ref, r -> new DefaultStyledDocument());
  }

  public synchronized StyledDocument document(TargetRef ref) {
    return docs.computeIfAbsent(ref, r -> new DefaultStyledDocument());
  }

  public synchronized void appendPlain(TargetRef ref, String text) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    try {
      doc.insertString(doc.getLength(), text, styles.message());
    } catch (Exception ignored) {
    }
  }

  public synchronized void closeTarget(TargetRef ref) {
    if (ref == null) return;
    docs.remove(ref);
  }

  public synchronized void appendLine(TargetRef ref,
                                      String from,
                                      String text,
                                      AttributeSet fromStyle,
                                      AttributeSet msgStyle) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);

    try {
      AttributeSet baseForId = msgStyle != null ? msgStyle : styles.message();
      Object styleIdObj = baseForId.getAttribute(ChatStyles.ATTR_STYLE);
      String styleId = styleIdObj != null ? String.valueOf(styleIdObj) : null;

      if (ts != null && ts.enabled()
          && (ChatStyles.STYLE_STATUS.equals(styleId)
 || ChatStyles.STYLE_ERROR.equals(styleId)
 || ChatStyles.STYLE_NOTICE_MESSAGE.equals(styleId))) {
        doc.insertString(doc.getLength(), ts.prefixNow(), styles.timestamp());
      }

      if (from != null && !from.isBlank()) {
        doc.insertString(doc.getLength(), from + ": ", fromStyle != null ? fromStyle : styles.from());
      }

      AttributeSet base = msgStyle != null ? msgStyle : styles.message();
      renderer.insertRichText(doc, ref, text, base);

      doc.insertString(doc.getLength(), "\n", styles.timestamp());

      // After the line, optionally embed any image URLs found in the message.
      // This keeps the raw URL text visible but also shows a thumbnail block.
      if (imageEmbeds != null && uiSettings != null && uiSettings.get().imageEmbedsEnabled()) {
        imageEmbeds.appendEmbeds(doc, text);
      }
    } catch (Exception ignored) {
    }
  }

  public void appendChat(TargetRef ref, String from, String text) {
    AttributeSet fromStyle = styles.from();
    if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
      fromStyle = nickColors.forNick(from, fromStyle);
    }
    appendLine(ref, from, text, fromStyle, styles.message());
  }

  public void appendNotice(TargetRef ref, String from, String text) {
    appendLine(ref, from, text, styles.noticeFrom(), styles.noticeMessage());
  }

  public void appendStatus(TargetRef ref, String from, String text) {
    // Status messages are dim/italic; apply the same style to the prefix and body.
    appendLine(ref, from, text, styles.status(), styles.status());
  }

  public void appendError(TargetRef ref, String from, String text) {
    appendLine(ref, from, text, styles.error(), styles.error());
  }

  /**
   * Restyle all open transcripts using the current {@link ChatStyles}.
   *
   * <p>This relies on {@link ChatStyles#ATTR_STYLE} markers that are attached to inserted segments.
   */
  public synchronized void restyleAllDocuments() {
    for (StyledDocument doc : docs.values()) {
      restyle(doc);
    }
  }

  private void restyle(StyledDocument doc) {
    if (doc == null) return;

    int len = doc.getLength();
    int offset = 0;

    while (offset < len) {
      Element el = doc.getCharacterElement(offset);
      if (el == null) break;

      int start = el.getStartOffset();
      int end = Math.min(el.getEndOffset(), len);
      if (end <= start) {
        offset = Math.min(len, offset + 1);
        continue;
      }

      AttributeSet old = el.getAttributes();
      Object styleIdObj = old.getAttribute(ChatStyles.ATTR_STYLE);
      String styleId = styleIdObj != null ? String.valueOf(styleIdObj) : null;

      SimpleAttributeSet fresh = new SimpleAttributeSet(styles.byStyleId(styleId));

      // Preserve URL metadata used for clickable links.
      Object url = old.getAttribute(ChatStyles.ATTR_URL);
      if (url != null) {
        fresh.addAttribute(ChatStyles.ATTR_URL, url);
      }

      // Preserve embedded Swing components (e.g., inline image previews).
      java.awt.Component comp = StyleConstants.getComponent(old);
      if (comp != null) {
        StyleConstants.setComponent(fresh, comp);
      }

      // Preserve per-nick marker and re-apply a theme-correct nick color.
      Object nickLower = old.getAttribute(NickColorService.ATTR_NICK);
      if (nickLower != null) {
        String n = String.valueOf(nickLower);
        fresh.addAttribute(NickColorService.ATTR_NICK, n);
        if (nickColors != null) {
          nickColors.applyColor(fresh, n);
        }
      }

      // Ensure the style marker survives replacements.
      if (styleId != null) {
        fresh.addAttribute(ChatStyles.ATTR_STYLE, styleId);
      }

      doc.setCharacterAttributes(start, end - start, fresh, true);
      offset = end;
    }
  }
}

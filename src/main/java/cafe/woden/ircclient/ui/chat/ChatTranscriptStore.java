package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.PresenceEvent;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.chat.embed.ChatImageEmbedder;
import cafe.woden.ircclient.ui.chat.fold.PresenceFoldComponent;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
  private final Map<TargetRef, TranscriptState> stateByTarget = new HashMap<>();

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
    stateByTarget.computeIfAbsent(ref, r -> new TranscriptState());
  }

  public synchronized StyledDocument document(TargetRef ref) {
    ensureTargetExists(ref);
    return docs.get(ref);
  }

  public synchronized void appendPlain(TargetRef ref, String text) {
    ensureTargetExists(ref);
    breakPresenceRun(ref);
    StyledDocument doc = docs.get(ref);
    try {
      doc.insertString(doc.getLength(), text, styles.message());
    } catch (Exception ignored) {
    }
  }

  public synchronized void closeTarget(TargetRef ref) {
    if (ref == null) return;
    docs.remove(ref);
    stateByTarget.remove(ref);
  }

  /**
   * Append a foldable presence/system event (join/part/quit/nick) in a channel transcript.
   *
   */
  public synchronized void appendPresence(TargetRef ref, PresenceEvent event) {
    if (ref == null || event == null) return;
    ensureTargetExists(ref);

    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return;

    // If the current run is already folded, just extend the component live.
    if (st.currentPresenceBlock != null && st.currentPresenceBlock.folded
        && st.currentPresenceBlock.component != null) {
      // Keep the backing list consistent even though the component owns its own list.
      st.currentPresenceBlock.entries.add(event);
      st.currentPresenceBlock.component.addEntry(event);
      return;
    }

    // New line
    ensureAtLineStart(doc);
    int startOffset = doc.getLength();

    try {
      // Presence lines are rendered like status lines.
      if (ts != null && ts.enabled()) {
        doc.insertString(doc.getLength(), ts.prefixNow(), styles.timestamp());
      }

      AttributeSet base = styles.status();
      renderer.insertRichText(doc, ref, event.displayText(), base);
      doc.insertString(doc.getLength(), "\n", styles.timestamp());
    } catch (Exception ignored) {
      return;
    }

    int endOffset = doc.getLength();

    // Start a new consecutive run if needed.
    PresenceBlock block = st.currentPresenceBlock;
    if (block == null || block.endOffset != startOffset) {
      block = new PresenceBlock(startOffset, endOffset);
      st.currentPresenceBlock = block;
    } else {
      block.endOffset = endOffset;
    }

    block.entries.add(event);

    // Fold immediately once the run reaches 2+.
    if (!block.folded && block.entries.size() == 2) {
      foldBlock(doc, ref, block);
    }
  }

  private void appendPresenceAsStatusLine(StyledDocument doc, TargetRef ref, PresenceEvent event) {
    if (doc == null || event == null) return;

    // Keep this consistent with other transcript entries.
    ensureAtLineStart(doc);
    try {
      if (ts != null && ts.enabled()) {
        doc.insertString(doc.getLength(), ts.prefixNow(), styles.timestamp());
      }
      AttributeSet base = styles.status();
      renderer.insertRichText(doc, ref, event.displayText(), base);
      doc.insertString(doc.getLength(), "\n", styles.timestamp());
    } catch (Exception ignored) {
      // ignore
    }
  }

  public synchronized void appendLine(TargetRef ref,
                                      String from,
                                      String text,
                                      AttributeSet fromStyle,
                                      AttributeSet msgStyle) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);

    // New line
    ensureAtLineStart(doc);

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
    // Chat message, start a new run
    breakPresenceRun(ref);

    AttributeSet fromStyle = styles.from();
    if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
      fromStyle = nickColors.forNick(from, fromStyle);
    }
    appendLine(ref, from, text, fromStyle, styles.message());
  }

  public void appendNotice(TargetRef ref, String from, String text) {
    breakPresenceRun(ref);
    appendLine(ref, from, text, styles.noticeFrom(), styles.noticeMessage());
  }

  public void appendStatus(TargetRef ref, String from, String text) {
    breakPresenceRun(ref);
    // Status messages are dim/italic; apply the same style to the prefix and body.
    appendLine(ref, from, text, styles.status(), styles.status());
  }

  public void appendError(TargetRef ref, String from, String text) {
    breakPresenceRun(ref);
    appendLine(ref, from, text, styles.error(), styles.error());
  }

  private void breakPresenceRun(TargetRef ref) {
    if (ref == null) return;
    TranscriptState st = stateByTarget.get(ref);
    if (st != null) st.currentPresenceBlock = null;
  }

  /**
   * Swing acts weird about this so make sure it's on a new line
   */
  private void ensureAtLineStart(StyledDocument doc) {
    if (doc == null) return;
    int len = doc.getLength();
    if (len <= 0) return;
    try {
      String last = doc.getText(len - 1, 1);
      if (!"\n".equals(last)) {
        doc.insertString(len, "\n", styles.timestamp());
      }
    } catch (Exception ignored) {
      // ignore
    }
  }


  private void foldBlock(StyledDocument doc, TargetRef ref, PresenceBlock block) {
    if (doc == null || block == null) return;

    int start = Math.max(0, Math.min(block.startOffset, doc.getLength()));
    int end = Math.max(0, Math.min(block.endOffset, doc.getLength()));
    if (end <= start) return;

    try {
      // Remove the raw presence lines (including their timestamps).
      doc.remove(start, end - start);

      // Insert a single embedded component line in their place.
      PresenceFoldComponent comp = new PresenceFoldComponent(block.entries);

      SimpleAttributeSet attrs = new SimpleAttributeSet(styles.status());
      StyleConstants.setComponent(attrs, comp);

      // Ensure the style marker survives restyles.
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);

      // IMPORTANT: do NOT prepend a timestamp on the folded line (user preference).
      // Ensure the embedded fold starts on its own line.
      int insertPos = start;
      if (insertPos > 0) {
        try {
          String prev = doc.getText(insertPos - 1, 1);
          if (!"\n".equals(prev)) {
            doc.insertString(insertPos, "\n", styles.timestamp());
            insertPos++;
          }
        } catch (Exception ignored2) {
          // ignore
        }
      }

      doc.insertString(insertPos, " ", attrs);
      doc.insertString(insertPos + 1, "\n", styles.timestamp());

      block.folded = true;
      block.component = comp;

      // After folding, this block is now represented by the embedded component.
      // We keep the block active so later presence events can update it live.
      block.startOffset = insertPos;
      block.endOffset = insertPos + 2;
    } catch (Exception ignored) {
      // no-op
    }
  }

  private static final class TranscriptState {
    PresenceBlock currentPresenceBlock;
  }

  private static final class PresenceBlock {
    int startOffset;
    int endOffset;
    boolean folded = false;
    PresenceFoldComponent component;

    final List<PresenceEvent> entries = new ArrayList<>();

    private PresenceBlock(int startOffset, int endOffset) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }
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

      // Preserve embedded Swing components (e.g., inline image previews, fold components).
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

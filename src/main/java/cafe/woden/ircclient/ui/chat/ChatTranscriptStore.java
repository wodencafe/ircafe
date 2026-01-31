package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.PresenceEvent;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.chat.embed.ChatImageEmbedder;
import cafe.woden.ircclient.ui.chat.embed.ChatLinkPreviewEmbedder;
import cafe.woden.ircclient.ui.chat.fold.PresenceFoldComponent;
import cafe.woden.ircclient.ui.chat.fold.SpoilerMessageComponent;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.render.IrcFormatting;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.Position;
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
  private final ChatLinkPreviewEmbedder linkPreviews;
  private final UiSettingsBus uiSettings;

  private final Map<TargetRef, StyledDocument> docs = new HashMap<>();
  private final Map<TargetRef, TranscriptState> stateByTarget = new HashMap<>();

  public ChatTranscriptStore(
      ChatStyles styles,
      ChatRichTextRenderer renderer,
      ChatTimestampFormatter ts,
      NickColorService nickColors,
      ChatImageEmbedder imageEmbeds,
      ChatLinkPreviewEmbedder linkPreviews,
      UiSettingsBus uiSettings
  ) {
    this.styles = styles;
    this.renderer = renderer;
    this.ts = ts;
    this.nickColors = nickColors;
    this.imageEmbeds = imageEmbeds;
    this.linkPreviews = linkPreviews;
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

    // Config: allow presence folding to be disabled (render as plain status lines).
    boolean foldsEnabled = true;
    try {
      foldsEnabled = uiSettings == null || uiSettings.get() == null || uiSettings.get().presenceFoldsEnabled();
    } catch (Exception ignored) {
      foldsEnabled = true;
    }

    if (!foldsEnabled) {
      // End any active presence run.
      st.currentPresenceBlock = null;

      // Append as a normal status line (with timestamp like other status lines).
      ensureAtLineStart(doc);
      try {
        if (ts != null && ts.enabled()) {
          doc.insertString(doc.getLength(), ts.prefixNow(), styles.timestamp());
        }
        AttributeSet base = styles.status();
        renderer.insertRichText(doc, ref, event.displayText(), base);
        doc.insertString(doc.getLength(), "\n", styles.timestamp());
      } catch (Exception ignored2) {
        // ignore
      }
      return;
    }


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

      // Config: timestamps for regular chat messages are optional (status/error/notice timestamps remain controlled
      // by ircafe.ui.timestamps.enabled).
      boolean chatMessageTimestampsEnabled = false;
      try {
        chatMessageTimestampsEnabled = uiSettings != null
            && uiSettings.get() != null
            && uiSettings.get().chatMessageTimestampsEnabled();
      } catch (Exception ignored) {
        chatMessageTimestampsEnabled = false;
      }

      if (ts != null && ts.enabled()
          && (ChatStyles.STYLE_STATUS.equals(styleId)
          || ChatStyles.STYLE_ERROR.equals(styleId)
          || ChatStyles.STYLE_NOTICE_MESSAGE.equals(styleId)
          || (chatMessageTimestampsEnabled && ChatStyles.STYLE_MESSAGE.equals(styleId)))) {
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

      // Optionally embed OpenGraph/Twitter-card style previews for non-image URLs.
      if (linkPreviews != null && uiSettings != null && uiSettings.get().linkPreviewsEnabled()) {
        linkPreviews.appendPreviews(doc, text);
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

  /**
   * Append a message as a collapsible "spoiler" (click-to-reveal) block.
   *
   * <p>This is UI plumbing for the upcoming soft-ignore feature. It does not
   * perform any matching or apply soft-ignore rules by itself; call-sites will
   * decide when to use it.
   */
  public void appendSpoilerChat(TargetRef ref, String from, String text) {
    breakPresenceRun(ref);
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return;

    // New line
    ensureAtLineStart(doc);

    String msg = text == null ? "" : text;
    String fromLabel = from == null ? "" : from;
    if (!fromLabel.isBlank() && !fromLabel.endsWith(":")) {
      fromLabel = fromLabel + ":";
    }

    boolean chatMessageTimestampsEnabled = false;
    try {
      chatMessageTimestampsEnabled = uiSettings != null
          && uiSettings.get() != null
          && uiSettings.get().chatMessageTimestampsEnabled();
    } catch (Exception ignored) {
      chatMessageTimestampsEnabled = false;
    }
    final String tsPrefixFinal =
        (ts != null && ts.enabled() && chatMessageTimestampsEnabled) ? ts.prefixNow() : "";

    final int offFinal = doc.getLength();
    final TargetRef refFinal = ref;
    final StyledDocument docFinal = doc;
    final String fromFinal = from;
    final String msgFinal = msg;
    final String fromLabelFinal = fromLabel;

    final SpoilerMessageComponent comp = new SpoilerMessageComponent(tsPrefixFinal, fromLabelFinal);
// If nick coloring is enabled, apply it to the visible prefix label.
    try {
      if (nickColors != null && nickColors.enabled() && from != null && !from.isBlank()) {
        Color bg = javax.swing.UIManager.getColor("TextPane.background");
        Color fg = javax.swing.UIManager.getColor("TextPane.foreground");
        comp.setFromColor(nickColors.colorForNick(from, bg, fg));
      }
    } catch (Exception ignored) {
      // ignore
    }

    SimpleAttributeSet attrs = new SimpleAttributeSet(styles.message());
    StyleConstants.setComponent(attrs, comp);
    try {
      doc.insertString(offFinal, " ", attrs);

      // Anchor the spoiler component location with a live Position so later transcript edits
      // (e.g., presence folding) don't break reveal.
      final Position spoilerPos = doc.createPosition(offFinal);

      // IMPORTANT: pass the exact component instance we inserted. The transcript may contain
      // multiple spoiler components close together; searching by type alone can reveal/remove
      // the wrong one, leaving the clicked component stuck in "revealing...".
      comp.setOnReveal(() -> revealSpoilerInPlace(refFinal, docFinal, spoilerPos, comp,
          tsPrefixFinal, fromFinal, msgFinal));

doc.insertString(doc.getLength(), "\n", styles.timestamp());
} catch (Exception ignored) {
      // ignore
    }
  }

  /**
   * Replace a spoiler placeholder (embedded component) with the original message line in-place.
   *
   * <p>This avoids JTextPane embedded-component resizing issues by swapping the component out
   * entirely once the user clicks reveal.
   */
  private boolean revealSpoilerInPlace(TargetRef ref,
                                    StyledDocument doc,
                                    Position anchor,
                                    SpoilerMessageComponent expected,
                                    String tsPrefix,
                                    String from,
                                    String msg) {
  if (doc == null || anchor == null) return false;

  // We must mutate the transcript on the EDT so the JTextPane updates reliably.
  if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
    final boolean[] ok = new boolean[] {false};
    try {
      javax.swing.SwingUtilities.invokeAndWait(() -> ok[0] =
          revealSpoilerInPlace(ref, doc, anchor, expected, tsPrefix, from, msg));
    } catch (Exception ignored) {
      return false;
    }
    return ok[0];
  }

  synchronized (ChatTranscriptStore.this) {
    try {
      int len = doc.getLength();
      if (len <= 0) return false;

      int guess = anchor.getOffset();
      if (guess < 0) guess = 0;
      if (guess >= len) guess = len - 1;

      int off = findSpoilerOffset(doc, guess, expected);
      if (off < 0) return false;

      // Only proceed if the character at 'off' is still our embedded component.
      Element el = doc.getCharacterElement(off);
      if (el == null) return false;
      AttributeSet as = el.getAttributes();
      Object comp = as != null ? StyleConstants.getComponent(as) : null;
      if (!(comp instanceof SpoilerMessageComponent)) return false;
      if (expected != null && comp != expected) return false;

      // Remove the component char and its newline (if present).
      int removeLen = 1;
      if (off + 1 < doc.getLength()) {
        try {
          String next = doc.getText(off + 1, 1);
          if ("\n".equals(next)) removeLen = 2;
        } catch (Exception ignored2) {
          // ignore
        }
      }
      doc.remove(off, removeLen);

      int pos = off;

      // Timestamp (only if enabled when the spoiler was created)
      if (tsPrefix != null && !tsPrefix.isBlank()) {
        doc.insertString(pos, tsPrefix, styles.timestamp());
        pos += tsPrefix.length();
      }

      // Nick prefix
      if (from != null && !from.isBlank()) {
        AttributeSet fromStyle = styles.from();
        if (nickColors != null && nickColors.enabled()) {
          fromStyle = nickColors.forNick(from, fromStyle);
        }
        String prefix = from + ": ";
        doc.insertString(pos, prefix, fromStyle);
        pos += prefix.length();
      }

      // Message body, inserted as rich text so URLs/mentions/channel-links retain metadata.
      // (We intentionally do not create inline image/link-preview blocks when revealing.)
      DefaultStyledDocument inner = new DefaultStyledDocument();
      try {
        if (renderer != null) {
          renderer.insertRichText(inner, ref, msg, styles.message());
        } else {
          inner.insertString(0, msg, styles.message());
        }
      } catch (Exception ignored2) {
        try {
          inner.remove(0, inner.getLength());
          inner.insertString(0, msg, styles.message());
        } catch (Exception ignored3) {
          // ignore
        }
      }

      pos = insertStyled(inner, doc, pos);
      doc.insertString(pos, "\n", styles.timestamp());
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }
}

/**
 * Find the offset for a spoiler embedded component near a guessed offset.
 *
 * <p>This exists because transcript edits (like folding) can shift offsets after the spoiler was
 * inserted. We search a small window around the anchor to find the actual component char.</p>
 */
private static int findSpoilerOffset(StyledDocument doc, int guess, SpoilerMessageComponent expected) {
  if (doc == null) return -1;
  int len = doc.getLength();
  if (len <= 0) return -1;

  int start = Math.max(0, guess - 256);
  int end = Math.min(len - 1, guess + 256);
  for (int i = start; i <= end; i++) {
    try {
      Element el = doc.getCharacterElement(i);
      if (el == null) continue;
      AttributeSet as = el.getAttributes();
      Object comp = as != null ? StyleConstants.getComponent(as) : null;
      if (comp instanceof SpoilerMessageComponent) {
        if (expected == null || comp == expected) return i;
      }
    } catch (Exception ignored) {
      // ignore
    }
  }
  return -1;
}
  /**
   * Inserts styled content from src into dest at position pos.
   *
   * <p>This is used to reveal spoiler messages in-place while preserving URL/mention/channel metadata.</p>
   */
  private static int insertStyled(StyledDocument src, StyledDocument dest, int pos) {
    if (src == null || dest == null) return pos;
    try {
      int len = src.getLength();
      int i = 0;
      while (i < len) {
        Element el = src.getCharacterElement(i);
        if (el == null) break;

        int start = Math.max(0, Math.min(el.getStartOffset(), len));
        int end = Math.max(start, Math.min(el.getEndOffset(), len));
        if (end <= start) {
          i = Math.min(len, i + 1);
          continue;
        }

        String t = src.getText(start, end - start);
        if (t != null && !t.isEmpty()) {
          dest.insertString(pos, t, el.getAttributes());
          pos += t.length();
        }
        i = end;
      }
    } catch (Exception ignored) {
      // best-effort
    }
    return pos;
  }

  /**
   * Append a CTCP ACTION (/me) line. Rendered as: "* nick action".
   */
  public void appendAction(TargetRef ref, String from, String action) {
    breakPresenceRun(ref);
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return;

    String a = action == null ? "" : action;

    // New line
    ensureAtLineStart(doc);

    try {
      boolean chatMessageTimestampsEnabled = false;
      try {
        chatMessageTimestampsEnabled = uiSettings != null
            && uiSettings.get() != null
            && uiSettings.get().chatMessageTimestampsEnabled();
      } catch (Exception ignored) {
        chatMessageTimestampsEnabled = false;
      }

      if (ts != null && ts.enabled() && chatMessageTimestampsEnabled) {
        doc.insertString(doc.getLength(), ts.prefixNow(), styles.timestamp());
      }

      AttributeSet msgStyle = styles.actionMessage();
      AttributeSet fromStyle = styles.actionFrom();

      if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
        fromStyle = nickColors.forNick(from, fromStyle);
      }

      doc.insertString(doc.getLength(), "* ", msgStyle);
      if (from != null && !from.isBlank()) {
        doc.insertString(doc.getLength(), from, fromStyle);
        doc.insertString(doc.getLength(), " ", msgStyle);
      }

      renderer.insertRichText(doc, ref, a, msgStyle);
      doc.insertString(doc.getLength(), "\n", styles.timestamp());

      if (imageEmbeds != null && uiSettings != null && uiSettings.get().imageEmbedsEnabled()) {
        imageEmbeds.appendEmbeds(doc, a);
      }

      if (linkPreviews != null && uiSettings != null && uiSettings.get().linkPreviewsEnabled()) {
        linkPreviews.appendPreviews(doc, a);
      }
    } catch (Exception ignored) {
      // ignore
    }
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

      // Preserve channel-link metadata.
      Object chan = old.getAttribute(ChatStyles.ATTR_CHANNEL);
      if (chan != null) {
        fresh.addAttribute(ChatStyles.ATTR_CHANNEL, chan);
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

      // Preserve and re-apply mIRC formatting (bold/italic/underline/colors) across theme changes.
      // The mIRC metadata attributes are set during insertion by {@link IrcFormatting}.
      Object ircBold = old.getAttribute(ChatStyles.ATTR_IRC_BOLD);
      Object ircItalic = old.getAttribute(ChatStyles.ATTR_IRC_ITALIC);
      Object ircUnderline = old.getAttribute(ChatStyles.ATTR_IRC_UNDERLINE);
      Object ircReverse = old.getAttribute(ChatStyles.ATTR_IRC_REVERSE);
      Object ircFg = old.getAttribute(ChatStyles.ATTR_IRC_FG);
      Object ircBg = old.getAttribute(ChatStyles.ATTR_IRC_BG);

      if (ircBold != null) {
        fresh.addAttribute(ChatStyles.ATTR_IRC_BOLD, ircBold);
        if (ircBold instanceof Boolean b) StyleConstants.setBold(fresh, b);
      }
      if (ircItalic != null) {
        fresh.addAttribute(ChatStyles.ATTR_IRC_ITALIC, ircItalic);
        if (ircItalic instanceof Boolean b) StyleConstants.setItalic(fresh, b);
      }
      if (ircUnderline != null) {
        fresh.addAttribute(ChatStyles.ATTR_IRC_UNDERLINE, ircUnderline);
        if (ircUnderline instanceof Boolean b) {
          // Always keep links underlined for click affordance.
          if (!ChatStyles.STYLE_LINK.equals(styleId) || b) {
            StyleConstants.setUnderline(fresh, b);
          }
        }
      }
      if (ircReverse != null) {
        fresh.addAttribute(ChatStyles.ATTR_IRC_REVERSE, ircReverse);
      }
      if (ircFg != null) {
        fresh.addAttribute(ChatStyles.ATTR_IRC_FG, ircFg);
      }
      if (ircBg != null) {
        fresh.addAttribute(ChatStyles.ATTR_IRC_BG, ircBg);
      }

      // Apply palette colors (if any) and reverse.
      boolean rev = Boolean.TRUE.equals(ircReverse);
      Color fgColor = (ircFg instanceof Integer i) ? IrcFormatting.colorForCode(i) : null;
      Color bgColor = (ircBg instanceof Integer i) ? IrcFormatting.colorForCode(i) : null;

      Color finalFg = fgColor != null ? fgColor : StyleConstants.getForeground(fresh);
      Color finalBg = bgColor != null ? bgColor : StyleConstants.getBackground(fresh);
      if (rev) {
        Color tmp = finalFg;
        finalFg = finalBg;
        finalBg = tmp;
      }
      if (finalFg != null) StyleConstants.setForeground(fresh, finalFg);
      if (finalBg != null) StyleConstants.setBackground(fresh, finalBg);

      // Ensure the style marker survives replacements.
      if (styleId != null) {
        fresh.addAttribute(ChatStyles.ATTR_STYLE, styleId);
      }

      doc.setCharacterAttributes(start, end - start, fresh, true);
      offset = end;
    }
  }
}

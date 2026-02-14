package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.PresenceEvent;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.chat.embed.ChatImageEmbedder;
import cafe.woden.ircclient.ui.chat.embed.ChatLinkPreviewEmbedder;
import cafe.woden.ircclient.ui.chat.fold.PresenceFoldComponent;
import cafe.woden.ircclient.ui.chat.fold.LoadOlderMessagesComponent;
import cafe.woden.ircclient.ui.chat.fold.HistoryDividerComponent;
import cafe.woden.ircclient.ui.chat.fold.SpoilerMessageComponent;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.render.IrcFormatting;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.awt.Font;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import javax.swing.SwingUtilities;
import javax.swing.text.StyleConstants;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

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
  private final NickColorSettingsBus nickColorSettings;

  private final PropertyChangeListener nickColorSettingsListener = this::onNickColorSettingsChanged;

  private final Map<TargetRef, StyledDocument> docs = new HashMap<>();
  private final Map<TargetRef, TranscriptState> stateByTarget = new HashMap<>();

  public ChatTranscriptStore(
      ChatStyles styles,
      ChatRichTextRenderer renderer,
      ChatTimestampFormatter ts,
      NickColorService nickColors,
      NickColorSettingsBus nickColorSettings,
      ChatImageEmbedder imageEmbeds,
      ChatLinkPreviewEmbedder linkPreviews,
      UiSettingsBus uiSettings
  ) {
    this.styles = styles;
    this.renderer = renderer;
    this.ts = ts;
    this.nickColors = nickColors;
    this.nickColorSettings = nickColorSettings;
    this.imageEmbeds = imageEmbeds;
    this.linkPreviews = linkPreviews;
    this.uiSettings = uiSettings;

    if (this.nickColorSettings != null) {
      this.nickColorSettings.addListener(nickColorSettingsListener);
    }
  }

  public synchronized void ensureTargetExists(TargetRef ref) {
    docs.computeIfAbsent(ref, r -> new DefaultStyledDocument());
    stateByTarget.computeIfAbsent(ref, r -> new TranscriptState());
  }

  private void noteEpochMs(TargetRef ref, Long epochMs) {
    if (ref == null || epochMs == null) return;
    TranscriptState st = stateByTarget.get(ref);
    if (st == null) return;
    Long cur = st.earliestEpochMsSeen;
    if (cur == null || epochMs < cur) {
      st.earliestEpochMsSeen = epochMs;
    }
  }

  public synchronized StyledDocument document(TargetRef ref) {
    ensureTargetExists(ref);
    return docs.get(ref);
  }

  public synchronized OptionalLong earliestTimestampEpochMs(TargetRef ref) {
    if (ref == null) return OptionalLong.empty();
    TranscriptState st = stateByTarget.get(ref);
    if (st == null || st.earliestEpochMsSeen == null) return OptionalLong.empty();
    return OptionalLong.of(st.earliestEpochMsSeen);
  }

  public synchronized LoadOlderMessagesComponent ensureLoadOlderMessagesControl(TargetRef ref) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return null;

    TranscriptState st = stateByTarget.get(ref);
    if (st != null && st.loadOlderControl != null) {
      return st.loadOlderControl.component;
    }

    int beforeLen = doc.getLength();
    int insertPos = 0;

    LoadOlderMessagesComponent comp = new LoadOlderMessagesComponent();
    try {
      if (uiSettings != null && uiSettings.get() != null) {
        comp.setTranscriptFont(new Font(
            uiSettings.get().chatFontFamily(),
            Font.PLAIN,
            uiSettings.get().chatFontSize()
        ));
      }
    } catch (Exception ignored) {
    }

    try {
      SimpleAttributeSet attrs = new SimpleAttributeSet(styles.status());
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
      StyleConstants.setComponent(attrs, comp);
      doc.insertString(insertPos, " ", attrs);
      Position pos = doc.createPosition(insertPos);
      doc.insertString(insertPos + 1, "\n", styles.timestamp());
      if (st != null) {
        st.loadOlderControl = new LoadOlderControl(pos, comp);
      }
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertPos, delta);
    return comp;
  }

  public synchronized HistoryDividerComponent ensureHistoryDivider(TargetRef ref,
                                                                   int insertAt,
                                                                   String labelText) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return null;

    TranscriptState st = stateByTarget.get(ref);
    if (st != null && st.historyDivider != null) {
      try {
        st.historyDivider.component.setText(labelText);
      } catch (Exception ignored) {
      }
      return st.historyDivider.component;
    }

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, insertAt);
    pos = ensureAtLineStartForInsert(doc, pos);
    final int insertionStart = pos;

    HistoryDividerComponent comp = new HistoryDividerComponent(labelText);
    try {
      if (uiSettings != null && uiSettings.get() != null) {
        comp.setTranscriptFont(new Font(
            uiSettings.get().chatFontFamily(),
            Font.PLAIN,
            uiSettings.get().chatFontSize()
        ));
      }
    } catch (Exception ignored) {
    }

    try {
      SimpleAttributeSet attrs = new SimpleAttributeSet(styles.status());
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
      StyleConstants.setComponent(attrs, comp);
      doc.insertString(pos, " ", attrs);
      Position p = doc.createPosition(pos);
      doc.insertString(pos + 1, "\n", styles.timestamp());
      if (st != null) {
        st.historyDivider = new HistoryDividerControl(p, comp);
      }
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
    return comp;
  }

  public synchronized int loadOlderInsertOffset(TargetRef ref) {
    if (ref == null) return 0;
    TranscriptState st = stateByTarget.get(ref);
    if (st == null || st.loadOlderControl == null) return 0;
    StyledDocument doc = docs.get(ref);
    if (doc == null) return 0;
    int base = st.loadOlderControl.pos.getOffset();
    int off = base + 2;
    return Math.max(0, Math.min(off, doc.getLength()));
  }

  public synchronized void setLoadOlderMessagesControlState(TargetRef ref, LoadOlderMessagesComponent.State s) {
    if (ref == null) return;
    TranscriptState st = stateByTarget.get(ref);
    if (st == null || st.loadOlderControl == null) return;
    try {
      st.loadOlderControl.component.setState(s);
    } catch (Exception ignored) {
    }
  }

  public synchronized void setLoadOlderMessagesControlHandler(TargetRef ref, java.util.function.BooleanSupplier onLoad) {
    if (ref == null) return;
    TranscriptState st = stateByTarget.get(ref);
    if (st == null || st.loadOlderControl == null) return;
    try {
      st.loadOlderControl.component.setOnLoadRequested(onLoad);
    } catch (Exception ignored) {
    }
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

  public synchronized void clearTarget(TargetRef ref) {
    if (ref == null) return;
    ensureTargetExists(ref);

    StyledDocument doc = docs.get(ref);
    if (doc == null) return;

    try {
      doc.remove(0, doc.getLength());
    } catch (Exception ignored) {
    }
    stateByTarget.put(ref, new TranscriptState());
  }

  public synchronized void appendPresence(TargetRef ref, PresenceEvent event) {
    if (ref == null || event == null) return;
    ensureTargetExists(ref);

    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return;
    boolean foldsEnabled = true;
    try {
      foldsEnabled = uiSettings == null || uiSettings.get() == null || uiSettings.get().presenceFoldsEnabled();
    } catch (Exception ignored) {
      foldsEnabled = true;
    }

    if (!foldsEnabled) {
      st.currentPresenceBlock = null;
      ensureAtLineStart(doc);
      try {
        if (ts != null && ts.enabled()) {
          doc.insertString(doc.getLength(), ts.prefixNow(), styles.timestamp());
        }
        AttributeSet base = styles.status();
        renderer.insertRichText(doc, ref, event.displayText(), base);
        doc.insertString(doc.getLength(), "\n", styles.timestamp());
      } catch (Exception ignored2) {
      }
      return;
    }
    if (st.currentPresenceBlock != null && st.currentPresenceBlock.folded
        && st.currentPresenceBlock.component != null) {
      st.currentPresenceBlock.entries.add(event);
      st.currentPresenceBlock.component.addEntry(event);
      return;
    }
    ensureAtLineStart(doc);
    int startOffset = doc.getLength();

    try {
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
    PresenceBlock block = st.currentPresenceBlock;
    if (block == null || block.endOffset != startOffset) {
      block = new PresenceBlock(startOffset, endOffset);
      st.currentPresenceBlock = block;
    } else {
      block.endOffset = endOffset;
    }

    block.entries.add(event);
    if (!block.folded && block.entries.size() == 2) {
      foldBlock(doc, ref, block);
    }
  }

  public synchronized void appendLine(TargetRef ref,
                                      String from,
                                      String text,
                                      AttributeSet fromStyle,
                                      AttributeSet msgStyle) {
    appendLineInternal(ref, from, text, fromStyle, msgStyle, true, null);
  }

    private synchronized void appendLineInternal(TargetRef ref,
                                  String from,
                                  String text,
                                  AttributeSet fromStyle,
                                  AttributeSet msgStyle,
                                  boolean allowEmbeds,
                                  Long epochMs) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, epochMs);
    ensureAtLineStart(doc);

    try {
      AttributeSet baseForId = msgStyle != null ? msgStyle : styles.message();
      Object styleIdObj = baseForId.getAttribute(ChatStyles.ATTR_STYLE);
      String styleId = styleIdObj != null ? String.valueOf(styleIdObj) : null;
      boolean timestampsIncludeChatMessages = false;
      try {
        timestampsIncludeChatMessages = uiSettings != null
            && uiSettings.get() != null
            && uiSettings.get().timestampsIncludeChatMessages();
      } catch (Exception ignored) {
        timestampsIncludeChatMessages = false;
      }

      if (ts != null && ts.enabled()
          && (ChatStyles.STYLE_STATUS.equals(styleId)
          || ChatStyles.STYLE_ERROR.equals(styleId)
          || ChatStyles.STYLE_NOTICE_MESSAGE.equals(styleId)
          || (timestampsIncludeChatMessages && ChatStyles.STYLE_MESSAGE.equals(styleId)))) {
        String prefix = (epochMs != null) ? ts.prefixAt(epochMs) : ts.prefixNow();
        doc.insertString(doc.getLength(), prefix, styles.timestamp());
      }

      if (from != null && !from.isBlank()) {
        doc.insertString(doc.getLength(), from + ": ", fromStyle != null ? fromStyle : styles.from());
      }

      AttributeSet base = msgStyle != null ? msgStyle : styles.message();
      renderer.insertRichText(doc, ref, text, base);

      doc.insertString(doc.getLength(), "\n", styles.timestamp());

      if (!allowEmbeds) {
        return;
      }
      if (imageEmbeds != null && uiSettings != null && uiSettings.get().imageEmbedsEnabled()) {
        imageEmbeds.appendEmbeds(ref, doc, text);
      }
      if (linkPreviews != null && uiSettings != null && uiSettings.get().linkPreviewsEnabled()) {
        linkPreviews.appendPreviews(ref, doc, text);
      }
    } catch (Exception ignored) {
    }
  }

  public void appendChat(TargetRef ref, String from, String text) {
    appendChat(ref, from, text, false);
  }

  public void appendChat(TargetRef ref, String from, String text, boolean outgoingLocalEcho) {
    breakPresenceRun(ref);

    AttributeSet fromStyle = styles.from();
    if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
      fromStyle = nickColors.forNick(from, fromStyle);
    }

    SimpleAttributeSet fs = new SimpleAttributeSet(fromStyle);
    SimpleAttributeSet ms = new SimpleAttributeSet(styles.message());
    applyOutgoingLineColor(fs, ms, outgoingLocalEcho);

    appendLine(ref, from, text, fs, ms);
  }

  public void appendChatFromHistory(TargetRef ref,
                                    String from,
                                    String text,
                                    boolean outgoingLocalEcho,
                                    long tsEpochMs) {
    breakPresenceRun(ref);

    AttributeSet fromStyle = styles.from();
    if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
      fromStyle = nickColors.forNick(from, fromStyle);
    }

    SimpleAttributeSet fs = new SimpleAttributeSet(fromStyle);
    SimpleAttributeSet ms = new SimpleAttributeSet(styles.message());
    applyOutgoingLineColor(fs, ms, outgoingLocalEcho);

    appendLineInternal(ref, from, text, fs, ms, false, tsEpochMs);
  }
/**
 * Append a chat message with a timestamp, allowing embeds (link previews / images).
 *
 * <p>This is used for inbound "live" messages where we have an Instant from the server. We keep the
 * history-loading paths (DB backfill / "load older") embed-free to avoid fetch storms.
 */
public void appendChatAt(TargetRef ref,
                         String from,
                         String text,
                         boolean outgoingLocalEcho,
                         long tsEpochMs) {
  breakPresenceRun(ref);

  AttributeSet fromStyle = styles.from();
  if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
    fromStyle = nickColors.forNick(from, fromStyle);
  }

  SimpleAttributeSet fs = new SimpleAttributeSet(fromStyle);
  SimpleAttributeSet ms = new SimpleAttributeSet(styles.message());
  applyOutgoingLineColor(fs, ms, outgoingLocalEcho);

  appendLineInternal(ref, from, text, fs, ms, true, tsEpochMs);
}
  public synchronized int insertChatFromHistoryAt(TargetRef ref,
                                                  int insertAt,
                                                  String from,
                                                  String text,
                                                  boolean outgoingLocalEcho,
                                                  long tsEpochMs) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);

    AttributeSet fromStyle = styles.from();
    if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
      fromStyle = nickColors.forNick(from, fromStyle);
    }

    SimpleAttributeSet fs = new SimpleAttributeSet(fromStyle);
    SimpleAttributeSet ms = new SimpleAttributeSet(styles.message());
    applyOutgoingLineColor(fs, ms, outgoingLocalEcho);

    return insertLineInternalAt(ref, insertAt, from, text, fs, ms, false, tsEpochMs);
  }

  public synchronized int prependChatFromHistory(TargetRef ref,
                                                 String from,
                                                 String text,
                                                 boolean outgoingLocalEcho,
                                                 long tsEpochMs) {
    return insertChatFromHistoryAt(ref, 0, from, text, outgoingLocalEcho, tsEpochMs);
  }

  public synchronized int insertActionFromHistoryAt(TargetRef ref,
                                                    int insertAt,
                                                    String from,
                                                    String action,
                                                    boolean outgoingLocalEcho,
                                                    long tsEpochMs) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return Math.max(0, insertAt);

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, insertAt);
    pos = ensureAtLineStartForInsert(doc, pos);
    final int insertionStart = pos;

    String a = action == null ? "" : action;

    try {
      boolean timestampsIncludeChatMessages = false;
      try {
        timestampsIncludeChatMessages = uiSettings != null
            && uiSettings.get() != null
            && uiSettings.get().timestampsIncludeChatMessages();
      } catch (Exception ignored) {
        timestampsIncludeChatMessages = false;
      }

      if (ts != null && ts.enabled() && timestampsIncludeChatMessages) {
        String prefix = ts.prefixAt(tsEpochMs);
        doc.insertString(pos, prefix, styles.timestamp());
        pos += prefix.length();
      }

      AttributeSet msgStyle = styles.actionMessage();
      AttributeSet fromStyle = styles.actionFrom();

      if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
        fromStyle = nickColors.forNick(from, fromStyle);
      }

      SimpleAttributeSet ms = new SimpleAttributeSet(msgStyle);
      SimpleAttributeSet fs = new SimpleAttributeSet(fromStyle);
      applyOutgoingLineColor(fs, ms, outgoingLocalEcho);

      doc.insertString(pos, "* ", ms);
      pos += 2;
      if (from != null && !from.isBlank()) {
        doc.insertString(pos, from, fs);
        pos += from.length();
        doc.insertString(pos, " ", ms);
        pos += 1;
      }

      if (renderer != null) {
        pos = renderer.insertRichTextAt(doc, ref, a, ms, pos);
      } else {
        doc.insertString(pos, a, ms);
        pos += a.length();
      }

      doc.insertString(pos, "\n", styles.timestamp());
      pos += 1;
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
    return pos;
  }

  public synchronized int prependActionFromHistory(TargetRef ref,
                                                   String from,
                                                   String action,
                                                   boolean outgoingLocalEcho,
                                                   long tsEpochMs) {
    return insertActionFromHistoryAt(ref, 0, from, action, outgoingLocalEcho, tsEpochMs);
  }

  public synchronized int insertNoticeFromHistoryAt(TargetRef ref,
                                                    int insertAt,
                                                    String from,
                                                    String text,
                                                    long tsEpochMs) {
    return insertLineInternalAt(ref, insertAt, from, text,
        styles.noticeFrom(), styles.noticeMessage(), false, tsEpochMs);
  }

  public synchronized int prependNoticeFromHistory(TargetRef ref,
                                                   String from,
                                                   String text,
                                                   long tsEpochMs) {
    return insertNoticeFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  public synchronized int insertStatusFromHistoryAt(TargetRef ref,
                                                    int insertAt,
                                                    String from,
                                                    String text,
                                                    long tsEpochMs) {
    return insertLineInternalAt(ref, insertAt, from, text,
        styles.status(), styles.status(), false, tsEpochMs);
  }

  public synchronized int prependStatusFromHistory(TargetRef ref,
                                                   String from,
                                                   String text,
                                                   long tsEpochMs) {
    return insertStatusFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  public synchronized int insertErrorFromHistoryAt(TargetRef ref,
                                                   int insertAt,
                                                   String from,
                                                   String text,
                                                   long tsEpochMs) {
    return insertLineInternalAt(ref, insertAt, from, text,
        styles.error(), styles.error(), false, tsEpochMs);
  }

  public synchronized int prependErrorFromHistory(TargetRef ref,
                                                  String from,
                                                  String text,
                                                  long tsEpochMs) {
    return insertErrorFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  public synchronized int insertPresenceFromHistoryAt(TargetRef ref,
                                                      int insertAt,
                                                      String displayText,
                                                      long tsEpochMs) {
    return insertLineInternalAt(ref, insertAt, null, displayText,
        styles.status(), styles.status(), false, tsEpochMs);
  }

  public synchronized int prependPresenceFromHistory(TargetRef ref,
                                                     String displayText,
                                                     long tsEpochMs) {
    return insertPresenceFromHistoryAt(ref, 0, displayText, tsEpochMs);
  }

  public synchronized int insertSpoilerChatFromHistoryAt(TargetRef ref,
                                                         int insertAt,
                                                         String from,
                                                         String text,
                                                         long tsEpochMs) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, insertAt);
    pos = ensureAtLineStartForInsert(doc, pos);

    String msg = text == null ? "" : text;
    String fromLabel = from == null ? "" : from;
    if (!fromLabel.isBlank()) {
      if (fromLabel.endsWith(":")) {
        fromLabel = fromLabel + " ";
      } else {
        fromLabel = fromLabel + ": ";
      }
    }

    boolean timestampsIncludeChatMessages = false;
    try {
      timestampsIncludeChatMessages = uiSettings != null
          && uiSettings.get() != null
          && uiSettings.get().timestampsIncludeChatMessages();
    } catch (Exception ignored) {
      timestampsIncludeChatMessages = false;
    }
    final String tsPrefixFinal =
        (ts != null && ts.enabled() && timestampsIncludeChatMessages) ? ts.prefixAt(tsEpochMs) : "";

    final int offFinal = pos;
    final TargetRef refFinal = ref;
    final StyledDocument docFinal = doc;
    final String fromFinal = from;
    final String msgFinal = msg;
    final String fromLabelFinal = fromLabel;

    final SpoilerMessageComponent comp = new SpoilerMessageComponent(tsPrefixFinal, fromLabelFinal);

    try {
      if (uiSettings != null && uiSettings.get() != null) {
        comp.setTranscriptFont(new Font(
            uiSettings.get().chatFontFamily(),
            Font.PLAIN,
            uiSettings.get().chatFontSize()
        ));
      }
    } catch (Exception ignored) {
    }

    try {
      if (nickColors != null && nickColors.enabled() && from != null && !from.isBlank()) {
        Color bg = javax.swing.UIManager.getColor("TextPane.background");
        Color fg = javax.swing.UIManager.getColor("TextPane.foreground");
        comp.setFromColor(nickColors.colorForNick(from, bg, fg));
      }
    } catch (Exception ignored) {
    }

    SimpleAttributeSet attrs = new SimpleAttributeSet(styles.message());
    StyleConstants.setComponent(attrs, comp);
    try {
      doc.insertString(offFinal, " ", attrs);
      final Position spoilerPos = doc.createPosition(offFinal);
      comp.setOnReveal(() -> revealSpoilerInPlace(refFinal, docFinal, spoilerPos, comp,
          tsPrefixFinal, fromFinal, msgFinal));
      doc.insertString(offFinal + 1, "\n", styles.timestamp());
      pos = offFinal + 2;
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, offFinal, delta);
    return pos;
  }

  public synchronized int prependSpoilerChatFromHistory(TargetRef ref,
                                                        String from,
                                                        String text,
                                                        long tsEpochMs) {
    return insertSpoilerChatFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  private int insertLineInternalAt(TargetRef ref,
                                   int insertAt,
                                   String from,
                                   String text,
                                   AttributeSet fromStyle,
                                   AttributeSet msgStyle,
                                   boolean allowEmbeds,
                                   Long epochMs) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, epochMs);
    if (doc == null) return Math.max(0, insertAt);

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, insertAt);
    pos = ensureAtLineStartForInsert(doc, pos);
    final int insertionStart = pos;

    try {
      AttributeSet baseForId = msgStyle != null ? msgStyle : styles.message();
      Object styleIdObj = baseForId.getAttribute(ChatStyles.ATTR_STYLE);
      String styleId = styleIdObj != null ? String.valueOf(styleIdObj) : null;

      boolean timestampsIncludeChatMessages = false;
      try {
        timestampsIncludeChatMessages = uiSettings != null
            && uiSettings.get() != null
            && uiSettings.get().timestampsIncludeChatMessages();
      } catch (Exception ignored) {
        timestampsIncludeChatMessages = false;
      }

      if (ts != null && ts.enabled()
          && (ChatStyles.STYLE_STATUS.equals(styleId)
          || ChatStyles.STYLE_ERROR.equals(styleId)
          || ChatStyles.STYLE_NOTICE_MESSAGE.equals(styleId)
          || (timestampsIncludeChatMessages && ChatStyles.STYLE_MESSAGE.equals(styleId)))) {
        String prefix = (epochMs != null) ? ts.prefixAt(epochMs) : ts.prefixNow();
        doc.insertString(pos, prefix, styles.timestamp());
        pos += prefix.length();
      }

      if (from != null && !from.isBlank()) {
        String prefix = from + ": ";
        doc.insertString(pos, prefix, fromStyle != null ? fromStyle : styles.from());
        pos += prefix.length();
      }

      AttributeSet base = msgStyle != null ? msgStyle : styles.message();
      if (renderer != null) {
        pos = renderer.insertRichTextAt(doc, ref, text, base, pos);
      } else {
        String t = text == null ? "" : text;
        doc.insertString(pos, t, base);
        pos += t.length();
      }

      doc.insertString(pos, "\n", styles.timestamp());
      pos += 1;
      if (allowEmbeds) {
      }
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
    return pos;
  }

  private int normalizeInsertAtLineStart(StyledDocument doc, int insertAt) {
    if (doc == null) return 0;
    int len = doc.getLength();
    if (len <= 0) return 0;
    int p = Math.max(0, Math.min(insertAt, len));
    if (p <= 0 || p >= len) return p;

    try {
      Element root = doc.getDefaultRootElement();
      if (root == null) return p;
      int line = root.getElementIndex(p);
      Element el = root.getElement(line);
      if (el == null) return p;
      int start = el.getStartOffset();
      return Math.max(0, Math.min(start, len));
    } catch (Exception ignored) {
      return p;
    }
  }

  private int ensureAtLineStartForInsert(StyledDocument doc, int pos) {
    if (doc == null) return Math.max(0, pos);
    int len = doc.getLength();
    int p = Math.max(0, Math.min(pos, len));
    if (p <= 0) return p;
    try {
      String prev = doc.getText(p - 1, 1);
      if (!"\n".equals(prev)) {
        doc.insertString(p, "\n", styles.timestamp());
        return p + 1;
      }
    } catch (Exception ignored) {
    }
    return p;
  }

  private void shiftCurrentPresenceBlock(TargetRef ref, int insertAt, int delta) {
    if (ref == null || delta == 0) return;
    TranscriptState st = stateByTarget.get(ref);
    if (st == null || st.currentPresenceBlock == null) return;
    PresenceBlock b = st.currentPresenceBlock;
    if (insertAt <= b.startOffset) {
      b.startOffset += delta;
      b.endOffset += delta;
    }
  }

  private void applyOutgoingLineColor(SimpleAttributeSet fromStyle,
                                      SimpleAttributeSet msgStyle,
                                      boolean outgoingLocalEcho) {
    if (!outgoingLocalEcho) return;
    if (fromStyle != null) fromStyle.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);
    if (msgStyle != null) msgStyle.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);

    UiSettings s = safeSettings();
    boolean enabled = s != null && s.clientLineColorEnabled();
    if (!enabled) return;

    Color c = parseHexColor(s.clientLineColor());
    if (c == null) return;

    if (fromStyle != null) {
      fromStyle.addAttribute(ChatStyles.ATTR_OVERRIDE_FG, c);
      StyleConstants.setForeground(fromStyle, c);
    }
    if (msgStyle != null) {
      msgStyle.addAttribute(ChatStyles.ATTR_OVERRIDE_FG, c);
      StyleConstants.setForeground(msgStyle, c);
    }
  }

  private void onNickColorSettingsChanged(PropertyChangeEvent evt) {
    if (!NickColorSettingsBus.PROP_NICK_COLOR_SETTINGS.equals(evt.getPropertyName())) return;
    SwingUtilities.invokeLater(this::restyleAllDocuments);
  }

  private UiSettings safeSettings() {
    try {
      return uiSettings != null ? uiSettings.get() : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Color parseHexColor(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return null;
    if (s.startsWith("#")) s = s.substring(1);
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
    if (s.length() != 6) return null;
    try {
      int rgb = Integer.parseInt(s, 16);
      return new Color(rgb);
    } catch (Exception ignored) {
      return null;
    }
  }

  public void appendSpoilerChat(TargetRef ref, String from, String text) {
    breakPresenceRun(ref);
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return;
    ensureAtLineStart(doc);

    String msg = text == null ? "" : text;
    String fromLabel = from == null ? "" : from;
    if (!fromLabel.isBlank()) {
      if (fromLabel.endsWith(":")) {
        fromLabel = fromLabel + " ";
      } else {
        fromLabel = fromLabel + ": ";
      }
    }

    boolean timestampsIncludeChatMessages = false;
    try {
      timestampsIncludeChatMessages = uiSettings != null
          && uiSettings.get() != null
          && uiSettings.get().timestampsIncludeChatMessages();
    } catch (Exception ignored) {
      timestampsIncludeChatMessages = false;
    }
    final String tsPrefixFinal =
        (ts != null && ts.enabled() && timestampsIncludeChatMessages) ? ts.prefixNow() : "";

    final int offFinal = doc.getLength();
    final TargetRef refFinal = ref;
    final StyledDocument docFinal = doc;
    final String fromFinal = from;
    final String msgFinal = msg;
    final String fromLabelFinal = fromLabel;

    final SpoilerMessageComponent comp = new SpoilerMessageComponent(tsPrefixFinal, fromLabelFinal);
    try {
      if (uiSettings != null && uiSettings.get() != null) {
        comp.setTranscriptFont(new Font(
            uiSettings.get().chatFontFamily(),
            Font.PLAIN,
            uiSettings.get().chatFontSize()
        ));
      }
    } catch (Exception ignored) {
    }
    try {
      if (nickColors != null && nickColors.enabled() && from != null && !from.isBlank()) {
        Color bg = javax.swing.UIManager.getColor("TextPane.background");
        Color fg = javax.swing.UIManager.getColor("TextPane.foreground");
        comp.setFromColor(nickColors.colorForNick(from, bg, fg));
      }
    } catch (Exception ignored) {
    }

    SimpleAttributeSet attrs = new SimpleAttributeSet(styles.message());
    StyleConstants.setComponent(attrs, comp);
    try {
      doc.insertString(offFinal, " ", attrs);
      final Position spoilerPos = doc.createPosition(offFinal);
      comp.setOnReveal(() -> revealSpoilerInPlace(refFinal, docFinal, spoilerPos, comp,
          tsPrefixFinal, fromFinal, msgFinal));

      doc.insertString(doc.getLength(), "\n", styles.timestamp());
    } catch (Exception ignored) {
    }
  }

  public void appendSpoilerChatFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    breakPresenceRun(ref);
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return;
    ensureAtLineStart(doc);

    String msg = text == null ? "" : text;
    String fromLabel = from == null ? "" : from;
    if (!fromLabel.isBlank()) {
      if (fromLabel.endsWith(":")) {
        fromLabel = fromLabel + " ";
      } else {
        fromLabel = fromLabel + ": ";
      }
    }

    boolean timestampsIncludeChatMessages = false;
    try {
      timestampsIncludeChatMessages = uiSettings != null
          && uiSettings.get() != null
          && uiSettings.get().timestampsIncludeChatMessages();
    } catch (Exception ignored) {
      timestampsIncludeChatMessages = false;
    }
    final String tsPrefixFinal =
        (ts != null && ts.enabled() && timestampsIncludeChatMessages) ? ts.prefixAt(tsEpochMs) : "";

    final int offFinal = doc.getLength();
    final TargetRef refFinal = ref;
    final StyledDocument docFinal = doc;
    final String fromFinal = from;
    final String msgFinal = msg;
    final String fromLabelFinal = fromLabel;

    final SpoilerMessageComponent comp = new SpoilerMessageComponent(tsPrefixFinal, fromLabelFinal);

    try {
      if (uiSettings != null && uiSettings.get() != null) {
        comp.setTranscriptFont(new Font(
            uiSettings.get().chatFontFamily(),
            Font.PLAIN,
            uiSettings.get().chatFontSize()
        ));
      }
    } catch (Exception ignored) {
    }

    try {
      if (nickColors != null && nickColors.enabled() && from != null && !from.isBlank()) {
        Color bg = javax.swing.UIManager.getColor("TextPane.background");
        Color fg = javax.swing.UIManager.getColor("TextPane.foreground");
        comp.setFromColor(nickColors.colorForNick(from, bg, fg));
      }
    } catch (Exception ignored) {
    }

    SimpleAttributeSet attrs = new SimpleAttributeSet(styles.message());
    StyleConstants.setComponent(attrs, comp);
    try {
      doc.insertString(offFinal, " ", attrs);

      final Position spoilerPos = doc.createPosition(offFinal);

      comp.setOnReveal(() -> revealSpoilerInPlace(refFinal, docFinal, spoilerPos, comp,
          tsPrefixFinal, fromFinal, msgFinal));

      doc.insertString(doc.getLength(), "\n", styles.timestamp());
    } catch (Exception ignored) {
    }
  }

  private boolean revealSpoilerInPlace(TargetRef ref,
                                    StyledDocument doc,
                                    Position anchor,
                                    SpoilerMessageComponent expected,
                                    String tsPrefix,
                                    String from,
                                    String msg) {
  if (doc == null || anchor == null) return false;
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
      Element el = doc.getCharacterElement(off);
      if (el == null) return false;
      AttributeSet as = el.getAttributes();
      Object comp = as != null ? StyleConstants.getComponent(as) : null;
      if (!(comp instanceof SpoilerMessageComponent)) return false;
      if (expected != null && comp != expected) return false;
      int removeLen = 1;
      if (off + 1 < doc.getLength()) {
        try {
          String next = doc.getText(off + 1, 1);
          if ("\n".equals(next)) removeLen = 2;
        } catch (Exception ignored2) {
        }
      }
      doc.remove(off, removeLen);

      int pos = off;
      if (tsPrefix != null && !tsPrefix.isBlank()) {
        doc.insertString(pos, tsPrefix, styles.timestamp());
        pos += tsPrefix.length();
      }
      if (from != null && !from.isBlank()) {
        AttributeSet fromStyle = styles.from();
        if (nickColors != null && nickColors.enabled()) {
          fromStyle = nickColors.forNick(from, fromStyle);
        }
        String prefix = from + ": ";
        doc.insertString(pos, prefix, fromStyle);
        pos += prefix.length();
      }
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
    }
  }
  return -1;
}
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
    }
    return pos;
  }

  public void appendAction(TargetRef ref, String from, String action) {
    appendAction(ref, from, action, false);
  }

  public void appendAction(TargetRef ref, String from, String action, boolean outgoingLocalEcho) {
    appendActionInternal(ref, from, action, outgoingLocalEcho, true, null);
  }

  public void appendActionFromHistory(TargetRef ref, String from, String action, boolean outgoingLocalEcho, long tsEpochMs) {
    appendActionInternal(ref, from, action, outgoingLocalEcho, false, tsEpochMs);
  }
/**
 * Append an action (/me) with a timestamp, allowing embeds.
 */
public void appendActionAt(TargetRef ref,
                           String from,
                           String action,
                           boolean outgoingLocalEcho,
                           long tsEpochMs) {
  appendActionInternal(ref, from, action, outgoingLocalEcho, true, tsEpochMs);
}
  private void appendActionInternal(TargetRef ref,
                                    String from,
                                    String action,
                                    boolean outgoingLocalEcho,
                                    boolean allowEmbeds,
                                    Long epochMs) {
    breakPresenceRun(ref);
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return;

    String a = action == null ? "" : action;
    ensureAtLineStart(doc);

    try {
      boolean timestampsIncludeChatMessages = false;
      try {
        timestampsIncludeChatMessages = uiSettings != null
            && uiSettings.get() != null
            && uiSettings.get().timestampsIncludeChatMessages();
      } catch (Exception ignored) {
        timestampsIncludeChatMessages = false;
      }

      if (ts != null && ts.enabled() && timestampsIncludeChatMessages) {
        String prefix = (epochMs != null) ? ts.prefixAt(epochMs) : ts.prefixNow();
        doc.insertString(doc.getLength(), prefix, styles.timestamp());
      }

      AttributeSet msgStyle = styles.actionMessage();
      AttributeSet fromStyle = styles.actionFrom();

      if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
        fromStyle = nickColors.forNick(from, fromStyle);
      }

      SimpleAttributeSet ms = new SimpleAttributeSet(msgStyle);
      SimpleAttributeSet fs = new SimpleAttributeSet(fromStyle);
      applyOutgoingLineColor(fs, ms, outgoingLocalEcho);

      doc.insertString(doc.getLength(), "* ", ms);
      if (from != null && !from.isBlank()) {
        doc.insertString(doc.getLength(), from, fs);
        doc.insertString(doc.getLength(), " ", ms);
      }

      renderer.insertRichText(doc, ref, a, ms);
      doc.insertString(doc.getLength(), "\n", styles.timestamp());

      if (!allowEmbeds) {
        return;
      }

      if (imageEmbeds != null && uiSettings != null && uiSettings.get().imageEmbedsEnabled()) {
        imageEmbeds.appendEmbeds(ref, doc, a);
      }

      if (linkPreviews != null && uiSettings != null && uiSettings.get().linkPreviewsEnabled()) {
        linkPreviews.appendPreviews(ref, doc, a);
      }
    } catch (Exception ignored) {
    }
  }

  public void appendNotice(TargetRef ref, String from, String text) {
    breakPresenceRun(ref);
    appendLine(ref, from, text, styles.noticeFrom(), styles.noticeMessage());
  }

  public void appendStatus(TargetRef ref, String from, String text) {
    breakPresenceRun(ref);
    appendLine(ref, from, text, styles.status(), styles.status());
  }

  public void appendError(TargetRef ref, String from, String text) {
    breakPresenceRun(ref);
    appendLine(ref, from, text, styles.error(), styles.error());
  }

  public void appendNoticeFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, styles.noticeFrom(), styles.noticeMessage(), false, tsEpochMs);
  }

  public void appendStatusFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, styles.status(), styles.status(), false, tsEpochMs);
  }

  public void appendErrorFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, styles.error(), styles.error(), false, tsEpochMs);
  }
/**
 * Append a notice with a timestamp, allowing embeds.
 */
public void appendNoticeAt(TargetRef ref, String from, String text, long tsEpochMs) {
  breakPresenceRun(ref);
  appendLineInternal(ref, from, text, styles.noticeFrom(), styles.noticeMessage(), true, tsEpochMs);
}

/**
 * Append a status line with a timestamp, allowing embeds.
 */
public void appendStatusAt(TargetRef ref, String from, String text, long tsEpochMs) {
  breakPresenceRun(ref);
  appendLineInternal(ref, from, text, styles.status(), styles.status(), true, tsEpochMs);
}

/**
 * Append an error line with a timestamp, allowing embeds.
 */
public void appendErrorAt(TargetRef ref, String from, String text, long tsEpochMs) {
  breakPresenceRun(ref);
  appendLineInternal(ref, from, text, styles.error(), styles.error(), true, tsEpochMs);
}
  public void appendPresenceFromHistory(TargetRef ref, String displayText, long tsEpochMs) {
    breakPresenceRun(ref);
    appendLineInternal(ref, null, displayText, styles.status(), styles.status(), false, tsEpochMs);
  }

  private void breakPresenceRun(TargetRef ref) {
    if (ref == null) return;
    TranscriptState st = stateByTarget.get(ref);
    if (st != null) st.currentPresenceBlock = null;
  }

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
    }
  }

  private void foldBlock(StyledDocument doc, TargetRef ref, PresenceBlock block) {
    if (doc == null || block == null) return;

    int start = Math.max(0, Math.min(block.startOffset, doc.getLength()));
    int end = Math.max(0, Math.min(block.endOffset, doc.getLength()));
    if (end <= start) return;

    try {
      doc.remove(start, end - start);
      PresenceFoldComponent comp = new PresenceFoldComponent(block.entries);

      SimpleAttributeSet attrs = new SimpleAttributeSet(styles.status());
      StyleConstants.setComponent(attrs, comp);
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
      int insertPos = start;
      if (insertPos > 0) {
        try {
          String prev = doc.getText(insertPos - 1, 1);
          if (!"\n".equals(prev)) {
            doc.insertString(insertPos, "\n", styles.timestamp());
            insertPos++;
          }
        } catch (Exception ignored2) {
        }
      }

      doc.insertString(insertPos, " ", attrs);
      doc.insertString(insertPos + 1, "\n", styles.timestamp());

      block.folded = true;
      block.component = comp;
      block.startOffset = insertPos;
      block.endOffset = insertPos + 2;
    } catch (Exception ignored) {
    }
  }

  private static final class TranscriptState {
    Long earliestEpochMsSeen;
    PresenceBlock currentPresenceBlock;
    LoadOlderControl loadOlderControl;
    HistoryDividerControl historyDivider;
  }

  private static final class LoadOlderControl {
    final Position pos;
    final LoadOlderMessagesComponent component;

    private LoadOlderControl(Position pos, LoadOlderMessagesComponent component) {
      this.pos = pos;
      this.component = component;
    }
  }

  private static final class HistoryDividerControl {
    final Position pos;
    final HistoryDividerComponent component;

    private HistoryDividerControl(Position pos, HistoryDividerComponent component) {
      this.pos = pos;
      this.component = component;
    }
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

  public synchronized void restyleAllDocuments() {
    for (StyledDocument doc : docs.values()) {
      restyle(doc);
    }
  }

  private void restyle(StyledDocument doc) {
    if (doc == null) return;

    UiSettings s = safeSettings();
    boolean outgoingColorEnabled = s != null && s.clientLineColorEnabled();
    Color outgoingColor = outgoingColorEnabled ? parseHexColor(s.clientLineColor()) : null;

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
      Object url = old.getAttribute(ChatStyles.ATTR_URL);
      if (url != null) {
        fresh.addAttribute(ChatStyles.ATTR_URL, url);
      }
      Object chan = old.getAttribute(ChatStyles.ATTR_CHANNEL);
      if (chan != null) {
        fresh.addAttribute(ChatStyles.ATTR_CHANNEL, chan);
      }
      java.awt.Component comp = StyleConstants.getComponent(old);
      if (comp != null) {
        StyleConstants.setComponent(fresh, comp);
      }
      Object nickLower = old.getAttribute(NickColorService.ATTR_NICK);
      if (nickLower != null) {
        String n = String.valueOf(nickLower);
        fresh.addAttribute(NickColorService.ATTR_NICK, n);
        if (nickColors != null) {
          nickColors.applyColor(fresh, n);
        }
      }
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
      boolean outgoing = Boolean.TRUE.equals(old.getAttribute(ChatStyles.ATTR_OUTGOING));
      if (outgoing) {
        fresh.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);
        if (outgoingColorEnabled && outgoingColor != null) {
          fresh.addAttribute(ChatStyles.ATTR_OVERRIDE_FG, outgoingColor);
          StyleConstants.setForeground(fresh, outgoingColor);
        }
      }
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
      if (styleId != null) {
        fresh.addAttribute(ChatStyles.ATTR_STYLE, styleId);
      }

      doc.setCharacterAttributes(start, end - start, fresh, true);
      offset = end;
    }
  }
}

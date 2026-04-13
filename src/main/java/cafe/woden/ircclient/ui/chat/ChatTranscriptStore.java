package cafe.woden.ircclient.ui.chat;

import static cafe.woden.ircclient.ui.chat.ChatTranscriptMessageMetadataSupport.formatIrcv3Tags;
import static cafe.woden.ircclient.ui.chat.ChatTranscriptMessageMetadataSupport.normalizeIrcv3Tags;
import static cafe.woden.ircclient.ui.chat.ChatTranscriptMessageMetadataSupport.normalizeMessageId;
import static cafe.woden.ircclient.ui.chat.ChatTranscriptMessageMetadataSupport.normalizePendingId;

import cafe.woden.ircclient.app.api.ChatTranscriptHistoryPort;
import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.app.api.PresenceKind;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.model.FilterAction;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.embed.ChatImageEmbedder;
import cafe.woden.ircclient.ui.chat.embed.ChatLinkPreviewEmbedder;
import cafe.woden.ircclient.ui.chat.fold.FilteredFoldComponent;
import cafe.woden.ircclient.ui.chat.fold.FilteredHintComponent;
import cafe.woden.ircclient.ui.chat.fold.FilteredLineComponent;
import cafe.woden.ircclient.ui.chat.fold.FilteredOverflowComponent;
import cafe.woden.ircclient.ui.chat.fold.HistoryDividerComponent;
import cafe.woden.ircclient.ui.chat.fold.LoadOlderMessagesComponent;
import cafe.woden.ircclient.ui.chat.fold.MessageReactionsComponent;
import cafe.woden.ircclient.ui.chat.fold.PresenceFoldComponent;
import cafe.woden.ircclient.ui.chat.fold.SpoilerMessageComponent;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.render.IrcFormatting;
import cafe.woden.ircclient.ui.filter.FilterContext;
import cafe.woden.ircclient.ui.filter.FilterEngine;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.util.EmojiFontSupport;
import jakarta.annotation.PreDestroy;
import java.awt.Color;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@SecondaryAdapter
@InterfaceLayer
@Lazy
public class ChatTranscriptStore implements ChatTranscriptHistoryPort {

  private static final int RESTYLE_ELEMENTS_PER_SLICE = 180;
  private static final int DEFAULT_TRANSCRIPT_MAX_LINES_PER_TARGET = 4000;
  private static final int MAX_TRANSCRIPT_LINES_PER_TARGET = 200_000;
  private static final int REPLY_PREVIEW_CACHE_LIMIT_PER_TARGET = 512;
  private static final int REDACTED_MESSAGE_CACHE_LIMIT_PER_TARGET = 512;
  private static final int REPLY_PREVIEW_TEXT_MAX_CHARS = 120;
  private static final String MANUAL_PREVIEW_MARKER = " \uD83D\uDC41";
  private static final String REDACTED_MESSAGE_PLACEHOLDER = "[message redacted]";
  private static final String AUX_ROW_KIND_HISTORY_DIVIDER = "history-divider";
  private static final String AUX_ROW_KIND_LOAD_OLDER = "load-older";
  private static final String AUX_ROW_KIND_READ_MARKER = "read-marker";
  private static final String AUX_ROW_KIND_REACTION_SUMMARY = "reaction-summary";

  /**
   * Step 5.2: Safety cap for history/backfill. After this many filtered placeholder/hint runs are
   * created in a single history batch, we stop creating new placeholder rows and collapse the
   * remainder into one summary row.
   */
  private final ChatStyles styles;

  private final ChatRichTextRenderer renderer;
  private final ChatTimestampFormatter ts;
  private final NickColorService nickColors;
  private final ChatImageEmbedder imageEmbeds;
  private final ChatLinkPreviewEmbedder linkPreviews;
  private final UiSettingsBus uiSettings;
  private final NickColorSettingsBus nickColorSettings;
  private final FilterEngine filterEngine;

  private final ChatTranscriptFilteredRunSupport.Context filteredRunSupportContext;
  private final ChatTranscriptMatrixDisplayNameSupport.Context matrixDisplayNameContext;
  private final ChatTranscriptMessageStateSupport.Context messageStateSupportContext;
  private final ChatTranscriptSenderStyleSupport.Context senderStyleSupportContext;

  private final PropertyChangeListener nickColorSettingsListener = this::onNickColorSettingsChanged;

  private final Map<TargetRef, StyledDocument> docs = new HashMap<>();
  private final Map<TargetRef, TranscriptState> stateByTarget = new HashMap<>();
  private List<StyledDocument> restylePassDocs = List.of();
  private int restylePassDocIndex = 0;
  private int restylePassDocOffset = 0;
  private boolean restylePassRunning = false;
  private boolean restylePassRestartRequested = false;
  private volatile ReactionChipActionHandler reactionChipActionHandler =
      (target, messageId, reactionToken, unreactRequested) -> {};

  @FunctionalInterface
  public interface ReactionChipActionHandler {
    void onReactionAction(
        TargetRef target, String messageId, String reactionToken, boolean unreactRequested);
  }

  public ChatTranscriptStore(
      ChatStyles styles,
      ChatRichTextRenderer renderer,
      ChatTimestampFormatter ts,
      NickColorService nickColors,
      NickColorSettingsBus nickColorSettings,
      ChatImageEmbedder imageEmbeds,
      ChatLinkPreviewEmbedder linkPreviews,
      UiSettingsBus uiSettings,
      FilterEngine filterEngine,
      UserListStore userListStore) {
    this.styles = styles;
    this.renderer = renderer;
    this.ts = ts;
    this.nickColors = nickColors;
    this.nickColorSettings = nickColorSettings;
    this.imageEmbeds = imageEmbeds;
    this.linkPreviews = linkPreviews;
    this.uiSettings = uiSettings;
    this.filterEngine = filterEngine;

    this.filteredRunSupportContext =
        new ChatTranscriptFilteredRunSupport.Context(styles, this::withLineMeta);
    this.matrixDisplayNameContext =
        new ChatTranscriptMatrixDisplayNameSupport.Context(uiSettings, userListStore, docs::get);
    this.messageStateSupportContext =
        new ChatTranscriptMessageStateSupport.Context(
            REPLY_PREVIEW_TEXT_MAX_CHARS, REDACTED_MESSAGE_PLACEHOLDER, System::currentTimeMillis);
    this.senderStyleSupportContext =
        new ChatTranscriptSenderStyleSupport.Context(
            styles,
            nickColors,
            this::withLineMeta,
            this::applyOutgoingLineColor,
            this::applyNotificationRuleHighlightColor);

    if (this.nickColorSettings != null) {
      this.nickColorSettings.addListener(nickColorSettingsListener);
    }
  }

  @PreDestroy
  void shutdown() {
    if (nickColorSettings != null) {
      nickColorSettings.removeListener(nickColorSettingsListener);
    }
  }

  record LineMeta(
      String bufferKey,
      LogKind kind,
      LogDirection direction,
      String fromNick,
      Long epochMs,
      Set<String> tags,
      String messageId,
      String ircv3Tags,
      Map<String, String> ircv3TagsMap) {
    String tagsDisplay() {
      if (tags == null || tags.isEmpty()) return "";
      return String.join(" ", tags);
    }

    String messageIdDisplay() {
      return messageId == null ? "" : messageId;
    }

    String ircv3TagsDisplay() {
      return ircv3Tags == null ? "" : ircv3Tags;
    }
  }

  public record RedactedMessageContent(
      String messageId,
      LogKind originalKind,
      String originalFromNick,
      String originalText,
      Long originalEpochMs,
      String redactedBy,
      Long redactedAtEpochMs) {}

  record MessageContentSnapshot(LogKind kind, String fromNick, String renderedText, Long epochMs) {}

  private LineMeta buildLineMeta(
      TargetRef ref,
      LogKind kind,
      LogDirection dir,
      String fromNick,
      Long epochMs,
      PresenceEvent presenceEvent) {
    return buildLineMeta(ref, kind, dir, fromNick, epochMs, presenceEvent, "", Map.of());
  }

  private LineMeta buildLineMeta(
      TargetRef ref,
      LogKind kind,
      LogDirection dir,
      String fromNick,
      Long epochMs,
      PresenceEvent presenceEvent,
      String messageId,
      Map<String, String> ircv3Tags) {
    String msgId = normalizeMessageId(messageId);
    Map<String, String> tagsMap = normalizeIrcv3Tags(ircv3Tags);
    Set<String> tags =
        ChatTranscriptLineTagSupport.computeTags(
            kind, dir, fromNick, presenceEvent, msgId, tagsMap);
    return new LineMeta(
        bufferKey(ref),
        kind,
        dir,
        fromNick,
        epochMs,
        tags,
        msgId,
        formatIrcv3Tags(tagsMap),
        tagsMap);
  }

  private SimpleAttributeSet withLineMeta(AttributeSet base, LineMeta meta) {
    SimpleAttributeSet a = new SimpleAttributeSet(base);
    if (meta == null) return a;

    if (meta.bufferKey() != null && !meta.bufferKey().isBlank()) {
      a.addAttribute(ChatStyles.ATTR_META_BUFFER_KEY, meta.bufferKey());
    }
    if (meta.kind() != null) {
      a.addAttribute(ChatStyles.ATTR_META_KIND, meta.kind().name());
    }
    if (meta.direction() != null) {
      a.addAttribute(ChatStyles.ATTR_META_DIRECTION, meta.direction().name());
    }
    if (meta.fromNick() != null && !meta.fromNick().isBlank()) {
      a.addAttribute(ChatStyles.ATTR_META_FROM, meta.fromNick());
    }
    if (meta.tags() != null && !meta.tags().isEmpty()) {
      a.addAttribute(ChatStyles.ATTR_META_TAGS, meta.tagsDisplay());
    }
    if (meta.epochMs() != null && meta.epochMs() > 0) {
      a.addAttribute(ChatStyles.ATTR_META_EPOCH_MS, meta.epochMs());
    }
    if (!meta.messageIdDisplay().isBlank()) {
      a.addAttribute(ChatStyles.ATTR_META_MSGID, meta.messageIdDisplay());
    }
    if (!meta.ircv3TagsDisplay().isBlank()) {
      a.addAttribute(ChatStyles.ATTR_META_IRCV3_TAGS, meta.ircv3TagsDisplay());
    }
    return a;
  }

  private SimpleAttributeSet withExistingMeta(AttributeSet base, AttributeSet existing) {
    return ChatTranscriptLineMetaSupport.withExistingMeta(base, existing);
  }

  private SimpleAttributeSet withAuxiliaryRowKind(AttributeSet base, String auxiliaryRowKind) {
    SimpleAttributeSet attrs = new SimpleAttributeSet(base);
    String kind = Objects.toString(auxiliaryRowKind, "").trim();
    if (!kind.isEmpty()) {
      attrs.addAttribute(ChatStyles.ATTR_META_AUX_ROW_KIND, kind);
    }
    return attrs;
  }

  private boolean shouldHideLine(
      TargetRef ref,
      LogKind kind,
      LogDirection dir,
      String fromNick,
      String text,
      Set<String> tags) {
    FilterEngine.Match m = firstFilterMatch(ref, kind, dir, fromNick, text, tags);
    return m != null && m.isHide();
  }

  private FilterEngine.Match firstFilterMatch(
      TargetRef ref,
      LogKind kind,
      LogDirection dir,
      String fromNick,
      String text,
      Set<String> tags) {
    if (ref == null) return null;
    if (filterEngine == null) return null;
    try {
      return filterEngine.firstMatch(
          new FilterContext(ref, kind, dir, fromNick, text, tags != null ? tags : Set.of()));
    } catch (Exception ignored) {
      return null;
    }
  }

  private FilterEngine.Match hideMatch(
      TargetRef ref,
      LogKind kind,
      LogDirection dir,
      String fromNick,
      String text,
      Set<String> tags) {
    FilterEngine.Match m = firstFilterMatch(ref, kind, dir, fromNick, text, tags);
    return (m != null && m.isHide()) ? m : null;
  }

  private SimpleAttributeSet withFilterMatch(AttributeSet base, FilterEngine.Match match) {
    SimpleAttributeSet out = new SimpleAttributeSet(base);
    if (match == null || match.action() == null) return out;

    if (match.ruleId() != null) {
      out.addAttribute(ChatStyles.ATTR_META_FILTER_RULE_ID, match.ruleId().toString());
    }
    String ruleName = Objects.toString(match.ruleName(), "").trim();
    if (!ruleName.isEmpty()) {
      out.addAttribute(ChatStyles.ATTR_META_FILTER_RULE_NAME, ruleName);
    }
    out.addAttribute(
        ChatStyles.ATTR_META_FILTER_ACTION, match.action().name().toLowerCase(Locale.ROOT));

    applyFilterActionStyle(out, match.action());
    return out;
  }

  private void applyFilterActionStyle(SimpleAttributeSet attrs, FilterAction action) {
    if (attrs == null || action == null) return;

    switch (action) {
      case HIDE -> {
        // HIDE actions are rendered via placeholders; no visible style override.
      }
      case DIM -> {
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted == null) muted = UIManager.getColor("Component.disabledForeground");
        if (muted != null) {
          StyleConstants.setForeground(attrs, muted);
        }
        StyleConstants.setItalic(attrs, true);
      }
      case HIGHLIGHT -> {
        AttributeSet mention = styles.mention();
        Color mentionFg = StyleConstants.getForeground(mention);
        Color mentionBg = StyleConstants.getBackground(mention);
        if (mentionFg != null) {
          StyleConstants.setForeground(attrs, mentionFg);
        }
        if (mentionBg != null) {
          StyleConstants.setBackground(attrs, mentionBg);
        }
        StyleConstants.setBold(attrs, true);
      }
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

  private String bufferKey(TargetRef ref) {
    if (ref == null) return "*/status";
    return ref.serverId() + "/" + ref.key();
  }

  private void endFilteredRun(TargetRef ref) {
    if (ref == null) return;
    TranscriptState st = stateByTarget.get(ref);
    if (st != null) {
      st.currentFilteredRun = null;
      st.currentFilteredHintRun = null;
    }
  }

  private void endFilteredInsertRun(TargetRef ref) {
    if (ref == null) return;
    TranscriptState st = stateByTarget.get(ref);
    if (st != null) {
      st.currentFilteredRunInsert = null;
      st.currentFilteredHintRunInsert = null;
    }
  }

  /**
   * Explicit batch boundary for history/backfill insertion.
   *
   * <p>History loaders typically prepend many lines in a tight loop. We want filtered
   * placeholders/hints to group consecutive hidden lines within that loop, but we do <b>not</b>
   * want a filtered run from a previous load to keep growing across separate paging operations.
   *
   * <p>Call this once before a batch of {@code insert*FromHistoryAt(...)} calls.
   */
  public synchronized void beginHistoryInsertBatch(TargetRef ref) {
    beginHistoryInsertBatch(ref, false);
  }

  public synchronized void beginHistoryInsertBatch(TargetRef ref, boolean forceDeferRichText) {
    if (ref == null) return;
    ensureTargetExists(ref);
    endFilteredInsertRun(ref);

    TranscriptState st = stateByTarget.get(ref);
    if (st != null) {
      st.historyInsertBatchActive = true;
      st.historyInsertPlaceholderRunsCreated = 0;
      st.historyInsertHintRunsCreated = 0;
      st.historyInsertOverflowRun = null;
      st.forceDeferRichTextDuringHistoryBatch = forceDeferRichText;
    }
  }

  /**
   * Optional end-of-batch signal for history/backfill insertion.
   *
   * <p>Calling this is safe but not strictly required as long as callers invoke {@link
   * #beginHistoryInsertBatch(TargetRef)} before each subsequent batch.
   */
  public synchronized void endHistoryInsertBatch(TargetRef ref) {
    if (ref == null) return;
    endFilteredInsertRun(ref);

    TranscriptState st = stateByTarget.get(ref);
    if (st != null) {
      st.historyInsertBatchActive = false;
      st.historyInsertPlaceholderRunsCreated = 0;
      st.historyInsertHintRunsCreated = 0;
      st.historyInsertOverflowRun = null;
      st.forceDeferRichTextDuringHistoryBatch = false;
    }
  }

  private boolean shouldDeferRichTextDuringHistoryBatch(TargetRef ref) {
    if (ref == null) return false;
    TranscriptState st = stateByTarget.get(ref);
    if (st == null || !st.historyInsertBatchActive) return false;
    if (st.forceDeferRichTextDuringHistoryBatch) return true;
    try {
      UiSettings s = uiSettings != null ? uiSettings.get() : null;
      return s != null && s.chatHistoryDeferRichTextDuringBatch();
    } catch (Exception ignored) {
      return false;
    }
  }

  private void onFilteredLineAppend(
      TargetRef ref, String previewText, LineMeta hiddenMeta, FilterEngine.Match match) {
    if (ref == null) return;
    if (filterEngine == null) return;
    if (match == null || !match.isHide()) return;

    ensureTargetExists(ref);
    noteEpochMs(ref, hiddenMeta != null ? hiddenMeta.epochMs() : null);

    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return;

    FilterEngine.Effective eff = filterEngine.effectiveFor(ref);
    if (!eff.placeholdersEnabled()) {
      // Placeholders are OFF, but we still want a tiny visible hint so the user
      // doesn't get "unread but nothing visible" confusion.
      onFilteredLineHintAppend(ref, hiddenMeta, match);
      return;
    }

    FilteredRun run = st.currentFilteredRun;
    FilteredFoldComponent comp = (run != null) ? run.component : null;

    // Safety cap: avoid a single placeholder fold representing an unbounded number of hidden lines.
    int maxRun = Math.max(0, eff.placeholderMaxLinesPerRun());
    if (comp != null && maxRun > 0 && comp.count() >= maxRun) {
      st.currentFilteredRun = null;
      run = null;
      comp = null;
    }

    if (comp == null) {
      // A placeholder is a visible element, so it should break any active presence fold...
      breakPresenceRun(ref);
      ensureAtLineStart(doc);

      boolean collapsed = eff.placeholdersCollapsed();
      int maxPreviewLines = eff.placeholderMaxPreviewLines();

      comp = new FilteredFoldComponent(collapsed, maxPreviewLines);
      try {
        comp.setMaxTagsInTooltip(eff.placeholderTooltipMaxTags());
      } catch (Exception ignored) {
      }
      Font f = safeTranscriptFont();
      if (f != null) comp.setTranscriptFont(f);

      long tsEpochMs =
          (hiddenMeta != null && hiddenMeta.epochMs() != null && hiddenMeta.epochMs() > 0)
              ? hiddenMeta.epochMs()
              : System.currentTimeMillis();
      LineMeta meta =
          ChatTranscriptFilteredRunSupport.buildFilteredMeta(
              hiddenMeta, tsEpochMs, false, hiddenMeta != null ? hiddenMeta.tags() : null);

      try {
        int insertAt = doc.getLength();
        SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
        attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
        ChatTranscriptFilteredRunSupport.attachFilterMatch(attrs, match, false);
        StyleConstants.setComponent(attrs, comp);

        doc.insertString(insertAt, " ", attrs);
        doc.insertString(doc.getLength(), "\n", withLineMeta(styles.timestamp(), meta));

        run = new FilteredRun(doc.createPosition(insertAt), comp);
        st.currentFilteredRun = run;
      } catch (Exception ignored) {
        st.currentFilteredRun = new FilteredRun(null, comp);
        run = st.currentFilteredRun;
      }
    }

    // Track which filter(s) caused this run, for inspector visibility.
    if (run != null) {
      run.observe(match, hiddenMeta);
      ChatTranscriptFilteredRunSupport.updateFilteredRunAttributes(
          filteredRunSupportContext, doc, run, false);
    }

    try {
      comp.addFilteredLine(previewText);
    } catch (Exception ignored) {
    }
    enforceTranscriptLineCap(ref, doc);
  }

  private void onFilteredLineHintAppend(
      TargetRef ref, LineMeta hiddenMeta, FilterEngine.Match match) {
    if (ref == null) return;
    if (match == null || !match.isHide()) return;

    ensureTargetExists(ref);
    noteEpochMs(ref, hiddenMeta != null ? hiddenMeta.epochMs() : null);

    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return;

    FilteredHintRun run = st.currentFilteredHintRun;
    FilteredHintComponent comp = (run != null) ? run.component : null;
    if (comp == null) {
      // A hint is a visible element, so it should break any active presence fold...
      breakPresenceRun(ref);
      ensureAtLineStart(doc);

      comp = new FilteredHintComponent();
      try {
        comp.setMaxTagsInTooltip(filterEngine.effectiveFor(ref).placeholderTooltipMaxTags());
      } catch (Exception ignored) {
      }
      Font f = safeTranscriptFont();
      if (f != null) comp.setTranscriptFont(f);

      long tsEpochMs =
          (hiddenMeta != null && hiddenMeta.epochMs() != null && hiddenMeta.epochMs() > 0)
              ? hiddenMeta.epochMs()
              : System.currentTimeMillis();
      LineMeta meta =
          ChatTranscriptFilteredRunSupport.buildFilteredMeta(
              hiddenMeta, tsEpochMs, true, hiddenMeta != null ? hiddenMeta.tags() : null);

      try {
        int insertAt = doc.getLength();
        SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
        attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
        ChatTranscriptFilteredRunSupport.attachFilterMatch(attrs, match, false);
        StyleConstants.setComponent(attrs, comp);

        doc.insertString(insertAt, " ", attrs);
        doc.insertString(doc.getLength(), "\n", withLineMeta(styles.timestamp(), meta));

        run = new FilteredHintRun(doc.createPosition(insertAt), comp);
        st.currentFilteredHintRun = run;
      } catch (Exception ignored) {
        st.currentFilteredHintRun = new FilteredHintRun(null, comp);
        run = st.currentFilteredHintRun;
      }
    }

    if (run != null) {
      run.observe(match, hiddenMeta);
      ChatTranscriptFilteredRunSupport.updateFilteredRunAttributes(
          filteredRunSupportContext, doc, run, true);
    }

    try {
      comp.addFilteredLine();
    } catch (Exception ignored) {
    }
    enforceTranscriptLineCap(ref, doc);
  }

  /**
   * History/backfill insertion path for filtered lines. Unlike {@link #onFilteredLineAppend}, this
   * inserts the placeholder/hint row at the given insertion offset (typically the top of the
   * document when loading older messages).
   *
   * <p>We keep separate run-tracking for inserts so we don't accidentally "reuse" the live append
   * placeholder run (which would attach hidden lines to the wrong component).
   */
  private int onFilteredLineInsertAt(
      TargetRef ref,
      int insertAt,
      String previewText,
      LineMeta hiddenMeta,
      FilterEngine.Match match) {
    if (ref == null) return Math.max(0, insertAt);
    if (filterEngine == null) return Math.max(0, insertAt);
    if (match == null || !match.isHide()) return Math.max(0, insertAt);

    ensureTargetExists(ref);
    noteEpochMs(ref, hiddenMeta != null ? hiddenMeta.epochMs() : null);

    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return Math.max(0, insertAt);

    FilterEngine.Effective eff = filterEngine.effectiveFor(ref);

    // Optional tuning: allow users to suppress placeholders/hints for history/backfill entirely.
    if (!eff.historyPlaceholdersEnabled()) {
      return Math.max(0, insertAt);
    }

    if (!eff.placeholdersEnabled()) {
      return onFilteredLineHintInsertAt(ref, insertAt, hiddenMeta, match);
    }

    FilteredRun run = st.currentFilteredRunInsert;
    FilteredFoldComponent comp = (run != null) ? run.component : null;

    // Safety cap: avoid a single placeholder fold representing an unbounded number of hidden lines.
    int maxRun = Math.max(0, eff.placeholderMaxLinesPerRun());
    if (comp != null && maxRun > 0 && comp.count() >= maxRun) {
      st.currentFilteredRunInsert = null;
      run = null;
      comp = null;
    }

    if (comp == null) {
      // Step 5.2: Avoid creating an unbounded number of placeholder rows during history loads.
      // Once we exceed the per-batch cap, collapse the remainder into a single summary row.
      int maxBatchRuns = Math.max(0, eff.historyPlaceholderMaxRunsPerBatch());
      if (st.historyInsertBatchActive
          && maxBatchRuns > 0
          && st.historyInsertPlaceholderRunsCreated >= maxBatchRuns) {
        return onFilteredOverflowInsertAt(ref, insertAt, hiddenMeta, match, eff);
      }

      int beforeLen = doc.getLength();
      int pos = normalizeInsertAtLineStart(doc, insertAt);
      pos = ensureAtLineStartForInsert(doc, pos);
      final int insertionStart = pos;

      boolean collapsed = eff.placeholdersCollapsed();
      int maxPreviewLines = eff.placeholderMaxPreviewLines();

      comp = new FilteredFoldComponent(collapsed, maxPreviewLines);
      try {
        comp.setMaxTagsInTooltip(eff.placeholderTooltipMaxTags());
      } catch (Exception ignored) {
      }
      Font f = safeTranscriptFont();
      if (f != null) comp.setTranscriptFont(f);

      long tsEpochMs =
          (hiddenMeta != null && hiddenMeta.epochMs() != null && hiddenMeta.epochMs() > 0)
              ? hiddenMeta.epochMs()
              : System.currentTimeMillis();
      LineMeta meta =
          ChatTranscriptFilteredRunSupport.buildFilteredMeta(
              hiddenMeta, tsEpochMs, false, hiddenMeta != null ? hiddenMeta.tags() : null);

      try {
        SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
        attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
        ChatTranscriptFilteredRunSupport.attachFilterMatch(attrs, match, false);
        StyleConstants.setComponent(attrs, comp);

        doc.insertString(pos, " ", attrs);
        doc.insertString(pos + 1, "\n", withLineMeta(styles.timestamp(), meta));

        run = new FilteredRun(doc.createPosition(pos), comp);
        st.currentFilteredRunInsert = run;

        if (st.historyInsertBatchActive) {
          st.historyInsertPlaceholderRunsCreated++;
        }
      } catch (Exception ignored) {
        st.currentFilteredRunInsert = new FilteredRun(null, comp);
        run = st.currentFilteredRunInsert;

        if (st.historyInsertBatchActive) {
          st.historyInsertPlaceholderRunsCreated++;
        }
      }

      int delta = doc.getLength() - beforeLen;
      shiftCurrentPresenceBlock(ref, insertionStart, delta);

      // After inserting a visible element, the caller should continue inserting after it.
      insertAt = insertionStart + delta;
    }

    if (run != null) {
      run.observe(match, hiddenMeta);
      ChatTranscriptFilteredRunSupport.updateFilteredRunAttributes(
          filteredRunSupportContext, doc, run, false);
    }

    try {
      comp.addFilteredLine(previewText);
    } catch (Exception ignored) {
    }

    // Hidden lines don't take up any visible space beyond the placeholder itself,
    // so the insertion offset only advances when we had to create a new placeholder.
    int trimmed = enforceTranscriptLineCap(ref, doc);
    if (trimmed > 0) {
      insertAt = Math.max(0, insertAt - trimmed);
    }
    return Math.max(0, insertAt);
  }

  private int onFilteredLineHintInsertAt(
      TargetRef ref, int insertAt, LineMeta hiddenMeta, FilterEngine.Match match) {
    if (ref == null) return Math.max(0, insertAt);
    if (match == null || !match.isHide()) return Math.max(0, insertAt);

    ensureTargetExists(ref);
    noteEpochMs(ref, hiddenMeta != null ? hiddenMeta.epochMs() : null);

    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return Math.max(0, insertAt);

    FilterEngine.Effective eff = filterEngine.effectiveFor(ref);

    // Optional tuning: allow users to suppress placeholders/hints for history/backfill entirely.
    if (!eff.historyPlaceholdersEnabled()) {
      return Math.max(0, insertAt);
    }

    FilteredHintRun run = st.currentFilteredHintRunInsert;
    FilteredHintComponent comp = (run != null) ? run.component : null;

    if (comp == null) {
      // Step 5.2: Avoid creating an unbounded number of hint rows during history loads.
      int maxBatchRuns = Math.max(0, eff.historyPlaceholderMaxRunsPerBatch());
      if (st.historyInsertBatchActive
          && maxBatchRuns > 0
          && st.historyInsertHintRunsCreated >= maxBatchRuns) {
        return onFilteredOverflowInsertAt(ref, insertAt, hiddenMeta, match, eff);
      }

      int beforeLen = doc.getLength();
      int pos = normalizeInsertAtLineStart(doc, insertAt);
      pos = ensureAtLineStartForInsert(doc, pos);
      final int insertionStart = pos;

      comp = new FilteredHintComponent();
      try {
        comp.setMaxTagsInTooltip(eff.placeholderTooltipMaxTags());
      } catch (Exception ignored) {
      }
      Font f = safeTranscriptFont();
      if (f != null) comp.setTranscriptFont(f);

      long tsEpochMs =
          (hiddenMeta != null && hiddenMeta.epochMs() != null && hiddenMeta.epochMs() > 0)
              ? hiddenMeta.epochMs()
              : System.currentTimeMillis();
      LineMeta meta =
          ChatTranscriptFilteredRunSupport.buildFilteredMeta(
              hiddenMeta, tsEpochMs, true, hiddenMeta != null ? hiddenMeta.tags() : null);

      try {
        SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
        attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
        ChatTranscriptFilteredRunSupport.attachFilterMatch(attrs, match, false);
        StyleConstants.setComponent(attrs, comp);

        doc.insertString(pos, " ", attrs);
        doc.insertString(pos + 1, "\n", withLineMeta(styles.timestamp(), meta));

        run = new FilteredHintRun(doc.createPosition(pos), comp);
        st.currentFilteredHintRunInsert = run;

        if (st.historyInsertBatchActive) {
          st.historyInsertHintRunsCreated++;
        }
      } catch (Exception ignored) {
        st.currentFilteredHintRunInsert = new FilteredHintRun(null, comp);
        run = st.currentFilteredHintRunInsert;

        if (st.historyInsertBatchActive) {
          st.historyInsertHintRunsCreated++;
        }
      }

      int delta = doc.getLength() - beforeLen;
      shiftCurrentPresenceBlock(ref, insertionStart, delta);

      insertAt = insertionStart + delta;
    }

    if (run != null) {
      run.observe(match, hiddenMeta);
      ChatTranscriptFilteredRunSupport.updateFilteredRunAttributes(
          filteredRunSupportContext, doc, run, true);
    }

    try {
      comp.addFilteredLine();
    } catch (Exception ignored) {
    }

    int trimmed = enforceTranscriptLineCap(ref, doc);
    if (trimmed > 0) {
      insertAt = Math.max(0, insertAt - trimmed);
    }
    return Math.max(0, insertAt);
  }

  /**
   * Step 5.2: When a history/backfill batch would create too many filtered placeholder/hint runs,
   * collapse the remainder into a single "overflow" summary row.
   */
  private int onFilteredOverflowInsertAt(
      TargetRef ref,
      int insertAt,
      LineMeta hiddenMeta,
      FilterEngine.Match match,
      FilterEngine.Effective eff) {
    if (ref == null) return Math.max(0, insertAt);
    if (match == null || !match.isHide()) return Math.max(0, insertAt);

    ensureTargetExists(ref);
    noteEpochMs(ref, hiddenMeta != null ? hiddenMeta.epochMs() : null);

    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return Math.max(0, insertAt);

    FilteredOverflowRun run = st.historyInsertOverflowRun;
    FilteredOverflowComponent comp = (run != null) ? run.component : null;

    if (comp == null) {
      int beforeLen = doc.getLength();
      int pos = normalizeInsertAtLineStart(doc, insertAt);
      pos = ensureAtLineStartForInsert(doc, pos);
      final int insertionStart = pos;

      comp = new FilteredOverflowComponent();
      try {
        comp.setMaxTagsInTooltip(eff.placeholderTooltipMaxTags());
      } catch (Exception ignored) {
      }
      Font f = safeTranscriptFont();
      if (f != null) comp.setTranscriptFont(f);

      long tsEpochMs =
          (hiddenMeta != null && hiddenMeta.epochMs() != null && hiddenMeta.epochMs() > 0)
              ? hiddenMeta.epochMs()
              : System.currentTimeMillis();
      LineMeta meta =
          ChatTranscriptFilteredRunSupport.buildFilteredOverflowMeta(
              hiddenMeta, tsEpochMs, hiddenMeta != null ? hiddenMeta.tags() : null);

      try {
        SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
        attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
        ChatTranscriptFilteredRunSupport.attachFilterMatch(attrs, match, false);
        StyleConstants.setComponent(attrs, comp);

        doc.insertString(pos, " ", attrs);
        doc.insertString(pos + 1, "\n", withLineMeta(styles.timestamp(), meta));

        run = new FilteredOverflowRun(doc.createPosition(pos), comp);
        st.historyInsertOverflowRun = run;
      } catch (Exception ignored) {
        run = new FilteredOverflowRun(null, comp);
        st.historyInsertOverflowRun = run;
      }

      int delta = doc.getLength() - beforeLen;
      shiftCurrentPresenceBlock(ref, insertionStart, delta);
      insertAt = insertionStart + delta;
    }

    if (run != null) {
      run.observe(match, hiddenMeta);
      ChatTranscriptFilteredRunSupport.updateFilteredOverflowRunAttributes(
          filteredRunSupportContext, doc, run);
    }

    try {
      comp.addFilteredLine();
    } catch (Exception ignored) {
    }

    int trimmed = enforceTranscriptLineCap(ref, doc);
    if (trimmed > 0) {
      insertAt = Math.max(0, insertAt - trimmed);
    }
    return Math.max(0, insertAt);
  }

  private String previewChatLine(String from, String text) {

    String t = (text == null) ? "" : text;
    if (from == null || from.isBlank()) return t;
    return from + ": " + t;
  }

  private String previewActionLine(String from, String action) {
    String f = (from == null) ? "" : from;
    String a = (action == null) ? "" : action;
    return "* " + f + " " + a;
  }

  private String renderTranscriptFrom(TargetRef ref, String from) {
    return ChatTranscriptMatrixDisplayNameSupport.renderTranscriptFrom(
        matrixDisplayNameContext, ref, from);
  }

  /**
   * Re-renders already-inserted Matrix sender labels in this transcript using the latest roster
   * real-name knowledge.
   *
   * <p>This is used after startup roster refreshes so initial persisted scrollback rows can switch
   * from raw Matrix IDs to display names without waiting for new message traffic.
   *
   * @return number of sender-label runs updated
   */
  public synchronized int refreshMatrixDisplayNames(TargetRef ref) {
    return ChatTranscriptMatrixDisplayNameSupport.refreshMatrixDisplayNames(
        matrixDisplayNameContext, ref, "");
  }

  /**
   * Re-renders already-inserted Matrix sender labels for a specific Matrix user ID across all open
   * transcripts on one server.
   *
   * @return number of sender-label runs updated
   */
  public synchronized int refreshMatrixDisplayNameAcrossServer(
      String serverId, String matrixUserId) {
    String sid = Objects.toString(serverId, "").trim();
    String userId = Objects.toString(matrixUserId, "").trim();
    if (sid.isEmpty() || !ChatTranscriptMatrixDisplayNameSupport.looksLikeMatrixUserId(userId)) {
      return 0;
    }

    int updated = 0;
    ArrayList<TargetRef> refs = new ArrayList<>(docs.keySet());
    for (TargetRef ref : refs) {
      if (ref == null) continue;
      if (!Objects.equals(ref.serverId(), sid)) continue;
      updated +=
          ChatTranscriptMatrixDisplayNameSupport.refreshMatrixDisplayNames(
              matrixDisplayNameContext, ref, userId);
    }
    return updated;
  }

  private static <K, V> LinkedHashMap<K, V> createBoundedCache(int maxEntries) {
    return new LinkedHashMap<>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxEntries;
      }
    };
  }

  private String previewForMessageId(TranscriptState st, String messageId) {
    return ChatTranscriptReplyPreviewSupport.previewForMessageId(
        (st == null) ? null : st.messagePreviewByMsgId, messageId);
  }

  private Font safeTranscriptFont() {
    try {
      if (uiSettings != null && uiSettings.get() != null) {
        UiSettings us = uiSettings.get();
        Font preferred = new Font(us.chatFontFamily(), Font.PLAIN, us.chatFontSize());
        return EmojiFontSupport.resolveTranscriptComponentFont(preferred);
      }
    } catch (Exception ignored) {
    }
    return null;
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
    endFilteredRun(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return null;

    // This is a synthetic UI row; still attach line metadata so the inspector and
    // filtering UI remain consistent and reliable.
    LineMeta meta =
        buildLineMeta(
            ref, LogKind.STATUS, LogDirection.SYSTEM, null, System.currentTimeMillis(), null);

    TranscriptState st = stateByTarget.get(ref);
    if (st != null && st.loadOlderControl != null) {
      return st.loadOlderControl.component;
    }

    int beforeLen = doc.getLength();
    int insertPos = 0;

    LoadOlderMessagesComponent comp = new LoadOlderMessagesComponent();
    try {
      if (uiSettings != null && uiSettings.get() != null) {
        comp.setTranscriptFont(
            new Font(
                uiSettings.get().chatFontFamily(), Font.PLAIN, uiSettings.get().chatFontSize()));
      }
    } catch (Exception ignored) {
    }

    try {
      SimpleAttributeSet attrs =
          withAuxiliaryRowKind(withLineMeta(styles.status(), meta), AUX_ROW_KIND_LOAD_OLDER);
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
      StyleConstants.setComponent(attrs, comp);
      doc.insertString(insertPos, " ", attrs);
      Position pos = doc.createPosition(insertPos);
      doc.insertString(
          insertPos + 1,
          "\n",
          withAuxiliaryRowKind(withLineMeta(styles.timestamp(), meta), AUX_ROW_KIND_LOAD_OLDER));
      if (st != null) {
        st.loadOlderControl = new LoadOlderControl(pos, comp);
      }
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertPos, delta);
    return comp;
  }

  public synchronized HistoryDividerComponent ensureHistoryDivider(
      TargetRef ref, int insertAt, String labelText) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return null;

    // Synthetic UI row; keep metadata consistent so inspector behavior is stable.
    LineMeta meta =
        buildLineMeta(
            ref, LogKind.STATUS, LogDirection.SYSTEM, null, System.currentTimeMillis(), null);

    TranscriptState st = stateByTarget.get(ref);
    if (st != null && st.historyDivider != null) {
      try {
        st.historyDivider.component.setText(labelText);
      } catch (Exception ignored) {
      }
      // If we already have a divider, it's no longer pending.
      st.pendingHistoryDividerLabel = null;
      return st.historyDivider.component;
    }

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, insertAt);
    pos = ensureAtLineStartForInsert(doc, pos);
    final int insertionStart = pos;

    HistoryDividerComponent comp = createTranscriptDividerComponent(labelText);

    try {
      SimpleAttributeSet attrs =
          withAuxiliaryRowKind(withLineMeta(styles.status(), meta), AUX_ROW_KIND_HISTORY_DIVIDER);
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
      StyleConstants.setComponent(attrs, comp);
      doc.insertString(pos, " ", attrs);
      doc.insertString(
          pos + 1,
          "\n",
          withAuxiliaryRowKind(
              withLineMeta(styles.timestamp(), meta), AUX_ROW_KIND_HISTORY_DIVIDER));
      if (st != null) {
        st.historyDivider = new HistoryDividerControl(comp);
        st.pendingHistoryDividerLabel = null;
      }
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
    return comp;
  }

  /**
   * Mark that a history divider should be inserted before the next live append for this target.
   * This is used when history is loaded into an otherwise-empty transcript.
   */
  public synchronized void markHistoryDividerPending(TargetRef ref, String labelText) {
    if (ref == null) return;
    ensureTargetExists(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (st == null) return;
    if (st.historyDivider != null) return;
    st.pendingHistoryDividerLabel = labelText;
  }

  /** Returns true if there is content after the given offset in the transcript document. */
  public synchronized boolean hasContentAfterOffset(TargetRef ref, int offset) {
    if (ref == null) return false;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return false;
    return doc.getLength() > Math.max(0, offset);
  }

  private void flushPendingHistoryDividerIfNeeded(TargetRef ref, StyledDocument doc) {
    if (ref == null || doc == null) return;
    TranscriptState st = stateByTarget.get(ref);
    if (st == null) return;
    if (st.historyDivider != null) {
      st.pendingHistoryDividerLabel = null;
      return;
    }

    String label = st.pendingHistoryDividerLabel;
    if (label == null || label.isBlank()) return;

    // Insert at the end of the current transcript (right before the live append that triggered
    // this flush).
    ensureHistoryDivider(ref, doc.getLength(), label);
    st.pendingHistoryDividerLabel = null;
  }

  private HistoryDividerComponent createTranscriptDividerComponent(String text) {
    HistoryDividerComponent comp = new HistoryDividerComponent(text);
    Font f = safeTranscriptFont();
    if (f != null) {
      try {
        comp.setTranscriptFont(f);
      } catch (Exception ignored) {
      }
    }
    return comp;
  }

  public synchronized void updateReadMarker(TargetRef ref, long markerEpochMs) {
    if (ref == null) return;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return;

    long markerMs = markerEpochMs > 0 ? markerEpochMs : System.currentTimeMillis();
    st.readMarkerEpochMs = markerMs;

    removeReadMarkerControl(ref, doc, st);
    tryInsertReadMarkerControl(ref, doc, st, markerMs);
  }

  public synchronized void clearReadMarker(TargetRef ref) {
    if (ref == null) return;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return;
    removeReadMarkerControl(ref, doc, st);
    st.readMarkerEpochMs = null;
  }

  public synchronized void clearReadMarkersForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    ArrayList<TargetRef> targets = new ArrayList<>(stateByTarget.keySet());
    for (TargetRef ref : targets) {
      if (ref == null || !sid.equals(Objects.toString(ref.serverId(), "").trim())) continue;
      StyledDocument doc = docs.get(ref);
      TranscriptState st = stateByTarget.get(ref);
      if (doc == null || st == null) continue;
      removeReadMarkerControl(ref, doc, st);
      st.readMarkerEpochMs = null;
    }
  }

  public synchronized int readMarkerJumpOffset(TargetRef ref) {
    if (ref == null) return -1;
    TranscriptState st = stateByTarget.get(ref);
    if (st == null || st.readMarker == null) return -1;
    StyledDocument doc = docs.get(ref);
    if (doc == null) return -1;
    int base = st.readMarker.pos.getOffset();
    int off = base + 2;
    return Math.max(0, Math.min(off, doc.getLength()));
  }

  public synchronized int messageOffsetById(TargetRef ref, String messageId) {
    if (ref == null) return -1;
    String msgId = normalizeMessageId(messageId);
    if (msgId.isEmpty()) return -1;
    StyledDocument doc = docs.get(ref);
    if (doc == null) return -1;
    return ChatTranscriptDocumentSupport.findLineStartByMessageId(doc, msgId);
  }

  public synchronized String messagePreviewById(TargetRef ref, String messageId) {
    if (ref == null) return "";
    String msgId = normalizeMessageId(messageId);
    if (msgId.isEmpty()) return "";
    TranscriptState st = stateByTarget.get(ref);
    if (st == null) return "";
    return previewForMessageId(st, msgId);
  }

  public synchronized RedactedMessageContent redactedOriginalById(TargetRef ref, String messageId) {
    if (ref == null) return null;
    String msgId = normalizeMessageId(messageId);
    if (msgId.isEmpty()) return null;
    TranscriptState st = stateByTarget.get(ref);
    if (st == null) return null;
    return st.redactedOriginalByMsgId.get(msgId);
  }

  public synchronized boolean hasReactionFromNick(
      TargetRef ref, String messageId, String reaction, String nick) {
    if (ref == null) return false;
    String msgId = normalizeMessageId(messageId);
    String token = Objects.toString(reaction, "").trim();
    String normalizedNick = ChatTranscriptReactionStateSupport.normalizeReactionNickKey(nick);
    if (msgId.isEmpty() || token.isEmpty() || normalizedNick.isEmpty()) return false;
    TranscriptState st = stateByTarget.get(ref);
    if (st == null) return false;
    ReactionState state = st.reactionsByTargetMsgId.get(msgId);
    if (state == null) return false;
    return state.hasReactionFromNick(token, normalizedNick);
  }

  public synchronized void setReactionChipActionHandler(ReactionChipActionHandler handler) {
    reactionChipActionHandler =
        (handler != null) ? handler : (target, messageId, reactionToken, unreactRequested) -> {};
    for (Map.Entry<TargetRef, TranscriptState> entry : stateByTarget.entrySet()) {
      TargetRef ref = entry.getKey();
      TranscriptState st = entry.getValue();
      if (ref == null || st == null) continue;
      for (Map.Entry<String, ReactionState> reactionEntry : st.reactionsByTargetMsgId.entrySet()) {
        String msgId = normalizeMessageId(reactionEntry.getKey());
        ReactionState state = reactionEntry.getValue();
        if (msgId.isEmpty()
            || state == null
            || state.control == null
            || state.control.component == null) continue;
        configureReactionControlCallbacks(state.control.component, ref, msgId);
      }
    }
  }

  public synchronized boolean isOwnMessage(TargetRef ref, String messageId) {
    if (ref == null) return false;
    String msgId = normalizeMessageId(messageId);
    if (msgId.isEmpty()) return false;
    StyledDocument doc = docs.get(ref);
    if (doc == null) return false;

    int lineStart = ChatTranscriptDocumentSupport.findLineStartByMessageId(doc, msgId);
    if (lineStart < 0) return false;

    try {
      int len = doc.getLength();
      if (len <= 0) return false;
      int safePos = Math.max(0, Math.min(lineStart, len - 1));
      AttributeSet attrs = doc.getCharacterElement(safePos).getAttributes();
      if (attrs == null) return false;
      if (Boolean.TRUE.equals(attrs.getAttribute(ChatStyles.ATTR_OUTGOING))) return true;
      return ChatTranscriptAttrSupport.logDirectionFromAttrs(attrs) == LogDirection.OUT;
    } catch (Exception ignored) {
      return false;
    }
  }

  public synchronized void applyMessageReaction(
      TargetRef ref, String targetMessageId, String reaction, String fromNick, long tsEpochMs) {
    if (ref == null) return;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return;
    applyMessageReactionInternal(ref, doc, st, targetMessageId, reaction, fromNick, tsEpochMs);
  }

  public synchronized void removeMessageReaction(
      TargetRef ref, String targetMessageId, String reaction, String fromNick, long tsEpochMs) {
    if (ref == null) return;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return;
    removeMessageReactionInternal(ref, doc, st, targetMessageId, reaction, fromNick, tsEpochMs);
  }

  public synchronized boolean applyMessageEdit(
      TargetRef ref,
      String targetMessageId,
      String editedText,
      String fromNick,
      long tsEpochMs,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    if (ref == null) return false;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null) return false;

    String targetMsgId = normalizeMessageId(targetMessageId);
    if (targetMsgId.isEmpty()) return false;
    int lineStart = ChatTranscriptDocumentSupport.findLineStartByMessageId(doc, targetMsgId);
    if (lineStart < 0) return false;

    AttributeSet attrs;
    try {
      attrs =
          doc.getCharacterElement(
                  Math.max(0, Math.min(lineStart, Math.max(0, doc.getLength() - 1))))
              .getAttributes();
    } catch (Exception ignored) {
      return false;
    }

    String renderedEditedText = renderEditedText(editedText);
    boolean replaced =
        replaceMessageLine(
            ref,
            doc,
            lineStart,
            attrs,
            renderedEditedText,
            tsEpochMs,
            replacementMessageId,
            replacementIrcv3Tags);
    if (replaced) {
      ChatTranscriptMessageStateSupport.rememberEditedCurrentMessageContent(
          st == null ? null : st.currentMessageContentByMsgId,
          targetMsgId,
          attrs,
          renderedEditedText);
    }
    return replaced;
  }

  public synchronized boolean applyMessageRedaction(
      TargetRef ref,
      String targetMessageId,
      String fromNick,
      long tsEpochMs,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    if (ref == null) return false;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null) return false;

    String targetMsgId = normalizeMessageId(targetMessageId);
    if (targetMsgId.isEmpty()) return false;
    int lineStart = ChatTranscriptDocumentSupport.findLineStartByMessageId(doc, targetMsgId);
    if (lineStart < 0) return false;

    AttributeSet attrs;
    try {
      attrs =
          doc.getCharacterElement(
                  Math.max(0, Math.min(lineStart, Math.max(0, doc.getLength() - 1))))
              .getAttributes();
    } catch (Exception ignored) {
      return false;
    }

    ChatTranscriptMessageStateSupport.rememberRedactedOriginal(
        messageStateSupportContext,
        st == null ? null : st.currentMessageContentByMsgId,
        st == null ? null : st.redactedOriginalByMsgId,
        targetMsgId,
        attrs,
        fromNick,
        tsEpochMs);

    boolean replaced =
        replaceMessageLine(
            ref,
            doc,
            lineStart,
            attrs,
            REDACTED_MESSAGE_PLACEHOLDER,
            tsEpochMs,
            replacementMessageId,
            replacementIrcv3Tags);
    if (replaced && st != null) {
      markLineRangeRedacted(doc, lineStart);
      st.currentMessageContentByMsgId.remove(targetMsgId);
      clearReactionStateForMessage(ref, doc, st, targetMsgId);
    }
    return replaced;
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

  public synchronized void setLoadOlderMessagesControlState(
      TargetRef ref, LoadOlderMessagesComponent.State s) {
    if (ref == null) return;
    TranscriptState st = stateByTarget.get(ref);
    if (st == null || st.loadOlderControl == null) return;
    try {
      st.loadOlderControl.component.setState(s);
    } catch (Exception ignored) {
    }
  }

  public synchronized void setLoadOlderMessagesControlHandler(
      TargetRef ref, java.util.function.BooleanSupplier onLoad) {
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
      ChatRichTextRenderer.insertStyledTextAt(doc, text, styles.message(), doc.getLength());
      enforceTranscriptLineCap(ref, doc);
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

    long eventEpochMs = System.currentTimeMillis();
    String presenceFrom = null;
    try {
      presenceFrom = (event.kind() == PresenceKind.NICK) ? event.oldNick() : event.nick();
    } catch (Exception ignored) {
      presenceFrom = event.nick();
    }
    LineMeta meta =
        buildLineMeta(
            ref, LogKind.PRESENCE, LogDirection.SYSTEM, presenceFrom, eventEpochMs, event);

    FilterEngine.Match m =
        firstFilterMatch(
            ref,
            LogKind.PRESENCE,
            LogDirection.SYSTEM,
            presenceFrom,
            event.displayText(),
            meta.tags());
    if (m != null && m.isHide()) {
      onFilteredLineAppend(ref, event.displayText(), meta, m);
      return;
    }

    endFilteredRun(ref);

    ensureTargetExists(ref);

    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return;
    boolean includePresenceTimestamps = shouldIncludePresenceTimestamps();
    String presenceTimestampPrefix = "";
    if (includePresenceTimestamps && ts != null && ts.enabled()) {
      try {
        presenceTimestampPrefix = ts.prefixAt(eventEpochMs);
      } catch (Exception ignored) {
        presenceTimestampPrefix = "";
      }
    }
    PresenceFoldComponent.Entry foldEntry =
        new PresenceFoldComponent.Entry(presenceTimestampPrefix, event);

    boolean foldsEnabled = true;
    try {
      foldsEnabled =
          uiSettings == null || uiSettings.get() == null || uiSettings.get().presenceFoldsEnabled();
    } catch (Exception ignored) {
      foldsEnabled = true;
    }

    if (!foldsEnabled) {
      st.currentPresenceBlock = null;
      ensureAtLineStart(doc);
      try {
        AttributeSet tsStyle = withFilterMatch(withLineMeta(styles.timestamp(), meta), m);
        if (!presenceTimestampPrefix.isBlank()) {
          doc.insertString(doc.getLength(), presenceTimestampPrefix, tsStyle);
        }
        AttributeSet base = withFilterMatch(withLineMeta(styles.presence(), meta), m);
        renderer.insertRichText(doc, ref, event.displayText(), base);
        doc.insertString(doc.getLength(), "\n", tsStyle);
        enforceTranscriptLineCap(ref, doc);
      } catch (Exception ignored2) {
      }
      return;
    }
    if (st.currentPresenceBlock != null
        && st.currentPresenceBlock.folded
        && st.currentPresenceBlock.component != null) {
      st.currentPresenceBlock.entries.add(foldEntry);
      st.currentPresenceBlock.component.addEntry(foldEntry);
      return;
    }
    ensureAtLineStart(doc);
    int startOffset = doc.getLength();

    try {
      AttributeSet tsStyle = withFilterMatch(withLineMeta(styles.timestamp(), meta), m);
      if (!presenceTimestampPrefix.isBlank()) {
        doc.insertString(doc.getLength(), presenceTimestampPrefix, tsStyle);
      }

      AttributeSet base = withFilterMatch(withLineMeta(styles.presence(), meta), m);
      renderer.insertRichText(doc, ref, event.displayText(), base);
      doc.insertString(doc.getLength(), "\n", tsStyle);
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

    block.entries.add(foldEntry);
    if (!block.folded && block.entries.size() == 2) {
      foldBlock(doc, block);
    }
    enforceTranscriptLineCap(ref, doc);
  }

  public synchronized void appendLine(
      TargetRef ref, String from, String text, AttributeSet fromStyle, AttributeSet msgStyle) {
    appendLineInternal(ref, from, text, fromStyle, msgStyle, true, null);
  }

  private synchronized void appendLineInternal(
      TargetRef ref,
      String from,
      String text,
      AttributeSet fromStyle,
      AttributeSet msgStyle,
      boolean allowEmbeds,
      LineMeta meta) {
    appendLineInternal(ref, from, text, fromStyle, msgStyle, allowEmbeds, meta, null, null);
  }

  /**
   * Like {@link #appendLineInternal(TargetRef, String, String, AttributeSet, AttributeSet, boolean,
   * LineMeta)} but optionally inserts an inline Swing component at the end of the line (before the
   * newline).
   */
  private synchronized void appendLineInternal(
      TargetRef ref,
      String from,
      String text,
      AttributeSet fromStyle,
      AttributeSet msgStyle,
      boolean allowEmbeds,
      LineMeta meta,
      java.awt.Component tailComponent,
      AttributeSet tailAttrs) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);

    // If history was loaded into an otherwise-empty transcript (e.g. transcript rebuild), we defer
    // inserting the history divider until the next live append so it doesn't appear as a dangling
    // row at the bottom.
    if (allowEmbeds) {
      flushPendingHistoryDividerIfNeeded(ref, doc);
    }

    noteEpochMs(ref, (meta != null) ? meta.epochMs() : null);
    ensureAtLineStart(doc);

    FilterEngine.Match match = null;
    if (meta != null) {
      String filterFrom = Objects.toString(meta.fromNick(), "").isBlank() ? from : meta.fromNick();
      match = firstFilterMatch(ref, meta.kind(), meta.direction(), filterFrom, text, meta.tags());
      if (match != null && match.isHide()) {
        onFilteredLineAppend(ref, previewChatLine(from, text), meta, match);
        return;
      }
    }

    Long epochMs = (meta != null) ? meta.epochMs() : null;
    String renderedFrom = renderTranscriptFrom(ref, from);
    SimpleAttributeSet tsStyle = withLineMeta(styles.timestamp(), meta);
    SimpleAttributeSet fromStyle2 =
        withLineMeta(fromStyle != null ? fromStyle : styles.from(), meta);
    SimpleAttributeSet msgStyle2 =
        withLineMeta(msgStyle != null ? msgStyle : styles.message(), meta);

    if (match != null) {
      tsStyle = withFilterMatch(tsStyle, match);
      fromStyle2 = withFilterMatch(fromStyle2, match);
      msgStyle2 = withFilterMatch(msgStyle2, match);
    }

    try {
      AttributeSet baseForId = msgStyle2;
      Object styleIdObj = baseForId.getAttribute(ChatStyles.ATTR_STYLE);
      String styleId = styleIdObj != null ? String.valueOf(styleIdObj) : null;
      boolean timestampsIncludeChatMessages = timestampsIncludeChatMessages();
      boolean timestampsIncludePresenceMessages = shouldIncludePresenceTimestamps();

      if (ts != null
          && ts.enabled()
          && (ChatStyles.STYLE_STATUS.equals(styleId)
              || ChatStyles.STYLE_ERROR.equals(styleId)
              || ChatStyles.STYLE_NOTICE_MESSAGE.equals(styleId)
              || (timestampsIncludePresenceMessages && ChatStyles.STYLE_PRESENCE.equals(styleId))
              || (timestampsIncludeChatMessages && ChatStyles.STYLE_MESSAGE.equals(styleId)))) {
        String prefix = (epochMs != null) ? ts.prefixAt(epochMs) : ts.prefixNow();
        doc.insertString(doc.getLength(), prefix, tsStyle);
      }

      if (renderedFrom != null && !renderedFrom.isBlank()) {
        doc.insertString(doc.getLength(), renderedFrom + ": ", fromStyle2);
      }

      AttributeSet base = msgStyle2;
      if (renderer != null && !shouldDeferRichTextDuringHistoryBatch(ref)) {
        renderer.insertRichText(doc, ref, text, base);
      } else {
        ChatRichTextRenderer.insertStyledTextAt(
            doc, text == null ? "" : text, base, doc.getLength());
      }

      if (tailComponent != null) {
        SimpleAttributeSet a = new SimpleAttributeSet(tailAttrs != null ? tailAttrs : msgStyle2);
        a = withLineMeta(a, meta);
        if (match != null) {
          a = withFilterMatch(a, match);
        }
        StyleConstants.setComponent(a, tailComponent);
        doc.insertString(doc.getLength(), " ", a);
      }

      int lineEndOffset = doc.getLength();
      doc.insertString(doc.getLength(), "\n", tsStyle);
      TranscriptState st = stateByTarget.get(ref);
      ChatTranscriptMessageStateSupport.rememberMessagePreview(
          messageStateSupportContext,
          st == null ? null : st.messagePreviewByMsgId,
          meta,
          renderedFrom,
          text);
      ChatTranscriptMessageStateSupport.rememberCurrentMessageContent(
          st == null ? null : st.currentMessageContentByMsgId, meta, renderedFrom, text);

      if (!allowEmbeds) {
        enforceTranscriptLineCap(ref, doc);
        maybeRenderPendingReadMarker(ref, epochMs);
        return;
      }
      String embedFrom = meta != null ? meta.fromNick() : from;
      Map<String, String> embedTags = meta != null ? meta.ircv3TagsMap() : Map.of();
      LinkedHashSet<String> blockedManualPreviewUrls = new LinkedHashSet<>();
      if (imageEmbeds != null && uiSettings != null && uiSettings.get().imageEmbedsEnabled()) {
        ChatImageEmbedder.AppendResult imageResult =
            imageEmbeds.appendEmbeds(ref, doc, text, embedFrom, embedTags);
        if (imageResult != null && imageResult.blockedUrls() != null) {
          blockedManualPreviewUrls.addAll(imageResult.blockedUrls());
        }
      }
      if (linkPreviews != null && uiSettings != null && uiSettings.get().linkPreviewsEnabled()) {
        ChatLinkPreviewEmbedder.AppendResult linkResult =
            linkPreviews.appendPreviews(ref, doc, text, embedFrom, embedTags);
        if (linkResult != null && linkResult.blockedUrls() != null) {
          blockedManualPreviewUrls.addAll(linkResult.blockedUrls());
        }
      }
      if (!blockedManualPreviewUrls.isEmpty()) {
        insertManualPreviewMarkers(
            doc, lineEndOffset, meta, match, List.copyOf(blockedManualPreviewUrls));
      }
      enforceTranscriptLineCap(ref, doc);
      maybeRenderPendingReadMarker(ref, epochMs);
    } catch (Exception ignored) {
    }
  }

  private void insertManualPreviewMarkers(
      StyledDocument doc,
      int lineEndOffset,
      LineMeta meta,
      FilterEngine.Match match,
      Collection<String> blockedUrls) {
    if (doc == null || blockedUrls == null || blockedUrls.isEmpty()) return;

    LinkedHashSet<String> deduped = new LinkedHashSet<>();
    for (String blockedUrl : blockedUrls) {
      String normalized = normalizeManualPreviewUrl(blockedUrl);
      if (!normalized.isEmpty()) {
        deduped.add(normalized);
      }
    }
    if (deduped.isEmpty()) return;

    int pos = Math.max(0, Math.min(lineEndOffset, doc.getLength()));
    for (String blockedUrl : deduped) {
      try {
        SimpleAttributeSet attrs = withLineMeta(styles.link(), meta);
        attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_LINK);
        attrs.addAttribute(ChatStyles.ATTR_MANUAL_PREVIEW_URL, blockedUrl);
        if (match != null) {
          attrs = withFilterMatch(attrs, match);
        }
        doc.insertString(pos, MANUAL_PREVIEW_MARKER, attrs);
        pos += MANUAL_PREVIEW_MARKER.length();
      } catch (Exception ignored) {
      }
    }
  }

  public synchronized boolean insertManualPreviewAt(TargetRef ref, int insertAt, String rawUrl) {
    if (ref == null || ref.isUiOnly()) return false;
    String url = normalizeManualPreviewUrl(rawUrl);
    if (url.isEmpty()) return false;

    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return false;

    int pos = Math.max(0, Math.min(insertAt, doc.getLength()));
    int beforeLen = doc.getLength();
    boolean inserted = false;
    if (imageEmbeds != null) {
      inserted = imageEmbeds.insertEmbedForUrlAt(ref, doc, url, pos);
    }
    if (!inserted && linkPreviews != null) {
      inserted = linkPreviews.insertPreviewForUrlAt(ref, doc, url, pos);
    }
    if (!inserted) return false;

    int delta = doc.getLength() - beforeLen;
    if (delta != 0) {
      shiftCurrentPresenceBlock(ref, pos, delta);
    }
    enforceTranscriptLineCap(ref, doc);
    return true;
  }

  private static String normalizeManualPreviewUrl(String rawUrl) {
    return Objects.toString(rawUrl, "").trim();
  }

  public void appendChat(TargetRef ref, String from, String text) {
    appendChat(ref, from, text, false);
  }

  public void appendChat(TargetRef ref, String from, String text, boolean outgoingLocalEcho) {
    long tsEpochMs = System.currentTimeMillis();
    LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
    LineMeta meta = buildLineMeta(ref, LogKind.CHAT, dir, from, tsEpochMs, null);
    FilterEngine.Match m = hideMatch(ref, LogKind.CHAT, dir, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }

    breakPresenceRun(ref);

    ChatTranscriptSenderStyleSupport.PreparedStyles preparedStyles =
        ChatTranscriptSenderStyleSupport.prepare(
            senderStyleSupportContext, meta, from, outgoingLocalEcho, null);
    SimpleAttributeSet fs = preparedStyles.fromStyle();
    SimpleAttributeSet ms = preparedStyles.messageStyle();

    appendLineInternal(ref, from, text, fs, ms, true, meta);
  }

  public void appendChatFromHistory(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs) {
    appendChatFromHistory(ref, from, text, outgoingLocalEcho, tsEpochMs, "", Map.of());
  }

  public void appendChatFromHistory(
      TargetRef ref,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    if (ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(docs.get(ref), messageId)) {
      return;
    }

    LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
    LineMeta meta =
        buildLineMeta(ref, LogKind.CHAT, dir, from, tsEpochMs, null, messageId, ircv3Tags);
    FilterEngine.Match m = hideMatch(ref, LogKind.CHAT, dir, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }

    breakPresenceRun(ref);

    ChatTranscriptSenderStyleSupport.PreparedStyles preparedStyles =
        ChatTranscriptSenderStyleSupport.prepare(
            senderStyleSupportContext, meta, from, outgoingLocalEcho, null);
    SimpleAttributeSet fs = preparedStyles.fromStyle();
    SimpleAttributeSet ms = preparedStyles.messageStyle();

    appendLineInternal(ref, from, text, fs, ms, false, meta);
  }

  /**
   * Append a chat message with a timestamp, allowing embeds (link previews / images).
   *
   * <p>This is used for inbound "live" messages where we have an Instant from the server. We keep
   * the history-loading paths (DB backfill / "load older") embed-free to avoid fetch storms.
   */
  public void appendChatAt(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs) {
    appendChatAt(ref, from, text, outgoingLocalEcho, tsEpochMs, "", Map.of(), null);
  }

  public void appendChatAt(
      TargetRef ref,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendChatAt(ref, from, text, outgoingLocalEcho, tsEpochMs, messageId, ircv3Tags, null);
  }

  public void appendChatAt(
      TargetRef ref,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    if (ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(docs.get(ref), messageId)) {
      return;
    }

    LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
    LineMeta meta =
        buildLineMeta(ref, LogKind.CHAT, dir, from, tsEpochMs, null, messageId, ircv3Tags);
    FilterEngine.Match m = hideMatch(ref, LogKind.CHAT, dir, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }

    breakPresenceRun(ref);

    ChatTranscriptSenderStyleSupport.PreparedStyles preparedStyles =
        ChatTranscriptSenderStyleSupport.prepare(
            senderStyleSupportContext,
            meta,
            from,
            outgoingLocalEcho,
            notificationRuleHighlightColor);
    SimpleAttributeSet fs = preparedStyles.fromStyle();
    SimpleAttributeSet ms = preparedStyles.messageStyle();

    ChatTranscriptOutgoingFollowUpSupport.Plan followUp =
        ChatTranscriptOutgoingFollowUpSupport.plan(messageId, ircv3Tags);
    followUp.runReplyContext(
        replyToMsgId -> appendReplyContextLine(ref, from, replyToMsgId, tsEpochMs));

    appendLineInternal(ref, from, text, fs, ms, true, meta);

    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc != null && st != null) {
      followUp.runPendingMaterialization(
          () ->
              materializePendingReactionsForMessage(
                  ref, doc, st, followUp.normalizedMessageId(), tsEpochMs));
      followUp.runReplyReaction(
          () ->
              applyMessageReactionInternal(
                  ref,
                  doc,
                  st,
                  followUp.replyToMessageId(),
                  followUp.reactionToken(),
                  from,
                  tsEpochMs));
    }
  }

  public synchronized void appendPendingOutgoingChat(
      TargetRef ref, String pendingId, String from, String text, long tsEpochMs) {
    if (ref == null) return;
    String pid = normalizePendingId(pendingId);
    if (pid.isEmpty()) return;

    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    breakPresenceRun(ref);

    LineMeta meta = buildLineMeta(ref, LogKind.CHAT, LogDirection.OUT, from, tsEpochMs, null);
    ChatTranscriptSenderStyleSupport.PreparedStyles preparedStyles =
        ChatTranscriptSenderStyleSupport.prepare(senderStyleSupportContext, meta, from, true, null);
    SimpleAttributeSet fs = preparedStyles.fromStyle();
    SimpleAttributeSet ms = preparedStyles.messageStyle();
    ChatTranscriptPendingOutgoingSupport.markPending(fs, pid);
    ChatTranscriptPendingOutgoingSupport.markPending(ms, pid);

    if (!outgoingDeliveryIndicatorsEnabled()) {
      appendLineInternal(ref, from, Objects.toString(text, ""), fs, ms, true, meta);
      return;
    }

    // Replace the old textual "[pending]" suffix with an inline spinner indicator.
    Color spinnerColor = ChatTranscriptPendingOutgoingSupport.pendingSpinnerColor(ms);
    OutgoingSendIndicator.PendingSpinner spinner =
        new OutgoingSendIndicator.PendingSpinner(spinnerColor);
    SimpleAttributeSet tail = ChatTranscriptPendingOutgoingSupport.pendingTailAttrs(ms, pid);

    appendLineInternal(ref, from, Objects.toString(text, ""), fs, ms, true, meta, spinner, tail);
  }

  public synchronized boolean resolvePendingOutgoingChat(
      TargetRef ref,
      String pendingId,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    if (ref == null) return false;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    ChatTranscriptPendingReplacementSupport.ReplacementPlan replacement =
        ChatTranscriptPendingReplacementSupport.prepareReplacement(
            doc, pendingId, tsEpochMs, System::currentTimeMillis);
    if (replacement == null) return false;

    insertCanonicalOutgoingChatLineAt(
        ref,
        replacement.lineStart(),
        from,
        text,
        replacement.effectiveEpochMs(),
        messageId,
        ircv3Tags);
    return true;
  }

  public synchronized boolean failPendingOutgoingChat(
      TargetRef ref, String pendingId, String from, String text, long tsEpochMs, String reason) {
    if (ref == null) return false;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    ChatTranscriptPendingReplacementSupport.ReplacementPlan replacement =
        ChatTranscriptPendingReplacementSupport.prepareReplacement(
            doc, pendingId, tsEpochMs, System::currentTimeMillis);
    if (replacement == null) return false;

    insertFailedOutgoingChatLineAt(
        ref, replacement.lineStart(), from, text, replacement.effectiveEpochMs(), reason);
    return true;
  }

  public synchronized int insertChatFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs) {
    return insertChatFromHistoryAt(
        ref, insertAt, from, text, outgoingLocalEcho, tsEpochMs, "", Map.of());
  }

  public synchronized int insertChatFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);
    if (ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(doc, messageId)) {
      return Math.max(0, insertAt);
    }

    LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
    LineMeta meta =
        buildLineMeta(ref, LogKind.CHAT, dir, from, tsEpochMs, null, messageId, ircv3Tags);
    FilterEngine.Match m = hideMatch(ref, LogKind.CHAT, dir, from, text, meta.tags());
    if (m != null) {
      FilterEngine.Effective eff = filterEngine.effectiveFor(ref);
      if (eff.historyPlaceholdersEnabled()) {
        return onFilteredLineInsertAt(ref, insertAt, previewChatLine(from, text), meta, m);
      }
      // History placeholders disabled: silently drop filtered history lines.
      endFilteredInsertRun(ref);
      return Math.max(0, insertAt);
    }

    ChatTranscriptSenderStyleSupport.PreparedStyles preparedStyles =
        ChatTranscriptSenderStyleSupport.prepare(
            senderStyleSupportContext, meta, from, outgoingLocalEcho, null);
    SimpleAttributeSet fs = preparedStyles.fromStyle();
    SimpleAttributeSet ms = preparedStyles.messageStyle();

    return insertLineInternalAt(ref, insertAt, from, text, fs, ms, false, meta);
  }

  public synchronized int prependChatFromHistory(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs) {
    return insertChatFromHistoryAt(ref, 0, from, text, outgoingLocalEcho, tsEpochMs);
  }

  public synchronized int insertActionFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs) {
    return insertActionFromHistoryAt(
        ref, insertAt, from, action, outgoingLocalEcho, tsEpochMs, "", Map.of());
  }

  public synchronized int insertActionFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return Math.max(0, insertAt);
    if (ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(doc, messageId)) {
      return Math.max(0, insertAt);
    }

    LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
    LineMeta meta =
        buildLineMeta(ref, LogKind.ACTION, dir, from, tsEpochMs, null, messageId, ircv3Tags);
    FilterEngine.Match m = firstFilterMatch(ref, LogKind.ACTION, dir, from, action, meta.tags());
    if (m != null && m.isHide()) {
      FilterEngine.Effective eff = filterEngine.effectiveFor(ref);
      if (eff.historyPlaceholdersEnabled()) {
        return onFilteredLineInsertAt(ref, insertAt, previewActionLine(from, action), meta, m);
      }
      // History placeholders disabled: silently drop filtered history lines.
      endFilteredInsertRun(ref);
      return Math.max(0, insertAt);
    }

    // This is a visible history insert (it creates real document content), so break any active
    // insert-run placeholders created by prior hidden lines.
    endFilteredInsertRun(ref);

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, insertAt);
    pos = ensureAtLineStartForInsert(doc, pos);
    final int insertionStart = pos;

    String a = action == null ? "" : action;

    try {
      boolean timestampsIncludeChatMessages = timestampsIncludeChatMessages();

      SimpleAttributeSet tsStyle = withLineMeta(styles.timestamp(), meta);
      if (m != null) {
        tsStyle = withFilterMatch(tsStyle, m);
      }

      if (ts != null && ts.enabled() && timestampsIncludeChatMessages) {
        String prefix = ts.prefixAt(tsEpochMs);
        doc.insertString(pos, prefix, tsStyle);
        pos += prefix.length();
      }

      AttributeSet msgStyle = styles.actionMessage();
      AttributeSet fromStyle = styles.actionFrom();

      if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
        fromStyle = nickColors.forNick(from, fromStyle);
      }

      SimpleAttributeSet ms = withLineMeta(msgStyle, meta);
      SimpleAttributeSet fs = withLineMeta(fromStyle, meta);
      applyOutgoingLineColor(fs, ms, outgoingLocalEcho);
      if (m != null) {
        fs = withFilterMatch(fs, m);
        ms = withFilterMatch(ms, m);
      }
      String renderedFrom = renderTranscriptFrom(ref, from);

      doc.insertString(pos, "* ", ms);
      pos += 2;
      if (renderedFrom != null && !renderedFrom.isBlank()) {
        doc.insertString(pos, renderedFrom, fs);
        pos += renderedFrom.length();
        doc.insertString(pos, " ", ms);
        pos += 1;
      }

      if (renderer != null && !shouldDeferRichTextDuringHistoryBatch(ref)) {
        pos = renderer.insertRichTextAt(doc, ref, a, ms, pos);
      } else {
        pos = ChatRichTextRenderer.insertStyledTextAt(doc, a, ms, pos);
      }

      doc.insertString(pos, "\n", tsStyle);
      pos += 1;
      TranscriptState st = stateByTarget.get(ref);
      ChatTranscriptMessageStateSupport.rememberMessagePreview(
          messageStateSupportContext,
          st == null ? null : st.messagePreviewByMsgId,
          meta,
          renderedFrom,
          a);
      ChatTranscriptMessageStateSupport.rememberCurrentMessageContent(
          st == null ? null : st.currentMessageContentByMsgId, meta, renderedFrom, a);
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
    int trimmed = enforceTranscriptLineCap(ref, doc);
    if (trimmed > 0) {
      pos = Math.max(0, pos - trimmed);
    }
    maybeRenderPendingReadMarker(ref, tsEpochMs);
    return pos;
  }

  public synchronized int prependActionFromHistory(
      TargetRef ref, String from, String action, boolean outgoingLocalEcho, long tsEpochMs) {
    return insertActionFromHistoryAt(ref, 0, from, action, outgoingLocalEcho, tsEpochMs);
  }

  public synchronized int insertNoticeFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    return insertNoticeFromHistoryAt(ref, insertAt, from, text, tsEpochMs, "", Map.of());
  }

  public synchronized int insertNoticeFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);
    if (ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(doc, messageId)) {
      return Math.max(0, insertAt);
    }

    LineMeta meta =
        buildLineMeta(
            ref, LogKind.NOTICE, LogDirection.IN, from, tsEpochMs, null, messageId, ircv3Tags);
    FilterEngine.Match m = hideMatch(ref, LogKind.NOTICE, LogDirection.IN, from, text, meta.tags());
    if (m != null) {
      FilterEngine.Effective eff = filterEngine.effectiveFor(ref);
      if (eff.historyPlaceholdersEnabled()) {
        return onFilteredLineInsertAt(ref, insertAt, previewChatLine(from, text), meta, m);
      }
      // History placeholders disabled: silently drop filtered history lines.
      endFilteredInsertRun(ref);
      return Math.max(0, insertAt);
    }

    return insertLineInternalAt(
        ref, insertAt, from, text, styles.noticeFrom(), styles.noticeMessage(), false, meta);
  }

  public synchronized int prependNoticeFromHistory(
      TargetRef ref, String from, String text, long tsEpochMs) {
    return insertNoticeFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  private AttributeSet statusFromStyleFor(TargetRef ref) {
    if (ref != null && ref.isApplicationUi()) {
      // Application diagnostics read better when the source tag is visually distinct.
      return styles.noticeFrom();
    }
    return styles.status();
  }

  private AttributeSet errorFromStyleFor(TargetRef ref) {
    if (ref != null && ref.isApplicationUi()) {
      // Keep source tags consistent across status/error lines in diagnostics buffers.
      return styles.noticeFrom();
    }
    return styles.error();
  }

  public synchronized int insertStatusFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);

    LineMeta meta = buildLineMeta(ref, LogKind.STATUS, LogDirection.SYSTEM, from, tsEpochMs, null);
    FilterEngine.Match m =
        hideMatch(ref, LogKind.STATUS, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      FilterEngine.Effective eff = filterEngine.effectiveFor(ref);
      if (eff.historyPlaceholdersEnabled()) {
        return onFilteredLineInsertAt(ref, insertAt, previewChatLine(from, text), meta, m);
      }
      // History placeholders disabled: silently drop filtered history lines.
      endFilteredInsertRun(ref);
      return Math.max(0, insertAt);
    }

    return insertLineInternalAt(
        ref, insertAt, from, text, statusFromStyleFor(ref), styles.status(), false, meta);
  }

  public synchronized int prependStatusFromHistory(
      TargetRef ref, String from, String text, long tsEpochMs) {
    return insertStatusFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  public synchronized int insertErrorFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);

    LineMeta meta = buildLineMeta(ref, LogKind.ERROR, LogDirection.SYSTEM, from, tsEpochMs, null);
    FilterEngine.Match m =
        hideMatch(ref, LogKind.ERROR, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      FilterEngine.Effective eff = filterEngine.effectiveFor(ref);
      if (eff.historyPlaceholdersEnabled()) {
        return onFilteredLineInsertAt(ref, insertAt, previewChatLine(from, text), meta, m);
      }
      // History placeholders disabled: silently drop filtered history lines.
      endFilteredInsertRun(ref);
      return Math.max(0, insertAt);
    }

    return insertLineInternalAt(
        ref, insertAt, from, text, errorFromStyleFor(ref), styles.error(), false, meta);
  }

  public synchronized int prependErrorFromHistory(
      TargetRef ref, String from, String text, long tsEpochMs) {
    return insertErrorFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  public synchronized int insertPresenceFromHistoryAt(
      TargetRef ref, int insertAt, String displayText, long tsEpochMs) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);

    LineMeta meta =
        buildLineMeta(ref, LogKind.PRESENCE, LogDirection.SYSTEM, null, tsEpochMs, null);
    FilterEngine.Match m =
        hideMatch(ref, LogKind.PRESENCE, LogDirection.SYSTEM, null, displayText, meta.tags());
    if (m != null) {
      return onFilteredLineInsertAt(ref, insertAt, previewChatLine(null, displayText), meta, m);
    }

    return insertLineInternalAt(
        ref, insertAt, null, displayText, styles.status(), styles.status(), false, meta);
  }

  public synchronized int prependPresenceFromHistory(
      TargetRef ref, String displayText, long tsEpochMs) {
    return insertPresenceFromHistoryAt(ref, 0, displayText, tsEpochMs);
  }

  public synchronized int insertSpoilerChatFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);

    LineMeta meta = buildLineMeta(ref, LogKind.SPOILER, LogDirection.IN, from, tsEpochMs, null);
    FilterEngine.Match m =
        firstFilterMatch(ref, LogKind.SPOILER, LogDirection.IN, from, text, meta.tags());
    if (m != null && m.isHide()) {
      FilterEngine.Effective eff = filterEngine.effectiveFor(ref);
      if (eff.historyPlaceholdersEnabled()) {
        return onFilteredLineInsertAt(ref, insertAt, previewChatLine(from, text), meta, m);
      }
      // History placeholders disabled: silently drop filtered history lines.
      endFilteredInsertRun(ref);
      return Math.max(0, insertAt);
    }

    // Visible history insert.
    endFilteredInsertRun(ref);

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, insertAt);
    pos = ensureAtLineStartForInsert(doc, pos);

    String msg = text == null ? "" : text;
    String tsPrefix =
        (ts != null && ts.enabled() && timestampsIncludeChatMessages())
            ? ts.prefixAt(tsEpochMs)
            : "";
    final int offFinal = pos;
    final SpoilerMessageComponent comp = buildSpoilerComponent(ref, from, tsPrefix);
    SimpleAttributeSet attrs = withLineMeta(styles.message(), meta);
    if (m != null) {
      attrs = withFilterMatch(attrs, m);
    }
    StyleConstants.setComponent(attrs, comp);
    try {
      doc.insertString(offFinal, " ", attrs);
      final Position spoilerPos = doc.createPosition(offFinal);
      comp.setOnReveal(() -> revealSpoilerInPlace(ref, doc, spoilerPos, comp, tsPrefix, from, msg));
      SimpleAttributeSet tsAttrs = withLineMeta(styles.timestamp(), meta);
      if (m != null) {
        tsAttrs = withFilterMatch(tsAttrs, m);
      }
      doc.insertString(offFinal + 1, "\n", tsAttrs);
      pos = offFinal + 2;
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, offFinal, delta);
    int trimmed = enforceTranscriptLineCap(ref, doc);
    if (trimmed > 0) {
      pos = Math.max(0, pos - trimmed);
    }
    return pos;
  }

  public synchronized int prependSpoilerChatFromHistory(
      TargetRef ref, String from, String text, long tsEpochMs) {
    return insertSpoilerChatFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  private int insertLineInternalAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      AttributeSet fromStyle,
      AttributeSet msgStyle,
      boolean allowEmbeds,
      LineMeta meta) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, (meta != null) ? meta.epochMs() : null);
    if (doc == null) return Math.max(0, insertAt);

    FilterEngine.Match match = null;
    if (meta != null) {
      String filterFrom = Objects.toString(meta.fromNick(), "").isBlank() ? from : meta.fromNick();
      match = firstFilterMatch(ref, meta.kind(), meta.direction(), filterFrom, text, meta.tags());
      if (match != null && match.isHide()) {
        FilterEngine.Effective eff = filterEngine.effectiveFor(ref);
        if (eff.historyPlaceholdersEnabled()) {
          return onFilteredLineInsertAt(ref, insertAt, previewChatLine(from, text), meta, match);
        }
        // History placeholders disabled: silently drop filtered history lines.
        endFilteredInsertRun(ref);
        return Math.max(0, insertAt);
      }
    }

    // Visible history inserts should break any active filtered run created by prior hidden lines.
    endFilteredInsertRun(ref);

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, insertAt);
    pos = ensureAtLineStartForInsert(doc, pos);
    final int insertionStart = pos;

    Long epochMs = (meta != null) ? meta.epochMs() : null;
    String renderedFrom = renderTranscriptFrom(ref, from);
    SimpleAttributeSet tsStyle = withLineMeta(styles.timestamp(), meta);
    SimpleAttributeSet fromStyle2 =
        withLineMeta(fromStyle != null ? fromStyle : styles.from(), meta);
    SimpleAttributeSet msgStyle2 =
        withLineMeta(msgStyle != null ? msgStyle : styles.message(), meta);

    if (match != null) {
      tsStyle = withFilterMatch(tsStyle, match);
      fromStyle2 = withFilterMatch(fromStyle2, match);
      msgStyle2 = withFilterMatch(msgStyle2, match);
    }

    try {
      AttributeSet baseForId = msgStyle2;
      Object styleIdObj = baseForId.getAttribute(ChatStyles.ATTR_STYLE);
      String styleId = styleIdObj != null ? String.valueOf(styleIdObj) : null;

      boolean timestampsIncludeChatMessages = timestampsIncludeChatMessages();
      boolean timestampsIncludePresenceMessages = shouldIncludePresenceTimestamps();

      if (ts != null
          && ts.enabled()
          && (ChatStyles.STYLE_STATUS.equals(styleId)
              || ChatStyles.STYLE_ERROR.equals(styleId)
              || ChatStyles.STYLE_NOTICE_MESSAGE.equals(styleId)
              || (timestampsIncludePresenceMessages && ChatStyles.STYLE_PRESENCE.equals(styleId))
              || (timestampsIncludeChatMessages && ChatStyles.STYLE_MESSAGE.equals(styleId)))) {
        String prefix = (epochMs != null) ? ts.prefixAt(epochMs) : ts.prefixNow();
        doc.insertString(pos, prefix, tsStyle);
        pos += prefix.length();
      }

      if (renderedFrom != null && !renderedFrom.isBlank()) {
        String prefix = renderedFrom + ": ";
        doc.insertString(pos, prefix, fromStyle2);
        pos += prefix.length();
      }

      if (renderer != null && !shouldDeferRichTextDuringHistoryBatch(ref)) {
        pos = renderer.insertRichTextAt(doc, ref, text, msgStyle2, pos);
      } else {
        String t = text == null ? "" : text;
        pos = ChatRichTextRenderer.insertStyledTextAt(doc, t, msgStyle2, pos);
      }

      doc.insertString(pos, "\n", tsStyle);
      pos += 1;
      TranscriptState st = stateByTarget.get(ref);
      ChatTranscriptMessageStateSupport.rememberMessagePreview(
          messageStateSupportContext,
          st == null ? null : st.messagePreviewByMsgId,
          meta,
          renderedFrom,
          text);
      ChatTranscriptMessageStateSupport.rememberCurrentMessageContent(
          st == null ? null : st.currentMessageContentByMsgId, meta, renderedFrom, text);

      if (allowEmbeds) {
        // (Embeds are intentionally skipped here; rich inserts during history prefill can be
        // expensive.)
      }
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
    int trimmed = enforceTranscriptLineCap(ref, doc);
    if (trimmed > 0) {
      pos = Math.max(0, pos - trimmed);
    }
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
        AttributeSet prevAttrs = null;
        try {
          prevAttrs = doc.getCharacterElement(Math.max(0, p - 1)).getAttributes();
        } catch (Exception ignored2) {
          prevAttrs = null;
        }
        doc.insertString(p, "\n", withExistingMeta(styles.timestamp(), prevAttrs));
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

  private void removeReadMarkerControl(TargetRef ref, StyledDocument doc, TranscriptState st) {
    if (ref == null || doc == null || st == null || st.readMarker == null) return;
    try {
      int len = doc.getLength();
      int start = Math.max(0, Math.min(st.readMarker.pos.getOffset(), len));
      int removeLen = 0;
      if (start < len) {
        removeLen = 1;
      }
      if ((start + removeLen) < len) {
        try {
          String maybeNl = doc.getText(start + removeLen, 1);
          if ("\n".equals(maybeNl)) removeLen += 1;
        } catch (Exception ignored) {
        }
      }
      if (removeLen > 0) {
        doc.remove(start, removeLen);
        shiftCurrentPresenceBlock(ref, start, -removeLen);
      }
    } catch (Exception ignored) {
    } finally {
      st.readMarker = null;
    }
  }

  private boolean tryInsertReadMarkerControl(
      TargetRef ref, StyledDocument doc, TranscriptState st, long markerEpochMs) {
    if (ref == null || doc == null || st == null) return false;
    if (markerEpochMs <= 0L) return false;

    int firstUnreadStart = findFirstUnreadLineStart(doc, markerEpochMs);
    if (firstUnreadStart < 0) return false;

    LineMeta meta =
        buildLineMeta(ref, LogKind.STATUS, LogDirection.SYSTEM, null, markerEpochMs, null);

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, firstUnreadStart);
    pos = ensureAtLineStartForInsert(doc, pos);
    final int insertionStart = pos;

    HistoryDividerComponent comp = createTranscriptDividerComponent("Unread");

    try {
      SimpleAttributeSet attrs =
          withAuxiliaryRowKind(withLineMeta(styles.status(), meta), AUX_ROW_KIND_READ_MARKER);
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
      StyleConstants.setComponent(attrs, comp);
      doc.insertString(pos, " ", attrs);
      Position p = doc.createPosition(pos);
      doc.insertString(
          pos + 1,
          "\n",
          withAuxiliaryRowKind(withLineMeta(styles.timestamp(), meta), AUX_ROW_KIND_READ_MARKER));
      st.readMarker = new ReadMarkerControl(p);
    } catch (Exception ignored) {
      st.readMarker = null;
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
    return st.readMarker != null;
  }

  private void maybeRenderPendingReadMarker(TargetRef ref, Long lineEpochMs) {
    if (ref == null) return;
    TranscriptState st = stateByTarget.get(ref);
    StyledDocument doc = docs.get(ref);
    if (st == null || doc == null) return;
    if (st.readMarker != null) return;

    Long markerEpochMs = st.readMarkerEpochMs;
    if (markerEpochMs == null || markerEpochMs <= 0L) return;
    if (lineEpochMs != null && lineEpochMs <= markerEpochMs) return;

    tryInsertReadMarkerControl(ref, doc, st, markerEpochMs);
  }

  private int findFirstUnreadLineStart(StyledDocument doc, long markerEpochMs) {
    if (doc == null) return -1;
    try {
      Element root = doc.getDefaultRootElement();
      if (root == null) return -1;
      int lineCount = root.getElementCount();
      int len = doc.getLength();
      for (int i = 0; i < lineCount; i++) {
        Element line = root.getElement(i);
        if (line == null) continue;
        int start = Math.max(0, line.getStartOffset());
        if (start >= len) continue;

        AttributeSet attrs = doc.getCharacterElement(start).getAttributes();
        if (!ChatTranscriptAttrSupport.isConversationLine(attrs)) continue;

        Long lineEpochMs = ChatTranscriptAttrSupport.lineEpochMs(attrs);
        if (lineEpochMs == null) continue;
        if (lineEpochMs > markerEpochMs) return start;
      }
    } catch (Exception ignored) {
    }
    return -1;
  }

  private void appendReplyContextLine(
      TargetRef ref, String fromNick, String replyToMsgId, long tsEpochMs) {
    String targetMsgId = normalizeMessageId(replyToMsgId);
    if (targetMsgId.isEmpty()) return;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null) return;

    ensureAtLineStart(doc);

    Map<String, String> tags = Map.of("draft/reply", targetMsgId);
    LineMeta meta =
        buildLineMeta(
            ref, LogKind.STATUS, LogDirection.SYSTEM, fromNick, tsEpochMs, null, targetMsgId, tags);
    AttributeSet tsStyle = withLineMeta(styles.timestamp(), meta);
    SimpleAttributeSet prefixStyle = withLineMeta(styles.status(), meta);
    prefixStyle.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
    SimpleAttributeSet msgRefStyle = withLineMeta(styles.link(), meta);
    msgRefStyle.addAttribute(ChatStyles.ATTR_MSG_REF, targetMsgId);

    String from = renderTranscriptFrom(ref, fromNick);
    String prefix = from.isEmpty() ? "-> Reply to " : ("-> " + from + " replied to ");
    String preview = previewForMessageId(st, targetMsgId);

    try {
      if (ts != null && ts.enabled()) {
        doc.insertString(doc.getLength(), ts.prefixAt(tsEpochMs), tsStyle);
      }
      doc.insertString(doc.getLength(), prefix, prefixStyle);
      doc.insertString(doc.getLength(), targetMsgId, msgRefStyle);
      if (!preview.isBlank()) {
        doc.insertString(doc.getLength(), " (" + preview + ")", prefixStyle);
      }
      doc.insertString(doc.getLength(), "\n", tsStyle);
    } catch (Exception ignored) {
    }
  }

  private boolean replaceMessageLine(
      TargetRef ref,
      StyledDocument doc,
      int lineStart,
      AttributeSet existingAttrs,
      String replacementText,
      long tsEpochMs,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    if (ref == null || doc == null || existingAttrs == null) return false;

    ChatTranscriptLineMetaSupport.ReplacementPlan plan =
        ChatTranscriptLineMetaSupport.planReplacement(
            existingAttrs,
            tsEpochMs,
            replacementMessageId,
            replacementIrcv3Tags,
            System::currentTimeMillis);
    if (plan == null) return false;
    noteEpochMs(ref, plan.epochMs());

    int lineEnd = ChatTranscriptDocumentSupport.lineEndOffsetForLineStart(doc, lineStart);
    int removeLen = Math.max(0, lineEnd - lineStart);
    if (removeLen <= 0) return false;
    try {
      doc.remove(lineStart, removeLen);
    } catch (Exception ignored) {
      return false;
    }

    LineMeta meta =
        buildLineMeta(
            ref,
            plan.kind(),
            plan.direction(),
            plan.fromNick(),
            plan.epochMs(),
            null,
            plan.messageIdForMeta(),
            plan.mergedTags());
    String text = Objects.toString(replacementText, "");

    if (plan.kind() == LogKind.ACTION) {
      insertActionLineInternalAt(
          ref, lineStart, plan.fromNick(), text, plan.outgoingLocalEcho(), meta);
      TranscriptState st = stateByTarget.get(ref);
      ChatTranscriptMessageStateSupport.rememberMessagePreview(
          messageStateSupportContext,
          st == null ? null : st.messagePreviewByMsgId,
          meta,
          renderTranscriptFrom(ref, plan.fromNick()),
          text);
      return true;
    }

    AttributeSet fromStyle = (plan.kind() == LogKind.NOTICE) ? styles.noticeFrom() : styles.from();
    if (plan.kind() == LogKind.CHAT
        && plan.fromNick() != null
        && !plan.fromNick().isBlank()
        && nickColors != null
        && nickColors.enabled()) {
      fromStyle = nickColors.forNick(plan.fromNick(), fromStyle);
    }
    AttributeSet msgStyle =
        (plan.kind() == LogKind.NOTICE) ? styles.noticeMessage() : styles.message();

    SimpleAttributeSet fs = withLineMeta(fromStyle, meta);
    SimpleAttributeSet ms = withLineMeta(msgStyle, meta);
    applyOutgoingLineColor(fs, ms, plan.outgoingLocalEcho());
    insertLineInternalAt(ref, lineStart, plan.fromNick(), text, fs, ms, false, meta);
    TranscriptState st = stateByTarget.get(ref);
    ChatTranscriptMessageStateSupport.rememberMessagePreview(
        messageStateSupportContext,
        st == null ? null : st.messagePreviewByMsgId,
        meta,
        renderTranscriptFrom(ref, plan.fromNick()),
        text);
    return true;
  }

  private int insertActionLineInternalAt(
      TargetRef ref,
      int insertAt,
      String from,
      String action,
      boolean outgoingLocalEcho,
      LineMeta meta) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, (meta != null) ? meta.epochMs() : null);
    if (doc == null) return Math.max(0, insertAt);

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, insertAt);
    pos = ensureAtLineStartForInsert(doc, pos);
    final int insertionStart = pos;

    long tsEpochMs =
        (meta != null && meta.epochMs() != null && meta.epochMs() > 0)
            ? meta.epochMs()
            : System.currentTimeMillis();
    String a = action == null ? "" : action;

    try {
      boolean timestampsIncludeChatMessages = timestampsIncludeChatMessages();

      AttributeSet tsStyle = withLineMeta(styles.timestamp(), meta);
      if (ts != null && ts.enabled() && timestampsIncludeChatMessages) {
        String prefix = ts.prefixAt(tsEpochMs);
        doc.insertString(pos, prefix, tsStyle);
        pos += prefix.length();
      }

      AttributeSet msgStyle = withLineMeta(styles.actionMessage(), meta);
      AttributeSet fromStyle = styles.actionFrom();
      if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
        fromStyle = nickColors.forNick(from, fromStyle);
      }

      SimpleAttributeSet ms = new SimpleAttributeSet(msgStyle);
      SimpleAttributeSet fs = withLineMeta(fromStyle, meta);
      applyOutgoingLineColor(fs, ms, outgoingLocalEcho);
      String renderedFrom = renderTranscriptFrom(ref, from);

      doc.insertString(pos, "* ", ms);
      pos += 2;
      if (renderedFrom != null && !renderedFrom.isBlank()) {
        doc.insertString(pos, renderedFrom, fs);
        pos += renderedFrom.length();
        doc.insertString(pos, " ", ms);
        pos += 1;
      }

      if (renderer != null) {
        pos = renderer.insertRichTextAt(doc, ref, a, ms, pos);
      } else {
        pos = ChatRichTextRenderer.insertStyledTextAt(doc, a, ms, pos);
      }

      doc.insertString(pos, "\n", tsStyle);
      pos += 1;
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
    return pos;
  }

  private void clearReactionStateForMessage(
      TargetRef ref, StyledDocument doc, TranscriptState st, String targetMessageId) {
    if (ref == null || doc == null || st == null) return;
    String targetMsgId = normalizeMessageId(targetMessageId);
    if (targetMsgId.isEmpty()) return;

    ReactionState state = st.reactionsByTargetMsgId.remove(targetMsgId);
    if (state == null || state.control == null) return;

    try {
      int len = doc.getLength();
      int start = Math.max(0, Math.min(state.control.pos.getOffset(), len));
      int removeLen = 0;
      if (start < len) removeLen = 1;
      if ((start + removeLen) < len) {
        try {
          String maybeNl = doc.getText(start + removeLen, 1);
          if ("\n".equals(maybeNl)) removeLen += 1;
        } catch (Exception ignored) {
        }
      }
      if (removeLen > 0) {
        doc.remove(start, removeLen);
        shiftCurrentPresenceBlock(ref, start, -removeLen);
      }
    } catch (Exception ignored) {
    } finally {
      state.control = null;
    }
  }

  private static String renderEditedText(String text) {
    String t = Objects.toString(text, "");
    if (t.isBlank()) {
      return "(edited)";
    }
    return t + " (edited)";
  }

  private void markLineRangeRedacted(StyledDocument doc, int lineStart) {
    if (doc == null || lineStart < 0) return;
    int lineEnd = ChatTranscriptDocumentSupport.lineEndOffsetForLineStart(doc, lineStart);
    int len = Math.max(0, lineEnd - lineStart);
    if (len <= 0) return;
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_REDACTED, Boolean.TRUE);
    try {
      doc.setCharacterAttributes(lineStart, len, attrs, false);
    } catch (Exception ignored) {
    }
  }

  private void applyMessageReactionInternal(
      TargetRef ref,
      StyledDocument doc,
      TranscriptState st,
      String targetMessageId,
      String reaction,
      String fromNick,
      long tsEpochMs) {
    if (ref == null || doc == null || st == null) return;
    String targetMsgId = normalizeMessageId(targetMessageId);
    String reactionToken = Objects.toString(reaction, "").trim();
    String nick = Objects.toString(fromNick, "").trim();
    if (targetMsgId.isEmpty() || reactionToken.isEmpty() || nick.isEmpty()) return;

    ReactionState state =
        st.reactionsByTargetMsgId.computeIfAbsent(targetMsgId, k -> new ReactionState());
    state.observe(reactionToken, nick);
    if (state.control != null && state.control.component != null) {
      try {
        configureReactionControlCallbacks(state.control.component, ref, targetMsgId);
        state.control.component.setReactions(state.reactionsSnapshot());
      } catch (Exception ignored) {
      }
      return;
    }

    int lineStart = ChatTranscriptDocumentSupport.findLineStartByMessageId(doc, targetMsgId);
    if (lineStart < 0) {
      return;
    }
    insertReactionControlForMessage(ref, doc, st, targetMsgId, lineStart, state, tsEpochMs);
  }

  private void removeMessageReactionInternal(
      TargetRef ref,
      StyledDocument doc,
      TranscriptState st,
      String targetMessageId,
      String reaction,
      String fromNick,
      long tsEpochMs) {
    if (ref == null || doc == null || st == null) return;
    String targetMsgId = normalizeMessageId(targetMessageId);
    String reactionToken = Objects.toString(reaction, "").trim();
    String nick = Objects.toString(fromNick, "").trim();
    if (targetMsgId.isEmpty() || reactionToken.isEmpty() || nick.isEmpty()) return;

    ReactionState state = st.reactionsByTargetMsgId.get(targetMsgId);
    if (state == null) return;
    state.forget(reactionToken, nick);
    if (state.isEmpty()) {
      clearReactionStateForMessage(ref, doc, st, targetMsgId);
      return;
    }

    if (state.control != null && state.control.component != null) {
      try {
        configureReactionControlCallbacks(state.control.component, ref, targetMsgId);
        state.control.component.setReactions(state.reactionsSnapshot());
      } catch (Exception ignored) {
      }
      return;
    }

    int lineStart = ChatTranscriptDocumentSupport.findLineStartByMessageId(doc, targetMsgId);
    if (lineStart < 0) return;
    insertReactionControlForMessage(ref, doc, st, targetMsgId, lineStart, state, tsEpochMs);
  }

  private void materializePendingReactionsForMessage(
      TargetRef ref, StyledDocument doc, TranscriptState st, String messageId, long tsEpochMs) {
    if (ref == null || doc == null || st == null) return;
    String msgId = normalizeMessageId(messageId);
    if (msgId.isEmpty()) return;

    ReactionState state = st.reactionsByTargetMsgId.get(msgId);
    if (state == null || state.isEmpty() || state.control != null) return;

    int lineStart = ChatTranscriptDocumentSupport.findLineStartByMessageId(doc, msgId);
    if (lineStart < 0) return;
    insertReactionControlForMessage(ref, doc, st, msgId, lineStart, state, tsEpochMs);
  }

  private void insertReactionControlForMessage(
      TargetRef ref,
      StyledDocument doc,
      TranscriptState st,
      String targetMsgId,
      int messageLineStart,
      ReactionState state,
      long tsEpochMs) {
    if (ref == null || doc == null || st == null || state == null) return;
    if (state.control != null) return;

    int lineEnd = ChatTranscriptDocumentSupport.lineEndOffsetForLineStart(doc, messageLineStart);
    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, lineEnd);
    pos = ensureAtLineStartForInsert(doc, pos);
    final int insertionStart = pos;

    MessageReactionsComponent comp = new MessageReactionsComponent();
    Font f = safeTranscriptFont();
    if (f != null) {
      try {
        comp.setTranscriptFont(f);
      } catch (Exception ignored) {
      }
    }
    configureReactionControlCallbacks(comp, ref, targetMsgId);
    comp.setReactions(state.reactionsSnapshot());

    LineMeta meta =
        buildLineMeta(
            ref,
            LogKind.STATUS,
            LogDirection.SYSTEM,
            null,
            tsEpochMs,
            null,
            targetMsgId,
            Map.of("draft/react", "1"));

    try {
      SimpleAttributeSet attrs =
          withAuxiliaryRowKind(withLineMeta(styles.status(), meta), AUX_ROW_KIND_REACTION_SUMMARY);
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
      StyleConstants.setComponent(attrs, comp);
      doc.insertString(pos, " ", attrs);
      Position p = doc.createPosition(pos);
      doc.insertString(
          pos + 1,
          "\n",
          withAuxiliaryRowKind(
              withLineMeta(styles.timestamp(), meta), AUX_ROW_KIND_REACTION_SUMMARY));
      state.control = new ReactionSummaryControl(p, comp);
    } catch (Exception ignored) {
      state.control = null;
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
  }

  private void configureReactionControlCallbacks(
      MessageReactionsComponent comp, TargetRef ref, String targetMsgId) {
    if (comp == null || ref == null) return;
    String msgId = normalizeMessageId(targetMsgId);
    if (msgId.isEmpty()) return;
    comp.setOnReactRequested(token -> dispatchReactionChipAction(ref, msgId, token, false));
    comp.setOnUnreactRequested(token -> dispatchReactionChipAction(ref, msgId, token, true));
  }

  private void dispatchReactionChipAction(
      TargetRef ref, String targetMsgId, String reactionToken, boolean unreactRequested) {
    if (ref == null) return;
    String msgId = normalizeMessageId(targetMsgId);
    String token = Objects.toString(reactionToken, "").trim();
    if (msgId.isEmpty() || token.isEmpty()) return;
    ReactionChipActionHandler handler = reactionChipActionHandler;
    if (handler == null) return;
    try {
      handler.onReactionAction(ref, msgId, token, unreactRequested);
    } catch (Exception ignored) {
    }
  }

  private void insertCanonicalOutgoingChatLineAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    breakPresenceRun(ref);

    LineMeta meta =
        buildLineMeta(
            ref, LogKind.CHAT, LogDirection.OUT, from, tsEpochMs, null, messageId, ircv3Tags);

    ChatTranscriptSenderStyleSupport.PreparedStyles preparedStyles =
        ChatTranscriptSenderStyleSupport.prepare(senderStyleSupportContext, meta, from, true, null);
    SimpleAttributeSet fs = preparedStyles.fromStyle();
    SimpleAttributeSet ms = preparedStyles.messageStyle();

    int after = insertLineInternalAt(ref, insertAt, from, text, fs, ms, false, meta);

    // Inline delivery confirmation dot that fades away.
    if (outgoingDeliveryIndicatorsEnabled()) {
      try {
        StyledDocument docForDot = docs.get(ref);
        if (docForDot != null) {
          SimpleAttributeSet attrs = new SimpleAttributeSet(ms);
          attrs = withLineMeta(attrs, meta);
          ChatTranscriptDeliveryIndicatorSupport.insertConfirmedDot(
              docForDot,
              after,
              attrs,
              component -> removeInlineComponentNear(docForDot, component));
        }
      } catch (Exception ignored) {
      }
    }

    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    ChatTranscriptOutgoingFollowUpSupport.Plan followUp =
        ChatTranscriptOutgoingFollowUpSupport.plan(messageId, ircv3Tags);
    if (doc != null && st != null) {
      followUp.runPendingMaterialization(
          () ->
              materializePendingReactionsForMessage(
                  ref, doc, st, followUp.normalizedMessageId(), tsEpochMs));
      followUp.runReplyReaction(
          () ->
              applyMessageReactionInternal(
                  ref,
                  doc,
                  st,
                  followUp.replyToMessageId(),
                  followUp.reactionToken(),
                  from,
                  tsEpochMs));
    }
  }

  /**
   * Removes a single embedded Swing component placeholder character from a transcript document.
   * Used by the outbound delivery indicator once its fade-out completes.
   */
  private boolean removeInlineComponentNear(StyledDocument doc, java.awt.Component expected) {
    if (doc == null || expected == null) return false;
    if (!SwingUtilities.isEventDispatchThread()) {
      final boolean[] ok = new boolean[] {false};
      try {
        SwingUtilities.invokeAndWait(() -> ok[0] = removeInlineComponentNear(doc, expected));
      } catch (Exception ignored) {
        return false;
      }
      return ok[0];
    }

    synchronized (ChatTranscriptStore.this) {
      return ChatTranscriptDeliveryIndicatorSupport.removeInlineComponent(doc, expected);
    }
  }

  private void insertFailedOutgoingChatLineAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs, String reason) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    breakPresenceRun(ref);

    String msg =
        Objects.toString(text, "")
            + " "
            + ChatTranscriptPendingOutgoingSupport.renderPendingFailure(reason);
    LineMeta meta = buildLineMeta(ref, LogKind.CHAT, LogDirection.OUT, from, tsEpochMs, null);
    SimpleAttributeSet fs = withLineMeta(styles.error(), meta);
    SimpleAttributeSet ms = withLineMeta(styles.error(), meta);
    fs.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);
    ms.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);
    insertLineInternalAt(ref, insertAt, from, msg, fs, ms, false, meta);
  }

  private void applyOutgoingLineColor(
      SimpleAttributeSet fromStyle, SimpleAttributeSet msgStyle, boolean outgoingLocalEcho) {
    if (!outgoingLocalEcho) return;
    if (fromStyle != null) fromStyle.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);
    if (msgStyle != null) msgStyle.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);

    UiSettings s = safeSettings();
    Color c = configuredOutgoingLineColor(s);
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

  private void applyNotificationRuleHighlightColor(
      SimpleAttributeSet fromStyle, SimpleAttributeSet msgStyle, String rawColor) {
    Color c = ChatTranscriptColorSupport.parseHexColor(rawColor);
    if (c == null) return;

    if (fromStyle != null) {
      fromStyle.addAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG, c);
      StyleConstants.setBackground(fromStyle, c);
    }
    if (msgStyle != null) {
      msgStyle.addAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG, c);
      StyleConstants.setBackground(msgStyle, c);
    }
  }

  private void onNickColorSettingsChanged(PropertyChangeEvent evt) {
    if (!NickColorSettingsBus.PROP_NICK_COLOR_SETTINGS.equals(evt.getPropertyName())) return;
    restyleAllDocumentsCoalesced();
  }

  private UiSettings safeSettings() {
    try {
      return uiSettings != null ? uiSettings.get() : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private boolean outgoingDeliveryIndicatorsEnabled() {
    UiSettings s = safeSettings();
    return s == null || s.outgoingDeliveryIndicatorsEnabled();
  }

  private Color configuredOutgoingLineColor(UiSettings s) {
    if (s == null || !s.clientLineColorEnabled()) return null;

    Color requested = ChatTranscriptColorSupport.parseHexColor(s.clientLineColor());
    if (requested == null) return null;

    Color bg = transcriptBaseBackground();
    if (bg == null) return requested;
    if (ChatTranscriptColorSupport.contrastRatio(requested, bg) >= 4.5) return requested;

    Color fallback = transcriptBaseForeground();
    if (fallback == null) fallback = ChatTranscriptColorSupport.bestTextColorForBackground(bg);

    // Try to preserve as much of the requested hue as possible while meeting transcript
    // readability.
    for (int i = 1; i <= 24; i++) {
      double keepRequested = i / 24.0;
      Color adjusted = ChatTranscriptColorSupport.blendToward(fallback, requested, keepRequested);
      if (ChatTranscriptColorSupport.contrastRatio(adjusted, bg) >= 4.5) return adjusted;
    }

    if (ChatTranscriptColorSupport.contrastRatio(fallback, bg) >= 4.5) return fallback;
    return ChatTranscriptColorSupport.bestTextColorForBackground(bg);
  }

  private Color transcriptBaseBackground() {
    Color bg = StyleConstants.getBackground(styles.message());
    if (bg == null) bg = UIManager.getColor("TextPane.background");
    return bg;
  }

  private Color transcriptBaseForeground() {
    Color fg = StyleConstants.getForeground(styles.message());
    if (fg == null) fg = UIManager.getColor("TextPane.foreground");
    return fg;
  }

  private String buildSpoilerFromLabel(TargetRef ref, String from) {
    String fromLabel = renderTranscriptFrom(ref, from);
    if (!fromLabel.isBlank()) {
      fromLabel = fromLabel.endsWith(":") ? fromLabel + " " : fromLabel + ": ";
    }
    return fromLabel;
  }

  private SpoilerMessageComponent buildSpoilerComponent(
      TargetRef ref, String from, String tsPrefix) {
    SpoilerMessageComponent comp =
        new SpoilerMessageComponent(tsPrefix, buildSpoilerFromLabel(ref, from));
    try {
      if (uiSettings != null && uiSettings.get() != null) {
        comp.setTranscriptFont(
            new Font(
                uiSettings.get().chatFontFamily(), Font.PLAIN, uiSettings.get().chatFontSize()));
      }
    } catch (Exception ignored) {
    }
    try {
      if (nickColors != null && nickColors.enabled() && from != null && !from.isBlank()) {
        Color bg = UIManager.getColor("TextPane.background");
        Color fg = UIManager.getColor("TextPane.foreground");
        comp.setFromColor(nickColors.colorForNick(from, bg, fg));
      }
    } catch (Exception ignored) {
    }
    return comp;
  }

  public void appendSpoilerChat(TargetRef ref, String from, String text) {
    LineMeta meta =
        buildLineMeta(
            ref, LogKind.SPOILER, LogDirection.IN, from, System.currentTimeMillis(), null);
    FilterEngine.Match m =
        firstFilterMatch(ref, LogKind.SPOILER, LogDirection.IN, from, text, meta.tags());
    if (m != null && m.isHide()) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return;
    ensureAtLineStart(doc);

    String msg = text == null ? "" : text;
    String tsPrefix =
        (ts != null && ts.enabled() && timestampsIncludeChatMessages()) ? ts.prefixNow() : "";
    final int offFinal = doc.getLength();
    final SpoilerMessageComponent comp = buildSpoilerComponent(ref, from, tsPrefix);
    SimpleAttributeSet attrs = withLineMeta(styles.message(), meta);
    if (m != null) {
      attrs = withFilterMatch(attrs, m);
    }
    StyleConstants.setComponent(attrs, comp);
    try {
      doc.insertString(offFinal, " ", attrs);
      final Position spoilerPos = doc.createPosition(offFinal);
      comp.setOnReveal(() -> revealSpoilerInPlace(ref, doc, spoilerPos, comp, tsPrefix, from, msg));
      SimpleAttributeSet tsAttrs = withLineMeta(styles.timestamp(), meta);
      if (m != null) {
        tsAttrs = withFilterMatch(tsAttrs, m);
      }
      doc.insertString(doc.getLength(), "\n", tsAttrs);
    } catch (Exception ignored) {
    }
    enforceTranscriptLineCap(ref, doc);
  }

  public void appendSpoilerChatFromHistory(
      TargetRef ref, String from, String text, long tsEpochMs) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    LineMeta meta = buildLineMeta(ref, LogKind.SPOILER, LogDirection.IN, from, tsEpochMs, null);
    FilterEngine.Match m =
        firstFilterMatch(ref, LogKind.SPOILER, LogDirection.IN, from, text, meta.tags());
    if (m != null && m.isHide()) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return;
    ensureAtLineStart(doc);

    String msg = text == null ? "" : text;
    String tsPrefix =
        (ts != null && ts.enabled() && timestampsIncludeChatMessages())
            ? ts.prefixAt(tsEpochMs)
            : "";
    final int offFinal = doc.getLength();
    final SpoilerMessageComponent comp = buildSpoilerComponent(ref, from, tsPrefix);
    SimpleAttributeSet attrs = withLineMeta(styles.message(), meta);
    if (m != null) {
      attrs = withFilterMatch(attrs, m);
    }
    StyleConstants.setComponent(attrs, comp);
    try {
      doc.insertString(offFinal, " ", attrs);
      final Position spoilerPos = doc.createPosition(offFinal);
      comp.setOnReveal(() -> revealSpoilerInPlace(ref, doc, spoilerPos, comp, tsPrefix, from, msg));
      SimpleAttributeSet tsAttrs = withLineMeta(styles.timestamp(), meta);
      if (m != null) {
        tsAttrs = withFilterMatch(tsAttrs, m);
      }
      doc.insertString(doc.getLength(), "\n", tsAttrs);
    } catch (Exception ignored) {
    }
    enforceTranscriptLineCap(ref, doc);
  }

  private boolean revealSpoilerInPlace(
      TargetRef ref,
      StyledDocument doc,
      Position anchor,
      SpoilerMessageComponent expected,
      String tsPrefix,
      String from,
      String msg) {
    if (doc == null || anchor == null) return false;
    if (!SwingUtilities.isEventDispatchThread()) {
      final boolean[] ok = new boolean[] {false};
      try {
        SwingUtilities.invokeAndWait(
            () -> ok[0] = revealSpoilerInPlace(ref, doc, anchor, expected, tsPrefix, from, msg));
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

        AttributeSet tsStyle = withExistingMeta(styles.timestamp(), as);
        AttributeSet msgStyle = withExistingMeta(styles.message(), as);
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
          doc.insertString(pos, tsPrefix, tsStyle);
          pos += tsPrefix.length();
        }
        if (from != null && !from.isBlank()) {
          AttributeSet fromStyle = styles.from();
          if (nickColors != null && nickColors.enabled()) {
            fromStyle = nickColors.forNick(from, fromStyle);
          }
          fromStyle = withExistingMeta(fromStyle, as);
          String renderedFrom = renderTranscriptFrom(ref, from);
          String prefix = renderedFrom + ": ";
          doc.insertString(pos, prefix, fromStyle);
          pos += prefix.length();
        }
        DefaultStyledDocument inner = new DefaultStyledDocument();
        try {
          if (renderer != null) {
            renderer.insertRichText(inner, ref, msg, new SimpleAttributeSet(msgStyle));
          } else {
            ChatRichTextRenderer.insertStyledTextAt(inner, msg, msgStyle, 0);
          }
        } catch (Exception ignored2) {
          try {
            inner.remove(0, inner.getLength());
            ChatRichTextRenderer.insertStyledTextAt(inner, msg, msgStyle, 0);
          } catch (Exception ignored3) {
          }
        }

        pos = insertStyled(inner, doc, pos);
        doc.insertString(pos, "\n", tsStyle);
        return true;
      } catch (Exception ignored) {
        return false;
      }
    }
  }

  private static int findSpoilerOffset(
      StyledDocument doc, int guess, SpoilerMessageComponent expected) {
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
    appendActionInternal(ref, from, action, outgoingLocalEcho, true, null, "", Map.of(), null);
  }

  public void appendActionFromHistory(
      TargetRef ref, String from, String action, boolean outgoingLocalEcho, long tsEpochMs) {
    appendActionFromHistory(ref, from, action, outgoingLocalEcho, tsEpochMs, "", Map.of());
  }

  public void appendActionFromHistory(
      TargetRef ref,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendActionInternal(
        ref, from, action, outgoingLocalEcho, false, tsEpochMs, messageId, ircv3Tags, null);
  }

  /** Append an action (/me) with a timestamp, allowing embeds. */
  public void appendActionAt(
      TargetRef ref, String from, String action, boolean outgoingLocalEcho, long tsEpochMs) {
    appendActionAt(ref, from, action, outgoingLocalEcho, tsEpochMs, "", Map.of(), null);
  }

  public void appendActionAt(
      TargetRef ref,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendActionAt(ref, from, action, outgoingLocalEcho, tsEpochMs, messageId, ircv3Tags, null);
  }

  public void appendActionAt(
      TargetRef ref,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    appendActionInternal(
        ref,
        from,
        action,
        outgoingLocalEcho,
        true,
        tsEpochMs,
        messageId,
        ircv3Tags,
        notificationRuleHighlightColor);
  }

  private void appendActionInternal(
      TargetRef ref,
      String from,
      String action,
      boolean outgoingLocalEcho,
      boolean allowEmbeds,
      Long epochMs,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    ensureTargetExists(ref);

    LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
    long tsEpochMs = epochMs != null ? epochMs : System.currentTimeMillis();
    noteEpochMs(ref, tsEpochMs);
    if (ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(docs.get(ref), messageId)) {
      return;
    }

    LineMeta meta =
        buildLineMeta(ref, LogKind.ACTION, dir, from, tsEpochMs, null, messageId, ircv3Tags);

    FilterEngine.Match m = firstFilterMatch(ref, LogKind.ACTION, dir, from, action, meta.tags());
    if (m != null && m.isHide()) {
      onFilteredLineAppend(ref, previewActionLine(from, action), meta, m);
      return;
    }

    breakPresenceRun(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return;

    ChatTranscriptOutgoingFollowUpSupport.Plan followUp =
        ChatTranscriptOutgoingFollowUpSupport.plan(messageId, ircv3Tags);
    followUp.runReplyContext(
        replyToMsgId -> appendReplyContextLine(ref, from, replyToMsgId, tsEpochMs));

    String a = action == null ? "" : action;
    ensureAtLineStart(doc);

    try {
      boolean timestampsIncludeChatMessages = timestampsIncludeChatMessages();

      SimpleAttributeSet tsStyle = withLineMeta(styles.timestamp(), meta);
      if (m != null) {
        tsStyle = withFilterMatch(tsStyle, m);
      }

      if (ts != null && ts.enabled() && timestampsIncludeChatMessages) {
        String prefix = ts.prefixAt(tsEpochMs);
        doc.insertString(doc.getLength(), prefix, tsStyle);
      }

      SimpleAttributeSet msgStyle = withLineMeta(styles.actionMessage(), meta);
      AttributeSet fromStyle = styles.actionFrom();

      if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
        fromStyle = nickColors.forNick(from, fromStyle);
      }

      SimpleAttributeSet ms = new SimpleAttributeSet(msgStyle);
      SimpleAttributeSet fs = withLineMeta(fromStyle, meta);
      applyOutgoingLineColor(fs, ms, outgoingLocalEcho);
      applyNotificationRuleHighlightColor(fs, ms, notificationRuleHighlightColor);
      if (m != null) {
        fs = withFilterMatch(fs, m);
        ms = withFilterMatch(ms, m);
      }
      String renderedFrom = renderTranscriptFrom(ref, from);

      doc.insertString(doc.getLength(), "* ", ms);
      if (renderedFrom != null && !renderedFrom.isBlank()) {
        doc.insertString(doc.getLength(), renderedFrom, fs);
        doc.insertString(doc.getLength(), " ", ms);
      }

      if (renderer != null) {
        renderer.insertRichText(doc, ref, a, ms);
      } else {
        ChatRichTextRenderer.insertStyledTextAt(doc, a, ms, doc.getLength());
      }

      int lineEndOffset = doc.getLength();
      doc.insertString(doc.getLength(), "\n", tsStyle);
      TranscriptState st = stateByTarget.get(ref);
      ChatTranscriptMessageStateSupport.rememberMessagePreview(
          messageStateSupportContext,
          st == null ? null : st.messagePreviewByMsgId,
          meta,
          renderedFrom,
          a);
      ChatTranscriptMessageStateSupport.rememberCurrentMessageContent(
          st == null ? null : st.currentMessageContentByMsgId, meta, renderedFrom, a);

      if (!allowEmbeds) {
        enforceTranscriptLineCap(ref, doc);
        maybeRenderPendingReadMarker(ref, tsEpochMs);
        return;
      }

      LinkedHashSet<String> blockedManualPreviewUrls = new LinkedHashSet<>();
      if (imageEmbeds != null && uiSettings != null && uiSettings.get().imageEmbedsEnabled()) {
        ChatImageEmbedder.AppendResult imageResult =
            imageEmbeds.appendEmbeds(ref, doc, a, from, ircv3Tags);
        if (imageResult != null && imageResult.blockedUrls() != null) {
          blockedManualPreviewUrls.addAll(imageResult.blockedUrls());
        }
      }

      if (linkPreviews != null && uiSettings != null && uiSettings.get().linkPreviewsEnabled()) {
        ChatLinkPreviewEmbedder.AppendResult linkResult =
            linkPreviews.appendPreviews(ref, doc, a, from, ircv3Tags);
        if (linkResult != null && linkResult.blockedUrls() != null) {
          blockedManualPreviewUrls.addAll(linkResult.blockedUrls());
        }
      }
      if (!blockedManualPreviewUrls.isEmpty()) {
        insertManualPreviewMarkers(
            doc, lineEndOffset, meta, m, List.copyOf(blockedManualPreviewUrls));
      }
      enforceTranscriptLineCap(ref, doc);
      maybeRenderPendingReadMarker(ref, tsEpochMs);
    } catch (Exception ignored) {
    }

    TranscriptState st = stateByTarget.get(ref);
    if (st != null) {
      followUp.runPendingMaterialization(
          () ->
              materializePendingReactionsForMessage(
                  ref, doc, st, followUp.normalizedMessageId(), tsEpochMs));
      followUp.runReplyReaction(
          () ->
              applyMessageReactionInternal(
                  ref,
                  doc,
                  st,
                  followUp.replyToMessageId(),
                  followUp.reactionToken(),
                  from,
                  tsEpochMs));
    }
  }

  public void appendNotice(TargetRef ref, String from, String text) {
    LineMeta meta =
        buildLineMeta(ref, LogKind.NOTICE, LogDirection.IN, from, System.currentTimeMillis(), null);
    FilterEngine.Match m = hideMatch(ref, LogKind.NOTICE, LogDirection.IN, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, styles.noticeFrom(), styles.noticeMessage(), true, meta);
  }

  public void appendStatus(TargetRef ref, String from, String text) {
    LineMeta meta =
        buildLineMeta(
            ref, LogKind.STATUS, LogDirection.SYSTEM, from, System.currentTimeMillis(), null);
    FilterEngine.Match m =
        hideMatch(ref, LogKind.STATUS, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, statusFromStyleFor(ref), styles.status(), true, meta);
  }

  public void appendError(TargetRef ref, String from, String text) {
    LineMeta meta =
        buildLineMeta(
            ref, LogKind.ERROR, LogDirection.SYSTEM, from, System.currentTimeMillis(), null);
    FilterEngine.Match m =
        hideMatch(ref, LogKind.ERROR, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, errorFromStyleFor(ref), styles.error(), true, meta);
  }

  public void appendNoticeFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    appendNoticeFromHistory(ref, from, text, tsEpochMs, "", Map.of());
  }

  public void appendNoticeFromHistory(
      TargetRef ref,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    if (ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(docs.get(ref), messageId)) {
      return;
    }
    LineMeta meta =
        buildLineMeta(
            ref, LogKind.NOTICE, LogDirection.IN, from, tsEpochMs, null, messageId, ircv3Tags);
    FilterEngine.Match m = hideMatch(ref, LogKind.NOTICE, LogDirection.IN, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, styles.noticeFrom(), styles.noticeMessage(), false, meta);
  }

  public void appendStatusFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    LineMeta meta = buildLineMeta(ref, LogKind.STATUS, LogDirection.SYSTEM, from, tsEpochMs, null);
    FilterEngine.Match m =
        hideMatch(ref, LogKind.STATUS, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, statusFromStyleFor(ref), styles.status(), false, meta);
  }

  public void appendErrorFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    LineMeta meta = buildLineMeta(ref, LogKind.ERROR, LogDirection.SYSTEM, from, tsEpochMs, null);
    FilterEngine.Match m =
        hideMatch(ref, LogKind.ERROR, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, errorFromStyleFor(ref), styles.error(), false, meta);
  }

  /** Append a notice with a timestamp, allowing embeds. */
  public void appendNoticeAt(TargetRef ref, String from, String text, long tsEpochMs) {
    appendNoticeAt(ref, from, text, tsEpochMs, "", Map.of());
  }

  public void appendNoticeAt(
      TargetRef ref,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    if (ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(docs.get(ref), messageId)) {
      return;
    }
    LineMeta meta =
        buildLineMeta(
            ref, LogKind.NOTICE, LogDirection.IN, from, tsEpochMs, null, messageId, ircv3Tags);
    FilterEngine.Match m = hideMatch(ref, LogKind.NOTICE, LogDirection.IN, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    ChatTranscriptOutgoingFollowUpSupport.Plan followUp =
        ChatTranscriptOutgoingFollowUpSupport.plan(messageId, ircv3Tags);
    followUp.runReplyContext(
        replyToMsgId -> appendReplyContextLine(ref, from, replyToMsgId, tsEpochMs));
    appendLineInternal(ref, from, text, styles.noticeFrom(), styles.noticeMessage(), true, meta);
    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc != null && st != null) {
      followUp.runPendingMaterialization(
          () ->
              materializePendingReactionsForMessage(
                  ref, doc, st, followUp.normalizedMessageId(), tsEpochMs));
      followUp.runReplyReaction(
          () ->
              applyMessageReactionInternal(
                  ref,
                  doc,
                  st,
                  followUp.replyToMessageId(),
                  followUp.reactionToken(),
                  from,
                  tsEpochMs));
    }
  }

  /** Append a status line with a timestamp, allowing embeds. */
  public void appendStatusAt(TargetRef ref, String from, String text, long tsEpochMs) {
    appendStatusAt(ref, from, text, tsEpochMs, "", Map.of());
  }

  public void appendStatusAt(
      TargetRef ref,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    if (ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(docs.get(ref), messageId)) {
      return;
    }
    LineMeta meta =
        buildLineMeta(
            ref, LogKind.STATUS, LogDirection.SYSTEM, from, tsEpochMs, null, messageId, ircv3Tags);
    FilterEngine.Match m =
        hideMatch(ref, LogKind.STATUS, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, statusFromStyleFor(ref), styles.status(), true, meta);
  }

  /** Append an error line with a timestamp, allowing embeds. */
  public void appendErrorAt(TargetRef ref, String from, String text, long tsEpochMs) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    LineMeta meta = buildLineMeta(ref, LogKind.ERROR, LogDirection.SYSTEM, from, tsEpochMs, null);
    FilterEngine.Match m =
        hideMatch(ref, LogKind.ERROR, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, errorFromStyleFor(ref), styles.error(), true, meta);
  }

  public void appendPresenceFromHistory(TargetRef ref, String displayText, long tsEpochMs) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    LineMeta meta =
        buildLineMeta(ref, LogKind.PRESENCE, LogDirection.SYSTEM, null, tsEpochMs, null);
    FilterEngine.Match m =
        hideMatch(ref, LogKind.PRESENCE, LogDirection.SYSTEM, null, displayText, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, displayText, meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, null, displayText, styles.presence(), styles.presence(), false, meta);
  }

  private boolean shouldIncludePresenceTimestamps() {
    try {
      return uiSettings != null
          && uiSettings.get() != null
          && uiSettings.get().timestampsIncludePresenceMessages();
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean timestampsIncludeChatMessages() {
    try {
      return uiSettings != null
          && uiSettings.get() != null
          && uiSettings.get().timestampsIncludeChatMessages();
    } catch (Exception ignored) {
      return false;
    }
  }

  private void breakPresenceRun(TargetRef ref) {
    if (ref == null) return;
    TranscriptState st = stateByTarget.get(ref);
    if (st != null) {
      st.currentPresenceBlock = null;
      st.currentFilteredRun = null;
      st.currentFilteredHintRun = null;
    }
  }

  private void ensureAtLineStart(StyledDocument doc) {
    if (doc == null) return;
    int len = doc.getLength();
    if (len <= 0) return;
    try {
      String last = doc.getText(len - 1, 1);
      if (!"\n".equals(last)) {
        AttributeSet lastAttrs = null;
        try {
          lastAttrs = doc.getCharacterElement(Math.max(0, len - 1)).getAttributes();
        } catch (Exception ignored2) {
          lastAttrs = null;
        }
        doc.insertString(len, "\n", withExistingMeta(styles.timestamp(), lastAttrs));
      }
    } catch (Exception ignored) {
    }
  }

  private int transcriptMaxLinesPerTarget() {
    try {
      UiSettings s = uiSettings != null ? uiSettings.get() : null;
      int v =
          (s != null)
              ? s.chatTranscriptMaxLinesPerTarget()
              : DEFAULT_TRANSCRIPT_MAX_LINES_PER_TARGET;
      if (v < 0) return 0;
      return Math.min(MAX_TRANSCRIPT_LINES_PER_TARGET, v);
    } catch (Exception ignored) {
      return DEFAULT_TRANSCRIPT_MAX_LINES_PER_TARGET;
    }
  }

  private static int logicalLineCount(StyledDocument doc) {
    if (doc == null) return 0;
    try {
      Element root = doc.getDefaultRootElement();
      if (root == null) return 0;
      int count = Math.max(0, root.getElementCount());
      int len = doc.getLength();
      if (count > 0 && len > 0) {
        String last = doc.getText(len - 1, 1);
        if ("\n".equals(last)) {
          count = Math.max(0, count - 1);
        }
      }
      return count;
    } catch (Exception ignored) {
      return 0;
    }
  }

  private int enforceTranscriptLineCap(TargetRef ref, StyledDocument doc) {
    if (ref == null || doc == null) return 0;

    int maxLines = transcriptMaxLinesPerTarget();
    if (maxLines <= 0) return 0;

    int lineCount = logicalLineCount(doc);
    if (lineCount <= maxLines) return 0;

    int trimLines = lineCount - maxLines;
    try {
      Element root = doc.getDefaultRootElement();
      if (root == null || trimLines <= 0) return 0;
      int idx = Math.min(root.getElementCount() - 1, trimLines - 1);
      if (idx < 0) return 0;
      Element lastTrimmed = root.getElement(idx);
      if (lastTrimmed == null) return 0;
      int removeLen = Math.max(0, Math.min(lastTrimmed.getEndOffset(), doc.getLength()));
      if (removeLen <= 0) return 0;

      doc.remove(0, removeLen);
      resetStateAfterHeadTrim(ref);
      maybeRenderPendingReadMarker(ref, null);
      return removeLen;
    } catch (Exception ignored) {
      return 0;
    }
  }

  private void resetStateAfterHeadTrim(TargetRef ref) {
    TranscriptState st = stateByTarget.get(ref);
    if (st == null) return;

    st.earliestEpochMsSeen = null;
    st.currentPresenceBlock = null;
    st.currentFilteredRun = null;
    st.currentFilteredHintRun = null;
    st.currentFilteredRunInsert = null;
    st.currentFilteredHintRunInsert = null;
    st.historyInsertBatchActive = false;
    st.historyInsertPlaceholderRunsCreated = 0;
    st.historyInsertHintRunsCreated = 0;
    st.historyInsertOverflowRun = null;
    st.forceDeferRichTextDuringHistoryBatch = false;
    st.loadOlderControl = null;
    st.historyDivider = null;
    st.pendingHistoryDividerLabel = null;
    st.readMarker = null;
    st.reactionsByTargetMsgId.clear();
  }

  private void foldBlock(StyledDocument doc, PresenceBlock block) {
    if (doc == null || block == null) return;

    int start = Math.max(0, Math.min(block.startOffset, doc.getLength()));
    int end = Math.max(0, Math.min(block.endOffset, doc.getLength()));
    if (end <= start) return;

    try {
      AttributeSet existingAttrs = null;
      try {
        existingAttrs = doc.getCharacterElement(start).getAttributes();
      } catch (Exception ignored2) {
        existingAttrs = null;
      }

      doc.remove(start, end - start);
      PresenceFoldComponent comp = new PresenceFoldComponent(block.entries);

      SimpleAttributeSet attrs = withExistingMeta(styles.presence(), existingAttrs);
      StyleConstants.setComponent(attrs, comp);
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_PRESENCE);

      int insertPos = start;
      if (insertPos > 0) {
        try {
          String prev = doc.getText(insertPos - 1, 1);
          if (!"\n".equals(prev)) {
            AttributeSet prevAttrs = null;
            try {
              prevAttrs = doc.getCharacterElement(insertPos - 1).getAttributes();
            } catch (Exception ignored3) {
              prevAttrs = null;
            }
            doc.insertString(insertPos, "\n", withExistingMeta(styles.timestamp(), prevAttrs));
            insertPos++;
          }
        } catch (Exception ignored2) {
        }
      }

      doc.insertString(insertPos, " ", attrs);
      doc.insertString(insertPos + 1, "\n", withExistingMeta(styles.timestamp(), existingAttrs));

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
    FilteredRun currentFilteredRun;
    FilteredHintRun currentFilteredHintRun;

    // Separate run tracking for history/backfill inserts (typically at the top of the doc).
    FilteredRun currentFilteredRunInsert;
    FilteredHintRun currentFilteredHintRunInsert;

    // Step 5.2: per-batch overflow aggregation for history inserts.
    boolean historyInsertBatchActive;
    int historyInsertPlaceholderRunsCreated;
    int historyInsertHintRunsCreated;
    FilteredOverflowRun historyInsertOverflowRun;
    boolean forceDeferRichTextDuringHistoryBatch;

    LoadOlderControl loadOlderControl;
    HistoryDividerControl historyDivider;

    /**
     * If we load history into an otherwise-empty transcript (e.g., transcript rebuild), we defer
     * inserting the "History - <date>" divider until the next live append. This avoids leaving a
     * confusing divider row at the very bottom when there's no "live" content below it.
     */
    String pendingHistoryDividerLabel;

    ReadMarkerControl readMarker;
    Long readMarkerEpochMs;
    Map<String, ReactionState> reactionsByTargetMsgId = new HashMap<>();
    Map<String, String> messagePreviewByMsgId =
        ChatTranscriptReplyPreviewSupport.createBoundedReplyPreviewCache(
            REPLY_PREVIEW_CACHE_LIMIT_PER_TARGET);
    Map<String, MessageContentSnapshot> currentMessageContentByMsgId =
        createBoundedCache(REPLY_PREVIEW_CACHE_LIMIT_PER_TARGET);
    Map<String, RedactedMessageContent> redactedOriginalByMsgId =
        createBoundedCache(REDACTED_MESSAGE_CACHE_LIMIT_PER_TARGET);
  }

  /**
   * Common base for filtered-line run trackers. Each run tracks a contiguous group of hidden lines
   * that share a single visible placeholder/hint/overflow component in the transcript document.
   */
  abstract static class AbstractFilteredRun<C extends FilteredLineComponent> {
    final Position pos;
    final C component;

    FilterEngine.Match primaryMatch;
    boolean multiple;

    LineMeta lastHiddenMeta;
    final LinkedHashSet<String> unionTags = new LinkedHashSet<>();

    AbstractFilteredRun(Position pos, C component) {
      this.pos = pos;
      this.component = component;
    }

    void observe(FilterEngine.Match m, LineMeta hiddenMeta) {
      if (hiddenMeta != null) {
        lastHiddenMeta = hiddenMeta;
        try {
          unionTags.addAll(hiddenMeta.tags());
        } catch (Exception ignored) {
        }
      }

      if (m == null) return;
      if (primaryMatch == null) {
        primaryMatch = m;
        return;
      }

      try {
        if (primaryMatch.ruleId() != null
            && m.ruleId() != null
            && !primaryMatch.ruleId().equals(m.ruleId())) {
          multiple = true;
        }
      } catch (Exception ignored) {
        multiple = true;
      }
    }
  }

  /** Tracks a contiguous run of filtered lines (represented by a single placeholder component). */
  private static final class FilteredRun extends AbstractFilteredRun<FilteredFoldComponent> {
    private FilteredRun(Position pos, FilteredFoldComponent component) {
      super(pos, component);
    }
  }

  /**
   * Tracks a contiguous run of filtered lines when placeholders are disabled (shown as a tiny hint
   * row).
   */
  private static final class FilteredHintRun extends AbstractFilteredRun<FilteredHintComponent> {
    private FilteredHintRun(Position pos, FilteredHintComponent component) {
      super(pos, component);
    }
  }

  /**
   * Tracks the aggregated overflow row used once the history placeholder/hint run cap is exceeded.
   */
  private static final class FilteredOverflowRun
      extends AbstractFilteredRun<FilteredOverflowComponent> {
    private FilteredOverflowRun(Position pos, FilteredOverflowComponent component) {
      super(pos, component);
    }
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

    final HistoryDividerComponent component;

    private HistoryDividerControl(HistoryDividerComponent component) {

      this.component = component;
    }
  }

  private static final class ReadMarkerControl {
    final Position pos;

    private ReadMarkerControl(Position pos) {
      this.pos = pos;
    }
  }

  private static final class ReactionSummaryControl {
    final Position pos;
    final MessageReactionsComponent component;

    private ReactionSummaryControl(Position pos, MessageReactionsComponent component) {
      this.pos = pos;
      this.component = component;
    }
  }

  private static final class ReactionState {
    final Map<String, LinkedHashSet<String>> nicksByReaction = new LinkedHashMap<>();
    ReactionSummaryControl control;

    void observe(String reaction, String nick) {
      ChatTranscriptReactionStateSupport.observe(nicksByReaction, reaction, nick);
    }

    void forget(String reaction, String nick) {
      ChatTranscriptReactionStateSupport.forget(nicksByReaction, reaction, nick);
    }

    boolean isEmpty() {
      return nicksByReaction.isEmpty();
    }

    boolean hasReactionFromNick(String reaction, String normalizedNick) {
      return ChatTranscriptReactionStateSupport.hasReactionFromNick(
          nicksByReaction, reaction, normalizedNick);
    }

    Map<String, Collection<String>> reactionsSnapshot() {
      return ChatTranscriptReactionStateSupport.reactionsSnapshot(nicksByReaction);
    }
  }

  private static final class PresenceBlock {
    int startOffset;
    int endOffset;
    boolean folded = false;
    PresenceFoldComponent component;

    final List<PresenceFoldComponent.Entry> entries = new ArrayList<>();

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

  public void restyleAllDocumentsCoalesced() {
    boolean schedule = false;
    synchronized (this) {
      if (restylePassRunning) {
        restylePassRestartRequested = true;
      } else {
        restylePassRunning = true;
        resetRestylePassLocked();
        schedule = true;
      }
    }
    if (schedule) {
      SwingUtilities.invokeLater(this::runRestylePassSliceSafely);
    }
  }

  private void resetRestylePassLocked() {
    restylePassDocs = new ArrayList<>(docs.values());
    restylePassDocIndex = 0;
    restylePassDocOffset = 0;
  }

  private void clearRestylePassLocked() {
    restylePassRunning = false;
    restylePassRestartRequested = false;
    restylePassDocs = List.of();
    restylePassDocIndex = 0;
    restylePassDocOffset = 0;
  }

  private void runRestylePassSliceSafely() {
    try {
      runRestylePassSlice();
    } catch (Exception ignored) {
      synchronized (this) {
        clearRestylePassLocked();
      }
    }
  }

  private void runRestylePassSlice() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::runRestylePassSliceSafely);
      return;
    }

    UiSettings s = safeSettings();
    Color outgoingColor = configuredOutgoingLineColor(s);
    boolean outgoingColorEnabled = outgoingColor != null;

    boolean scheduleNext = false;
    synchronized (this) {
      if (!restylePassRunning) return;

      if (restylePassRestartRequested) {
        restylePassRestartRequested = false;
        resetRestylePassLocked();
      }

      int budget = RESTYLE_ELEMENTS_PER_SLICE;
      while (budget > 0 && restylePassDocIndex < restylePassDocs.size()) {
        StyledDocument doc = restylePassDocs.get(restylePassDocIndex);
        int currentOffset = restylePassDocOffset;
        RestyleSliceOutcome outcome =
            restyleDocumentSlice(doc, currentOffset, budget, outgoingColorEnabled, outgoingColor);
        if (outcome.done() || outcome.nextOffset() <= currentOffset) {
          restylePassDocIndex++;
          restylePassDocOffset = 0;
        } else {
          restylePassDocOffset = outcome.nextOffset();
        }
        budget -= Math.max(1, outcome.processedElements());
      }

      if (restylePassDocIndex >= restylePassDocs.size()) {
        if (restylePassRestartRequested) {
          restylePassRestartRequested = false;
          resetRestylePassLocked();
          scheduleNext = true;
        } else {
          clearRestylePassLocked();
        }
      } else {
        scheduleNext = true;
      }
    }

    if (scheduleNext) {
      SwingUtilities.invokeLater(this::runRestylePassSliceSafely);
    }
  }

  private record RestyleSliceOutcome(int processedElements, int nextOffset, boolean done) {}

  private void restyle(StyledDocument doc) {
    if (doc == null) return;

    UiSettings s = safeSettings();
    Color outgoingColor = configuredOutgoingLineColor(s);
    boolean outgoingColorEnabled = outgoingColor != null;

    int offset = 0;
    while (true) {
      RestyleSliceOutcome outcome =
          restyleDocumentSlice(doc, offset, Integer.MAX_VALUE, outgoingColorEnabled, outgoingColor);
      if (outcome.done()) return;
      if (outcome.nextOffset() <= offset) return;
      offset = outcome.nextOffset();
    }
  }

  private RestyleSliceOutcome restyleDocumentSlice(
      StyledDocument doc,
      int startOffset,
      int maxElements,
      boolean outgoingColorEnabled,
      Color outgoingColor) {
    if (doc == null) return new RestyleSliceOutcome(1, 0, true);

    int len = doc.getLength();
    if (len <= 0) return new RestyleSliceOutcome(1, 0, true);

    int offset = Math.max(0, Math.min(startOffset, len));
    int budget = Math.max(1, maxElements);
    int processed = 0;

    while (offset < len && processed < budget) {
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
      ChatTranscriptLineMetaSupport.copyRestyleMetaAttrs(old, fresh);
      Object url = old.getAttribute(ChatStyles.ATTR_URL);
      if (url != null) {
        fresh.addAttribute(ChatStyles.ATTR_URL, url);
      }
      Object manualPreviewUrl = old.getAttribute(ChatStyles.ATTR_MANUAL_PREVIEW_URL);
      if (manualPreviewUrl != null) {
        fresh.addAttribute(ChatStyles.ATTR_MANUAL_PREVIEW_URL, manualPreviewUrl);
      }
      Object chan = old.getAttribute(ChatStyles.ATTR_CHANNEL);
      if (chan != null) {
        fresh.addAttribute(ChatStyles.ATTR_CHANNEL, chan);
      }
      Object msgRef = old.getAttribute(ChatStyles.ATTR_MSG_REF);
      if (msgRef != null) {
        fresh.addAttribute(ChatStyles.ATTR_MSG_REF, msgRef);
      }
      Object filterActionRaw = old.getAttribute(ChatStyles.ATTR_META_FILTER_ACTION);
      FilterAction filterAction = ChatTranscriptAttrSupport.filterActionFromAttr(filterActionRaw);
      Color ruleBg = null;
      Object ruleBgObj = old.getAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG);
      if (ruleBgObj instanceof Color c) {
        ruleBg = c;
        fresh.addAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG, c);
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
      if (ruleBg != null) {
        finalBg = ruleBg;
      }
      if (finalFg != null) StyleConstants.setForeground(fresh, finalFg);
      if (finalBg != null) StyleConstants.setBackground(fresh, finalBg);
      if (filterAction != null && filterAction != FilterAction.HIDE) {
        applyFilterActionStyle(fresh, filterAction);
      }
      if (styleId != null) {
        fresh.addAttribute(ChatStyles.ATTR_STYLE, styleId);
      }
      EmojiFontSupport.reapplyEmojiRunFontIfPresent(old, fresh);

      doc.setCharacterAttributes(start, end - start, fresh, true);
      offset = end;
      processed++;
    }

    if (offset >= len) {
      return new RestyleSliceOutcome(Math.max(1, processed), len, true);
    }

    return new RestyleSliceOutcome(Math.max(1, processed), offset, false);
  }
}

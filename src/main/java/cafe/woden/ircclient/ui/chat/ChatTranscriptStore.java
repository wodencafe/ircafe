package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.PresenceEvent;
import cafe.woden.ircclient.app.PresenceKind;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.chat.embed.ChatImageEmbedder;
import cafe.woden.ircclient.ui.chat.embed.ChatLinkPreviewEmbedder;
import cafe.woden.ircclient.ui.chat.fold.FilteredFoldComponent;
import cafe.woden.ircclient.ui.chat.fold.FilteredHintComponent;
import cafe.woden.ircclient.ui.chat.fold.FilteredOverflowComponent;
import cafe.woden.ircclient.ui.chat.fold.PresenceFoldComponent;
import cafe.woden.ircclient.ui.chat.fold.LoadOlderMessagesComponent;
import cafe.woden.ircclient.ui.chat.fold.HistoryDividerComponent;
import cafe.woden.ircclient.ui.chat.fold.MessageReactionsComponent;
import cafe.woden.ircclient.ui.chat.fold.SpoilerMessageComponent;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.render.IrcFormatting;
import cafe.woden.ircclient.ui.filter.FilterContext;
import cafe.woden.ircclient.ui.filter.FilterEngine;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.logging.model.LogDirection;
import cafe.woden.ircclient.logging.model.LogKind;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.awt.Font;
import java.awt.Color;
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

  private static final int MAX_FILTERED_LINES_PER_PLACEHOLDER_RUN = 250;

  /**
   * Step 5.2: Safety cap for history/backfill. After this many filtered placeholder/hint runs are created in a single
   * history batch, we stop creating new placeholder rows and collapse the remainder into one summary row.
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
      UiSettingsBus uiSettings,
      FilterEngine filterEngine
  ) {
    this.styles = styles;
    this.renderer = renderer;
    this.ts = ts;
    this.nickColors = nickColors;
    this.nickColorSettings = nickColorSettings;
    this.imageEmbeds = imageEmbeds;
    this.linkPreviews = linkPreviews;
    this.uiSettings = uiSettings;
    this.filterEngine = filterEngine;

    if (this.nickColorSettings != null) {
      this.nickColorSettings.addListener(nickColorSettingsListener);
    }
  }


  private record LineMeta(
      String bufferKey,
      LogKind kind,
      LogDirection direction,
      String fromNick,
      Long epochMs,
      Set<String> tags,
      String messageId,
      String ircv3Tags
  ) {
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

  private LineMeta buildLineMeta(TargetRef ref, LogKind kind, LogDirection dir, String fromNick, Long epochMs, PresenceEvent presenceEvent) {
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
      Map<String, String> ircv3Tags
  ) {
    String msgId = normalizeMessageId(messageId);
    Map<String, String> tagsMap = normalizeIrcv3Tags(ircv3Tags);
    Set<String> tags = computeTags(kind, dir, fromNick, presenceEvent, msgId, tagsMap);
    return new LineMeta(
        bufferKey(ref),
        kind,
        dir,
        fromNick,
        epochMs,
        tags,
        msgId,
        formatIrcv3Tags(tagsMap));
  }

  private Set<String> computeTags(LogKind kind, LogDirection dir, String fromNick, PresenceEvent presenceEvent) {
    return computeTags(kind, dir, fromNick, presenceEvent, "", Map.of());
  }

  private Set<String> computeTags(
      LogKind kind,
      LogDirection dir,
      String fromNick,
      PresenceEvent presenceEvent,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();

    if (kind != null) {
      switch (kind) {
        case CHAT -> out.add("irc_privmsg");
        case ACTION -> out.add("irc_action");
        case NOTICE -> out.add("irc_notice");
        case PRESENCE -> out.add("irc_presence");
        case STATUS -> out.add("irc_status");
        case ERROR -> out.add("irc_error");
        case SPOILER -> {
          out.add("irc_privmsg");
          out.add("irc_spoiler");
        }
        default -> out.add("irc_misc");
      }
    }

    if (dir != null) {
      switch (dir) {
        case IN -> out.add("irc_in");
        case OUT -> out.add("irc_out");
        case SYSTEM -> out.add("irc_system");
      }
    }

    String fn = java.util.Objects.toString(fromNick, "").trim();
    if (!fn.isEmpty()) {
      out.add("nick_" + fn.toLowerCase(java.util.Locale.ROOT));
    }

    if (presenceEvent != null) {
      try {
        String nick = java.util.Objects.toString(presenceEvent.nick(), "").trim();
        String oldNick = java.util.Objects.toString(presenceEvent.oldNick(), "").trim();
        String newNick = java.util.Objects.toString(presenceEvent.newNick(), "").trim();
        switch (presenceEvent.kind()) {
          case JOIN -> {
            out.add("irc_join");
            if (!nick.isEmpty()) out.add("nick_" + nick.toLowerCase(java.util.Locale.ROOT));
          }
          case PART -> {
            out.add("irc_part");
            if (!nick.isEmpty()) out.add("nick_" + nick.toLowerCase(java.util.Locale.ROOT));
          }
          case QUIT -> {
            out.add("irc_quit");
            if (!nick.isEmpty()) out.add("nick_" + nick.toLowerCase(java.util.Locale.ROOT));
          }
          case NICK -> {
            out.add("irc_nick");
            if (!oldNick.isEmpty()) out.add("nick_" + oldNick.toLowerCase(java.util.Locale.ROOT));
            if (!newNick.isEmpty()) out.add("nick_" + newNick.toLowerCase(java.util.Locale.ROOT));
          }
        }
      } catch (Exception ignored) {
      }
    }

    if (messageId != null && !messageId.isBlank()) {
      out.add("ircv3_msgid");
    }

    if (ircv3Tags != null && !ircv3Tags.isEmpty()) {
      out.add("ircv3_tagged");
      for (String rawKey : ircv3Tags.keySet()) {
        String key = sanitizeTagForMeta(rawKey);
        if (!key.isEmpty()) out.add("ircv3_tag_" + key);
      }
    }

    return java.util.Set.copyOf(out);
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
    SimpleAttributeSet a = new SimpleAttributeSet(base);
    if (existing == null) return a;

    copyMetaAttr(existing, a, ChatStyles.ATTR_META_BUFFER_KEY);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_KIND);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_DIRECTION);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_FROM);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_TAGS);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_EPOCH_MS);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_MSGID);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_IRCV3_TAGS);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_PENDING_ID);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_PENDING_STATE);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_FILTER_RULE_ID);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_FILTER_RULE_NAME);
    copyMetaAttr(existing, a, ChatStyles.ATTR_META_FILTER_ACTION);
    return a;
  }

  private static void copyMetaAttr(AttributeSet src, javax.swing.text.MutableAttributeSet dst, Object key) {
    try {
      Object v = src.getAttribute(key);
      if (v != null) {
        dst.addAttribute(key, v);
      }
    } catch (Exception ignored) {
    }
  }

  private static String normalizeMessageId(String raw) {
    String s = (raw == null) ? "" : raw.trim();
    return s;
  }

  private static String normalizePendingId(String raw) {
    String s = (raw == null) ? "" : raw.trim();
    return s;
  }

  private static Map<String, String> normalizeIrcv3Tags(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
    for (Map.Entry<String, String> e : raw.entrySet()) {
      String key = normalizeIrcv3TagKey(e.getKey());
      if (key.isEmpty()) continue;
      String val = (e.getValue() == null) ? "" : e.getValue();
      out.put(key, val);
    }
    if (out.isEmpty()) return Map.of();
    return java.util.Collections.unmodifiableMap(out);
  }

  private static String normalizeIrcv3TagKey(String rawKey) {
    String k = (rawKey == null) ? "" : rawKey.trim();
    if (k.startsWith("@")) k = k.substring(1).trim();
    if (k.startsWith("+")) k = k.substring(1).trim();
    if (k.isEmpty()) return "";
    return k.toLowerCase(Locale.ROOT);
  }

  private static String firstIrcv3TagValue(Map<String, String> tags, String... keys) {
    if (tags == null || tags.isEmpty() || keys == null) return "";
    for (String key : keys) {
      String want = normalizeIrcv3TagKey(key);
      if (want.isEmpty()) continue;
      for (Map.Entry<String, String> e : tags.entrySet()) {
        String got = normalizeIrcv3TagKey(e.getKey());
        if (!want.equals(got)) continue;
        String raw = (e.getValue() == null) ? "" : e.getValue().trim();
        if (raw.isEmpty()) continue;
        return raw;
      }
    }
    return "";
  }

  private static String sanitizeTagForMeta(String rawKey) {
    String key = normalizeIrcv3TagKey(rawKey);
    if (key.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(key.length());
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    return sb.toString();
  }

  private static String formatIrcv3Tags(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : tags.entrySet()) {
      String key = normalizeIrcv3TagKey(e.getKey());
      if (key.isEmpty()) continue;
      if (sb.length() > 0) sb.append(';');
      sb.append(key);
      String val = (e.getValue() == null) ? "" : e.getValue();
      if (!val.isEmpty()) sb.append('=').append(val);
    }
    return sb.toString();
  }


  private boolean shouldHideLine(TargetRef ref, LogKind kind, LogDirection dir, String fromNick, String text, Set<String> tags) {
    if (ref == null) return false;
    if (filterEngine == null) return false;
    try {
      return filterEngine.shouldHide(new FilterContext(ref, kind, dir, fromNick, text, tags != null ? tags : Set.of()));
    } catch (Exception ignored) {
      return false;
    }
  }

  private FilterEngine.Match hideMatch(TargetRef ref, LogKind kind, LogDirection dir, String fromNick, String text, Set<String> tags) {
    if (ref == null) return null;
    if (filterEngine == null) return null;
    try {
      FilterEngine.Match m = filterEngine.firstMatch(
          new FilterContext(ref, kind, dir, fromNick, text, tags != null ? tags : Set.of())
      );
      if (m == null || !m.isHide()) return null;
      return m;
    } catch (Exception ignored) {
      return null;
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
   * <p>History loaders typically prepend many lines in a tight loop. We want filtered placeholders/hints
   * to group consecutive hidden lines within that loop, but we do <b>not</b> want a filtered run from a
   * previous load to keep growing across separate paging operations.
   *
   * <p>Call this once before a batch of {@code insert*FromHistoryAt(...)} calls.
   */
  public synchronized void beginHistoryInsertBatch(TargetRef ref) {
    if (ref == null) return;
    ensureTargetExists(ref);
    endFilteredInsertRun(ref);

    TranscriptState st = stateByTarget.get(ref);
    if (st != null) {
      st.historyInsertBatchActive = true;
      st.historyInsertPlaceholderRunsCreated = 0;
      st.historyInsertHintRunsCreated = 0;
      st.historyInsertOverflowRun = null;
    }
  }

  /**
   * Optional end-of-batch signal for history/backfill insertion.
   *
   * <p>Calling this is safe but not strictly required as long as callers invoke
   * {@link #beginHistoryInsertBatch(TargetRef)} before each subsequent batch.
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
    }
  }



  private void onFilteredLineAppend(TargetRef ref, String previewText, LineMeta hiddenMeta, FilterEngine.Match match) {
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
    if (comp != null && maxRun > 0
        && comp.count() >= maxRun) {
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
      try { comp.setMaxTagsInTooltip(eff.placeholderTooltipMaxTags()); } catch (Exception ignored) {}
      Font f = safeTranscriptFont();
      if (f != null) comp.setTranscriptFont(f);

      long tsEpochMs = (hiddenMeta != null && hiddenMeta.epochMs() != null && hiddenMeta.epochMs() > 0) ? hiddenMeta.epochMs() : System.currentTimeMillis();
      LineMeta meta = buildFilteredMeta(hiddenMeta, tsEpochMs, false, hiddenMeta != null ? hiddenMeta.tags() : null);

      try {
        int insertAt = doc.getLength();
        SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
        attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
        attachFilterMatch(attrs, match, false);
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
      updateFilteredRunAttributes(doc, run, false);
    }

    try {
      comp.addFilteredLine(previewText);
    } catch (Exception ignored) {
    }
  }

  private void onFilteredLineHintAppend(TargetRef ref, LineMeta hiddenMeta, FilterEngine.Match match) {
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
      try { comp.setMaxTagsInTooltip(filterEngine.effectiveFor(ref).placeholderTooltipMaxTags()); } catch (Exception ignored) {}
      Font f = safeTranscriptFont();
      if (f != null) comp.setTranscriptFont(f);

      long tsEpochMs = (hiddenMeta != null && hiddenMeta.epochMs() != null && hiddenMeta.epochMs() > 0) ? hiddenMeta.epochMs() : System.currentTimeMillis();
      LineMeta meta = buildFilteredMeta(hiddenMeta, tsEpochMs, true, hiddenMeta != null ? hiddenMeta.tags() : null);

      try {
        int insertAt = doc.getLength();
        SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
        attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
        attachFilterMatch(attrs, match, false);
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
      updateFilteredRunAttributes(doc, run, true);
    }

    try {
      comp.addFilteredLine();
    } catch (Exception ignored) {
    }
  }


  /**
   * History/backfill insertion path for filtered lines. Unlike {@link #onFilteredLineAppend},
   * this inserts the placeholder/hint row at the given insertion offset (typically the top of the
   * document when loading older messages).
   *
   * <p>We keep separate run-tracking for inserts so we don't accidentally "reuse" the live append
   * placeholder run (which would attach hidden lines to the wrong component).
   */
  private int onFilteredLineInsertAt(TargetRef ref, int insertAt, String previewText, LineMeta hiddenMeta, FilterEngine.Match match) {
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
    if (comp != null && maxRun > 0
        && comp.count() >= maxRun) {
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
      try { comp.setMaxTagsInTooltip(eff.placeholderTooltipMaxTags()); } catch (Exception ignored) {}
      Font f = safeTranscriptFont();
      if (f != null) comp.setTranscriptFont(f);

      long tsEpochMs = (hiddenMeta != null && hiddenMeta.epochMs() != null && hiddenMeta.epochMs() > 0) ? hiddenMeta.epochMs() : System.currentTimeMillis();
      LineMeta meta = buildFilteredMeta(hiddenMeta, tsEpochMs, false, hiddenMeta != null ? hiddenMeta.tags() : null);

      try {
        SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
        attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
        attachFilterMatch(attrs, match, false);
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
      updateFilteredRunAttributes(doc, run, false);
    }

    try {
      comp.addFilteredLine(previewText);
    } catch (Exception ignored) {
    }

    // Hidden lines don't take up any visible space beyond the placeholder itself,
    // so the insertion offset only advances when we had to create a new placeholder.
    return Math.max(0, insertAt);
  }

  private int onFilteredLineHintInsertAt(TargetRef ref, int insertAt, LineMeta hiddenMeta, FilterEngine.Match match) {
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
      try { comp.setMaxTagsInTooltip(eff.placeholderTooltipMaxTags()); } catch (Exception ignored) {}
      Font f = safeTranscriptFont();
      if (f != null) comp.setTranscriptFont(f);

      long tsEpochMs = (hiddenMeta != null && hiddenMeta.epochMs() != null && hiddenMeta.epochMs() > 0) ? hiddenMeta.epochMs() : System.currentTimeMillis();
      LineMeta meta = buildFilteredMeta(hiddenMeta, tsEpochMs, true, hiddenMeta != null ? hiddenMeta.tags() : null);

      try {
        SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
        attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
        attachFilterMatch(attrs, match, false);
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
      updateFilteredRunAttributes(doc, run, true);
    }

    try {
      comp.addFilteredLine();
    } catch (Exception ignored) {
    }

    return Math.max(0, insertAt);
  }

  /**
   * Step 5.2: When a history/backfill batch would create too many filtered placeholder/hint runs,
   * collapse the remainder into a single "overflow" summary row.
   */
  private int onFilteredOverflowInsertAt(TargetRef ref,
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
      try { comp.setMaxTagsInTooltip(eff.placeholderTooltipMaxTags()); } catch (Exception ignored) {}
      Font f = safeTranscriptFont();
      if (f != null) comp.setTranscriptFont(f);

      long tsEpochMs = (hiddenMeta != null && hiddenMeta.epochMs() != null && hiddenMeta.epochMs() > 0)
          ? hiddenMeta.epochMs()
          : System.currentTimeMillis();
      LineMeta meta = buildFilteredOverflowMeta(hiddenMeta, tsEpochMs, hiddenMeta != null ? hiddenMeta.tags() : null);

      try {
        SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
        attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
        attachFilterMatch(attrs, match, false);
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
      updateFilteredOverflowRunAttributes(doc, run);
    }

    try {
      comp.addFilteredLine();
    } catch (Exception ignored) {
    }

    return Math.max(0, insertAt);
  }

  private LineMeta buildFilteredOverflowMeta(LineMeta base, long tsEpochMs, Set<String> unionTags) {
    if (base == null) {
      base = new LineMeta("", LogKind.STATUS, LogDirection.SYSTEM, null, tsEpochMs, Set.of(), "", "");
    }

    java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>();
    if (unionTags != null && !unionTags.isEmpty()) {
      tags.addAll(unionTags);
    } else {
      tags.addAll(base.tags());
    }

    tags.add("irc_filtered");
    tags.add("irc_filtered_overflow");

    return new LineMeta(
        base.bufferKey(),
        base.kind(),
        base.direction(),
        base.fromNick(),
        tsEpochMs,
        Set.copyOf(tags),
        base.messageId(),
        base.ircv3Tags()
    );
  }

  private void updateFilteredOverflowRunAttributes(StyledDocument doc, FilteredOverflowRun run) {
    if (doc == null || run == null || run.pos == null || run.component == null) return;

    LineMeta base = run.lastHiddenMeta;
    if (base == null) return;

    long tsEpochMs = (base.epochMs() != null && base.epochMs() > 0) ? base.epochMs() : System.currentTimeMillis();
    LineMeta meta = buildFilteredOverflowMeta(base, tsEpochMs, run.unionTags);

    SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
    attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
    attachFilterMatch(attrs, run.primaryMatch, run.multiple);
    StyleConstants.setComponent(attrs, run.component);

    try {
      int off = run.pos.getOffset();
      doc.setCharacterAttributes(off, 1, attrs, true);
      if (off + 1 < doc.getLength()) {
        SimpleAttributeSet nl = withLineMeta(styles.timestamp(), meta);
        attachFilterMatch(nl, run.primaryMatch, run.multiple);
        doc.setCharacterAttributes(off + 1, 1, nl, true);
      }
    } catch (Exception ignored) {
    }

    try {
      boolean multiple = run.multiple;
      String ruleLabel;
      if (multiple) {
        ruleLabel = "(multiple)";
      } else if (run.primaryMatch != null && run.primaryMatch.ruleName() != null) {
        ruleLabel = String.valueOf(run.primaryMatch.ruleName());
      } else {
        ruleLabel = "(unknown)";
      }
      run.component.setFilterDetails(ruleLabel, multiple, run.unionTags);
    } catch (Exception ignored) {
    }
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

  private Font safeTranscriptFont() {
    try {
      if (uiSettings != null && uiSettings.get() != null) {
        UiSettings us = uiSettings.get();
        return new Font(
            us.chatFontFamily(),
            Font.PLAIN,
            us.chatFontSize()
        );
      }
    } catch (Exception ignored) {
    }
    return null;
  }



  private LineMeta buildFilteredMeta(LineMeta base, long tsEpochMs, boolean hint, Set<String> unionTags) {
    if (base == null) {
      // Fallback meta: should be rare (only if a caller fails to provide meta).
      base = new LineMeta("", LogKind.STATUS, LogDirection.SYSTEM, null, tsEpochMs, Set.of(), "", "");
    }

    java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>();
    if (unionTags != null && !unionTags.isEmpty()) {
      tags.addAll(unionTags);
    } else {
      tags.addAll(base.tags());
    }

    tags.add("irc_filtered");
    tags.add(hint ? "irc_filtered_hint" : "irc_filtered_placeholder");

    return new LineMeta(
        base.bufferKey(),
        base.kind(),
        base.direction(),
        base.fromNick(),
        tsEpochMs,
        Set.copyOf(tags),
        base.messageId(),
        base.ircv3Tags()
    );
  }

  private void attachFilterMatch(SimpleAttributeSet attrs, FilterEngine.Match match, boolean multiple) {
    if (attrs == null) return;

    String action = (match != null && match.action() != null) ? match.action().name() : "HIDE";
    attrs.addAttribute(ChatStyles.ATTR_META_FILTER_ACTION, action);
    attrs.addAttribute(ChatStyles.ATTR_META_FILTER_MULTIPLE, multiple ? Boolean.TRUE : Boolean.FALSE);

    if (multiple) {
      attrs.addAttribute(ChatStyles.ATTR_META_FILTER_RULE_NAME, "(multiple)");
      attrs.addAttribute(ChatStyles.ATTR_META_FILTER_RULE_ID, "");
      return;
    }

    if (match != null) {
      attrs.addAttribute(ChatStyles.ATTR_META_FILTER_RULE_NAME, String.valueOf(match.ruleName()));
      attrs.addAttribute(ChatStyles.ATTR_META_FILTER_RULE_ID, String.valueOf(match.ruleId()));
    } else {
      attrs.addAttribute(ChatStyles.ATTR_META_FILTER_RULE_NAME, "(unknown)");
      attrs.addAttribute(ChatStyles.ATTR_META_FILTER_RULE_ID, "");
    }
  }

  private void updateFilteredRunAttributes(StyledDocument doc, FilteredRun run, boolean hint) {
    if (doc == null || run == null || run.pos == null || run.component == null) return;

    LineMeta base = run.lastHiddenMeta;
    if (base == null) return;

    long tsEpochMs = (base.epochMs() != null && base.epochMs() > 0) ? base.epochMs() : System.currentTimeMillis();
    LineMeta meta = buildFilteredMeta(base, tsEpochMs, hint, run.unionTags);

    SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
    attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
    attachFilterMatch(attrs, run.primaryMatch, run.multiple);
    StyleConstants.setComponent(attrs, run.component);

    try {
      int off = run.pos.getOffset();
      doc.setCharacterAttributes(off, 1, attrs, true);
      // Also apply metadata to the newline that terminates this row, so the inspector
      // is reliable even if the user clicks just past the component.
      if (off + 1 < doc.getLength()) {
        SimpleAttributeSet nl = withLineMeta(styles.timestamp(), meta);
        attachFilterMatch(nl, run.primaryMatch, run.multiple);
        doc.setCharacterAttributes(off + 1, 1, nl, true);
      }
    } catch (Exception ignored) {
    }

    // Tooltip polish: show which rule filtered this run and a summary of tags.
    try {
      boolean multiple = run.multiple;
      String ruleLabel;
      if (multiple) {
        ruleLabel = "(multiple)";
      } else if (run.primaryMatch != null && run.primaryMatch.ruleName() != null) {
        ruleLabel = String.valueOf(run.primaryMatch.ruleName());
      } else {
        ruleLabel = "(unknown)";
      }
      run.component.setFilterDetails(ruleLabel, multiple, run.unionTags);
    } catch (Exception ignored) {
    }
  }

  private void updateFilteredRunAttributes(StyledDocument doc, FilteredHintRun run, boolean hint) {
    if (doc == null || run == null || run.pos == null || run.component == null) return;

    LineMeta base = run.lastHiddenMeta;
    if (base == null) return;

    long tsEpochMs = (base.epochMs() != null && base.epochMs() > 0) ? base.epochMs() : System.currentTimeMillis();
    LineMeta meta = buildFilteredMeta(base, tsEpochMs, hint, run.unionTags);

    SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
    attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
    attachFilterMatch(attrs, run.primaryMatch, run.multiple);
    StyleConstants.setComponent(attrs, run.component);

    try {
      int off = run.pos.getOffset();
      doc.setCharacterAttributes(off, 1, attrs, true);
      // Also apply metadata to the newline that terminates this row, so the inspector
      // is reliable even if the user clicks just past the component.
      if (off + 1 < doc.getLength()) {
        SimpleAttributeSet nl = withLineMeta(styles.timestamp(), meta);
        attachFilterMatch(nl, run.primaryMatch, run.multiple);
        doc.setCharacterAttributes(off + 1, 1, nl, true);
      }
    } catch (Exception ignored) {
    }

    // Tooltip polish: show which rule filtered this run and a summary of tags.
    try {
      boolean multiple = run.multiple;
      String ruleLabel;
      if (multiple) {
        ruleLabel = "(multiple)";
      } else if (run.primaryMatch != null && run.primaryMatch.ruleName() != null) {
        ruleLabel = String.valueOf(run.primaryMatch.ruleName());
      } else {
        ruleLabel = "(unknown)";
      }
      run.component.setFilterDetails(ruleLabel, multiple, run.unionTags);
    } catch (Exception ignored) {
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
    endFilteredRun(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return null;

    // This is a synthetic UI row; still attach line metadata so the inspector and
    // filtering UI remain consistent and reliable.
    LineMeta meta = buildLineMeta(ref, LogKind.STATUS, LogDirection.SYSTEM, null, System.currentTimeMillis(), null);

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
      SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
      StyleConstants.setComponent(attrs, comp);
      doc.insertString(insertPos, " ", attrs);
      Position pos = doc.createPosition(insertPos);
      doc.insertString(insertPos + 1, "\n", withLineMeta(styles.timestamp(), meta));
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

    // Synthetic UI row; keep metadata consistent so inspector behavior is stable.
    LineMeta meta = buildLineMeta(ref, LogKind.STATUS, LogDirection.SYSTEM, null, System.currentTimeMillis(), null);

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
      SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
      StyleConstants.setComponent(attrs, comp);
      doc.insertString(pos, " ", attrs);
      Position p = doc.createPosition(pos);
      doc.insertString(pos + 1, "\n", withLineMeta(styles.timestamp(), meta));
      if (st != null) {
        st.historyDivider = new HistoryDividerControl(p, comp);
      }
    } catch (Exception ignored) {
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
    return comp;
  }

  public synchronized void updateReadMarker(TargetRef ref, long markerEpochMs) {
    if (ref == null) return;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return;

    removeReadMarkerControl(ref, doc, st);

    long markerMs = markerEpochMs > 0 ? markerEpochMs : System.currentTimeMillis();
    int firstUnreadStart = findFirstUnreadLineStart(doc, markerMs);
    if (firstUnreadStart < 0) return;

    LineMeta meta = buildLineMeta(ref, LogKind.STATUS, LogDirection.SYSTEM, null, markerMs, null);

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, firstUnreadStart);
    pos = ensureAtLineStartForInsert(doc, pos);
    final int insertionStart = pos;

    HistoryDividerComponent comp = new HistoryDividerComponent("Unread");
    Font f = safeTranscriptFont();
    if (f != null) {
      try {
        comp.setTranscriptFont(f);
      } catch (Exception ignored) {
      }
    }

    try {
      SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
      StyleConstants.setComponent(attrs, comp);
      doc.insertString(pos, " ", attrs);
      Position p = doc.createPosition(pos);
      doc.insertString(pos + 1, "\n", withLineMeta(styles.timestamp(), meta));
      st.readMarker = new ReadMarkerControl(p, comp);
    } catch (Exception ignored) {
      st.readMarker = null;
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
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
    return findLineStartByMessageId(doc, msgId);
  }

  public synchronized void applyMessageReaction(
      TargetRef ref,
      String targetMessageId,
      String reaction,
      String fromNick,
      long tsEpochMs
  ) {
    if (ref == null) return;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    if (doc == null || st == null) return;
    applyMessageReactionInternal(ref, doc, st, targetMessageId, reaction, fromNick, tsEpochMs);
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

    String presenceFrom = null;
    try {
      presenceFrom = (event.kind() == PresenceKind.NICK) ? event.oldNick() : event.nick();
    } catch (Exception ignored) {
      presenceFrom = event.nick();
    }
    LineMeta meta = buildLineMeta(ref, LogKind.PRESENCE, LogDirection.SYSTEM, presenceFrom, System.currentTimeMillis(), event);

    FilterEngine.Match m = hideMatch(ref, LogKind.PRESENCE, LogDirection.SYSTEM, presenceFrom, event.displayText(), meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, event.displayText(), meta, m);
      return;
    }

    endFilteredRun(ref);

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
          doc.insertString(doc.getLength(), ts.prefixNow(), withLineMeta(styles.timestamp(), meta));
        }
        AttributeSet base = withLineMeta(styles.status(), meta);
        renderer.insertRichText(doc, ref, event.displayText(), base);
        doc.insertString(doc.getLength(), "\n", withLineMeta(styles.timestamp(), meta));
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
        doc.insertString(doc.getLength(), ts.prefixNow(), withLineMeta(styles.timestamp(), meta));
      }

      AttributeSet base = withLineMeta(styles.status(), meta);
      renderer.insertRichText(doc, ref, event.displayText(), base);
      doc.insertString(doc.getLength(), "\n", withLineMeta(styles.timestamp(), meta));
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
                                  LineMeta meta) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, (meta != null) ? meta.epochMs() : null);
    ensureAtLineStart(doc);

    Long epochMs = (meta != null) ? meta.epochMs() : null;
    AttributeSet tsStyle = withLineMeta(styles.timestamp(), meta);
    AttributeSet fromStyle2 = withLineMeta(fromStyle != null ? fromStyle : styles.from(), meta);
    AttributeSet msgStyle2 = withLineMeta(msgStyle != null ? msgStyle : styles.message(), meta);

    try {
      AttributeSet baseForId = msgStyle2;
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
        doc.insertString(doc.getLength(), prefix, tsStyle);
      }

      if (from != null && !from.isBlank()) {
        doc.insertString(doc.getLength(), from + ": ", fromStyle2);
      }

      AttributeSet base = msgStyle2;
      renderer.insertRichText(doc, ref, text, base);

      doc.insertString(doc.getLength(), "\n", withLineMeta(styles.timestamp(), meta));

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
    long tsEpochMs = System.currentTimeMillis();
    LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
    LineMeta meta = buildLineMeta(ref, LogKind.CHAT, dir, from, tsEpochMs, null);
    FilterEngine.Match m = hideMatch(ref, LogKind.CHAT, dir, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }

    breakPresenceRun(ref);

    AttributeSet fromStyle = styles.from();
    if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
      fromStyle = nickColors.forNick(from, fromStyle);
    }

    SimpleAttributeSet fs = withLineMeta(fromStyle, meta);
    SimpleAttributeSet ms = withLineMeta(styles.message(), meta);
    applyOutgoingLineColor(fs, ms, outgoingLocalEcho);

    appendLineInternal(ref, from, text, fs, ms, true, meta);
  }

  public void appendChatFromHistory(TargetRef ref,
                                    String from,
                                    String text,
                                    boolean outgoingLocalEcho,
                                    long tsEpochMs) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);

    LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
    LineMeta meta = buildLineMeta(ref, LogKind.CHAT, dir, from, tsEpochMs, null);
    FilterEngine.Match m = hideMatch(ref, LogKind.CHAT, dir, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }

    breakPresenceRun(ref);

    AttributeSet fromStyle = styles.from();
    if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
      fromStyle = nickColors.forNick(from, fromStyle);
    }

    SimpleAttributeSet fs = withLineMeta(fromStyle, meta);
    SimpleAttributeSet ms = withLineMeta(styles.message(), meta);
    applyOutgoingLineColor(fs, ms, outgoingLocalEcho);

    appendLineInternal(ref, from, text, fs, ms, false, meta);
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
  appendChatAt(ref, from, text, outgoingLocalEcho, tsEpochMs, "", Map.of());
}

public void appendChatAt(TargetRef ref,
                         String from,
                         String text,
                         boolean outgoingLocalEcho,
                         long tsEpochMs,
                         String messageId,
                         Map<String, String> ircv3Tags) {
  ensureTargetExists(ref);
  noteEpochMs(ref, tsEpochMs);

  LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
  LineMeta meta = buildLineMeta(ref, LogKind.CHAT, dir, from, tsEpochMs, null, messageId, ircv3Tags);
  FilterEngine.Match m = hideMatch(ref, LogKind.CHAT, dir, from, text, meta.tags());
  if (m != null) {
    onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
    return;
  }

  breakPresenceRun(ref);

  AttributeSet fromStyle = styles.from();
  if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
    fromStyle = nickColors.forNick(from, fromStyle);
  }

  SimpleAttributeSet fs = withLineMeta(fromStyle, meta);
  SimpleAttributeSet ms = withLineMeta(styles.message(), meta);
  applyOutgoingLineColor(fs, ms, outgoingLocalEcho);

  String normalizedMsgId = normalizeMessageId(messageId);
  String replyToMsgId = firstIrcv3TagValue(ircv3Tags, "draft/reply", "+draft/reply");
  String reactionToken = firstIrcv3TagValue(ircv3Tags, "draft/react", "+draft/react");
  if (!replyToMsgId.isBlank()) {
    appendReplyContextLine(ref, from, replyToMsgId, tsEpochMs);
  }

  appendLineInternal(ref, from, text, fs, ms, true, meta);

  StyledDocument doc = docs.get(ref);
  TranscriptState st = stateByTarget.get(ref);
  if (doc != null && st != null && !normalizedMsgId.isBlank()) {
    materializePendingReactionsForMessage(ref, doc, st, normalizedMsgId, tsEpochMs);
  }
  if (doc != null && st != null && !reactionToken.isBlank() && !replyToMsgId.isBlank()) {
    applyMessageReactionInternal(ref, doc, st, replyToMsgId, reactionToken, from, tsEpochMs);
  }
}

  public synchronized void appendPendingOutgoingChat(
      TargetRef ref,
      String pendingId,
      String from,
      String text,
      long tsEpochMs
  ) {
    if (ref == null) return;
    String pid = normalizePendingId(pendingId);
    if (pid.isEmpty()) return;

    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    breakPresenceRun(ref);

    LineMeta meta = buildLineMeta(ref, LogKind.CHAT, LogDirection.OUT, from, tsEpochMs, null);
    AttributeSet fromStyle = styles.from();
    if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
      fromStyle = nickColors.forNick(from, fromStyle);
    }

    SimpleAttributeSet fs = withLineMeta(fromStyle, meta);
    SimpleAttributeSet ms = withLineMeta(styles.message(), meta);
    applyOutgoingLineColor(fs, ms, true);
    fs.addAttribute(ChatStyles.ATTR_META_PENDING_ID, pid);
    fs.addAttribute(ChatStyles.ATTR_META_PENDING_STATE, "pending");
    ms.addAttribute(ChatStyles.ATTR_META_PENDING_ID, pid);
    ms.addAttribute(ChatStyles.ATTR_META_PENDING_STATE, "pending");

    appendLineInternal(ref, from, Objects.toString(text, "") + " [pending]", fs, ms, true, meta);
  }

  public synchronized boolean resolvePendingOutgoingChat(
      TargetRef ref,
      String pendingId,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    if (ref == null) return false;
    String pid = normalizePendingId(pendingId);
    if (pid.isEmpty()) return false;

    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return false;

    int lineStart = findLineStartByPendingId(doc, pid);
    if (lineStart < 0) return false;
    int lineEnd = lineEndOffsetForLineStart(doc, lineStart);
    try {
      doc.remove(lineStart, Math.max(0, lineEnd - lineStart));
    } catch (Exception ignored) {
      return false;
    }

    long ts = tsEpochMs > 0 ? tsEpochMs : System.currentTimeMillis();
    insertCanonicalOutgoingChatLineAt(ref, lineStart, from, text, ts, messageId, ircv3Tags);
    return true;
  }

  public synchronized boolean failPendingOutgoingChat(
      TargetRef ref,
      String pendingId,
      String from,
      String text,
      long tsEpochMs,
      String reason
  ) {
    if (ref == null) return false;
    String pid = normalizePendingId(pendingId);
    if (pid.isEmpty()) return false;

    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return false;

    int lineStart = findLineStartByPendingId(doc, pid);
    if (lineStart < 0) return false;
    int lineEnd = lineEndOffsetForLineStart(doc, lineStart);
    try {
      doc.remove(lineStart, Math.max(0, lineEnd - lineStart));
    } catch (Exception ignored) {
      return false;
    }

    long ts = tsEpochMs > 0 ? tsEpochMs : System.currentTimeMillis();
    insertFailedOutgoingChatLineAt(ref, lineStart, from, text, ts, reason);
    return true;
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

    LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
    LineMeta meta = buildLineMeta(ref, LogKind.CHAT, dir, from, tsEpochMs, null);
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

    AttributeSet fromStyle = styles.from();
    if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
      fromStyle = nickColors.forNick(from, fromStyle);
    }

    SimpleAttributeSet fs = withLineMeta(fromStyle, meta);
    SimpleAttributeSet ms = withLineMeta(styles.message(), meta);
    applyOutgoingLineColor(fs, ms, outgoingLocalEcho);

    return insertLineInternalAt(ref, insertAt, from, text, fs, ms, false, meta);
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

    LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
    LineMeta meta = buildLineMeta(ref, LogKind.ACTION, dir, from, tsEpochMs, null);
    FilterEngine.Match m = hideMatch(ref, LogKind.ACTION, dir, from, action, meta.tags());
    if (m != null) {
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
      boolean timestampsIncludeChatMessages = false;
      try {
        timestampsIncludeChatMessages = uiSettings != null
            && uiSettings.get() != null
            && uiSettings.get().timestampsIncludeChatMessages();
      } catch (Exception ignored) {
        timestampsIncludeChatMessages = false;
      }

      AttributeSet tsStyle = withLineMeta(styles.timestamp(), meta);

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

      doc.insertString(pos, "\n", tsStyle);
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
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);

    LineMeta meta = buildLineMeta(ref, LogKind.NOTICE, LogDirection.IN, from, tsEpochMs, null);
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

    return insertLineInternalAt(ref, insertAt, from, text,
        styles.noticeFrom(), styles.noticeMessage(), false, meta);
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
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);

    LineMeta meta = buildLineMeta(ref, LogKind.STATUS, LogDirection.SYSTEM, from, tsEpochMs, null);
    FilterEngine.Match m = hideMatch(ref, LogKind.STATUS, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      FilterEngine.Effective eff = filterEngine.effectiveFor(ref);
      if (eff.historyPlaceholdersEnabled()) {
        return onFilteredLineInsertAt(ref, insertAt, previewChatLine(from, text), meta, m);
      }
      // History placeholders disabled: silently drop filtered history lines.
      endFilteredInsertRun(ref);
      return Math.max(0, insertAt);
    }

    return insertLineInternalAt(ref, insertAt, from, text,
        styles.status(), styles.status(), false, meta);
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
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);

    LineMeta meta = buildLineMeta(ref, LogKind.ERROR, LogDirection.SYSTEM, from, tsEpochMs, null);
    FilterEngine.Match m = hideMatch(ref, LogKind.ERROR, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      FilterEngine.Effective eff = filterEngine.effectiveFor(ref);
      if (eff.historyPlaceholdersEnabled()) {
        return onFilteredLineInsertAt(ref, insertAt, previewChatLine(from, text), meta, m);
      }
      // History placeholders disabled: silently drop filtered history lines.
      endFilteredInsertRun(ref);
      return Math.max(0, insertAt);
    }

    return insertLineInternalAt(ref, insertAt, from, text,
        styles.error(), styles.error(), false, meta);
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
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, tsEpochMs);
    if (doc == null) return Math.max(0, insertAt);

    LineMeta meta = buildLineMeta(ref, LogKind.PRESENCE, LogDirection.SYSTEM, null, tsEpochMs, null);
    FilterEngine.Match m = hideMatch(ref, LogKind.PRESENCE, LogDirection.SYSTEM, null, displayText, meta.tags());
    if (m != null) {
      return onFilteredLineInsertAt(ref, insertAt, previewChatLine(null, displayText), meta, m);
    }

    return insertLineInternalAt(ref, insertAt, null, displayText,
        styles.status(), styles.status(), false, meta);
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

    LineMeta meta = buildLineMeta(ref, LogKind.SPOILER, LogDirection.IN, from, tsEpochMs, null);
    FilterEngine.Match m = hideMatch(ref, LogKind.SPOILER, LogDirection.IN, from, text, meta.tags());
    if (m != null) {
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

    SimpleAttributeSet attrs = withLineMeta(styles.message(), meta);
    StyleConstants.setComponent(attrs, comp);
    try {
      doc.insertString(offFinal, " ", attrs);
      final Position spoilerPos = doc.createPosition(offFinal);
      comp.setOnReveal(() -> revealSpoilerInPlace(refFinal, docFinal, spoilerPos, comp,
          tsPrefixFinal, fromFinal, msgFinal));
      doc.insertString(offFinal + 1, "\n", withLineMeta(styles.timestamp(), meta));
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
                                   LineMeta meta) {
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    noteEpochMs(ref, (meta != null) ? meta.epochMs() : null);
    if (doc == null) return Math.max(0, insertAt);

    // Visible history inserts should break any active filtered run created by prior hidden lines.
    endFilteredInsertRun(ref);

    int beforeLen = doc.getLength();
    int pos = normalizeInsertAtLineStart(doc, insertAt);
    pos = ensureAtLineStartForInsert(doc, pos);
    final int insertionStart = pos;

    Long epochMs = (meta != null) ? meta.epochMs() : null;
    AttributeSet tsStyle = withLineMeta(styles.timestamp(), meta);
    AttributeSet fromStyle2 = withLineMeta(fromStyle != null ? fromStyle : styles.from(), meta);
    AttributeSet msgStyle2 = withLineMeta(msgStyle != null ? msgStyle : styles.message(), meta);

    try {
      AttributeSet baseForId = msgStyle2;
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
        doc.insertString(pos, prefix, tsStyle);
        pos += prefix.length();
      }

      if (from != null && !from.isBlank()) {
        String prefix = from + ": ";
        doc.insertString(pos, prefix, fromStyle2);
        pos += prefix.length();
      }

      if (renderer != null) {
        pos = renderer.insertRichTextAt(doc, ref, text, msgStyle2, pos);
      } else {
        String t = text == null ? "" : text;
        doc.insertString(pos, t, msgStyle2);
        pos += t.length();
      }

      doc.insertString(pos, "\n", tsStyle);
      pos += 1;

      if (allowEmbeds) {
        // (Embeds are intentionally skipped here; rich inserts during history prefill can be expensive.)
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
        if (!isConversationLine(attrs)) continue;

        Long lineEpochMs = lineEpochMs(attrs);
        if (lineEpochMs == null) continue;
        if (lineEpochMs > markerEpochMs) return start;
      }
    } catch (Exception ignored) {
    }
    return -1;
  }

  private static boolean isConversationLine(AttributeSet attrs) {
    if (attrs == null) return false;
    Object rawKind = attrs.getAttribute(ChatStyles.ATTR_META_KIND);
    if (rawKind == null) return false;
    String kind = String.valueOf(rawKind).trim().toUpperCase(Locale.ROOT);
    return "CHAT".equals(kind)
        || "ACTION".equals(kind)
        || "NOTICE".equals(kind)
        || "SPOILER".equals(kind);
  }

  private static Long lineEpochMs(AttributeSet attrs) {
    if (attrs == null) return null;
    Object raw = attrs.getAttribute(ChatStyles.ATTR_META_EPOCH_MS);
    if (raw instanceof Number n) {
      return n.longValue();
    }
    if (raw == null) return null;
    try {
      String s = String.valueOf(raw).trim();
      if (s.isEmpty()) return null;
      return Long.parseLong(s);
    } catch (Exception ignored) {
      return null;
    }
  }

  private void appendReplyContextLine(TargetRef ref, String fromNick, String replyToMsgId, long tsEpochMs) {
    String targetMsgId = normalizeMessageId(replyToMsgId);
    if (targetMsgId.isEmpty()) return;
    ensureTargetExists(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return;

    ensureAtLineStart(doc);

    Map<String, String> tags = Map.of("draft/reply", targetMsgId);
    LineMeta meta = buildLineMeta(ref, LogKind.STATUS, LogDirection.SYSTEM, fromNick, tsEpochMs, null, targetMsgId, tags);
    AttributeSet tsStyle = withLineMeta(styles.timestamp(), meta);
    SimpleAttributeSet prefixStyle = withLineMeta(styles.status(), meta);
    prefixStyle.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
    SimpleAttributeSet msgRefStyle = withLineMeta(styles.link(), meta);
    msgRefStyle.addAttribute(ChatStyles.ATTR_MSG_REF, targetMsgId);

    String from = Objects.toString(fromNick, "").trim();
    String prefix = from.isEmpty() ? "-> Reply to " : ("-> " + from + " replied to ");

    try {
      if (ts != null && ts.enabled()) {
        doc.insertString(doc.getLength(), ts.prefixAt(tsEpochMs), tsStyle);
      }
      doc.insertString(doc.getLength(), prefix, prefixStyle);
      doc.insertString(doc.getLength(), targetMsgId, msgRefStyle);
      doc.insertString(doc.getLength(), "\n", tsStyle);
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
      long tsEpochMs
  ) {
    if (ref == null || doc == null || st == null) return;
    String targetMsgId = normalizeMessageId(targetMessageId);
    String reactionToken = Objects.toString(reaction, "").trim();
    String nick = Objects.toString(fromNick, "").trim();
    if (targetMsgId.isEmpty() || reactionToken.isEmpty() || nick.isEmpty()) return;

    ReactionState state = st.reactionsByTargetMsgId.computeIfAbsent(targetMsgId, k -> new ReactionState());
    state.observe(reactionToken, nick);
    if (state.control != null && state.control.component != null) {
      try {
        state.control.component.setReactions(state.reactionsSnapshot());
      } catch (Exception ignored) {
      }
      return;
    }

    int lineStart = findLineStartByMessageId(doc, targetMsgId);
    if (lineStart < 0) {
      return;
    }
    insertReactionControlForMessage(ref, doc, st, targetMsgId, lineStart, state, tsEpochMs);
  }

  private void materializePendingReactionsForMessage(
      TargetRef ref,
      StyledDocument doc,
      TranscriptState st,
      String messageId,
      long tsEpochMs
  ) {
    if (ref == null || doc == null || st == null) return;
    String msgId = normalizeMessageId(messageId);
    if (msgId.isEmpty()) return;

    ReactionState state = st.reactionsByTargetMsgId.get(msgId);
    if (state == null || state.isEmpty() || state.control != null) return;

    int lineStart = findLineStartByMessageId(doc, msgId);
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
      long tsEpochMs
  ) {
    if (ref == null || doc == null || st == null || state == null) return;
    if (state.control != null) return;

    int lineEnd = lineEndOffsetForLineStart(doc, messageLineStart);
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
    comp.setReactions(state.reactionsSnapshot());

    LineMeta meta = buildLineMeta(
        ref,
        LogKind.STATUS,
        LogDirection.SYSTEM,
        null,
        tsEpochMs,
        null,
        targetMsgId,
        Map.of("draft/react", "1"));

    try {
      SimpleAttributeSet attrs = withLineMeta(styles.status(), meta);
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);
      StyleConstants.setComponent(attrs, comp);
      doc.insertString(pos, " ", attrs);
      Position p = doc.createPosition(pos);
      doc.insertString(pos + 1, "\n", withLineMeta(styles.timestamp(), meta));
      state.control = new ReactionSummaryControl(p, comp);
    } catch (Exception ignored) {
      state.control = null;
    }

    int delta = doc.getLength() - beforeLen;
    shiftCurrentPresenceBlock(ref, insertionStart, delta);
  }

  private int findLineStartByMessageId(StyledDocument doc, String messageId) {
    if (doc == null) return -1;
    String want = normalizeMessageId(messageId);
    if (want.isEmpty()) return -1;
    try {
      Element root = doc.getDefaultRootElement();
      if (root == null) return -1;
      int len = doc.getLength();
      for (int i = 0; i < root.getElementCount(); i++) {
        Element line = root.getElement(i);
        if (line == null) continue;
        int start = Math.max(0, line.getStartOffset());
        if (start >= len) continue;
        AttributeSet attrs = doc.getCharacterElement(start).getAttributes();
        String got = Objects.toString(attrs.getAttribute(ChatStyles.ATTR_META_MSGID), "").trim();
        if (want.equals(got)) return start;
      }
    } catch (Exception ignored) {
    }
    return -1;
  }

  private int lineEndOffsetForLineStart(StyledDocument doc, int lineStart) {
    if (doc == null) return Math.max(0, lineStart);
    try {
      Element root = doc.getDefaultRootElement();
      if (root == null) return Math.max(0, lineStart);
      int idx = root.getElementIndex(Math.max(0, lineStart));
      Element line = root.getElement(idx);
      if (line == null) return Math.max(0, lineStart);
      int end = line.getEndOffset();
      return Math.max(0, Math.min(end, doc.getLength()));
    } catch (Exception ignored) {
      return Math.max(0, lineStart);
    }
  }

  private int findLineStartByPendingId(StyledDocument doc, String pendingId) {
    if (doc == null) return -1;
    String want = normalizePendingId(pendingId);
    if (want.isEmpty()) return -1;
    try {
      Element root = doc.getDefaultRootElement();
      if (root == null) return -1;
      int len = doc.getLength();
      for (int i = 0; i < root.getElementCount(); i++) {
        Element line = root.getElement(i);
        if (line == null) continue;
        int start = Math.max(0, line.getStartOffset());
        int end = Math.max(start, Math.min(line.getEndOffset(), len));
        if (start >= end) continue;
        for (int p = start; p < end; p++) {
          AttributeSet attrs = doc.getCharacterElement(p).getAttributes();
          String got = Objects.toString(attrs.getAttribute(ChatStyles.ATTR_META_PENDING_ID), "").trim();
          if (want.equals(got)) return start;
        }
      }
    } catch (Exception ignored) {
    }
    return -1;
  }

  private void insertCanonicalOutgoingChatLineAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    breakPresenceRun(ref);

    LineMeta meta = buildLineMeta(ref, LogKind.CHAT, LogDirection.OUT, from, tsEpochMs, null, messageId, ircv3Tags);

    AttributeSet fromStyle = styles.from();
    if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
      fromStyle = nickColors.forNick(from, fromStyle);
    }

    SimpleAttributeSet fs = withLineMeta(fromStyle, meta);
    SimpleAttributeSet ms = withLineMeta(styles.message(), meta);
    applyOutgoingLineColor(fs, ms, true);

    insertLineInternalAt(ref, insertAt, from, text, fs, ms, false, meta);

    StyledDocument doc = docs.get(ref);
    TranscriptState st = stateByTarget.get(ref);
    String normalizedMsgId = normalizeMessageId(messageId);
    String replyToMsgId = firstIrcv3TagValue(ircv3Tags, "draft/reply", "+draft/reply");
    String reactionToken = firstIrcv3TagValue(ircv3Tags, "draft/react", "+draft/react");
    if (doc != null && st != null && !normalizedMsgId.isBlank()) {
      materializePendingReactionsForMessage(ref, doc, st, normalizedMsgId, tsEpochMs);
    }
    if (doc != null && st != null && !reactionToken.isBlank() && !replyToMsgId.isBlank()) {
      applyMessageReactionInternal(ref, doc, st, replyToMsgId, reactionToken, from, tsEpochMs);
    }
  }

  private void insertFailedOutgoingChatLineAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      long tsEpochMs,
      String reason
  ) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    breakPresenceRun(ref);

    String msg = Objects.toString(text, "") + " " + renderPendingFailure(reason);
    LineMeta meta = buildLineMeta(ref, LogKind.CHAT, LogDirection.OUT, from, tsEpochMs, null);
    SimpleAttributeSet fs = withLineMeta(styles.error(), meta);
    SimpleAttributeSet ms = withLineMeta(styles.error(), meta);
    fs.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);
    ms.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);
    insertLineInternalAt(ref, insertAt, from, msg, fs, ms, false, meta);
  }

  private static String renderPendingFailure(String reason) {
    String r = Objects.toString(reason, "").trim();
    if (r.isEmpty()) return "[failed]";
    if (r.length() > 120) {
      r = r.substring(0, 117) + "...";
    }
    return "[failed: " + r + "]";
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
    LineMeta meta = buildLineMeta(ref, LogKind.SPOILER, LogDirection.IN, from, System.currentTimeMillis(), null);
    FilterEngine.Match m = hideMatch(ref, LogKind.SPOILER, LogDirection.IN, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
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

    SimpleAttributeSet attrs = withLineMeta(styles.message(), meta);
    StyleConstants.setComponent(attrs, comp);
    try {
      doc.insertString(offFinal, " ", attrs);
      final Position spoilerPos = doc.createPosition(offFinal);
      comp.setOnReveal(() -> revealSpoilerInPlace(refFinal, docFinal, spoilerPos, comp,
          tsPrefixFinal, fromFinal, msgFinal));

      doc.insertString(doc.getLength(), "\n", withLineMeta(styles.timestamp(), meta));
    } catch (Exception ignored) {
    }
  }

  public void appendSpoilerChatFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    LineMeta meta = buildLineMeta(ref, LogKind.SPOILER, LogDirection.IN, from, tsEpochMs, null);
    FilterEngine.Match m = hideMatch(ref, LogKind.SPOILER, LogDirection.IN, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
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

    SimpleAttributeSet attrs = withLineMeta(styles.message(), meta);
    StyleConstants.setComponent(attrs, comp);
    try {
      doc.insertString(offFinal, " ", attrs);

      final Position spoilerPos = doc.createPosition(offFinal);

      comp.setOnReveal(() -> revealSpoilerInPlace(refFinal, docFinal, spoilerPos, comp,
          tsPrefixFinal, fromFinal, msgFinal));

      doc.insertString(doc.getLength(), "\n", withLineMeta(styles.timestamp(), meta));
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
        String prefix = from + ": ";
        doc.insertString(pos, prefix, fromStyle);
        pos += prefix.length();
      }
      DefaultStyledDocument inner = new DefaultStyledDocument();
      try {
        if (renderer != null) {
          renderer.insertRichText(inner, ref, msg, new SimpleAttributeSet(msgStyle));
        } else {
          inner.insertString(0, msg, msgStyle);
        }
      } catch (Exception ignored2) {
        try {
          inner.remove(0, inner.getLength());
          inner.insertString(0, msg, msgStyle);
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
    appendActionInternal(ref, from, action, outgoingLocalEcho, true, null, "", Map.of());
  }

  public void appendActionFromHistory(TargetRef ref, String from, String action, boolean outgoingLocalEcho, long tsEpochMs) {
    appendActionInternal(ref, from, action, outgoingLocalEcho, false, tsEpochMs, "", Map.of());
  }
  /**
   * Append an action (/me) with a timestamp, allowing embeds.
   */
  public void appendActionAt(TargetRef ref,
                             String from,
                             String action,
                             boolean outgoingLocalEcho,
                             long tsEpochMs) {
    appendActionAt(ref, from, action, outgoingLocalEcho, tsEpochMs, "", Map.of());
  }

  public void appendActionAt(TargetRef ref,
                             String from,
                             String action,
                             boolean outgoingLocalEcho,
                             long tsEpochMs,
                             String messageId,
                             Map<String, String> ircv3Tags) {
    appendActionInternal(ref, from, action, outgoingLocalEcho, true, tsEpochMs, messageId, ircv3Tags);
  }

  private void appendActionInternal(TargetRef ref,
                                    String from,
                                    String action,
                                    boolean outgoingLocalEcho,
                                    boolean allowEmbeds,
                                    Long epochMs,
                                    String messageId,
                                    Map<String, String> ircv3Tags) {
    ensureTargetExists(ref);

    LogDirection dir = outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN;
    long tsEpochMs = epochMs != null ? epochMs : System.currentTimeMillis();
    noteEpochMs(ref, tsEpochMs);

    LineMeta meta = buildLineMeta(ref, LogKind.ACTION, dir, from, tsEpochMs, null, messageId, ircv3Tags);

    FilterEngine.Match m = hideMatch(ref, LogKind.ACTION, dir, from, action, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewActionLine(from, action), meta, m);
      return;
    }

    breakPresenceRun(ref);
    StyledDocument doc = docs.get(ref);
    if (doc == null) return;

    String normalizedMsgId = normalizeMessageId(messageId);
    String replyToMsgId = firstIrcv3TagValue(ircv3Tags, "draft/reply", "+draft/reply");
    String reactionToken = firstIrcv3TagValue(ircv3Tags, "draft/react", "+draft/react");
    if (!replyToMsgId.isBlank()) {
      appendReplyContextLine(ref, from, replyToMsgId, tsEpochMs);
    }

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

      AttributeSet tsStyle = withLineMeta(styles.timestamp(), meta);

      if (ts != null && ts.enabled() && timestampsIncludeChatMessages) {
        String prefix = ts.prefixAt(tsEpochMs);
        doc.insertString(doc.getLength(), prefix, tsStyle);
      }

      AttributeSet msgStyle = withLineMeta(styles.actionMessage(), meta);
      AttributeSet fromStyle = styles.actionFrom();

      if (from != null && !from.isBlank() && nickColors != null && nickColors.enabled()) {
        fromStyle = nickColors.forNick(from, fromStyle);
      }

      SimpleAttributeSet ms = new SimpleAttributeSet(msgStyle);
      SimpleAttributeSet fs = withLineMeta(fromStyle, meta);
      applyOutgoingLineColor(fs, ms, outgoingLocalEcho);

      doc.insertString(doc.getLength(), "* ", ms);
      if (from != null && !from.isBlank()) {
        doc.insertString(doc.getLength(), from, fs);
        doc.insertString(doc.getLength(), " ", ms);
      }

      if (renderer != null) {
        renderer.insertRichText(doc, ref, a, ms);
      } else {
        doc.insertString(doc.getLength(), a, ms);
      }

      doc.insertString(doc.getLength(), "\n", tsStyle);

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

    TranscriptState st = stateByTarget.get(ref);
    if (st != null && !normalizedMsgId.isBlank()) {
      materializePendingReactionsForMessage(ref, doc, st, normalizedMsgId, tsEpochMs);
    }
    if (st != null && !reactionToken.isBlank() && !replyToMsgId.isBlank()) {
      applyMessageReactionInternal(ref, doc, st, replyToMsgId, reactionToken, from, tsEpochMs);
    }
  }

  public void appendNotice(TargetRef ref, String from, String text) {
    LineMeta meta = buildLineMeta(ref, LogKind.NOTICE, LogDirection.IN, from, System.currentTimeMillis(), null);
    FilterEngine.Match m = hideMatch(ref, LogKind.NOTICE, LogDirection.IN, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, styles.noticeFrom(), styles.noticeMessage(), true, meta);
  }

  public void appendStatus(TargetRef ref, String from, String text) {
    LineMeta meta = buildLineMeta(ref, LogKind.STATUS, LogDirection.SYSTEM, from, System.currentTimeMillis(), null);
    FilterEngine.Match m = hideMatch(ref, LogKind.STATUS, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, styles.status(), styles.status(), true, meta);
  }

  public void appendError(TargetRef ref, String from, String text) {
    LineMeta meta = buildLineMeta(ref, LogKind.ERROR, LogDirection.SYSTEM, from, System.currentTimeMillis(), null);
    FilterEngine.Match m = hideMatch(ref, LogKind.ERROR, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, styles.error(), styles.error(), true, meta);
  }

  public void appendNoticeFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    LineMeta meta = buildLineMeta(ref, LogKind.NOTICE, LogDirection.IN, from, tsEpochMs, null);
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
    FilterEngine.Match m = hideMatch(ref, LogKind.STATUS, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, styles.status(), styles.status(), false, meta);
  }

  public void appendErrorFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    LineMeta meta = buildLineMeta(ref, LogKind.ERROR, LogDirection.SYSTEM, from, tsEpochMs, null);
    FilterEngine.Match m = hideMatch(ref, LogKind.ERROR, LogDirection.SYSTEM, from, text, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, from, text, styles.error(), styles.error(), false, meta);
  }
/**
 * Append a notice with a timestamp, allowing embeds.
 */
public void appendNoticeAt(TargetRef ref, String from, String text, long tsEpochMs) {
  appendNoticeAt(ref, from, text, tsEpochMs, "", Map.of());
}

public void appendNoticeAt(
    TargetRef ref,
    String from,
    String text,
    long tsEpochMs,
    String messageId,
    Map<String, String> ircv3Tags
) {
  ensureTargetExists(ref);
  noteEpochMs(ref, tsEpochMs);
  LineMeta meta = buildLineMeta(ref, LogKind.NOTICE, LogDirection.IN, from, tsEpochMs, null, messageId, ircv3Tags);
  FilterEngine.Match m = hideMatch(ref, LogKind.NOTICE, LogDirection.IN, from, text, meta.tags());
  if (m != null) {
    onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
    return;
  }
  breakPresenceRun(ref);
  String normalizedMsgId = normalizeMessageId(messageId);
  String replyToMsgId = firstIrcv3TagValue(ircv3Tags, "draft/reply", "+draft/reply");
  String reactionToken = firstIrcv3TagValue(ircv3Tags, "draft/react", "+draft/react");
  if (!replyToMsgId.isBlank()) {
    appendReplyContextLine(ref, from, replyToMsgId, tsEpochMs);
  }
  appendLineInternal(ref, from, text, styles.noticeFrom(), styles.noticeMessage(), true, meta);
  StyledDocument doc = docs.get(ref);
  TranscriptState st = stateByTarget.get(ref);
  if (doc != null && st != null && !normalizedMsgId.isBlank()) {
    materializePendingReactionsForMessage(ref, doc, st, normalizedMsgId, tsEpochMs);
  }
  if (doc != null && st != null && !reactionToken.isBlank() && !replyToMsgId.isBlank()) {
    applyMessageReactionInternal(ref, doc, st, replyToMsgId, reactionToken, from, tsEpochMs);
  }
}

/**
 * Append a status line with a timestamp, allowing embeds.
 */
public void appendStatusAt(TargetRef ref, String from, String text, long tsEpochMs) {
  appendStatusAt(ref, from, text, tsEpochMs, "", Map.of());
}

public void appendStatusAt(
    TargetRef ref,
    String from,
    String text,
    long tsEpochMs,
    String messageId,
    Map<String, String> ircv3Tags
) {
  ensureTargetExists(ref);
  noteEpochMs(ref, tsEpochMs);
  LineMeta meta = buildLineMeta(ref, LogKind.STATUS, LogDirection.SYSTEM, from, tsEpochMs, null, messageId, ircv3Tags);
  FilterEngine.Match m = hideMatch(ref, LogKind.STATUS, LogDirection.SYSTEM, from, text, meta.tags());
  if (m != null) {
    onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
    return;
  }
  breakPresenceRun(ref);
  appendLineInternal(ref, from, text, styles.status(), styles.status(), true, meta);
}

/**
 * Append an error line with a timestamp, allowing embeds.
 */
public void appendErrorAt(TargetRef ref, String from, String text, long tsEpochMs) {
  ensureTargetExists(ref);
  noteEpochMs(ref, tsEpochMs);
  LineMeta meta = buildLineMeta(ref, LogKind.ERROR, LogDirection.SYSTEM, from, tsEpochMs, null);
  FilterEngine.Match m = hideMatch(ref, LogKind.ERROR, LogDirection.SYSTEM, from, text, meta.tags());
  if (m != null) {
    onFilteredLineAppend(ref, previewChatLine(from, text), meta, m);
    return;
  }
  breakPresenceRun(ref);
  appendLineInternal(ref, from, text, styles.error(), styles.error(), true, meta);
}
  public void appendPresenceFromHistory(TargetRef ref, String displayText, long tsEpochMs) {
    ensureTargetExists(ref);
    noteEpochMs(ref, tsEpochMs);
    LineMeta meta = buildLineMeta(ref, LogKind.PRESENCE, LogDirection.SYSTEM, null, tsEpochMs, null);
    FilterEngine.Match m = hideMatch(ref, LogKind.PRESENCE, LogDirection.SYSTEM, null, displayText, meta.tags());
    if (m != null) {
      onFilteredLineAppend(ref, displayText, meta, m);
      return;
    }
    breakPresenceRun(ref);
    appendLineInternal(ref, null, displayText, styles.status(), styles.status(), false, meta);
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

  private void foldBlock(StyledDocument doc, TargetRef ref, PresenceBlock block) {
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

      SimpleAttributeSet attrs = withExistingMeta(styles.status(), existingAttrs);
      StyleConstants.setComponent(attrs, comp);
      attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_STATUS);

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

    LoadOlderControl loadOlderControl;
    HistoryDividerControl historyDivider;
    ReadMarkerControl readMarker;
    Map<String, ReactionState> reactionsByTargetMsgId = new HashMap<>();
  }


  
  /** Tracks a contiguous run of filtered lines (represented by a single placeholder component). */
  private static final class FilteredRun {
    final Position pos;
    final FilteredFoldComponent component;

    FilterEngine.Match primaryMatch;
    boolean multiple;

    LineMeta lastHiddenMeta;
    final java.util.LinkedHashSet<String> unionTags = new java.util.LinkedHashSet<>();

    private FilteredRun(Position pos, FilteredFoldComponent component) {
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
        if (primaryMatch.ruleId() != null && m.ruleId() != null && !primaryMatch.ruleId().equals(m.ruleId())) {
          multiple = true;
        }
      } catch (Exception ignored) {
        multiple = true;
      }
    }
  }

  /** Tracks a contiguous run of filtered lines when placeholders are disabled (shown as a tiny hint row). */
  private static final class FilteredHintRun {
    final Position pos;
    final FilteredHintComponent component;

    FilterEngine.Match primaryMatch;
    boolean multiple;

    LineMeta lastHiddenMeta;
    final java.util.LinkedHashSet<String> unionTags = new java.util.LinkedHashSet<>();

    private FilteredHintRun(Position pos, FilteredHintComponent component) {
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
        if (primaryMatch.ruleId() != null && m.ruleId() != null && !primaryMatch.ruleId().equals(m.ruleId())) {
          multiple = true;
        }
      } catch (Exception ignored) {
        multiple = true;
      }
    }
  }

  /** Tracks the aggregated overflow row used once the history placeholder/hint run cap is exceeded. */
  private static final class FilteredOverflowRun {
    final Position pos;
    final FilteredOverflowComponent component;

    FilterEngine.Match primaryMatch;
    boolean multiple;

    LineMeta lastHiddenMeta;
    final java.util.LinkedHashSet<String> unionTags = new java.util.LinkedHashSet<>();

    private FilteredOverflowRun(Position pos, FilteredOverflowComponent component) {
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
        if (primaryMatch.ruleId() != null && m.ruleId() != null && !primaryMatch.ruleId().equals(m.ruleId())) {
          multiple = true;
        }
      } catch (Exception ignored) {
        multiple = true;
      }
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
    final Position pos;
    final HistoryDividerComponent component;

    private HistoryDividerControl(Position pos, HistoryDividerComponent component) {
      this.pos = pos;
      this.component = component;
    }
  }

  private static final class ReadMarkerControl {
    final Position pos;
    final HistoryDividerComponent component;

    private ReadMarkerControl(Position pos, HistoryDividerComponent component) {
      this.pos = pos;
      this.component = component;
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
      String token = Objects.toString(reaction, "").trim();
      String n = Objects.toString(nick, "").trim();
      if (token.isEmpty() || n.isEmpty()) return;
      nicksByReaction.computeIfAbsent(token, k -> new LinkedHashSet<>()).add(n);
    }

    boolean isEmpty() {
      return nicksByReaction.isEmpty();
    }

    Map<String, Collection<String>> reactionsSnapshot() {
      LinkedHashMap<String, Collection<String>> out = new LinkedHashMap<>();
      for (Map.Entry<String, LinkedHashSet<String>> e : nicksByReaction.entrySet()) {
        out.put(e.getKey(), List.copyOf(e.getValue()));
      }
      return out;
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
      Object msgRef = old.getAttribute(ChatStyles.ATTR_MSG_REF);
      if (msgRef != null) {
        fresh.addAttribute(ChatStyles.ATTR_MSG_REF, msgRef);
      }
      Object pendingId = old.getAttribute(ChatStyles.ATTR_META_PENDING_ID);
      if (pendingId != null) {
        fresh.addAttribute(ChatStyles.ATTR_META_PENDING_ID, pendingId);
      }
      Object pendingState = old.getAttribute(ChatStyles.ATTR_META_PENDING_STATE);
      if (pendingState != null) {
        fresh.addAttribute(ChatStyles.ATTR_META_PENDING_STATE, pendingState);
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

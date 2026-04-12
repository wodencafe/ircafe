package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.FilterAction;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.ui.chat.fold.FilteredHintComponent;
import cafe.woden.ircclient.ui.filter.FilterEngine;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.Test;

class ChatTranscriptFilteredRunSupportTest {

  @Test
  void buildFilteredMetaAddsFilteredTagsAndPreservesBaseFields() {
    ChatTranscriptStore.LineMeta base =
        lineMeta(
            "buffer",
            LogKind.CHAT,
            LogDirection.IN,
            "alice",
            123L,
            Set.of("irc_in"),
            "m-1",
            "msgid=m-1",
            Map.of("msgid", "m-1"));

    ChatTranscriptStore.LineMeta filtered =
        ChatTranscriptFilteredRunSupport.buildFilteredMeta(
            base, 456L, true, Set.of("tag_alpha", "tag_beta"));

    assertEquals("buffer", filtered.bufferKey());
    assertEquals(LogKind.CHAT, filtered.kind());
    assertEquals(LogDirection.IN, filtered.direction());
    assertEquals("alice", filtered.fromNick());
    assertEquals(456L, filtered.epochMs());
    assertEquals("m-1", filtered.messageId());
    assertTrue(filtered.tags().contains("tag_alpha"));
    assertTrue(filtered.tags().contains("tag_beta"));
    assertTrue(filtered.tags().contains("irc_filtered"));
    assertTrue(filtered.tags().contains("irc_filtered_hint"));
  }

  @Test
  void buildFilteredOverflowMetaAddsOverflowTag() {
    ChatTranscriptStore.LineMeta filtered =
        ChatTranscriptFilteredRunSupport.buildFilteredOverflowMeta(
            null, 789L, Set.of("tag_overflow"));

    assertEquals(LogKind.STATUS, filtered.kind());
    assertEquals(LogDirection.SYSTEM, filtered.direction());
    assertEquals(789L, filtered.epochMs());
    assertTrue(filtered.tags().contains("tag_overflow"));
    assertTrue(filtered.tags().contains("irc_filtered"));
    assertTrue(filtered.tags().contains("irc_filtered_overflow"));
  }

  @Test
  void attachFilterMatchPopulatesRuleMetadataAndMultipleFallback() {
    UUID ruleId = UUID.randomUUID();
    FilterEngine.Match match = new FilterEngine.Match(ruleId, "Rule A", FilterAction.DIM);

    SimpleAttributeSet attrs = new SimpleAttributeSet();
    ChatTranscriptFilteredRunSupport.attachFilterMatch(attrs, match, false);

    assertEquals("DIM", attrs.getAttribute(ChatStyles.ATTR_META_FILTER_ACTION));
    assertEquals(Boolean.FALSE, attrs.getAttribute(ChatStyles.ATTR_META_FILTER_MULTIPLE));
    assertEquals("Rule A", attrs.getAttribute(ChatStyles.ATTR_META_FILTER_RULE_NAME));
    assertEquals(ruleId.toString(), attrs.getAttribute(ChatStyles.ATTR_META_FILTER_RULE_ID));

    SimpleAttributeSet multiple = new SimpleAttributeSet();
    ChatTranscriptFilteredRunSupport.attachFilterMatch(multiple, match, true);

    assertEquals("DIM", multiple.getAttribute(ChatStyles.ATTR_META_FILTER_ACTION));
    assertEquals(Boolean.TRUE, multiple.getAttribute(ChatStyles.ATTR_META_FILTER_MULTIPLE));
    assertEquals("(multiple)", multiple.getAttribute(ChatStyles.ATTR_META_FILTER_RULE_NAME));
    assertEquals("", multiple.getAttribute(ChatStyles.ATTR_META_FILTER_RULE_ID));
  }

  @Test
  void updateFilteredRunAttributesWritesDocumentMetadataAndTooltipContext() throws Exception {
    ChatStyles styles = new ChatStyles(null);
    ChatTranscriptFilteredRunSupport.Context context =
        new ChatTranscriptFilteredRunSupport.Context(
            styles, ChatTranscriptFilteredRunSupportTest::withLineMeta);
    DefaultStyledDocument doc = new DefaultStyledDocument();
    doc.insertString(0, " \n", new SimpleAttributeSet());
    Position pos = doc.createPosition(0);

    FilteredHintComponent component = new FilteredHintComponent();
    component.addFilteredLine();
    TestFilteredRun run = new TestFilteredRun(pos, component);
    run.observe(
        new FilterEngine.Match(UUID.randomUUID(), "Rule B", FilterAction.HIDE),
        lineMeta(
            "buffer",
            LogKind.CHAT,
            LogDirection.IN,
            "alice",
            999L,
            Set.of("tag_alpha", "tag_beta"),
            "m-2",
            "msgid=m-2",
            Map.of("msgid", "m-2")));

    ChatTranscriptFilteredRunSupport.updateFilteredRunAttributes(context, doc, run, true);

    AttributeSet componentAttrs = doc.getCharacterElement(0).getAttributes();
    assertEquals(ChatStyles.STYLE_STATUS, componentAttrs.getAttribute(ChatStyles.ATTR_STYLE));
    assertEquals("HIDE", componentAttrs.getAttribute(ChatStyles.ATTR_META_FILTER_ACTION));
    assertEquals("Rule B", componentAttrs.getAttribute(ChatStyles.ATTR_META_FILTER_RULE_NAME));
    assertEquals(component, StyleConstants.getComponent(componentAttrs));

    AttributeSet newlineAttrs = doc.getCharacterElement(1).getAttributes();
    assertEquals("HIDE", newlineAttrs.getAttribute(ChatStyles.ATTR_META_FILTER_ACTION));
    assertEquals("Rule B", newlineAttrs.getAttribute(ChatStyles.ATTR_META_FILTER_RULE_NAME));

    String tooltip = component.getToolTipText();
    assertTrue(tooltip.contains("Rule B"));
    assertTrue(tooltip.contains("tag_alpha"));
    assertTrue(tooltip.contains("Hidden lines: 1"));
  }

  private static ChatTranscriptStore.LineMeta lineMeta(
      String bufferKey,
      LogKind kind,
      LogDirection direction,
      String fromNick,
      Long epochMs,
      Set<String> tags,
      String messageId,
      String ircv3Tags,
      Map<String, String> ircv3TagsMap) {
    return new ChatTranscriptStore.LineMeta(
        bufferKey, kind, direction, fromNick, epochMs, tags, messageId, ircv3Tags, ircv3TagsMap);
  }

  private static SimpleAttributeSet withLineMeta(
      AttributeSet base, ChatTranscriptStore.LineMeta meta) {
    SimpleAttributeSet attrs = new SimpleAttributeSet(base);
    if (meta == null) {
      return attrs;
    }
    if (meta.bufferKey() != null && !meta.bufferKey().isBlank()) {
      attrs.addAttribute(ChatStyles.ATTR_META_BUFFER_KEY, meta.bufferKey());
    }
    if (meta.kind() != null) {
      attrs.addAttribute(ChatStyles.ATTR_META_KIND, meta.kind().name());
    }
    if (meta.direction() != null) {
      attrs.addAttribute(ChatStyles.ATTR_META_DIRECTION, meta.direction().name());
    }
    if (meta.fromNick() != null && !meta.fromNick().isBlank()) {
      attrs.addAttribute(ChatStyles.ATTR_META_FROM, meta.fromNick());
    }
    if (meta.epochMs() != null) {
      attrs.addAttribute(ChatStyles.ATTR_META_EPOCH_MS, meta.epochMs());
    }
    if (meta.messageId() != null && !meta.messageId().isBlank()) {
      attrs.addAttribute(ChatStyles.ATTR_META_MSGID, meta.messageId());
    }
    return attrs;
  }

  private static final class TestFilteredRun
      extends ChatTranscriptStore.AbstractFilteredRun<FilteredHintComponent> {
    private TestFilteredRun(Position pos, FilteredHintComponent component) {
      super(pos, component);
    }
  }
}

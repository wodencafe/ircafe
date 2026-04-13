package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import java.awt.Color;
import java.util.Map;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class ChatTranscriptLineMetaSupportTest {

  @Test
  void withExistingMetaCopiesTranscriptMetadataKeys() {
    SimpleAttributeSet existing = new SimpleAttributeSet();
    existing.addAttribute(ChatStyles.ATTR_META_BUFFER_KEY, "buffer");
    existing.addAttribute(ChatStyles.ATTR_META_MSGID, "m-1");
    existing.addAttribute(ChatStyles.ATTR_META_PENDING_ID, "pending-1");
    existing.addAttribute(ChatStyles.ATTR_META_FILTER_ACTION, "DIM");
    existing.addAttribute(ChatStyles.ATTR_META_AUX_ROW_KIND, "read-marker");
    existing.addAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG, Color.YELLOW);

    SimpleAttributeSet copied =
        ChatTranscriptLineMetaSupport.withExistingMeta(new SimpleAttributeSet(), existing);

    assertEquals("buffer", copied.getAttribute(ChatStyles.ATTR_META_BUFFER_KEY));
    assertEquals("m-1", copied.getAttribute(ChatStyles.ATTR_META_MSGID));
    assertEquals("pending-1", copied.getAttribute(ChatStyles.ATTR_META_PENDING_ID));
    assertEquals("DIM", copied.getAttribute(ChatStyles.ATTR_META_FILTER_ACTION));
    assertEquals("read-marker", copied.getAttribute(ChatStyles.ATTR_META_AUX_ROW_KIND));
    assertEquals(Color.YELLOW, copied.getAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG));
  }

  @Test
  void copyRestyleMetaAttrsNormalizesFilterActionAndSkipsInvalidRuleBackground() {
    SimpleAttributeSet existing = new SimpleAttributeSet();
    existing.addAttribute(ChatStyles.ATTR_META_FILTER_ACTION, "HIGHLIGHT");
    existing.addAttribute(ChatStyles.ATTR_META_AUX_ROW_KIND, " read-marker ");
    existing.addAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG, "not-a-color");
    SimpleAttributeSet fresh = new SimpleAttributeSet();

    ChatTranscriptLineMetaSupport.copyRestyleMetaAttrs(existing, fresh);

    assertEquals("highlight", fresh.getAttribute(ChatStyles.ATTR_META_FILTER_ACTION));
    assertEquals("read-marker", fresh.getAttribute(ChatStyles.ATTR_META_AUX_ROW_KIND));
    assertNull(fresh.getAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG));
  }

  @Test
  void planReplacementPrefersExistingEpochAndMsgIdAndMergesTags() {
    SimpleAttributeSet existing = new SimpleAttributeSet();
    existing.addAttribute(ChatStyles.ATTR_META_KIND, "chat");
    existing.addAttribute(ChatStyles.ATTR_META_DIRECTION, "out");
    existing.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);
    existing.addAttribute(ChatStyles.ATTR_META_FROM, "alice");
    existing.addAttribute(ChatStyles.ATTR_META_EPOCH_MS, 1_234L);
    existing.addAttribute(ChatStyles.ATTR_META_MSGID, "m-existing");
    existing.addAttribute(ChatStyles.ATTR_META_IRCV3_TAGS, "label = base ; draft/reply = old");

    ChatTranscriptLineMetaSupport.ReplacementPlan plan =
        ChatTranscriptLineMetaSupport.planReplacement(
            existing,
            9_999L,
            "m-replacement",
            Map.of("+draft/reply", "new-target", "+label", "override"),
            () -> 42L);

    assertNotNull(plan);
    assertEquals(LogKind.CHAT, plan.kind());
    assertEquals(LogDirection.OUT, plan.direction());
    assertEquals("alice", plan.fromNick());
    assertTrue(plan.outgoingLocalEcho());
    assertEquals(1_234L, plan.epochMs());
    assertEquals("m-existing", plan.messageIdForMeta());
    assertEquals("override", plan.mergedTags().get("label"));
    assertEquals("new-target", plan.mergedTags().get("draft/reply"));
    assertEquals("m-replacement", plan.mergedTags().get("msgid"));
  }

  @Test
  void planReplacementRejectsUnsupportedKinds() {
    SimpleAttributeSet existing = new SimpleAttributeSet();
    existing.addAttribute(ChatStyles.ATTR_META_KIND, "status");

    ChatTranscriptLineMetaSupport.ReplacementPlan plan =
        ChatTranscriptLineMetaSupport.planReplacement(existing, 0L, "", Map.of(), () -> 42L);

    assertNull(plan);
  }

  @Test
  void planReplacementUsesFallbackClockAndReplacementMsgIdWhenNeeded() {
    SimpleAttributeSet existing = new SimpleAttributeSet();
    existing.addAttribute(ChatStyles.ATTR_META_KIND, "notice");
    existing.addAttribute(ChatStyles.ATTR_META_DIRECTION, "in");

    ChatTranscriptLineMetaSupport.ReplacementPlan plan =
        ChatTranscriptLineMetaSupport.planReplacement(
            existing, 0L, " m-2 ", Map.of("label", "x"), () -> 42L);

    assertNotNull(plan);
    assertEquals(42L, plan.epochMs());
    assertEquals("m-2", plan.messageIdForMeta());
    assertFalse(plan.outgoingLocalEcho());
    assertEquals("x", plan.mergedTags().get("label"));
    assertEquals("m-2", plan.mergedTags().get("msgid"));
  }
}

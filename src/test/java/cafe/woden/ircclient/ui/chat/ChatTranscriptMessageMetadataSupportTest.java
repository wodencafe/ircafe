package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatTranscriptMessageMetadataSupportTest {

  @Test
  void normalizeIdsTrimWhitespaceAndTreatNullAsEmpty() {
    assertEquals("", ChatTranscriptMessageMetadataSupport.normalizeMessageId(null));
    assertEquals("m-1", ChatTranscriptMessageMetadataSupport.normalizeMessageId("  m-1  "));
    assertEquals("", ChatTranscriptMessageMetadataSupport.normalizePendingId(null));
    assertEquals(
        "pending-1", ChatTranscriptMessageMetadataSupport.normalizePendingId(" pending-1 "));
  }

  @Test
  void normalizeTagsCanonicalizesKeysAndPreservesInsertionOrder() {
    LinkedHashMap<String, String> raw = new LinkedHashMap<>();
    raw.put(" @MsgId ", "m-1");
    raw.put("+draft/reply", "m-0");
    raw.put(" ", "ignored");
    raw.put("Vendor/Thing", "value");

    Map<String, String> normalized = ChatTranscriptMessageMetadataSupport.normalizeIrcv3Tags(raw);

    assertEquals(Map.of("msgid", "m-1", "draft/reply", "m-0", "vendor/thing", "value"), normalized);
    assertEquals(
        List.of("msgid", "draft/reply", "vendor/thing"), normalized.keySet().stream().toList());
  }

  @Test
  void firstTagValueMatchesNormalizedKeyAndReturnsTrimmedValue() {
    String value =
        ChatTranscriptMessageMetadataSupport.firstIrcv3TagValue(
            Map.of("MsgId", "m-1", "+draft/reply", "  target-42  "), "@draft/reply", "msgid");

    assertEquals("target-42", value);
  }

  @Test
  void sanitizeAndFormatTagsUseCanonicalKeys() {
    LinkedHashMap<String, String> tags = new LinkedHashMap<>();
    tags.put(" MsgId ", "m-1");
    tags.put("+draft/reply", "target-42");
    tags.put("@flag", "");

    assertEquals(
        "draft_reply", ChatTranscriptMessageMetadataSupport.sanitizeTagForMeta("+Draft/Reply"));
    assertEquals(
        "msgid=m-1;draft/reply=target-42;flag",
        ChatTranscriptMessageMetadataSupport.formatIrcv3Tags(tags));
  }

  @Test
  void parseAndMergeTagDisplaysNormalizeOverlayKeys() {
    Map<String, String> parsed =
        ChatTranscriptMessageMetadataSupport.parseIrcv3TagsDisplay(
            " msgid = m-1 ; draft/reply = target-42 ; bare ");
    Map<String, String> merged =
        ChatTranscriptMessageMetadataSupport.mergeIrcv3Tags(
            Map.of("MsgId", "old", "label", "base"), Map.of("+msgid", "m-1", "@flag", ""));

    assertEquals(Map.of("msgid", "m-1", "draft/reply", "target-42", "bare", ""), parsed);
    assertEquals(Map.of("msgid", "m-1", "label", "base", "flag", ""), merged);
    assertTrue(merged.containsKey("msgid"));
  }
}

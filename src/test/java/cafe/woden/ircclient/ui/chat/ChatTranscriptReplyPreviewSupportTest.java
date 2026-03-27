package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.model.LogKind;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatTranscriptReplyPreviewSupportTest {

  @Test
  void previewForMessageIdUsesNormalizedIds() {
    assertEquals(
        "alice: hello",
        ChatTranscriptReplyPreviewSupport.previewForMessageId(
            Map.of("m-1", "alice: hello"), "  m-1  "));
  }

  @Test
  void formatReplyPreviewSnippetMatchesChatActionAndNoticeShapes() {
    assertEquals(
        "alice: hello there",
        ChatTranscriptReplyPreviewSupport.formatReplyPreviewSnippet(
            LogKind.CHAT, "alice", "hello there", 120));
    assertEquals(
        "* alice waves",
        ChatTranscriptReplyPreviewSupport.formatReplyPreviewSnippet(
            LogKind.ACTION, "alice", "waves", 120));
    assertEquals(
        "[notice] server: maintenance",
        ChatTranscriptReplyPreviewSupport.formatReplyPreviewSnippet(
            LogKind.NOTICE, "server", "maintenance", 120));
  }

  @Test
  void normalizeReplyPreviewTextCollapsesWhitespaceAndControlCharacters() {
    assertEquals(
        "hello world next line",
        ChatTranscriptReplyPreviewSupport.normalizeReplyPreviewText(
            "  hello \n\tworld \u0001 next\r\nline  ", 120));
  }

  @Test
  void normalizeReplyPreviewTextTruncatesWithEllipsis() {
    assertEquals(
        "1234567...",
        ChatTranscriptReplyPreviewSupport.normalizeReplyPreviewText("123456789012345", 10));
  }

  @Test
  void boundedReplyPreviewCacheEvictsOldestEntries() {
    LinkedHashMap<String, String> cache =
        ChatTranscriptReplyPreviewSupport.createBoundedReplyPreviewCache(2);

    cache.put("m-1", "one");
    cache.put("m-2", "two");
    cache.put("m-3", "three");

    assertEquals(Map.of("m-2", "two", "m-3", "three"), cache);
  }
}

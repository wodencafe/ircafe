package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.Test;

class ChatTranscriptPendingOutgoingSupportTest {

  @Test
  void markPendingAddsTrimmedPendingMetadata() {
    SimpleAttributeSet attrs = new SimpleAttributeSet();

    ChatTranscriptPendingOutgoingSupport.markPending(attrs, " pending-1 ");

    assertEquals("pending-1", attrs.getAttribute(ChatStyles.ATTR_META_PENDING_ID));
    assertEquals("pending", attrs.getAttribute(ChatStyles.ATTR_META_PENDING_STATE));
  }

  @Test
  void pendingTailAttrsCopiesBaseAttributesAndMarksPending() {
    SimpleAttributeSet base = new SimpleAttributeSet();
    base.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_MESSAGE);

    SimpleAttributeSet tail =
        ChatTranscriptPendingOutgoingSupport.pendingTailAttrs(base, "pending-2");

    assertEquals(ChatStyles.STYLE_MESSAGE, tail.getAttribute(ChatStyles.ATTR_STYLE));
    assertEquals("pending-2", tail.getAttribute(ChatStyles.ATTR_META_PENDING_ID));
    assertEquals("pending", tail.getAttribute(ChatStyles.ATTR_META_PENDING_STATE));
  }

  @Test
  void pendingSpinnerColorReadsForegroundWhenAvailable() {
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    StyleConstants.setForeground(attrs, Color.BLUE);

    assertEquals(Color.BLUE, ChatTranscriptPendingOutgoingSupport.pendingSpinnerColor(attrs));
    assertNull(ChatTranscriptPendingOutgoingSupport.pendingSpinnerColor(null));
  }

  @Test
  void renderPendingFailureHandlesBlankAndTruncatesLongReason() {
    assertEquals("[failed]", ChatTranscriptPendingOutgoingSupport.renderPendingFailure("  "));

    String longReason = "x".repeat(130);
    assertEquals(
        "[failed: " + "x".repeat(117) + "...]",
        ChatTranscriptPendingOutgoingSupport.renderPendingFailure(longReason));
  }
}

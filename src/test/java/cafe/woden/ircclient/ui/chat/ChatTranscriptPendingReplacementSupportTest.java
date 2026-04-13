package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class ChatTranscriptPendingReplacementSupportTest {

  @Test
  void prepareReplacementRemovesPendingLineAndKeepsInsertionOffset() throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    SimpleAttributeSet pending = new SimpleAttributeSet();
    pending.addAttribute(ChatStyles.ATTR_META_PENDING_ID, "pending-1");
    doc.insertString(0, "me: hello\n", pending);
    doc.insertString(doc.getLength(), "later\n", new SimpleAttributeSet());

    ChatTranscriptPendingReplacementSupport.ReplacementPlan plan =
        ChatTranscriptPendingReplacementSupport.prepareReplacement(
            doc, " pending-1 ", 1_234L, () -> 42L);

    assertNotNull(plan);
    assertEquals(0, plan.lineStart());
    assertEquals(1_234L, plan.effectiveEpochMs());
    assertFalse(doc.getText(0, doc.getLength()).contains("hello"));
    assertTrue(doc.getText(0, doc.getLength()).contains("later"));
  }

  @Test
  void prepareReplacementUsesFallbackClockAndRejectsMissingPendingLine() throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    SimpleAttributeSet pending = new SimpleAttributeSet();
    pending.addAttribute(ChatStyles.ATTR_META_PENDING_ID, "pending-1");
    doc.insertString(0, "me: hello\n", pending);

    ChatTranscriptPendingReplacementSupport.ReplacementPlan fallbackPlan =
        ChatTranscriptPendingReplacementSupport.prepareReplacement(doc, "pending-1", 0L, () -> 42L);

    assertNotNull(fallbackPlan);
    assertEquals(42L, fallbackPlan.effectiveEpochMs());
    assertNull(
        ChatTranscriptPendingReplacementSupport.prepareReplacement(
            doc, "pending-1", 1L, () -> 42L));
    assertNull(
        ChatTranscriptPendingReplacementSupport.prepareReplacement(
            null, "pending-1", 1L, () -> 42L));
  }
}

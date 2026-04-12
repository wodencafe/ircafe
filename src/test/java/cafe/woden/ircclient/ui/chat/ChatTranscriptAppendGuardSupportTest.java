package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class ChatTranscriptAppendGuardSupportTest {

  @Test
  void shouldSkipAppendByMessageIdMatchesNormalizedExistingMessageId() throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_MSGID, "m-1");
    doc.insertString(0, "alice: hello\n", attrs);

    assertTrue(ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(doc, " m-1 "));
  }

  @Test
  void shouldSkipAppendByMessageIdAllowsBlankUnknownOrMissingDocument() throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_MSGID, "m-1");
    doc.insertString(0, "alice: hello\n", attrs);

    assertFalse(ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(doc, ""));
    assertFalse(ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(doc, "m-2"));
    assertFalse(ChatTranscriptAppendGuardSupport.shouldSkipAppendByMessageId(null, "m-1"));
  }
}

package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JLabel;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.Test;

class ChatTranscriptDocumentSupportTest {

  @Test
  void findLineStartByMessageIdMatchesNormalizedMessageIdAtLineStart() throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    SimpleAttributeSet firstLine = new SimpleAttributeSet();
    firstLine.addAttribute(ChatStyles.ATTR_META_MSGID, "m-1");

    doc.insertString(0, "alice: hello\n", firstLine);
    doc.insertString(doc.getLength(), "bob: hi\n", new SimpleAttributeSet());

    assertEquals(0, ChatTranscriptDocumentSupport.findLineStartByMessageId(doc, " m-1 "));
    assertEquals(-1, ChatTranscriptDocumentSupport.findLineStartByMessageId(doc, "m-2"));
  }

  @Test
  void findLineStartByPendingIdScansAcrossWholeLine() throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    doc.insertString(0, "me: ", new SimpleAttributeSet());
    SimpleAttributeSet pending = new SimpleAttributeSet();
    pending.addAttribute(ChatStyles.ATTR_META_PENDING_ID, "pending-1");
    doc.insertString(doc.getLength(), "hello", pending);
    doc.insertString(doc.getLength(), "\n", new SimpleAttributeSet());

    assertEquals(0, ChatTranscriptDocumentSupport.findLineStartByPendingId(doc, " pending-1 "));
    assertEquals(-1, ChatTranscriptDocumentSupport.findLineStartByPendingId(doc, "pending-2"));
  }

  @Test
  void lineEndOffsetForLineStartClampsToDocumentBounds() throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    doc.insertString(0, "one\nsecond\n", new SimpleAttributeSet());

    assertEquals(4, ChatTranscriptDocumentSupport.lineEndOffsetForLineStart(doc, 0));
    assertEquals(doc.getLength(), ChatTranscriptDocumentSupport.lineEndOffsetForLineStart(doc, 4));
    assertEquals(999, ChatTranscriptDocumentSupport.lineEndOffsetForLineStart(null, 999));
  }

  @Test
  void findInlineComponentOffsetMatchesExpectedComponentWithinRange() throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    JLabel marker = new JLabel("marker");
    SimpleAttributeSet componentAttrs = new SimpleAttributeSet();
    StyleConstants.setComponent(componentAttrs, marker);

    doc.insertString(0, "a", new SimpleAttributeSet());
    doc.insertString(doc.getLength(), " ", componentAttrs);
    doc.insertString(doc.getLength(), "b", new SimpleAttributeSet());

    int offset = ChatTranscriptDocumentSupport.findInlineComponentOffset(doc, 2, 0, marker);

    assertEquals(1, offset);
    assertTrue(
        ChatTranscriptDocumentSupport.findInlineComponentOffset(doc, 0, 2, new JLabel("x")) < 0);
  }
}

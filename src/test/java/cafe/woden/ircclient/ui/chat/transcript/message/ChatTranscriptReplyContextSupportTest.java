package cafe.woden.ircclient.ui.chat.transcript.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.Test;

class ChatTranscriptReplyContextSupportTest {

  @Test
  void appendReplyContextLineWritesMsgRefAndPreview() throws Exception {
    ChatTranscriptReplyContextSupport.Context context =
        new ChatTranscriptReplyContextSupport.Context(new ChatStyles(null));
    DefaultStyledDocument doc = new DefaultStyledDocument();
    TargetRef ref = new TargetRef("srv", "#chan");

    ChatTranscriptReplyContextSupport.appendReplyContextLine(
        context, doc, ref, "alice", " m-1 ", 1_234L, messageId -> "alice: hello");

    assertEquals("↪ Reply to alice: hello\n", doc.getText(0, doc.getLength()));
    int msgRefOffset = doc.getText(0, doc.getLength()).indexOf("alice");
    assertEquals(
        "m-1",
        doc.getCharacterElement(msgRefOffset)
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_MSG_REF));
    assertFalse(StyleConstants.isItalic(doc.getCharacterElement(msgRefOffset).getAttributes()));
    assertEquals(
        ChatStyles.REPLY_BLOCK_QUOTE,
        doc.getParagraphElement(0).getAttributes().getAttribute(ChatStyles.ATTR_REPLY_BLOCK));
    assertNull(
        doc.getParagraphElement(doc.getLength())
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_REPLY_BLOCK));
  }

  @Test
  void appendReplyContextLineSkipsBlankReplyIds() throws Exception {
    ChatTranscriptReplyContextSupport.Context context =
        new ChatTranscriptReplyContextSupport.Context(new ChatStyles(null));
    DefaultStyledDocument doc = new DefaultStyledDocument();

    ChatTranscriptReplyContextSupport.appendReplyContextLine(
        context,
        doc,
        new TargetRef("srv", "#chan"),
        "alice",
        "   ",
        1_234L,
        messageId -> "ignored");

    assertEquals("", doc.getText(0, doc.getLength()));
  }

  @Test
  void missingOriginalUsesReadableClickableFallback() throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    ChatTranscriptReplyContextSupport.appendReplyContextLine(
        new ChatTranscriptReplyContextSupport.Context(new ChatStyles(null)),
        doc,
        new TargetRef("srv", "#chan"),
        "bob",
        "unloaded-id",
        1L,
        id -> "");
    assertEquals("↪ Reply to an earlier message\n", doc.getText(0, doc.getLength()));
    assertEquals(
        "unloaded-id",
        doc.getCharacterElement(2).getAttributes().getAttribute(ChatStyles.ATTR_MSG_REF));
  }

  @Test
  void longOrMultilinePreviewStaysBoundedToOneParagraph() throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    ChatTranscriptReplyContextSupport.appendReplyContextLine(
        new ChatTranscriptReplyContextSupport.Context(new ChatStyles(null)),
        doc,
        new TargetRef("srv", "#chan"),
        "bob",
        "m-1",
        1L,
        id -> "alice: first\nsecond " + "word ".repeat(100));
    String text = doc.getText(0, doc.getLength());
    assertTrue(text.startsWith("↪ Reply to alice: first second "));
    assertTrue(text.endsWith("...\n"));
    assertTrue(text.length() <= 172);
    assertEquals(1, text.chars().filter(c -> c == '\n').count());
  }
}

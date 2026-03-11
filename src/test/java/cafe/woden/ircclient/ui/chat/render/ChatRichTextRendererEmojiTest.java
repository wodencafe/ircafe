package cafe.woden.ircclient.ui.chat.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.util.EmojiFontSupport;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class ChatRichTextRendererEmojiTest {

  @Test
  void insertStyledTextAtMarksEmojiRunsWithoutChangingNeighborTextRuns() throws Exception {
    DefaultStyledDocument doc = new DefaultStyledDocument();

    ChatRichTextRenderer.insertStyledTextAt(doc, "hi 😀 there", new SimpleAttributeSet(), 0);

    String text = doc.getText(0, doc.getLength());
    int emojiIndex = text.indexOf("😀");
    assertEquals("hi 😀 there", text);
    assertTrue(emojiIndex >= 0);
    assertFalse(
        EmojiFontSupport.isEmojiRun(doc.getCharacterElement(emojiIndex - 1).getAttributes()));
    assertTrue(EmojiFontSupport.isEmojiRun(doc.getCharacterElement(emojiIndex).getAttributes()));
    assertFalse(
        EmojiFontSupport.isEmojiRun(
            doc.getCharacterElement(emojiIndex + "😀".length()).getAttributes()));
  }
}

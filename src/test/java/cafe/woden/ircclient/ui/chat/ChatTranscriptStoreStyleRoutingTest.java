package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;

class ChatTranscriptStoreStyleRoutingTest {

  @Test
  void messageTypesMapToExpectedStyleKeys() throws Exception {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    ChatTranscriptStore store =
        new ChatTranscriptStore(styles, renderer, null, null, null, null, null, null, null);
    TargetRef ref = new TargetRef("srv", "#chan");

    store.appendChatAt(ref, "alice", "chat-style-token", false, 1_000L);
    store.appendNoticeAt(ref, "(notice) server", "notice-style-token", 2_000L);
    store.appendActionAt(ref, "alice", "action-style-token", false, 3_000L);
    store.appendErrorAt(ref, "(error) server", "error-style-token", 4_000L);
    store.appendPresenceFromHistory(ref, "presence-style-token", 5_000L);

    StyledDocument doc = store.document(ref);
    assertEquals(ChatStyles.STYLE_MESSAGE, styleAtToken(doc, "chat-style-token"));
    assertEquals(ChatStyles.STYLE_NOTICE_MESSAGE, styleAtToken(doc, "notice-style-token"));
    assertEquals(ChatStyles.STYLE_ACTION_MESSAGE, styleAtToken(doc, "action-style-token"));
    assertEquals(ChatStyles.STYLE_ERROR, styleAtToken(doc, "error-style-token"));
    assertEquals(ChatStyles.STYLE_PRESENCE, styleAtToken(doc, "presence-style-token"));
  }

  private static String styleAtToken(StyledDocument doc, String token) throws Exception {
    String text = doc.getText(0, doc.getLength());
    int idx = text.indexOf(token);
    assertTrue(idx >= 0, "token not found in transcript");
    AttributeSet attrs = doc.getCharacterElement(idx).getAttributes();
    Object style = attrs.getAttribute(ChatStyles.ATTR_STYLE);
    return style == null ? "" : style.toString();
  }
}

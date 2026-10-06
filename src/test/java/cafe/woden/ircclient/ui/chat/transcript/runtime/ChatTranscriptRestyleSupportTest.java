package cafe.woden.ircclient.ui.chat.transcript.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.ui.DocumentComponentFactory;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.NickColorService;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.Test;

class ChatTranscriptRestyleSupportTest {

  @Test
  void restylingPreservesTheFactoryForComponentsInSharedTranscripts() throws Exception {
    ChatStyles styles = new ChatStyles(null);
    DefaultStyledDocument doc = new DefaultStyledDocument();
    DocumentComponentFactory factory = new DocumentComponentFactory(() -> new JLabel("preview"));
    SimpleAttributeSet attrs = new SimpleAttributeSet(styles.message());
    attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_MESSAGE);
    attrs.addAttribute(ChatStyles.ATTR_URL, "https://example.test/article");
    StyleConstants.setComponent(attrs, factory);
    doc.insertString(0, " ", attrs);

    ChatTranscriptRestyleSupport.restyleDocument(
        new ChatTranscriptRestyleSupport.Context(styles, null, (fresh, action) -> {}),
        doc,
        false,
        null);

    AttributeSet restyled = doc.getCharacterElement(0).getAttributes();
    assertSame(factory, StyleConstants.getComponent(restyled));
    assertEquals(StyleConstants.ComponentElementName, doc.getCharacterElement(0).getName());
    assertEquals("https://example.test/article", restyled.getAttribute(ChatStyles.ATTR_URL));
  }

  @Test
  void restyleDocumentSlicePreservesMetadataAndReappliesOutgoingColor() throws Exception {
    ChatStyles styles = new ChatStyles(null);
    DefaultStyledDocument doc = new DefaultStyledDocument();
    SimpleAttributeSet attrs = new SimpleAttributeSet(styles.message());
    attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_MESSAGE);
    attrs.addAttribute(ChatStyles.ATTR_META_MSGID, "m-1");
    attrs.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);
    doc.insertString(0, "hello", attrs);

    ChatTranscriptRestyleSupport.SliceOutcome outcome =
        ChatTranscriptRestyleSupport.restyleDocumentSlice(
            new ChatTranscriptRestyleSupport.Context(styles, null, (fresh, action) -> {}),
            doc,
            0,
            Integer.MAX_VALUE,
            true,
            Color.RED);

    AttributeSet restyled = doc.getCharacterElement(0).getAttributes();
    assertTrue(outcome.done());
    assertEquals("m-1", restyled.getAttribute(ChatStyles.ATTR_META_MSGID));
    assertEquals(Boolean.TRUE, restyled.getAttribute(ChatStyles.ATTR_OUTGOING));
    assertEquals(ChatStyles.STYLE_MESSAGE, restyled.getAttribute(ChatStyles.ATTR_STYLE));
    assertEquals(Color.RED, StyleConstants.getForeground(restyled));
  }

  @Test
  void restyleDocumentSliceReappliesFilterActionAndNickColor() throws Exception {
    ChatStyles styles = new ChatStyles(null);
    NickColorService nickColors = mock(NickColorService.class);
    doAnswer(
            invocation -> {
              SimpleAttributeSet fresh = invocation.getArgument(0);
              StyleConstants.setForeground(fresh, Color.GREEN);
              return null;
            })
        .when(nickColors)
        .applyColor(any(SimpleAttributeSet.class), eq("alice"));

    DefaultStyledDocument doc = new DefaultStyledDocument();
    SimpleAttributeSet attrs = new SimpleAttributeSet(styles.message());
    attrs.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_MESSAGE);
    attrs.addAttribute(ChatStyles.ATTR_META_FILTER_ACTION, "highlight");
    attrs.addAttribute(NickColorService.ATTR_NICK, "alice");
    doc.insertString(0, "hello", attrs);

    ChatTranscriptRestyleSupport.restyleDocumentSlice(
        new ChatTranscriptRestyleSupport.Context(
            styles,
            nickColors,
            (fresh, action) -> fresh.addAttribute("filterActionApplied", action.name())),
        doc,
        0,
        1,
        false,
        null);

    AttributeSet restyled = doc.getCharacterElement(0).getAttributes();
    assertEquals("alice", restyled.getAttribute(NickColorService.ATTR_NICK));
    assertEquals("HIGHLIGHT", restyled.getAttribute("filterActionApplied"));
    assertEquals(Color.GREEN, StyleConstants.getForeground(restyled));
    verify(nickColors).applyColor(any(SimpleAttributeSet.class), eq("alice"));
  }
}

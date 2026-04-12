package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class ChatTranscriptDeliveryIndicatorSupportTest {

  @Test
  void insertConfirmedDotAddsInlineConfirmedDotComponent() {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    SimpleAttributeSet attrs = new SimpleAttributeSet();

    boolean inserted =
        ChatTranscriptDeliveryIndicatorSupport.insertConfirmedDot(doc, 0, attrs, component -> {});

    assertTrue(inserted);
    assertEquals(
        1,
        ChatTranscriptDeliveryIndicatorSupport.inlineComponentCount(
            doc, OutgoingSendIndicator.ConfirmedDot.class));
  }

  @Test
  void removeInlineComponentRemovesMatchedComponentOnly() {
    DefaultStyledDocument doc = new DefaultStyledDocument();
    SimpleAttributeSet attrs = new SimpleAttributeSet();

    ChatTranscriptDeliveryIndicatorSupport.insertConfirmedDot(doc, 0, attrs, component -> {});
    Component expected =
        doc.getCharacterElement(0) != null
            ? javax.swing.text.StyleConstants.getComponent(
                doc.getCharacterElement(0).getAttributes())
            : null;

    boolean removed = ChatTranscriptDeliveryIndicatorSupport.removeInlineComponent(doc, expected);

    assertTrue(removed);
    assertEquals(
        0,
        ChatTranscriptDeliveryIndicatorSupport.inlineComponentCount(
            doc, OutgoingSendIndicator.ConfirmedDot.class));
  }
}

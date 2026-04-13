package cafe.woden.ircclient.ui.chat;

import java.awt.Color;
import java.awt.Component;
import java.util.function.Consumer;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** Shared helpers for inline outbound delivery indicators in transcript documents. */
final class ChatTranscriptDeliveryIndicatorSupport {

  private static final Color CONFIRMED_DOT_COLOR = new Color(0x2ecc71);
  private static final int CONFIRMED_DOT_HOLD_MS = 200;
  private static final int CONFIRMED_DOT_FADE_MS = 900;

  private ChatTranscriptDeliveryIndicatorSupport() {}

  static boolean insertConfirmedDot(
      StyledDocument doc, int after, AttributeSet baseAttrs, Consumer<Component> removeCallback) {
    if (doc == null || baseAttrs == null || removeCallback == null) return false;
    int insertPos = Math.max(0, Math.min(after - 1, doc.getLength()));
    if (insertPos < 0 || insertPos > doc.getLength()) return false;

    final OutgoingSendIndicator.ConfirmedDot[] holder = new OutgoingSendIndicator.ConfirmedDot[1];
    holder[0] =
        new OutgoingSendIndicator.ConfirmedDot(
            CONFIRMED_DOT_COLOR,
            CONFIRMED_DOT_HOLD_MS,
            CONFIRMED_DOT_FADE_MS,
            () -> {
              try {
                removeCallback.accept(holder[0]);
              } catch (Exception ignored) {
              }
            });

    try {
      SimpleAttributeSet attrs = new SimpleAttributeSet(baseAttrs);
      StyleConstants.setComponent(attrs, holder[0]);
      doc.insertString(insertPos, " ", attrs);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  static boolean removeInlineComponent(StyledDocument doc, Component expected) {
    if (doc == null || expected == null) return false;
    try {
      int len = doc.getLength();
      if (len <= 0) return false;

      int start = Math.max(0, len - 4096);
      int offset =
          ChatTranscriptDocumentSupport.findInlineComponentOffset(doc, start, len - 1, expected);
      if (offset < 0) {
        offset = ChatTranscriptDocumentSupport.findInlineComponentOffset(doc, 0, len - 1, expected);
      }
      if (offset < 0) return false;

      doc.remove(offset, 1);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  static int inlineComponentCount(StyledDocument doc, Class<?> componentType) {
    if (doc == null || componentType == null) return 0;
    int count = 0;
    int len = doc.getLength();
    for (int i = 0; i < len; i++) {
      Element element = doc.getCharacterElement(i);
      if (element == null) continue;
      Object component = StyleConstants.getComponent(element.getAttributes());
      if (component != null && componentType.isInstance(component)) {
        count++;
      }
    }
    return count;
  }
}

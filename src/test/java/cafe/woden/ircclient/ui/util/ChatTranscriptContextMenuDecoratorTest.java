package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class ChatTranscriptContextMenuDecoratorTest {

  @Test
  void lineIdentityFromAttributesReadsMessageIdentityMetadata() {
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_MSGID, "abc123");
    attrs.addAttribute(ChatStyles.ATTR_META_IRCV3_TAGS, "msgid=abc123;typing=active");

    ChatTranscriptContextMenuDecorator.LineIdentity id =
        ChatTranscriptContextMenuDecorator.lineIdentityFromAttributes(attrs);

    assertEquals("abc123", id.messageId());
    assertEquals("msgid=abc123;typing=active", id.ircv3Tags());
    assertFalse(id.outgoingOwnMessage());
  }

  @Test
  void lineIdentityFromAttributesMarksOutgoingFromDirectionMetadata() {
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_MSGID, "abc123");
    attrs.addAttribute(ChatStyles.ATTR_META_DIRECTION, "OUT");

    ChatTranscriptContextMenuDecorator.LineIdentity id =
        ChatTranscriptContextMenuDecorator.lineIdentityFromAttributes(attrs);

    assertTrue(id.outgoingOwnMessage());
  }

  @Test
  void lineIdentityFromAttributesMarksOutgoingFromLegacyOutgoingAttribute() {
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);

    ChatTranscriptContextMenuDecorator.LineIdentity id =
        ChatTranscriptContextMenuDecorator.lineIdentityFromAttributes(attrs);

    assertTrue(id.outgoingOwnMessage());
  }

  @Test
  void lineIdentityFromAttributesReturnsEmptyForMissingMetadata() {
    ChatTranscriptContextMenuDecorator.LineIdentity id =
        ChatTranscriptContextMenuDecorator.lineIdentityFromAttributes(new SimpleAttributeSet());

    assertSame(ChatTranscriptContextMenuDecorator.LineIdentity.EMPTY, id);
  }
}

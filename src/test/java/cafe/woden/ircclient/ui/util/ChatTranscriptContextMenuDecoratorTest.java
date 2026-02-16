package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
  }

  @Test
  void lineIdentityFromAttributesReturnsEmptyForMissingMetadata() {
    ChatTranscriptContextMenuDecorator.LineIdentity id =
        ChatTranscriptContextMenuDecorator.lineIdentityFromAttributes(new SimpleAttributeSet());

    assertSame(ChatTranscriptContextMenuDecorator.LineIdentity.EMPTY, id);
  }
}


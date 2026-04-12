package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import java.util.Map;
import java.util.Set;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class ChatTranscriptSenderStyleSupportTest {

  @Test
  void prepareAppliesNickColorMetaOutgoingAndNotificationStyles() {
    ChatStyles styles = new ChatStyles(null);
    NickColorService nickColors = mock(NickColorService.class);
    when(nickColors.enabled()).thenReturn(true);
    SimpleAttributeSet coloredFrom = new SimpleAttributeSet(styles.from());
    coloredFrom.addAttribute("colored", Boolean.TRUE);
    when(nickColors.forNick(eq("alice"), any(AttributeSet.class))).thenReturn(coloredFrom);

    ChatTranscriptSenderStyleSupport.Context context =
        new ChatTranscriptSenderStyleSupport.Context(
            styles,
            nickColors,
            ChatTranscriptSenderStyleSupportTest::bindMeta,
            (fromStyle, messageStyle, outgoingLocalEcho) -> {
              if (outgoingLocalEcho) {
                fromStyle.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);
                messageStyle.addAttribute(ChatStyles.ATTR_OUTGOING, Boolean.TRUE);
              }
            },
            (fromStyle, messageStyle, rawNotificationColor) -> {
              if (rawNotificationColor != null) {
                fromStyle.addAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG, rawNotificationColor);
                messageStyle.addAttribute(
                    ChatStyles.ATTR_NOTIFICATION_RULE_BG, rawNotificationColor);
              }
            });

    ChatTranscriptSenderStyleSupport.PreparedStyles prepared =
        ChatTranscriptSenderStyleSupport.prepare(context, lineMeta(), "alice", true, "#ffee00");

    assertEquals(Boolean.TRUE, prepared.fromStyle().getAttribute("colored"));
    assertEquals("m-1", prepared.fromStyle().getAttribute(ChatStyles.ATTR_META_MSGID));
    assertEquals("m-1", prepared.messageStyle().getAttribute(ChatStyles.ATTR_META_MSGID));
    assertEquals(Boolean.TRUE, prepared.fromStyle().getAttribute(ChatStyles.ATTR_OUTGOING));
    assertEquals(Boolean.TRUE, prepared.messageStyle().getAttribute(ChatStyles.ATTR_OUTGOING));
    assertEquals(
        "#ffee00", prepared.fromStyle().getAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG));
    assertEquals(
        "#ffee00", prepared.messageStyle().getAttribute(ChatStyles.ATTR_NOTIFICATION_RULE_BG));
    verify(nickColors).forNick(eq("alice"), any(AttributeSet.class));
  }

  @Test
  void prepareSkipsNickColoringWhenSenderBlank() {
    ChatStyles styles = new ChatStyles(null);
    NickColorService nickColors = mock(NickColorService.class);
    ChatTranscriptSenderStyleSupport.Context context =
        new ChatTranscriptSenderStyleSupport.Context(
            styles,
            nickColors,
            ChatTranscriptSenderStyleSupportTest::bindMeta,
            (fromStyle, messageStyle, outgoingLocalEcho) -> {},
            (fromStyle, messageStyle, rawNotificationColor) -> {});

    ChatTranscriptSenderStyleSupport.PreparedStyles prepared =
        ChatTranscriptSenderStyleSupport.prepare(context, lineMeta(), "   ", false, null);

    assertEquals("m-1", prepared.fromStyle().getAttribute(ChatStyles.ATTR_META_MSGID));
    assertEquals("m-1", prepared.messageStyle().getAttribute(ChatStyles.ATTR_META_MSGID));
    assertNull(prepared.fromStyle().getAttribute("colored"));
    verify(nickColors, never()).enabled();
    verify(nickColors, never()).forNick(eq("alice"), any(AttributeSet.class));
  }

  private static SimpleAttributeSet bindMeta(AttributeSet base, ChatTranscriptStore.LineMeta meta) {
    SimpleAttributeSet attrs = new SimpleAttributeSet(base);
    if (meta != null && meta.messageId() != null) {
      attrs.addAttribute(ChatStyles.ATTR_META_MSGID, meta.messageId());
    }
    return attrs;
  }

  private static ChatTranscriptStore.LineMeta lineMeta() {
    return new ChatTranscriptStore.LineMeta(
        "buffer", LogKind.CHAT, LogDirection.OUT, "alice", 1L, Set.of(), "m-1", "", Map.of());
  }
}

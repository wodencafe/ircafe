package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatHistoryActionCoordinatorTest {

  @Test
  void replyContextActionVisibleUsesIrcCapabilityForActiveTarget() {
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);

    ChatHistoryActionCoordinator coordinator =
        new ChatHistoryActionCoordinator(
            irc,
            null,
            () -> channel,
            target -> {},
            () -> {},
            () -> {},
            () -> {},
            cmd -> {},
            (target, msgId) -> {},
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200");

    assertTrue(coordinator.replyContextActionVisible());
  }

  @Test
  void onReplyToMessageRequestedActivatesInputAndBeginsCompose() {
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    AtomicReference<TargetRef> activatedTarget = new AtomicReference<>();
    AtomicInteger activateInputCalls = new AtomicInteger();
    AtomicInteger focusInputCalls = new AtomicInteger();
    AtomicReference<String> replyTarget = new AtomicReference<>();
    AtomicReference<String> replyMessageId = new AtomicReference<>();

    ChatHistoryActionCoordinator coordinator =
        new ChatHistoryActionCoordinator(
            irc,
            null,
            () -> channel,
            activatedTarget::set,
            activateInputCalls::incrementAndGet,
            focusInputCalls::incrementAndGet,
            () -> {},
            cmd -> {},
            (target, msgId) -> {
              replyTarget.set(target);
              replyMessageId.set(msgId);
            },
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200");

    coordinator.onReplyToMessageRequested("msg-42");

    assertEquals(channel, activatedTarget.get());
    assertEquals(1, activateInputCalls.get());
    assertEquals(1, focusInputCalls.get());
    assertEquals("#ircafe", replyTarget.get());
    assertEquals("msg-42", replyMessageId.get());
  }

  @Test
  void onLoadNewerHistoryRequestedEmitsLatestCommandWhenChatHistorySupported() {
    IrcClientService irc = mock(IrcClientService.class);
    ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    AtomicReference<TargetRef> activatedTarget = new AtomicReference<>();
    AtomicInteger activateInputCalls = new AtomicInteger();
    AtomicInteger armTailPinCalls = new AtomicInteger();
    AtomicReference<String> emittedCommand = new AtomicReference<>();
    when(irc.isChatHistoryAvailable("libera")).thenReturn(true);

    ChatHistoryActionCoordinator coordinator =
        new ChatHistoryActionCoordinator(
            irc,
            chatHistoryService,
            () -> channel,
            activatedTarget::set,
            activateInputCalls::incrementAndGet,
            () -> {},
            armTailPinCalls::incrementAndGet,
            emittedCommand::set,
            (target, msgId) -> {},
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200");

    coordinator.onLoadNewerHistoryRequested();

    assertEquals(channel, activatedTarget.get());
    assertEquals(1, activateInputCalls.get());
    assertEquals(1, armTailPinCalls.get());
    assertEquals("/chathistory latest * 200", emittedCommand.get());
    verify(chatHistoryService, never()).reloadRecent(channel);
  }

  @Test
  void onLoadNewerHistoryRequestedReloadsRecentWhenZncSupportedOnly() {
    IrcClientService irc = mock(IrcClientService.class);
    ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(irc.isChatHistoryAvailable("libera")).thenReturn(false);
    when(irc.isZncPlaybackAvailable("libera")).thenReturn(true);
    when(chatHistoryService.canReloadRecent(channel)).thenReturn(true);

    ChatHistoryActionCoordinator coordinator =
        new ChatHistoryActionCoordinator(
            irc,
            chatHistoryService,
            () -> channel,
            target -> {},
            () -> {},
            () -> {},
            () -> {},
            cmd -> {},
            (target, msgId) -> {},
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200");

    coordinator.onLoadNewerHistoryRequested();

    verify(chatHistoryService).reloadRecent(channel);
  }

  @Test
  void requestHistoryAroundMessageReturnsFalseWhenUnsupported() {
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(irc.isChatHistoryAvailable("libera")).thenReturn(false);

    ChatHistoryActionCoordinator coordinator =
        new ChatHistoryActionCoordinator(
            irc,
            null,
            () -> channel,
            target -> {},
            () -> {},
            () -> {},
            () -> {},
            cmd -> {},
            (target, msgId) -> {},
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200");

    assertFalse(coordinator.requestHistoryAroundMessage("msg-1"));
  }

  @Test
  void onRedactMessageRequestedEmitsRedactCommandWhenSupported() {
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    AtomicReference<String> emittedCommand = new AtomicReference<>();
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(true);

    ChatHistoryActionCoordinator coordinator =
        new ChatHistoryActionCoordinator(
            irc,
            null,
            () -> channel,
            target -> {},
            () -> {},
            () -> {},
            () -> {},
            emittedCommand::set,
            (target, msgId) -> {},
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200");

    coordinator.onRedactMessageRequested("abc123");

    assertEquals("/redact abc123", emittedCommand.get());
  }
}

package cafe.woden.ircclient.ui.coordinator;

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
            (target, msgId, preview, jumpAction) -> {},
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200",
            (target, msgId) -> "",
            (target, msgId) -> -1,
            () -> {},
            offset -> {});

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
    AtomicReference<String> replyPreview = new AtomicReference<>();

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
            (target, msgId, preview, jumpAction) -> {
              replyTarget.set(target);
              replyMessageId.set(msgId);
              replyPreview.set(preview);
            },
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200",
            (target, msgId) -> "alice: earlier line",
            (target, msgId) -> 12,
            () -> {},
            offset -> {});

    coordinator.onReplyToMessageRequested("msg-42");

    assertEquals(channel, activatedTarget.get());
    assertEquals(1, activateInputCalls.get());
    assertEquals(1, focusInputCalls.get());
    assertEquals("#ircafe", replyTarget.get());
    assertEquals("msg-42", replyMessageId.get());
    assertEquals("alice: earlier line", replyPreview.get());
  }

  @Test
  void unreactContextActionVisibleRequiresReplyAndUnreactCapabilities() {
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isDraftUnreactAvailable("libera")).thenReturn(true);

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
            (target, msgId, preview, jumpAction) -> {},
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200",
            (target, msgId) -> "",
            (target, msgId) -> -1,
            () -> {},
            offset -> {});

    assertTrue(coordinator.unreactContextActionVisible());
    when(irc.isDraftUnreactAvailable("libera")).thenReturn(false);
    assertFalse(coordinator.unreactContextActionVisible());
  }

  @Test
  void onUnreactToMessageRequestedPrefillsCommandAndFocusesInput() {
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isDraftUnreactAvailable("libera")).thenReturn(true);
    AtomicReference<TargetRef> activatedTarget = new AtomicReference<>();
    AtomicInteger activateInputCalls = new AtomicInteger();
    AtomicInteger focusInputCalls = new AtomicInteger();
    AtomicReference<String> draftText = new AtomicReference<>();

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
            (target, msgId, preview, jumpAction) -> {},
            (target, msgId) -> {},
            draftText::set,
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200",
            (target, msgId) -> "",
            (target, msgId) -> -1,
            () -> {},
            offset -> {});

    coordinator.onUnreactToMessageRequested("msg-88");

    assertEquals(channel, activatedTarget.get());
    assertEquals(1, activateInputCalls.get());
    assertEquals(1, focusInputCalls.get());
    assertEquals("/unreact msg-88 ", draftText.get());
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
            (target, msgId, preview, jumpAction) -> {},
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200",
            (target, msgId) -> "",
            (target, msgId) -> -1,
            () -> {},
            offset -> {});

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
            (target, msgId, preview, jumpAction) -> {},
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200",
            (target, msgId) -> "",
            (target, msgId) -> -1,
            () -> {},
            offset -> {});

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
            (target, msgId, preview, jumpAction) -> {},
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200",
            (target, msgId) -> "",
            (target, msgId) -> -1,
            () -> {},
            offset -> {});

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
            (target, msgId, preview, jumpAction) -> {},
            (target, msgId) -> {},
            text -> {},
            () -> "/chathistory latest * 200",
            msgId -> "/chathistory around msgid=" + msgId + " 200",
            (target, msgId) -> "",
            (target, msgId) -> -1,
            () -> {},
            offset -> {});

    coordinator.onRedactMessageRequested("abc123");

    assertEquals("/redact abc123", emittedCommand.get());
  }
}

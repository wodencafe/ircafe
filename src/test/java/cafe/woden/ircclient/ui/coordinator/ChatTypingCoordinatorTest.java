package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.ui.input.MessageInputPanel;
import io.reactivex.rxjava3.core.Completable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ChatTypingCoordinatorTest {

  @Test
  void showTypingIndicatorRepinsWhenBannerVisibilityChanges() throws Exception {
    MessageInputPanel inputPanel = mock(MessageInputPanel.class);
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    AtomicInteger armTailPinCalls = new AtomicInteger();
    AtomicInteger scrollToBottomCalls = new AtomicInteger();
    Map<TargetRef, String> drafts = new HashMap<>();

    when(inputPanel.showRemoteTypingIndicator("alice", "active")).thenReturn(true);

    ChatTypingCoordinator coordinator =
        new ChatTypingCoordinator(
            inputPanel,
            irc,
            () -> channel,
            () -> true,
            armTailPinCalls::incrementAndGet,
            () -> false,
            scrollToBottomCalls::incrementAndGet,
            drafts);

    coordinator.showTypingIndicator(channel, "alice", "active");
    flushEdt();

    verify(inputPanel).showRemoteTypingIndicator("alice", "active");
    assertEquals(1, armTailPinCalls.get());
    assertEquals(1, scrollToBottomCalls.get());
  }

  @Test
  void showTypingIndicatorIgnoresNonActiveTargets() {
    MessageInputPanel inputPanel = mock(MessageInputPanel.class);
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef active = new TargetRef("libera", "#ircafe");
    Map<TargetRef, String> drafts = new HashMap<>();

    ChatTypingCoordinator coordinator =
        new ChatTypingCoordinator(
            inputPanel, irc, () -> active, () -> true, () -> {}, () -> false, () -> {}, drafts);

    coordinator.showTypingIndicator(new TargetRef("libera", "#other"), "alice", "active");

    verify(inputPanel, never()).showRemoteTypingIndicator("alice", "active");
  }

  @Test
  void normalizeTypingCapabilityClearsRemoteIndicatorAndRefreshesAvailability() throws Exception {
    MessageInputPanel inputPanel = mock(MessageInputPanel.class);
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    AtomicInteger armTailPinCalls = new AtomicInteger();
    AtomicInteger scrollToBottomCalls = new AtomicInteger();
    Map<TargetRef, String> drafts = new HashMap<>();

    when(inputPanel.clearRemoteTypingIndicator()).thenReturn(true);
    when(irc.isTypingAvailable("libera")).thenReturn(true);

    ChatTypingCoordinator coordinator =
        new ChatTypingCoordinator(
            inputPanel,
            irc,
            () -> channel,
            () -> true,
            armTailPinCalls::incrementAndGet,
            () -> false,
            scrollToBottomCalls::incrementAndGet,
            drafts);

    coordinator.normalizeIrcv3CapabilityUiState("libera", "typing");
    flushEdt();

    verify(inputPanel).clearRemoteTypingIndicator();
    verify(inputPanel).setTypingSignalAvailable(true);
    assertEquals(1, armTailPinCalls.get());
    assertEquals(1, scrollToBottomCalls.get());
  }

  @Test
  void normalizeDraftCapabilitiesUpdatesStoredDraftsForServer() {
    MessageInputPanel inputPanel = mock(MessageInputPanel.class);
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    Map<TargetRef, String> drafts = new HashMap<>();
    String before = "/quote @draft/reply=abc PRIVMSG #ircafe :hello";
    drafts.put(channel, before);

    when(irc.isDraftReplyAvailable("libera")).thenReturn(false);
    when(irc.isDraftReactAvailable("libera")).thenReturn(false);

    ChatTypingCoordinator coordinator =
        new ChatTypingCoordinator(
            inputPanel, irc, () -> channel, () -> false, () -> {}, () -> false, () -> {}, drafts);

    coordinator.normalizeIrcv3CapabilityUiState("libera", "draft/reply");

    String expected = MessageInputPanel.normalizeIrcv3DraftForCapabilities(before, false, false);
    assertEquals(expected, drafts.get(channel));
    verify(inputPanel).normalizeIrcv3DraftForCapabilities(false, false);
  }

  @Test
  void onLocalTypingStateChangedSendsNormalizedTypingWhenAvailable() {
    MessageInputPanel inputPanel = mock(MessageInputPanel.class);
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    Map<TargetRef, String> drafts = new HashMap<>();

    when(irc.isTypingAvailable("libera")).thenReturn(true);
    when(irc.sendTyping("libera", "#ircafe", "active")).thenReturn(Completable.complete());

    ChatTypingCoordinator coordinator =
        new ChatTypingCoordinator(
            inputPanel, irc, () -> channel, () -> false, () -> {}, () -> false, () -> {}, drafts);

    coordinator.onLocalTypingStateChanged("composing");

    verify(inputPanel).setTypingSignalAvailable(true);
    verify(irc).sendTyping("libera", "#ircafe", "active");
    verify(inputPanel).onLocalTypingIndicatorSent("active");
  }

  @Test
  void onLocalTypingStateChangedDoesNotSendWhenUnavailable() {
    MessageInputPanel inputPanel = mock(MessageInputPanel.class);
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef channel = new TargetRef("libera", "#ircafe");
    Map<TargetRef, String> drafts = new HashMap<>();

    when(irc.isTypingAvailable("libera")).thenReturn(false);
    when(irc.typingAvailabilityReason("libera")).thenReturn("capability disabled");

    ChatTypingCoordinator coordinator =
        new ChatTypingCoordinator(
            inputPanel, irc, () -> channel, () -> false, () -> {}, () -> false, () -> {}, drafts);

    coordinator.onLocalTypingStateChanged("active");

    verify(inputPanel).setTypingSignalAvailable(false);
    verify(irc, never()).sendTyping("libera", "#ircafe", "active");
    assertTrue(drafts.isEmpty());
  }

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      return;
    }
    SwingUtilities.invokeAndWait(() -> {});
  }
}

package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatTranscriptInteractionCoordinatorTest {

  @Test
  void onTranscriptClickedIgnoresWhenNoActiveTarget() {
    AtomicInteger activationCalls = new AtomicInteger();
    AtomicInteger inputActivationCalls = new AtomicInteger();
    AtomicInteger focusCalls = new AtomicInteger();

    ChatTranscriptInteractionCoordinator coordinator =
        coordinator(
            () -> null,
            target -> activationCalls.incrementAndGet(),
            inputActivationCalls::incrementAndGet,
            focusCalls::incrementAndGet,
            req -> {},
            cmd -> {},
            (target, messageId) -> -1,
            messageId -> false,
            () -> {},
            offset -> {});

    coordinator.onTranscriptClicked();

    assertEquals(0, activationCalls.get());
    assertEquals(0, inputActivationCalls.get());
    assertEquals(0, focusCalls.get());
  }

  @Test
  void onTranscriptClickedActivatesTargetAndInput() {
    TargetRef activeTarget = new TargetRef("libera", "#ircafe");
    AtomicReference<TargetRef> activatedTarget = new AtomicReference<>();
    AtomicInteger inputActivationCalls = new AtomicInteger();
    AtomicInteger focusCalls = new AtomicInteger();

    ChatTranscriptInteractionCoordinator coordinator =
        coordinator(
            () -> activeTarget,
            activatedTarget::set,
            inputActivationCalls::incrementAndGet,
            focusCalls::incrementAndGet,
            req -> {},
            cmd -> {},
            (target, messageId) -> -1,
            messageId -> false,
            () -> {},
            offset -> {});

    coordinator.onTranscriptClicked();

    assertEquals(activeTarget, activatedTarget.get());
    assertEquals(1, inputActivationCalls.get());
    assertEquals(1, focusCalls.get());
  }

  @Test
  void onNickClickedEmitsOpenPrivateRequestForChannelTarget() {
    TargetRef activeTarget = new TargetRef("libera", "#ircafe");
    AtomicReference<PrivateMessageRequest> privateRequest = new AtomicReference<>();

    ChatTranscriptInteractionCoordinator coordinator =
        coordinator(
            () -> activeTarget,
            target -> {},
            () -> {},
            () -> {},
            privateRequest::set,
            cmd -> {},
            (target, messageId) -> -1,
            messageId -> false,
            () -> {},
            offset -> {});

    boolean handled = coordinator.onNickClicked("alice");

    assertTrue(handled);
    assertEquals(new PrivateMessageRequest("libera", "alice"), privateRequest.get());
  }

  @Test
  void onNickClickedReturnsFalseWhenTargetIsNotChannelOrNickBlank() {
    ChatTranscriptInteractionCoordinator coordinatorForStatus =
        coordinator(
            () -> new TargetRef("libera", "status"),
            target -> {},
            () -> {},
            () -> {},
            req -> {},
            cmd -> {},
            (target, messageId) -> -1,
            messageId -> false,
            () -> {},
            offset -> {});

    ChatTranscriptInteractionCoordinator coordinatorForBlankNick =
        coordinator(
            () -> new TargetRef("libera", "#ircafe"),
            target -> {},
            () -> {},
            () -> {},
            req -> {},
            cmd -> {},
            (target, messageId) -> -1,
            messageId -> false,
            () -> {},
            offset -> {});

    assertFalse(coordinatorForStatus.onNickClicked("alice"));
    assertFalse(coordinatorForBlankNick.onNickClicked("   "));
  }

  @Test
  void onChannelClickedActivatesTargetAndEmitsJoin() {
    TargetRef activeTarget = new TargetRef("libera", "#ircafe");
    AtomicReference<TargetRef> activatedTarget = new AtomicReference<>();
    AtomicReference<String> emittedCommand = new AtomicReference<>();

    ChatTranscriptInteractionCoordinator coordinator =
        coordinator(
            () -> activeTarget,
            activatedTarget::set,
            () -> {},
            () -> {},
            req -> {},
            emittedCommand::set,
            (target, messageId) -> -1,
            messageId -> false,
            () -> {},
            offset -> {});

    boolean handled = coordinator.onChannelClicked("  #java  ");

    assertTrue(handled);
    assertEquals(activeTarget, activatedTarget.get());
    assertEquals("/join #java", emittedCommand.get());
  }

  @Test
  void onMessageReferenceClickedUsesTranscriptOffsetWhenFound() {
    TargetRef activeTarget = new TargetRef("libera", "#ircafe");
    AtomicInteger followTailDisableCalls = new AtomicInteger();
    AtomicInteger scrolledTo = new AtomicInteger(-1);
    AtomicInteger historyCalls = new AtomicInteger();

    ChatTranscriptInteractionCoordinator coordinator =
        coordinator(
            () -> activeTarget,
            target -> {},
            () -> {},
            () -> {},
            req -> {},
            cmd -> {},
            (target, messageId) -> 123,
            messageId -> {
              historyCalls.incrementAndGet();
              return false;
            },
            followTailDisableCalls::incrementAndGet,
            scrolledTo::set);

    boolean handled = coordinator.onMessageReferenceClicked("msg-1");

    assertTrue(handled);
    assertEquals(1, followTailDisableCalls.get());
    assertEquals(123, scrolledTo.get());
    assertEquals(0, historyCalls.get());
  }

  @Test
  void onMessageReferenceClickedFallsBackToHistoryRequester() {
    TargetRef activeTarget = new TargetRef("libera", "#ircafe");
    AtomicReference<String> requestedMessageId = new AtomicReference<>();

    ChatTranscriptInteractionCoordinator coordinator =
        coordinator(
            () -> activeTarget,
            target -> {},
            () -> {},
            () -> {},
            req -> {},
            cmd -> {},
            (target, messageId) -> -1,
            messageId -> {
              requestedMessageId.set(messageId);
              return true;
            },
            () -> {},
            offset -> {});

    boolean handled = coordinator.onMessageReferenceClicked("msg-42");

    assertTrue(handled);
    assertEquals("msg-42", requestedMessageId.get());
  }

  @Test
  void onMessageReferenceClickedReturnsFalseForUiOnlyTargets() {
    AtomicInteger historyCalls = new AtomicInteger();

    ChatTranscriptInteractionCoordinator coordinator =
        coordinator(
            () -> TargetRef.channelList("libera"),
            target -> {},
            () -> {},
            () -> {},
            req -> {},
            cmd -> {},
            (target, messageId) -> -1,
            messageId -> {
              historyCalls.incrementAndGet();
              return true;
            },
            () -> {},
            offset -> {});

    boolean handled = coordinator.onMessageReferenceClicked("msg-1");

    assertFalse(handled);
    assertEquals(0, historyCalls.get());
  }

  private static ChatTranscriptInteractionCoordinator coordinator(
      java.util.function.Supplier<TargetRef> activeTargetSupplier,
      java.util.function.Consumer<TargetRef> targetActivator,
      Runnable inputActivator,
      Runnable inputFocuser,
      java.util.function.Consumer<PrivateMessageRequest> openPrivateEmitter,
      java.util.function.Consumer<String> outboundEmitter,
      java.util.function.BiFunction<TargetRef, String, Integer> messageOffsetLookup,
      java.util.function.Predicate<String> historyAroundRequester,
      Runnable disableFollowTail,
      java.util.function.IntConsumer scrollToTranscriptOffset) {
    return new ChatTranscriptInteractionCoordinator(
        activeTargetSupplier,
        targetActivator,
        inputActivator,
        inputFocuser,
        openPrivateEmitter,
        outboundEmitter,
        messageOffsetLookup,
        historyAroundRequester,
        disableFollowTail,
        scrollToTranscriptOffset);
  }
}

package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.ChatDockable;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Handles transcript click interactions for {@link ChatDockable}. */
public final class ChatTranscriptInteractionCoordinator {

  private final Supplier<TargetRef> activeTargetSupplier;
  private final Consumer<TargetRef> targetActivator;
  private final Runnable inputActivator;
  private final Runnable inputFocuser;
  private final Consumer<PrivateMessageRequest> openPrivateEmitter;
  private final Consumer<String> outboundEmitter;
  private final BiFunction<TargetRef, String, Integer> messageOffsetLookup;
  private final Predicate<String> historyAroundRequester;
  private final Runnable disableFollowTail;
  private final IntConsumer scrollToTranscriptOffset;

  public ChatTranscriptInteractionCoordinator(
      Supplier<TargetRef> activeTargetSupplier,
      Consumer<TargetRef> targetActivator,
      Runnable inputActivator,
      Runnable inputFocuser,
      Consumer<PrivateMessageRequest> openPrivateEmitter,
      Consumer<String> outboundEmitter,
      BiFunction<TargetRef, String, Integer> messageOffsetLookup,
      Predicate<String> historyAroundRequester,
      Runnable disableFollowTail,
      IntConsumer scrollToTranscriptOffset) {
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
    this.targetActivator = Objects.requireNonNull(targetActivator, "targetActivator");
    this.inputActivator = Objects.requireNonNull(inputActivator, "inputActivator");
    this.inputFocuser = Objects.requireNonNull(inputFocuser, "inputFocuser");
    this.openPrivateEmitter = Objects.requireNonNull(openPrivateEmitter, "openPrivateEmitter");
    this.outboundEmitter = Objects.requireNonNull(outboundEmitter, "outboundEmitter");
    this.messageOffsetLookup = Objects.requireNonNull(messageOffsetLookup, "messageOffsetLookup");
    this.historyAroundRequester =
        Objects.requireNonNull(historyAroundRequester, "historyAroundRequester");
    this.disableFollowTail = Objects.requireNonNull(disableFollowTail, "disableFollowTail");
    this.scrollToTranscriptOffset =
        Objects.requireNonNull(scrollToTranscriptOffset, "scrollToTranscriptOffset");
  }

  public void onTranscriptClicked() {
    // Clicking the main transcript should make it the active target for input/status.
    // This does NOT change what the main chat is already displaying.
    TargetRef activeTarget = activeTargetSupplier.get();
    if (activeTarget == null) return;
    targetActivator.accept(activeTarget);
    inputActivator.run();
    inputFocuser.run();
  }

  public boolean onNickClicked(String nick) {
    TargetRef activeTarget = activeTargetSupplier.get();
    if (activeTarget == null || !activeTarget.isChannel()) return false;
    if (nick == null || nick.isBlank()) return false;

    openPrivateEmitter.accept(new PrivateMessageRequest(activeTarget.serverId(), nick));
    return true;
  }

  public boolean onChannelClicked(String channel) {
    TargetRef activeTarget = activeTargetSupplier.get();
    if (activeTarget == null) return false;
    if (channel == null || channel.isBlank()) return false;

    // Ensure the app's "active target" (server context) matches the transcript we clicked in.
    targetActivator.accept(activeTarget);

    // Delegate join to the normal command pipeline so config/auto-join behavior stays consistent.
    outboundEmitter.accept("/join " + channel.trim());
    return true;
  }

  public boolean onMessageReferenceClicked(String messageId) {
    TargetRef activeTarget = activeTargetSupplier.get();
    if (activeTarget == null || activeTarget.isUiOnly()) return false;
    int offset = messageOffsetLookup.apply(activeTarget, messageId);
    if (offset >= 0) {
      disableFollowTail.run();
      scrollToTranscriptOffset.accept(offset);
      return true;
    }
    return historyAroundRequester.test(messageId);
  }
}

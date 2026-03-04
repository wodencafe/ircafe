package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import cafe.woden.ircclient.ui.servertree.ServerTreeEdtExecutor;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestEmitter;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/** Encapsulates channel-target-only state mutations, requests, and policy checks. */
public final class ServerTreeChannelTargetOperations {

  private final ServerTreeEdtExecutor edtExecutor;
  private final ServerTreeChannelStateCoordinator channelStateCoordinator;
  private final ServerTreeRequestEmitter requestEmitter;
  private final BiConsumer<TargetRef, Boolean> onChannelMutedStateChanged;

  private volatile BiPredicate<String, String> canEditChannelModes = (serverId, channel) -> false;

  public ServerTreeChannelTargetOperations(
      ServerTreeEdtExecutor edtExecutor,
      ServerTreeChannelStateCoordinator channelStateCoordinator,
      ServerTreeRequestEmitter requestEmitter,
      BiConsumer<TargetRef, Boolean> onChannelMutedStateChanged) {
    this.edtExecutor = Objects.requireNonNull(edtExecutor, "edtExecutor");
    this.channelStateCoordinator =
        Objects.requireNonNull(channelStateCoordinator, "channelStateCoordinator");
    this.requestEmitter = Objects.requireNonNull(requestEmitter, "requestEmitter");
    this.onChannelMutedStateChanged =
        Objects.requireNonNull(onChannelMutedStateChanged, "onChannelMutedStateChanged");
  }

  public void setCanEditChannelModes(BiPredicate<String, String> canEditChannelModes) {
    this.canEditChannelModes =
        canEditChannelModes == null ? (serverId, channel) -> false : canEditChannelModes;
  }

  public boolean canEditChannelModesForTarget(TargetRef target) {
    if (!isChannelTarget(target)) return false;
    BiPredicate<String, String> predicate = canEditChannelModes;
    if (predicate == null) return false;
    try {
      return predicate.test(target.serverId(), target.target());
    } catch (Exception ignored) {
      return false;
    }
  }

  public boolean isChannelAutoReattach(TargetRef ref) {
    return readChannelState(ref, true, channelStateCoordinator::isChannelAutoReattach);
  }

  public void setChannelAutoReattach(TargetRef ref, boolean autoReattach) {
    writeChannelState(ref, () -> channelStateCoordinator.setChannelAutoReattach(ref, autoReattach));
  }

  public boolean isChannelPinned(TargetRef ref) {
    return readChannelState(ref, false, channelStateCoordinator::isChannelPinned);
  }

  public void setChannelPinned(TargetRef ref, boolean pinned) {
    writeChannelState(ref, () -> channelStateCoordinator.setChannelPinned(ref, pinned));
  }

  public boolean isChannelMuted(TargetRef ref) {
    return readChannelState(ref, false, channelStateCoordinator::isChannelMuted);
  }

  public void setChannelMuted(TargetRef ref, boolean muted) {
    writeChannelState(
        ref,
        () -> {
          channelStateCoordinator.setChannelMuted(ref, muted);
          onChannelMutedStateChanged.accept(ref, muted);
        });
  }

  public void requestJoinChannel(TargetRef target) {
    emitChannelRequest(target, requestEmitter::emitJoinChannel);
  }

  public void requestDisconnectChannel(TargetRef target) {
    emitChannelRequest(target, requestEmitter::emitDisconnectChannel);
  }

  public void requestCloseChannel(TargetRef target) {
    emitChannelRequest(target, requestEmitter::emitCloseChannel);
  }

  private boolean readChannelState(
      TargetRef ref, boolean fallback, Function<TargetRef, Boolean> reader) {
    if (!isChannelTarget(ref)) return fallback;
    return edtExecutor.read(() -> reader.apply(ref), fallback, null);
  }

  private void writeChannelState(TargetRef ref, Runnable writer) {
    if (!isChannelTarget(ref)) return;
    edtExecutor.write(writer);
  }

  private void emitChannelRequest(TargetRef target, Consumer<TargetRef> emitter) {
    if (!isChannelTarget(target)) return;
    emitter.accept(target);
  }

  private static boolean isChannelTarget(TargetRef ref) {
    return ServerTreeConventions.isChannelTarget(ref);
  }
}

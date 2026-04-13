package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import cafe.woden.ircclient.ui.servertree.ServerTreeEdtExecutor;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestEmitter;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Encapsulates channel-target-only state mutations, requests, and policy checks. */
@Component
@RequiredArgsConstructor
public final class ServerTreeChannelTargetOperations {

  @NonNull private final ServerTreeEdtExecutor edtExecutor;

  public interface Context {
    void setCanEditChannelModes(BiPredicate<String, String> canEditChannelModes);

    boolean canEditChannelModes(String serverId, String channel);

    boolean isChannelAutoReattach(TargetRef ref);

    void setChannelAutoReattach(TargetRef ref, boolean autoReattach);

    boolean isChannelPinned(TargetRef ref);

    void setChannelPinned(TargetRef ref, boolean pinned);

    boolean isChannelMuted(TargetRef ref);

    void setChannelMuted(TargetRef ref, boolean muted);

    void onChannelMutedStateChanged(TargetRef ref, boolean muted);

    void emitJoinChannel(TargetRef target);

    void emitDisconnectChannel(TargetRef target);

    void emitCloseChannel(TargetRef target);
  }

  public static Context context(
      ServerTreeChannelStateCoordinator channelStateCoordinator,
      ServerTreeRequestEmitter requestEmitter,
      BiConsumer<TargetRef, Boolean> onChannelMutedStateChanged) {
    Objects.requireNonNull(channelStateCoordinator, "channelStateCoordinator");
    Objects.requireNonNull(requestEmitter, "requestEmitter");
    Objects.requireNonNull(onChannelMutedStateChanged, "onChannelMutedStateChanged");
    return new Context() {
      private volatile BiPredicate<String, String> canEditChannelModes =
          (serverId, channel) -> false;

      @Override
      public void setCanEditChannelModes(BiPredicate<String, String> canEditChannelModes) {
        this.canEditChannelModes =
            canEditChannelModes == null ? (serverId, channel) -> false : canEditChannelModes;
      }

      @Override
      public boolean canEditChannelModes(String serverId, String channel) {
        return canEditChannelModes.test(serverId, channel);
      }

      @Override
      public boolean isChannelAutoReattach(TargetRef ref) {
        return channelStateCoordinator.isChannelAutoReattach(ref);
      }

      @Override
      public void setChannelAutoReattach(TargetRef ref, boolean autoReattach) {
        channelStateCoordinator.setChannelAutoReattach(ref, autoReattach);
      }

      @Override
      public boolean isChannelPinned(TargetRef ref) {
        return channelStateCoordinator.isChannelPinned(ref);
      }

      @Override
      public void setChannelPinned(TargetRef ref, boolean pinned) {
        channelStateCoordinator.setChannelPinned(ref, pinned);
      }

      @Override
      public boolean isChannelMuted(TargetRef ref) {
        return channelStateCoordinator.isChannelMuted(ref);
      }

      @Override
      public void setChannelMuted(TargetRef ref, boolean muted) {
        channelStateCoordinator.setChannelMuted(ref, muted);
      }

      @Override
      public void onChannelMutedStateChanged(TargetRef ref, boolean muted) {
        onChannelMutedStateChanged.accept(ref, muted);
      }

      @Override
      public void emitJoinChannel(TargetRef target) {
        requestEmitter.emitJoinChannel(target);
      }

      @Override
      public void emitDisconnectChannel(TargetRef target) {
        requestEmitter.emitDisconnectChannel(target);
      }

      @Override
      public void emitCloseChannel(TargetRef target) {
        requestEmitter.emitCloseChannel(target);
      }
    };
  }

  public void setCanEditChannelModes(
      Context context, BiPredicate<String, String> canEditChannelModes) {
    Objects.requireNonNull(context, "context").setCanEditChannelModes(canEditChannelModes);
  }

  public boolean canEditChannelModesForTarget(Context context, TargetRef target) {
    Context in = Objects.requireNonNull(context, "context");
    if (!isChannelTarget(target)) return false;
    try {
      return in.canEditChannelModes(target.serverId(), target.target());
    } catch (Exception ignored) {
      return false;
    }
  }

  public boolean isChannelAutoReattach(Context context, TargetRef ref) {
    return readChannelState(context, ref, true, Context::isChannelAutoReattach);
  }

  public void setChannelAutoReattach(Context context, TargetRef ref, boolean autoReattach) {
    writeChannelState(context, ref, in -> in.setChannelAutoReattach(ref, autoReattach));
  }

  public boolean isChannelPinned(Context context, TargetRef ref) {
    return readChannelState(context, ref, false, Context::isChannelPinned);
  }

  public void setChannelPinned(Context context, TargetRef ref, boolean pinned) {
    writeChannelState(context, ref, in -> in.setChannelPinned(ref, pinned));
  }

  public boolean isChannelMuted(Context context, TargetRef ref) {
    return readChannelState(context, ref, false, Context::isChannelMuted);
  }

  public void setChannelMuted(Context context, TargetRef ref, boolean muted) {
    writeChannelState(
        context,
        ref,
        in -> {
          in.setChannelMuted(ref, muted);
          in.onChannelMutedStateChanged(ref, muted);
        });
  }

  public void requestJoinChannel(Context context, TargetRef target) {
    emitChannelRequest(context, target, Context::emitJoinChannel);
  }

  public void requestDisconnectChannel(Context context, TargetRef target) {
    emitChannelRequest(context, target, Context::emitDisconnectChannel);
  }

  public void requestCloseChannel(Context context, TargetRef target) {
    emitChannelRequest(context, target, Context::emitCloseChannel);
  }

  private boolean readChannelState(
      Context context,
      TargetRef ref,
      boolean fallback,
      BiFunction<Context, TargetRef, Boolean> reader) {
    if (!isChannelTarget(ref)) return fallback;
    Context in = Objects.requireNonNull(context, "context");
    return edtExecutor.read(() -> reader.apply(in, ref), fallback, null);
  }

  private void writeChannelState(Context context, TargetRef ref, Consumer<Context> writer) {
    if (!isChannelTarget(ref)) return;
    Context in = Objects.requireNonNull(context, "context");
    edtExecutor.write(() -> writer.accept(in));
  }

  private void emitChannelRequest(
      Context context, TargetRef target, BiConsumer<Context, TargetRef> emitter) {
    if (!isChannelTarget(target)) return;
    emitter.accept(Objects.requireNonNull(context, "context"), target);
  }

  private static boolean isChannelTarget(TargetRef ref) {
    return ServerTreeConventions.isChannelTarget(ref);
  }
}

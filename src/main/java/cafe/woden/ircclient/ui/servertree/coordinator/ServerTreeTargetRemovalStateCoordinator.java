package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.config.api.IrcSessionRuntimeConfigPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeChannelStateStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Handles non-tree state cleanup when a target is removed from the server tree. */
@org.springframework.stereotype.Component
public final class ServerTreeTargetRemovalStateCoordinator {

  public interface Context {
    boolean isPrivateMessageTarget(TargetRef ref);

    boolean shouldPersistPrivateMessageList();

    String foldChannelKey(String channelName);

    void emitManagedChannelsChanged(String serverId);

    void removePrivateMessageOnline(TargetRef ref);

    void forgetPrivateMessageTarget(String serverId, String target);

    void forgetJoinedChannel(String serverId, String channelName);

    void removeChannelAutoReattach(String serverId, String channelKey);

    void removeChannelActivityRank(String serverId, String channelKey);

    void removeChannelPinned(String serverId, String channelKey);

    void removeChannelMuted(String serverId, String channelKey);

    void removeChannelCustomOrderEntries(String serverId, String channelKey);
  }

  public static Context context(
      ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore,
      IrcSessionRuntimeConfigPort runtimeConfig,
      ServerTreeChannelStateStore channelStateStore,
      Predicate<TargetRef> isPrivateMessageTarget,
      BooleanSupplier shouldPersistPrivateMessageList,
      Function<String, String> foldChannelKey,
      Consumer<String> emitManagedChannelsChanged) {
    Objects.requireNonNull(privateMessageOnlineStateStore, "privateMessageOnlineStateStore");
    Objects.requireNonNull(channelStateStore, "channelStateStore");
    Objects.requireNonNull(isPrivateMessageTarget, "isPrivateMessageTarget");
    Objects.requireNonNull(shouldPersistPrivateMessageList, "shouldPersistPrivateMessageList");
    Objects.requireNonNull(foldChannelKey, "foldChannelKey");
    Objects.requireNonNull(emitManagedChannelsChanged, "emitManagedChannelsChanged");
    return new Context() {
      @Override
      public boolean isPrivateMessageTarget(TargetRef ref) {
        return isPrivateMessageTarget.test(ref);
      }

      @Override
      public boolean shouldPersistPrivateMessageList() {
        return shouldPersistPrivateMessageList.getAsBoolean();
      }

      @Override
      public String foldChannelKey(String channelName) {
        return foldChannelKey.apply(channelName);
      }

      @Override
      public void emitManagedChannelsChanged(String serverId) {
        emitManagedChannelsChanged.accept(serverId);
      }

      @Override
      public void removePrivateMessageOnline(TargetRef ref) {
        privateMessageOnlineStateStore.remove(ref);
      }

      @Override
      public void forgetPrivateMessageTarget(String serverId, String target) {
        if (runtimeConfig == null) return;
        runtimeConfig.forgetPrivateMessageTarget(serverId, target);
      }

      @Override
      public void forgetJoinedChannel(String serverId, String channelName) {
        if (runtimeConfig == null) return;
        runtimeConfig.forgetJoinedChannel(serverId, channelName);
      }

      @Override
      public void removeChannelAutoReattach(String serverId, String channelKey) {
        var autoByChannel = channelStateStore.channelAutoReattachByServer().get(serverId);
        if (autoByChannel != null) {
          autoByChannel.remove(channelKey);
        }
      }

      @Override
      public void removeChannelActivityRank(String serverId, String channelKey) {
        var activityByChannel = channelStateStore.channelActivityRankByServer().get(serverId);
        if (activityByChannel != null) {
          activityByChannel.remove(channelKey);
        }
      }

      @Override
      public void removeChannelPinned(String serverId, String channelKey) {
        var pinnedByChannel = channelStateStore.channelPinnedByServer().get(serverId);
        if (pinnedByChannel != null) {
          pinnedByChannel.remove(channelKey);
        }
      }

      @Override
      public void removeChannelMuted(String serverId, String channelKey) {
        var mutedByChannel = channelStateStore.channelMutedByServer().get(serverId);
        if (mutedByChannel != null) {
          mutedByChannel.remove(channelKey);
        }
      }

      @Override
      public void removeChannelCustomOrderEntries(String serverId, String channelKey) {
        var customOrder = channelStateStore.channelCustomOrderByServer().get(serverId);
        if (customOrder != null) {
          customOrder.removeIf(channelName -> foldChannelKey(channelName).equals(channelKey));
        }
      }
    };
  }

  public void cleanupForRemovedTarget(Context context, TargetRef ref) {
    Context in = Objects.requireNonNull(context, "context");
    if (ref == null) return;

    if (in.isPrivateMessageTarget(ref)) {
      in.removePrivateMessageOnline(ref);
      if (in.shouldPersistPrivateMessageList()) {
        in.forgetPrivateMessageTarget(ref.serverId(), ref.target());
      }
    }

    if (!ref.isChannel()) return;
    String sid = normalizeServerId(ref.serverId());
    String key = in.foldChannelKey(ref.target());
    if (sid.isEmpty() || key.isEmpty()) return;

    // Removing a channel node from the tree is a permanent close, not a detach.
    in.forgetJoinedChannel(sid, ref.target());
    in.removeChannelAutoReattach(sid, key);
    in.removeChannelActivityRank(sid, key);
    in.removeChannelPinned(sid, key);
    in.removeChannelMuted(sid, key);
    in.removeChannelCustomOrderEntries(sid, key);
    in.emitManagedChannelsChanged(sid);
  }

  private static String normalizeServerId(String serverId) {
    return ServerTreeConventions.normalizeServerId(serverId);
  }
}

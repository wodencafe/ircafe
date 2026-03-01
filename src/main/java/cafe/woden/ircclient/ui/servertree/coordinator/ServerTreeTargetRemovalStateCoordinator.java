package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/** Handles non-tree state cleanup when a target is removed from the server tree. */
public final class ServerTreeTargetRemovalStateCoordinator {

  public interface Context {
    boolean isPrivateMessageTarget(TargetRef ref);

    boolean shouldPersistPrivateMessageList();

    String foldChannelKey(String channelName);

    void emitManagedChannelsChanged(String serverId);
  }

  private final ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore;
  private final RuntimeConfigStore runtimeConfig;
  private final Map<String, Map<String, Boolean>> channelAutoReattachByServer;
  private final Map<String, Map<String, Long>> channelActivityRankByServer;
  private final Map<String, ArrayList<String>> channelCustomOrderByServer;
  private final Context context;

  public ServerTreeTargetRemovalStateCoordinator(
      ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore,
      RuntimeConfigStore runtimeConfig,
      Map<String, Map<String, Boolean>> channelAutoReattachByServer,
      Map<String, Map<String, Long>> channelActivityRankByServer,
      Map<String, ArrayList<String>> channelCustomOrderByServer,
      Context context) {
    this.privateMessageOnlineStateStore =
        Objects.requireNonNull(privateMessageOnlineStateStore, "privateMessageOnlineStateStore");
    this.runtimeConfig = runtimeConfig;
    this.channelAutoReattachByServer =
        Objects.requireNonNull(channelAutoReattachByServer, "channelAutoReattachByServer");
    this.channelActivityRankByServer =
        Objects.requireNonNull(channelActivityRankByServer, "channelActivityRankByServer");
    this.channelCustomOrderByServer =
        Objects.requireNonNull(channelCustomOrderByServer, "channelCustomOrderByServer");
    this.context = Objects.requireNonNull(context, "context");
  }

  public void cleanupForRemovedTarget(TargetRef ref) {
    if (ref == null) return;

    if (context.isPrivateMessageTarget(ref)) {
      privateMessageOnlineStateStore.remove(ref);
      if (context.shouldPersistPrivateMessageList() && runtimeConfig != null) {
        runtimeConfig.forgetPrivateMessageTarget(ref.serverId(), ref.target());
      }
    }

    if (!ref.isChannel()) return;
    String sid = normalizeServerId(ref.serverId());
    String key = context.foldChannelKey(ref.target());
    if (sid.isEmpty() || key.isEmpty()) return;

    Map<String, Boolean> autoByChannel = channelAutoReattachByServer.get(sid);
    if (autoByChannel != null) {
      autoByChannel.remove(key);
    }
    Map<String, Long> activityByChannel = channelActivityRankByServer.get(sid);
    if (activityByChannel != null) {
      activityByChannel.remove(key);
    }
    ArrayList<String> customOrder = channelCustomOrderByServer.get(sid);
    if (customOrder != null) {
      customOrder.removeIf(channelName -> context.foldChannelKey(channelName).equals(key));
    }
    context.emitManagedChannelsChanged(sid);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}

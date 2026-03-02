package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Adapter for {@link ServerTreeTargetRemovalStateCoordinator.Context}. */
public final class ServerTreeTargetRemovalStateCoordinatorContextAdapter
    implements ServerTreeTargetRemovalStateCoordinator.Context {

  private final Predicate<TargetRef> isPrivateMessageTarget;
  private final BooleanSupplier shouldPersistPrivateMessageList;
  private final Function<String, String> foldChannelKey;
  private final Consumer<String> emitManagedChannelsChanged;

  public ServerTreeTargetRemovalStateCoordinatorContextAdapter(
      Predicate<TargetRef> isPrivateMessageTarget,
      BooleanSupplier shouldPersistPrivateMessageList,
      Function<String, String> foldChannelKey,
      Consumer<String> emitManagedChannelsChanged) {
    this.isPrivateMessageTarget =
        Objects.requireNonNull(isPrivateMessageTarget, "isPrivateMessageTarget");
    this.shouldPersistPrivateMessageList =
        Objects.requireNonNull(shouldPersistPrivateMessageList, "shouldPersistPrivateMessageList");
    this.foldChannelKey = Objects.requireNonNull(foldChannelKey, "foldChannelKey");
    this.emitManagedChannelsChanged =
        Objects.requireNonNull(emitManagedChannelsChanged, "emitManagedChannelsChanged");
  }

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
}

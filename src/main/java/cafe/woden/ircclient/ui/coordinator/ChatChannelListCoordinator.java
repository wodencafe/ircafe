package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.UserListDockable;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Wires channel-list UI actions and projections for {@link ChatDockable}. */
public final class ChatChannelListCoordinator {

  private static final Logger log = LoggerFactory.getLogger(ChatChannelListCoordinator.class);

  private final ChannelListPanel channelListPanel;
  private final ServerTreeDockable serverTree;
  private final OutboundLineBus outboundBus;
  private final UserListStore userListStore;
  private final UserListDockable usersDock;
  private final Supplier<TargetRef> activeTargetSupplier;
  private final BiFunction<String, String, String> topicLookup;
  private final BiFunction<String, String, List<String>> banListSnapshotLookup;

  private final FlowableProcessor<String> channelListCommandRequests =
      PublishProcessor.<String>create().toSerialized();

  public ChatChannelListCoordinator(
      ChannelListPanel channelListPanel,
      ServerTreeDockable serverTree,
      OutboundLineBus outboundBus,
      UserListStore userListStore,
      UserListDockable usersDock,
      Supplier<TargetRef> activeTargetSupplier,
      BiFunction<String, String, String> topicLookup,
      BiFunction<String, String, List<String>> banListSnapshotLookup) {
    this.channelListPanel = Objects.requireNonNull(channelListPanel, "channelListPanel");
    this.serverTree = serverTree;
    this.outboundBus = Objects.requireNonNull(outboundBus, "outboundBus");
    this.userListStore = Objects.requireNonNull(userListStore, "userListStore");
    this.usersDock = Objects.requireNonNull(usersDock, "usersDock");
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
    this.topicLookup = Objects.requireNonNull(topicLookup, "topicLookup");
    this.banListSnapshotLookup =
        Objects.requireNonNull(banListSnapshotLookup, "banListSnapshotLookup");
  }

  public void bind(CompositeDisposable disposables) {
    Objects.requireNonNull(disposables, "disposables");

    channelListPanel.setOnJoinChannel(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.requestJoinChannel(new TargetRef(sid, ch));
          refreshManagedChannelsCard(sid);
        });
    channelListPanel.setOnRunListRequest(
        () -> {
          String sid = channelListServerIdForActions();
          if (sid.isBlank()) return;
          serverTree.selectTarget(TargetRef.channelList(sid));
          channelListCommandRequests.onNext("/list");
        });
    channelListPanel.setOnRunAlisRequest(
        command -> {
          String sid = channelListServerIdForActions();
          String cmd = Objects.toString(command, "").trim();
          if (sid.isBlank() || cmd.isEmpty()) return;
          serverTree.selectTarget(TargetRef.channelList(sid));
          channelListCommandRequests.onNext(cmd);
        });
    channelListPanel.setOnAddChannelRequest(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          TargetRef ref = new TargetRef(sid, ch);
          serverTree.ensureNode(ref);
          serverTree.setChannelAutoReattach(ref, true);
          serverTree.requestJoinChannel(ref);
          refreshManagedChannelsCard(sid);
        });
    channelListPanel.setOnReconnectChannelRequest(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.requestJoinChannel(new TargetRef(sid, ch));
          refreshManagedChannelsCard(sid);
        });
    channelListPanel.setOnDisconnectChannelRequest(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.requestDisconnectChannel(new TargetRef(sid, ch));
          refreshManagedChannelsCard(sid);
        });
    channelListPanel.setOnCloseChannelRequest(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.requestCloseChannel(new TargetRef(sid, ch));
          refreshManagedChannelsCard(sid);
        });
    channelListPanel.setOnAutoReattachChanged(
        (channel, autoReattach) -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.setChannelAutoReattach(new TargetRef(sid, ch), autoReattach);
          refreshManagedChannelsCard(sid);
        });
    channelListPanel.setOnManagedSortModeChanged(
        mode -> {
          String sid = channelListServerIdForActions();
          if (sid.isBlank()) return;
          ServerTreeDockable.ChannelSortMode mapped = toServerTreeSortMode(mode);
          serverTree.setChannelSortModeForServer(sid, mapped);
          refreshManagedChannelsCard(sid);
        });
    channelListPanel.setOnManagedCustomOrderChanged(
        order -> {
          String sid = channelListServerIdForActions();
          if (sid.isBlank()) return;
          serverTree.setChannelCustomOrderForServer(sid, order);
          refreshManagedChannelsCard(sid);
        });
    channelListPanel.setOnManagedChannelSelected(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          updateUsersDockForChannel(sid, ch);
        });
    channelListPanel.setOnChannelTopicRequest(
        (serverId, channel) -> {
          String sid = Objects.toString(serverId, "").trim();
          if (sid.isBlank()) sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return "";
          return topicLookup.apply(sid, ch);
        });
    channelListPanel.setOnChannelBanListSnapshotRequest(
        (serverId, channel) -> banListSnapshotLookup.apply(serverId, channel));
    channelListPanel.setOnChannelBanListRefreshRequest(
        (serverId, channel) -> {
          String sid = Objects.toString(serverId, "").trim();
          if (sid.isBlank()) sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          serverTree.selectTarget(TargetRef.channelList(sid));
          outboundBus.emit("/mode " + ch + " +b");
        });

    disposables.add(
        channelListCommandRequests
            .debounce(150, TimeUnit.MILLISECONDS)
            .throttleFirst(5, TimeUnit.SECONDS)
            .subscribe(
                outboundBus::emit,
                err -> log.debug("[ircafe] channel-list command flow failed", err)));
    disposables.add(
        serverTree
            .managedChannelsChangedByServer()
            .subscribe(
                sid -> {
                  String changed = Objects.toString(sid, "").trim();
                  if (changed.isEmpty()) return;
                  if (!changed.equalsIgnoreCase(channelListPanel.currentServerId())) return;
                  refreshManagedChannelsCard(changed);
                },
                err -> log.debug("[ircafe] managed-channel refresh stream failed", err)));
  }

  public void refreshManagedChannelsCard(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty() || serverTree == null) {
      channelListPanel.setManagedChannels(sid, List.of(), ChannelListPanel.ManagedSortMode.CUSTOM);
      return;
    }

    List<ChannelListPanel.ManagedChannelRow> rows =
        serverTree.managedChannelsForServer(sid).stream()
            .map(
                entry -> {
                  int users = userListStore.get(sid, entry.channel()).size();
                  return new ChannelListPanel.ManagedChannelRow(
                      entry.channel(),
                      entry.detached(),
                      entry.autoReattach(),
                      users,
                      entry.notifications(),
                      modeSummaryForChannel());
                })
            .toList();
    ChannelListPanel.ManagedSortMode sortMode =
        toManagedSortMode(serverTree.channelSortModeForServer(sid));
    channelListPanel.setManagedChannels(sid, rows, sortMode);
  }

  private static ServerTreeDockable.ChannelSortMode toServerTreeSortMode(
      ChannelListPanel.ManagedSortMode mode) {
    if (mode == null) return ServerTreeDockable.ChannelSortMode.CUSTOM;
    return switch (mode) {
      case ALPHABETICAL -> ServerTreeDockable.ChannelSortMode.ALPHABETICAL;
      case MOST_RECENT_ACTIVITY -> ServerTreeDockable.ChannelSortMode.MOST_RECENT_ACTIVITY;
      case MOST_UNREAD_MESSAGES -> ServerTreeDockable.ChannelSortMode.MOST_UNREAD_MESSAGES;
      case MOST_UNREAD_NOTIFICATIONS ->
          ServerTreeDockable.ChannelSortMode.MOST_UNREAD_NOTIFICATIONS;
      case CUSTOM -> ServerTreeDockable.ChannelSortMode.CUSTOM;
    };
  }

  private static ChannelListPanel.ManagedSortMode toManagedSortMode(
      ServerTreeDockable.ChannelSortMode mode) {
    if (mode == null) return ChannelListPanel.ManagedSortMode.CUSTOM;
    return switch (mode) {
      case ALPHABETICAL -> ChannelListPanel.ManagedSortMode.ALPHABETICAL;
      case MOST_RECENT_ACTIVITY -> ChannelListPanel.ManagedSortMode.MOST_RECENT_ACTIVITY;
      case MOST_UNREAD_MESSAGES -> ChannelListPanel.ManagedSortMode.MOST_UNREAD_MESSAGES;
      case MOST_UNREAD_NOTIFICATIONS -> ChannelListPanel.ManagedSortMode.MOST_UNREAD_NOTIFICATIONS;
      case CUSTOM -> ChannelListPanel.ManagedSortMode.CUSTOM;
    };
  }

  private void updateUsersDockForChannel(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;
    TargetRef target = new TargetRef(sid, ch);
    usersDock.setChannel(target);
    usersDock.setNicks(userListStore.get(sid, ch));
  }

  private String channelListServerIdForActions() {
    String sid = Objects.toString(channelListPanel.currentServerId(), "").trim();
    if (!sid.isEmpty()) return sid;

    TargetRef target = activeTargetSupplier.get();
    if (target == null) return "";
    return Objects.toString(target.serverId(), "").trim();
  }

  private static String modeSummaryForChannel() {
    return "(Unknown)";
  }

  private static String normalizeChannelName(String channel) {
    String c = Objects.toString(channel, "").trim();
    if (c.isEmpty()) return "";
    return (c.startsWith("#") || c.startsWith("&")) ? c : "";
  }
}

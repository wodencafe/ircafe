package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.UserListDockable;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Wires channel-list UI actions and projections for {@link ChatDockable}. */
public final class ChatChannelListCoordinator {

  private static final Logger log = LoggerFactory.getLogger(ChatChannelListCoordinator.class);
  private static final ModeRoutingPort NO_OP_MODE_ROUTING =
      new ModeRoutingPort() {
        @Override
        public void putPendingModeTarget(String serverId, String channel, TargetRef target) {}

        @Override
        public TargetRef removePendingModeTarget(String serverId, String channel) {
          return null;
        }

        @Override
        public TargetRef getPendingModeTarget(String serverId, String channel) {
          return null;
        }

        @Override
        public void clearServer(String serverId) {}
      };

  private final ChannelListPanel channelListPanel;
  private final ServerTreeDockable serverTree;
  private final OutboundLineBus outboundBus;
  private final IrcClientService irc;
  private final ModeRoutingPort modeRoutingState;
  private final UserListStore userListStore;
  private final UserListDockable usersDock;
  private final Supplier<TargetRef> activeTargetSupplier;
  private final Function<String, String> currentNickLookup;
  private final BiFunction<String, String, String> topicLookup;
  private final BiFunction<String, String, ChannelListPanel.BanListSnapshot> banListSnapshotLookup;
  private CompositeDisposable bindDisposables;

  private final FlowableProcessor<String> channelListCommandRequests =
      PublishProcessor.<String>create().toSerialized();

  public ChatChannelListCoordinator(
      ChannelListPanel channelListPanel,
      ServerTreeDockable serverTree,
      OutboundLineBus outboundBus,
      UserListStore userListStore,
      UserListDockable usersDock,
      Supplier<TargetRef> activeTargetSupplier,
      Function<String, String> currentNickLookup,
      BiFunction<String, String, String> topicLookup,
      BiFunction<String, String, ChannelListPanel.BanListSnapshot> banListSnapshotLookup) {
    this(
        channelListPanel,
        serverTree,
        outboundBus,
        userListStore,
        usersDock,
        activeTargetSupplier,
        currentNickLookup,
        topicLookup,
        banListSnapshotLookup,
        null,
        NO_OP_MODE_ROUTING);
  }

  public ChatChannelListCoordinator(
      ChannelListPanel channelListPanel,
      ServerTreeDockable serverTree,
      OutboundLineBus outboundBus,
      UserListStore userListStore,
      UserListDockable usersDock,
      Supplier<TargetRef> activeTargetSupplier,
      Function<String, String> currentNickLookup,
      BiFunction<String, String, String> topicLookup,
      BiFunction<String, String, ChannelListPanel.BanListSnapshot> banListSnapshotLookup,
      IrcClientService irc,
      ModeRoutingPort modeRoutingState) {
    this.channelListPanel = Objects.requireNonNull(channelListPanel, "channelListPanel");
    this.serverTree = serverTree;
    this.outboundBus = Objects.requireNonNull(outboundBus, "outboundBus");
    this.irc = irc;
    this.modeRoutingState = (modeRoutingState == null) ? NO_OP_MODE_ROUTING : modeRoutingState;
    this.userListStore = Objects.requireNonNull(userListStore, "userListStore");
    this.usersDock = Objects.requireNonNull(usersDock, "usersDock");
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
    this.currentNickLookup = Objects.requireNonNull(currentNickLookup, "currentNickLookup");
    this.topicLookup = Objects.requireNonNull(topicLookup, "topicLookup");
    this.banListSnapshotLookup =
        Objects.requireNonNull(banListSnapshotLookup, "banListSnapshotLookup");
  }

  public void bind(CompositeDisposable disposables) {
    Objects.requireNonNull(disposables, "disposables");
    this.bindDisposables = disposables;

    channelListPanel.setOnJoinChannel(
        channel -> {
          String sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          handleChannelListJoinSelection(sid, ch);
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
          serverTree.setChannelDisconnected(ref, true);
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
          if (sendRawModeCommand(disposables, sid, "MODE " + ch + " +b", "ban-list refresh")) {
            return;
          }
          serverTree.selectTarget(TargetRef.channelList(sid));
          outboundBus.emit("/mode " + ch + " +b");
        });
    channelListPanel.setOnChannelModeRefreshRequest(
        (serverId, channel) -> {
          String sid = Objects.toString(serverId, "").trim();
          if (sid.isBlank()) sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          if (sid.isBlank() || ch.isEmpty()) return;
          modeRoutingState.putPendingModeTarget(sid, ch, new TargetRef(sid, ch));
          if (sendRawModeCommand(disposables, sid, "MODE " + ch, "channel-mode refresh")) {
            return;
          }
          outboundBus.emit("/mode " + ch);
        });
    channelListPanel.setOnChannelModeSetRequest(
        (serverId, channel, modeSpec) -> {
          String sid = Objects.toString(serverId, "").trim();
          if (sid.isBlank()) sid = channelListServerIdForActions();
          String ch = normalizeChannelName(channel);
          String spec = Objects.toString(modeSpec, "").trim();
          if (sid.isBlank() || ch.isEmpty() || spec.isEmpty()) return;
          if (sendRawModeCommand(disposables, sid, "MODE " + ch + " " + spec, "channel-mode set")) {
            return;
          }
          outboundBus.emit("/mode " + ch + " " + spec);
        });
    channelListPanel.setCanEditChannelModes(this::canEditChannelModes);
    serverTree.setCanEditChannelModes(this::canEditChannelModes);

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
    Flowable<TargetRef> modeDetailsRequests = serverTree.channelModeDetailsRequests();
    if (modeDetailsRequests != null) {
      disposables.add(
          modeDetailsRequests.subscribe(
              this::openChannelModeDetailsFromTree,
              err -> log.debug("[ircafe] channel-mode details flow failed", err)));
    }
    Flowable<TargetRef> modeRefreshRequests = serverTree.channelModeRefreshRequests();
    if (modeRefreshRequests != null) {
      disposables.add(
          modeRefreshRequests.subscribe(
              this::refreshChannelModesFromTree,
              err -> log.debug("[ircafe] channel-mode refresh flow failed", err)));
    }
    Flowable<ServerTreeDockable.ChannelModeSetRequest> modeSetRequests =
        serverTree.channelModeSetRequests();
    if (modeSetRequests != null) {
      disposables.add(
          modeSetRequests.subscribe(
              this::setChannelModesFromTree,
              err -> log.debug("[ircafe] channel-mode set flow failed", err)));
    }
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
                      modeSummaryForChannel(sid, entry.channel()));
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

  private String modeSummaryForChannel(String serverId, String channel) {
    String modes = Objects.toString(channelListPanel.rawChannelModeSnapshot(serverId, channel), "");
    modes = modes.trim();
    if (!modes.isBlank()) return modes;
    return "(Unknown)";
  }

  private boolean canEditChannelModes(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isBlank()) sid = channelListServerIdForActions();
    String ch = normalizeChannelName(channel);
    if (sid.isBlank() || ch.isEmpty()) return false;

    String me = Objects.toString(currentNickLookup.apply(sid), "").trim();
    if (me.isEmpty()) return false;

    List<NickInfo> nicks = userListStore.get(sid, ch);
    for (NickInfo ni : nicks) {
      if (ni == null) continue;
      String nick = Objects.toString(ni.nick(), "").trim();
      if (!nick.equalsIgnoreCase(me)) continue;
      String prefix = Objects.toString(ni.prefix(), "");
      return prefix.contains("~")
          || prefix.contains("&")
          || prefix.contains("@")
          || prefix.contains("%");
    }
    return false;
  }

  private void openChannelModeDetailsFromTree(TargetRef target) {
    if (target == null || !target.isChannel()) return;
    String sid = Objects.toString(target.serverId(), "").trim();
    String channel = normalizeChannelName(target.target());
    if (sid.isBlank() || channel.isEmpty()) return;
    refreshManagedChannelsCard(sid);
    channelListPanel.showChannelDetails(sid, channel);
  }

  private void refreshChannelModesFromTree(TargetRef target) {
    if (target == null || !target.isChannel()) return;
    String sid = Objects.toString(target.serverId(), "").trim();
    String channel = normalizeChannelName(target.target());
    if (sid.isBlank() || channel.isEmpty()) return;
    serverTree.selectTarget(TargetRef.channelList(sid));
    modeRoutingState.putPendingModeTarget(sid, channel, new TargetRef(sid, channel));
    if (sendRawModeCommand(bindDisposables, sid, "MODE " + channel, "tree channel-mode refresh")) {
      return;
    }
    outboundBus.emit("/mode " + channel);
  }

  private void setChannelModesFromTree(ServerTreeDockable.ChannelModeSetRequest request) {
    if (request == null) return;
    TargetRef target = request.target();
    if (target == null || !target.isChannel()) return;
    String sid = Objects.toString(target.serverId(), "").trim();
    String channel = normalizeChannelName(target.target());
    String modeSpec = Objects.toString(request.modeSpec(), "").trim();
    if (sid.isBlank() || channel.isEmpty() || modeSpec.isEmpty()) return;
    if (!canEditChannelModes(sid, channel)) return;
    serverTree.selectTarget(TargetRef.channelList(sid));
    if (sendRawModeCommand(
        bindDisposables, sid, "MODE " + channel + " " + modeSpec, "tree channel-mode set")) {
      return;
    }
    outboundBus.emit("/mode " + channel + " " + modeSpec);
  }

  private boolean sendRawModeCommand(
      CompositeDisposable disposables, String serverId, String rawLine, String action) {
    if (irc == null) return false;
    Completable send = irc.sendRaw(serverId, rawLine);
    if (send == null) return false;
    if (disposables == null) {
      var unused =
          send.subscribe(
              () -> {},
              err ->
                  log.debug(
                      "[ircafe] raw MODE send failed action={} serverId={} line={}",
                      action,
                      serverId,
                      rawLine,
                      err));
      return true;
    }
    disposables.add(
        send.subscribe(
            () -> {},
            err ->
                log.debug(
                    "[ircafe] raw MODE send failed action={} serverId={} line={}",
                    action,
                    serverId,
                    rawLine,
                    err)));
    return true;
  }

  private static String normalizeChannelName(String channel) {
    String c = Objects.toString(channel, "").trim();
    if (c.isEmpty()) return "";
    return (c.startsWith("#") || c.startsWith("&")) ? c : "";
  }

  private void handleChannelListJoinSelection(String serverId, String channel) {
    TargetRef ref = new TargetRef(serverId, channel);
    ServerTreeDockable.ManagedChannelEntry managed = managedChannelEntry(serverId, channel);
    if (managed != null && !managed.detached()) {
      serverTree.selectTarget(ref);
      return;
    }

    serverTree.ensureNode(ref);
    serverTree.setChannelDisconnected(ref, true);
    serverTree.selectTarget(ref);
    serverTree.requestJoinChannel(ref);
    refreshManagedChannelsCard(serverId);
  }

  private ServerTreeDockable.ManagedChannelEntry managedChannelEntry(
      String serverId, String channel) {
    if (serverTree == null) return null;
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isBlank() || ch.isEmpty()) return null;

    List<ServerTreeDockable.ManagedChannelEntry> managed = serverTree.managedChannelsForServer(sid);
    if (managed == null || managed.isEmpty()) return null;
    for (ServerTreeDockable.ManagedChannelEntry entry : managed) {
      if (entry == null) continue;
      if (ch.equalsIgnoreCase(Objects.toString(entry.channel(), "").trim())) {
        return entry;
      }
    }
    return null;
  }
}

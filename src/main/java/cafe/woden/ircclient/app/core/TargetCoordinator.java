package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.irc.UserhostQueryService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
@ApplicationLayer
public class TargetCoordinator implements ActiveTargetPort {
  private static final Logger log = LoggerFactory.getLogger(TargetCoordinator.class);

  private final UiPort ui;
  private final UserListStore userListStore;
  private final IrcClientService irc;
  private final ServerRegistry serverRegistry;
  private final RuntimeConfigStore runtimeConfig;
  private final ConnectionCoordinator connectionCoordinator;
  private final IgnoreListService ignoreList;
  private final UserhostQueryService userhostQueryService;
  private final UserInfoEnrichmentService userInfoEnrichmentService;
  private final TargetChatHistoryPort targetChatHistoryPort;
  private final TargetLogMaintenancePort targetLogMaintenancePort;

  private final ExecutorService maintenanceExec;

  /**
   * UI refreshes for user-list metadata (away/account/hostmask/real-name) can arrive in huge bursts
   * (e.g., WHOX scans on big channels). Coalesce these to avoid rebuilding nick completions on the
   * EDT thousands of times.
   */
  private final ScheduledExecutorService usersRefreshExec;

  private final AtomicBoolean usersRefreshScheduled = new AtomicBoolean(false);

  private final CompositeDisposable disposables = new CompositeDisposable();
  private final Set<TargetRef> closedPrivateTargetsByUser = ConcurrentHashMap.newKeySet();
  private final Set<TargetRef> detachedChannelsByUserOrKick = ConcurrentHashMap.newKeySet();
  private final Set<TargetRef> channelsClosedByUser = ConcurrentHashMap.newKeySet();

  private TargetRef activeTarget;

  public TargetCoordinator(
      UiPort ui,
      UserListStore userListStore,
      IrcClientService irc,
      ServerRegistry serverRegistry,
      RuntimeConfigStore runtimeConfig,
      ConnectionCoordinator connectionCoordinator,
      IgnoreListService ignoreList,
      UserhostQueryService userhostQueryService,
      UserInfoEnrichmentService userInfoEnrichmentService,
      TargetChatHistoryPort targetChatHistoryPort,
      TargetLogMaintenancePort targetLogMaintenancePort,
      @Qualifier(ExecutorConfig.TARGET_COORDINATOR_MAINTENANCE_EXECUTOR)
          ExecutorService maintenanceExec,
      @Qualifier(ExecutorConfig.TARGET_COORDINATOR_USERS_REFRESH_SCHEDULER)
          ScheduledExecutorService usersRefreshExec) {
    this.ui = ui;
    this.userListStore = userListStore;
    this.irc = irc;
    this.serverRegistry = serverRegistry;
    this.runtimeConfig = runtimeConfig;
    this.connectionCoordinator = connectionCoordinator;
    this.ignoreList = ignoreList;
    this.userhostQueryService = userhostQueryService;
    this.userInfoEnrichmentService = userInfoEnrichmentService;
    this.targetChatHistoryPort = targetChatHistoryPort;
    this.targetLogMaintenancePort = targetLogMaintenancePort;
    this.maintenanceExec = maintenanceExec;
    this.usersRefreshExec = usersRefreshExec;
  }

  @PreDestroy
  void shutdown() {
    disposables.dispose();
  }

  public void clearLog(TargetRef target) {
    if (target == null) return;
    // Requested scope: channels + status only.
    if (!(target.isChannel() || target.isStatus())) return;

    ensureTargetExists(target);
    // Immediate UI reset on EDT (caller is already on EDT via IrcMediator subscription).
    ui.clearTranscript(target);
    ui.clearUnread(target);
    targetChatHistoryPort.reset(target);

    // DB purge off the EDT.
    maintenanceExec.submit(
        () -> {
          try {
            targetLogMaintenancePort.clearTarget(target);
          } catch (Throwable t) {
            log.warn("[ircafe] Clear log failed for {}", target, t);
          }
        });
  }

  @Override
  public TargetRef getActiveTarget() {
    return activeTarget;
  }

  public void openPrivateConversation(PrivateMessageRequest req) {
    if (req == null) return;
    String sid = Objects.toString(req.serverId(), "").trim();
    String nick = Objects.toString(req.nick(), "").trim();
    if (sid.isEmpty() || nick.isEmpty()) return;

    TargetRef pm = new TargetRef(sid, nick);
    clearClosedPrivateTargetByUser(pm);
    ensureTargetExists(pm);
    ui.selectTarget(pm);
  }

  public void onTargetActivated(TargetRef target) {
    if (target == null) return;
    if (isClosedPrivateTargetByUser(target)) return;

    ensureTargetExists(target);
    // Do NOT change the main Chat dock's displayed transcript.
    // Only update the active target for input + status/users panels.
    applyTargetContext(target);
  }

  public void onTargetSelected(TargetRef target) {
    if (target == null) return;
    if (isClosedPrivateTargetByUser(target)) return;

    ensureTargetExists(target);

    // Load history into an empty transcript before showing it (async; won't block the EDT).
    targetChatHistoryPort.onTargetSelected(target);

    // Selection in the server tree drives what the main Chat dock is displaying.
    ui.setChatActiveTarget(target);

    // And it also sets the active target used by input + status/users panels.
    applyTargetContext(target);
  }

  public void onServerDisconnected(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    userListStore.clearServer(sid);
    userhostQueryService.clearServer(sid);
    userInfoEnrichmentService.clearServer(sid);
    closedPrivateTargetsByUser.removeIf(t -> t != null && Objects.equals(t.serverId(), sid));
    if (activeTarget != null && Objects.equals(activeTarget.serverId(), sid)) {
      ui.setStatusBarCounts(0, 0);
      ui.setUsersNicks(List.of());
    }
  }

  public void onNickListUpdated(String serverId, IrcEvent.NickListUpdated ev) {
    if (ev == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    userListStore.put(sid, ev.channel(), ev.nicks());

    // User info enrichment (fallback): keep a server-wide roster snapshot and enqueue lightweight
    // USERHOST probes for nicks with missing hostmask/away info. This is a no-op unless the
    // feature is enabled in Preferences.
    updateEnrichmentFromRoster(sid, ev.channel(), ev.nicks());

    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && activeTarget.matches(ev.channel())) {
      ui.setUsersNicks(ev.nicks());
      ui.setStatusBarCounts(ev.totalUsers(), ev.operatorCount());

      // If hostmask-based ignores exist, opportunistically resolve missing hostmasks.
      maybeRequestMissingHostmasks(sid, ev.nicks());
    }
  }

  /**
   * Passive hostmask capture: when we observe a user's hostmask from a server prefix, enrich the
   * cached roster so ignore markers can reflect hostmask-based ignores even if NAMES didn't provide
   * user@host.
   */
  public void onUserHostmaskObserved(String serverId, IrcEvent.UserHostmaskObserved ev) {
    if (ev == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    java.util.Set<String> changedChannels =
        userListStore.updateHostmaskAcrossChannels(sid, ev.nick(), ev.hostmask());
    if (changedChannels.isEmpty()) return;
    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && activeTarget.isChannel()
        && changedChannels.contains(activeTarget.key())) {
      scheduleActiveUsersRefresh(sid, activeTarget.target());
    }
  }

  /**
   * Handles IRCv3 CHGHOST as an identity refresh signal for roster metadata.
   *
   * @return true if this nick is known in any roster (whether or not metadata changed)
   */
  public boolean onUserHostChanged(String serverId, IrcEvent.UserHostChanged ev) {
    if (ev == null) return false;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;

    String nick = Objects.toString(ev.nick(), "").trim();
    String user = Objects.toString(ev.user(), "").trim();
    String host = Objects.toString(ev.host(), "").trim();
    if (nick.isEmpty()) return false;

    java.util.Set<String> changedChannels = java.util.Set.of();
    if (!user.isEmpty() && !host.isEmpty()) {
      String hostmask = nick + "!" + user + "@" + host;
      changedChannels = userListStore.updateHostmaskAcrossChannels(sid, nick, hostmask);
    }

    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && activeTarget.isChannel()
        && changedChannels.contains(activeTarget.key())) {
      scheduleActiveUsersRefresh(sid, activeTarget.target());
    }

    return !changedChannels.isEmpty() || userListStore.isNickPresentOnServer(sid, nick);
  }

  public void onUserAwayStateObserved(String serverId, IrcEvent.UserAwayStateObserved ev) {
    if (ev == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    String msg = ev.awayMessage();
    java.util.Set<String> changedChannels =
        userListStore.updateAwayStateAcrossChannels(sid, ev.nick(), ev.awayState(), msg);
    if (changedChannels.isEmpty()) {
      log.debug(
          "Away state observed but no roster entries changed: serverId={} nick={} state={}",
          sid,
          ev.nick(),
          ev.awayState());
      return;
    }
    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && activeTarget.isChannel()
        && changedChannels.contains(activeTarget.key())) {
      scheduleActiveUsersRefresh(sid, activeTarget.target());
    }
  }

  public void onUserAccountStateObserved(String serverId, IrcEvent.UserAccountStateObserved ev) {
    if (ev == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    java.util.Set<String> changedChannels =
        userListStore.updateAccountAcrossChannels(
            sid, ev.nick(), ev.accountState(), ev.accountName());
    if (changedChannels.isEmpty()) {
      log.debug(
          "Account state observed but no roster entries changed: serverId={} nick={} state={} account={}",
          sid,
          ev.nick(),
          ev.accountState(),
          ev.accountName());
      return;
    }

    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && activeTarget.isChannel()
        && changedChannels.contains(activeTarget.key())) {
      scheduleActiveUsersRefresh(sid, activeTarget.target());
    }
  }

  /**
   * Handles IRCv3 SETNAME as an identity refresh signal for roster metadata.
   *
   * @return true if this nick is known in any roster (whether or not metadata changed)
   */
  public boolean onUserSetNameObserved(String serverId, IrcEvent.UserSetNameObserved ev) {
    if (ev == null) return false;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;

    String nick = Objects.toString(ev.nick(), "").trim();
    if (nick.isEmpty()) return false;

    java.util.Set<String> changedChannels =
        userListStore.updateRealNameAcrossChannels(sid, nick, ev.realName());
    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && activeTarget.isChannel()
        && changedChannels.contains(activeTarget.key())) {
      scheduleActiveUsersRefresh(sid, activeTarget.target());
    }

    return !changedChannels.isEmpty() || userListStore.isNickPresentOnServer(sid, nick);
  }

  /** Channel targets where this nick is currently present in our cached roster for the server. */
  public List<TargetRef> sharedChannelTargetsForNick(String serverId, String nick) {
    String sid = Objects.toString(serverId, "").trim();
    String n = Objects.toString(nick, "").trim();
    if (sid.isEmpty() || n.isEmpty()) return List.of();

    Set<String> channels = userListStore.channelsContainingNick(sid, n);
    if (channels.isEmpty()) return List.of();

    java.util.ArrayList<TargetRef> out = new java.util.ArrayList<>(channels.size());
    for (String ch : channels) {
      if (ch == null || ch.isBlank()) continue;
      out.add(new TargetRef(sid, ch));
    }
    if (out.isEmpty()) return List.of();
    out.sort(Comparator.comparing(TargetRef::key));
    return List.copyOf(out);
  }

  public void refreshInputEnabledForActiveTarget() {
    if (activeTarget == null) {
      ui.setInputEnabled(false);
      return;
    }
    if (activeTarget.isUiOnly()) {
      ui.setInputEnabled(false);
      return;
    }
    if (activeTarget.isChannel() && ui.isChannelDetached(activeTarget)) {
      ui.setInputEnabled(false);
      return;
    }
    ui.setInputEnabled(connectionCoordinator.isConnected(activeTarget.serverId()));
  }

  @Override
  public TargetRef safeStatusTarget() {
    if (activeTarget != null && !activeTarget.isApplicationServer()) {
      return new TargetRef(activeTarget.serverId(), "status");
    }
    String sid = serverRegistry.serverIds().stream().findFirst().orElse("default");
    return new TargetRef(sid, "status");
  }

  public void closeTarget(TargetRef target) {
    if (target == null || target.isStatus() || target.isUiOnly()) return;

    if (target.isChannel()) {
      detachChannel(target);
      return;
    }

    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) return;

    TargetRef status = new TargetRef(sid, "status");
    ensureTargetExists(status);
    if (Objects.equals(activeTarget, target)) {
      // Make close deterministic for an active PM/channel: switch coordinator + chat context
      // to status before events from focus/selection can replay the closing target.
      applyTargetContext(status);
      ui.setChatActiveTarget(status);
      ui.selectTarget(status);
    }

    markClosedPrivateTargetByUser(target);
    targetChatHistoryPort.reset(target);
    ui.appendStatus(status, "(ui)", "Closed " + target.target());
    ui.closeTarget(target);
  }

  /**
   * Fully close a channel target.
   *
   * <p>Unlike detach, this removes the channel target from the tree/transcript and removes it from
   * persisted joined-channel state.
   */
  public void closeChannel(TargetRef target) {
    if (target == null || !target.isChannel()) return;

    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) return;

    TargetRef status = new TargetRef(sid, "status");
    ensureTargetExists(status);
    ensureTargetExists(target);
    boolean detached = ui.isChannelDetached(target);

    if (Objects.equals(activeTarget, target)) {
      applyTargetContext(status);
      ui.setChatActiveTarget(status);
      ui.selectTarget(status);
    }

    detachedChannelsByUserOrKick.remove(target);
    channelsClosedByUser.remove(target);
    runtimeConfig.forgetJoinedChannel(sid, target.target());
    userListStore.clear(sid, target.target());
    targetChatHistoryPort.reset(target);
    ui.appendStatus(status, "(ui)", "Closed " + target.target());
    ui.closeTarget(target);

    boolean shouldPart = !detached && connectionCoordinator.isConnected(sid);
    if (!shouldPart) return;
    channelsClosedByUser.add(target);
    disposables.add(
        irc.partChannel(sid, target.target(), null)
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(part-error)", String.valueOf(err))));
  }

  public void detachChannel(TargetRef target) {
    detachChannel(target, null);
  }

  public void detachChannel(TargetRef target, String reason) {
    if (target == null || !target.isChannel()) return;

    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) return;

    TargetRef status = new TargetRef(sid, "status");
    ensureTargetExists(status);
    ensureTargetExists(target);

    channelsClosedByUser.remove(target);
    detachedChannelsByUserOrKick.add(target);
    ui.setChannelDetached(target, true);
    userListStore.clear(sid, target.target());

    if (Objects.equals(activeTarget, target)) {
      applyTargetContext(target);
      ui.setChatActiveTarget(target);
    }

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(status, "(detach)", "Detached from " + target.target());
      return;
    }

    String msg = Objects.toString(reason, "").trim();
    disposables.add(
        irc.partChannel(sid, target.target(), msg.isEmpty() ? null : msg)
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(part-error)", String.valueOf(err))));
  }

  public void joinChannel(TargetRef target) {
    if (target == null || !target.isChannel()) return;

    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) return;

    TargetRef status = new TargetRef(sid, "status");
    ensureTargetExists(status);
    ensureTargetExists(target);

    channelsClosedByUser.remove(target);
    runtimeConfig.rememberJoinedChannel(sid, target.target());
    detachedChannelsByUserOrKick.remove(target);
    // Keep detached until JOIN is confirmed by the server.
    ui.setChannelDetached(target, true);

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(status, "(conn)", "Not connected (join queued in config only)");
      return;
    }

    disposables.add(
        irc.joinChannel(sid, target.target())
            .subscribe(
                () -> {},
                err -> {
                  detachedChannelsByUserOrKick.add(target);
                  ui.appendError(status, "(join-error)", String.valueOf(err));
                }));
  }

  /**
   * Close a channel target locally without sending PART.
   *
   * <p>Used when the server already ended our membership (e.g. remote PART/KICK).
   */
  public void closeChannelLocally(String serverId, String channel) {
    onChannelMembershipLost(serverId, channel, true, "Channel closed.");
  }

  public void onChannelMembershipLost(String serverId, String channel, boolean suppressAutoRejoin) {
    onChannelMembershipLost(serverId, channel, suppressAutoRejoin, null);
  }

  public void onChannelMembershipLost(
      String serverId, String channel, boolean suppressAutoRejoin, String warningReason) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return;
    TargetRef target = new TargetRef(sid, ch);
    if (!target.isChannel()) return;
    boolean alreadyDetached = detachedChannelsByUserOrKick.contains(target);

    if (channelsClosedByUser.remove(target)) {
      detachedChannelsByUserOrKick.remove(target);
      userListStore.clear(sid, ch);
      return;
    }

    if (suppressAutoRejoin) detachedChannelsByUserOrKick.add(target);
    else detachedChannelsByUserOrKick.remove(target);

    ensureTargetExists(target);
    String warning = "";
    if (!alreadyDetached) {
      warning = normalizeDetachedWarning(warningReason);
      if (warning.isEmpty()) warning = "Detached by server.";
    }
    if (warning.isEmpty()) {
      ui.setChannelDetached(target, true);
    } else {
      ui.setChannelDetached(target, true, warning);
    }
    userListStore.clear(sid, ch);

    if (Objects.equals(activeTarget, target)) {
      applyTargetContext(target);
      ui.setChatActiveTarget(target);
    }
  }

  /**
   * @return true when we should treat this join as active membership; false means we intentionally
   *     remain detached and immediately part again.
   */
  public boolean onJoinedChannel(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return false;

    TargetRef target = new TargetRef(sid, ch);
    if (!target.isChannel()) return false;

    channelsClosedByUser.remove(target);
    ensureTargetExists(target);
    TargetRef status = new TargetRef(sid, "status");
    ensureTargetExists(status);

    if (detachedChannelsByUserOrKick.contains(target)) {
      ui.setChannelDetached(target, true);
      userListStore.clear(sid, ch);

      if (connectionCoordinator.isConnected(sid)) {
        disposables.add(
            irc.partChannel(sid, ch)
                .subscribe(
                    () -> {}, err -> ui.appendError(status, "(part-error)", String.valueOf(err))));
      }
      if (Objects.equals(activeTarget, target)) {
        applyTargetContext(target);
        ui.setChatActiveTarget(target);
      }
      return false;
    }

    detachedChannelsByUserOrKick.remove(target);
    ui.setChannelDetached(target, false);
    if (Objects.equals(activeTarget, target)) {
      applyTargetContext(target);
      ui.setChatActiveTarget(target);
    }
    return true;
  }

  private void applyTargetContext(TargetRef target) {
    if (target == null) return;

    this.activeTarget = target;
    String statusBarChannel = target.target();
    if (target.isNotifications()) {
      statusBarChannel = "Notifications";
    } else if (target.isChannelList()) {
      statusBarChannel = "Channel List";
    } else if (target.isWeechatFilters()) {
      statusBarChannel = "Filters";
    } else if (target.isDccTransfers()) {
      statusBarChannel = "DCC Transfers";
    } else if (target.isMonitorGroup()) {
      statusBarChannel = "Monitor";
    } else if (target.isInterceptorsGroup()) {
      statusBarChannel = "Interceptors";
    } else if (target.isApplicationUnhandledErrors()) {
      statusBarChannel = "Unhandled Errors";
    } else if (target.isApplicationAssertjSwing()) {
      statusBarChannel = "AssertJ Swing";
    } else if (target.isApplicationJhiccup()) {
      statusBarChannel = "jHiccup";
    } else if (target.isApplicationJfr()) {
      statusBarChannel = "JFR";
    } else if (target.isApplicationSpring()) {
      statusBarChannel = "Spring";
    } else if (target.isApplicationTerminal()) {
      statusBarChannel = "Terminal";
    } else if (target.isLogViewer()) {
      statusBarChannel = "Log Viewer";
    } else if (target.isInterceptor()) {
      statusBarChannel = "Interceptor";
    }
    ui.setStatusBarChannel(statusBarChannel);
    ui.setStatusBarServer(serverDisplay(target.serverId()));
    ui.setUsersChannel(target);
    if (target.isChannel()) {
      if (ui.isChannelDetached(target)) {
        ui.setStatusBarCounts(0, 0);
        ui.setUsersNicks(List.of());
      } else {
        List<IrcEvent.NickInfo> cached = userListStore.get(target.serverId(), target.target());
        ui.setUsersNicks(cached);
        ui.setStatusBarCounts(
            cached.size(), (int) cached.stream().filter(TargetCoordinator::isOperatorLike).count());
        maybeRequestMissingHostmasks(target.serverId(), cached);
        updateEnrichmentFromRoster(target.serverId(), target.target(), cached);
        if (cached.isEmpty() && connectionCoordinator.isConnected(target.serverId())) {
          disposables.add(
              irc.requestNames(target.serverId(), target.target())
                  .subscribe(
                      () -> {},
                      err ->
                          ui.appendError(
                              safeStatusTarget(), "(names-error)", String.valueOf(err))));
        }
      }
    } else {
      ui.setStatusBarCounts(0, 0);
      ui.setUsersNicks(List.of());
    }
    irc.currentNick(target.serverId())
        .ifPresent(nick -> ui.setChatCurrentNick(target.serverId(), nick));

    ui.clearUnread(target);
    refreshInputEnabledForActiveTarget();
  }

  private void updateEnrichmentFromRoster(
      String serverId, String channel, List<IrcEvent.NickInfo> nicks) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    userInfoEnrichmentService.setRosterSnapshot(sid, userListStore.getServerNicks(sid));

    if (nicks == null || nicks.isEmpty()) return;

    boolean isActiveChannel =
        activeTarget != null
            && Objects.equals(activeTarget.serverId(), sid)
            && activeTarget.isChannel()
            && activeTarget.matches(Objects.toString(channel, "").trim());

    java.util.ArrayList<String> userhostCandidates = new java.util.ArrayList<>();
    java.util.ArrayList<String> whoisUnknownAccountCandidates = new java.util.ArrayList<>();

    String self = irc.currentNick(sid).orElse("");

    for (IrcEvent.NickInfo ni : nicks) {
      if (ni == null) continue;
      String nick = Objects.toString(ni.nick(), "").trim();
      if (nick.isEmpty()) continue;
      if (!self.isBlank() && Objects.equals(self, nick)) continue;

      boolean missingHostmask = !isUsefulHostmask(Objects.toString(ni.hostmask(), "").trim());
      boolean missingAway = ni.awayState() == IrcEvent.AwayState.UNKNOWN;
      boolean missingAccount = ni.accountState() == IrcEvent.AccountState.UNKNOWN;

      if (missingHostmask || missingAway) {
        userhostCandidates.add(nick);
      }
      if (isActiveChannel && missingAccount) {
        whoisUnknownAccountCandidates.add(nick);
      }
    }

    boolean whoxForAccountEnabled =
        isActiveChannel && userInfoEnrichmentService.shouldUseWhoxForChannelScan(sid);
    boolean wantWhoxForAccount =
        whoxForAccountEnabled
            && !whoisUnknownAccountCandidates.isEmpty()
            && shouldWhoChannelScan(nicks.size(), whoisUnknownAccountCandidates.size());

    boolean wantWhoChannelForUserhost =
        isActiveChannel
            && !userhostCandidates.isEmpty()
            && shouldWhoChannelScan(nicks.size(), userhostCandidates.size());

    if (wantWhoxForAccount || wantWhoChannelForUserhost) {
      userInfoEnrichmentService.enqueueWhoChannelPrioritized(sid, channel);
    } else if (!userhostCandidates.isEmpty()) {
      userInfoEnrichmentService.enqueueUserhost(sid, userhostCandidates);
    }

    if (whoisUnknownAccountCandidates.isEmpty()) return;

    if (wantWhoxForAccount) return;
    final int MAX_WHOIS_ENQUEUE = 10;
    final int MIN_WHOIS_TRICKLE = 2;
    final java.time.Duration RECENT_WINDOW = java.time.Duration.ofMinutes(30);
    java.time.Instant now = java.time.Instant.now();

    java.util.HashMap<String, java.time.Instant> lastByNick = new java.util.HashMap<>();
    java.util.ArrayList<String> recent = new java.util.ArrayList<>();
    java.util.ArrayList<String> other = new java.util.ArrayList<>();

    for (String nick : whoisUnknownAccountCandidates) {
      java.time.Instant last = userInfoEnrichmentService.lastActiveAt(sid, nick);
      if (last != null) {
        lastByNick.put(nick, last);
        if (java.time.Duration.between(last, now).compareTo(RECENT_WINDOW) <= 0) {
          recent.add(nick);
          continue;
        }
      }
      other.add(nick);
    }

    recent.sort((a, b) -> lastByNick.get(b).compareTo(lastByNick.get(a)));

    java.util.ArrayList<String> selected = new java.util.ArrayList<>(MAX_WHOIS_ENQUEUE);
    for (String nick : recent) {
      if (selected.size() >= MAX_WHOIS_ENQUEUE) break;
      selected.add(nick);
    }
    for (String nick : other) {
      if (selected.size() >= MAX_WHOIS_ENQUEUE) break;
      if (selected.size() >= MIN_WHOIS_TRICKLE && !recent.isEmpty()) break;
      selected.add(nick);
      if (selected.size() >= MIN_WHOIS_TRICKLE && recent.isEmpty()) break;
    }

    if (!selected.isEmpty()) {
      userInfoEnrichmentService.enqueueWhoisPrioritized(sid, selected);
    }
  }

  private static boolean shouldWhoChannelScan(int rosterSize, int missingCount) {
    if (rosterSize <= 0) return false;
    if (missingCount <= 0) return false;
    if (missingCount < 20) return false;

    double ratio = (double) missingCount / (double) rosterSize;
    return ratio >= 0.35 || missingCount >= 50;
  }

  /**
   * This is deliberately conservative and relies on {@link UserhostQueryService} for anti-flood
   * behavior.
   */
  private void maybeRequestMissingHostmasks(String serverId, List<IrcEvent.NickInfo> nicks) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    if (!connectionCoordinator.isConnected(sid)) return;
    if (nicks == null || nicks.isEmpty()) return;
    if (!hasHostmaskDependentIgnores(sid)) return;

    java.util.ArrayList<String> missing = new java.util.ArrayList<>();
    for (IrcEvent.NickInfo ni : nicks) {
      if (ni == null) continue;
      String nick = Objects.toString(ni.nick(), "").trim();
      if (nick.isEmpty()) continue;

      String hm = Objects.toString(ni.hostmask(), "").trim();
      if (isUsefulHostmask(hm)) continue;

      missing.add(nick);
    }
    if (missing.isEmpty()) return;

    userhostQueryService.enqueue(sid, missing);
  }

  private boolean hasHostmaskDependentIgnores(String serverId) {
    for (String m : ignoreList.listMasks(serverId)) {
      if (maskDependsOnUserOrHost(m)) return true;
    }
    for (String m : ignoreList.listSoftMasks(serverId)) {
      if (maskDependsOnUserOrHost(m)) return true;
    }
    return false;
  }

  private static boolean maskDependsOnUserOrHost(String mask) {
    String m = Objects.toString(mask, "").trim();
    if (m.isEmpty()) return false;
    int bang = m.indexOf('!');
    int at = m.indexOf('@');
    if (bang < 0 || at < 0 || at <= bang) return false;

    String ident = m.substring(bang + 1, at).trim();
    String host = m.substring(at + 1).trim();
    boolean identUnknown = ident.isEmpty() || "*".equals(ident);
    boolean hostUnknown = host.isEmpty() || "*".equals(host);
    return !(identUnknown && hostUnknown);
  }

  private static boolean isUsefulHostmask(String hostmask) {
    if (hostmask == null) return false;
    String hm = hostmask.trim();
    if (hm.isEmpty()) return false;

    int bang = hm.indexOf('!');
    int at = hm.indexOf('@');
    if (bang <= 0 || at <= bang + 1 || at >= hm.length() - 1) return false;

    String ident = hm.substring(bang + 1, at).trim();
    String host = hm.substring(at + 1).trim();

    boolean identUnknown = ident.isEmpty() || "*".equals(ident);
    boolean hostUnknown = host.isEmpty() || "*".equals(host);
    return !(identUnknown && hostUnknown);
  }

  private void scheduleActiveUsersRefresh(String serverId, String channel) {
    TargetRef at = activeTarget;
    if (at == null || !at.isChannel()) return;
    if (!Objects.equals(at.serverId(), serverId)) return;
    if (!Objects.equals(at.target(), channel)) return;
    if (!usersRefreshScheduled.compareAndSet(false, true)) return;

    usersRefreshExec.schedule(
        () -> {
          try {
            TargetRef cur = activeTarget;
            if (cur == null || !cur.isChannel()) return;
            if (!Objects.equals(cur.serverId(), serverId)) return;
            if (!Objects.equals(cur.target(), channel)) return;

            List<IrcEvent.NickInfo> cached = userListStore.get(serverId, channel);
            ui.setUsersNicks(cached);

            int ops = 0;
            for (IrcEvent.NickInfo n : cached) {
              if (isOperatorLike(n)) ops++;
            }
            ui.setStatusBarCounts(cached.size(), ops);
          } catch (Throwable t) {
            log.debug("Failed to refresh users list (coalesced)", t);
          } finally {
            usersRefreshScheduled.set(false);
          }
        },
        200,
        TimeUnit.MILLISECONDS);
  }

  private void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  /**
   * Decide whether an inbound private event may auto-open a user-closed PM target.
   *
   * <p>If the user explicitly closed a PM:
   *
   * <ul>
   *   <li>self-authored events do <em>not</em> reopen it
   *   <li>peer-authored events do reopen it (and clear the closed flag)
   * </ul>
   */
  public boolean allowPrivateAutoOpenFromInbound(TargetRef target, boolean fromSelf) {
    if (!isPrivateTarget(target)) return true;
    if (!closedPrivateTargetsByUser.contains(target)) return true;
    if (fromSelf) return false;
    closedPrivateTargetsByUser.remove(target);
    return true;
  }

  private void markClosedPrivateTargetByUser(TargetRef target) {
    if (!isPrivateTarget(target)) return;
    closedPrivateTargetsByUser.add(target);
  }

  private void clearClosedPrivateTargetByUser(TargetRef target) {
    if (target == null) return;
    closedPrivateTargetsByUser.remove(target);
  }

  private boolean isClosedPrivateTargetByUser(TargetRef target) {
    return isPrivateTarget(target) && closedPrivateTargetsByUser.contains(target);
  }

  private static String normalizeDetachedWarning(String warningReason) {
    return Objects.toString(warningReason, "").trim();
  }

  private static boolean isPrivateTarget(TargetRef target) {
    return target != null && !target.isStatus() && !target.isUiOnly() && !target.isChannel();
  }

  private String serverDisplay(String serverId) {
    if (TargetRef.APPLICATION_SERVER_ID.equals(serverId)) {
      return "Application";
    }
    return serverRegistry
        .find(serverId)
        .map(s -> serverId + "  (" + s.host() + ":" + s.port() + ")")
        .orElse(serverId);
  }

  /**
   * pircbotx exposes channel privilege via a prefix (e.g. "@" op, "+" voice). We treat ops as @
   * (op), & (admin), ~ (owner).
   */
  private static boolean isOperatorLike(IrcEvent.NickInfo n) {
    if (n == null) return false;
    String p = n.prefix();
    if (p == null || p.isBlank()) return false;
    return p.contains("@") || p.contains("&") || p.contains("~");
  }
}

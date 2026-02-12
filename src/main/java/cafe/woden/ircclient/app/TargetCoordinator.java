package cafe.woden.ircclient.app;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.UserhostQueryService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.logging.ChatLogMaintenance;
import cafe.woden.ircclient.model.UserListStore;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class TargetCoordinator {
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
  private final ChatHistoryService chatHistoryService;
  private final ChatLogMaintenance chatLogMaintenance;

  private final ExecutorService maintenanceExec = Executors.newSingleThreadExecutor(new ThreadFactory() {
    @Override
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r, "ircafe-chatlog-maintenance");
      t.setDaemon(true);
      return t;
    }
  });

  /**
   * UI refreshes for user-list metadata (away/account/hostmask) can arrive in huge bursts
   * (e.g., WHOX scans on big channels). Coalesce these to avoid rebuilding nick completions
   * on the EDT thousands of times.
   */
  private final ScheduledExecutorService usersRefreshExec = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
    @Override
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r, "ircafe-users-refresh");
      t.setDaemon(true);
      return t;
    }
  });
  private final AtomicBoolean usersRefreshScheduled = new AtomicBoolean(false);

  private final CompositeDisposable disposables = new CompositeDisposable();

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
      ChatHistoryService chatHistoryService,
      ChatLogMaintenance chatLogMaintenance
  ) {
    this.ui = ui;
    this.userListStore = userListStore;
    this.irc = irc;
    this.serverRegistry = serverRegistry;
    this.runtimeConfig = runtimeConfig;
    this.connectionCoordinator = connectionCoordinator;
    this.ignoreList = ignoreList;
    this.userhostQueryService = userhostQueryService;
    this.userInfoEnrichmentService = userInfoEnrichmentService;
    this.chatHistoryService = chatHistoryService;
    this.chatLogMaintenance = chatLogMaintenance;
  }

  @PreDestroy
  void shutdown() {
    disposables.dispose();
    maintenanceExec.shutdown();
    usersRefreshExec.shutdown();
    try {
      maintenanceExec.awaitTermination(500, TimeUnit.MILLISECONDS);
      usersRefreshExec.awaitTermination(500, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  public void clearLog(TargetRef target) {
    if (target == null) return;
    // Requested scope: channels + status only.
    if (!(target.isChannel() || target.isStatus())) return;

    ensureTargetExists(target);
    // Immediate UI reset on EDT (caller is already on EDT via IrcMediator subscription).
    ui.clearTranscript(target);
    ui.clearUnread(target);
    chatHistoryService.reset(target);

    // DB purge off the EDT.
    maintenanceExec.submit(() -> {
      try {
        chatLogMaintenance.clearTarget(target);
      } catch (Throwable t) {
        log.warn("[ircafe] Clear log failed for {}", target, t);
      }
    });
  }

  public TargetRef getActiveTarget() {
    return activeTarget;
  }

  public void openPrivateConversation(PrivateMessageRequest req) {
    if (req == null) return;
    String sid = Objects.toString(req.serverId(), "").trim();
    String nick = Objects.toString(req.nick(), "").trim();
    if (sid.isEmpty() || nick.isEmpty()) return;

    TargetRef pm = new TargetRef(sid, nick);
    ensureTargetExists(pm);
    ui.selectTarget(pm);
  }

  public void onTargetActivated(TargetRef target) {
    if (target == null) return;

    ensureTargetExists(target);
    // Do NOT change the main Chat dock's displayed transcript.
    // Only update the active target for input + status/users panels.
    applyTargetContext(target);
  }

  public void onTargetSelected(TargetRef target) {
    if (target == null) return;

    ensureTargetExists(target);

    // Load history into an empty transcript before showing it (async; won't block the EDT).
    chatHistoryService.onTargetSelected(target);

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
      maybeRequestMissingHostmasks(sid, ev.channel(), ev.nicks());
    }
  }

  /**
   * Passive hostmask capture: when we observe a user's hostmask from a server prefix, enrich
   * the cached roster so ignore markers can reflect hostmask-based ignores even if NAMES
   * didn't provide user@host.
   */
  public void onUserHostmaskObserved(String serverId, IrcEvent.UserHostmaskObserved ev) {
    if (ev == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    java.util.Set<String> changedChannels = userListStore.updateHostmaskAcrossChannels(sid, ev.nick(), ev.hostmask());
    if (changedChannels.isEmpty()) return;
    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && activeTarget.isChannel()
        && changedChannels.contains(activeTarget.key())) {
      scheduleActiveUsersRefresh(sid, activeTarget.target());
    }
  }

  public void onUserAwayStateObserved(String serverId, IrcEvent.UserAwayStateObserved ev) {
    if (ev == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    String msg = ev.awayMessage();
    java.util.Set<String> changedChannels = userListStore.updateAwayStateAcrossChannels(sid, ev.nick(), ev.awayState(), msg);
    if (changedChannels.isEmpty()) {
      log.debug("Away state observed but no roster entries changed: serverId={} nick={} state={}", sid, ev.nick(), ev.awayState());
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

    java.util.Set<String> changedChannels = userListStore.updateAccountAcrossChannels(sid, ev.nick(), ev.accountState(), ev.accountName());
    if (changedChannels.isEmpty()) {
      log.debug("Account state observed but no roster entries changed: serverId={} nick={} state={} account={}",
          sid, ev.nick(), ev.accountState(), ev.accountName());
      return;
    }

    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && activeTarget.isChannel()
        && changedChannels.contains(activeTarget.key())) {
      scheduleActiveUsersRefresh(sid, activeTarget.target());
    }
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
    ui.setInputEnabled(connectionCoordinator.isConnected(activeTarget.serverId()));
  }

  public TargetRef safeStatusTarget() {
    if (activeTarget != null) return new TargetRef(activeTarget.serverId(), "status");
    String sid = serverRegistry.serverIds().stream().findFirst().orElse("default");
    return new TargetRef(sid, "status");
  }

  public void closeTarget(TargetRef target) {
    if (target == null || target.isStatus() || target.isUiOnly()) return;

    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) return;

    TargetRef status = new TargetRef(sid, "status");
    ensureTargetExists(status);
    if (Objects.equals(activeTarget, target)) {
      ui.selectTarget(status);
    }

    if (target.isChannel()) {
      runtimeConfig.forgetJoinedChannel(sid, target.target());
      userListStore.clear(sid, target.target());

      if (connectionCoordinator.isConnected(sid)) {
        disposables.add(
            irc.partChannel(sid, target.target()).subscribe(
                () -> ui.appendStatus(status, "(part)", "Left " + target.target()),
                err -> ui.appendError(status, "(part-error)", String.valueOf(err))
            )
        );
      } else {
        ui.appendStatus(status, "(ui)", "Closed " + target.target());
      }
    } else {
      ui.appendStatus(status, "(ui)", "Closed " + target.target());
    }

    ui.closeTarget(target);
  }

  private void applyTargetContext(TargetRef target) {
    if (target == null) return;

    this.activeTarget = target;
    ui.setStatusBarChannel(target.target());
    ui.setStatusBarServer(serverDisplay(target.serverId()));
    ui.setUsersChannel(target);
    if (target.isChannel()) {
      List<IrcEvent.NickInfo> cached = userListStore.get(target.serverId(), target.target());
      ui.setUsersNicks(cached);
      ui.setStatusBarCounts(cached.size(), (int) cached.stream().filter(TargetCoordinator::isOperatorLike).count());
      maybeRequestMissingHostmasks(target.serverId(), target.target(), cached);
      updateEnrichmentFromRoster(target.serverId(), target.target(), cached);
      if (cached.isEmpty() && connectionCoordinator.isConnected(target.serverId())) {
        disposables.add(
            irc.requestNames(target.serverId(), target.target()).subscribe(
                () -> {},
                err -> ui.appendError(safeStatusTarget(), "(names-error)", String.valueOf(err))
            )
        );
      }
    } else {
      ui.setStatusBarCounts(0, 0);
      ui.setUsersNicks(List.of());
    }
    irc.currentNick(target.serverId()).ifPresent(nick -> ui.setChatCurrentNick(target.serverId(), nick));

    ui.clearUnread(target);
    refreshInputEnabledForActiveTarget();
  }


  private void updateEnrichmentFromRoster(String serverId, String channel, List<IrcEvent.NickInfo> nicks) {
  String sid = Objects.toString(serverId, "").trim();
  if (sid.isEmpty()) return;
  userInfoEnrichmentService.setRosterSnapshot(sid, userListStore.getServerNicks(sid));

  if (nicks == null || nicks.isEmpty()) return;

  boolean isActiveChannel = activeTarget != null
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

  boolean whoxForAccountEnabled = isActiveChannel && userInfoEnrichmentService.shouldUseWhoxForChannelScan(sid);
  boolean wantWhoxForAccount = whoxForAccountEnabled
      && !whoisUnknownAccountCandidates.isEmpty()
      && shouldWhoChannelScan(nicks.size(), whoisUnknownAccountCandidates.size());

  boolean wantWhoChannelForUserhost = isActiveChannel && !userhostCandidates.isEmpty()
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
   *
   * <p>This is deliberately conservative and relies on {@link UserhostQueryService} for anti-flood behavior.
   */
  private void maybeRequestMissingHostmasks(String serverId, String channel, List<IrcEvent.NickInfo> nicks) {
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

    usersRefreshExec.schedule(() -> {
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
    }, 200, TimeUnit.MILLISECONDS);
  }

  private void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  private String serverDisplay(String serverId) {
    return serverRegistry.find(serverId)
        .map(s -> serverId + "  (" + s.host() + ":" + s.port() + ")")
        .orElse(serverId);
  }

  /**
   * pircbotx exposes channel privilege via a prefix (e.g. "@" op, "+" voice).
   * We treat ops as @ (op), & (admin), ~ (owner).
   */
  private static boolean isOperatorLike(IrcEvent.NickInfo n) {
    if (n == null) return false;
    String p = n.prefix();
    if (p == null || p.isBlank()) return false;
    return p.contains("@") || p.contains("&") || p.contains("~");
  }
}

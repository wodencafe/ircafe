package cafe.woden.ircclient.app;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.UserhostQueryService;
import cafe.woden.ircclient.logging.ChatLogMaintenance;
import cafe.woden.ircclient.model.UserListStore;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Coordinates target lifecycle (active target context, users panel, and per-target UI wiring).
 *
 */
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
    this.chatHistoryService = chatHistoryService;
    this.chatLogMaintenance = chatLogMaintenance;
  }

  @PreDestroy
  void shutdown() {
    disposables.dispose();
    maintenanceExec.shutdown();
    try {
      maintenanceExec.awaitTermination(500, TimeUnit.MILLISECONDS);
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
    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && Objects.equals(activeTarget.target(), ev.channel())) {
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

    // Step 1.5 propagation: update across all cached channels on this server.
    java.util.Set<String> changedChannels = userListStore.updateHostmaskAcrossChannels(sid, ev.nick(), ev.hostmask());
    if (changedChannels.isEmpty()) return;

    // Refresh users panel only if the active target is a channel that was modified.
    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && activeTarget.isChannel()
        && changedChannels.contains(activeTarget.target())) {
      List<IrcEvent.NickInfo> cached = userListStore.get(sid, activeTarget.target());
      ui.setUsersNicks(cached);
      ui.setStatusBarCounts(cached.size(), (int) cached.stream().filter(TargetCoordinator::isOperatorLike).count());
    }
  }

  /**
   * Passive away-state capture (currently via WHOIS): enrich cached roster so the user list can
   * eventually show away markers.
   */
  public void onUserAwayStateObserved(String serverId, IrcEvent.UserAwayStateObserved ev) {
    if (ev == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    String msg = ev.awayMessage();
    java.util.Set<String> changedChannels = userListStore.updateAwayStateAcrossChannels(sid, ev.nick(), ev.awayState(), msg);
    if (changedChannels.isEmpty()) {
      // This is typically a sign that we parsed an away update for a nick that isn't present in any
      // cached channel rosters (or that the state didn't change). If you're debugging away-notify,
      // this log helps confirm the event made it to the coordinator even if the UI doesn't change.
      log.debug("Away state observed but no roster entries changed: serverId={} nick={} state={}", sid, ev.nick(), ev.awayState());
      return;
    }

    // Refresh users panel only if the active target is a channel that was modified.
    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && activeTarget.isChannel()
        && changedChannels.contains(activeTarget.target())) {
      List<IrcEvent.NickInfo> cached = userListStore.get(sid, activeTarget.target());
      ui.setUsersNicks(cached);
      ui.setStatusBarCounts(cached.size(), (int) cached.stream().filter(TargetCoordinator::isOperatorLike).count());
    }
  }


  public void refreshInputEnabledForActiveTarget() {
    if (activeTarget == null) {
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
    if (target == null || target.isStatus()) return;

    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) return;

    TargetRef status = new TargetRef(sid, "status");
    ensureTargetExists(status);

    // If we're closing the currently active target, switch back to status first.
    if (Objects.equals(activeTarget, target)) {
      ui.selectTarget(status);
    }

    if (target.isChannel()) {
      // Remove from auto-join for next startup.
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
      // Private message buffer: local UI-only close.
      ui.appendStatus(status, "(ui)", "Closed " + target.target());
    }

    ui.closeTarget(target);
  }

  private void applyTargetContext(TargetRef target) {
    if (target == null) return;

    this.activeTarget = target;

    // Status bar
    ui.setStatusBarChannel(target.target());
    ui.setStatusBarServer(serverDisplay(target.serverId()));

    // Users panel
    ui.setUsersChannel(target);
    if (target.isChannel()) {
      List<IrcEvent.NickInfo> cached = userListStore.get(target.serverId(), target.target());
      ui.setUsersNicks(cached);
      ui.setStatusBarCounts(cached.size(), (int) cached.stream().filter(TargetCoordinator::isOperatorLike).count());

      // Opportunistically resolve missing hostmasks (low traffic).
      maybeRequestMissingHostmasks(target.serverId(), target.target(), cached);

      // Request names if cache is empty.
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

    // Mention highlighting uses server-scoped nick.
    irc.currentNick(target.serverId()).ifPresent(nick -> ui.setChatCurrentNick(target.serverId(), nick));

    ui.clearUnread(target);
    refreshInputEnabledForActiveTarget();
  }

  /**
   * Step 2 (careful): If hostmask-based ignore patterns exist, request missing hostmasks via USERHOST.
   *
   * <p>This is deliberately conservative and relies on {@link UserhostQueryService} for anti-flood behavior.
   */
  private void maybeRequestMissingHostmasks(String serverId, String channel, List<IrcEvent.NickInfo> nicks) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    if (!connectionCoordinator.isConnected(sid)) return;
    if (nicks == null || nicks.isEmpty()) return;

    // Only do this if there is at least one ignore mask that actually depends on user/host.
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

    // Pure nick-based ignore patterns look like "nick!*@*" (ident == "*" and host == "*").
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
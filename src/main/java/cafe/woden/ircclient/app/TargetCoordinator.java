package cafe.woden.ircclient.app;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.model.UserListStore;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Coordinates target lifecycle (active target context, users panel, and per-target UI wiring).
 *
 */
@Component
@Lazy
public class TargetCoordinator {
  private final UiPort ui;
  private final UserListStore userListStore;
  private final IrcClientService irc;
  private final ServerRegistry serverRegistry;
  private final RuntimeConfigStore runtimeConfig;
  private final ConnectionCoordinator connectionCoordinator;

  private final CompositeDisposable disposables = new CompositeDisposable();

  private TargetRef activeTarget;

  public TargetCoordinator(
      UiPort ui,
      UserListStore userListStore,
      IrcClientService irc,
      ServerRegistry serverRegistry,
      RuntimeConfigStore runtimeConfig,
      ConnectionCoordinator connectionCoordinator
  ) {
    this.ui = ui;
    this.userListStore = userListStore;
    this.irc = irc;
    this.serverRegistry = serverRegistry;
    this.runtimeConfig = runtimeConfig;
    this.connectionCoordinator = connectionCoordinator;
  }

  @PreDestroy
  void shutdown() {
    disposables.dispose();
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

    // Selection in the server tree drives what the main Chat dock is displaying.
    ui.setChatActiveTarget(target);

    // And it also sets the active target used by input + status/users panels.
    applyTargetContext(target);
  }

  public void onServerDisconnected(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    userListStore.clearServer(sid);
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

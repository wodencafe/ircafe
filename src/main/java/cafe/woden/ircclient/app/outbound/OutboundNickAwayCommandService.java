package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import lombok.NonNull;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles outbound /nick and /away command flow. */
@Component
@ApplicationLayer
final class OutboundNickAwayCommandService {

  @NonNull private final IrcClientService irc;
  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final ChatCommandRuntimeConfigPort runtimeConfig;
  @NonNull private final AwayCommandSupport awayCommandSupport;

  OutboundNickAwayCommandService(
      IrcClientService irc,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      ChatCommandRuntimeConfigPort runtimeConfig,
      AwayRoutingPort awayRoutingState) {
    this.irc = irc;
    this.ui = ui;
    this.connectionCoordinator = connectionCoordinator;
    this.targetCoordinator = targetCoordinator;
    this.runtimeConfig = runtimeConfig;
    this.awayCommandSupport =
        new AwayCommandSupport(irc, ui, connectionCoordinator, targetCoordinator, awayRoutingState);
  }

  void handleNick(CompositeDisposable disposables, String newNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(nick)", "Select a server first.");
      return;
    }

    String nick = newNick == null ? "" : newNick.trim();
    if (nick.isEmpty()) {
      ui.appendStatus(at, "(nick)", "Usage: /nick <newNick>");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      runtimeConfig.rememberNick(at.serverId(), nick);
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(nick)",
          "Not connected. Saved preferred nick for next connect.");
      return;
    }

    disposables.add(
        irc.changeNick(at.serverId(), nick)
            .subscribe(
                () ->
                    ui.appendStatus(
                        new TargetRef(at.serverId(), "status"),
                        "(nick)",
                        "Requested nick change to " + nick),
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(nick-error)",
                        String.valueOf(err))));
  }

  void handleAway(CompositeDisposable disposables, String message) {
    awayCommandSupport.handleAway(disposables, message);
  }
}

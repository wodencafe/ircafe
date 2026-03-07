package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Handles outbound /nick and /away command flow. */
@Component
final class OutboundNickAwayCommandService {

  private final IrcClientService irc;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final ChatCommandRuntimeConfigPort runtimeConfig;
  private final AwayRoutingPort awayRoutingState;

  OutboundNickAwayCommandService(
      IrcClientService irc,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      ChatCommandRuntimeConfigPort runtimeConfig,
      AwayRoutingPort awayRoutingState) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    this.awayRoutingState = Objects.requireNonNull(awayRoutingState, "awayRoutingState");
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
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(away)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");
    String msg = message == null ? "" : message.trim();
    boolean explicitClear =
        "-".equals(msg) || "off".equalsIgnoreCase(msg) || "clear".equalsIgnoreCase(msg);

    boolean clear;
    String toSend;

    if (msg.isEmpty()) {
      boolean currentlyAway = awayRoutingState.isAway(at.serverId());
      if (currentlyAway) {
        clear = true;
        toSend = "";
      } else {
        clear = false;
        toSend = "Gone for now.";
      }
    } else if (explicitClear) {
      clear = true;
      toSend = "";
    } else {
      clear = false;
      toSend = msg;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    awayRoutingState.rememberOrigin(at.serverId(), at);

    String prevReason = awayRoutingState.getLastReason(at.serverId());
    if (clear) awayRoutingState.setLastReason(at.serverId(), null);
    else awayRoutingState.setLastReason(at.serverId(), toSend);

    disposables.add(
        irc.setAway(at.serverId(), toSend)
            .subscribe(
                () -> {
                  awayRoutingState.setAway(at.serverId(), !clear);
                  ui.appendStatus(
                      status, "(away)", clear ? "Away cleared" : ("Away set: " + toSend));
                },
                err -> {
                  awayRoutingState.setLastReason(at.serverId(), prevReason);
                  ui.appendError(status, "(away-error)", String.valueOf(err));
                }));
  }
}

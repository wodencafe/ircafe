package cafe.woden.ircclient.app.outbound.identity;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared nick-command validation, persistence, and send support. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class NickCommandSupport {

  @NonNull private final IrcClientService irc;
  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final ChatCommandRuntimeConfigPort runtimeConfig;

  void handleNick(CompositeDisposable disposables, String newNick) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(nick)", "Select a server first.");
      return;
    }

    String nick = newNick == null ? "" : newNick.trim();
    if (nick.isEmpty()) {
      ui.appendStatus(active, "(nick)", "Usage: /nick <newNick>");
      return;
    }

    if (!connectionCoordinator.isConnected(active.serverId())) {
      runtimeConfig.rememberNick(active.serverId(), nick);
      ui.appendStatus(
          new TargetRef(active.serverId(), "status"),
          "(nick)",
          "Not connected. Saved preferred nick for next connect.");
      return;
    }

    disposables.add(
        irc.changeNick(active.serverId(), nick)
            .subscribe(
                () ->
                    ui.appendStatus(
                        new TargetRef(active.serverId(), "status"),
                        "(nick)",
                        "Requested nick change to " + nick),
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(nick-error)",
                        String.valueOf(err))));
  }
}

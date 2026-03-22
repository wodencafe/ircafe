package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import lombok.NonNull;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles outbound /nick and /away command flow. */
@Component
@ApplicationLayer
final class OutboundNickAwayCommandService {

  @NonNull private final NickCommandSupport nickCommandSupport;
  @NonNull private final AwayCommandSupport awayCommandSupport;

  OutboundNickAwayCommandService(
      IrcClientService irc,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      ChatCommandRuntimeConfigPort runtimeConfig,
      AwayRoutingPort awayRoutingState) {
    this.nickCommandSupport =
        new NickCommandSupport(irc, ui, connectionCoordinator, targetCoordinator, runtimeConfig);
    this.awayCommandSupport =
        new AwayCommandSupport(irc, ui, connectionCoordinator, targetCoordinator, awayRoutingState);
  }

  void handleNick(CompositeDisposable disposables, String newNick) {
    nickCommandSupport.handleNick(disposables, newNick);
  }

  void handleAway(CompositeDisposable disposables, String message) {
    awayCommandSupport.handleAway(disposables, message);
  }
}

package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.InboundModeEventHandler;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort;
import cafe.woden.ircclient.state.api.CtcpRoutingPort;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.state.api.PendingInvitePort;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Coordinates connectivity lifecycle side effects extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorConnectivityLifecycleOrchestrator {

  interface Callbacks {
    void failPendingEchoesForServer(String serverId, String reason);

    void clearNetsplitDebounceForServer(String serverId);
  }

  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final WhoisRoutingPort whoisRoutingState;
  private final CtcpRoutingPort ctcpRoutingState;
  private final ModeRoutingPort modeRoutingState;
  private final AwayRoutingPort awayRoutingState;
  private final ChatHistoryRequestRoutingPort chatHistoryRequestRoutingState;
  private final JoinRoutingPort joinRoutingState;
  private final LabeledResponseRoutingPort labeledResponseRoutingState;
  private final PendingInvitePort pendingInviteState;
  private final ServerIsupportStatePort serverIsupportState;
  private final InboundModeEventHandler inboundModeEventHandler;

  public boolean isConnectivityLifecycleEvent(IrcEvent event) {
    return event instanceof IrcEvent.Connected
        || event instanceof IrcEvent.Connecting
        || event instanceof IrcEvent.Reconnecting
        || event instanceof IrcEvent.Disconnected
        || event instanceof IrcEvent.ConnectionReady
        || event instanceof IrcEvent.ConnectionFeaturesUpdated;
  }

  public void handleConnectivityLifecycleEvent(Callbacks callbacks, String sid, IrcEvent event) {
    if (event instanceof IrcEvent.Connecting
        || event instanceof IrcEvent.Connected
        || event instanceof IrcEvent.Reconnecting) {
      serverIsupportState.clearServer(sid);
    }
    if (event instanceof IrcEvent.Connected ev) {
      ui.setServerConnectedIdentity(sid, ev.serverHost(), ev.serverPort(), ev.nick(), ev.at());
    }
    connectionCoordinator.handleConnectivityEvent(sid, event, targetCoordinator.getActiveTarget());
    if (event instanceof IrcEvent.Disconnected) {
      clearDisconnectedServerState(callbacks, sid);
    }
    targetCoordinator.refreshInputEnabledForActiveTarget();
  }

  private void clearDisconnectedServerState(Callbacks callbacks, String sid) {
    callbacks.failPendingEchoesForServer(sid, "disconnected before echo");
    ui.clearPrivateMessageOnlineStates(sid);
    targetCoordinator.onServerDisconnected(sid);
    whoisRoutingState.clearServer(sid);
    ctcpRoutingState.clearServer(sid);
    modeRoutingState.clearServer(sid);
    awayRoutingState.clearServer(sid);
    chatHistoryRequestRoutingState.clearServer(sid);
    joinRoutingState.clearServer(sid);
    labeledResponseRoutingState.clearServer(sid);
    pendingInviteState.clearServer(sid);
    serverIsupportState.clearServer(sid);
    inboundModeEventHandler.clearServer(sid);
    callbacks.clearNetsplitDebounceForServer(sid);
  }
}

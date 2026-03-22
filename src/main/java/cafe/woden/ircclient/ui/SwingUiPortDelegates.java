package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.UiChannelListPort;
import cafe.woden.ircclient.app.api.UiEventPort;
import cafe.woden.ircclient.app.api.UiPromptPort;
import cafe.woden.ircclient.app.api.UiTranscriptPort;
import cafe.woden.ircclient.app.api.UiViewStatePort;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.shell.StatusBar;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Internal shared Swing delegates behind the aggregate UI adapters. */
@Component
@InterfaceLayer
@Lazy
final class SwingUiPortDelegates {

  private final SwingUiInteractionPort interactionPort;
  private final UiViewStatePort viewStatePort;
  private final UiChannelListPort channelListPort;
  private final UiTranscriptPort transcriptPort;

  SwingUiPortDelegates(
      ServerTreeDockable serverTree,
      ChatDockable chat,
      ChatTranscriptStore transcripts,
      MentionPatternRegistry mentions,
      NotificationStore notificationStore,
      UserListDockable users,
      StatusBar statusBar,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus,
      ChatDockManager chatDockManager,
      ActiveInputRouter activeInputRouter,
      SwingUiBackendCommandBridge backendCommandBridge) {
    SwingEdtExecutor edt = new SwingEdtExecutor();
    this.viewStatePort =
        new SwingUiViewStatePort(
            edt,
            serverTree,
            chat,
            transcripts,
            mentions,
            notificationStore,
            users,
            statusBar,
            chatDockManager,
            activeInputRouter);
    this.channelListPort = new SwingUiChannelListPort(edt, serverTree, chat);
    this.transcriptPort =
        new SwingUiTranscriptPort(edt, serverTree, chat, transcripts, users, chatDockManager);
    this.interactionPort =
        new SwingUiInteractionPort(
            edt,
            serverTree,
            chat,
            users,
            connectBtn,
            disconnectBtn,
            activationBus,
            outboundBus,
            viewStatePort,
            backendCommandBridge);
  }

  UiEventPort eventPort() {
    return interactionPort;
  }

  UiPromptPort promptPort() {
    return interactionPort;
  }

  UiViewStatePort viewStatePort() {
    return viewStatePort;
  }

  UiChannelListPort channelListPort() {
    return channelListPort;
  }

  UiTranscriptPort transcriptPort() {
    return transcriptPort;
  }
}

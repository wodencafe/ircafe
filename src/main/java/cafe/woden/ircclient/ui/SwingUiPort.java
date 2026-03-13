package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.QuasselNetworkManagerAction;
import cafe.woden.ircclient.app.api.UiChannelListPort;
import cafe.woden.ircclient.app.api.UiInteractionPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiTranscriptPort;
import cafe.woden.ircclient.app.api.UiViewStatePort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.QuasselCoreControlPort;
import cafe.woden.ircclient.model.TargetRef;
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
import io.reactivex.rxjava3.core.Flowable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Aggregate Swing adapter that composes specialized UI port delegates. */
@Component
@InterfaceLayer
@Lazy
public class SwingUiPort implements UiPort {

  private final UiInteractionPort interactionPort;
  private final UiViewStatePort viewStatePort;
  private final UiChannelListPort channelListPort;
  private final UiTranscriptPort transcriptPort;

  public SwingUiPort(
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
      ActiveInputRouter activeInputRouter) {
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
            viewStatePort);
  }

  @Override
  public Flowable<TargetRef> targetSelections() {
    return interactionPort.targetSelections();
  }

  @Override
  public Flowable<TargetRef> targetActivations() {
    return interactionPort.targetActivations();
  }

  @Override
  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    return interactionPort.privateMessageRequests();
  }

  @Override
  public Flowable<UserActionRequest> userActionRequests() {
    return interactionPort.userActionRequests();
  }

  @Override
  public Flowable<String> outboundLines() {
    return interactionPort.outboundLines();
  }

  @Override
  public boolean confirmMultilineSplitFallback(
      TargetRef target, int lineCount, long payloadUtf8Bytes, String reason) {
    return interactionPort.confirmMultilineSplitFallback(
        target, lineCount, payloadUtf8Bytes, reason);
  }

  @Override
  public Flowable<Object> connectClicks() {
    return interactionPort.connectClicks();
  }

  @Override
  public Flowable<Object> disconnectClicks() {
    return interactionPort.disconnectClicks();
  }

  @Override
  public Flowable<String> connectServerRequests() {
    return interactionPort.connectServerRequests();
  }

  @Override
  public Flowable<String> disconnectServerRequests() {
    return interactionPort.disconnectServerRequests();
  }

  @Override
  public Flowable<ParsedInput.BackendNamed> backendNamedCommandRequests() {
    return interactionPort.backendNamedCommandRequests();
  }

  @Override
  public void openQuasselNetworkManager(String serverId) {
    interactionPort.openQuasselNetworkManager(serverId);
  }

  @Override
  public Flowable<TargetRef> closeTargetRequests() {
    return interactionPort.closeTargetRequests();
  }

  @Override
  public Flowable<TargetRef> joinChannelRequests() {
    return interactionPort.joinChannelRequests();
  }

  @Override
  public Flowable<TargetRef> disconnectChannelRequests() {
    return interactionPort.disconnectChannelRequests();
  }

  @Override
  public Flowable<TargetRef> bouncerDetachChannelRequests() {
    return interactionPort.bouncerDetachChannelRequests();
  }

  @Override
  public Flowable<TargetRef> closeChannelRequests() {
    return interactionPort.closeChannelRequests();
  }

  @Override
  public Flowable<TargetRef> clearLogRequests() {
    return interactionPort.clearLogRequests();
  }

  @Override
  public Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests() {
    return interactionPort.ircv3CapabilityToggleRequests();
  }

  @Override
  public Optional<QuasselCoreControlPort.QuasselCoreSetupRequest> promptQuasselCoreSetup(
      String serverId, QuasselCoreControlPort.QuasselCoreSetupPrompt prompt) {
    return interactionPort.promptQuasselCoreSetup(serverId, prompt);
  }

  @Override
  public Optional<QuasselNetworkManagerAction> promptQuasselNetworkManagerAction(
      String serverId, List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {
    return interactionPort.promptQuasselNetworkManagerAction(serverId, networks);
  }

  @Override
  public void ensureTargetExists(TargetRef target) {
    viewStatePort.ensureTargetExists(target);
  }

  @Override
  public void selectTarget(TargetRef target) {
    viewStatePort.selectTarget(target);
  }

  @Override
  public void closeTarget(TargetRef target) {
    viewStatePort.closeTarget(target);
  }

  @Override
  public void setChannelDisconnected(TargetRef target, boolean detached) {
    viewStatePort.setChannelDisconnected(target, detached);
  }

  @Override
  public void setChannelDisconnected(TargetRef target, boolean detached, String warningReason) {
    viewStatePort.setChannelDisconnected(target, detached, warningReason);
  }

  @Override
  public boolean isChannelDisconnected(TargetRef target) {
    return viewStatePort.isChannelDisconnected(target);
  }

  @Override
  public boolean isChannelMuted(TargetRef target) {
    return viewStatePort.isChannelMuted(target);
  }

  @Override
  public void markUnread(TargetRef target) {
    viewStatePort.markUnread(target);
  }

  @Override
  public void markHighlight(TargetRef target) {
    viewStatePort.markHighlight(target);
  }

  @Override
  public void recordHighlight(TargetRef target, String fromNick) {
    viewStatePort.recordHighlight(target, fromNick);
  }

  @Override
  public void recordHighlight(TargetRef target, String fromNick, String snippet) {
    viewStatePort.recordHighlight(target, fromNick, snippet);
  }

  @Override
  public void recordRuleMatch(TargetRef target, String fromNick, String ruleLabel, String snippet) {
    viewStatePort.recordRuleMatch(target, fromNick, ruleLabel, snippet);
  }

  @Override
  public void clearUnread(TargetRef target) {
    viewStatePort.clearUnread(target);
  }

  @Override
  public void clearTranscript(TargetRef target) {
    transcriptPort.clearTranscript(target);
  }

  @Override
  public void setChatActiveTarget(TargetRef target) {
    viewStatePort.setChatActiveTarget(target);
  }

  @Override
  public void setChatCurrentNick(String serverId, String nick) {
    viewStatePort.setChatCurrentNick(serverId, nick);
  }

  @Override
  public void setChannelTopic(TargetRef target, String topic) {
    viewStatePort.setChannelTopic(target, topic);
  }

  @Override
  public void setUsersChannel(TargetRef target) {
    viewStatePort.setUsersChannel(target);
  }

  @Override
  public void setUsersNicks(List<NickInfo> nicks) {
    viewStatePort.setUsersNicks(nicks);
  }

  @Override
  public void syncQuasselNetworks(
      String serverId, List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {
    viewStatePort.syncQuasselNetworks(serverId, networks);
  }

  @Override
  public void refreshMatrixTranscriptDisplayName(String serverId, String matrixUserId) {
    transcriptPort.refreshMatrixTranscriptDisplayName(serverId, matrixUserId);
  }

  @Override
  public void beginChannelList(String serverId, String banner) {
    channelListPort.beginChannelList(serverId, banner);
  }

  @Override
  public void appendChannelListEntry(
      String serverId, String channel, int visibleUsers, String topic) {
    channelListPort.appendChannelListEntry(serverId, channel, visibleUsers, topic);
  }

  @Override
  public void endChannelList(String serverId, String summary) {
    channelListPort.endChannelList(serverId, summary);
  }

  @Override
  public void beginChannelBanList(String serverId, String channel) {
    channelListPort.beginChannelBanList(serverId, channel);
  }

  @Override
  public void appendChannelBanListEntry(
      String serverId, String channel, String mask, String setBy, Long setAtEpochSeconds) {
    channelListPort.appendChannelBanListEntry(serverId, channel, mask, setBy, setAtEpochSeconds);
  }

  @Override
  public void endChannelBanList(String serverId, String channel, String summary) {
    channelListPort.endChannelBanList(serverId, channel, summary);
  }

  @Override
  public void setChannelModeSnapshot(
      String serverId, String channel, String rawModes, String friendlySummary) {
    channelListPort.setChannelModeSnapshot(serverId, channel, rawModes, friendlySummary);
  }

  @Override
  public void setStatusBarChannel(String channel) {
    viewStatePort.setStatusBarChannel(channel);
  }

  @Override
  public void setStatusBarCounts(int users, int ops) {
    viewStatePort.setStatusBarCounts(users, ops);
  }

  @Override
  public void setStatusBarServer(String serverText) {
    viewStatePort.setStatusBarServer(serverText);
  }

  @Override
  public void enqueueStatusNotice(String text, TargetRef clickTarget) {
    viewStatePort.enqueueStatusNotice(text, clickTarget);
  }

  @Override
  public void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled) {
    viewStatePort.setConnectionControlsEnabled(connectEnabled, disconnectEnabled);
  }

  @Override
  public void setConnectionStatusText(String text) {
    viewStatePort.setConnectionStatusText(text);
  }

  @Override
  public void setServerConnectionState(String serverId, ConnectionState state) {
    viewStatePort.setServerConnectionState(serverId, state);
  }

  @Override
  public void setServerDesiredOnline(String serverId, boolean desiredOnline) {
    viewStatePort.setServerDesiredOnline(serverId, desiredOnline);
  }

  @Override
  public void setServerConnectionDiagnostics(
      String serverId, String lastError, Long nextRetryEpochMs) {
    viewStatePort.setServerConnectionDiagnostics(serverId, lastError, nextRetryEpochMs);
  }

  @Override
  public void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {
    viewStatePort.setPrivateMessageOnlineState(serverId, nick, online);
  }

  @Override
  public void clearPrivateMessageOnlineStates(String serverId) {
    viewStatePort.clearPrivateMessageOnlineStates(serverId);
  }

  @Override
  public void setServerConnectedIdentity(
      String serverId, String connectedHost, int connectedPort, String nick, Instant at) {
    viewStatePort.setServerConnectedIdentity(serverId, connectedHost, connectedPort, nick, at);
  }

  @Override
  public void setServerIrcv3Capability(
      String serverId, String capability, String subcommand, boolean enabled) {
    viewStatePort.setServerIrcv3Capability(serverId, capability, subcommand, enabled);
  }

  @Override
  public void setServerIsupportToken(String serverId, String tokenName, String tokenValue) {
    viewStatePort.setServerIsupportToken(serverId, tokenName, tokenValue);
  }

  @Override
  public void setServerVersionDetails(
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {
    viewStatePort.setServerVersionDetails(
        serverId, serverName, serverVersion, userModes, channelModes);
  }

  @Override
  public void setInputEnabled(boolean enabled) {
    viewStatePort.setInputEnabled(enabled);
  }

  @Override
  public void appendChat(TargetRef target, String from, String text) {
    transcriptPort.appendChat(target, from, text);
  }

  @Override
  public void appendChat(TargetRef target, String from, String text, boolean outgoingLocalEcho) {
    transcriptPort.appendChat(target, from, text, outgoingLocalEcho);
  }

  @Override
  public void appendChatAt(
      TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
    transcriptPort.appendChatAt(target, at, from, text, outgoingLocalEcho);
  }

  @Override
  public void appendChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags) {
    transcriptPort.appendChatAt(target, at, from, text, outgoingLocalEcho, messageId, ircv3Tags);
  }

  @Override
  public void appendChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    transcriptPort.appendChatAt(
        target,
        at,
        from,
        text,
        outgoingLocalEcho,
        messageId,
        ircv3Tags,
        notificationRuleHighlightColor);
  }

  @Override
  public void appendPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text) {
    transcriptPort.appendPendingOutgoingChat(target, pendingId, at, from, text);
  }

  @Override
  public boolean resolvePendingOutgoingChat(
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    return transcriptPort.resolvePendingOutgoingChat(
        target, pendingId, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void failPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text, String reason) {
    transcriptPort.failPendingOutgoingChat(target, pendingId, at, from, text, reason);
  }

  @Override
  public void appendSpoilerChat(TargetRef target, String from, String text) {
    transcriptPort.appendSpoilerChat(target, from, text);
  }

  @Override
  public void appendSpoilerChatAt(TargetRef target, Instant at, String from, String text) {
    transcriptPort.appendSpoilerChatAt(target, at, from, text);
  }

  @Override
  public void appendAction(TargetRef target, String from, String action) {
    transcriptPort.appendAction(target, from, action);
  }

  @Override
  public void appendAction(
      TargetRef target, String from, String action, boolean outgoingLocalEcho) {
    transcriptPort.appendAction(target, from, action, outgoingLocalEcho);
  }

  @Override
  public void appendActionAt(
      TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
    transcriptPort.appendActionAt(target, at, from, action, outgoingLocalEcho);
  }

  @Override
  public void appendActionAt(
      TargetRef target,
      Instant at,
      String from,
      String action,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags) {
    transcriptPort.appendActionAt(
        target, at, from, action, outgoingLocalEcho, messageId, ircv3Tags);
  }

  @Override
  public void appendActionAt(
      TargetRef target,
      Instant at,
      String from,
      String action,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    transcriptPort.appendActionAt(
        target,
        at,
        from,
        action,
        outgoingLocalEcho,
        messageId,
        ircv3Tags,
        notificationRuleHighlightColor);
  }

  @Override
  public void appendPresence(TargetRef target, cafe.woden.ircclient.app.api.PresenceEvent event) {
    transcriptPort.appendPresence(target, event);
  }

  @Override
  public void appendNotice(TargetRef target, String from, String text) {
    transcriptPort.appendNotice(target, from, text);
  }

  @Override
  public void appendNoticeAt(TargetRef target, Instant at, String from, String text) {
    transcriptPort.appendNoticeAt(target, at, from, text);
  }

  @Override
  public void appendNoticeAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    transcriptPort.appendNoticeAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendStatus(TargetRef target, String from, String text) {
    transcriptPort.appendStatus(target, from, text);
  }

  @Override
  public void appendStatusAt(TargetRef target, Instant at, String from, String text) {
    transcriptPort.appendStatusAt(target, at, from, text);
  }

  @Override
  public void appendStatusAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    transcriptPort.appendStatusAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendError(TargetRef target, String from, String text) {
    transcriptPort.appendError(target, from, text);
  }

  @Override
  public void appendErrorAt(TargetRef target, Instant at, String from, String text) {
    transcriptPort.appendErrorAt(target, at, from, text);
  }

  @Override
  public void showTypingIndicator(TargetRef target, String nick, String state) {
    transcriptPort.showTypingIndicator(target, nick, state);
  }

  @Override
  public void showTypingActivity(TargetRef target, String state) {
    transcriptPort.showTypingActivity(target, state);
  }

  @Override
  public void showUsersTypingIndicator(TargetRef target, String nick, String state) {
    transcriptPort.showUsersTypingIndicator(target, nick, state);
  }

  @Override
  public void setReadMarker(TargetRef target, long markerEpochMs) {
    transcriptPort.setReadMarker(target, markerEpochMs);
  }

  @Override
  public void applyMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {
    transcriptPort.applyMessageReaction(target, at, fromNick, targetMessageId, reaction);
  }

  @Override
  public void removeMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {
    transcriptPort.removeMessageReaction(target, at, fromNick, targetMessageId, reaction);
  }

  @Override
  public boolean isOwnMessage(TargetRef target, String targetMessageId) {
    return transcriptPort.isOwnMessage(target, targetMessageId);
  }

  @Override
  public boolean applyMessageEdit(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String editedText,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    return transcriptPort.applyMessageEdit(
        target,
        at,
        fromNick,
        targetMessageId,
        editedText,
        replacementMessageId,
        replacementIrcv3Tags);
  }

  @Override
  public boolean applyMessageRedaction(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    return transcriptPort.applyMessageRedaction(
        target, at, fromNick, targetMessageId, replacementMessageId, replacementIrcv3Tags);
  }

  @Override
  public void normalizeIrcv3CapabilityUiState(String serverId, String capability) {
    transcriptPort.normalizeIrcv3CapabilityUiState(serverId, capability);
  }
}

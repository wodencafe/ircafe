package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.model.TargetRef;
import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Non-transcript UI state, navigation, and server metadata updates. */
@ApplicationLayer
public interface UiViewStatePort {

  void ensureTargetExists(TargetRef target);

  default boolean hasTarget(TargetRef target) {
    return false;
  }

  void selectTarget(TargetRef target);

  void closeTarget(TargetRef target);

  default void setChannelDisconnected(TargetRef target, boolean detached) {}

  default void setChannelDisconnected(TargetRef target, boolean detached, String warningReason) {
    setChannelDisconnected(target, detached);
  }

  default boolean isChannelDisconnected(TargetRef target) {
    return false;
  }

  default boolean isChannelMuted(TargetRef target) {
    return false;
  }

  void markUnread(TargetRef target);

  void markHighlight(TargetRef target);

  void recordHighlight(TargetRef target, String fromNick);

  default void recordHighlight(TargetRef target, String fromNick, String snippet) {
    recordHighlight(target, fromNick);
  }

  void recordRuleMatch(TargetRef target, String fromNick, String ruleLabel, String snippet);

  void clearUnread(TargetRef target);

  void setChatActiveTarget(TargetRef target);

  void setChatCurrentNick(String serverId, String nick);

  void setChannelTopic(TargetRef target, String topic);

  void setUsersChannel(TargetRef target);

  void setUsersNicks(List<NickInfo> nicks);

  default void syncQuasselNetworks(
      String serverId, List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {}

  void setStatusBarChannel(String channel);

  void setStatusBarCounts(int users, int ops);

  void setStatusBarServer(String serverText);

  default void enqueueStatusNotice(String text, TargetRef clickTarget) {}

  void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled);

  void setConnectionStatusText(String text);

  void setServerConnectionState(String serverId, ConnectionState state);

  default void setServerDesiredOnline(String serverId, boolean desiredOnline) {}

  default void setServerConnectionDiagnostics(
      String serverId, String lastError, Long nextRetryEpochMs) {}

  default void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {}

  default void clearPrivateMessageOnlineStates(String serverId) {}

  default void setServerConnectedIdentity(
      String serverId, String connectedHost, int connectedPort, String nick, Instant at) {}

  default void setServerIrcv3Capability(
      String serverId, String capability, String subcommand, boolean enabled) {}

  default void setServerIsupportToken(String serverId, String tokenName, String tokenValue) {}

  default void setServerVersionDetails(
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {}

  void setInputEnabled(boolean enabled);
}

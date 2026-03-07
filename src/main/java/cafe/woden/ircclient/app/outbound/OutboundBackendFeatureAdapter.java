package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.IrcClientService;

/** Backend-specific outbound command feature adapter. */
interface OutboundBackendFeatureAdapter {

  IrcProperties.Server.Backend backend();

  default boolean supportsSemanticUpload() {
    return false;
  }

  default boolean supportsQuasselCoreCommands() {
    return false;
  }

  default boolean supportsReadMarker(IrcClientService irc, String serverId) {
    return irc != null && irc.isReadMarkerAvailable(serverId);
  }

  default boolean supportsMonitor(IrcClientService irc, String serverId) {
    return irc != null && irc.isMonitorAvailable(serverId);
  }

  default boolean supportsLabeledResponse(IrcClientService irc, String serverId) {
    return irc != null && irc.isLabeledResponseAvailable(serverId);
  }

  default boolean supportsMultiline(IrcClientService irc, String serverId) {
    return irc != null && irc.isMultilineAvailable(serverId);
  }

  default boolean supportsDraftReply(IrcClientService irc, String serverId) {
    return irc != null && irc.isDraftReplyAvailable(serverId);
  }

  default boolean supportsDraftReact(IrcClientService irc, String serverId) {
    return irc != null && irc.isDraftReactAvailable(serverId);
  }

  default boolean supportsDraftUnreact(IrcClientService irc, String serverId) {
    return irc != null && irc.isDraftUnreactAvailable(serverId);
  }

  default boolean supportsMessageEdit(IrcClientService irc, String serverId) {
    return irc != null && irc.isMessageEditAvailable(serverId);
  }

  default boolean supportsMessageRedaction(IrcClientService irc, String serverId) {
    return irc != null && irc.isMessageRedactionAvailable(serverId);
  }
}

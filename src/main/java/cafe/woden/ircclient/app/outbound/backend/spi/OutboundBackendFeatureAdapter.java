package cafe.woden.ircclient.app.outbound.backend.spi;

import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Backend-specific outbound command feature adapter. */
@SecondaryPort
@ApplicationLayer
public interface OutboundBackendFeatureAdapter {

  IrcProperties.Server.Backend backend();

  default String backendId() {
    IrcProperties.Server.Backend backend = backend();
    return backend == null ? "" : BackendDescriptorCatalog.builtIns().idFor(backend);
  }

  default boolean supportsSemanticUpload() {
    return false;
  }

  default boolean supportsQuasselCoreCommands() {
    return false;
  }

  default boolean persistsJoinedChannelsLocally() {
    return true;
  }

  default boolean supportsReadMarker(IrcNegotiatedFeaturePort irc, String serverId) {
    return irc != null && irc.isReadMarkerAvailable(serverId);
  }

  default boolean supportsMonitor(IrcNegotiatedFeaturePort irc, String serverId) {
    return irc != null && irc.isMonitorAvailable(serverId);
  }

  default boolean supportsLabeledResponse(IrcNegotiatedFeaturePort irc, String serverId) {
    return irc != null && irc.isLabeledResponseAvailable(serverId);
  }

  default boolean supportsMultiline(IrcNegotiatedFeaturePort irc, String serverId) {
    return irc != null && irc.isMultilineAvailable(serverId);
  }

  default boolean supportsDraftReply(IrcNegotiatedFeaturePort irc, String serverId) {
    return irc != null && irc.isDraftReplyAvailable(serverId);
  }

  default boolean supportsDraftReact(IrcNegotiatedFeaturePort irc, String serverId) {
    return irc != null && irc.isDraftReactAvailable(serverId);
  }

  default boolean supportsDraftUnreact(IrcNegotiatedFeaturePort irc, String serverId) {
    return irc != null && irc.isDraftUnreactAvailable(serverId);
  }

  default boolean supportsMessageEdit(IrcNegotiatedFeaturePort irc, String serverId) {
    return irc != null && irc.isMessageEditAvailable(serverId);
  }

  default boolean supportsMessageRedaction(IrcNegotiatedFeaturePort irc, String serverId) {
    return irc != null && irc.isMessageRedactionAvailable(serverId);
  }
}

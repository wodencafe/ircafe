package cafe.woden.ircclient.irc.port;

import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Read-only negotiated feature/capability view for a server connection. */
@SecondaryPort
@ApplicationLayer
public interface IrcNegotiatedFeaturePort {

  default boolean isChatHistoryAvailable(String serverId) {
    return false;
  }

  default boolean isMessageTagsAvailable(String serverId) {
    return false;
  }

  default boolean isDraftReplyAvailable(String serverId) {
    return false;
  }

  default boolean isDraftReactAvailable(String serverId) {
    return false;
  }

  default boolean isDraftUnreactAvailable(String serverId) {
    return false;
  }

  default boolean isMultilineAvailable(String serverId) {
    return false;
  }

  default long negotiatedMultilineMaxBytes(String serverId) {
    return 0L;
  }

  default int negotiatedMultilineMaxLines(String serverId) {
    return 0;
  }

  default boolean isExperimentalMessageEditAvailable(String serverId) {
    return false;
  }

  default boolean isMessageRedactionAvailable(String serverId) {
    return false;
  }

  default boolean isReadMarkerAvailable(String serverId) {
    return false;
  }

  default boolean isLabeledResponseAvailable(String serverId) {
    return false;
  }

  default boolean isMonitorAvailable(String serverId) {
    return false;
  }
}

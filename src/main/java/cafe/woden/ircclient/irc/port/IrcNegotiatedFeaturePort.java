package cafe.woden.ircclient.irc.port;

import cafe.woden.ircclient.irc.IrcClientService;
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

  static IrcNegotiatedFeaturePort from(IrcClientService irc) {
    if (irc instanceof IrcNegotiatedFeaturePort port) {
      return port;
    }
    if (irc == null) {
      return new IrcNegotiatedFeaturePort() {};
    }
    return new IrcNegotiatedFeaturePort() {
      @Override
      public boolean isChatHistoryAvailable(String serverId) {
        return irc.isChatHistoryAvailable(serverId);
      }

      @Override
      public boolean isMessageTagsAvailable(String serverId) {
        return irc.isMessageTagsAvailable(serverId);
      }

      @Override
      public boolean isDraftReplyAvailable(String serverId) {
        return irc.isDraftReplyAvailable(serverId);
      }

      @Override
      public boolean isDraftReactAvailable(String serverId) {
        return irc.isDraftReactAvailable(serverId);
      }

      @Override
      public boolean isDraftUnreactAvailable(String serverId) {
        return irc.isDraftUnreactAvailable(serverId);
      }

      @Override
      public boolean isMultilineAvailable(String serverId) {
        return irc.isMultilineAvailable(serverId);
      }

      @Override
      public long negotiatedMultilineMaxBytes(String serverId) {
        return irc.negotiatedMultilineMaxBytes(serverId);
      }

      @Override
      public int negotiatedMultilineMaxLines(String serverId) {
        return irc.negotiatedMultilineMaxLines(serverId);
      }

      @Override
      public boolean isExperimentalMessageEditAvailable(String serverId) {
        return irc.isExperimentalMessageEditAvailable(serverId);
      }

      @Override
      public boolean isMessageRedactionAvailable(String serverId) {
        return irc.isMessageRedactionAvailable(serverId);
      }

      @Override
      public boolean isReadMarkerAvailable(String serverId) {
        return irc.isReadMarkerAvailable(serverId);
      }

      @Override
      public boolean isLabeledResponseAvailable(String serverId) {
        return irc.isLabeledResponseAvailable(serverId);
      }

      @Override
      public boolean isMonitorAvailable(String serverId) {
        return irc.isMonitorAvailable(serverId);
      }
    };
  }
}

package cafe.woden.ircclient.irc.port;

import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.core.Completable;

/** Typing capability/readiness + send API used by UI coordinators. */
public interface IrcTypingPort {

  default boolean isTypingAvailable(String serverId) {
    return false;
  }

  default String typingAvailabilityReason(String serverId) {
    return "";
  }

  default Completable sendTyping(String serverId, String target, String state) {
    return Completable.error(new UnsupportedOperationException("typing capability not supported"));
  }

  static IrcTypingPort from(IrcClientService irc) {
    if (irc instanceof IrcTypingPort port) {
      return port;
    }
    if (irc == null) {
      return new IrcTypingPort() {};
    }
    return new IrcTypingPort() {
      @Override
      public boolean isTypingAvailable(String serverId) {
        return irc.isTypingAvailable(serverId);
      }

      @Override
      public String typingAvailabilityReason(String serverId) {
        return irc.typingAvailabilityReason(serverId);
      }

      @Override
      public Completable sendTyping(String serverId, String target, String state) {
        return irc.sendTyping(serverId, target, state);
      }
    };
  }
}

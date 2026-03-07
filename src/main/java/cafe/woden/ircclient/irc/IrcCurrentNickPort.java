package cafe.woden.ircclient.irc;

import java.util.Objects;
import java.util.Optional;

/** Current nickname lookup used by app/domain services. */
public interface IrcCurrentNickPort {

  default Optional<String> currentNick(String serverId) {
    return Optional.empty();
  }

  static IrcCurrentNickPort from(IrcClientService irc) {
    if (irc instanceof IrcCurrentNickPort port) {
      return port;
    }
    if (irc == null) {
      return new IrcCurrentNickPort() {};
    }
    return new IrcCurrentNickPort() {
      @Override
      public Optional<String> currentNick(String serverId) {
        String sid = Objects.toString(serverId, "").trim();
        if (sid.isEmpty()) return Optional.empty();
        return irc.currentNick(sid);
      }
    };
  }
}

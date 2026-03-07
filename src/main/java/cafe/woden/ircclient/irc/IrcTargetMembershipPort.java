package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import java.util.Optional;

/** Channel/query target membership operations used by target coordination. */
public interface IrcTargetMembershipPort {

  default Completable joinChannel(String serverId, String channel) {
    return Completable.complete();
  }

  default Completable partChannel(String serverId, String channel) {
    return partChannel(serverId, channel, null);
  }

  default Completable partChannel(String serverId, String channel, String reason) {
    return Completable.complete();
  }

  default Completable requestNames(String serverId, String channel) {
    return Completable.complete();
  }

  default Completable sendRaw(String serverId, String line) {
    return Completable.complete();
  }

  default Optional<String> currentNick(String serverId) {
    return Optional.empty();
  }

  static IrcTargetMembershipPort from(IrcClientService irc) {
    if (irc instanceof IrcTargetMembershipPort port) {
      return port;
    }
    if (irc == null) {
      return new IrcTargetMembershipPort() {};
    }
    return new IrcTargetMembershipPort() {
      @Override
      public Completable joinChannel(String serverId, String channel) {
        return irc.joinChannel(serverId, channel);
      }

      @Override
      public Completable partChannel(String serverId, String channel) {
        return irc.partChannel(serverId, channel);
      }

      @Override
      public Completable partChannel(String serverId, String channel, String reason) {
        return irc.partChannel(serverId, channel, reason);
      }

      @Override
      public Completable requestNames(String serverId, String channel) {
        return irc.requestNames(serverId, channel);
      }

      @Override
      public Completable sendRaw(String serverId, String line) {
        return irc.sendRaw(serverId, line);
      }

      @Override
      public Optional<String> currentNick(String serverId) {
        String sid = Objects.toString(serverId, "").trim();
        if (sid.isEmpty()) return Optional.empty();
        return irc.currentNick(sid);
      }
    };
  }
}

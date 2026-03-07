package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import java.util.Optional;

/** Connection lifecycle operations used by connection orchestration. */
public interface IrcConnectionLifecyclePort {

  default Completable connect(String serverId) {
    return Completable.complete();
  }

  default Completable disconnect(String serverId) {
    return Completable.complete();
  }

  default Completable disconnect(String serverId, String reason) {
    return disconnect(serverId);
  }

  default Optional<String> currentNick(String serverId) {
    return Optional.empty();
  }

  static IrcConnectionLifecyclePort from(IrcClientService irc) {
    if (irc instanceof IrcConnectionLifecyclePort port) {
      return port;
    }
    if (irc == null) {
      return new IrcConnectionLifecyclePort() {};
    }
    return new IrcConnectionLifecyclePort() {
      @Override
      public Completable connect(String serverId) {
        return irc.connect(serverId);
      }

      @Override
      public Completable disconnect(String serverId) {
        return irc.disconnect(serverId);
      }

      @Override
      public Completable disconnect(String serverId, String reason) {
        return irc.disconnect(serverId, reason);
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

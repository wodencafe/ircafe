package cafe.woden.ircclient.irc.port;

import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Lag probe operations used by UI lag indicator surfaces. */
public interface IrcLagProbePort {

  default Optional<String> currentNick(String serverId) {
    return Optional.empty();
  }

  default Completable requestLagProbe(String serverId) {
    return Completable.complete();
  }

  default OptionalLong lastMeasuredLagMs(String serverId) {
    return OptionalLong.empty();
  }

  static IrcLagProbePort from(IrcClientService irc) {
    if (irc instanceof IrcLagProbePort port) {
      return port;
    }
    if (irc == null) {
      return new IrcLagProbePort() {};
    }
    return new IrcLagProbePort() {
      @Override
      public Optional<String> currentNick(String serverId) {
        String sid = Objects.toString(serverId, "").trim();
        if (sid.isEmpty()) return Optional.empty();
        return irc.currentNick(sid);
      }

      @Override
      public Completable requestLagProbe(String serverId) {
        return irc.requestLagProbe(serverId);
      }

      @Override
      public OptionalLong lastMeasuredLagMs(String serverId) {
        return irc.lastMeasuredLagMs(serverId);
      }
    };
  }
}

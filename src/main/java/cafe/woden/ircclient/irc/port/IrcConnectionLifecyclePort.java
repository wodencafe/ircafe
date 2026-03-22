package cafe.woden.ircclient.irc.port;

import cafe.woden.ircclient.irc.DisconnectRequestSource;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcDisconnectWithSourcePort;
import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Connection lifecycle operations used by connection orchestration. */
@SecondaryPort
@ApplicationLayer
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

  default Completable disconnect(String serverId, String reason, DisconnectRequestSource source) {
    return disconnect(serverId, reason);
  }

  default Optional<String> currentNick(String serverId) {
    return Optional.empty();
  }

  static IrcConnectionLifecyclePort from(IrcClientService irc) {
    if (irc == null) {
      return new IrcConnectionLifecyclePort() {};
    }
    IrcConnectionLifecyclePort lifecyclePort =
        (irc instanceof IrcConnectionLifecyclePort port) ? port : null;
    IrcDisconnectWithSourcePort sourceAware =
        (irc instanceof IrcDisconnectWithSourcePort port) ? port : null;
    return new IrcConnectionLifecyclePort() {
      @Override
      public Completable connect(String serverId) {
        if (lifecyclePort != null) return lifecyclePort.connect(serverId);
        return irc.connect(serverId);
      }

      @Override
      public Completable disconnect(String serverId) {
        if (lifecyclePort != null) return lifecyclePort.disconnect(serverId);
        return irc.disconnect(serverId);
      }

      @Override
      public Completable disconnect(String serverId, String reason) {
        if (lifecyclePort != null) return lifecyclePort.disconnect(serverId, reason);
        return irc.disconnect(serverId, reason);
      }

      @Override
      public Completable disconnect(
          String serverId, String reason, DisconnectRequestSource source) {
        if (lifecyclePort != null) {
          return lifecyclePort.disconnect(serverId, reason, source);
        }
        if (sourceAware != null) {
          return sourceAware.disconnect(
              serverId, reason, source == null ? DisconnectRequestSource.UNKNOWN : source);
        }
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

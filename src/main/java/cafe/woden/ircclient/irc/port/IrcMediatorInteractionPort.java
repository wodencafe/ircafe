package cafe.woden.ircclient.irc.port;

import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Mediator-facing IRC stream and command operations. */
@SecondaryPort
@ApplicationLayer
public interface IrcMediatorInteractionPort {

  default Flowable<ServerIrcEvent> events() {
    return Flowable.empty();
  }

  default Completable whois(String serverId, String nick) {
    return Completable.complete();
  }

  default Completable whowas(String serverId, String nick, int count) {
    return Completable.complete();
  }

  default Completable sendPrivateMessage(String serverId, String target, String message) {
    return Completable.complete();
  }

  default Completable sendRaw(String serverId, String line) {
    return Completable.complete();
  }

  default Completable setIrcv3CapabilityEnabled(String serverId, String capability, boolean value) {
    return Completable.complete();
  }

  default Completable joinChannel(String serverId, String channel) {
    return Completable.complete();
  }

  default Optional<String> currentNick(String serverId) {
    return Optional.empty();
  }
}

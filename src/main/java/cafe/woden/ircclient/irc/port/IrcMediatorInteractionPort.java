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

  Flowable<ServerIrcEvent> events();

  Completable whois(String serverId, String nick);

  Completable whowas(String serverId, String nick, int count);

  Completable sendPrivateMessage(String serverId, String target, String message);

  Completable sendRaw(String serverId, String line);

  Completable setIrcv3CapabilityEnabled(String serverId, String capability, boolean value);

  Completable joinChannel(String serverId, String channel);

  Optional<String> currentNick(String serverId);
}

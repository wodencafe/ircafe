package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.port.*;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Optional;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing mediator interaction operations via a narrow port. */
@Component("ircMediatorInteractionPort")
@InfrastructureLayer
public class IrcMediatorInteractionPortAdapter implements IrcMediatorInteractionPort {

  private final IrcMediatorInteractionPort delegate;

  public IrcMediatorInteractionPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.delegate = IrcMediatorInteractionPort.from(irc);
  }

  @Override
  public Flowable<ServerIrcEvent> events() {
    return delegate.events();
  }

  @Override
  public Completable whois(String serverId, String nick) {
    return delegate.whois(serverId, nick);
  }

  @Override
  public Completable whowas(String serverId, String nick, int count) {
    return delegate.whowas(serverId, nick, count);
  }

  @Override
  public Completable sendPrivateMessage(String serverId, String target, String message) {
    return delegate.sendPrivateMessage(serverId, target, message);
  }

  @Override
  public Completable sendRaw(String serverId, String line) {
    return delegate.sendRaw(serverId, line);
  }

  @Override
  public Completable setIrcv3CapabilityEnabled(String serverId, String capability, boolean value) {
    return delegate.setIrcv3CapabilityEnabled(serverId, capability, value);
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return delegate.joinChannel(serverId, channel);
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    return delegate.currentNick(serverId);
  }
}

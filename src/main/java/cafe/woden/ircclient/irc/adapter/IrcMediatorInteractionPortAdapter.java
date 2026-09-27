package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing mediator interaction operations via a narrow port. */
@Component("ircMediatorInteractionPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcMediatorInteractionPortAdapter implements IrcMediatorInteractionPort {

  private final IrcClientService irc;

  public IrcMediatorInteractionPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = irc;
  }

  @Override
  public Flowable<ServerIrcEvent> events() {
    return irc == null ? IrcMediatorInteractionPort.super.events() : irc.events();
  }

  @Override
  public Completable whois(String serverId, String nick) {
    return irc == null
        ? IrcMediatorInteractionPort.super.whois(serverId, nick)
        : irc.whois(serverId, nick);
  }

  @Override
  public Completable whowas(String serverId, String nick, int count) {
    return irc == null
        ? IrcMediatorInteractionPort.super.whowas(serverId, nick, count)
        : irc.whowas(serverId, nick, count);
  }

  @Override
  public Completable sendPrivateMessage(String serverId, String target, String message) {
    return irc == null
        ? IrcMediatorInteractionPort.super.sendPrivateMessage(serverId, target, message)
        : irc.sendPrivateMessage(serverId, target, message);
  }

  @Override
  public Completable sendRaw(String serverId, String line) {
    return irc == null
        ? IrcMediatorInteractionPort.super.sendRaw(serverId, line)
        : irc.sendRaw(serverId, line);
  }

  @Override
  public Completable setIrcv3CapabilityEnabled(String serverId, String capability, boolean value) {
    return irc == null
        ? IrcMediatorInteractionPort.super.setIrcv3CapabilityEnabled(serverId, capability, value)
        : irc.setIrcv3CapabilityEnabled(serverId, capability, value);
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return irc == null
        ? IrcMediatorInteractionPort.super.joinChannel(serverId, channel)
        : irc.joinChannel(serverId, channel);
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    if (irc instanceof IrcMediatorInteractionPort port) {
      return port.currentNick(serverId);
    }
    String sid = Objects.toString(serverId, "").trim();
    if (irc == null || sid.isEmpty()) return Optional.empty();
    return irc.currentNick(sid);
  }
}

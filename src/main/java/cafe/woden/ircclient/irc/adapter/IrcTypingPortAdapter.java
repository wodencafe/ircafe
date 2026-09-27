package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcTypingPort;
import io.reactivex.rxjava3.core.Completable;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing typing readiness/send behavior via a narrow port. */
@Component("ircTypingPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcTypingPortAdapter implements IrcTypingPort {

  private final IrcClientService irc;

  public IrcTypingPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = irc;
  }

  @Override
  public boolean isTypingAvailable(String serverId) {
    return irc == null
        ? IrcTypingPort.super.isTypingAvailable(serverId)
        : irc.isTypingAvailable(serverId);
  }

  @Override
  public String typingAvailabilityReason(String serverId) {
    return irc == null
        ? IrcTypingPort.super.typingAvailabilityReason(serverId)
        : irc.typingAvailabilityReason(serverId);
  }

  @Override
  public Completable sendTyping(String serverId, String target, String state) {
    return irc == null
        ? IrcTypingPort.super.sendTyping(serverId, target, state)
        : irc.sendTyping(serverId, target, state);
  }
}

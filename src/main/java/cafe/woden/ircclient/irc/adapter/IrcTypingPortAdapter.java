package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.*;
import io.reactivex.rxjava3.core.Completable;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing typing capability/send behavior via a narrow port. */
@Component("ircTypingPort")
@InfrastructureLayer
public class IrcTypingPortAdapter implements IrcTypingPort {

  private final IrcTypingPort delegate;

  public IrcTypingPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.delegate = IrcTypingPort.from(irc);
  }

  @Override
  public boolean isTypingAvailable(String serverId) {
    return delegate.isTypingAvailable(serverId);
  }

  @Override
  public String typingAvailabilityReason(String serverId) {
    return delegate.typingAvailabilityReason(serverId);
  }

  @Override
  public Completable sendTyping(String serverId, String target, String state) {
    return delegate.sendTyping(serverId, target, state);
  }
}

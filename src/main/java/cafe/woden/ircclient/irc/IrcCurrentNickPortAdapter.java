package cafe.woden.ircclient.irc;

import java.util.Optional;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing current-nick lookup via a narrow port. */
@Component("ircCurrentNickPort")
@InfrastructureLayer
public class IrcCurrentNickPortAdapter implements IrcCurrentNickPort {

  private final IrcCurrentNickPort delegate;

  public IrcCurrentNickPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.delegate = IrcCurrentNickPort.from(irc);
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    return delegate.currentNick(serverId);
  }
}

package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.port.*;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing echo-message capability checks via a narrow port. */
@Component("ircEchoCapabilityPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcEchoCapabilityPortAdapter implements IrcEchoCapabilityPort {

  private final IrcEchoCapabilityPort delegate;

  public IrcEchoCapabilityPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.delegate = IrcEchoCapabilityPort.from(irc);
  }

  @Override
  public boolean isEchoMessageAvailable(String serverId) {
    return delegate.isEchoMessageAvailable(serverId);
  }
}

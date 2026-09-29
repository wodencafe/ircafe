package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcEchoCapabilityPort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing echo-message capability checks via a narrow port. */
@Component("ircEchoCapabilityPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcEchoCapabilityPortAdapter implements IrcEchoCapabilityPort {

  private final IrcClientService irc;

  public IrcEchoCapabilityPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = irc;
  }

  @Override
  public boolean isEchoMessageAvailable(String serverId) {
    return irc == null
        ? IrcEchoCapabilityPort.super.isEchoMessageAvailable(serverId)
        : irc.isEchoMessageAvailable(serverId);
  }
}

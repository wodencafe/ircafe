package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcShutdownPort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing shutdown operations via a narrow port. */
@Component("ircShutdownPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcShutdownPortAdapter implements IrcShutdownPort {

  private final IrcClientService irc;

  public IrcShutdownPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = irc;
  }

  @Override
  public void shutdownNow() {
    if (irc != null) irc.shutdownNow();
  }
}

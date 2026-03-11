package cafe.woden.ircclient.irc;

import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing shutdown operations via a narrow port. */
@Component("ircShutdownPort")
@InfrastructureLayer
public class IrcShutdownPortAdapter implements IrcShutdownPort {

  private final IrcShutdownPort delegate;

  public IrcShutdownPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.delegate = IrcShutdownPort.from(irc);
  }

  @Override
  public void shutdownNow() {
    delegate.shutdownNow();
  }
}

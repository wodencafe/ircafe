package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.port.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing shutdown operations via a narrow port. */
@Component("ircShutdownPort")
@InfrastructureLayer
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class IrcShutdownPortAdapter implements IrcShutdownPort {

  private final IrcShutdownPort delegate;

  public IrcShutdownPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this(IrcShutdownPort.from(irc));
  }

  @Override
  public void shutdownNow() {
    delegate.shutdownNow();
  }
}

package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcIdentityPort;
import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Adapts the routed IRC client to the identity command port. */
@Component("ircIdentityPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcIdentityPortAdapter implements IrcIdentityPort {
  private final IrcClientService irc;

  public IrcIdentityPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = Objects.requireNonNull(irc, "irc");
  }

  @Override
  public Completable changeNick(String serverId, String newNick) {
    return irc.changeNick(serverId, newNick);
  }

  @Override
  public Completable setAway(String serverId, String awayMessage) {
    return irc.setAway(serverId, awayMessage);
  }
}

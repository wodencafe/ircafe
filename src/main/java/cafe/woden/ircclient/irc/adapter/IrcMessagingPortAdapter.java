package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcMessagingPort;
import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Adapts the routed IRC client to the messaging command port. */
@Component("ircMessagingPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcMessagingPortAdapter implements IrcMessagingPort {
  private final IrcClientService irc;

  public IrcMessagingPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = Objects.requireNonNull(irc, "irc");
  }

  @Override
  public Completable sendMessage(String serverId, String target, String message) {
    return irc.sendMessage(serverId, target, message);
  }

  @Override
  public Completable sendNotice(String serverId, String target, String message) {
    return irc.sendNotice(serverId, target, message);
  }

  @Override
  public Completable sendAction(String serverId, String target, String action) {
    return irc.sendAction(serverId, target, action);
  }
}

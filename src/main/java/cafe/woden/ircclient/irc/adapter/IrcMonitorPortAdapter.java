package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcMonitorPort;
import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Adapts the routed IRC client to the monitor command port. */
@Component("ircMonitorPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcMonitorPortAdapter implements IrcMonitorPort {
  private final IrcClientService irc;

  public IrcMonitorPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = Objects.requireNonNull(irc, "irc");
  }

  @Override
  public int negotiatedMonitorLimit(String serverId) {
    return irc.negotiatedMonitorLimit(serverId);
  }

  @Override
  public Completable sendRaw(String serverId, String rawLine) {
    return irc.sendRaw(serverId, rawLine);
  }
}

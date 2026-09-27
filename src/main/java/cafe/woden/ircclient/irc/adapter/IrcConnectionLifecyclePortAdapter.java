package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.DisconnectRequestSource;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcDisconnectWithSourcePort;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Adapts IRC clients to lifecycle operations, including optional disconnect-source support. */
@Component("ircConnectionLifecyclePort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcConnectionLifecyclePortAdapter implements IrcConnectionLifecyclePort {

  private final IrcClientService irc;

  public IrcConnectionLifecyclePortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = irc;
  }

  @Override
  public Completable connect(String serverId) {
    return irc == null ? IrcConnectionLifecyclePort.super.connect(serverId) : irc.connect(serverId);
  }

  @Override
  public Completable disconnect(String serverId) {
    return irc == null
        ? IrcConnectionLifecyclePort.super.disconnect(serverId)
        : irc.disconnect(serverId);
  }

  @Override
  public Completable disconnect(String serverId, String reason) {
    return irc == null
        ? IrcConnectionLifecyclePort.super.disconnect(serverId, reason)
        : irc.disconnect(serverId, reason);
  }

  @Override
  public Completable disconnect(String serverId, String reason, DisconnectRequestSource source) {
    // Native lifecycle clients own their source policy, including the meaning of null.
    if (irc instanceof IrcConnectionLifecyclePort port) {
      return port.disconnect(serverId, reason, source);
    }
    if (irc instanceof IrcDisconnectWithSourcePort sourceAware) {
      return sourceAware.disconnect(
          serverId, reason, source == null ? DisconnectRequestSource.UNKNOWN : source);
    }
    return disconnect(serverId, reason);
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (irc == null || sid.isEmpty()) return Optional.empty();
    return irc.currentNick(sid);
  }
}

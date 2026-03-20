package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.DisconnectRequestSource;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.port.*;
import io.reactivex.rxjava3.core.Completable;
import java.util.Optional;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing connect/disconnect/currentNick via a narrow lifecycle port. */
@Component("ircConnectionLifecyclePort")
@InfrastructureLayer
public class IrcConnectionLifecyclePortAdapter implements IrcConnectionLifecyclePort {

  private final IrcConnectionLifecyclePort delegate;

  public IrcConnectionLifecyclePortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.delegate = IrcConnectionLifecyclePort.from(irc);
  }

  @Override
  public Completable connect(String serverId) {
    return delegate.connect(serverId);
  }

  @Override
  public Completable disconnect(String serverId) {
    return delegate.disconnect(serverId);
  }

  @Override
  public Completable disconnect(String serverId, String reason) {
    return delegate.disconnect(serverId, reason);
  }

  @Override
  public Completable disconnect(String serverId, String reason, DisconnectRequestSource source) {
    return delegate.disconnect(serverId, reason, source);
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    return delegate.currentNick(serverId);
  }
}

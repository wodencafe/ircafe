package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.port.*;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing read-marker send capability via a narrow port. */
@Component("ircReadMarkerPort")
@InfrastructureLayer
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class IrcReadMarkerPortAdapter implements IrcReadMarkerPort {

  private final IrcReadMarkerPort delegate;

  public IrcReadMarkerPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this(IrcReadMarkerPort.from(irc));
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    return delegate.isReadMarkerAvailable(serverId);
  }

  @Override
  public Completable sendReadMarker(String serverId, String target, Instant markerAt) {
    return delegate.sendReadMarker(serverId, target, markerAt);
  }
}

package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing read-marker send capability via a narrow port. */
@Component("ircReadMarkerPort")
public class IrcReadMarkerPortAdapter implements IrcReadMarkerPort {

  private final IrcReadMarkerPort delegate;

  public IrcReadMarkerPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.delegate = IrcReadMarkerPort.from(irc);
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

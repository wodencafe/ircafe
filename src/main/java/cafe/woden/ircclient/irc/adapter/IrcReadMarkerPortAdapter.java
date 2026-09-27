package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing read-marker send capability via a narrow port. */
@Component("ircReadMarkerPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcReadMarkerPortAdapter implements IrcReadMarkerPort {

  private final IrcClientService irc;

  public IrcReadMarkerPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = irc;
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    return irc == null
        ? IrcReadMarkerPort.super.isReadMarkerAvailable(serverId)
        : irc.isReadMarkerAvailable(serverId);
  }

  @Override
  public Completable sendReadMarker(String serverId, String target, Instant markerAt) {
    return irc == null
        ? IrcReadMarkerPort.super.sendReadMarker(serverId, target, markerAt)
        : irc.sendReadMarker(serverId, target, markerAt);
  }
}

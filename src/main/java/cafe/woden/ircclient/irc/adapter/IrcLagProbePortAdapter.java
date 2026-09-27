package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcLagProbePort;
import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing lag probe operations via a narrow port. */
@Component("ircLagProbePort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcLagProbePortAdapter implements IrcLagProbePort {

  private final IrcClientService irc;

  public IrcLagProbePortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = irc;
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    // A client implementing the narrow port owns its identifier normalization.
    if (irc instanceof IrcLagProbePort port) return port.currentNick(serverId);
    String sid = Objects.toString(serverId, "").trim();
    if (irc == null || sid.isEmpty()) return Optional.empty();
    return irc.currentNick(sid);
  }

  @Override
  public Completable requestLagProbe(String serverId) {
    return irc == null
        ? IrcLagProbePort.super.requestLagProbe(serverId)
        : irc.requestLagProbe(serverId);
  }

  @Override
  public boolean shouldRequestLagProbe(String serverId) {
    return irc == null
        ? IrcLagProbePort.super.shouldRequestLagProbe(serverId)
        : irc.shouldRequestLagProbe(serverId);
  }

  @Override
  public boolean isLagProbeReady(String serverId) {
    return irc == null
        ? IrcLagProbePort.super.isLagProbeReady(serverId)
        : irc.isLagProbeReady(serverId);
  }

  @Override
  public OptionalLong lastMeasuredLagMs(String serverId) {
    return irc == null
        ? IrcLagProbePort.super.lastMeasuredLagMs(serverId)
        : irc.lastMeasuredLagMs(serverId);
  }
}

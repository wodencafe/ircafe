package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.bouncer.BouncerConnectionPort;
import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** IRC-backed adapter for bouncer-triggered auto-connect requests. */
@Component
@InfrastructureLayer
public class BouncerIrcConnectionPortAdapter implements BouncerConnectionPort {

  private final IrcClientService irc;

  public BouncerIrcConnectionPortAdapter(@Lazy IrcClientService irc) {
    this.irc = Objects.requireNonNull(irc, "irc");
  }

  @Override
  public Completable connect(String serverId) {
    return irc.connect(serverId);
  }
}

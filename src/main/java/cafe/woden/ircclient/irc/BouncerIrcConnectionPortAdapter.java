package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.bouncer.BouncerConnectionPort;
import io.reactivex.rxjava3.core.Completable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** IRC-backed adapter for bouncer-triggered auto-connect requests. */
@Component
@InfrastructureLayer
@RequiredArgsConstructor
public class BouncerIrcConnectionPortAdapter implements BouncerConnectionPort {

  @NonNull @Lazy private final IrcClientService irc;

  @Override
  public Completable connect(String serverId) {
    return irc.connect(serverId);
  }
}

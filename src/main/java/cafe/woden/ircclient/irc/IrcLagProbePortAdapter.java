package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing lag probe operations via a narrow port. */
@Component("ircLagProbePort")
public class IrcLagProbePortAdapter implements IrcLagProbePort {

  private final IrcLagProbePort delegate;

  public IrcLagProbePortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.delegate = IrcLagProbePort.from(irc);
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    return delegate.currentNick(serverId);
  }

  @Override
  public Completable requestLagProbe(String serverId) {
    return delegate.requestLagProbe(serverId);
  }

  @Override
  public OptionalLong lastMeasuredLagMs(String serverId) {
    return delegate.lastMeasuredLagMs(serverId);
  }
}

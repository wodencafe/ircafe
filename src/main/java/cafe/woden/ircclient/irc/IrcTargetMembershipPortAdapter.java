package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing target-membership operations via a narrow port. */
@Component("ircTargetMembershipPort")
public class IrcTargetMembershipPortAdapter implements IrcTargetMembershipPort {

  private final IrcTargetMembershipPort delegate;

  public IrcTargetMembershipPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.delegate = IrcTargetMembershipPort.from(irc);
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return delegate.joinChannel(serverId, channel);
  }

  @Override
  public Completable partChannel(String serverId, String channel) {
    return delegate.partChannel(serverId, channel);
  }

  @Override
  public Completable partChannel(String serverId, String channel, String reason) {
    return delegate.partChannel(serverId, channel, reason);
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    return delegate.requestNames(serverId, channel);
  }

  @Override
  public Completable sendRaw(String serverId, String line) {
    return delegate.sendRaw(serverId, line);
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    return delegate.currentNick(serverId);
  }
}

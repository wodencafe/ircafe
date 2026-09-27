package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing target-membership operations via a narrow port. */
@Component("ircTargetMembershipPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcTargetMembershipPortAdapter implements IrcTargetMembershipPort {

  private final IrcClientService irc;

  public IrcTargetMembershipPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = irc;
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return irc == null
        ? IrcTargetMembershipPort.super.joinChannel(serverId, channel)
        : irc.joinChannel(serverId, channel);
  }

  @Override
  public Completable partChannel(String serverId, String channel) {
    return irc == null
        ? IrcTargetMembershipPort.super.partChannel(serverId, channel)
        : irc.partChannel(serverId, channel);
  }

  @Override
  public Completable partChannel(String serverId, String channel, String reason) {
    return irc == null
        ? IrcTargetMembershipPort.super.partChannel(serverId, channel, reason)
        : irc.partChannel(serverId, channel, reason);
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    return irc == null
        ? IrcTargetMembershipPort.super.requestNames(serverId, channel)
        : irc.requestNames(serverId, channel);
  }

  @Override
  public Completable sendRaw(String serverId, String line) {
    return irc == null
        ? IrcTargetMembershipPort.super.sendRaw(serverId, line)
        : irc.sendRaw(serverId, line);
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    if (irc instanceof IrcTargetMembershipPort port) {
      return port.currentNick(serverId);
    }
    String sid = Objects.toString(serverId, "").trim();
    if (irc == null || sid.isEmpty()) return Optional.empty();
    return irc.currentNick(sid);
  }
}

package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcCurrentNickPort;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring adapter exposing current-nick lookup via a narrow port. */
@Component("ircCurrentNickPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcCurrentNickPortAdapter implements IrcCurrentNickPort {

  private final IrcClientService irc;

  public IrcCurrentNickPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = irc;
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    // A client implementing the narrow port owns its identifier normalization.
    if (irc instanceof IrcCurrentNickPort port) return port.currentNick(serverId);
    String sid = Objects.toString(serverId, "").trim();
    if (irc == null || sid.isEmpty()) return Optional.empty();
    return irc.currentNick(sid);
  }
}

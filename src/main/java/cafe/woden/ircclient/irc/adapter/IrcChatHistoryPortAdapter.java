package cafe.woden.ircclient.irc.adapter;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcChatHistoryPort;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Adapts the routed IRC client to the chat history port. */
@Component("ircChatHistoryPort")
@SecondaryAdapter
@InfrastructureLayer
public class IrcChatHistoryPortAdapter implements IrcChatHistoryPort {
  private final IrcClientService irc;

  public IrcChatHistoryPortAdapter(@Qualifier("ircClientService") IrcClientService irc) {
    this.irc = Objects.requireNonNull(irc, "irc");
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, Instant beforeExclusive, int limit) {
    return irc.requestChatHistoryBefore(serverId, target, beforeExclusive, limit);
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, long beforeExclusiveEpochMs, int limit) {
    return irc.requestChatHistoryBefore(serverId, target, beforeExclusiveEpochMs, limit);
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, String selector, int limit) {
    return irc.requestChatHistoryBefore(serverId, target, selector, limit);
  }

  @Override
  public Completable requestChatHistoryLatest(
      String serverId, String target, String selector, int limit) {
    return irc.requestChatHistoryLatest(serverId, target, selector, limit);
  }

  @Override
  public Completable requestChatHistoryAround(
      String serverId, String target, String selector, int limit) {
    return irc.requestChatHistoryAround(serverId, target, selector, limit);
  }

  @Override
  public Completable requestChatHistoryBetween(
      String serverId, String target, String startSelector, String endSelector, int limit) {
    return irc.requestChatHistoryBetween(serverId, target, startSelector, endSelector, limit);
  }
}

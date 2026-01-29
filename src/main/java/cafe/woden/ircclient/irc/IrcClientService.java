package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Optional;

/**
 * Multi-server IRC client API.
 *
 * <p>All operations are explicitly scoped to a server id.
 */
public interface IrcClientService {
  Flowable<ServerIrcEvent> events();

  Optional<String> currentNick(String serverId);

  Completable connect(String serverId);
  Completable disconnect(String serverId);

  Completable changeNick(String serverId, String newNick);

  Completable requestNames(String serverId, String channel);
  Completable joinChannel(String serverId, String channel);

  /** Request WHOIS info for a nick (results will be emitted on {@link #events()}). */
  Completable whois(String serverId, String nick);


  /** Leave a channel (PART). */
  default Completable partChannel(String serverId, String channel) {
    return partChannel(serverId, channel, null);
  }

  /** Leave a channel (PART) with an optional reason. */
  Completable partChannel(String serverId, String channel, String reason);
  Completable sendToChannel(String serverId, String channel, String message);

  Completable sendPrivateMessage(String serverId, String nick, String message);

  /**
   * Convenience method used by the app layer.
   *
   * <p>If {@code target} looks like a channel (# or &), we send to the channel.
   * Otherwise we treat it as a nick and send a private message.
   */
  default Completable sendMessage(String serverId, String target, String message) {
    String t = target == null ? "" : target.trim();
    if (t.startsWith("#") || t.startsWith("&")) {
      return sendToChannel(serverId, t, message);
    }
    return sendPrivateMessage(serverId, t, message);
  }
}

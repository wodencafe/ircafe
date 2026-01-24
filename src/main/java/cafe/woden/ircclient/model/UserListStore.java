package cafe.woden.ircclient.model;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory cache of channel userlists, keyed by server + channel. */
@Component
public class UserListStore {

  private final Map<String, Map<String, List<NickInfo>>> usersByServerAndChannel = new ConcurrentHashMap<>();

  public List<NickInfo> get(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return Collections.emptyList();

    Map<String, List<NickInfo>> byChannel = usersByServerAndChannel.get(sid);
    if (byChannel == null) return Collections.emptyList();
    return byChannel.getOrDefault(ch, Collections.emptyList());
  }

  public void put(String serverId, String channel, List<NickInfo> nicks) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return;

    usersByServerAndChannel
        .computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
        .put(ch, nicks == null ? List.of() : List.copyOf(nicks));
  }

  public void clear(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return;

    Map<String, List<NickInfo>> byChannel = usersByServerAndChannel.get(sid);
    if (byChannel != null) byChannel.remove(ch);
  }

  public void clearServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    usersByServerAndChannel.remove(sid);
  }
}

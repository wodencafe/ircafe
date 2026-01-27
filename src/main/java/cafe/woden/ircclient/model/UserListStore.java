package cafe.woden.ircclient.model;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory cache of channel userlists, keyed by server + channel. */
@Component
public class UserListStore {

  private final Map<String, Map<String, List<NickInfo>>> usersByServerAndChannel = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Set<String>>> lowerNickSetByServerAndChannel = new ConcurrentHashMap<>();

  public List<NickInfo> get(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return Collections.emptyList();

    Map<String, List<NickInfo>> byChannel = usersByServerAndChannel.get(sid);
    if (byChannel == null) return Collections.emptyList();
    return byChannel.getOrDefault(ch, Collections.emptyList());
  }

  /**
   * Case-insensitive (lowercased) nick set for mention matching.
   *
   * <p>This is kept in sync with {@link #put(String, String, List)}.
   */
  public Set<String> getLowerNickSet(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return Collections.emptySet();

    Map<String, Set<String>> byChannel = lowerNickSetByServerAndChannel.get(sid);
    if (byChannel == null) return Collections.emptySet();
    return byChannel.getOrDefault(ch, Collections.emptySet());
  }

  public void put(String serverId, String channel, List<NickInfo> nicks) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return;

    List<NickInfo> safe = nicks == null ? List.of() : List.copyOf(nicks);

    usersByServerAndChannel
        .computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
        .put(ch, safe);

    // Precompute lowercased nick set for fast mention checking.
    Set<String> lower = safe.stream()
        .map(NickInfo::nick)
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> s.toLowerCase(Locale.ROOT))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());

    lowerNickSetByServerAndChannel
        .computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
        .put(ch, lower);
  }

  public void clear(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return;

    Map<String, List<NickInfo>> byChannel = usersByServerAndChannel.get(sid);
    if (byChannel != null) byChannel.remove(ch);

    Map<String, Set<String>> byChannelSet = lowerNickSetByServerAndChannel.get(sid);
    if (byChannelSet != null) byChannelSet.remove(ch);
  }

  public void clearServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    usersByServerAndChannel.remove(sid);
    lowerNickSetByServerAndChannel.remove(sid);
  }
}

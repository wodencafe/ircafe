package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Lazy
public class UserListStore {

  public record Entry(List<NickInfo> nicks, Instant updatedAt) {}

  private final Map<String, Entry> byChannel = new ConcurrentHashMap<>();

  public void put(String channel, List<NickInfo> nicks) {
    byChannel.put(channel, new Entry(List.copyOf(nicks), Instant.now()));
  }

  public List<NickInfo> get(String channel) {
    Entry e = byChannel.get(channel);
    return e == null ? List.of() : e.nicks();
  }

  public int userCount(String channel) {
    return get(channel).size();
  }

  public int opCount(String channel) {
    return (int) get(channel).stream()
        .filter(n -> isOpLike(n.prefix()))
        .count();
  }

  public boolean has(String channel) {
    return byChannel.containsKey(channel);
  }

  private static boolean isOpLike(String prefix) {
    if (prefix == null || prefix.isBlank()) return false;
    return prefix.indexOf('@') >= 0
        || prefix.indexOf('&') >= 0
        || prefix.indexOf('~') >= 0
        || prefix.indexOf('%') >= 0;
  }
}

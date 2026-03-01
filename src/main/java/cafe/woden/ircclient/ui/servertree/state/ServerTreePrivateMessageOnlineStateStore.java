package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Tracks online/offline state for private-message targets. */
public final class ServerTreePrivateMessageOnlineStateStore {

  private final Map<TargetRef, Boolean> onlineByTarget = new HashMap<>();

  public void put(TargetRef ref, boolean online) {
    if (ref == null) return;
    onlineByTarget.put(ref, online);
  }

  public void putIfAbsent(TargetRef ref, boolean online) {
    if (ref == null) return;
    onlineByTarget.putIfAbsent(ref, online);
  }

  public void remove(TargetRef ref) {
    if (ref == null) return;
    onlineByTarget.remove(ref);
  }

  public List<TargetRef> clearServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return List.of();

    ArrayList<TargetRef> changed = new ArrayList<>();
    java.util.Iterator<Map.Entry<TargetRef, Boolean>> it = onlineByTarget.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<TargetRef, Boolean> entry = it.next();
      TargetRef ref = entry.getKey();
      if (ref != null && Objects.equals(ref.serverId(), sid)) {
        changed.add(ref);
        it.remove();
      }
    }
    return List.copyOf(changed);
  }

  public boolean isOnline(TargetRef ref) {
    return Boolean.TRUE.equals(onlineByTarget.get(ref));
  }
}

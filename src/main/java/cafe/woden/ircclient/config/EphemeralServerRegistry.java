package cafe.woden.ircclient.config;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class EphemeralServerRegistry {

  private final LinkedHashMap<String, IrcProperties.Server> byId = new LinkedHashMap<>();
  private final Map<String, String> originById = new HashMap<>();
  private final BehaviorProcessor<List<ServerEntry>> updates = BehaviorProcessor.create();

  public EphemeralServerRegistry() {
    updates.onNext(snapshotEntries());
  }

  public Flowable<List<ServerEntry>> updates() {
    return updates.onBackpressureLatest();
  }

  public synchronized List<ServerEntry> entries() {
    return snapshotEntries();
  }

  public synchronized List<IrcProperties.Server> servers() {
    return List.copyOf(byId.values());
  }

  public synchronized Set<String> serverIds() {
    return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(byId.keySet()));
  }

  public synchronized Optional<IrcProperties.Server> find(String serverId) {
    String id = norm(serverId);
    if (id == null) return Optional.empty();
    return Optional.ofNullable(byId.get(id));
  }

  public synchronized IrcProperties.Server require(String serverId) {
    return find(serverId).orElseThrow(() -> new IllegalArgumentException("Unknown server id: " + serverId));
  }

  public synchronized boolean containsId(String serverId) {
    String id = norm(serverId);
    return id != null && byId.containsKey(id);
  }

  public synchronized Optional<String> originOf(String serverId) {
    String id = norm(serverId);
    if (id == null) return Optional.empty();
    return Optional.ofNullable(originById.get(id));
  }

  public synchronized void upsert(IrcProperties.Server server, String originId) {
    if (server == null) return;
    String id = norm(server.id());
    if (id == null) throw new IllegalArgumentException("server.id is required");
    byId.put(id, server);
    String origin = norm(originId);
    if (origin == null) originById.remove(id);
    else originById.put(id, origin);
    emit();
  }

  public synchronized void remove(String serverId) {
    String id = norm(serverId);
    if (id == null) return;
    boolean changed = byId.remove(id) != null;
    changed |= originById.remove(id) != null;
    if (changed) emit();
  }

  public synchronized void removeByOrigin(String originId) {
    String origin = norm(originId);
    if (origin == null) return;

    Set<String> toRemove = new HashSet<>();
    for (var e : originById.entrySet()) {
      if (Objects.equals(origin, e.getValue())) {
        toRemove.add(e.getKey());
      }
    }

    if (toRemove.isEmpty()) return;

    for (String id : toRemove) {
      byId.remove(id);
      originById.remove(id);
    }

    emit();
  }

  public synchronized void clear() {
    if (byId.isEmpty() && originById.isEmpty()) return;
    byId.clear();
    originById.clear();
    emit();
  }

  private void emit() {
    updates.onNext(snapshotEntries());
  }

  private List<ServerEntry> snapshotEntries() {
    List<ServerEntry> out = new ArrayList<>(byId.size());
    for (var e : byId.entrySet()) {
      out.add(ServerEntry.ephemeral(e.getValue(), originById.get(e.getKey())));
    }
    return List.copyOf(out);
  }

  private static String norm(String s) {
    String v = Objects.toString(s, "").trim();
    return v.isEmpty() ? null : v;
  }
}

package cafe.woden.ircclient.config;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/**
 * Live, mutable server registry.
 *
 * <p>This is intended to be the source-of-truth at runtime for the currently configured {@code
 * irc.servers} list. UI can add/edit/remove servers and the rest of the application (server tree,
 * mediator, IRC service) can react.
 */
@Component
@ApplicationLayer
public class ServerRegistry {

  private final RuntimeConfigStore runtimeConfig;
  private final LinkedHashMap<String, IrcProperties.Server> byId = new LinkedHashMap<>();
  private final BehaviorProcessor<List<IrcProperties.Server>> updates = BehaviorProcessor.create();

  public ServerRegistry(IrcProperties props, RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
    Map<String, List<String>> runtimeAutoJoinByServer =
        (runtimeConfig == null)
            ? Map.of()
            : Objects.requireNonNullElse(runtimeConfig.readExplicitServerAutoJoinById(), Map.of());

    if (props != null && props.servers() != null) {
      for (IrcProperties.Server s : props.servers()) {
        if (s == null) continue;
        String id = Objects.toString(s.id(), "").trim();
        if (id.isEmpty()) continue;
        byId.put(id, withRuntimeAutoJoinOverride(s, runtimeAutoJoinByServer));
      }
    }
    updates.onNext(snapshot());
  }

  public synchronized Set<String> serverIds() {
    return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(byId.keySet()));
  }

  public synchronized Map<String, IrcProperties.Server> byId() {
    return Map.copyOf(byId);
  }

  public synchronized List<IrcProperties.Server> servers() {
    return snapshot();
  }

  public synchronized Optional<IrcProperties.Server> find(String serverId) {
    String id = Objects.toString(serverId, "").trim();
    if (id.isEmpty()) return Optional.empty();
    return Optional.ofNullable(byId.get(id));
  }

  public synchronized IrcProperties.Server require(String serverId) {
    return find(serverId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown server id: " + serverId));
  }

  public synchronized boolean containsId(String serverId) {
    String id = Objects.toString(serverId, "").trim();
    return !id.isEmpty() && byId.containsKey(id);
  }

  public Flowable<List<IrcProperties.Server>> updates() {
    return updates.onBackpressureLatest();
  }

  public synchronized void setAll(List<IrcProperties.Server> servers) {
    byId.clear();
    if (servers != null) {
      for (IrcProperties.Server s : servers) {
        if (s == null) continue;
        String id = Objects.toString(s.id(), "").trim();
        if (id.isEmpty()) continue;
        byId.put(id, s);
      }
    }
    persistAndEmit();
  }

  public synchronized void upsert(IrcProperties.Server server) {
    if (server == null) return;
    String id = Objects.toString(server.id(), "").trim();
    if (id.isEmpty()) throw new IllegalArgumentException("server.id is required");
    byId.put(id, server);
    persistAndEmit();
  }

  public synchronized void remove(String serverId) {
    String id = Objects.toString(serverId, "").trim();
    if (id.isEmpty()) return;
    byId.remove(id);
    persistAndEmit();
  }

  /**
   * Updates the in-memory auto-join list for an existing persisted server without rewriting the
   * runtime config file.
   *
   * <p>This is used after runtime config mutations that already persisted the authoritative
   * auto-join override on disk; reconnect logic needs the in-process registry view refreshed too.
   */
  public synchronized void syncRuntimeAutoJoin(String serverId, List<String> autoJoin) {
    String requestedId = Objects.toString(serverId, "").trim();
    if (requestedId.isEmpty()) return;

    String storedId = null;
    for (String id : byId.keySet()) {
      if (id != null && id.equalsIgnoreCase(requestedId)) {
        storedId = id;
        break;
      }
    }
    if (storedId == null) return;

    IrcProperties.Server existing = byId.get(storedId);
    if (existing == null) return;

    List<String> nextAutoJoin = autoJoin == null ? List.of() : List.copyOf(autoJoin);
    if (Objects.equals(existing.autoJoin(), nextAutoJoin)) return;

    byId.put(storedId, existing.withAutoJoin(nextAutoJoin));
    updates.onNext(snapshot());
  }

  private void persistAndEmit() {
    List<IrcProperties.Server> snap = snapshot();
    // Persist the full list into the runtime YAML.
    runtimeConfig.writeServers(snap);
    updates.onNext(snap);
  }

  private List<IrcProperties.Server> snapshot() {
    return List.copyOf(byId.values());
  }

  private static IrcProperties.Server withRuntimeAutoJoinOverride(
      IrcProperties.Server server, Map<String, List<String>> runtimeAutoJoinByServer) {
    if (server == null || runtimeAutoJoinByServer == null || runtimeAutoJoinByServer.isEmpty()) {
      return server;
    }
    List<String> runtimeAutoJoin = findAutoJoinForServer(runtimeAutoJoinByServer, server.id());
    if (runtimeAutoJoin == null) return server;
    return copyServerWithAutoJoin(server, runtimeAutoJoin);
  }

  private static List<String> findAutoJoinForServer(
      Map<String, List<String>> runtimeAutoJoinByServer, String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return null;
    List<String> exact = runtimeAutoJoinByServer.get(sid);
    if (exact != null) return exact;
    for (Map.Entry<String, List<String>> entry : runtimeAutoJoinByServer.entrySet()) {
      String key = Objects.toString(entry.getKey(), "").trim();
      if (!key.isEmpty() && key.equalsIgnoreCase(sid)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static IrcProperties.Server copyServerWithAutoJoin(
      IrcProperties.Server server, List<String> autoJoin) {
    return server.withAutoJoin(autoJoin);
  }
}

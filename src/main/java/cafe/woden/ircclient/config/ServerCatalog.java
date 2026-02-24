package cafe.woden.ircclient.config;

import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Read-only view over all servers currently available at runtime.
 *
 * <p>This merges the persisted {@link ServerRegistry} and the in-memory {@link
 * EphemeralServerRegistry} so connection code can treat both as first-class.
 */
@Component
public class ServerCatalog {

  private final ServerRegistry serverRegistry;
  private final EphemeralServerRegistry ephemeralServers;

  public ServerCatalog(ServerRegistry serverRegistry, EphemeralServerRegistry ephemeralServers) {
    this.serverRegistry = Objects.requireNonNull(serverRegistry, "serverRegistry");
    this.ephemeralServers = Objects.requireNonNull(ephemeralServers, "ephemeralServers");
  }

  public Optional<IrcProperties.Server> find(String serverId) {
    String id = norm(serverId);
    if (id == null) return Optional.empty();
    Optional<IrcProperties.Server> persisted = serverRegistry.find(id);
    if (persisted.isPresent()) return persisted;
    return ephemeralServers.find(id);
  }

  public IrcProperties.Server require(String serverId) {
    return find(serverId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown server id: " + serverId));
  }

  public boolean containsId(String serverId) {
    String id = norm(serverId);
    if (id == null) return false;
    return serverRegistry.containsId(id) || ephemeralServers.containsId(id);
  }

  public Optional<ServerEntry> findEntry(String serverId) {
    String id = norm(serverId);
    if (id == null) return Optional.empty();

    Optional<IrcProperties.Server> persisted = serverRegistry.find(id);
    if (persisted.isPresent()) {
      return Optional.of(ServerEntry.persistent(persisted.get()));
    }

    Optional<IrcProperties.Server> eph = ephemeralServers.find(id);
    if (eph.isEmpty()) return Optional.empty();
    String origin = ephemeralServers.originOf(id).orElse(null);
    return Optional.of(ServerEntry.ephemeral(eph.get(), origin));
  }

  public ServerEntry requireEntry(String serverId) {
    return findEntry(serverId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown server id: " + serverId));
  }

  /** Snapshot list of all servers (persisted + ephemeral). */
  public List<ServerEntry> entries() {
    List<IrcProperties.Server> persisted = serverRegistry.servers();
    List<ServerEntry> ephem = ephemeralServers.entries();

    ArrayList<ServerEntry> out = new ArrayList<>(persisted.size() + ephem.size());
    for (IrcProperties.Server s : persisted) {
      if (s == null) continue;
      out.add(ServerEntry.persistent(s));
    }
    out.addAll(ephem);
    return List.copyOf(out);
  }

  /** Reactive stream of all servers (persisted + ephemeral). */
  public Flowable<List<ServerEntry>> updates() {
    Flowable<List<ServerEntry>> persisted =
        serverRegistry
            .updates()
            .map(
                list -> {
                  ArrayList<ServerEntry> out = new ArrayList<>(list.size());
                  for (IrcProperties.Server s : list) {
                    if (s == null) continue;
                    out.add(ServerEntry.persistent(s));
                  }
                  return List.copyOf(out);
                });

    return Flowable.combineLatest(
            persisted,
            ephemeralServers.updates(),
            (p, e) -> {
              ArrayList<ServerEntry> out = new ArrayList<>(p.size() + e.size());
              out.addAll(p);
              out.addAll(e);
              return List.copyOf(out);
            })
        .distinctUntilChanged();
  }

  private static String norm(String s) {
    String v = Objects.toString(s, "").trim();
    return v.isEmpty() ? null : v;
  }
}

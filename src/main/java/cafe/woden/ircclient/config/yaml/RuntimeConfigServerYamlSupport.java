package cafe.woden.ircclient.config.yaml;

import static cafe.woden.ircclient.config.yaml.RuntimeConfigYamlSupport.getOrCreateMap;
import static cafe.woden.ircclient.config.yaml.RuntimeConfigYamlSupport.readMap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.slf4j.Logger;

/** Shared helpers for runtime configuration stored under {@code irc.servers[]}. */
@InfrastructureLayer
public final class RuntimeConfigServerYamlSupport {

  private RuntimeConfigServerYamlSupport() {}

  @SuppressWarnings("unchecked")
  public static Optional<List<Map<String, Object>>> readServerList(Map<String, Object> irc) {
    Object raw = irc.get("servers");
    if (raw instanceof List<?>) {
      return Optional.of((List<Map<String, Object>>) raw);
    }
    return Optional.empty();
  }

  public static Optional<Map<String, Object>> findServerById(
      List<Map<String, Object>> servers, String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty() || servers == null || servers.isEmpty()) return Optional.empty();

    for (Map<String, Object> server : servers) {
      if (server == null) continue;
      if (sid.equalsIgnoreCase(Objects.toString(server.get("id"), "").trim())) {
        return Optional.of(server);
      }
    }
    return Optional.empty();
  }

  public static Optional<Map<String, Object>> readExistingServer(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      String serverId) {
    try {
      if (file.toString().isBlank()) return Optional.empty();
      String sid = normalizeServerId(serverId);
      if (sid.isEmpty()) return Optional.empty();
      if (!documentStore.documentExists()) return Optional.empty();

      Map<String, Object> doc = documentStore.load();
      Map<String, Object> irc = readMap(doc, "irc").orElse(null);
      if (irc == null) return Optional.empty();

      return readServerList(irc).flatMap(servers -> findServerById(servers, sid));
    } catch (Exception e) {
      log.warn("[ircafe] Could not read {} from '{}'", description, file, e);
      return Optional.empty();
    }
  }

  public static void mutateExistingServer(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      String serverId,
      Consumer<Map<String, Object>> mutation) {
    try {
      if (file.toString().isBlank()) return;
      String sid = normalizeServerId(serverId);
      if (sid.isEmpty()) return;

      Map<String, Object> doc = documentStore.loadOrEmpty();
      Map<String, Object> irc = getOrCreateMap(doc, "irc");
      List<Map<String, Object>> servers = readServerList(irc).orElseGet(ArrayList::new);
      Map<String, Object> found = findServerById(servers, sid).orElse(null);

      // Do not auto-create missing servers: removed servers must stay removed.
      if (found == null) return;

      mutation.accept(found);
      irc.put("servers", servers);
      documentStore.write(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist {} to '{}'", description, file, e);
    }
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}

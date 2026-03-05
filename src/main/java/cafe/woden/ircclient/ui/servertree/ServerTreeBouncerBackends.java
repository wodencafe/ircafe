package cafe.woden.ircclient.ui.servertree;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared backend ids and conventions for bouncer-discovered server-tree state. */
public final class ServerTreeBouncerBackends {

  public static final String SOJU = "soju";
  public static final String ZNC = "znc";
  public static final String GENERIC = "generic";

  private static final Map<String, String> PREFIX_BY_BACKEND_ID =
      Map.of(SOJU, "soju:", ZNC, "znc:", GENERIC, "bouncer:");
  private static final Map<String, String> NETWORKS_GROUP_LABEL_BY_BACKEND_ID =
      Map.of(SOJU, "Soju Networks", ZNC, "ZNC Networks", GENERIC, "Bouncer Networks");
  private static final Map<String, String> NETWORKS_GROUP_TOOLTIP_BY_BACKEND_ID =
      Map.of(
          SOJU,
          "Soju networks discovered from the bouncer (not saved).",
          ZNC,
          "ZNC networks discovered from the bouncer (not saved).",
          GENERIC,
          "Bouncer networks discovered from generic protocol lines (not saved).");
  private static final Map<String, String> EPHEMERAL_DISCOVERY_TOOLTIP_BY_BACKEND_ID =
      Map.of(
          SOJU,
          "Discovered from soju; not saved.",
          ZNC,
          "Discovered from ZNC; not saved.",
          GENERIC,
          "Discovered from generic bouncer protocol; not saved.");

  private ServerTreeBouncerBackends() {}

  public static List<String> orderedIds() {
    return List.of(SOJU, ZNC, GENERIC);
  }

  public static String prefixFor(String backendId) {
    return PREFIX_BY_BACKEND_ID.get(normalize(backendId));
  }

  public static String defaultNetworksGroupLabel(String backendId) {
    String backend = normalize(backendId);
    return NETWORKS_GROUP_LABEL_BY_BACKEND_ID.getOrDefault(backend, "Bouncer Networks");
  }

  public static String networksGroupTooltip(String backendId) {
    String backend = normalize(backendId);
    return NETWORKS_GROUP_TOOLTIP_BY_BACKEND_ID.getOrDefault(
        backend, "Bouncer networks discovered from the bouncer (not saved).");
  }

  public static String ephemeralDiscoveryTooltip(String backendId) {
    String backend = normalize(backendId);
    return EPHEMERAL_DISCOVERY_TOOLTIP_BY_BACKEND_ID.getOrDefault(
        backend, "Discovered from bouncer backend; not saved.");
  }

  public static String backendIdForServerId(String serverId) {
    String id = normalize(serverId);
    if (id.isEmpty()) return null;
    for (String backendId : orderedIds()) {
      String prefix = prefixFor(backendId);
      if (prefix != null && id.startsWith(prefix)) {
        return backendId;
      }
    }
    return null;
  }

  public static boolean isBouncerServerId(String serverId) {
    return backendIdForServerId(serverId) != null;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}

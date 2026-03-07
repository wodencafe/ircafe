package cafe.woden.ircclient.app.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Tracks Quassel setup/sync lifecycle state used by {@link ConnectionCoordinator}. */
final class QuasselSetupLifecycleState {

  record FeatureUpdate(String phase, String detail) {}

  private final Map<String, String> lastFeatureMarkerByServer = new HashMap<>();
  private final Set<String> setupPendingServers = new HashSet<>();
  private final Set<String> openNetworkManagerOnSyncReadyServers = new HashSet<>();

  Optional<FeatureUpdate> onFeatureMarker(String serverId, String source) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return Optional.empty();
    String marker = Objects.toString(source, "").trim();
    if (marker.isEmpty()) return Optional.empty();
    String previousMarker = lastFeatureMarkerByServer.put(sid, marker);
    if (Objects.equals(previousMarker, marker)) return Optional.empty();

    String phase = quasselFeaturePhase(marker);
    if (phase.isEmpty()) return Optional.empty();
    return Optional.of(new FeatureUpdate(phase, quasselFeatureDetail(marker)));
  }

  void clearFeatureMarker(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    lastFeatureMarkerByServer.remove(sid);
  }

  void markSetupPending(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    setupPendingServers.add(sid);
  }

  void clearSetupPending(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    setupPendingServers.remove(sid);
  }

  boolean isSetupPending(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return setupPendingServers.contains(sid);
  }

  int setupPendingCount(Collection<String> serverIds) {
    if (serverIds == null || serverIds.isEmpty()) return 0;
    int count = 0;
    for (String sid : serverIds) {
      if (isSetupPending(sid)) count++;
    }
    return count;
  }

  void markSetupSubmitted(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    openNetworkManagerOnSyncReadyServers.add(sid);
  }

  boolean consumeOpenNetworkManagerOnSyncReady(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return openNetworkManagerOnSyncReadyServers.remove(sid);
  }

  void clearServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    lastFeatureMarkerByServer.remove(sid);
    setupPendingServers.remove(sid);
    openNetworkManagerOnSyncReadyServers.remove(sid);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String quasselFeaturePhase(String source) {
    String src = Objects.toString(source, "").trim();
    if (src.isEmpty()) return "";
    int idx = src.indexOf("quassel-phase=");
    if (idx < 0) return "";
    int start = idx + "quassel-phase=".length();
    int end = src.indexOf(';', start);
    if (end < 0) end = src.length();
    if (end <= start) return "";
    return src.substring(start, end).trim().toLowerCase(Locale.ROOT);
  }

  private static String quasselFeatureDetail(String source) {
    String src = Objects.toString(source, "").trim();
    if (src.isEmpty()) return "";
    int idx = src.indexOf(";detail=");
    if (idx < 0) return "";
    int start = idx + ";detail=".length();
    if (start >= src.length()) return "";
    return src.substring(start).trim();
  }
}

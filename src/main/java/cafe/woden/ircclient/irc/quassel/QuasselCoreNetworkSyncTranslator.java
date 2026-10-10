package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.collectPotentialNetworkStateMaps;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.flattenNetworkStateFromKeyValueParams;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.networkIdFromStateMap;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlank;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.mapValueIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.parseBoolean;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.tryParseInt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodes network sync/init payloads through session observation callbacks.
 *
 * <p>Scalar properties update snapshots only; full state maps also inform capability and monitor
 * observers. The caller keeps membership processing between property and direct-map observations.
 */
final class QuasselCoreNetworkSyncTranslator {
  private static final Logger log = LoggerFactory.getLogger(QuasselCoreNetworkSyncTranslator.class);

  private final String serverId;
  private final BiConsumer<Integer, String> observeNetwork;
  private final BiConsumer<Integer, Map<?, ?>> observeSnapshot;
  private final BiConsumer<Integer, Map<?, ?>> observeState;
  private final BiConsumer<Integer, String> observeNick;

  QuasselCoreNetworkSyncTranslator(
      String serverId,
      BiConsumer<Integer, String> observeNetwork,
      BiConsumer<Integer, Map<?, ?>> observeSnapshot,
      BiConsumer<Integer, Map<?, ?>> observeState,
      BiConsumer<Integer, String> observeNick) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.observeNetwork = Objects.requireNonNull(observeNetwork, "observeNetwork");
    this.observeSnapshot = Objects.requireNonNull(observeSnapshot, "observeSnapshot");
    this.observeState = Objects.requireNonNull(observeState, "observeState");
    this.observeNick = Objects.requireNonNull(observeNick, "observeNick");
  }

  void handleProperty(String objectName, String slotName, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    int networkId = parseNetworkId(objectName);
    if (networkId < 0) return;
    Object value = values.getFirst();
    switch (slotName) {
      case "setNetworkName" -> {
        String name = Objects.toString(value, "").trim();
        if (!name.isEmpty()) {
          observeNetwork.accept(networkId, name);
          observeSnapshot.accept(networkId, Map.of("networkName", name));
        }
      }
      case "setConnected" -> {
        Boolean connected = parseBoolean(value);
        if (connected != null) {
          observeSnapshot.accept(networkId, Map.of("isConnected", connected));
        }
      }
      case "setConnectionState" -> {
        int state = tryParseInt(value);
        if (state >= 0) {
          observeSnapshot.accept(networkId, Map.of("connectionState", state));
        }
      }
      case "setMyNick" -> observeNick.accept(networkId, Objects.toString(value, ""));
      default -> {}
    }
  }

  void observeUnknownState(
      String classToken, String objectName, String slotToken, List<Object> values) {
    String className = Objects.toString(classToken, "").trim();
    if (!className.toLowerCase(Locale.ROOT).contains("network")) {
      return;
    }
    if (values == null || values.isEmpty()) return;

    int fallbackNetworkId = parseNetworkId(objectName);
    ArrayList<Map<?, ?>> candidates = new ArrayList<>();
    Map<String, Object> flattened = flattenNetworkStateFromKeyValueParams(values);
    if (!flattened.isEmpty()) {
      candidates.add(flattened);
    }
    for (Object value : values) {
      collectPotentialNetworkStateMaps(value, candidates);
    }
    if (candidates.isEmpty()) {
      log.debug(
          "Network-related sync envelope had no map payloads to inspect: serverId={}, className={}, objectName={}, slotName={}, params={}",
          serverId,
          className,
          objectName,
          slotToken,
          values);
      return;
    }

    int applied = 0;
    for (Map<?, ?> candidate : candidates) {
      if (candidate == null || candidate.isEmpty()) continue;
      int networkId = networkIdFromStateMap(candidate, fallbackNetworkId);
      String networkName =
          firstNonBlank(
              mapValueIgnoreCase(candidate, "networkName"),
              mapValueIgnoreCase(candidate, "networkname"),
              mapValueIgnoreCase(candidate, "name"));
      boolean looksLikeNetworkState =
          networkId >= 0
              || !networkName.isEmpty()
              || mapValueIgnoreCase(candidate, "ServerList") != null
              || mapValueIgnoreCase(candidate, "serverList") != null;
      if (!looksLikeNetworkState) continue;

      observeNetwork.accept(networkId, networkName);
      observeState.accept(networkId, candidate);
      applied++;
      log.debug(
          "Applied network state from unknown sync class: serverId={}, className={}, objectName={}, slotName={}, networkId={}, networkName={}, state={}",
          serverId,
          className,
          objectName,
          slotToken,
          networkId,
          networkName,
          candidate);
    }

    if (applied == 0) {
      log.debug(
          "Network-related sync envelope maps were inspected but no network states were derived: serverId={}, className={}, objectName={}, slotName={}, mapCount={}",
          serverId,
          className,
          objectName,
          slotToken,
          candidates.size());
    }
  }

  void observeNetworkState(String objectName, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    int objectNetworkId = parseNetworkId(objectName);
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map)) continue;
      int networkId = networkIdFromStateMap(map, objectNetworkId);
      String networkName =
          firstNonBlank(map.get("networkName"), map.get("networkname"), map.get("name"));
      observeNetwork.accept(networkId, networkName);
      observeState.accept(networkId, map);
      Object maybeNick = map.get("myNick");
      String next = Objects.toString(maybeNick, "").trim();
      if (!next.isEmpty()) {
        observeNick.accept(networkId, next);
      }
    }
  }

  void observeNetworkInfo(String objectName, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    int objectNetworkId = parseNetworkId(objectName);
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map)) continue;
      int networkId = networkIdFromStateMap(map, objectNetworkId);
      String networkName =
          firstNonBlank(map.get("networkName"), map.get("networkname"), map.get("name"));
      log.debug(
          "Quassel NetworkInfo sync observed: serverId={}, objectName={}, networkId={}, networkName={}, map={}",
          serverId,
          objectName,
          networkId,
          networkName,
          map);
      observeNetwork.accept(networkId, networkName);
      observeState.accept(networkId, map);
    }
  }
}

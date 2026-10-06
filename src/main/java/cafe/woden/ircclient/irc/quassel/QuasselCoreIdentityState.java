package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.containsAnyMapKeysIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstIntFromMapKeys;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstMapValueByKeyIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlank;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.mapValueIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.normalizeObjectMap;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.trimMapToMaxSize;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.tryParseInt;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Session-owned identity state and Core lifecycle observations. */
final class QuasselCoreIdentityState {
  private static final Logger log = LoggerFactory.getLogger(QuasselCoreIdentityState.class);
  private final String serverId;
  private final int maxStates;
  private final Consumer<String> observed;
  private final Map<Integer, Map<String, Object>> states = new ConcurrentHashMap<>();
  private final Map<Integer, String> names = new ConcurrentHashMap<>();
  private final Set<Integer> knownIds = ConcurrentHashMap.newKeySet();

  QuasselCoreIdentityState(String serverId, int maxStates, Consumer<String> observed) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.maxStates = maxStates;
    this.observed = Objects.requireNonNull(observed, "observed");
  }

  Set<Integer> knownIds() {
    return Collections.unmodifiableSet(knownIds);
  }

  Set<Integer> stateIds() {
    return Collections.unmodifiableSet(states.keySet());
  }

  Map<Integer, String> names() {
    return Collections.unmodifiableMap(names);
  }

  Map<String, Object> state(int identityId) {
    return states.getOrDefault(identityId, Map.of());
  }

  boolean isKnown(int identityId) {
    return knownIds.contains(identityId);
  }

  void remove(int identityId) {
    knownIds.remove(identityId);
    states.remove(identityId);
    names.remove(identityId);
  }

  void clear() {
    states.clear();
    names.clear();
    knownIds.clear();
  }

  void handleRpc(String slotName, List<Object> params) {
    String slot = Objects.toString(slotName, "").trim().toLowerCase(Locale.ROOT);
    if (slot.isEmpty() || !slot.contains("identity")) return;
    boolean remove = slot.contains("remove") || slot.contains("deleted");
    if (params == null || params.isEmpty()) {
      log.debug(
          "Observed identity lifecycle RPC with no params: serverId={}, slot={}, remove={}",
          serverId,
          slotName,
          remove);
      return;
    }

    for (Object param : params) {
      observeIdentityLifecycleFromRpcParam(slotName, param, remove);
    }
  }

  private void observeIdentityLifecycleFromRpcParam(String slotName, Object raw, boolean remove) {
    if (raw == null) return;
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        observeIdentityLifecycleFromRpcParam(slotName, value, remove);
      }
      return;
    }
    if (raw instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      String type = Objects.toString(userType.typeName(), "").trim();
      Object value = userType.value();
      if ("IdentityId".equals(type)) {
        int identityId = tryParseInt(value);
        if (identityId >= 0) {
          if (remove) {
            remove(identityId);
          } else {
            observe(identityId, "");
          }
          log.debug(
              "Observed identity lifecycle RPC id user-type: serverId={}, slot={}, remove={}, identityId={}",
              serverId,
              slotName,
              remove,
              identityId);
        }
      }
      observeIdentityLifecycleFromRpcParam(slotName, value, remove);
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      int identityId = identityIdFromStateMap(map, -1);
      String identityName = parseIdentityName(map);
      if (identityId >= 0) {
        if (remove) {
          remove(identityId);
        } else {
          observe(identityId, identityName);
          states.put(identityId, normalizeObjectMap(map));
          trimMapToMaxSize(states, maxStates);
        }
      }
      log.debug(
          "Observed identity lifecycle RPC map: serverId={}, slot={}, remove={}, identityId={}, identityName={}, map={}",
          serverId,
          slotName,
          remove,
          identityId,
          identityName,
          map);
      return;
    }
  }

  void observeUnknownState(
      Object raw, int fallbackIdentityId, String sourceClass, String objectName, String slotName) {
    if (raw == null) return;
    if (raw instanceof Map<?, ?> map) {
      int identityId = identityIdFromStateMap(map, fallbackIdentityId);
      String identityName = parseIdentityName(map);
      boolean hasIdentityKeys =
          containsAnyMapKeysIgnoreCase(
              map,
              "identityId",
              "identityid",
              "identityName",
              "identityname",
              "nicks",
              "awayNick",
              "realName",
              "ident",
              "kickReason",
              "partReason",
              "quitReason");
      boolean looksLikeIdentity = hasIdentityKeys || (identityId >= 0 && !identityName.isEmpty());
      if (looksLikeIdentity) {
        observe(identityId, identityName);
        if (identityId >= 0) {
          states.put(identityId, normalizeObjectMap(map));
          trimMapToMaxSize(states, maxStates);
        }
        log.debug(
            "Observed identity state from {} sync: serverId={}, objectName={}, slotName={}, identityId={}, identityName={}, map={}",
            sourceClass,
            serverId,
            objectName,
            slotName,
            identityId,
            identityName,
            map);
      }
      for (Object value : map.values()) {
        observeUnknownState(value, fallbackIdentityId, sourceClass, objectName, slotName);
      }
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        observeUnknownState(value, fallbackIdentityId, sourceClass, objectName, slotName);
      }
      return;
    }
    if (raw instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      String type = Objects.toString(userType.typeName(), "").trim();
      Object value = userType.value();
      if ("IdentityId".equals(type)) {
        int identityId = tryParseInt(value);
        if (identityId >= 0) {
          observe(identityId, "");
          log.debug(
              "Observed identity id from {} sync user-type: serverId={}, objectName={}, slotName={}, identityId={}",
              sourceClass,
              serverId,
              objectName,
              slotName,
              identityId);
        }
      }
      int nestedFallback = "IdentityId".equals(type) ? tryParseInt(value) : fallbackIdentityId;
      observeUnknownState(value, nestedFallback, sourceClass, objectName, slotName);
      return;
    }
  }

  void handleCoreInfoSync(String objectName, String slotName, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map) || map.isEmpty()) continue;
      log.debug(
          "Quassel CoreInfo sync observed: serverId={}, objectName={}, slotName={}, keys={}, map={}",
          serverId,
          objectName,
          slotName,
          map.keySet(),
          map);
      Object identities = firstMapValueByKeyIgnoreCase(map, "Identities", "identities");
      if (identities != null) {
        observeUnknownState(identities, -1, "CoreInfo.Identities", objectName, slotName);
      }
    }
  }

  void handleSync(String objectName, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    int objectIdentityId = parseNetworkId(objectName);
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map) || map.isEmpty()) continue;
      int identityId = identityIdFromStateMap(map, objectIdentityId);
      String identityName = parseIdentityName(map);
      observe(identityId, identityName);
      if (identityId >= 0) {
        states.put(identityId, normalizeObjectMap(map));
        trimMapToMaxSize(states, maxStates);
      }
      log.debug(
          "Quassel Identity sync observed: serverId={}, objectName={}, identityId={}, identityName={}, map={}",
          serverId,
          objectName,
          identityId,
          identityName,
          map);
    }
  }

  void initialize(Map<Integer, Map<String, Object>> initialIdentities) {
    if (initialIdentities == null) return;
    for (Map.Entry<Integer, Map<String, Object>> entry : initialIdentities.entrySet()) {
      if (entry == null || entry.getKey() == null || entry.getKey().intValue() < 0) continue;
      int identityId = entry.getKey().intValue();
      Map<String, Object> normalized = normalizeObjectMap(entry.getValue());
      String identityName =
          firstNonBlank(
              mapValueIgnoreCase(normalized, "identityName"),
              mapValueIgnoreCase(normalized, "identityname"));
      observe(identityId, identityName);
      if (!normalized.isEmpty()) {
        states.put(identityId, normalized);
      }
    }
    trimMapToMaxSize(states, maxStates);
  }

  void observe(int identityId, String identityName) {
    if (identityId <= 0) return;
    String name = Objects.toString(identityName, "").trim();
    boolean added = knownIds.add(identityId);
    String previousName = names.get(identityId);
    if (!name.isEmpty()) {
      names.put(identityId, name);
      trimMapToMaxSize(names, maxStates);
    } else {
      names.putIfAbsent(identityId, "");
    }
    if (added || (!name.isEmpty() && !name.equals(previousName))) {
      log.debug(
          "Observed Quassel identity: serverId={}, identityId={}, identityName='{}', knownIdentityIds={}",
          serverId,
          identityId,
          names.get(identityId),
          knownIds);
    }
    observed.accept(serverId);
  }

  int firstKnownId() {
    if (knownIds.isEmpty()) return -1;
    int best = Integer.MAX_VALUE;
    for (Integer candidate : knownIds) {
      if (candidate == null || candidate.intValue() < 0) continue;
      if (candidate.intValue() < best) {
        best = candidate.intValue();
      }
    }
    return best == Integer.MAX_VALUE ? -1 : best;
  }

  boolean hasKnown() {
    return firstKnownId() >= 0;
  }

  private static int identityIdFromStateMap(Map<?, ?> state, int fallbackIdentityId) {
    if (state == null || state.isEmpty()) return fallbackIdentityId;
    int id = firstIntFromMapKeys(state, "identityId", "identityid");
    if (id > 0) return id;
    Object wrapped = firstMapValueByKeyIgnoreCase(state, "identityId", "identityid");
    if (wrapped instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      int parsed = tryParseInt(userType.value());
      if (parsed > 0) return parsed;
    }
    return fallbackIdentityId;
  }

  private static String parseIdentityName(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return "";
    return firstNonBlank(
        mapValueIgnoreCase(state, "identityName"),
        mapValueIgnoreCase(state, "identityname"),
        mapValueIgnoreCase(state, "name"));
  }

  static boolean looksUsable(Map<?, ?> identityState) {
    if (identityState == null || identityState.isEmpty()) return false;
    int identityId = identityIdFromStateMap(identityState, -1);
    if (identityId <= 0) return false;
    String identityName =
        firstNonBlank(
            mapValueIgnoreCase(identityState, "identityName"),
            mapValueIgnoreCase(identityState, "identityname"),
            mapValueIgnoreCase(identityState, "name"));
    if (identityName.isBlank()) return false;
    Object rawNicks = firstMapValueByKeyIgnoreCase(identityState, "nicks", "Nicks", "nick", "Nick");
    if (rawNicks instanceof List<?> nicks) {
      for (Object nick : nicks) {
        if (!Objects.toString(nick, "").trim().isBlank()) return true;
      }
      return false;
    }
    String singleNick =
        firstNonBlank(
            mapValueIgnoreCase(identityState, "nick"), mapValueIgnoreCase(identityState, "Nick"));
    return !singleNick.isBlank();
  }
}

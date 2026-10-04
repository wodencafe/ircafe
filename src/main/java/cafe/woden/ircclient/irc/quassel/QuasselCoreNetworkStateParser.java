package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstIntFromMapKeys;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstMapValueByKeyIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlank;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.mapValueIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.parseBoolean;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.tryParseInt;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Decodes network snapshots and wire payloads without changing session state. */
final class QuasselCoreNetworkStateParser {
  private QuasselCoreNetworkStateParser() {}

  record NetworkServerEndpoint(String host, int port, boolean useTls) {
    private static final NetworkServerEndpoint EMPTY = new NetworkServerEndpoint("", 0, false);
  }

  static void collectPotentialNetworkStateMaps(Object raw, List<Map<?, ?>> out) {
    if (raw == null || out == null) return;
    if (raw instanceof Map<?, ?> map) {
      out.add(map);
      for (Object value : map.values()) {
        collectPotentialNetworkStateMaps(value, out);
      }
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectPotentialNetworkStateMaps(value, out);
      }
      return;
    }
    if (raw instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      collectPotentialNetworkStateMaps(userType.value(), out);
    }
  }

  static Map<String, Object> flattenNetworkStateFromKeyValueParams(List<Object> values) {
    if (values == null || values.size() < 4 || (values.size() % 2) != 0) return Map.of();
    LinkedHashMap<String, Object> flattened = new LinkedHashMap<>();
    int decodedPairs = 0;
    for (int i = 0; i + 1 < values.size(); i += 2) {
      String key = decodeNetworkStateKey(values.get(i));
      if (key.isEmpty()) continue;
      flattened.put(key, values.get(i + 1));
      decodedPairs++;
    }
    // Guard against accidental flattening of non-key/value payloads.
    if (decodedPairs < 6) return Map.of();
    return Collections.unmodifiableMap(flattened);
  }

  static String decodeNetworkStateKey(Object raw) {
    if (raw == null) return "";
    if (raw instanceof byte[] bytes) {
      return new String(bytes, StandardCharsets.UTF_8).trim();
    }
    return Objects.toString(raw, "").trim();
  }

  static boolean parseNetworkConnected(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return false;
    Boolean direct = parseBoolean(mapValueIgnoreCase(state, "isConnected"));
    if (direct != null) return direct;
    direct = parseBoolean(mapValueIgnoreCase(state, "connected"));
    if (direct != null) return direct;

    int connectionState =
        firstIntFromMapKeys(state, "connectionState", "connectionstate", "state", "status");
    if (connectionState >= 0) return connectionState != 0;
    return false;
  }

  static boolean parseNetworkEnabled(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return true;
    Boolean enabled = parseBoolean(mapValueIgnoreCase(state, "isEnabled"));
    if (enabled != null) return enabled;
    enabled = parseBoolean(mapValueIgnoreCase(state, "enabled"));
    if (enabled != null) return enabled;
    Boolean initialized = parseBoolean(mapValueIgnoreCase(state, "isInitialized"));
    if (initialized != null) return initialized;
    return true;
  }

  static int parseNetworkIdentityId(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return -1;
    Object identity = firstMapValueByKeyIgnoreCase(state, "identity", "identityId", "identityid");
    if (identity instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      int parsed = tryParseInt(userType.value());
      if (parsed > 0) return parsed;
    }
    int id = firstIntFromMapKeys(state, "identity", "identityId", "identityid");
    return id > 0 ? id : -1;
  }

  static NetworkServerEndpoint parsePrimaryNetworkServer(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return NetworkServerEndpoint.EMPTY;
    Object rawServerList =
        firstMapValueByKeyIgnoreCase(state, "ServerList", "serverList", "servers");
    if (rawServerList instanceof List<?> list) {
      for (Object entry : list) {
        if (entry instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
          Object value = userType.value();
          if (value instanceof Map<?, ?> userMap) {
            NetworkServerEndpoint parsed = parseNetworkServerEntry(userMap);
            if (!parsed.host().isBlank()) return parsed;
          }
        }
        if (!(entry instanceof Map<?, ?> serverMap)) continue;
        NetworkServerEndpoint parsed = parseNetworkServerEntry(serverMap);
        if (!parsed.host().isBlank()) return parsed;
      }
    }
    if (rawServerList instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      Object value = userType.value();
      if (value instanceof Map<?, ?> userMap) {
        NetworkServerEndpoint parsed = parseNetworkServerEntry(userMap);
        if (!parsed.host().isBlank()) return parsed;
      }
    }
    if (rawServerList instanceof Map<?, ?> serverMap) {
      NetworkServerEndpoint parsed = parseNetworkServerEntry(serverMap);
      if (!parsed.host().isBlank()) return parsed;
    }
    return parseNetworkServerEntry(state);
  }

  private static NetworkServerEndpoint parseNetworkServerEntry(Map<?, ?> serverMap) {
    if (serverMap == null || serverMap.isEmpty()) return NetworkServerEndpoint.EMPTY;
    String host =
        firstNonBlank(
            mapValueIgnoreCase(serverMap, "server"),
            mapValueIgnoreCase(serverMap, "host"),
            mapValueIgnoreCase(serverMap, "hostname"));
    if (host.isBlank()) return NetworkServerEndpoint.EMPTY;

    int port = firstIntFromMapKeys(serverMap, "port");
    Boolean useTls = parseBoolean(mapValueIgnoreCase(serverMap, "useSSL"));
    if (useTls == null) useTls = parseBoolean(mapValueIgnoreCase(serverMap, "ssl"));
    boolean tls = Boolean.TRUE.equals(useTls);
    int resolvedPort = port > 0 ? port : (tls ? 6697 : 6667);
    return new NetworkServerEndpoint(host, resolvedPort, tls);
  }

  static int networkIdFromStateMap(Map<?, ?> map, int fallbackNetworkId) {
    if (map == null || map.isEmpty()) return fallbackNetworkId;
    int byNetworkId = tryParseInt(map.get("networkId"));
    if (byNetworkId >= 0) return byNetworkId;
    int byNetwork = tryParseInt(map.get("network"));
    if (byNetwork >= 0) return byNetwork;
    int byId = tryParseInt(map.get("id"));
    if (byId >= 0) return byId;
    return fallbackNetworkId;
  }

  static int parseNetworkId(String raw) {
    String token = Objects.toString(raw, "").trim();
    if (token.isEmpty()) return -1;
    int slash = token.lastIndexOf('/');
    if (slash >= 0 && slash < token.length() - 1) {
      token = token.substring(slash + 1).trim();
    }
    try {
      return Integer.parseInt(token);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  static boolean networkStateLooksUsableForConnect(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return false;
    NetworkServerEndpoint endpoint = parsePrimaryNetworkServer(state);
    if (!endpoint.host().isBlank()) return true;
    if (parseNetworkIdentityId(state) > 0) return true;
    String networkName =
        firstNonBlank(
            mapValueIgnoreCase(state, "networkName"),
            mapValueIgnoreCase(state, "networkname"),
            mapValueIgnoreCase(state, "name"));
    return !networkName.isBlank();
  }
}

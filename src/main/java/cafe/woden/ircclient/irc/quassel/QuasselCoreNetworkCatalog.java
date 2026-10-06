package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkConnected;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkEnabled;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkIdentityId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parsePrimaryNetworkServer;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlank;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.mapValueIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.normalizeObjectMap;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.trimMapToMaxSize;

import cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.NetworkServerEndpoint;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkSummary;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/** Session-owned network metadata, routing tokens, and side-effect-free catalog reads. */
final class QuasselCoreNetworkCatalog {
  private static final int MAX_PENDING_NETWORK_CREATE_NAMES = 32;
  private static final long PENDING_NETWORK_CREATE_NAME_TTL_MS = TimeUnit.MINUTES.toMillis(2);

  private final int maxEntries;
  private final IntConsumer identityObserved;
  private final LongSupplier nowMs;
  private final ConcurrentLinkedDeque<PendingCreatedNetworkName> pendingCreatedNames =
      new ConcurrentLinkedDeque<>();
  private final Map<Integer, String> displayNames = new ConcurrentHashMap<>();
  private final Map<Integer, String> tokens = new ConcurrentHashMap<>();
  private final Map<String, Integer> idsByToken = new ConcurrentHashMap<>();
  private final Map<Integer, Map<String, Object>> states = new ConcurrentHashMap<>();
  private final Set<Integer> removedIds = ConcurrentHashMap.newKeySet();

  QuasselCoreNetworkCatalog(int maxEntries, IntConsumer identityObserved) {
    this(maxEntries, identityObserved, System::currentTimeMillis);
  }

  QuasselCoreNetworkCatalog(int maxEntries, IntConsumer identityObserved, LongSupplier nowMs) {
    this.nowMs = Objects.requireNonNull(nowMs, "nowMs");
    this.maxEntries = maxEntries;
    this.identityObserved = Objects.requireNonNull(identityObserved, "identityObserved");
  }

  Map<Integer, String> displayNames() {
    return Collections.unmodifiableMap(displayNames);
  }

  Set<Integer> tokenIds() {
    return Collections.unmodifiableSet(tokens.keySet());
  }

  Set<Integer> stateIds() {
    return Collections.unmodifiableSet(states.keySet());
  }

  Collection<Map<String, Object>> states() {
    return Collections.unmodifiableCollection(states.values());
  }

  Map<String, Object> state(int networkId) {
    return states.get(networkId);
  }

  Integer idForToken(String token) {
    return idsByToken.get(token);
  }

  String token(int networkId) {
    return Objects.toString(tokens.get(networkId), "").trim();
  }

  void forget(int networkId) {
    if (networkId < 0) return;
    removedIds.add(networkId);
    states.remove(networkId);
    displayNames.remove(networkId);
    tokens.remove(networkId);
    for (Map.Entry<String, Integer> entry : new ArrayList<>(idsByToken.entrySet())) {
      if (entry == null) continue;
      Integer mapped = entry.getValue();
      if (mapped == null || mapped.intValue() != networkId) continue;
      idsByToken.remove(entry.getKey(), mapped);
    }
  }

  void clearMetadata() {
    displayNames.clear();
    tokens.clear();
    idsByToken.clear();
    states.clear();
    pendingCreatedNames.clear();
  }

  void reset() {
    clearMetadata();
    removedIds.clear();
  }

  int primaryNetworkId(
      QuasselCoreAuthHandshake.AuthResult auth,
      Collection<QuasselCoreDatastreamCodec.BufferInfoValue> buffers) {
    LinkedHashSet<Integer> knownIds = knownIds(auth, buffers);
    if (knownIds.isEmpty()) return -1;
    if (auth == null) {
      return knownIds.iterator().next();
    }
    int primary = auth.primaryNetworkId();
    if (primary >= 0 && knownIds.contains(primary)) return primary;
    if (auth.networkIds() != null) {
      for (Integer id : auth.networkIds()) {
        if (id != null && id.intValue() >= 0 && knownIds.contains(id.intValue())) {
          return id.intValue();
        }
      }
    }
    return knownIds.iterator().next();
  }

  void observe(int networkId, String networkName) {
    if (networkId < 0) return;
    removedIds.remove(networkId);
    String display = Objects.toString(networkName, "").trim();
    if (display.isEmpty()) {
      display = Objects.toString(displayNames.get(networkId), "").trim();
    }
    if (display.isEmpty()) {
      display = "network-" + networkId;
    }

    String token = sanitizeNetworkToken(display);
    if (token.isEmpty()) {
      token = "id-" + networkId;
    }
    String previousToken = tokens.get(networkId);
    if (previousToken != null && !previousToken.isBlank()) {
      idsByToken.remove(previousToken.toLowerCase(Locale.ROOT));
    }
    String uniqueToken = token;
    Integer assigned = idsByToken.get(uniqueToken.toLowerCase(Locale.ROOT));
    if (assigned != null && assigned.intValue() != networkId) {
      uniqueToken = token + "-" + networkId;
    }

    displayNames.put(networkId, display);
    tokens.put(networkId, uniqueToken);
    idsByToken.put(uniqueToken.toLowerCase(Locale.ROOT), networkId);
    trimMapToMaxSize(displayNames, maxEntries);
    trimMapToMaxSize(tokens, maxEntries);
    trimMapToMaxSize(idsByToken, maxEntries);
  }

  Map<String, Object> observeState(int networkId, Map<?, ?> stateMap) {
    if (networkId < 0 || stateMap == null || stateMap.isEmpty()) return null;
    Map<String, Object> normalized = normalizeObjectMap(stateMap);
    if (normalized.isEmpty()) return null;
    Map<String, Object> merged =
        states.merge(
            networkId,
            normalized,
            (existing, incoming) -> {
              LinkedHashMap<String, Object> joined = new LinkedHashMap<>();
              if (existing != null && !existing.isEmpty()) {
                joined.putAll(existing);
              }
              if (incoming != null && !incoming.isEmpty()) {
                joined.putAll(incoming);
              }
              return Collections.unmodifiableMap(joined);
            });
    int identityId = parseNetworkIdentityId(merged);
    if (identityId >= 0) {
      identityObserved.accept(identityId);
    }
    trimMapToMaxSize(states, maxEntries);
    return merged;
  }

  List<QuasselCoreNetworkSummary> snapshot(
      QuasselCoreAuthHandshake.AuthResult auth,
      Collection<QuasselCoreDatastreamCodec.BufferInfoValue> buffers) {
    // Keep snapshot reads side-effect-free: a collected ID may be removed while we build the
    // result.
    LinkedHashSet<Integer> knownIds = knownIds(auth, buffers);
    if (knownIds.isEmpty()) return List.of();

    ArrayList<Integer> orderedIds = new ArrayList<>(knownIds);
    Collections.sort(orderedIds);

    ArrayList<QuasselCoreNetworkSummary> out = new ArrayList<>(orderedIds.size());
    for (Integer id : orderedIds) {
      if (id == null || id.intValue() < 0) continue;
      int networkId = id.intValue();
      Map<String, Object> state = states.get(networkId);
      String networkName =
          firstNonBlank(
              mapValueIgnoreCase(state, "networkName"),
              mapValueIgnoreCase(state, "networkname"),
              mapValueIgnoreCase(state, "name"),
              displayNames.get(networkId));
      if (networkName.isBlank()) networkName = "network-" + networkId;

      NetworkServerEndpoint endpoint = parsePrimaryNetworkServer(state);
      Map<String, Object> rawState =
          state == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(state));
      out.add(
          new QuasselCoreNetworkSummary(
              networkId,
              networkName,
              parseNetworkConnected(state),
              parseNetworkEnabled(state),
              parseNetworkIdentityId(state),
              endpoint.host(),
              endpoint.port(),
              endpoint.useTls(),
              rawState));
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  LinkedHashSet<Integer> knownIds(
      QuasselCoreAuthHandshake.AuthResult auth,
      Collection<QuasselCoreDatastreamCodec.BufferInfoValue> buffers) {
    LinkedHashSet<Integer> ids = new LinkedHashSet<>();
    if (auth != null && auth.networkIds() != null) {
      for (Integer id : auth.networkIds()) {
        if (id != null && id.intValue() >= 0) ids.add(id.intValue());
      }
    }
    for (QuasselCoreDatastreamCodec.BufferInfoValue info : buffers) {
      if (info != null && info.networkId() >= 0) ids.add(info.networkId());
    }
    ids.addAll(displayNames.keySet());
    ids.addAll(tokens.keySet());
    ids.addAll(states.keySet());
    ids.removeIf(id -> id == null || id.intValue() < 0 || removedIds.contains(id.intValue()));
    return ids;
  }

  int resolve(String networkIdOrName) {
    String raw = Objects.toString(networkIdOrName, "").trim();
    if (raw.isEmpty()) {
      throw new IllegalArgumentException("network id/name is required");
    }

    int parsedNumeric = parseNetworkId(raw);
    if (parsedNumeric >= 0) return parsedNumeric;

    String lowered = raw.toLowerCase(Locale.ROOT);
    Integer byToken = idsByToken.get(lowered);
    if (byToken != null && byToken.intValue() >= 0) return byToken.intValue();

    String sanitized = sanitizeNetworkToken(raw).toLowerCase(Locale.ROOT);
    if (!sanitized.isBlank()) {
      Integer bySanitized = idsByToken.get(sanitized);
      if (bySanitized != null && bySanitized.intValue() >= 0) return bySanitized.intValue();
    }

    for (Map.Entry<Integer, String> entry : displayNames.entrySet()) {
      if (entry == null || entry.getKey() == null) continue;
      String display = Objects.toString(entry.getValue(), "").trim();
      if (display.equalsIgnoreCase(raw)) return entry.getKey().intValue();
    }
    for (Map.Entry<Integer, Map<String, Object>> entry : states.entrySet()) {
      if (entry == null || entry.getKey() == null) continue;
      Map<String, Object> state = entry.getValue();
      String display =
          firstNonBlank(
              mapValueIgnoreCase(state, "networkName"),
              mapValueIgnoreCase(state, "networkname"),
              mapValueIgnoreCase(state, "name"));
      if (display.equalsIgnoreCase(raw)) return entry.getKey().intValue();
    }

    return -1;
  }

  private static String sanitizeNetworkToken(String raw) {
    String in = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (in.isEmpty()) return "";
    StringBuilder out = new StringBuilder(in.length());
    boolean pendingDash = false;
    for (int i = 0; i < in.length(); i++) {
      char c = in.charAt(i);
      boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
      if (allowed) {
        if (pendingDash && out.length() > 0) {
          out.append('-');
        }
        out.append(c);
        pendingDash = false;
      } else {
        pendingDash = out.length() > 0;
      }
    }
    return out.toString();
  }

  boolean isObservedName(String expectedNetworkName) {
    String wanted = Objects.toString(expectedNetworkName, "").trim();
    if (wanted.isEmpty()) return false;

    for (String display : displayNames.values()) {
      if (Objects.toString(display, "").trim().equalsIgnoreCase(wanted)) {
        return true;
      }
    }

    for (Map<String, Object> state : states.values()) {
      String name =
          firstNonBlank(
              mapValueIgnoreCase(state, "networkName"),
              mapValueIgnoreCase(state, "networkname"),
              mapValueIgnoreCase(state, "name"));
      if (name.equalsIgnoreCase(wanted)) {
        return true;
      }
    }
    return false;
  }

  int firstKnownNetworkId(
      QuasselCoreAuthHandshake.AuthResult auth,
      Collection<QuasselCoreDatastreamCodec.BufferInfoValue> buffers) {
    LinkedHashSet<Integer> knownIds = knownIds(auth, buffers);
    if (knownIds.isEmpty()) return -1;
    if (auth != null
        && auth.primaryNetworkId() >= 0
        && knownIds.contains(auth.primaryNetworkId())) {
      return auth.primaryNetworkId();
    }
    for (Integer id : knownIds) {
      if (id != null && id.intValue() >= 0) return id.intValue();
    }
    return -1;
  }

  void rememberCreatedName(String networkName) {
    String name = Objects.toString(networkName, "").trim();
    if (name.isEmpty()) return;
    long observedAtMs = nowMs.getAsLong();
    prunePendingCreatedNames(observedAtMs);
    pendingCreatedNames.addLast(new PendingCreatedNetworkName(name, observedAtMs));
    while (pendingCreatedNames.size() > MAX_PENDING_NETWORK_CREATE_NAMES) {
      pendingCreatedNames.pollFirst();
    }
  }

  String claimCreatedName() {
    long observedAtMs = nowMs.getAsLong();
    prunePendingCreatedNames(observedAtMs);
    PendingCreatedNetworkName pending = pendingCreatedNames.pollFirst();
    if (pending == null) return "";
    String name = Objects.toString(pending.networkName(), "").trim();
    return name;
  }

  private void prunePendingCreatedNames(long nowMs) {
    long cutoffMs = nowMs - PENDING_NETWORK_CREATE_NAME_TTL_MS;
    for (; ; ) {
      PendingCreatedNetworkName first = pendingCreatedNames.peekFirst();
      if (first == null) break;
      if (first.observedAtMs() >= cutoffMs) break;
      pendingCreatedNames.pollFirst();
    }
  }

  private record PendingCreatedNetworkName(String networkName, long observedAtMs) {}
}

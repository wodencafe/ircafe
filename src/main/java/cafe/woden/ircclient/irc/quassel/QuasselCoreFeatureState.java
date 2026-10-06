package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreFeatureStateParser.canonicalCapabilityToken;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.containsAnyMapKeysIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.trimMapToMaxSize;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3MultilineSupport;
import cafe.woden.ircclient.irc.quassel.QuasselCoreFeatureStateParser.MonitorSupportState;
import cafe.woden.ircclient.irc.quassel.QuasselCoreFeatureStateParser.MultilineLimitState;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Session-owned IRCv3 feature state and notifications from Core snapshots and IRC CAP lines. */
final class QuasselCoreFeatureState {
  private final int maxNetworks;
  private final Consumer<IrcEvent> emit;
  private final Map<Integer, Set<String>> capabilities = new ConcurrentHashMap<>();
  private final Map<Integer, MonitorSupportState> monitors = new ConcurrentHashMap<>();
  private final Map<Integer, MultilineLimitState> multilineLimits = new ConcurrentHashMap<>();
  private final AtomicBoolean capabilitiesObserved = new AtomicBoolean(false);

  QuasselCoreFeatureState(int maxNetworks, Consumer<IrcEvent> emit) {
    this.maxNetworks = maxNetworks;
    this.emit = Objects.requireNonNull(emit, "emit");
  }

  boolean hasObservedCapabilities() {
    return capabilitiesObserved.get();
  }

  boolean hasMonitorState() {
    return !monitors.isEmpty();
  }

  boolean hasMultilineLimits() {
    return !multilineLimits.isEmpty();
  }

  boolean hasAnyCapability(String... requested) {
    if (!capabilitiesObserved.get() || requested == null || requested.length == 0) return false;
    if (capabilities.isEmpty()) return false;
    HashSet<String> wanted = new HashSet<>();
    for (String cap : requested) {
      String token = canonicalCapabilityToken(cap);
      if (!token.isEmpty()) wanted.add(token);
    }
    if (wanted.isEmpty()) return false;
    for (Set<String> enabled : capabilities.values()) {
      for (String cap : wanted) {
        if (enabled.contains(cap)) return true;
      }
    }
    return false;
  }

  boolean monitorAvailable(int preferredNetworkId) {
    MonitorSupportState monitor = preferredState(monitors, preferredNetworkId);
    return monitor != null && monitor.available();
  }

  int monitorLimit(int preferredNetworkId) {
    MonitorSupportState monitor = preferredState(monitors, preferredNetworkId);
    return monitor == null || !monitor.available() ? 0 : boundedInt(monitor.limit());
  }

  long multilineMaxBytes(int preferredNetworkId) {
    MultilineLimitState limits = preferredState(multilineLimits, preferredNetworkId);
    return limits == null ? 0L : Math.max(0L, limits.maxBytes());
  }

  int multilineMaxLines(int preferredNetworkId) {
    MultilineLimitState limits = preferredState(multilineLimits, preferredNetworkId);
    return limits == null ? 0 : boundedInt(limits.maxLines());
  }

  void observeCapabilities(int networkId, Map<?, ?> stateMap) {
    if (networkId < 0 || stateMap == null || stateMap.isEmpty()) return;
    if (!containsAnyMapKeysIgnoreCase(
        stateMap,
        "capsEnabled",
        "capsenabled",
        "enabledCaps",
        "enabledcaps",
        "caps",
        "capabilities",
        "availableCaps")) return;

    Set<String> enabled =
        QuasselCoreFeatureStateParser.extractCapabilityTokens(
            stateMap, "capsEnabled", "capsenabled", "enabledCaps", "enabledcaps");
    if (enabled.isEmpty()) {
      enabled =
          QuasselCoreFeatureStateParser.extractCapabilityTokens(
              stateMap, "caps", "capabilities", "availableCaps");
    }
    capabilitiesObserved.set(true);
    Set<String> previous = capabilities.get(networkId);
    capabilities.put(networkId, Set.copyOf(enabled));
    trimMapToMaxSize(capabilities, maxNetworks);
    observeMultilineLimits(networkId, stateMap, enabled);
    emitCapabilityDelta(previous, enabled);
  }

  void observeMonitor(int networkId, Map<?, ?> stateMap) {
    if (networkId < 0 || stateMap == null || stateMap.isEmpty()) return;
    MonitorSupportState parsed =
        QuasselCoreFeatureStateParser.extractMonitorSupportFromStateMap(stateMap);
    if (parsed == null) return;
    monitors.put(networkId, parsed);
    trimMapToMaxSize(monitors, maxNetworks);
  }

  void observeMonitor(int networkId, boolean supported, long limit) {
    if (networkId < 0) return;
    monitors.put(networkId, new MonitorSupportState(supported, Math.max(0L, limit)));
    trimMapToMaxSize(monitors, maxNetworks);
  }

  void observeCapLine(Instant at, int networkId, QuasselCoreIrcEnvelope envelope) {
    if (envelope == null || !envelope.parsed()) return;
    String subcommand = envelope.capSubcommand();
    if (subcommand.isEmpty()) return;
    String caps = envelope.capList();
    if (caps.isBlank()) return;

    boolean emitted = false;
    for (String rawToken : caps.split("\\s+")) {
      String token = Objects.toString(rawToken, "").trim();
      if (token.isEmpty()) continue;
      boolean disabledToken = token.startsWith("-");
      String capName = canonicalCapabilityToken(token);
      if (capName.isBlank()) continue;
      boolean enabled = "ACK".equals(subcommand) && !disabledToken;

      // CAP observers see each announcement before its state delta, as in the transport bridge.
      emit.accept(new IrcEvent.Ircv3CapabilityChanged(at, subcommand, capName, enabled));
      emitted = true;
      if ("ACK".equals(subcommand) || "DEL".equals(subcommand) || "NAK".equals(subcommand)) {
        applyCapabilityDelta(networkId, capName, enabled);
      }
      if (Ircv3MultilineSupport.isMultilineCapability(capName) && networkId >= 0) {
        if ("DEL".equals(subcommand) || disabledToken) {
          multilineLimits.remove(networkId);
        } else if ("ACK".equals(subcommand)) {
          MultilineLimitState parsed =
              QuasselCoreFeatureStateParser.multilineLimitsFromToken(token);
          if (parsed != null) {
            multilineLimits.put(networkId, parsed);
          } else {
            multilineLimits.putIfAbsent(networkId, new MultilineLimitState(0L, 0L));
          }
          trimMapToMaxSize(multilineLimits, maxNetworks);
        }
      }
    }
    if (emitted) {
      emit.accept(
          new IrcEvent.ConnectionFeaturesUpdated(at, "cap-" + subcommand.toLowerCase(Locale.ROOT)));
    }
  }

  void removeNetwork(int networkId) {
    capabilities.remove(networkId);
    monitors.remove(networkId);
    multilineLimits.remove(networkId);
  }

  void clear() {
    capabilities.clear();
    monitors.clear();
    multilineLimits.clear();
    capabilitiesObserved.set(false);
  }

  private void observeMultilineLimits(int networkId, Map<?, ?> stateMap, Set<String> enabled) {
    if (!enabled.contains(Ircv3MultilineSupport.MULTILINE_CAPABILITY)
        && !enabled.contains(Ircv3MultilineSupport.DRAFT_MULTILINE_CAPABILITY)) {
      multilineLimits.remove(networkId);
      return;
    }
    MultilineLimitState existing = multilineLimits.get(networkId);
    MultilineLimitState parsed =
        QuasselCoreFeatureStateParser.extractMultilineLimitsFromStateMap(stateMap);
    if (parsed != null) {
      multilineLimits.put(networkId, parsed);
    } else if (existing == null) {
      multilineLimits.put(networkId, new MultilineLimitState(0L, 0L));
    }
    trimMapToMaxSize(multilineLimits, maxNetworks);
  }

  private void emitCapabilityDelta(Set<String> previous, Set<String> current) {
    Set<String> prev = previous == null ? Set.of() : previous;
    Instant now = Instant.now();
    boolean emitted = false;
    for (String cap : current) {
      if (cap == null || cap.isBlank() || prev.contains(cap)) continue;
      emit.accept(new IrcEvent.Ircv3CapabilityChanged(now, "SYNC", cap, true));
      emitted = true;
    }
    for (String cap : prev) {
      if (cap == null || cap.isBlank() || current.contains(cap)) continue;
      emit.accept(new IrcEvent.Ircv3CapabilityChanged(now, "SYNC", cap, false));
      emitted = true;
    }
    if (emitted) emit.accept(new IrcEvent.ConnectionFeaturesUpdated(now, "cap-sync"));
  }

  private void applyCapabilityDelta(int networkId, String capability, boolean enabled) {
    if (networkId < 0) return;
    String cap = canonicalCapabilityToken(capability);
    if (cap.isBlank()) return;
    Set<String> previous = capabilities.get(networkId);
    LinkedHashSet<String> next = new LinkedHashSet<>(previous == null ? Set.of() : previous);
    if (enabled) {
      next.add(cap);
    } else {
      next.remove(cap);
    }
    capabilitiesObserved.set(true);
    capabilities.put(networkId, Set.copyOf(next));
    trimMapToMaxSize(capabilities, maxNetworks);
  }

  private static <T> T preferredState(Map<Integer, T> states, int preferredNetworkId) {
    if (preferredNetworkId >= 0) {
      T preferred = states.get(preferredNetworkId);
      if (preferred != null) return preferred;
    }
    for (T state : states.values()) {
      if (state != null) return state;
    }
    return null;
  }

  private static int boundedInt(long value) {
    return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
  }
}

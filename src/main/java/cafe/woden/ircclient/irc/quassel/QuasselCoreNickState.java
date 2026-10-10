package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.trimMapToMaxSize;

import cafe.woden.ircclient.irc.IrcEvent;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/** Owns bounded network nick observations and publishes changes to the primary nick. */
final class QuasselCoreNickState {
  private static final int MAX_NETWORK_NICKS = 256;
  private final String initialNick;
  private final AtomicReference<String> lastObservedNick;
  private final Map<Integer, String> networkNicks = new ConcurrentHashMap<>();
  private final IntSupplier primaryNetwork;
  private final Consumer<IrcEvent> eventObserved;

  QuasselCoreNickState(
      String initialNick, IntSupplier primaryNetwork, Consumer<IrcEvent> eventObserved) {
    this.initialNick = initialNick;
    this.lastObservedNick = new AtomicReference<>(initialNick);
    this.primaryNetwork = Objects.requireNonNull(primaryNetwork, "primaryNetwork");
    this.eventObserved = Objects.requireNonNull(eventObserved, "eventObserved");
  }

  String lastObservedNick() {
    return Objects.toString(lastObservedNick.get(), "");
  }

  String current() {
    int primary = primaryNetwork.getAsInt();
    String observed = primary >= 0 ? normalizedNetworkNick(primary) : "";
    return observed.isEmpty() ? lastObservedNick().trim() : observed;
  }

  String forNetwork(int networkId) {
    String observed = networkId >= 0 ? normalizedNetworkNick(networkId) : "";
    return observed.isEmpty() ? current() : observed;
  }

  boolean isSelf(String nick, int networkId) {
    String candidate = Objects.toString(nick, "").trim();
    if (candidate.isEmpty()) return false;
    if (networkId >= 0 && candidate.equalsIgnoreCase(normalizedNetworkNick(networkId))) {
      return true;
    }
    String known = current();
    return !known.isEmpty() && known.equalsIgnoreCase(candidate);
  }

  void observe(int networkId, String nick, Instant at) {
    String next = Objects.toString(nick, "").trim();
    if (next.isEmpty()) return;
    if (networkId >= 0) {
      networkNicks.put(networkId, next);
      trimMapToMaxSize(networkNicks, MAX_NETWORK_NICKS);
    }
    int primary = primaryNetwork.getAsInt();
    if (networkId >= 0 && primary >= 0 && networkId != primary) return;
    String oldNick = Objects.toString(lastObservedNick.getAndSet(next), "").trim();
    if (!oldNick.isEmpty() && !oldNick.equalsIgnoreCase(next)) {
      eventObserved.accept(new IrcEvent.NickChanged(at, oldNick, next));
    }
  }

  void seedPrimaryNetwork(int networkId) {
    if (networkId >= 0) {
      networkNicks.put(networkId, initialNick);
      trimMapToMaxSize(networkNicks, MAX_NETWORK_NICKS);
    }
  }

  void forgetNetwork(int networkId) {
    networkNicks.remove(networkId);
  }

  void clear() {
    networkNicks.clear();
  }

  private String normalizedNetworkNick(int networkId) {
    return Objects.toString(networkNicks.get(networkId), "").trim();
  }
}

package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.normalizeTargetHintKey;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.trimMapToMaxSize;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/** Session-owned, bounded routing hints shared by qualified and unqualified target names. */
final class QuasselCoreTargetNetworkHints {
  private static final int MAX_TARGETS = 4_096;

  private final int maxSize;
  private final Map<String, Integer> networkByTarget = new ConcurrentHashMap<>();

  QuasselCoreTargetNetworkHints() {
    this(MAX_TARGETS);
  }

  QuasselCoreTargetNetworkHints(int maxSize) {
    if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
    this.maxSize = maxSize;
  }

  void observe(String target, int networkId) {
    if (networkId < 0) return;
    String key = normalizeTargetHintKey(target);
    if (key.isEmpty()) return;
    networkByTarget.put(key, networkId);
    trimMapToMaxSize(networkByTarget, maxSize);
  }

  void seed(String target, int observedNetworkId, IntSupplier defaultNetworkId) {
    if (observedNetworkId < 0) return;
    String key = normalizeTargetHintKey(target);
    if (key.isEmpty()) return;
    // Resolving the default scans session networks; blank targets should remain a cheap no-op.
    int preferredDefault = defaultNetworkId.getAsInt();
    int hint = preferredDefault >= 0 ? preferredDefault : observedNetworkId;
    networkByTarget.putIfAbsent(key, hint);
    trimMapToMaxSize(networkByTarget, maxSize);
  }

  int networkIdForTarget(String target) {
    String key = normalizeTargetHintKey(target);
    if (key.isEmpty()) return -1;
    return networkByTarget.getOrDefault(key, -1);
  }

  void forgetNetwork(int networkId) {
    for (Map.Entry<String, Integer> entry : new ArrayList<>(networkByTarget.entrySet())) {
      Integer mapped = entry.getValue();
      if (mapped.intValue() != networkId) continue;
      // Preserve a newer observation if routing changed after the snapshot was collected.
      networkByTarget.remove(entry.getKey(), mapped);
    }
  }

  void clear() {
    networkByTarget.clear();
  }
}

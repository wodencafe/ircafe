package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.trimMapToMaxSize;

import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

/** Session-owned, bounded Core buffer metadata and network-aware name lookup. */
final class QuasselCoreBufferCatalog {
  private static final int MAX_BUFFERS = 8_192;

  private final int maxSize;
  private final Map<Integer, BufferInfoValue> buffersById = new ConcurrentHashMap<>();
  private final Collection<BufferInfoValue> values =
      Collections.unmodifiableCollection(buffersById.values());

  QuasselCoreBufferCatalog() {
    this(MAX_BUFFERS);
  }

  QuasselCoreBufferCatalog(int maxSize) {
    if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
    this.maxSize = maxSize;
  }

  void loadInitial(Map<Integer, BufferInfoValue> initial) {
    buffersById.putAll(initial);
    trimMapToMaxSize(buffersById, maxSize);
  }

  BufferInfoValue get(int bufferId) {
    return buffersById.get(bufferId);
  }

  // A read-only live view retains the concurrent iteration behavior used by network snapshots.
  Collection<BufferInfoValue> values() {
    return values;
  }

  BufferInfoValue merge(BufferInfoValue incoming) {
    if (incoming == null || incoming.bufferId() < 0) return incoming;
    BufferInfoValue merged =
        buffersById.merge(incoming.bufferId(), incoming, QuasselCoreBufferCatalog::mergeMetadata);
    trimMapToMaxSize(buffersById, maxSize);
    return merged;
  }

  BufferInfoValue resolve(BufferInfoValue incoming) {
    if (incoming == null || incoming.bufferId() < 0) return incoming;
    // Display messages retain their existing read/merge/write semantics.
    BufferInfoValue merged = mergeMetadata(buffersById.get(incoming.bufferId()), incoming);
    buffersById.put(merged.bufferId(), merged);
    trimMapToMaxSize(buffersById, maxSize);
    return merged;
  }

  void remove(int bufferId) {
    buffersById.remove(bufferId);
  }

  void forgetNetwork(int networkId, IntConsumer onMatchedBuffer) {
    for (Map.Entry<Integer, BufferInfoValue> entry : new ArrayList<>(buffersById.entrySet())) {
      BufferInfoValue info = entry.getValue();
      if (info.networkId() != networkId) continue;
      buffersById.remove(entry.getKey(), info);
      // Cleanup still runs for snapshot matches whose metadata changed during removal.
      onMatchedBuffer.accept(entry.getKey());
    }
  }

  void clear() {
    buffersById.clear();
  }

  private static BufferInfoValue mergeMetadata(BufferInfoValue existing, BufferInfoValue update) {
    if (existing == null) return update;
    if (update == null) return existing;

    int bufferId = update.bufferId() >= 0 ? update.bufferId() : existing.bufferId();
    int networkId = preferKnownInt(update.networkId(), existing.networkId());
    int typeBits = update.typeBits() != 0 ? update.typeBits() : existing.typeBits();
    int groupId = preferKnownInt(update.groupId(), existing.groupId());
    String bufferName = preferNonBlank(update.bufferName(), existing.bufferName());
    return new BufferInfoValue(bufferId, networkId, typeBits, groupId, bufferName);
  }

  private static int preferKnownInt(int preferred, int fallback) {
    if (preferred != 0 && preferred != -1) return preferred;
    return fallback;
  }

  private static String preferNonBlank(String preferred, String fallback) {
    String p = Objects.toString(preferred, "").trim();
    if (!p.isEmpty()) return p;
    return Objects.toString(fallback, "").trim();
  }

  BufferInfoValue findByName(String bufferName, int typeBitsHint, int preferredNetworkId) {
    String requested = Objects.toString(bufferName, "").trim();
    if (requested.isEmpty()) return null;

    BufferInfoValue preferredAnyType = null;
    BufferInfoValue fallback = null;
    BufferInfoValue fallbackAnyType = null;
    for (BufferInfoValue candidate : buffersById.values()) {
      if (candidate == null) continue;
      if (!requested.equalsIgnoreCase(Objects.toString(candidate.bufferName(), "").trim()))
        continue;
      boolean typeMatch = (candidate.typeBits() & typeBitsHint) != 0;
      boolean preferredNetwork =
          preferredNetworkId >= 0 && candidate.networkId() == preferredNetworkId;
      if (preferredNetwork && typeMatch) {
        return candidate;
      }
      if (preferredNetwork && preferredAnyType == null) {
        preferredAnyType = candidate;
      }
      if (typeMatch && fallback == null) {
        fallback = candidate;
      }
      if (fallbackAnyType == null) {
        fallbackAnyType = candidate;
      }
    }
    if (preferredAnyType != null) return preferredAnyType;
    if (fallback != null) return fallback;
    return fallbackAnyType;
  }
}

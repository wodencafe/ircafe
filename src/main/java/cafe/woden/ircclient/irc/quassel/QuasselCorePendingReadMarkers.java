package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.trimMapToMaxSize;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Session-owned, bounded lookup for native markers awaiting an exact history timestamp. */
final class QuasselCorePendingReadMarkers {
  private static final int MAX_PENDING_MARKERS = 8_192;

  private final int maxSize;
  private final Map<Long, Integer> bufferByMessageId = new ConcurrentHashMap<>();

  QuasselCorePendingReadMarkers() {
    this(MAX_PENDING_MARKERS);
  }

  QuasselCorePendingReadMarkers(int maxSize) {
    if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
    this.maxSize = maxSize;
  }

  void defer(int bufferId, long messageId) {
    if (bufferId < 0 || messageId <= 0L) return;
    bufferByMessageId.put(messageId, bufferId);
    trimMapToMaxSize(bufferByMessageId, maxSize);
  }

  Integer takeBufferForMessage(long messageId) {
    return bufferByMessageId.remove(messageId);
  }

  void forgetBuffer(int bufferId) {
    bufferByMessageId.values().removeIf(id -> id == bufferId);
  }

  void clear() {
    bufferByMessageId.clear();
  }
}

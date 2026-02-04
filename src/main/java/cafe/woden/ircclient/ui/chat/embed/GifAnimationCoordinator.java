package cafe.woden.ircclient.ui.chat.embed;

import java.lang.ref.WeakReference;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Per-transcript coordinator to ensure only the most-recent GIF animates (based on transcript insertion order).
 *
 * <p>All calls are expected to happen on the Swing EDT.
 */
final class GifAnimationCoordinator {

  private final NavigableMap<Long, WeakReference<ChatImageComponent>> gifsBySeq = new TreeMap<>();

  private final TreeSet<Long> hintedGifSeqs = new TreeSet<>();

  private long activeSeq = -1;

  void hintNewGifPlaceholder(long seq) {
    hintedGifSeqs.add(seq);
    recomputeActive();
  }

  void rejectGifHint(long seq) {
    if (hintedGifSeqs.remove(seq)) {
      recomputeActive();
    }
  }

  void registerAnimatedGif(long seq, ChatImageComponent comp) {
    if (comp == null) return;
    gifsBySeq.put(seq, new WeakReference<>(comp));
    recomputeActive();
  }

  private void recomputeActive() {
    // Prune dead refs.
    gifsBySeq.entrySet().removeIf(e -> e.getValue() == null || e.getValue().get() == null);

    Long minSeq = hintedGifSeqs.isEmpty() ? null : hintedGifSeqs.last();

    Long newActiveSeq = null;
    if (!gifsBySeq.isEmpty()) {
      if (minSeq == null) {
        newActiveSeq = gifsBySeq.lastKey();
      } else {
        var tail = gifsBySeq.tailMap(minSeq, true);
        if (!tail.isEmpty()) {
          newActiveSeq = tail.lastKey();
        } else {
          newActiveSeq = null; // newer GIF hinted but not yet decoded; animate none.
        }
      }
    }

    if (newActiveSeq == null) {
      // No active GIF should animate.
      if (activeSeq >= 0) {
        WeakReference<ChatImageComponent> ref = gifsBySeq.get(activeSeq);
        ChatImageComponent old = ref != null ? ref.get() : null;
        if (old != null) old.setGifAnimationAllowed(false);
      }
      activeSeq = -1;
      return;
    }

    long next = newActiveSeq;
    if (next == activeSeq) {
      // Ensure it's allowed (in case it was stopped due to hint changes).
      WeakReference<ChatImageComponent> ref = gifsBySeq.get(activeSeq);
      ChatImageComponent cur = ref != null ? ref.get() : null;
      if (cur != null) cur.setGifAnimationAllowed(true);
      return;
    }

    // Disable the previous active, enable the new one.
    if (activeSeq >= 0) {
      WeakReference<ChatImageComponent> ref = gifsBySeq.get(activeSeq);
      ChatImageComponent old = ref != null ? ref.get() : null;
      if (old != null) old.setGifAnimationAllowed(false);
    }

    WeakReference<ChatImageComponent> ref = gifsBySeq.get(next);
    ChatImageComponent now = ref != null ? ref.get() : null;
    if (now != null) {
      now.setGifAnimationAllowed(true);
      activeSeq = next;
    } else {
      // Shouldn't happen after pruning, but be defensive.
      gifsBySeq.remove(next);
      activeSeq = -1;
      recomputeActive();
    }
  }
}

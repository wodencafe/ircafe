package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Owns follow-tail state and read-marker flow coordination for {@link ChatDockable}. */
final class ChatReadMarkerCoordinator {

  private static final long READ_MARKER_SEND_COOLDOWN_MS = 3000L;
  private static final int MAX_TRACKED_TARGET_STATES = 1024;

  private final ChatTranscriptStore transcripts;
  private final IrcClientService irc;
  private final Supplier<TargetRef> activeTargetSupplier;
  private final IntConsumer scrollToTranscriptOffset;
  private final Runnable updateScrollStateFromBar;
  private final BooleanSupplier transcriptAtBottomSupplier;
  private final LongSupplier currentTimeMillis;

  private final Map<TargetRef, ViewState> stateByTarget = newBoundedTargetMap();
  private final ViewState fallbackState = new ViewState();
  private final Map<TargetRef, Long> lastReadMarkerSentAtByTarget = newBoundedTargetMap();

  private static final class ViewState {
    private boolean followTail = true;
    private int scrollValue = 0;
  }

  ChatReadMarkerCoordinator(
      ChatTranscriptStore transcripts,
      IrcClientService irc,
      Supplier<TargetRef> activeTargetSupplier,
      IntConsumer scrollToTranscriptOffset,
      Runnable updateScrollStateFromBar,
      BooleanSupplier transcriptAtBottomSupplier) {
    this(
        transcripts,
        irc,
        activeTargetSupplier,
        scrollToTranscriptOffset,
        updateScrollStateFromBar,
        transcriptAtBottomSupplier,
        System::currentTimeMillis);
  }

  ChatReadMarkerCoordinator(
      ChatTranscriptStore transcripts,
      IrcClientService irc,
      Supplier<TargetRef> activeTargetSupplier,
      IntConsumer scrollToTranscriptOffset,
      Runnable updateScrollStateFromBar,
      BooleanSupplier transcriptAtBottomSupplier,
      LongSupplier currentTimeMillis) {
    this.transcripts = Objects.requireNonNull(transcripts, "transcripts");
    this.irc = irc;
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
    this.scrollToTranscriptOffset =
        Objects.requireNonNull(scrollToTranscriptOffset, "scrollToTranscriptOffset");
    this.updateScrollStateFromBar =
        Objects.requireNonNull(updateScrollStateFromBar, "updateScrollStateFromBar");
    this.transcriptAtBottomSupplier =
        Objects.requireNonNull(transcriptAtBottomSupplier, "transcriptAtBottomSupplier");
    this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
  }

  boolean isFollowTail() {
    return state().followTail;
  }

  void setFollowTail(boolean followTail) {
    ViewState state = state();
    boolean was = state.followTail;
    state.followTail = followTail;
    TargetRef target = activeTargetSupplier.get();
    if (!was && followTail && target != null && !target.isUiOnly()) {
      maybeSendReadMarker(target);
    }
  }

  int savedScrollValue() {
    return state().scrollValue;
  }

  void setSavedScrollValue(int value) {
    state().scrollValue = value;
  }

  void applyReadMarkerViewState(TargetRef target, int unreadJumpOffset) {
    if (target == null || target.isUiOnly()) return;
    if (!Objects.equals(activeTargetSupplier.get(), target)) return;

    if (unreadJumpOffset >= 0) {
      setFollowTail(false);
      scrollToTranscriptOffset.accept(unreadJumpOffset);
      updateScrollStateFromBar.run();
      return;
    }

    if (transcriptAtBottomSupplier.getAsBoolean()) {
      maybeSendReadMarker(target);
    }
  }

  void onTargetClosed(TargetRef target) {
    if (target == null) return;
    stateByTarget.remove(target);
    lastReadMarkerSentAtByTarget.remove(target);
  }

  void clearServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    stateByTarget.entrySet().removeIf(e -> Objects.equals(serverIdOf(e.getKey()), sid));
    lastReadMarkerSentAtByTarget
        .entrySet()
        .removeIf(e -> Objects.equals(serverIdOf(e.getKey()), sid));
  }

  void clearAll() {
    stateByTarget.clear();
    lastReadMarkerSentAtByTarget.clear();
  }

  private ViewState state() {
    TargetRef activeTarget = activeTargetSupplier.get();
    if (activeTarget == null) return fallbackState;
    return stateByTarget.computeIfAbsent(activeTarget, __ -> new ViewState());
  }

  private void maybeSendReadMarker(TargetRef target) {
    if (target == null || target.isStatus() || target.isUiOnly()) return;
    if (irc == null || !irc.isReadMarkerAvailable(target.serverId())) return;

    long now = currentTimeMillis.getAsLong();
    Long last = lastReadMarkerSentAtByTarget.get(target);
    if (last != null && (now - last) < READ_MARKER_SEND_COOLDOWN_MS) return;
    lastReadMarkerSentAtByTarget.put(target, now);

    transcripts.updateReadMarker(target, now);
    try {
      var send = irc.sendReadMarker(target.serverId(), target.target(), Instant.ofEpochMilli(now));
      if (send != null) {
        var unused = send.subscribe(() -> {}, err -> {});
      }
    } catch (Exception ignored) {
    }
  }

  private static String serverIdOf(TargetRef target) {
    if (target == null) return "";
    return Objects.toString(target.serverId(), "").trim();
  }

  private static <V> Map<TargetRef, V> newBoundedTargetMap() {
    return new LinkedHashMap<>(128, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<TargetRef, V> eldest) {
        return size() > MAX_TRACKED_TARGET_STATES;
      }
    };
  }
}

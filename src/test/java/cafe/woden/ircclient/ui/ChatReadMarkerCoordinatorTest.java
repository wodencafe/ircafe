package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ChatReadMarkerCoordinatorTest {

  @Test
  void setFollowTailTransitionSendsReadMarkerWhenAvailable() {
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef target = new TargetRef("libera", "#ircafe");
    AtomicLong now = new AtomicLong(1_000L);

    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);
    when(irc.sendReadMarker("libera", "#ircafe", Instant.ofEpochMilli(1_000L)))
        .thenReturn(Completable.complete());

    ChatReadMarkerCoordinator coordinator =
        new ChatReadMarkerCoordinator(
            transcripts, irc, () -> target, offset -> {}, () -> {}, () -> false, now::get);

    coordinator.setFollowTail(false);
    coordinator.setFollowTail(true);

    verify(transcripts).updateReadMarker(target, 1_000L);
    verify(irc).sendReadMarker("libera", "#ircafe", Instant.ofEpochMilli(1_000L));
  }

  @Test
  void setFollowTailUsesCooldownToSuppressRapidRepeatMarkers() {
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef target = new TargetRef("libera", "#ircafe");
    AtomicLong now = new AtomicLong(1_000L);

    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);
    when(irc.sendReadMarker(any(), any(), any())).thenReturn(Completable.complete());

    ChatReadMarkerCoordinator coordinator =
        new ChatReadMarkerCoordinator(
            transcripts, irc, () -> target, offset -> {}, () -> {}, () -> false, now::get);

    coordinator.setFollowTail(false);
    coordinator.setFollowTail(true);

    now.set(2_000L);
    coordinator.setFollowTail(false);
    coordinator.setFollowTail(true);

    verify(irc, times(1)).sendReadMarker(any(), any(), any());
    verify(transcripts, times(1)).updateReadMarker(target, 1_000L);
  }

  @Test
  void applyReadMarkerViewStateWithUnreadJumpDisablesFollowTailAndScrolls() {
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef target = new TargetRef("libera", "#ircafe");
    AtomicInteger scrolledOffset = new AtomicInteger(-1);
    AtomicInteger scrollStateUpdates = new AtomicInteger();

    ChatReadMarkerCoordinator coordinator =
        new ChatReadMarkerCoordinator(
            transcripts,
            irc,
            () -> target,
            scrolledOffset::set,
            scrollStateUpdates::incrementAndGet,
            () -> false,
            () -> 1_000L);

    coordinator.applyReadMarkerViewState(target, 42);

    assertFalse(coordinator.isFollowTail());
    assertEquals(42, scrolledOffset.get());
    assertEquals(1, scrollStateUpdates.get());
    verifyNoInteractions(transcripts, irc);
  }

  @Test
  void applyReadMarkerViewStateSendsMarkerWhenAtBottomAndNoUnreadJump() {
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef target = new TargetRef("libera", "#ircafe");
    AtomicLong now = new AtomicLong(1_000L);

    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);
    when(irc.sendReadMarker("libera", "#ircafe", Instant.ofEpochMilli(1_000L)))
        .thenReturn(Completable.complete());

    ChatReadMarkerCoordinator coordinator =
        new ChatReadMarkerCoordinator(
            transcripts, irc, () -> target, offset -> {}, () -> {}, () -> true, now::get);

    coordinator.applyReadMarkerViewState(target, -1);

    verify(transcripts).updateReadMarker(target, 1_000L);
    verify(irc).sendReadMarker("libera", "#ircafe", Instant.ofEpochMilli(1_000L));
  }

  @Test
  void applyReadMarkerViewStateIgnoresTargetsThatAreNotActive() {
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    IrcClientService irc = mock(IrcClientService.class);
    TargetRef active = new TargetRef("libera", "#ircafe");
    TargetRef other = new TargetRef("libera", "#other");

    ChatReadMarkerCoordinator coordinator =
        new ChatReadMarkerCoordinator(
            transcripts, irc, () -> active, offset -> {}, () -> {}, () -> true, () -> 1_000L);

    coordinator.applyReadMarkerViewState(other, -1);

    verifyNoInteractions(transcripts, irc);
  }
}

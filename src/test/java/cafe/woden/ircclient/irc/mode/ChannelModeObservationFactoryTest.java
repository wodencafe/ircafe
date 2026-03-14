package cafe.woden.ircclient.irc.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.irc.IrcEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChannelModeObservationFactoryTest {

  @Test
  void numeric324CreatesSnapshotObservationWithNumericProvenance() {
    IrcEvent.ChannelModeObserved ev =
        ChannelModeObservationFactory.fromNumeric324(
            Instant.parse("2026-03-03T12:00:00Z"), "#ircafe", "+Cgn");

    assertEquals(IrcEvent.ChannelModeKind.SNAPSHOT, ev.kind());
    assertEquals(IrcEvent.ChannelModeProvenance.NUMERIC_324, ev.provenance());
    assertEquals("", ev.by());
    assertEquals("+Cgn", ev.details());
  }

  @Test
  void liveModeClassifiesBlankActorSnapshotLikeDetails() {
    IrcEvent.ChannelModeObserved ev =
        ChannelModeObservationFactory.fromLiveMode(
            Instant.parse("2026-03-03T12:00:00Z"), "#ircafe", "", "+nrf [10j#R10]:5");

    assertEquals(IrcEvent.ChannelModeKind.SNAPSHOT, ev.kind());
    assertEquals(IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT, ev.provenance());
  }

  @Test
  void liveModeClassifiesStatusDeltaDetailsAsDelta() {
    IrcEvent.ChannelModeObserved ev =
        ChannelModeObservationFactory.fromLiveMode(
            Instant.parse("2026-03-03T12:00:00Z"), "#ircafe", "FurBot", "+o Arca");

    assertEquals(IrcEvent.ChannelModeKind.DELTA, ev.kind());
    assertEquals(IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT, ev.provenance());
    assertEquals("FurBot", ev.by());
    assertEquals("+o Arca", ev.details());
  }

  @Test
  void numeric324FallbackCreatesSnapshotObservationWithFallbackProvenance() {
    IrcEvent.ChannelModeObserved ev =
        ChannelModeObservationFactory.fromNumeric324Fallback(
            Instant.parse("2026-03-03T12:00:00Z"), "#ircafe", "+nrt");

    assertEquals(IrcEvent.ChannelModeKind.SNAPSHOT, ev.kind());
    assertEquals(IrcEvent.ChannelModeProvenance.NUMERIC_324_FALLBACK, ev.provenance());
  }
}

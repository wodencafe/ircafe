package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PircbotxLagProbeStateTest {

  @Test
  void matchingPongRecordsLagAndClearsProbe() {
    PircbotxLagProbeState state = new PircbotxLagProbeState();

    state.beginProbe("ircafe-lag-1", 1_000L);

    assertTrue(state.observePong("ircafe-lag-1", 1_650L));
    assertEquals("", state.currentProbeToken());
    assertEquals(650L, state.currentMeasuredLagMs());
    assertEquals(1_650L, state.currentMeasuredAtMs());
  }

  @Test
  void nonMatchingPongIsIgnored() {
    PircbotxLagProbeState state = new PircbotxLagProbeState();

    state.beginProbe("ircafe-lag-1", 1_000L);

    assertFalse(state.observePong("wrong-token", 1_650L));
    assertEquals("ircafe-lag-1", state.currentProbeToken());
    assertEquals(-1L, state.currentMeasuredLagMs());
  }

  @Test
  void passiveSampleIsBoundedAndCanExpire() {
    PircbotxLagProbeState state = new PircbotxLagProbeState();

    state.observePassiveSample(2_000L, 10_000L);

    assertEquals(2_000L, state.lagMsIfFresh(10_500L));
    assertEquals(-1L, state.lagMsIfFresh(200_001L));
  }

  @Test
  void resetClearsProbeAndMeasurements() {
    PircbotxLagProbeState state = new PircbotxLagProbeState();

    state.beginProbe("ircafe-lag-1", 1_000L);
    state.observePassiveSample(2_000L, 10_000L);
    state.reset();

    assertEquals("", state.currentProbeToken());
    assertEquals(0L, state.currentProbeSentAtMs());
    assertEquals(-1L, state.currentMeasuredLagMs());
    assertEquals(0L, state.currentMeasuredAtMs());
  }
}

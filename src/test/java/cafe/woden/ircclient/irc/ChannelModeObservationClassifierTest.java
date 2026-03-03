package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChannelModeObservationClassifierTest {

  @Test
  void classifiesBlankActorSnapshotLikeModesAsSnapshot() {
    assertEquals(
        IrcEvent.ChannelModeKind.SNAPSHOT,
        ChannelModeObservationClassifier.classifyLiveModeKind("", "+nrf [10j#R10]:5"));
    assertEquals(
        IrcEvent.ChannelModeKind.SNAPSHOT,
        ChannelModeObservationClassifier.classifyLiveModeKind(null, "+nt"));
  }

  @Test
  void classifiesStatusModesAsDeltaEvenWithoutActor() {
    assertEquals(
        IrcEvent.ChannelModeKind.DELTA,
        ChannelModeObservationClassifier.classifyLiveModeKind("", "+o Arca"));
    assertEquals(
        IrcEvent.ChannelModeKind.DELTA,
        ChannelModeObservationClassifier.classifyLiveModeKind("", "+b bad!*@*"));
  }

  @Test
  void classifiesActorBackedModesAsDelta() {
    assertEquals(
        IrcEvent.ChannelModeKind.DELTA,
        ChannelModeObservationClassifier.classifyLiveModeKind("ChanServ", "+nrf [10j#R10]:5"));
  }

  @Test
  void snapshotDetectorRejectsNegativeOrMalformedModes() {
    assertFalse(ChannelModeObservationClassifier.looksLikeSnapshotModeDetails("-n"));
    assertFalse(ChannelModeObservationClassifier.looksLikeSnapshotModeDetails("+o Arca"));
    assertFalse(ChannelModeObservationClassifier.looksLikeSnapshotModeDetails("mode +n"));
    assertTrue(ChannelModeObservationClassifier.looksLikeSnapshotModeDetails("+nrf"));
  }
}

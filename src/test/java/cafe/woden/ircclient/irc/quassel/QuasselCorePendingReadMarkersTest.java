package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCorePendingReadMarkersTest {
  private final QuasselCorePendingReadMarkers pending = new QuasselCorePendingReadMarkers();

  @Test
  void replayConsumesOnlyTheExactMessageIdOnce() {
    pending.defer(11, 42);
    pending.defer(12, 80);
    assertNull(pending.takeBufferForMessage(41));
    assertEquals(11, pending.takeBufferForMessage(42));
    assertNull(pending.takeBufferForMessage(42));
    assertEquals(12, pending.takeBufferForMessage(80));
  }

  @Test
  void bufferZeroAndFullWidthMessageIdsAreRetained() {
    pending.defer(0, Long.MAX_VALUE);
    assertEquals(0, pending.takeBufferForMessage(Long.MAX_VALUE));
  }

  @Test
  void repeatedObservationsOfOneMessageKeepOnePendingReplay() {
    pending.defer(11, 42);
    pending.defer(11, 42);
    assertEquals(11, pending.takeBufferForMessage(42));
    assertNull(pending.takeBufferForMessage(42));
  }

  @Test
  void forgettingABufferDiscardsItsMarkersAndLeavesOtherBuffersPending() {
    pending.defer(11, 42);
    pending.defer(11, 43);
    pending.defer(12, 80);
    pending.forgetBuffer(11);
    pending.forgetBuffer(11);
    assertNull(pending.takeBufferForMessage(42));
    assertNull(pending.takeBufferForMessage(43));
    assertEquals(12, pending.takeBufferForMessage(80));
  }

  @Test
  void replacingABufferObservationPreventsReplayOfTheSupersededMarker() {
    pending.defer(11, 42);
    pending.defer(12, 80);
    pending.forgetBuffer(11);
    pending.defer(11, 43);
    assertNull(pending.takeBufferForMessage(42));
    assertEquals(11, pending.takeBufferForMessage(43));
    assertEquals(12, pending.takeBufferForMessage(80));
  }

  @Test
  void clearingTheSessionDiscardsPendingMarkersAndAllowsFreshObservations() {
    pending.defer(11, 42);
    pending.defer(12, 80);
    pending.clear();
    pending.clear();
    assertNull(pending.takeBufferForMessage(42));
    assertNull(pending.takeBufferForMessage(80));
    pending.defer(11, 43);
    assertEquals(11, pending.takeBufferForMessage(43));
  }

  @ParameterizedTest
  @CsvSource({"-1,42", "11,0", "11,-1"})
  void invalidObservationsDoNotConsumeCapacityOrAValidPendingMessage(int bufferId, long messageId) {
    var limited = new QuasselCorePendingReadMarkers(1);
    limited.defer(12, 42);
    limited.defer(bufferId, messageId);
    assertEquals(12, limited.takeBufferForMessage(42));
    assertNull(limited.takeBufferForMessage(messageId));
  }

  @Test
  void capacityIsCappedDuringRepeatedInsertionsWithoutRequiringReplay() {
    var limited = new QuasselCorePendingReadMarkers(3);
    for (int id = 1; id <= 100; id++) {
      limited.defer(id, id);
    }
    int retained = 0;
    for (int id = 1; id <= 100; id++) {
      Integer bufferId = limited.takeBufferForMessage(id);
      if (bufferId != null) {
        assertEquals(id, bufferId);
        retained++;
      }
    }
    assertEquals(3, retained);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void capacityMustBePositive(int maxSize) {
    assertThrows(IllegalArgumentException.class, () -> new QuasselCorePendingReadMarkers(maxSize));
  }
}

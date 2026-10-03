package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.HistorySelectorKind;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuasselCoreHistorySupportTest {
  @Test
  void parsesNumericTimestampAndOptionalWildcardSelectors() {
    assertEquals(
        42L, QuasselCoreHistorySupport.parseHistorySelector(" MSGID = 42 ", false).msgId());
    Instant at = Instant.parse("2026-01-01T00:00:00Z");
    assertEquals(
        at, QuasselCoreHistorySupport.parseHistorySelector("timestamp=" + at, false).timestamp());
    assertEquals(
        HistorySelectorKind.WILDCARD,
        QuasselCoreHistorySupport.parseHistorySelector("*", true).kind());
    for (String selector :
        new String[] {
          "*",
          "msgid=0",
          "msgid=-1",
          "msgid=abc",
          "msgid=9223372036854775808",
          "timestamp=bad",
          "unknown=1",
          "msgid="
        }) {
      assertThrows(
          IllegalArgumentException.class,
          () -> QuasselCoreHistorySupport.parseHistorySelector(selector, false));
    }
  }

  @Test
  void clampsBacklogLimitsAndWireMessageIds() {
    assertEquals(50, QuasselCoreHistorySupport.normalizeHistoryLimit(0));
    assertEquals(50, QuasselCoreHistorySupport.normalizeHistoryLimit(-5));
    assertEquals(200, QuasselCoreHistorySupport.normalizeHistoryLimit(999));
    assertEquals(25, QuasselCoreHistorySupport.normalizeHistoryLimit(25));
    assertEquals(-1, QuasselCoreHistorySupport.clampMsgId(0));
    assertEquals(Integer.MAX_VALUE, QuasselCoreHistorySupport.clampMsgId(Long.MAX_VALUE));
  }

  @Test
  void keepsQualifiedTargetsSeparateAndResolvesNearestSamplesWithLowerIdTieBreak() {
    QuasselCoreHistorySupport history = new QuasselCoreHistorySupport();
    history.observe(" #ROOM{net:one} ", 10, Instant.ofEpochMilli(1000));
    history.observe("#room{net:one}", 20, Instant.ofEpochMilli(2000));
    history.observe("#room{net:two}", 10, Instant.ofEpochMilli(9000));
    assertEquals(1000, history.timestampForMsgId("#ROOM{net:one}", 15));
    assertEquals(2000, history.timestampForMsgId("#room{net:one}", 16));
    assertEquals(9000, history.timestampForMsgId("#room{net:two}", 10));
    assertEquals(-1, history.timestampForMsgId("#room", 10));
    assertEquals(10, history.msgIdForTimestamp("#room{net:one}", Instant.ofEpochMilli(1500)));
    assertEquals(20, history.msgIdForTimestamp("#room{net:one}", Instant.ofEpochMilli(1501)));
  }

  @Test
  void boundsSamplesWhileRetainingLifetimeAnchorsForAscendingAndDescendingReplay() {
    QuasselCoreHistorySupport history = new QuasselCoreHistorySupport();
    for (int id = 1; id <= 1000; id++) {
      history.observe("#ascending", id, Instant.ofEpochMilli(id * 1000L));
      int descending = 1001 - id;
      history.observe("#descending", descending, Instant.ofEpochMilli(descending * 1000L));
    }
    assertEquals(489_000, history.timestampForMsgId("#ascending", 1));
    assertEquals(512_000, history.timestampForMsgId("#descending", 1000));
    assertEquals(1, history.msgIdForTimestamp("#ascending", Instant.EPOCH));
    assertEquals(1000, history.msgIdForTimestamp("#descending", Instant.ofEpochMilli(2_000_000)));
  }

  @Test
  void boundsTargetsAndClearsAllObservationsAtSessionTeardown() {
    QuasselCoreHistorySupport history = new QuasselCoreHistorySupport();
    for (int target = 0; target < 4500; target++) {
      history.observe("#room-" + target, 1, Instant.ofEpochMilli(1000));
    }
    int retained = 0;
    for (int target = 0; target < 4500; target++) {
      if (history.timestampForMsgId("#room-" + target, 1) > 0) retained++;
    }
    assertEquals(4096, retained);
    history.clear();
    for (int target = 0; target < 4500; target++) {
      assertEquals(-1, history.timestampForMsgId("#room-" + target, 1));
    }
  }

  @Test
  void ignoresInvalidObservationsAndReplacesDuplicateIdSamples() {
    QuasselCoreHistorySupport history = new QuasselCoreHistorySupport();
    history.observe(null, 1, Instant.ofEpochMilli(1000));
    history.observe("#room", 0, Instant.ofEpochMilli(1000));
    assertEquals(-1, history.msgIdForTimestamp("#room", Instant.now()));
    history.observe("#room", 1, Instant.ofEpochMilli(1000));
    history.observe("#room", 1, Instant.ofEpochMilli(2000));
    assertEquals(2000, history.timestampForMsgId("#room", 1));
    assertEquals(-1, history.timestampForMsgId("#room", 0));
  }
}

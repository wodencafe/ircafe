package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryRuntimeSupport.Plan;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandOperation;
import cafe.woden.ircclient.irc.quassel.QuasselCoreHistoryRequestPlanner.BacklogRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuasselCoreHistoryRequestPlannerTest {
  @ParameterizedTest
  @CsvSource({
    "BEFORE, msgid=20, '', 25, -1, 20, 25",
    "LATEST, msgid=20, '', 25, 21, -1, 25",
    "LATEST, *, '', 999, -1, -1, 200",
    "BETWEEN, msgid=10, msgid=30, 25, 11, 30, 25",
    "BETWEEN, msgid=30, msgid=10, 25, 11, 30, 25",
    "BETWEEN, msgid=20, msgid=20, 25, 21, 20, 25",
    "BETWEEN, *, msgid=20, 25, -1, 20, 25",
    "BETWEEN, msgid=20, *, 25, 21, -1, 25",
    "BETWEEN, *, *, 0, -1, -1, 50",
    "AROUND, msgid=20, '', 10, 15, 25, 10",
    "AROUND, msgid=20, '', 5, 18, 22, 5",
    "AROUND, msgid=20, '', 1, 19, 21, 1",
    "AROUND, msgid=1, '', 200, 1, 101, 200",
    "AROUND, msgid=100, '', -5, 75, 125, 50",
    "BEFORE, msgid=2147483648, '', 25, -1, 2147483647, 25",
    "LATEST, msgid=2147483647, '', 25, 2147483647, -1, 25",
    "AROUND, msgid=2147483647, '', 10, 2147483642, 2147483647, 10",
    "BEFORE, timestamp=1970-01-01T00:00:02Z, '', 25, -1, 20, 25",
    "LATEST, timestamp=1970-01-01T00:00:02Z, '', 25, 30, -1, 25",
    "BEFORE, timestamp=1970-01-01T00:00:01.500Z, '', 25, -1, 20, 25",
    "LATEST, timestamp=1970-01-01T00:00:01.500Z, '', 25, 20, -1, 25",
    "BEFORE, timestamp=1970-01-01T00:00:04Z, '', 25, -1, 31, 25",
    "LATEST, timestamp=1970-01-01T00:00:04Z, '', 25, 31, -1, 25",
    "LATEST, timestamp=1970-01-01T00:00:00Z, '', 25, 10, -1, 25",
    "BETWEEN, timestamp=1970-01-01T00:00:01Z, timestamp=1970-01-01T00:00:03Z, 25, 20, 30, 25",
    "BETWEEN, timestamp=1970-01-01T00:00:03Z, timestamp=1970-01-01T00:00:01Z, 25, 20, 30, 25",
    "BETWEEN, timestamp=1970-01-01T00:00:02Z, timestamp=1970-01-01T00:00:02Z, 25, 30, 20, 25",
    "BETWEEN, timestamp=1970-01-01T00:00:01.100Z, timestamp=1970-01-01T00:00:01.900Z, 25, 20, 20, 25",
    "BETWEEN, timestamp=1970-01-01T00:00:01.900Z, timestamp=1970-01-01T00:00:01.100Z, 25, 20, 20, 25",
    "BETWEEN, msgid=10, timestamp=1970-01-01T00:00:02Z, 25, 11, 20, 25",
    "BETWEEN, timestamp=1970-01-01T00:00:02Z, msgid=10, 25, 11, 20, 25",
    "BETWEEN, msgid=30, timestamp=1970-01-01T00:00:02Z, 25, 30, 30, 25",
    "BETWEEN, timestamp=1970-01-01T00:00:02Z, msgid=30, 25, 30, 30, 25",
    "AROUND, timestamp=1970-01-01T00:00:02Z, '', 10, 5, 15, 10",
    "AROUND, timestamp=1970-01-01T00:00:02.001Z, '', 10, 25, 35, 10"
  })
  void preservesNativeBounds(
      String mode,
      String primary,
      String secondary,
      int limit,
      int first,
      int last,
      int expectedLimit) {
    QuasselCoreHistorySupport history = observedHistory();
    assertEquals(
        new BacklogRequest(first, last, expectedLimit),
        QuasselCoreHistoryRequestPlanner.plan(
            history, plan(mode, "#room", primary, secondary, limit)));
  }

  @ParameterizedTest
  @CsvSource({"BEFORE, -1, -1", "LATEST, -1, -1", "BETWEEN, -1, 30", "AROUND, -1, -1"})
  void unknownTargetDoesNotBorrowAnotherTargetsTimestampSamples(String mode, int first, int last) {
    assertEquals(
        new BacklogRequest(first, last, 25),
        QuasselCoreHistoryRequestPlanner.plan(
            observedHistory(),
            plan(mode, "#unknown", "timestamp=1970-01-01T00:00:02Z", "msgid=30", 25)));
  }

  @ParameterizedTest
  @CsvSource({
    "BEFORE, *, history selector must be key=value",
    "AROUND, *, history selector must be key=value",
    "LATEST, msgid=abc, msgid selector must be numeric for Quassel backlog",
    "BEFORE, msgid=0, msgid selector must be a positive integer",
    "BETWEEN, timestamp=bad, timestamp selector must be ISO-8601 (UTC)"
  })
  void rejectsInvalidNativeSelectors(String mode, String primary, String message) {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                QuasselCoreHistoryRequestPlanner.plan(
                    observedHistory(), plan(mode, "#room", primary, "msgid=30", 25)));
    assertEquals(message, error.getMessage());
  }

  @Test
  void rejectsInvalidSecondarySelector() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                QuasselCoreHistoryRequestPlanner.plan(
                    observedHistory(), plan("BETWEEN", "#room", "msgid=10", "msgid=bad", 25)));
    assertEquals("msgid selector must be numeric for Quassel backlog", error.getMessage());
  }

  @Test
  void plansAgainstCurrentSessionObservationsAndKeepsNetworkTargetsSeparate() {
    QuasselCoreHistorySupport history = observedHistory();
    history.observe("#room{net:one}", 100, Instant.ofEpochSecond(2));
    history.observe("#room{net:two}", 200, Instant.ofEpochSecond(2));
    Plan request = plan("BEFORE", "#room{net:one}", "timestamp=1970-01-01T00:00:02Z", "", 25);
    assertEquals(
        new BacklogRequest(-1, 100, 25), QuasselCoreHistoryRequestPlanner.plan(history, request));
    assertEquals(
        new BacklogRequest(-1, 200, 25),
        QuasselCoreHistoryRequestPlanner.plan(
            history, plan("BEFORE", "#room{net:two}", request.primarySelector(), "", 25)));
    history.clear();
    assertEquals(
        new BacklogRequest(-1, -1, 25), QuasselCoreHistoryRequestPlanner.plan(history, request));
    history.observe(request.target(), 300, Instant.ofEpochSecond(2));
    assertEquals(
        new BacklogRequest(-1, 300, 25), QuasselCoreHistoryRequestPlanner.plan(history, request));
  }

  private static QuasselCoreHistorySupport observedHistory() {
    QuasselCoreHistorySupport history = new QuasselCoreHistorySupport();
    history.observe("#room", 10, Instant.ofEpochSecond(1));
    history.observe("#room", 20, Instant.ofEpochSecond(2));
    history.observe("#room", 21, Instant.ofEpochSecond(2));
    history.observe("#room", 30, Instant.ofEpochSecond(3));
    return history;
  }

  private static Plan plan(
      String mode, String target, String primary, String secondary, int limit) {
    return new Plan(
        Ircv3OutboundCommandOperation.valueOf("CHAT_HISTORY_" + mode),
        "",
        target,
        primary,
        secondary,
        limit);
  }
}

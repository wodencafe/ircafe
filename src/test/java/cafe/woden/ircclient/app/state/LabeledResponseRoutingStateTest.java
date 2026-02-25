package cafe.woden.ircclient.app.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LabeledResponseRoutingStateTest {

  private final LabeledResponseRoutingState state = new LabeledResponseRoutingState();

  @Test
  void prepareOutgoingRawInjectsGeneratedLabelWhenMissing() {
    var prepared = state.prepareOutgoingRaw("libera", "WHO #ircafe");

    assertTrue(prepared.injected());
    assertTrue(prepared.line().startsWith("@label="));
    assertTrue(prepared.line().endsWith(" WHO #ircafe"));
    assertTrue(prepared.label().startsWith("ircafe-libera-"));
  }

  @Test
  void prepareOutgoingRawPreservesExistingLabel() {
    var prepared = state.prepareOutgoingRaw("libera", "@label=req-42 WHO #ircafe");

    assertFalse(prepared.injected());
    assertEquals("@label=req-42 WHO #ircafe", prepared.line());
    assertEquals("req-42", prepared.label());
  }

  @Test
  void prepareOutgoingRawReadsEscapedLabelValues() {
    var prepared = state.prepareOutgoingRaw("libera", "@+label=req\\:42 WHO #ircafe");

    assertFalse(prepared.injected());
    assertEquals("req;42", prepared.label());
  }

  @Test
  void rememberAndFindIfFreshReturnsPendingContext() {
    TargetRef origin = new TargetRef("libera", "#ircafe");
    Instant at = Instant.now(); // or Instant.now().minus(Duration.ofMinutes(1))
    state.remember("libera", "req-1", origin, "WHO #ircafe", at);

    var found = state.findIfFresh("libera", "req-1", Duration.ofDays(1));
    assertNotNull(found);

    assertEquals(origin, found.originTarget());
    assertEquals("WHO #ircafe", found.requestPreview());
  }

  @Test
  void findIfFreshDropsExpiredEntries() {
    TargetRef origin = new TargetRef("libera", "#ircafe");
    Instant old = Instant.now().minus(Duration.ofMinutes(10));
    state.remember("libera", "req-expired", origin, "LIST", old);

    var found = state.findIfFresh("libera", "req-expired", Duration.ofSeconds(1));
    assertNull(found);
    assertNull(state.findIfFresh("libera", "req-expired", Duration.ofDays(1)));
  }

  @Test
  void clearServerRemovesAllPendingLabelsForServer() {
    TargetRef libera = new TargetRef("libera", "#ircafe");
    TargetRef oftc = new TargetRef("oftc", "#chat");
    state.remember("libera", "req-a", libera, "WHO #ircafe", Instant.now());
    state.remember("oftc", "req-b", oftc, "WHO #chat", Instant.now());

    state.clearServer("libera");

    assertNull(state.findIfFresh("libera", "req-a", Duration.ofDays(1)));
    assertNotNull(state.findIfFresh("oftc", "req-b", Duration.ofDays(1)));
  }

  @Test
  void markOutcomeIfPendingAllowsFailureToOverrideSuccess() {
    TargetRef origin = new TargetRef("libera", "#ircafe");
    state.remember("libera", "req-1", origin, "WHO #ircafe", Instant.now());

    var first =
        state.markOutcomeIfPending(
            "libera", "req-1", LabeledResponseRoutingState.Outcome.SUCCESS, Instant.now());
    assertNotNull(first);
    assertEquals(LabeledResponseRoutingState.Outcome.SUCCESS, first.outcome());

    var second =
        state.markOutcomeIfPending(
            "libera", "req-1", LabeledResponseRoutingState.Outcome.FAILURE, Instant.now());
    assertNotNull(second);
    assertEquals(LabeledResponseRoutingState.Outcome.FAILURE, second.outcome());
  }

  @Test
  void collectTimedOutMarksOldPendingRequests() {
    TargetRef origin = new TargetRef("libera", "#ircafe");
    Instant old = Instant.now().minus(Duration.ofMinutes(2));
    state.remember("libera", "req-timeout", origin, "LIST", old);

    var timedOut = state.collectTimedOut(Duration.ofSeconds(10), 10);
    assertEquals(1, timedOut.size());
    assertEquals("req-timeout", timedOut.get(0).label());
    assertEquals(LabeledResponseRoutingState.Outcome.TIMEOUT, timedOut.get(0).request().outcome());
  }
}

package cafe.woden.ircclient.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LabeledResponseRoutingStateTest {

  private final LabeledResponseRoutingState state = new LabeledResponseRoutingState();

  @Test
  void adapterNormalizesTargetsAndPreviewsWithoutLosingOutcomeContext() {
    Instant started = Instant.now().minusSeconds(2);
    Instant finished = Instant.now();
    state.remember(
        " oftc ", " req ", new TargetRef("libera", "#ircafe"), " WHO\n  #ircafe ", started);
    var pending = state.findIfFresh("oftc", "req", Duration.ofMinutes(2));
    assertNotNull(pending);
    assertEquals(new TargetRef("oftc", "#ircafe"), pending.originTarget());
    assertEquals("WHO #ircafe", pending.requestPreview());
    assertEquals(started, pending.startedAt());
    assertEquals(LabeledResponseRoutingPort.Outcome.PENDING, pending.outcome());
    assertNull(pending.outcomeAt());
    var completed =
        state.markOutcomeIfPending(
            "oftc", "req", LabeledResponseRoutingPort.Outcome.SUCCESS, finished);
    assertEquals(pending.originTarget(), completed.originTarget());
    assertEquals(pending.requestPreview(), completed.requestPreview());
    assertEquals(started, completed.startedAt());
    assertEquals(finished, completed.outcomeAt());
    assertEquals(LabeledResponseRoutingPort.Outcome.SUCCESS, completed.outcome());
    assertNull(state.markOutcomeIfPending("oftc", "req", null, finished));
  }

  @Test
  void adapterUsesOriginServerWhenOmittedAndRejectsMissingTargets() {
    TargetRef origin = new TargetRef("libera", "#ircafe");
    state.remember(" ", "req", origin, null, null);
    var found = state.findIfFresh("libera", "req", Duration.ofMinutes(2));
    assertNotNull(found);
    assertEquals(origin, found.originTarget());
    assertEquals("", found.requestPreview());
    state.remember("libera", "missing", null, "WHO", null);
    assertNull(state.findIfFresh("libera", "missing", Duration.ZERO));
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
            "libera", "req-1", LabeledResponseRoutingPort.Outcome.SUCCESS, Instant.now());
    assertNotNull(first);
    assertEquals(LabeledResponseRoutingPort.Outcome.SUCCESS, first.outcome());

    var second =
        state.markOutcomeIfPending(
            "libera", "req-1", LabeledResponseRoutingPort.Outcome.FAILURE, Instant.now());
    assertNotNull(second);
    assertEquals(LabeledResponseRoutingPort.Outcome.FAILURE, second.outcome());
  }

  @Test
  void collectTimedOutMarksOldPendingRequests() {
    TargetRef origin = new TargetRef("libera", "#ircafe");
    Instant old = Instant.now().minus(Duration.ofMinutes(2));
    state.remember("libera", "req-timeout", origin, "LIST", old);

    var timedOut = state.collectTimedOut(Duration.ofSeconds(10), 10);
    assertEquals(1, timedOut.size());
    assertEquals("req-timeout", timedOut.get(0).label());
    assertEquals(LabeledResponseRoutingPort.Outcome.TIMEOUT, timedOut.get(0).request().outcome());
  }
}

package cafe.woden.ircclient.state;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LabeledResponseRequestStoreTest {
  private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
  private final Clock clock = mock(Clock.class);
  private final LabeledResponseRequestStore<String> store =
      new LabeledResponseRequestStore<>(clock);

  @BeforeEach
  void setClock() {
    when(clock.instant()).thenReturn(NOW);
  }

  @Test
  void requestsAreServerScopedAndRetainedAcrossReplyLines() {
    store.remember(" libera ", " label ", "first", null);
    store.remember("oftc", "label", "second", null);
    var first = store.findIfFresh("libera", "label", Duration.ofMinutes(2));
    assertEquals("first", first.context());
    assertEquals(NOW, first.startedAt());
    assertEquals(LabeledResponseRequestStore.Outcome.PENDING, first.outcome());
    assertNull(first.outcomeAt());
    assertEquals(first, store.findIfFresh(" libera ", " label ", Duration.ofMinutes(2)));
    assertEquals("second", store.findIfFresh("oftc", "label", null).context());
    store.clearServer(" libera ");
    assertNull(store.findIfFresh("libera", "label", null));
    assertNotNull(store.findIfFresh("oftc", "label", null));
  }

  @Test
  void invalidRequestsAreIgnoredAndSameLabelReplacesPreviousContext() {
    store.remember(null, "label", "invalid", null);
    store.remember("libera", " ", "invalid", null);
    store.remember("libera", "label", null, null);
    assertNull(store.findIfFresh("libera", "label", null));
    store.remember("libera", "label", "old", NOW.minusSeconds(10));
    store.remember("libera", "label", "new", null);
    assertEquals("new", store.findIfFresh("libera", "label", null).context());
    store.clearServer(null);
    assertNotNull(store.findIfFresh("libera", "label", null));
  }

  @Test
  void freshnessIncludesExactBoundaryAndPermanentlyRemovesExpiredRequests() {
    store.remember("libera", "label", "context", NOW.minusSeconds(10));
    assertNotNull(store.findIfFresh("libera", "label", Duration.ofSeconds(10)));
    when(clock.instant()).thenReturn(NOW.plusNanos(1));
    assertNull(store.findIfFresh("libera", "label", Duration.ofSeconds(10)));
    assertNull(store.findIfFresh("libera", "label", Duration.ofDays(1)));
  }

  @Test
  void nullZeroAndNegativeMaxAgeDisableFreshnessExpiry() {
    store.remember("libera", "label", "context", NOW.minus(Duration.ofDays(1)));
    assertNotNull(store.findIfFresh("libera", "label", null));
    assertNotNull(store.findIfFresh("libera", "label", Duration.ZERO));
    assertNotNull(store.findIfFresh("libera", "label", Duration.ofSeconds(-1)));
  }

  @Test
  void failureOverridesSuccessOnceWithoutLosingContextOrTimestamps() {
    Instant started = NOW.minusSeconds(5);
    store.remember("libera", "label", "context", started);
    var success =
        store.markOutcomeIfPending(
            "libera", "label", LabeledResponseRequestStore.Outcome.SUCCESS, NOW);
    assertEquals(LabeledResponseRequestStore.Outcome.SUCCESS, success.outcome());
    assertNull(
        store.markOutcomeIfPending(
            "libera", "label", LabeledResponseRequestStore.Outcome.SUCCESS, NOW));
    assertNull(
        store.markOutcomeIfPending(
            "libera", "label", LabeledResponseRequestStore.Outcome.TIMEOUT, NOW));
    when(clock.instant()).thenReturn(NOW.plusSeconds(1));
    var failure =
        store.markOutcomeIfPending(
            "libera", "label", LabeledResponseRequestStore.Outcome.FAILURE, null);
    assertEquals(LabeledResponseRequestStore.Outcome.FAILURE, failure.outcome());
    assertEquals("context", failure.context());
    assertEquals(started, failure.startedAt());
    assertEquals(NOW.plusSeconds(1), failure.outcomeAt());
    assertNull(
        store.markOutcomeIfPending(
            "libera", "label", LabeledResponseRequestStore.Outcome.FAILURE, NOW));
    assertNull(
        store.markOutcomeIfPending(
            "libera", "label", LabeledResponseRequestStore.Outcome.SUCCESS, NOW));
  }

  @Test
  void pendingAndUnknownOutcomesDoNotCompleteRequests() {
    store.remember("libera", "label", "context", null);
    assertNull(store.markOutcomeIfPending("libera", "label", null, null));
    assertNull(
        store.markOutcomeIfPending(
            "libera", "label", LabeledResponseRequestStore.Outcome.PENDING, null));
    assertNull(
        store.markOutcomeIfPending(
            "libera", "missing", LabeledResponseRequestStore.Outcome.SUCCESS, null));
    assertFalse(store.findIfFresh("libera", "label", null).terminal());
  }

  @Test
  void timeoutDefaultsToThirtySecondsAndRetainsContextForLateFailure() {
    store.remember("libera", "label", "context", NOW.minusSeconds(30));
    assertTrue(store.collectTimedOut(null, 10).isEmpty());
    when(clock.instant()).thenReturn(NOW.plusNanos(1));
    var timeouts = store.collectTimedOut(Duration.ZERO, 0);
    assertEquals(1, timeouts.size());
    var timeout = timeouts.getFirst();
    assertEquals("libera", timeout.serverId());
    assertEquals("label", timeout.label());
    assertEquals("context", timeout.request().context());
    assertEquals(LabeledResponseRequestStore.Outcome.TIMEOUT, timeout.request().outcome());
    assertEquals(NOW.plusNanos(1), timeout.timedOutAt());
    assertEquals(timeout.timedOutAt(), timeout.request().outcomeAt());
    assertTrue(store.collectTimedOut(Duration.ofSeconds(-1), 10).isEmpty());
    assertNotNull(store.findIfFresh("libera", "label", Duration.ofMinutes(2)));
    assertNotNull(
        store.markOutcomeIfPending(
            "libera", "label", LabeledResponseRequestStore.Outcome.FAILURE, null));
  }

  @Test
  void timeoutBatchesRespectLimitAndSkipTerminalRequests() {
    for (String label : new String[] {"a", "b", "c", "done"}) {
      store.remember("libera", label, label, NOW.minusSeconds(60));
    }
    store.markOutcomeIfPending("libera", "done", LabeledResponseRequestStore.Outcome.SUCCESS, null);
    assertEquals(2, store.collectTimedOut(Duration.ofSeconds(10), 2).size());
    assertEquals(1, store.collectTimedOut(Duration.ofSeconds(10), 2).size());
    assertTrue(store.collectTimedOut(Duration.ofSeconds(10), 2).isEmpty());
  }

  @Test
  void rememberPrunesOnlyOlderRequestsOnTheSameServer() {
    Instant cutoff = NOW.minus(Duration.ofMinutes(10));
    store.remember("libera", "old", "context", cutoff.minusNanos(1));
    store.remember("oftc", "old", "context", cutoff.minusNanos(1));
    store.remember("libera", "boundary", "context", cutoff);
    store.remember("libera", "new", "context", NOW);
    assertNull(store.findIfFresh("libera", "old", null));
    assertNotNull(store.findIfFresh("libera", "boundary", null));
    assertNotNull(store.findIfFresh("oftc", "old", null));
  }

  @Test
  void timeoutCollectionPrunesStaleEntriesAcrossServersAfterReportingThem() {
    store.remember("libera", "old", "context", NOW.minus(Duration.ofMinutes(11)));
    store.remember("oftc", "old", "context", NOW.minus(Duration.ofMinutes(11)));
    var timeouts = store.collectTimedOut(Duration.ofSeconds(10), 10);
    assertEquals(2, timeouts.size());
    assertNull(store.findIfFresh("libera", "old", null));
    assertNull(store.findIfFresh("oftc", "old", null));
  }
}

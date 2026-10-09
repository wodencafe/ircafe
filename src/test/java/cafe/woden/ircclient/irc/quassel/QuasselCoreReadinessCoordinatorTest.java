package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreReadinessCoordinatorTest {
  private final TestScheduler scheduler = new TestScheduler();
  private final AtomicBoolean established = new AtomicBoolean(true);
  private final List<IrcEvent> events = new CopyOnWriteArrayList<>();
  private final QuasselCoreReadinessCoordinator readiness =
      new QuasselCoreReadinessCoordinator(established::get, events::add, () -> scheduler);

  @Test
  void fallbackWaitsThreeSecondsThenPublishesReadyBeforeFeatures() {
    readiness.scheduleFallback();
    Disposable task = readiness.fallbackTask.get();
    readiness.emitIfReady();
    scheduler.advanceTimeBy(2_999, TimeUnit.MILLISECONDS);
    assertTrue(events.isEmpty());
    scheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);
    assertReadyPair();
    assertTrue(task.isDisposed());
    assertNull(readiness.fallbackTask.get());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void readinessRequiresEstablishedPhaseForBothSyncAndFallback(boolean sync) {
    established.set(false);
    if (sync) {
      readiness.observeSync();
      readiness.emitIfReady();
    } else {
      readiness.scheduleFallback();
      scheduler.advanceTimeBy(3, TimeUnit.SECONDS);
    }
    assertTrue(events.isEmpty());
    established.set(true);
    readiness.emitIfReady();
    assertReadyPair();
  }

  @Test
  void syncObservationDoesNotPublishUntilStateApplicationHasFinished() {
    var stateApplied = new AtomicBoolean();
    var coordinator =
        new QuasselCoreReadinessCoordinator(
            established::get,
            event -> {
              assertTrue(stateApplied.get());
              events.add(event);
            },
            () -> scheduler);
    coordinator.scheduleFallback();
    Disposable task = coordinator.fallbackTask.get();
    coordinator.observeSync();
    assertTrue(events.isEmpty());
    stateApplied.set(true);
    coordinator.emitIfReady();
    assertReadyPair();
    assertTrue(task.isDisposed());
    assertNull(coordinator.fallbackTask.get());
  }

  @Test
  void repeatedSyncAndCancelledFallbackDoNotRepublishReadiness() {
    readiness.scheduleFallback();
    for (int count = 0; count < 3; count++) {
      readiness.observeSync();
      readiness.emitIfReady();
    }
    scheduler.advanceTimeBy(30, TimeUnit.SECONDS);
    readiness.emitIfReady();
    assertReadyPair();
  }

  @Test
  void syncAfterFallbackDoesNotRepublishReadiness() {
    readiness.scheduleFallback();
    scheduler.advanceTimeBy(3, TimeUnit.SECONDS);
    readiness.observeSync();
    readiness.emitIfReady();
    assertReadyPair();
  }

  @Test
  void cancellationPreventsFallbackObservationAndPublication() {
    readiness.scheduleFallback();
    Disposable task = readiness.fallbackTask.get();
    readiness.cancelFallback();
    readiness.cancelFallback();
    scheduler.advanceTimeBy(30, TimeUnit.SECONDS);
    readiness.emitIfReady();
    assertTrue(events.isEmpty());
    assertFalse(readiness.syncObserved.get());
    assertTrue(task.isDisposed());
    assertNull(readiness.fallbackTask.get());
  }

  @Test
  void resettingObservationsRequiresAnotherSyncAndAllowsOneNewPublication() {
    readiness.observeSync();
    readiness.emitIfReady();
    readiness.resetObservations();
    readiness.emitIfReady();
    assertReadyPair();
    readiness.observeSync();
    readiness.emitIfReady();
    assertEquals(4, events.size());
    assertInstanceOf(IrcEvent.ConnectionReady.class, events.get(2));
    assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.get(3));
  }

  @Test
  void cancellationFailureIsIgnoredAndTaskIsDetachedBeforePublication() {
    var calls = new AtomicInteger();
    readiness.fallbackTask.set(
        Disposable.fromRunnable(
            () -> {
              assertNull(readiness.fallbackTask.get());
              assertTrue(events.isEmpty());
              calls.incrementAndGet();
              throw new IllegalStateException("cancel failed");
            }));
    readiness.observeSync();
    readiness.emitIfReady();
    readiness.cancelFallback();
    assertReadyPair();
    assertEquals(1, calls.get());
  }

  @Test
  void alreadyDisposedFallbackIsDetachedWithoutAnotherDisposeCall() {
    readiness.fallbackTask.set(
        new Disposable() {
          @Override
          public void dispose() {
            fail("already disposed");
          }

          @Override
          public boolean isDisposed() {
            return true;
          }
        });
    readiness.observeSync();
    readiness.emitIfReady();
    assertReadyPair();
    assertNull(readiness.fallbackTask.get());
  }

  @Test
  void observerFailureDoesNotRetryPublicationOrEmitFeatures() {
    IllegalStateException failure = new IllegalStateException("observer failed");
    var calls = new AtomicInteger();
    var coordinator =
        new QuasselCoreReadinessCoordinator(
            established::get,
            event -> {
              calls.incrementAndGet();
              throw failure;
            },
            () -> scheduler);
    coordinator.scheduleFallback();
    Disposable task = coordinator.fallbackTask.get();
    coordinator.observeSync();
    assertSame(failure, assertThrows(IllegalStateException.class, coordinator::emitIfReady));
    coordinator.emitIfReady();
    assertEquals(1, calls.get());
    assertTrue(task.isDisposed());
    assertNull(coordinator.fallbackTask.get());
  }

  @Test
  void concurrentPublicationAttemptsEmitOnlyOneReadyPair() throws Exception {
    readiness.observeSync();
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first =
          workers.submit(
              () -> {
                start.await();
                readiness.emitIfReady();
                return null;
              });
      var second =
          workers.submit(
              () -> {
                start.await();
                readiness.emitIfReady();
                return null;
              });
      start.countDown();
      first.get(2, TimeUnit.SECONDS);
      second.get(2, TimeUnit.SECONDS);
    }
    assertReadyPair();
  }

  @Test
  void schedulerIsResolvedOnlyWhenFallbackIsScheduled() {
    var resolutions = new AtomicInteger();
    var coordinator =
        new QuasselCoreReadinessCoordinator(
            established::get,
            events::add,
            () -> {
              resolutions.incrementAndGet();
              return scheduler;
            });
    coordinator.resetObservations();
    coordinator.observeSync();
    coordinator.emitIfReady();
    coordinator.cancelFallback();
    assertEquals(0, resolutions.get());
    coordinator.scheduleFallback();
    assertEquals(1, resolutions.get());
    coordinator.cancelFallback();
  }

  private void assertReadyPair() {
    assertEquals(2, events.size());
    assertInstanceOf(IrcEvent.ConnectionReady.class, events.getFirst());
    IrcEvent.ConnectionFeaturesUpdated features =
        assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.getLast());
    assertEquals("quassel-phase=sync-ready;detail=quassel-sync", features.source());
  }
}

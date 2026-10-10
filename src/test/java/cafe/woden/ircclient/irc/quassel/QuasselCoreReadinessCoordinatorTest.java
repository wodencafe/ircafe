package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    coordinator.cancelFallback();
    assertEquals(0, resolutions.get());
    coordinator.scheduleFallback();
    assertEquals(1, resolutions.get());
    coordinator.cancelFallback();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void cancellationOrSyncBeforeTimerRegistrationDisposesTheLateTask(boolean sync) {
    var owner = new AtomicReference<QuasselCoreReadinessCoordinator>();
    var coordinator =
        new QuasselCoreReadinessCoordinator(
            established::get,
            events::add,
            () -> {
              if (sync) {
                owner.get().observeSync();
                owner.get().emitIfReady();
              } else {
                owner.get().cancelFallback();
              }
              return scheduler;
            });
    owner.set(coordinator);
    coordinator.scheduleFallback();
    assertNull(coordinator.fallbackTask.get(), "late scheduling must not retain a task");
    scheduler.advanceTimeBy(30, TimeUnit.SECONDS);
    if (sync) assertReadyPair();
    else assertTrue(events.isEmpty(), "cancelled registration must not publish readiness");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void closeOrPriorReadinessSkipsSchedulingWithoutResolvingScheduler(boolean close) {
    var resolutions = new AtomicInteger();
    var coordinator =
        new QuasselCoreReadinessCoordinator(
            established::get,
            events::add,
            () -> {
              resolutions.incrementAndGet();
              return scheduler;
            });
    if (close) coordinator.close();
    else {
      coordinator.observeSync();
      coordinator.emitIfReady();
    }
    coordinator.scheduleFallback();
    assertEquals(0, resolutions.get());
    assertNull(coordinator.fallbackTask.get());
    if (close) assertTrue(events.isEmpty());
    else assertReadyPair();
  }

  @Test
  void closeCancelsTimerAndObservationResetCannotReopenTheCoordinator() {
    readiness.scheduleFallback();
    Disposable task = readiness.fallbackTask.get();
    readiness.close();
    readiness.close();
    readiness.resetObservations();
    readiness.observeSync();
    readiness.emitIfReady();
    readiness.scheduleFallback();
    scheduler.advanceTimeBy(30, TimeUnit.SECONDS);
    assertTrue(task.isDisposed());
    assertNull(readiness.fallbackTask.get());
    assertFalse(readiness.syncObserved.get());
    assertTrue(events.isEmpty());
  }

  @Test
  void closeDuringSchedulingDoesNotWaitForTheLateHandleAndSuppressesItsCallback() throws Exception {
    var created = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var timer = new AtomicReference<Disposable>();
    Scheduler delayed =
        duringRegistration(
            () -> {
              created.countDown();
              try {
                assertTrue(release.await(2, TimeUnit.SECONDS));
              } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
              }
            },
            timer);
    var coordinator =
        new QuasselCoreReadinessCoordinator(established::get, events::add, () -> delayed);
    try (var worker = Executors.newSingleThreadExecutor()) {
      var scheduling = worker.submit(coordinator::scheduleFallback);
      try {
        assertTrue(created.await(2, TimeUnit.SECONDS));
        coordinator.close();
        assertNull(coordinator.fallbackTask.get());
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS);
        assertFalse(coordinator.syncObserved.get());
        assertTrue(events.isEmpty());
      } finally {
        release.countDown();
      }
      scheduling.get(2, TimeUnit.SECONDS);
    }
    assertTrue(timer.get().isDisposed());
    assertNull(coordinator.fallbackTask.get());
  }

  @Test
  void fallbackCompletingBeforeSchedulingReturnsDetachesAndDisposesTheLateHandle() {
    var timer = new AtomicReference<Disposable>();
    Scheduler immediate =
        duringRegistration(() -> scheduler.advanceTimeBy(3, TimeUnit.SECONDS), timer);
    var coordinator =
        new QuasselCoreReadinessCoordinator(established::get, events::add, () -> immediate);
    coordinator.scheduleFallback();
    assertReadyPair();
    assertTrue(timer.get().isDisposed());
    assertNull(coordinator.fallbackTask.get());
  }

  @Test
  void schedulerFailureDetachesOwnerAndPreservesTheOriginalErrorForRetry() {
    var failed = new AtomicBoolean(true);
    var error = new IllegalStateException("scheduler unavailable");
    var coordinator =
        new QuasselCoreReadinessCoordinator(
            established::get,
            events::add,
            () -> {
              if (failed.get()) throw error;
              return scheduler;
            });
    assertSame(error, assertThrows(IllegalStateException.class, coordinator::scheduleFallback));
    assertNull(coordinator.fallbackTask.get());
    assertTrue(events.isEmpty());
    failed.set(false);
    coordinator.scheduleFallback();
    scheduler.advanceTimeBy(3, TimeUnit.SECONDS);
    assertReadyPair();
  }

  @Test
  void reentrantSchedulingDoesNotCreateASecondTimer() {
    var owner = new AtomicReference<QuasselCoreReadinessCoordinator>();
    var resolutions = new AtomicInteger();
    var coordinator =
        new QuasselCoreReadinessCoordinator(
            established::get,
            events::add,
            () -> {
              if (resolutions.incrementAndGet() == 1) owner.get().scheduleFallback();
              return scheduler;
            });
    owner.set(coordinator);
    coordinator.scheduleFallback();
    assertEquals(1, resolutions.get());
    coordinator.close();
    scheduler.advanceTimeBy(30, TimeUnit.SECONDS);
    assertTrue(events.isEmpty());
  }

  @Test
  void closeDuringPhaseCheckPreventsReadinessPublication() {
    var owner = new AtomicReference<QuasselCoreReadinessCoordinator>();
    var coordinator =
        new QuasselCoreReadinessCoordinator(
            () -> {
              owner.get().close();
              return true;
            },
            events::add,
            () -> scheduler);
    owner.set(coordinator);
    coordinator.observeSync();
    coordinator.emitIfReady();
    assertTrue(events.isEmpty());
  }

  @Test
  void closeDuringTimerDisposalPreventsReadinessPublication() {
    readiness.fallbackTask.set(Disposable.fromRunnable(readiness::close));
    readiness.observeSync();
    readiness.emitIfReady();
    assertTrue(events.isEmpty());
    assertNull(readiness.fallbackTask.get());
  }

  @Test
  void closeFromReadyObserverSuppressesTheFollowingFeatureEvent() {
    var owner = new AtomicReference<QuasselCoreReadinessCoordinator>();
    var coordinator =
        new QuasselCoreReadinessCoordinator(
            established::get,
            event -> {
              events.add(event);
              owner.get().close();
            },
            () -> scheduler);
    owner.set(coordinator);
    coordinator.observeSync();
    coordinator.emitIfReady();
    assertEquals(1, events.size());
    assertInstanceOf(IrcEvent.ConnectionReady.class, events.getFirst());
  }

  @Test
  void lateHandleDisposalFailureDoesNotRestoreTheTimerOrFailScheduling() {
    var owner = new AtomicReference<QuasselCoreReadinessCoordinator>();
    var cancellations = new AtomicInteger();
    Scheduler delayed =
        new Scheduler() {
          @Override
          public Worker createWorker() {
            return scheduler.createWorker();
          }

          @Override
          public Disposable scheduleDirect(Runnable runnable, long delay, TimeUnit unit) {
            owner.get().close();
            return Disposable.fromRunnable(
                () -> {
                  cancellations.incrementAndGet();
                  throw new IllegalStateException("late disposal failed");
                });
          }
        };
    var coordinator =
        new QuasselCoreReadinessCoordinator(established::get, events::add, () -> delayed);
    owner.set(coordinator);
    assertDoesNotThrow(coordinator::scheduleFallback);
    assertNull(coordinator.fallbackTask.get());
    assertEquals(1, cancellations.get());
    assertTrue(events.isEmpty());
  }

  private Scheduler duringRegistration(Runnable beforeReturn, AtomicReference<Disposable> timer) {
    return new Scheduler() {
      @Override
      public Worker createWorker() {
        return scheduler.createWorker();
      }

      @Override
      public Disposable scheduleDirect(Runnable runnable, long delay, TimeUnit unit) {
        Disposable task = scheduler.scheduleDirect(runnable, delay, unit);
        timer.set(task);
        beforeReturn.run();
        return task;
      }
    };
  }

  private void assertReadyPair() {
    assertEquals(2, events.size());
    assertInstanceOf(IrcEvent.ConnectionReady.class, events.getFirst());
    IrcEvent.ConnectionFeaturesUpdated features =
        assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.getLast());
    assertEquals("quassel-phase=sync-ready;detail=quassel-sync", features.source());
  }
}

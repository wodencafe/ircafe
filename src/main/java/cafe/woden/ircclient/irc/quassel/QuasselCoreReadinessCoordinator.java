package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.irc.IrcEvent;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates sync-driven and fallback readiness publication for one Core session. */
final class QuasselCoreReadinessCoordinator {
  private static final String READY_SOURCE = "quassel-phase=sync-ready;detail=quassel-sync";
  final AtomicBoolean syncObserved = new AtomicBoolean();
  final AtomicBoolean connectionReadyEmitted = new AtomicBoolean();
  final AtomicReference<Disposable> fallbackTask = new AtomicReference<>();
  private final BooleanSupplier established;
  private final Consumer<IrcEvent> events;
  private final Supplier<Scheduler> scheduler;

  QuasselCoreReadinessCoordinator(
      BooleanSupplier established, Consumer<IrcEvent> events, Supplier<Scheduler> scheduler) {
    this.established = Objects.requireNonNull(established, "established");
    this.events = Objects.requireNonNull(events, "events");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
  }

  void resetObservations() {
    syncObserved.set(false);
    connectionReadyEmitted.set(false);
  }

  void observeSync() {
    syncObserved.set(true);
  }

  void scheduleFallback() {
    Disposable task =
        scheduler
            .get()
            .scheduleDirect(
                () -> {
                  syncObserved.compareAndSet(false, true);
                  emitIfReady();
                },
                3,
                TimeUnit.SECONDS);
    fallbackTask.set(task);
  }

  /** Called after the service has applied sync state so observers see the updated session. */
  void emitIfReady() {
    if (!established.getAsBoolean()) return;
    if (!syncObserved.get()) return;
    if (!connectionReadyEmitted.compareAndSet(false, true)) return;
    cancelFallback();
    events.accept(new IrcEvent.ConnectionReady(Instant.now()));
    events.accept(new IrcEvent.ConnectionFeaturesUpdated(Instant.now(), READY_SOURCE));
  }

  void cancelFallback() {
    Disposable task = fallbackTask.getAndSet(null);
    if (task == null || task.isDisposed()) return;
    try {
      task.dispose();
    } catch (Exception ignored) {
    }
  }
}

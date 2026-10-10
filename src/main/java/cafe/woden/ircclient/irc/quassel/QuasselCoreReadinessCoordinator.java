package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.irc.IrcEvent;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.disposables.SerialDisposable;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates sync-driven and fallback readiness publication for one Core session. */
final class QuasselCoreReadinessCoordinator implements AutoCloseable {
  private static final String READY_SOURCE = "quassel-phase=sync-ready;detail=quassel-sync";
  final AtomicBoolean syncObserved = new AtomicBoolean();
  final AtomicBoolean connectionReadyEmitted = new AtomicBoolean();
  final AtomicReference<Disposable> fallbackTask = new AtomicReference<>();
  private final AtomicBoolean closed = new AtomicBoolean();
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
    if (!closed.get()) syncObserved.set(true);
  }

  void scheduleFallback() {
    if (closed.get() || connectionReadyEmitted.get()) return;
    SerialDisposable owner = new SerialDisposable();
    if (!fallbackTask.compareAndSet(null, owner)) return;
    if (closed.get() || connectionReadyEmitted.get()) {
      detach(owner);
      return;
    }
    Disposable task;
    try {
      task =
          scheduler
              .get()
              .scheduleDirect(
                  () -> {
                    if (closed.get() || owner.isDisposed()) return;
                    syncObserved.compareAndSet(false, true);
                    emitIfReady();
                  },
                  3,
                  TimeUnit.SECONDS);
    } catch (RuntimeException error) {
      detach(owner);
      throw error;
    }
    // Cancellation can dispose the owner before the scheduler returns this handle.
    try {
      var unused = owner.set(task);
    } catch (Exception ignored) {
      // Disposing a late handle must not fail session establishment.
    }
  }

  /** Called after the service has applied sync state so observers see the updated session. */
  void emitIfReady() {
    if (!canPublish()) return;
    if (!syncObserved.get()) return;
    if (!connectionReadyEmitted.compareAndSet(false, true)) return;
    cancelFallback();
    if (!canPublish()) return;
    events.accept(new IrcEvent.ConnectionReady(Instant.now()));
    if (canPublish()) {
      events.accept(new IrcEvent.ConnectionFeaturesUpdated(Instant.now(), READY_SOURCE));
    }
  }

  void cancelFallback() {
    disposeQuietly(fallbackTask.getAndSet(null));
  }

  @Override
  public void close() {
    closed.set(true);
    cancelFallback();
  }

  private boolean canPublish() {
    if (closed.get()) return false;
    boolean active = established.getAsBoolean();
    return active && !closed.get();
  }

  private void detach(Disposable owner) {
    fallbackTask.compareAndSet(owner, null);
    disposeQuietly(owner);
  }

  private static void disposeQuietly(Disposable task) {
    if (task == null) return;
    try {
      if (!task.isDisposed()) task.dispose();
    } catch (Exception ignored) {
    }
  }
}

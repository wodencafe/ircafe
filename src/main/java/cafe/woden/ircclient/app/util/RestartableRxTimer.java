package cafe.woden.ircclient.app.util;

import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A small helper that mimics {@code javax.swing.Timer} restart/stop semantics using RxJava.
 *
 * <p>This is intended for low-frequency debounce and one-shot timers in the application layer. UI
 * animation should generally remain a Swing Timer.
 */
public final class RestartableRxTimer implements AutoCloseable {
  private final Scheduler scheduler;
  private final Consumer<Throwable> onError;
  private final AtomicReference<Disposable> current = new AtomicReference<>();

  public RestartableRxTimer(Scheduler scheduler) {
    this(scheduler, err -> {});
  }

  public RestartableRxTimer(Scheduler scheduler, Consumer<Throwable> onError) {
    this.scheduler = Objects.requireNonNullElse(scheduler, RxVirtualSchedulers.computation());
    this.onError = (onError != null) ? onError : (err -> {});
  }

  public void restart(long delayMs, Runnable action) {
    restart(delayMs, TimeUnit.MILLISECONDS, action);
  }

  public void restart(long delay, TimeUnit unit, Runnable action) {
    stop();
    if (action == null) return;

    try {
      Disposable next =
          Completable.timer(delay, unit, scheduler).subscribe(action::run, onError::accept);
      current.set(next);
    } catch (Throwable t) {
      onError.accept(t);
    }
  }

  public void stop() {
    Disposable prev = current.getAndSet(null);
    if (prev != null && !prev.isDisposed()) {
      prev.dispose();
    }
  }

  @Override
  public void close() {
    stop();
  }
}

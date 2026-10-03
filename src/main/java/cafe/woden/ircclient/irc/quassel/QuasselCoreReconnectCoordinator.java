package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.disposables.SerialDisposable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Owns delayed reconnects and their subscriptions for one backend service. */
final class QuasselCoreReconnectCoordinator implements AutoCloseable {
  private static final long MIN_RECONNECT_DELAY_MS = 250L;
  private final IrcProperties.Reconnect policy;
  private final Predicate<String> serverExists;
  private final Function<String, Completable> connect;
  private final Consumer<ServerIrcEvent> events;
  private final Supplier<Scheduler> scheduler;
  private final Map<String, SerialDisposable> pending = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> attempts = new ConcurrentHashMap<>();
  private final CompositeDisposable subscriptions = new CompositeDisposable();
  private final AtomicBoolean closed = new AtomicBoolean();

  QuasselCoreReconnectCoordinator(
      IrcProperties.Reconnect policy,
      Predicate<String> serverExists,
      Function<String, Completable> connect,
      Consumer<ServerIrcEvent> events,
      Supplier<Scheduler> scheduler) {
    this.policy = policy;
    this.serverExists = Objects.requireNonNull(serverExists, "serverExists");
    this.connect = Objects.requireNonNull(connect, "connect");
    this.events = Objects.requireNonNull(events, "events");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
  }

  void schedule(String serverId, String reason) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || closed.get() || policy == null || !policy.enabled()) return;
    if (!serverExists.test(sid)) return;
    long attempt = attempts.computeIfAbsent(sid, ignored -> new AtomicLong()).incrementAndGet();
    if (policy.maxAttempts() > 0 && attempt > policy.maxAttempts()) {
      emitError(sid, "Reconnect aborted (max attempts reached)", null);
      return;
    }

    long delayMs = computeDelayMs(policy, attempt);
    SerialDisposable job = new SerialDisposable();
    SerialDisposable previous = pending.put(sid, job);
    disposeQuietly(previous);
    if (previous != null) subscriptions.delete(previous);
    if (!subscriptions.add(job) || closed.get()) {
      pending.remove(sid, job);
      attempts.remove(sid);
      disposeQuietly(job);
      subscriptions.delete(job);
      return;
    }
    events.accept(
        new ServerIrcEvent(
            sid,
            new IrcEvent.Reconnecting(
                Instant.now(), attempt, delayMs, Objects.toString(reason, "Disconnected"))));
    var unused =
        Completable.timer(delayMs, TimeUnit.MILLISECONDS, scheduler.get())
            // connectInternal cancels a pending timer; detach this timer before invoking it.
            .doOnComplete(() -> pending.remove(sid, job))
            .andThen(
                Completable.defer(
                    () -> {
                      if (closed.get()) return Completable.complete();
                      if (!serverExists.test(sid)) {
                        emitError(sid, "Reconnect cancelled (server removed)", null);
                        return Completable.complete();
                      }
                      return connect.apply(sid);
                    }))
            .doFinally(
                () -> {
                  pending.remove(sid, job);
                  subscriptions.delete(job);
                })
            .doOnSubscribe(job::set)
            .subscribe(
                () -> {},
                error -> {
                  String detail = Objects.toString(error.getMessage(), "").trim();
                  if (detail.isEmpty()) detail = error.getClass().getSimpleName();
                  emitError(sid, "Reconnect attempt failed: " + detail, error);
                  schedule(sid, "Reconnect attempt failed");
                });
  }

  void cancel(String serverId, boolean clearAttempts) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    SerialDisposable job = pending.remove(sid);
    disposeQuietly(job);
    // A timer disposed before subscription does not invoke doFinally.
    if (job != null) subscriptions.delete(job);
    if (clearAttempts) attempts.remove(sid);
  }

  void reset(String serverId) {
    String sid = normalizeServerId(serverId);
    if (!sid.isEmpty()) attempts.remove(sid);
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    subscriptions.dispose();
    pending.clear();
    attempts.clear();
  }

  private void emitError(String serverId, String message, Throwable error) {
    events.accept(new ServerIrcEvent(serverId, new IrcEvent.Error(Instant.now(), message, error)));
  }

  static long computeDelayMs(IrcProperties.Reconnect policy, long attempt) {
    if (policy == null) return MIN_RECONNECT_DELAY_MS;
    double multiplier = Math.pow(policy.multiplier(), Math.max(0L, attempt - 1L));
    long cappedDelay =
        (long) Math.min(policy.initialDelayMs() * multiplier, (double) policy.maxDelayMs());
    if (policy.jitterPct() <= 0d) return Math.max(MIN_RECONNECT_DELAY_MS, cappedDelay);
    double jitterFactor =
        1.0 + ThreadLocalRandom.current().nextDouble(-policy.jitterPct(), policy.jitterPct());
    long jittered = (long) Math.max(0L, cappedDelay * jitterFactor);
    return Math.max(MIN_RECONNECT_DELAY_MS, jittered);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static void disposeQuietly(Disposable disposable) {
    if (disposable == null || disposable.isDisposed()) return;
    try {
      disposable.dispose();
    } catch (Exception ignored) {
    }
  }
}

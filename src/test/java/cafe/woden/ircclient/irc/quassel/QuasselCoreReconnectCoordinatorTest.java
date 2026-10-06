package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class QuasselCoreReconnectCoordinatorTest {
  private final TestScheduler scheduler = new TestScheduler();
  private final List<ServerIrcEvent> events = new ArrayList<>();
  private final AtomicBoolean serverExists = new AtomicBoolean(true);
  private final AtomicInteger connections = new AtomicInteger();

  @Test
  void retriesFailedConnectionsWithCappedBackoffAndStopsAtTheAttemptLimit() {
    try (var coordinator = coordinator(policy(100, 500, 3), sid -> failedConnection())) {
      coordinator.schedule(" core ", "EOF");
      assertEquals(List.of(250L), delays());
      scheduler.advanceTimeBy(249, TimeUnit.MILLISECONDS);
      assertEquals(0, connections.get());
      scheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);
      assertEquals(1, connections.get());
      assertEquals(List.of(250L, 250L), delays());
      scheduler.advanceTimeBy(250, TimeUnit.MILLISECONDS);
      assertEquals(List.of(250L, 250L, 400L), delays());
      scheduler.advanceTimeBy(400, TimeUnit.MILLISECONDS);
      scheduler.advanceTimeBy(10, TimeUnit.SECONDS);
      assertEquals(3, connections.get());
      assertEquals(List.of(1L, 2L, 3L), attempts());
      assertEquals("core", events.getFirst().serverId());
      assertEquals("Reconnect aborted (max attempts reached)", lastError());
      assertTrue(
          events.stream()
              .anyMatch(
                  event ->
                      event.event() instanceof IrcEvent.Error error
                          && error.message().equals("Reconnect attempt failed: unavailable")
                          && error.cause() instanceof IOException));
    }
    assertEquals(500L, QuasselCoreReconnectCoordinator.computeDelayMs(policy(100, 500, 0), 4));
  }

  @Test
  void cancellationPreventsConnectAndClearsAttemptsWhenRequested() {
    try (var coordinator = coordinator(policy(300, 1000, 0), sid -> successfulConnection())) {
      coordinator.schedule("core", "EOF");
      coordinator.cancel(" core ", true);
      scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
      assertEquals(0, connections.get());
      coordinator.schedule("core", "again");
      scheduler.advanceTimeBy(300, TimeUnit.MILLISECONDS);
      assertEquals(1, connections.get());
      assertEquals(List.of(1L, 1L), attempts());
    }
  }

  @Test
  void replacementCancelsOnlyTheEarlierPendingTimer() {
    try (var coordinator = coordinator(policy(300, 1000, 0), sid -> successfulConnection())) {
      coordinator.schedule("core", "first");
      coordinator.schedule("core", "second");
      scheduler.advanceTimeBy(300, TimeUnit.MILLISECONDS);
      assertEquals(0, connections.get());
      scheduler.advanceTimeBy(300, TimeUnit.MILLISECONDS);
      assertEquals(1, connections.get());
      coordinator.reset("core");
      coordinator.schedule("core", "after success");
      assertEquals(List.of(1L, 2L, 1L), attempts());
    }
  }

  @Test
  void removingServerDuringDelayReportsCancellationWithoutConnecting() {
    try (var coordinator = coordinator(policy(300, 1000, 0), sid -> successfulConnection())) {
      coordinator.schedule("core", "EOF");
      serverExists.set(false);
      scheduler.advanceTimeBy(300, TimeUnit.MILLISECONDS);
      assertEquals(0, connections.get());
      assertEquals("Reconnect cancelled (server removed)", lastError());
    }
  }

  @Test
  void connectionCanCancelItsPendingTimerWithoutCancellingItself() {
    AtomicReference<QuasselCoreReconnectCoordinator> reference = new AtomicReference<>();
    AtomicBoolean completed = new AtomicBoolean();
    try (var coordinator =
        coordinator(
            policy(300, 1000, 0),
            sid ->
                Completable.fromAction(() -> reference.get().cancel(sid, false))
                    .andThen(Completable.timer(100, TimeUnit.MILLISECONDS, scheduler))
                    .doOnComplete(() -> completed.set(true)))) {
      reference.set(coordinator);
      coordinator.schedule("core", "EOF");
      scheduler.advanceTimeBy(400, TimeUnit.MILLISECONDS);
      assertTrue(completed.get());
    }
  }

  @Test
  void shutdownDisposesPendingAndInFlightReconnectsAndRejectsFurtherScheduling() {
    AtomicBoolean disposed = new AtomicBoolean();
    var coordinator =
        coordinator(
            policy(300, 1000, 0),
            sid ->
                Completable.never()
                    .doOnSubscribe(ignored -> connections.incrementAndGet())
                    .doOnDispose(() -> disposed.set(true)));
    coordinator.schedule("core", "EOF");
    scheduler.advanceTimeBy(300, TimeUnit.MILLISECONDS);
    coordinator.schedule("other", "EOF");
    coordinator.close();
    coordinator.close();
    coordinator.schedule("core", "after shutdown");
    scheduler.advanceTimeBy(10, TimeUnit.SECONDS);
    assertTrue(disposed.get());
    assertEquals(1, connections.get());
    assertEquals(2, delays().size());
  }

  @Test
  void reconnectObserverCanCancelBeforeTheTimerIsSubscribed() {
    AtomicReference<QuasselCoreReconnectCoordinator> reference = new AtomicReference<>();
    try (var coordinator =
        new QuasselCoreReconnectCoordinator(
            policy(300, 1000, 0),
            serverExistsPredicate -> true,
            sid -> successfulConnection(),
            event -> {
              events.add(event);
              reference.get().cancel(event.serverId(), true);
            },
            () -> scheduler)) {
      reference.set(coordinator);
      coordinator.schedule("core", "EOF");
      scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
      assertEquals(0, connections.get());
    }
  }

  @Test
  void disabledPolicyAndUnknownOrBlankServersDoNotScheduleReconnects() {
    for (IrcProperties.Reconnect policy :
        new IrcProperties.Reconnect[] {
          null, new IrcProperties.Reconnect(false, 300, 1000, 2, 0, 0), policy(300, 1000, 0)
        }) {
      try (var coordinator = coordinator(policy, sid -> successfulConnection())) {
        coordinator.schedule("", "EOF");
        serverExists.set(true);
        if (policy == null || !policy.enabled()) coordinator.schedule("core", "EOF");
        serverExists.set(false);
        coordinator.schedule("core", "EOF");
      }
    }
    scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
    assertEquals(0, connections.get());
    assertTrue(events.isEmpty());
  }

  private QuasselCoreReconnectCoordinator coordinator(
      IrcProperties.Reconnect policy, Function<String, Completable> connect) {
    return new QuasselCoreReconnectCoordinator(
        policy, sid -> serverExists.get(), connect, events::add, () -> scheduler);
  }

  private Completable failedConnection() {
    return Completable.defer(
        () -> {
          connections.incrementAndGet();
          return Completable.error(new IOException("unavailable"));
        });
  }

  private Completable successfulConnection() {
    return Completable.fromAction(connections::incrementAndGet);
  }

  private List<Long> delays() {
    return events.stream()
        .map(ServerIrcEvent::event)
        .filter(IrcEvent.Reconnecting.class::isInstance)
        .map(IrcEvent.Reconnecting.class::cast)
        .map(IrcEvent.Reconnecting::delayMs)
        .toList();
  }

  private List<Long> attempts() {
    return events.stream()
        .map(ServerIrcEvent::event)
        .filter(IrcEvent.Reconnecting.class::isInstance)
        .map(IrcEvent.Reconnecting.class::cast)
        .map(IrcEvent.Reconnecting::attempt)
        .toList();
  }

  private String lastError() {
    return ((IrcEvent.Error) events.getLast().event()).message();
  }

  private static IrcProperties.Reconnect policy(long initial, long max, int attempts) {
    return new IrcProperties.Reconnect(true, initial, max, 2, 0, attempts);
  }
}

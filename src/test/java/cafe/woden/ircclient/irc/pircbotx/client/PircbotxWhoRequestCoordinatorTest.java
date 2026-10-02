package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PircbotxWhoRequestCoordinatorTest {
  @Test
  void joinAndEnrichmentScansCoalesceAndWhoxSupersedesPendingPlainWho() {
    try (Fixture f = new Fixture()) {
      f.coordinator.enqueue("WHO #one");
      f.coordinator.enqueue("WHO #one %tcuhnaf,1");
      f.coordinator.enqueue("WHO #ONE");
      f.coordinator.enqueue("WHO #one %tcuhnaf,1");
      f.drain();
      assertEquals(List.of("WHO #one %tcuhnaf,1"), f.lines);
      f.coordinator.enqueue("WHO #one");
      f.coordinator.enqueue("WHO #one %tcuhnaf,1");
      assertTrue(f.tasks.isEmpty(), "recent equivalent scans should also be suppressed");
    }
  }

  @Test
  void scansAcrossChannelsShareThirtySecondSpacing() {
    try (Fixture f = new Fixture()) {
      f.coordinator.enqueue("WHO #one");
      f.coordinator.enqueue("WHO #two");
      f.coordinator.enqueue("WHO #three");
      f.drain();
      assertEquals(List.of(0L, seconds(30), seconds(60)), f.times);
    }
  }

  @Test
  void richerScanStillRunsAfterPlainWhoWasAlreadySent() {
    try (Fixture f = new Fixture()) {
      f.coordinator.enqueue("WHO #one");
      f.drain();
      f.coordinator.enqueue("WHO #one %tcuhnaf,1");
      f.drain();
      assertEquals(List.of("WHO #one", "WHO #one %tcuhnaf,1"), f.lines);
      assertEquals(List.of(0L, seconds(30)), f.times);
    }
  }

  @Test
  void explicitWhoSharesSpacingAndIsNeverCoalesced() {
    try (Fixture f = new Fixture()) {
      f.coordinator.enqueue("WHO #one");
      f.drain();
      for (int i = 0; i < 2; i++) {
        f.coordinator.send(
            () -> {
              f.record("@label=manual WHO #one");
              return true;
            });
      }
      f.coordinator.enqueue("WHO #two");
      f.drain();
      assertEquals(List.of(0L, seconds(30), seconds(60), seconds(90)), f.times);
      assertEquals(4, f.lines.size());
    }
  }

  @Test
  void rateLimitDefersQueuedWhoAndRepeatedErrorsExtendCooldown() {
    try (Fixture f = new Fixture()) {
      f.coordinator.enqueue("WHO #one");
      f.coordinator.rateLimited();
      f.clock.addAndGet(seconds(20));
      f.coordinator.rateLimited();
      f.drain();
      assertEquals(List.of(seconds(80)), f.times);
      f.coordinator.enqueue("WHO #two");
      f.drain();
      assertEquals(List.of(seconds(80), seconds(110)), f.times);
    }
  }

  @Test
  void backoffReceivedWhileAlreadyWaitingIsRechecked() {
    try (Fixture f = new Fixture()) {
      var holder = new PircbotxWhoRequestCoordinator[1];
      var extended = new java.util.concurrent.atomic.AtomicBoolean();
      PircbotxWhoRequestCoordinator coordinator =
          new PircbotxWhoRequestCoordinator(
              () -> true,
              f::record,
              f.executor,
              f.clock::get,
              nanos -> {
                f.clock.addAndGet(nanos);
                if (extended.compareAndSet(false, true)) holder[0].rateLimited();
              });
      holder[0] = coordinator;
      try {
        coordinator.send(
            () -> {
              f.record("WHO #one");
              return true;
            });
        coordinator.send(
            () -> {
              f.record("WHO #two");
              return true;
            });
        assertEquals(List.of(0L, seconds(90)), f.times);
      } finally {
        coordinator.close();
      }
    }
  }

  @Test
  void queueIsBoundedAndShutdownDiscardsPendingScans() {
    try (Fixture f = new Fixture()) {
      for (int i = 0; i < 2048; i++) f.coordinator.enqueue("WHO #chan" + i);
      assertEquals(1, f.tasks.size(), "only one drain task may be scheduled");
      f.drain();
      assertEquals(1024, f.lines.size());
      f.coordinator.enqueue("WHO #shutdown");
      f.coordinator.close();
      f.drain();
      assertEquals(1024, f.lines.size());
      verify(f.executor, atLeastOnce()).shutdownNow();
    }
  }

  @Test
  void disconnectedTransportDoesNotSendQueuedScans() {
    try (Fixture f = new Fixture()) {
      f.coordinator.enqueue("WHO #one");
      f.connected = false;
      f.drain();
      assertTrue(f.lines.isEmpty());
    }
  }

  private static long seconds(long seconds) {
    return TimeUnit.SECONDS.toNanos(seconds);
  }

  private static final class Fixture implements AutoCloseable {
    final AtomicLong clock = new AtomicLong();
    final List<String> lines = new ArrayList<>();
    final List<Long> times = new ArrayList<>();
    final List<Runnable> tasks = new ArrayList<>();
    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    final PircbotxWhoRequestCoordinator coordinator;
    boolean connected = true;

    Fixture() {
      when(executor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
          .thenAnswer(
              invocation -> {
                tasks.add(invocation.getArgument(0));
                return null;
              });
      coordinator =
          new PircbotxWhoRequestCoordinator(
              () -> connected, this::record, executor, clock::get, clock::addAndGet);
    }

    void record(String line) {
      lines.add(line);
      times.add(clock.get());
    }

    void drain() {
      tasks.removeFirst().run();
    }

    @Override
    public void close() {
      coordinator.close();
    }
  }
}

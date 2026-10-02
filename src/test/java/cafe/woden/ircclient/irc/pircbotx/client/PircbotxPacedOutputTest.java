package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.config.IrcProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;

class PircbotxPacedOutputTest {
  @Test
  void waitingTaggedWhoDoesNotDelayChatOrKeepalivesAndCloseCancelsIt() throws Exception {
    RecordingBot bot = new RecordingBot();
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(bot, new IrcProperties.FloodProtection(false, 100, 0));
    output.rawLine("WHO #one");
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch cancelled = new CountDownLatch(1);
    Thread pending =
        Thread.startVirtualThread(
            () -> {
              started.countDown();
              try {
                assertThrows(
                    IllegalStateException.class, () -> output.rawLine("@label=manual who #two"));
              } finally {
                cancelled.countDown();
              }
            });
    try {
      assertTrue(started.await(1, TimeUnit.SECONDS));
      assertTimeoutPreemptively(
          Duration.ofSeconds(1),
          () -> {
            output.rawLine("PRIVMSG #one :hello");
            output.rawLine("PONG keepalive");
          });
      assertEquals(List.of("WHO #one", "PRIVMSG #one :hello", "PONG keepalive"), bot.lines);
    } finally {
      output.close();
      pending.join(1000);
    }
    assertTrue(cancelled.await(1, TimeUnit.SECONDS));
    assertFalse(pending.isAlive());
    assertEquals(3, bot.lines.size());
  }

  @Test
  void automaticJoinQueryOverloadLeavesReaderFreeAndShutdownCancelsWaitingAndQueuedQueries()
      throws Exception {
    RecordingBot bot = new RecordingBot();
    CountDownLatch waiting = new CountDownLatch(1);
    CountDownLatch cancelled = new CountDownLatch(1);
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(
            bot,
            new IrcProperties.FloodProtection(10_000, 0),
            () -> 0L,
            nanos -> {
              waiting.countDown();
              try {
                new CountDownLatch(1).await();
              } finally {
                cancelled.countDown();
              }
            });
    try {
      output.rawLine("JOIN #one");
      output.rawLine("JOIN #two");
      assertTimeoutPreemptively(
          Duration.ofSeconds(1),
          () ->
              output.deferJoinQueries(
                  "#one",
                  () -> {
                    output.rawLine("WHO #one");
                    output.rawLine("MODE #one");
                  }));
      assertTrue(waiting.await(1, TimeUnit.SECONDS));
      assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () ->
              output.deferJoinQueries(
                  "#two",
                  () -> {
                    // Exceed the bounded queue while the worker is waiting for a flood credit.
                    for (int i = 0; i < 2048; i++) output.rawLine("WHO #two");
                  }));
      output.rawLine("PONG keepalive");
      assertEquals(List.of("JOIN #one", "JOIN #two", "PONG keepalive"), bot.lines);
    } finally {
      output.close();
    }
    assertTrue(cancelled.await(1, TimeUnit.SECONDS));
    assertEquals(3, bot.lines.size(), "shutdown must discard both waiting and queued queries");
  }

  @Test
  void twoCommandBurstRebuildsGraduallyAndIdleCreditIsCapped() {
    RecordingBot bot = new RecordingBot();
    AtomicLong clock = new AtomicLong();
    List<Long> waits = new ArrayList<>();
    long interval = TimeUnit.MILLISECONDS.toNanos(1500);
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(
            bot,
            IrcProperties.FloodProtection.defaults(),
            clock::get,
            nanos -> {
              waits.add(nanos);
              clock.addAndGet(nanos);
            });

    output.rawLine("@+typing=active TAGMSG #one");
    assertTrue(waits.isEmpty(), "first command must not wait");
    output.rawLine("PRIVMSG #one :hello");
    assertTrue(waits.isEmpty(), "typing and a message may share a two-command burst");

    clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(600));
    output.rawLine("NAMES #one");
    assertEquals(List.of(TimeUnit.MILLISECONDS.toNanos(900)), waits);
    output.rawLine("PRIVMSG #one :sustained traffic");
    assertEquals(List.of(TimeUnit.MILLISECONDS.toNanos(900), interval), waits);

    clock.addAndGet(10 * interval);
    waits.clear();
    output.rawLine("PRIVMSG #one :after idle");
    assertTrue(waits.isEmpty(), "first command after idle must not wait");
    output.rawLine("NAMES #one");
    assertTrue(waits.isEmpty(), "idle replenishes both burst credits");
    output.rawLine("JOIN #two");
    assertEquals(List.of(interval), waits, "idle must not bank more than two credits");
    assertEquals(7, bot.lines.size());
  }

  @Test
  void concurrentCommandsShareOneBurstAllowance() throws Exception {
    RecordingBot bot = new RecordingBot();
    AtomicLong clock = new AtomicLong();
    List<Long> waits = new CopyOnWriteArrayList<>();
    long interval = TimeUnit.MILLISECONDS.toNanos(1500);
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(
            bot,
            IrcProperties.FloodProtection.defaults(),
            clock::get,
            nanos -> {
              waits.add(nanos);
              clock.addAndGet(nanos);
            });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<?>> sends = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        String line = "PRIVMSG #one :message " + i;
        sends.add(
            executor.submit(
                () -> {
                  start.await();
                  output.rawLine(line);
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> send : sends) send.get(2, TimeUnit.SECONDS);
    }
    assertEquals(8, bot.lines.size());
    assertEquals(List.of(interval, interval, interval, interval, interval, interval), waits);
  }

  @Test
  void oversleepCannotRebuildMoreThanTwoBurstCredits() {
    RecordingBot bot = new RecordingBot();
    AtomicLong clock = new AtomicLong();
    List<Long> waits = new ArrayList<>();
    long interval = TimeUnit.MILLISECONDS.toNanos(100);
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(
            bot,
            new IrcProperties.FloodProtection(100, 0),
            clock::get,
            nanos -> {
              waits.add(nanos);
              clock.addAndGet(nanos + 3 * interval);
            });

    output.rawLine("PRIVMSG #one :first");
    output.rawLine("PRIVMSG #one :second");
    output.rawLine("PRIVMSG #one :third");
    output.rawLine("PRIVMSG #one :fourth");
    output.rawLine("PRIVMSG #one :fifth");
    assertEquals(List.of(interval, interval), waits);
    assertEquals(5, bot.lines.size());
  }

  @Test
  void ordinaryCommandsShareTheBurstBudgetAndThenWaitForCredit() throws Exception {
    RecordingBot bot = new RecordingBot();
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(bot, new IrcProperties.FloodProtection(100, 0));
    output.rawLine("JOIN #one");
    Thread.sleep(350);
    output.rawLine("PRIVMSG #one :hello");
    output.rawLine("NAMES #one");
    output.rawLine("JOIN #two");
    assertEquals(4, bot.lines.size());
    assertTrue(bot.times.get(3) - bot.times.get(1) >= TimeUnit.MILLISECONDS.toNanos(95));
  }

  @Test
  void keepalivesAndQuitBypassWaitingOrdinarySendsAndCancellationDropsPendingSend()
      throws Exception {
    RecordingBot bot = new RecordingBot();
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(bot, new IrcProperties.FloodProtection(10_000, 0));
    output.rawLine("JOIN #one");
    output.rawLine("JOIN #two");
    CountDownLatch started = new CountDownLatch(1);
    Thread pending =
        Thread.startVirtualThread(
            () -> {
              started.countDown();
              assertThrows(IllegalStateException.class, () -> output.rawLine("JOIN #three"));
            });
    try {
      assertTrue(started.await(1, TimeUnit.SECONDS));
      output.rawLine("PONG probe");
      output.rawLine("AUTHENTICATE +");
      output.rawLineNow("QUIT :bye");
      assertEquals(
          List.of("JOIN #one", "JOIN #two", "PONG probe", "AUTHENTICATE +", "QUIT :bye"),
          bot.lines);
    } finally {
      pending.interrupt();
      pending.join(1000);
    }
    assertFalse(pending.isAlive());
    assertEquals(5, bot.lines.size());
  }

  @Test
  void disconnectWhileWaitingPreventsFurtherWrites() throws Exception {
    RecordingBot bot = new RecordingBot();
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(bot, new IrcProperties.FloodProtection(100, 0));
    output.rawLine("JOIN #one");
    output.rawLine("JOIN #two");
    bot.connected = false;
    assertThrows(IllegalArgumentException.class, () -> output.rawLine("JOIN #three"));
    assertEquals(List.of("JOIN #one", "JOIN #two"), bot.lines);
  }

  @Test
  void disabledPacingBypassesBothLimitersAndStillValidatesSends() {
    RecordingBot bot = new RecordingBot();
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(bot, new IrcProperties.FloodProtection(false, 10_000, 0));
    assertTimeoutPreemptively(
        Duration.ofSeconds(1),
        () -> {
          for (int i = 0; i < 20; i++) output.rawLine("PRIVMSG #one :message " + i);
        });
    assertEquals(20, bot.lines.size());
    assertThrows(IllegalArgumentException.class, () -> output.rawLine(" "));
    bot.connected = false;
    assertThrows(IllegalArgumentException.class, () -> output.rawLine("JOIN #two"));
    assertEquals(20, bot.lines.size());
  }

  private static final class RecordingBot extends PircBotX {
    final List<String> lines = new CopyOnWriteArrayList<>();
    final List<Long> times = new CopyOnWriteArrayList<>();
    volatile boolean connected = true;

    RecordingBot() {
      super(
          new Configuration.Builder()
              .setName("probe")
              .addServer("localhost", 6667)
              .buildConfiguration());
    }

    @Override
    public boolean isConnected() {
      return connected;
    }

    @Override
    protected void sendRawLineToServer(String line) {
      lines.add(line);
      times.add(System.nanoTime());
    }
  }
}

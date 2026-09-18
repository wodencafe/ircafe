package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.config.IrcProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;

class PircbotxPacedOutputTest {
  @Test
  void ordinaryCommandsShareSpacingWithoutAccumulatingIdleBurstCredit() throws Exception {
    RecordingBot bot = new RecordingBot();
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(bot, new IrcProperties.FloodProtection(100, 0));
    output.rawLine("JOIN #one");
    Thread.sleep(350);
    output.rawLine("PRIVMSG #one :hello");
    output.rawLine("WHO #one");
    output.rawLine("JOIN #two");
    assertEquals(4, bot.lines.size());
    for (int i = 1; i < bot.times.size(); i++) {
      assertTrue(bot.times.get(i) - bot.times.get(i - 1) >= TimeUnit.MILLISECONDS.toNanos(95));
    }
  }

  @Test
  void keepalivesAndQuitBypassWaitingOrdinarySendsAndCancellationDropsPendingSend()
      throws Exception {
    RecordingBot bot = new RecordingBot();
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(bot, new IrcProperties.FloodProtection(10_000, 0));
    output.rawLine("JOIN #one");
    CountDownLatch started = new CountDownLatch(1);
    Thread pending =
        Thread.startVirtualThread(
            () -> {
              started.countDown();
              assertThrows(IllegalStateException.class, () -> output.rawLine("JOIN #two"));
            });
    try {
      assertTrue(started.await(1, TimeUnit.SECONDS));
      output.rawLine("PONG probe");
      output.rawLine("AUTHENTICATE +");
      output.rawLineNow("QUIT :bye");
      assertEquals(List.of("JOIN #one", "PONG probe", "AUTHENTICATE +", "QUIT :bye"), bot.lines);
    } finally {
      pending.interrupt();
      pending.join(1000);
    }
    assertFalse(pending.isAlive());
    assertEquals(4, bot.lines.size());
  }

  @Test
  void disconnectWhileWaitingPreventsFurtherWrites() throws Exception {
    RecordingBot bot = new RecordingBot();
    PircbotxPacedOutput output =
        new PircbotxPacedOutput(bot, new IrcProperties.FloodProtection(100, 0));
    output.rawLine("JOIN #one");
    bot.connected = false;
    assertThrows(IllegalArgumentException.class, () -> output.rawLine("JOIN #two"));
    assertEquals(List.of("JOIN #one"), bot.lines);
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

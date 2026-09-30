package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.properties.SojuProperties;
import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeTestFixtures;
import cafe.woden.ircclient.net.NetFloodProtectionContext;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.NoticeEvent;

class PircbotxFloodProtectionIntegrationTest {
  @Test
  void productionBotSendsUnpacedWhenExplicitlyDisabled() throws Exception {
    IrcProperties.FloodProtection previous = NetFloodProtectionContext.settings();
    NetFloodProtectionContext.configure(new IrcProperties.FloodProtection(false, 10_000, 0));
    try (ScriptedServer server = new ScriptedServer()) {
      PircBotX bot = bot(server.port(), new ListenerAdapter() {});
      assertEquals(0, bot.getConfiguration().getMessageDelay().getDelay());
      // Preferences are captured when building the connection, and take effect on reconnect.
      NetFloodProtectionContext.configure(IrcProperties.FloodProtection.defaults());
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread runner =
          Thread.startVirtualThread(
              () -> {
                try {
                  bot.startBot();
                } catch (Throwable e) {
                  failure.set(e);
                }
              });
      try {
        Sent first = server.joins.poll(10, TimeUnit.SECONDS);
        assertNotNull(first);
        assertEquals("JOIN #one", first.line().trim());
        for (String expected : List.of("JOIN #two secret-key", "JOIN #three", "JOIN #four")) {
          Sent next = server.joins.poll(1, TimeUnit.SECONDS);
          assertNotNull(next, "disabled rate limiting must not delay auto-joins");
          assertEquals(expected, next.line().trim());
        }
        assertTrue(server.pong.await(1, TimeUnit.SECONDS));
      } finally {
        bot.close();
        runner.join(3000);
      }
      assertFalse(runner.isAlive());
      assertNull(failure.get());
      server.runner.join(1000);
      assertNull(server.failure.get());
    } finally {
      NetFloodProtectionContext.configure(previous);
    }
  }

  @Test
  void productionBotReadsAndAnswersPingDuringPacedJoinsAndCancelsOnDisconnect() throws Exception {
    IrcProperties.FloodProtection previous = NetFloodProtectionContext.settings();
    NetFloodProtectionContext.configure(new IrcProperties.FloodProtection(2000, 300));
    try {
      // Each reconnect builds a fresh bot and must start a fresh, independently cancellable plan.
      for (int attempt = 0; attempt < 2; attempt++) {
        try (ScriptedServer server = new ScriptedServer()) {
          CountDownLatch notice = new CountDownLatch(1);
          PircBotX bot =
              bot(
                  server.port(),
                  new ListenerAdapter() {
                    @Override
                    public void onNotice(NoticeEvent event) {
                      if (event.getMessage().equals("reader-alive")) notice.countDown();
                    }
                  });
          assertTrue(
              bot.getConfiguration().getAutoJoinChannels().isEmpty(),
              "PircBotX must not auto-join synchronously inside its input parser");
          AtomicReference<Throwable> failure = new AtomicReference<>();
          Thread runner =
              Thread.startVirtualThread(
                  () -> {
                    try {
                      bot.startBot();
                    } catch (Throwable e) {
                      failure.set(e);
                    }
                  });
          try {
            Sent first = server.joins.poll(10, TimeUnit.SECONDS);
            assertNotNull(first);
            assertEquals("JOIN #one", first.line().trim());
            assertTrue(first.at() - server.welcomeAt >= TimeUnit.MILLISECONDS.toNanos(250));
            Sent second = server.joins.poll(1, TimeUnit.SECONDS);
            assertNotNull(second, "the initial two-command burst must not wait for refill");
            assertEquals("JOIN #two secret-key", second.line().trim());
            // PONG and inbound callbacks must complete while the exhausted burst budget refills.
            assertTrue(server.pong.await(1, TimeUnit.SECONDS));
            assertTrue(notice.await(1, TimeUnit.SECONDS));
            Sent third = server.joins.poll(5, TimeUnit.SECONDS);
            assertNotNull(third);
            assertEquals("JOIN #three", third.line().trim());
            assertTrue(third.at() - first.at() >= TimeUnit.MILLISECONDS.toNanos(1900));
          } finally {
            bot.close();
            runner.join(3000);
          }
          assertFalse(runner.isAlive());
          assertNull(failure.get());
          server.runner.join(1000);
          assertTrue(server.joins.isEmpty(), "disconnect must cancel unsent auto-joins");
          assertNull(server.failure.get());
        }
      }
    } finally {
      NetFloodProtectionContext.configure(previous);
    }
  }

  private static PircBotX bot(int port, ListenerAdapter listener) {
    ServerProxyResolver resolver = mock(ServerProxyResolver.class);
    when(resolver.planForServer("test"))
        .thenReturn(
            ProxyPlan.from(new IrcProperties.Proxy(false, "", 0, "", "", true, 2000, 10000)));
    var factory =
        new PircbotxBotFactory(
            resolver,
            new SojuProperties(Map.of(), null),
            null,
            Ircv3ExtensionCatalog.builtInCatalog(),
            Ircv3RuntimeTestFixtures.catalogs());
    var cfg =
        IrcPropertiesTestFixtures.serverBuilder("test")
            .host(InetAddress.getLoopbackAddress().getHostAddress())
            .port(port)
            .tls(false)
            .nick("probe")
            .login("probe")
            .sasl(new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null))
            .nickserv(new IrcProperties.Server.Nickserv(false, "", "", false))
            .autoJoin(List.of("#one", "#two secret-key", "#three", "#four"))
            .build();
    return factory.build(cfg, "test", listener);
  }

  private record Sent(String line, long at) {}

  private static final class ScriptedServer implements AutoCloseable {
    final ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    final BlockingQueue<Sent> joins = new LinkedBlockingQueue<>();
    final CountDownLatch pong = new CountDownLatch(1);
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    volatile long welcomeAt;
    volatile Socket socket;
    final Thread runner;

    ScriptedServer() throws IOException {
      runner = Thread.startVirtualThread(this::serve);
    }

    int port() {
      return listener.getLocalPort();
    }

    void serve() {
      try (Socket client = listener.accept()) {
        socket = client;
        client.setSoTimeout(15000);
        var reader =
            new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        var writer =
            new BufferedWriter(
                new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("CAP LS")) {
            send(writer, ":test CAP * LS :server-time");
          } else if (line.startsWith("CAP REQ :")) {
            send(writer, ":test CAP probe ACK :" + line.substring(9));
          } else if (line.equals("CAP END")) {
            welcomeAt = System.nanoTime();
            send(writer, ":test 001 probe :Welcome");
            send(writer, ":test 002 probe :Your host is test");
            send(writer, ":test 375 probe :- Test server MOTD");
            send(writer, ":test 372 probe :- Test network");
            send(writer, ":test 376 probe :End of MOTD");
          } else if (line.startsWith("JOIN ")) {
            joins.add(new Sent(line, System.nanoTime()));
            if (line.trim().equals("JOIN #one")) {
              send(writer, "PING :probe-keepalive");
              send(writer, ":test NOTICE probe :reader-alive");
            }
          } else if (line.contains("PONG") && line.contains("probe-keepalive")) {
            pong.countDown();
          }
        }
      } catch (SocketException ignored) {
        // Expected when closing the local fixture.
      } catch (Throwable e) {
        failure.set(e);
      }
    }

    private static void send(BufferedWriter writer, String line) throws IOException {
      writer.write(line + "\r\n");
      writer.flush();
    }

    @Override
    public void close() throws Exception {
      listener.close();
      if (socket != null) socket.close();
      runner.join(1000);
    }
  }
}

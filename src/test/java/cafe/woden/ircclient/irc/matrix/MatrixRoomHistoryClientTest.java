package cafe.woden.ircclient.irc.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MatrixRoomHistoryClientTest {

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixRoomHistoryClient historyClient = new MatrixRoomHistoryClient(proxyResolver);

  @Test
  void fetchMessagesBeforeParsesChunkEventsAndEndToken() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    String body =
        """
        {
          "start":"s72595_4483_1934",
          "end":"t34-23535_0_0",
          "chunk":[
            {
              "type":"m.room.message",
              "event_id":"$h1",
              "sender":"@alice:matrix.example.org",
              "origin_server_ts":1710000000000,
              "content":{"msgtype":"m.text","body":"hello"}
            },
            {
              "type":"m.room.message",
              "event_id":"$h2",
              "sender":"@alice:matrix.example.org",
              "origin_server_ts":1710000001000,
              "content":{"msgtype":"m.emote","body":"waves"}
            }
          ]
        }
        """;
    try (TestServer server = TestServer.start(200, body)) {
      MatrixRoomHistoryClient.HistoryResult result =
          historyClient.fetchMessagesBefore(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "s-anchor",
              50);

      assertTrue(result.success());
      assertEquals("t34-23535_0_0", result.endToken());
      assertEquals(2, result.events().size());
      assertEquals("@alice:matrix.example.org", result.events().get(0).sender());
      assertEquals("$h1", result.events().get(0).eventId());
      assertEquals("m.text", result.events().get(0).msgType());
      assertEquals("hello", result.events().get(0).body());
      assertEquals("Bearer secret-token", server.lastAuthorizationHeader().get());
      assertTrue(server.lastQuery().get().contains("from=s-anchor"));
      assertTrue(server.lastQuery().get().contains("dir=b"));
      assertTrue(server.lastQuery().get().contains("limit=50"));
    }
  }

  @Test
  void fetchMessagesAfterUsesForwardPaginationDirection() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    String body =
        """
        {
          "start":"s-forward",
          "end":"s-forward-next",
          "chunk":[
            {
              "type":"m.room.message",
              "event_id":"$f1",
              "sender":"@alice:matrix.example.org",
              "origin_server_ts":1710000010000,
              "content":{"msgtype":"m.text","body":"forward"}
            }
          ]
        }
        """;
    try (TestServer server = TestServer.start(200, body)) {
      MatrixRoomHistoryClient.HistoryResult result =
          historyClient.fetchMessagesAfter(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "s-anchor",
              25);

      assertTrue(result.success());
      assertEquals("s-forward-next", result.endToken());
      assertEquals(1, result.events().size());
      assertTrue(server.lastQuery().get().contains("from=s-anchor"));
      assertTrue(server.lastQuery().get().contains("dir=f"));
      assertTrue(server.lastQuery().get().contains("limit=25"));
    }
  }

  @Test
  void fetchMessagesBeforeReportsHttpFailure() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(500, "{\"errcode\":\"M_UNKNOWN\"}")) {
      MatrixRoomHistoryClient.HistoryResult result =
          historyClient.fetchMessagesBefore(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "s-anchor",
              50);

      assertFalse(result.success());
      assertEquals("HTTP 500 from room messages endpoint", result.detail());
      assertTrue(result.events().isEmpty());
    }
  }

  @Test
  void fetchMessagesBeforeRejectsBlankToken() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixRoomHistoryClient.HistoryResult result =
        historyClient.fetchMessagesBefore(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            " ",
            "!room:matrix.example.org",
            "s-anchor",
            50);

    assertFalse(result.success());
    assertEquals("access token is blank", result.detail());
    assertTrue(result.events().isEmpty());
  }

  private static ProxyPlan directPlan() {
    IrcProperties.Proxy cfg = new IrcProperties.Proxy(false, "", 0, "", "", true, 3_000, 3_000);
    return new ProxyPlan(cfg, Proxy.NO_PROXY, 3_000, 3_000);
  }

  private static IrcProperties.Server serverConfig(String id, String host, int port, boolean tls) {
    return new IrcProperties.Server(
        id,
        host,
        port,
        tls,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        null,
        List.of(),
        List.of(),
        null,
        IrcProperties.Server.Backend.MATRIX);
  }

  private record TestServer(
      HttpServer server,
      int port,
      AtomicReference<String> lastAuthorizationHeader,
      AtomicReference<String> lastQuery)
      implements AutoCloseable {

    static TestServer start(int statusCode, String body) throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicReference<String> auth = new AtomicReference<>("");
      AtomicReference<String> query = new AtomicReference<>("");
      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      server.createContext(
          "/_matrix/client/v3/rooms/",
          exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            query.set(exchange.getRequestURI().getRawQuery());
            reply(exchange, statusCode, payload);
          });
      server.start();
      return new TestServer(server, server.getAddress().getPort(), auth, query);
    }

    @Override
    public void close() {
      server.stop(0);
    }

    private static void reply(HttpExchange exchange, int statusCode, byte[] payload)
        throws IOException {
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(statusCode, payload.length);
      exchange.getResponseBody().write(payload);
      exchange.close();
    }
  }
}

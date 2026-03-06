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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MatrixRoomStateClientTest {

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixRoomStateClient roomStateClient = new MatrixRoomStateClient(proxyResolver);

  @Test
  void fetchRoomTopicReadsTopicFromStateEvent() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(
            200,
            "{\"topic\":\"hello matrix\"}",
            200,
            "{\"users_default\":0,\"users\":{\"@alice:matrix.example.org\":50}}")) {
      MatrixRoomStateClient.TopicResult result =
          roomStateClient.fetchRoomTopic(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org");

      assertTrue(result.success());
      assertEquals("hello matrix", result.topic());
      assertEquals(
          "/_matrix/client/v3/rooms/!room:matrix.example.org/state/m.room.topic",
          server.lastPath().get());
      assertEquals("GET", server.lastMethod().get());
      assertEquals("Bearer secret-token", server.lastAuthorization().get());
    }
  }

  @Test
  void updateRoomTopicPutsTopicPayload() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(200, "{\"topic\":\"x\"}", 200, "{}")) {
      MatrixRoomStateClient.UpdateResult result =
          roomStateClient.updateRoomTopic(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "new topic");

      assertTrue(result.updated());
      assertEquals("PUT", server.lastMethod().get());
      assertEquals(
          "/_matrix/client/v3/rooms/!room:matrix.example.org/state/m.room.topic",
          server.lastPath().get());
      assertTrue(server.lastBody().get().contains("\"topic\":\"new topic\""));
    }
  }

  @Test
  void fetchPowerLevelsParsesUsersAndDefaults() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(
            200,
            "{\"topic\":\"x\"}",
            200,
            "{\"users_default\":0,\"users\":{\"@alice:matrix.example.org\":50,\"@bob:matrix.example.org\":10}}")) {
      MatrixRoomStateClient.PowerLevelsResult result =
          roomStateClient.fetchRoomPowerLevels(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org");

      assertTrue(result.success());
      assertEquals("GET", server.lastMethod().get());
      assertEquals(
          "/_matrix/client/v3/rooms/!room:matrix.example.org/state/m.room.power_levels",
          server.lastPath().get());
      assertEquals(0L, result.content().get("users_default"));
      Object users = result.content().get("users");
      assertTrue(users instanceof Map<?, ?>);
      Map<?, ?> usersMap = (Map<?, ?>) users;
      assertEquals(50L, usersMap.get("@alice:matrix.example.org"));
      assertEquals(10L, usersMap.get("@bob:matrix.example.org"));
    }
  }

  @Test
  void updatePowerLevelsRejectsBlankToken() {
    MatrixRoomStateClient.UpdateResult result =
        roomStateClient.updateRoomPowerLevels(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            " ",
            "!room:matrix.example.org",
            Map.of("users_default", 0));

    assertFalse(result.updated());
    assertEquals("access token is blank", result.detail());
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
      AtomicReference<String> lastMethod,
      AtomicReference<String> lastPath,
      AtomicReference<String> lastAuthorization,
      AtomicReference<String> lastBody)
      implements AutoCloseable {

    static TestServer start(int topicStatus, String topicBody, int powerStatus, String powerBody)
        throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicReference<String> method = new AtomicReference<>("");
      AtomicReference<String> path = new AtomicReference<>("");
      AtomicReference<String> auth = new AtomicReference<>("");
      AtomicReference<String> body = new AtomicReference<>("");

      server.createContext(
          "/_matrix/client/v3/rooms/",
          exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getRawPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(readBody(exchange));

            String reqPath = path.get();
            if (reqPath.endsWith("/state/m.room.topic")) {
              reply(exchange, topicStatus, topicBody.getBytes(StandardCharsets.UTF_8));
              return;
            }
            if (reqPath.endsWith("/state/m.room.power_levels")) {
              reply(exchange, powerStatus, powerBody.getBytes(StandardCharsets.UTF_8));
              return;
            }
            reply(exchange, 404, "{}".getBytes(StandardCharsets.UTF_8));
          });

      server.start();
      return new TestServer(server, server.getAddress().getPort(), method, path, auth, body);
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

    private static String readBody(HttpExchange exchange) throws IOException {
      byte[] raw = exchange.getRequestBody().readAllBytes();
      if (raw == null || raw.length == 0) return "";
      return new String(raw, StandardCharsets.UTF_8);
    }
  }
}

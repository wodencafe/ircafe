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

class MatrixRoomDirectoryClientTest {

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixRoomDirectoryClient directoryClient =
      new MatrixRoomDirectoryClient(proxyResolver);

  @Test
  void resolveRoomAliasParsesRoomIdFromDirectoryResponse() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(
            "/_matrix/client/v3/directory/room/",
            200,
            "{\"room_id\":\"!room:matrix.example.org\"}")) {
      MatrixRoomDirectoryClient.ResolveResult result =
          directoryClient.resolveRoomAlias(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "#ircafe:matrix.example.org");

      assertTrue(result.resolved());
      assertEquals("!room:matrix.example.org", result.roomId());
      assertEquals("Bearer secret-token", server.lastAuthorizationHeader().get());
      assertEquals(
          "/_matrix/client/v3/directory/room/%23ircafe:matrix.example.org",
          server.lastPath().get());
    }
  }

  @Test
  void resolveRoomAliasReportsHttpFailure() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start("/_matrix/client/v3/directory/room/", 404, "{\"errcode\":\"M_NOT_FOUND\"}")) {
      MatrixRoomDirectoryClient.ResolveResult result =
          directoryClient.resolveRoomAlias(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "#ircafe:matrix.example.org");

      assertFalse(result.resolved());
      assertEquals("HTTP 404 from room directory endpoint", result.detail());
      assertEquals("", result.roomId());
    }
  }

  @Test
  void resolveRoomAliasRejectsBlankToken() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixRoomDirectoryClient.ResolveResult result =
        directoryClient.resolveRoomAlias(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            " ",
            "#ircafe:matrix.example.org");

    assertFalse(result.resolved());
    assertEquals("access token is blank", result.detail());
  }

  @Test
  void fetchPublicRoomsParsesChunkAndPagination() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    String body =
        """
        {
          "chunk":[
            {"room_id":"!a:matrix.example.org","canonical_alias":"#a:matrix.example.org","topic":"Topic A","num_joined_members":42},
            {"room_id":"!b:matrix.example.org","name":"Room B","num_joined_members":8}
          ],
          "next_batch":"s-next"
        }
        """;
    try (TestServer server = TestServer.start("/_matrix/client/v3/publicRooms", 200, body)) {
      MatrixRoomDirectoryClient.PublicRoomsResult result =
          directoryClient.fetchPublicRooms(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "irc",
              "s-prev",
              25);

      assertTrue(result.success());
      assertEquals("s-next", result.nextBatch());
      assertEquals(2, result.rooms().size());
      assertEquals("#a:matrix.example.org", result.rooms().get(0).canonicalAlias());
      assertEquals("Topic A", result.rooms().get(0).topic());
      assertEquals(42, result.rooms().get(0).joinedMembers());
      assertEquals("!b:matrix.example.org", result.rooms().get(1).roomId());
      assertEquals("Room B", result.rooms().get(1).name());
      assertEquals("Bearer secret-token", server.lastAuthorizationHeader().get());
      assertEquals("/_matrix/client/v3/publicRooms", server.lastPath().get());
      assertTrue(server.lastRequestBody().get().contains("\"generic_search_term\":\"irc\""));
      assertTrue(server.lastRequestBody().get().contains("\"since\":\"s-prev\""));
      assertTrue(server.lastRequestBody().get().contains("\"limit\":25"));
    }
  }

  @Test
  void fetchPublicRoomsRejectsBlankToken() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());

    MatrixRoomDirectoryClient.PublicRoomsResult result =
        directoryClient.fetchPublicRooms(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            " ",
            "irc",
            "",
            100);

    assertFalse(result.success());
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
      AtomicReference<String> lastPath,
      AtomicReference<String> lastAuthorizationHeader,
      AtomicReference<String> lastRequestBody)
      implements AutoCloseable {

    static TestServer start(String contextPath, int statusCode, String body) throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicReference<String> path = new AtomicReference<>("");
      AtomicReference<String> auth = new AtomicReference<>("");
      AtomicReference<String> requestBody = new AtomicReference<>("");
      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      server.createContext(
          contextPath,
          exchange -> {
            path.set(exchange.getRequestURI().getRawPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, statusCode, payload);
          });
      server.start();
      return new TestServer(server, server.getAddress().getPort(), path, auth, requestBody);
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

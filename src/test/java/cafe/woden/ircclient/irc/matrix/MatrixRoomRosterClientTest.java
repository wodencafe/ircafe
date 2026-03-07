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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MatrixRoomRosterClientTest {

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixRoomRosterClient rosterClient = new MatrixRoomRosterClient(proxyResolver);

  @Test
  void fetchJoinedMembersParsesMembersFromResponse() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(
            200,
            """
            {
              "joined": {
                "@alice:matrix.example.org": { "display_name": "Alice" },
                "@bob:matrix.example.org": {}
              }
            }
            """)) {
      MatrixRoomRosterClient.RosterResult result =
          rosterClient.fetchJoinedMembers(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org");

      assertTrue(result.success());
      assertEquals(2, result.members().size());
      Map<String, String> displayNameByUserId =
          result.members().stream()
              .collect(
                  Collectors.toMap(
                      MatrixRoomRosterClient.JoinedMember::userId,
                      MatrixRoomRosterClient.JoinedMember::displayName));
      assertEquals("Alice", displayNameByUserId.get("@alice:matrix.example.org"));
      assertEquals("", displayNameByUserId.get("@bob:matrix.example.org"));
      assertEquals("Bearer secret-token", server.lastAuthorizationHeader().get());
      assertEquals(
          "/_matrix/client/v3/rooms/!room:matrix.example.org/joined_members",
          server.lastPath().get());
    }
  }

  @Test
  void fetchJoinedMembersReportsHttpFailure() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(403, "{\"errcode\":\"M_FORBIDDEN\"}")) {
      MatrixRoomRosterClient.RosterResult result =
          rosterClient.fetchJoinedMembers(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org");

      assertFalse(result.success());
      assertEquals("HTTP 403 from joined members endpoint", result.detail());
      assertEquals(List.of(), result.members());
    }
  }

  @Test
  void fetchJoinedMembersRejectsBlankToken() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixRoomRosterClient.RosterResult result =
        rosterClient.fetchJoinedMembers(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            " ",
            "!room:matrix.example.org");

    assertFalse(result.success());
    assertEquals("access token is blank", result.detail());
    assertEquals(List.of(), result.members());
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
      AtomicReference<String> lastAuthorizationHeader)
      implements AutoCloseable {

    static TestServer start(int statusCode, String body) throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicReference<String> path = new AtomicReference<>("");
      AtomicReference<String> auth = new AtomicReference<>("");
      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      server.createContext(
          "/_matrix/client/v3/rooms/",
          exchange -> {
            path.set(exchange.getRequestURI().getRawPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, statusCode, payload);
          });
      server.start();
      return new TestServer(server, server.getAddress().getPort(), path, auth);
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

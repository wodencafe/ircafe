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

class MatrixRoomMembershipClientTest {

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixRoomMembershipClient membershipClient =
      new MatrixRoomMembershipClient(proxyResolver);

  @Test
  void joinRoomPostsRequestAndParsesRoomId() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(200, "{\"room_id\":\"!joined:matrix.example.org\"}", 200, "{}")) {
      MatrixRoomMembershipClient.JoinResult result =
          membershipClient.joinRoom(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "#ircafe:matrix.example.org");

      assertTrue(result.joined());
      assertEquals("!joined:matrix.example.org", result.roomId());
      assertEquals("POST", server.lastJoinMethod().get());
      assertEquals("Bearer secret-token", server.lastJoinAuthorizationHeader().get());
      assertEquals(
          "/_matrix/client/v3/join/%23ircafe:matrix.example.org", server.lastJoinPath().get());
    }
  }

  @Test
  void leaveRoomPostsRequestAndReportsSuccess() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(200, "{\"room_id\":\"!joined:matrix.example.org\"}", 200, "{}")) {
      MatrixRoomMembershipClient.LeaveResult result =
          membershipClient.leaveRoom(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!joined:matrix.example.org");

      assertTrue(result.left());
      assertEquals("POST", server.lastLeaveMethod().get());
      assertEquals("Bearer secret-token", server.lastLeaveAuthorizationHeader().get());
      assertEquals(
          "/_matrix/client/v3/rooms/!joined:matrix.example.org/leave",
          server.lastLeavePath().get());
    }
  }

  @Test
  void joinRoomReportsHttpFailure() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(403, "{\"errcode\":\"M_FORBIDDEN\"}", 200, "{}")) {
      MatrixRoomMembershipClient.JoinResult result =
          membershipClient.joinRoom(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org");

      assertFalse(result.joined());
      assertEquals("HTTP 403 from join endpoint", result.detail());
    }
  }

  @Test
  void leaveRoomRejectsBlankToken() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixRoomMembershipClient.LeaveResult result =
        membershipClient.leaveRoom(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            " ",
            "!room:matrix.example.org");

    assertFalse(result.left());
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
      AtomicReference<String> lastJoinMethod,
      AtomicReference<String> lastJoinPath,
      AtomicReference<String> lastJoinAuthorizationHeader,
      AtomicReference<String> lastLeaveMethod,
      AtomicReference<String> lastLeavePath,
      AtomicReference<String> lastLeaveAuthorizationHeader)
      implements AutoCloseable {

    static TestServer start(int joinStatus, String joinBody, int leaveStatus, String leaveBody)
        throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicReference<String> joinMethod = new AtomicReference<>("");
      AtomicReference<String> joinPath = new AtomicReference<>("");
      AtomicReference<String> joinAuth = new AtomicReference<>("");
      AtomicReference<String> leaveMethod = new AtomicReference<>("");
      AtomicReference<String> leavePath = new AtomicReference<>("");
      AtomicReference<String> leaveAuth = new AtomicReference<>("");

      server.createContext(
          "/_matrix/client/v3/join/",
          exchange -> {
            joinMethod.set(exchange.getRequestMethod());
            joinPath.set(exchange.getRequestURI().getRawPath());
            joinAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, joinStatus, joinBody.getBytes(StandardCharsets.UTF_8));
          });

      server.createContext(
          "/_matrix/client/v3/rooms/",
          exchange -> {
            leaveMethod.set(exchange.getRequestMethod());
            leavePath.set(exchange.getRequestURI().getRawPath());
            leaveAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, leaveStatus, leaveBody.getBytes(StandardCharsets.UTF_8));
          });
      server.start();
      return new TestServer(
          server,
          server.getAddress().getPort(),
          joinMethod,
          joinPath,
          joinAuth,
          leaveMethod,
          leavePath,
          leaveAuth);
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

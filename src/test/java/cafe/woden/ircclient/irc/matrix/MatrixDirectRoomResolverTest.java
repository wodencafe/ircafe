package cafe.woden.ircclient.irc.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MatrixDirectRoomResolverTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixDirectRoomResolver resolver = new MatrixDirectRoomResolver(proxyResolver);

  @Test
  void resolveDirectRoomPostsCreateRoomPayloadAndParsesRoomId() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(200, "{\"room_id\":\"!dm:matrix.example.org\"}")) {
      MatrixDirectRoomResolver.ResolveResult result =
          resolver.resolveDirectRoom(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "@bob:matrix.example.org");

      assertTrue(result.resolved());
      assertEquals("!dm:matrix.example.org", result.roomId());
      assertEquals("POST", server.lastMethod().get());
      assertEquals("Bearer secret-token", server.lastAuthorizationHeader().get());
      assertEquals("/_matrix/client/v3/createRoom", server.lastPath().get());

      JsonNode body = JSON.readTree(server.lastRequestBody().get());
      assertTrue(body.path("is_direct").asBoolean(false));
      assertEquals("trusted_private_chat", body.path("preset").asText(""));
      assertEquals("@bob:matrix.example.org", body.path("invite").path(0).asText(""));
    }
  }

  @Test
  void resolveDirectRoomReportsHttpErrors() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(403, "{\"errcode\":\"M_FORBIDDEN\"}")) {
      MatrixDirectRoomResolver.ResolveResult result =
          resolver.resolveDirectRoom(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "@bob:matrix.example.org");

      assertFalse(result.resolved());
      assertEquals("HTTP 403 from createRoom endpoint", result.detail());
      assertEquals("", result.roomId());
    }
  }

  @Test
  void resolveDirectRoomRejectsBlankAccessToken() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixDirectRoomResolver.ResolveResult result =
        resolver.resolveDirectRoom(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            "  ",
            "@bob:matrix.example.org");

    assertFalse(result.resolved());
    assertEquals("access token is blank", result.detail());
  }

  @Test
  void resolveDirectRoomRejectsInvalidTarget() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixDirectRoomResolver.ResolveResult result =
        resolver.resolveDirectRoom(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            "secret-token",
            "bob");

    assertFalse(result.resolved());
    assertEquals("target is not a Matrix user id", result.detail());
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
      AtomicReference<String> lastAuthorizationHeader,
      AtomicReference<String> lastRequestBody)
      implements AutoCloseable {

    static TestServer start(int statusCode, String body) throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicReference<String> method = new AtomicReference<>("");
      AtomicReference<String> path = new AtomicReference<>("");
      AtomicReference<String> auth = new AtomicReference<>("");
      AtomicReference<String> requestBody = new AtomicReference<>("");
      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      server.createContext(
          "/_matrix/client/v3/createRoom",
          exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getRawPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, statusCode, payload);
          });
      server.start();
      return new TestServer(server, server.getAddress().getPort(), method, path, auth, requestBody);
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

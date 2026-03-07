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

class MatrixHomeserverProbeTest {

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixHomeserverProbe probe = new MatrixHomeserverProbe(proxyResolver);

  @Test
  void probeReportsReachableWhenVersionsEndpointReturnsJsonArray() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(
            response(200, "{\"versions\":[\"r0.6.1\",\"v1.1\"],\"unstable_features\":{}}"),
            response(200, "{\"user_id\":\"@alice:matrix.example.org\",\"device_id\":\"DEV1\"}"))) {
      MatrixHomeserverProbe.ProbeResult result =
          probe.probe("matrix", serverConfig("matrix", "127.0.0.1", server.port(), false));

      assertTrue(result.reachable());
      assertEquals(2, result.advertisedVersionCount());
      assertEquals(
          "http://127.0.0.1:" + server.port() + "/_matrix/client/versions",
          result.endpoint().toString());
    }
  }

  @Test
  void probeReportsFailureWhenVersionsEndpointReturnsHttpError() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(
            response(502, "{\"error\":\"bad gateway\"}"),
            response(200, "{\"user_id\":\"@alice:matrix.example.org\"}"))) {
      MatrixHomeserverProbe.ProbeResult result =
          probe.probe("matrix", serverConfig("matrix", "127.0.0.1", server.port(), false));

      assertFalse(result.reachable());
      assertEquals("HTTP 502 from versions endpoint", result.detail());
    }
  }

  @Test
  void probeReportsFailureWhenVersionsEndpointBodyLacksVersionsArray() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(
            response(200, "{\"unstable_features\":{}}"),
            response(200, "{\"user_id\":\"@alice:matrix.example.org\"}"))) {
      MatrixHomeserverProbe.ProbeResult result =
          probe.probe("matrix", serverConfig("matrix", "127.0.0.1", server.port(), false));

      assertFalse(result.reachable());
      assertEquals(
          "versions endpoint response did not include a non-empty versions array", result.detail());
    }
  }

  @Test
  void whoamiReportsAuthenticatedWhenEndpointReturnsUserId() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(
            response(200, "{\"versions\":[\"v1.11\"]}"),
            response(200, "{\"user_id\":\"@alice:matrix.example.org\",\"device_id\":\"DEV1\"}"))) {
      MatrixHomeserverProbe.WhoamiResult result =
          probe.whoami(
              "matrix", serverConfig("matrix", "127.0.0.1", server.port(), false), "secret-token");

      assertTrue(result.authenticated());
      assertEquals("@alice:matrix.example.org", result.userId());
      assertEquals("DEV1", result.deviceId());
      assertEquals("Bearer secret-token", server.lastAuthorizationHeader().get());
      assertEquals(
          "http://127.0.0.1:" + server.port() + "/_matrix/client/v3/account/whoami",
          result.endpoint().toString());
    }
  }

  @Test
  void whoamiReportsFailureWhenEndpointReturnsHttpError() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(
            response(200, "{\"versions\":[\"v1.11\"]}"),
            response(401, "{\"errcode\":\"M_UNKNOWN_TOKEN\"}"))) {
      MatrixHomeserverProbe.WhoamiResult result =
          probe.whoami(
              "matrix", serverConfig("matrix", "127.0.0.1", server.port(), false), "secret-token");

      assertFalse(result.authenticated());
      assertEquals("HTTP 401 from whoami endpoint", result.detail());
    }
  }

  @Test
  void whoamiReportsFailureWhenUserIdMissing() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(
            response(200, "{\"versions\":[\"v1.11\"]}"),
            response(200, "{\"device_id\":\"DEV1\"}"))) {
      MatrixHomeserverProbe.WhoamiResult result =
          probe.whoami(
              "matrix", serverConfig("matrix", "127.0.0.1", server.port(), false), "secret-token");

      assertFalse(result.authenticated());
      assertEquals("whoami response did not include user_id", result.detail());
    }
  }

  @Test
  void whoamiRejectsBlankToken() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixHomeserverProbe.WhoamiResult result =
        probe.whoami("matrix", serverConfig("matrix", "matrix.example.org", 8448, true), "  ");

    assertFalse(result.authenticated());
    assertEquals("access token is blank", result.detail());
    assertEquals(
        "https://matrix.example.org:8448/_matrix/client/v3/account/whoami",
        result.endpoint().toString());
  }

  private static ResponseSpec response(int statusCode, String body) {
    return new ResponseSpec(statusCode, body);
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
      HttpServer server, int port, AtomicReference<String> lastAuthorizationHeader)
      implements AutoCloseable {

    static TestServer start(ResponseSpec versions, ResponseSpec whoami) throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicReference<String> authHeader = new AtomicReference<>("");
      ResponseSpec versionsResponse = versions == null ? response(404, "{}") : versions;
      ResponseSpec whoamiResponse = whoami == null ? response(404, "{}") : whoami;

      server.createContext(
          "/_matrix/client/versions",
          exchange ->
              reply(
                  exchange,
                  versionsResponse.statusCode(),
                  versionsResponse.body().getBytes(StandardCharsets.UTF_8)));
      server.createContext(
          "/_matrix/client/v3/account/whoami",
          exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(
                exchange,
                whoamiResponse.statusCode(),
                whoamiResponse.body().getBytes(StandardCharsets.UTF_8));
          });
      server.start();
      return new TestServer(server, server.getAddress().getPort(), authHeader);
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

  private record ResponseSpec(int statusCode, String body) {}
}

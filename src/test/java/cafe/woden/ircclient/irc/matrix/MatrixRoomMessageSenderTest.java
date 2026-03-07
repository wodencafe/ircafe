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

class MatrixRoomMessageSenderTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixRoomMessageSender sender = new MatrixRoomMessageSender(proxyResolver);

  @Test
  void sendRoomMessagePutsJsonBodyAndAuthorizationHeader() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(200, "{\"event_id\":\"$abc\"}")) {
      MatrixRoomMessageSender.SendResult result =
          sender.sendRoomMessage(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "txn-1",
              "hello matrix");

      assertTrue(result.accepted());
      assertEquals("$abc", result.eventId());
      assertEquals("PUT", server.lastMethod().get());
      assertEquals("Bearer secret-token", server.lastAuthorizationHeader().get());
      assertEquals(
          "/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-1",
          server.lastPath().get());

      JsonNode body = JSON.readTree(server.lastRequestBody().get());
      assertEquals("m.text", body.path("msgtype").asText(""));
      assertEquals("hello matrix", body.path("body").asText(""));
    }
  }

  @Test
  void sendRoomMessageMapsCtcpActionToMatrixEmote() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(200, "{\"event_id\":\"$emote\"}")) {
      MatrixRoomMessageSender.SendResult result =
          sender.sendRoomMessage(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "txn-2",
              "\u0001ACTION waves\u0001");

      assertTrue(result.accepted());
      JsonNode body = JSON.readTree(server.lastRequestBody().get());
      assertEquals("m.emote", body.path("msgtype").asText(""));
      assertEquals("waves", body.path("body").asText(""));
    }
  }

  @Test
  void sendRoomNoticeUsesMatrixNoticeMsgtype() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(200, "{\"event_id\":\"$notice\"}")) {
      MatrixRoomMessageSender.SendResult result =
          sender.sendRoomNotice(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "txn-n1",
              "maintenance notice");

      assertTrue(result.accepted());
      assertEquals("$notice", result.eventId());
      JsonNode body = JSON.readTree(server.lastRequestBody().get());
      assertEquals("m.notice", body.path("msgtype").asText(""));
      assertEquals("maintenance notice", body.path("body").asText(""));
    }
  }

  @Test
  void sendRoomMediaMessageUsesProvidedMsgtypeAndMediaUrl() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(200, "{\"event_id\":\"$media\"}")) {
      MatrixRoomMessageSender.SendResult result =
          sender.sendRoomMediaMessage(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "txn-m1",
              "image.png",
              "m.image",
              "mxc://matrix.example.org/media-1");

      assertTrue(result.accepted());
      assertEquals("$media", result.eventId());
      JsonNode body = JSON.readTree(server.lastRequestBody().get());
      assertEquals("m.image", body.path("msgtype").asText(""));
      assertEquals("image.png", body.path("body").asText(""));
      assertEquals("mxc://matrix.example.org/media-1", body.path("url").asText(""));
    }
  }

  @Test
  void sendRoomMediaMessageRejectsUnsupportedMediaMsgtype() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixRoomMessageSender.SendResult result =
        sender.sendRoomMediaMessage(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            "secret-token",
            "!room:matrix.example.org",
            "txn-m2",
            "payload",
            "m.text",
            "mxc://matrix.example.org/media-2");

    assertFalse(result.accepted());
    assertEquals("unsupported media msgtype", result.detail());
  }

  @Test
  void sendRoomMediaMessageRejectsBlankMediaUrl() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixRoomMessageSender.SendResult result =
        sender.sendRoomMediaMessage(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            "secret-token",
            "!room:matrix.example.org",
            "txn-m3",
            "payload",
            "m.image",
            " ");

    assertFalse(result.accepted());
    assertEquals("media url is blank", result.detail());
  }

  @Test
  void sendRoomReplyIncludesReplyRelationPayload() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(200, "{\"event_id\":\"$reply\"}")) {
      MatrixRoomMessageSender.SendResult result =
          sender.sendRoomReply(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "txn-r1",
              "$target",
              "reply body");

      assertTrue(result.accepted());
      assertEquals("$reply", result.eventId());
      JsonNode body = JSON.readTree(server.lastRequestBody().get());
      assertEquals("m.text", body.path("msgtype").asText(""));
      assertEquals("reply body", body.path("body").asText(""));
      assertEquals(
          "$target", body.path("m.relates_to").path("m.in_reply_to").path("event_id").asText(""));
    }
  }

  @Test
  void sendRoomEditIncludesReplaceRelationPayload() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(200, "{\"event_id\":\"$edit\"}")) {
      MatrixRoomMessageSender.SendResult result =
          sender.sendRoomEdit(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "txn-e1",
              "$target",
              "edited body");

      assertTrue(result.accepted());
      assertEquals("$edit", result.eventId());
      JsonNode body = JSON.readTree(server.lastRequestBody().get());
      assertEquals("m.text", body.path("msgtype").asText(""));
      assertEquals("edited body", body.path("body").asText(""));
      assertEquals("m.replace", body.path("m.relates_to").path("rel_type").asText(""));
      assertEquals("$target", body.path("m.relates_to").path("event_id").asText(""));
      assertEquals("edited body", body.path("m.new_content").path("body").asText(""));
    }
  }

  @Test
  void sendRoomReactionUsesReactionEventTypePath() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(200, "{\"event_id\":\"$react\"}")) {
      MatrixRoomMessageSender.SendResult result =
          sender.sendRoomReaction(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "txn-re1",
              "$target",
              ":+1:");

      assertTrue(result.accepted());
      assertEquals("$react", result.eventId());
      assertEquals(
          "/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.reaction/txn-re1",
          server.lastPath().get());
      JsonNode body = JSON.readTree(server.lastRequestBody().get());
      assertEquals("m.annotation", body.path("m.relates_to").path("rel_type").asText(""));
      assertEquals("$target", body.path("m.relates_to").path("event_id").asText(""));
      assertEquals(":+1:", body.path("m.relates_to").path("key").asText(""));
    }
  }

  @Test
  void sendRoomRedactionUsesRedactionEndpointPath() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(200, "{\"event_id\":\"$redact\"}")) {
      MatrixRoomMessageSender.SendResult result =
          sender.sendRoomRedaction(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "$target",
              "txn-rd1",
              "cleanup");

      assertTrue(result.accepted());
      assertEquals("$redact", result.eventId());
      assertEquals(
          "/_matrix/client/v3/rooms/!room:matrix.example.org/redact/$target/txn-rd1",
          server.lastPath().get());
      JsonNode body = JSON.readTree(server.lastRequestBody().get());
      assertEquals("cleanup", body.path("reason").asText(""));
    }
  }

  @Test
  void sendRoomMessageReportsHttpErrors() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(403, "{\"errcode\":\"M_FORBIDDEN\"}")) {
      MatrixRoomMessageSender.SendResult result =
          sender.sendRoomMessage(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "!room:matrix.example.org",
              "txn-3",
              "hello matrix");

      assertFalse(result.accepted());
      assertEquals("HTTP 403 from room send endpoint", result.detail());
      assertEquals("", result.eventId());
    }
  }

  @Test
  void sendRoomMessageRejectsBlankToken() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixRoomMessageSender.SendResult result =
        sender.sendRoomMessage(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            "  ",
            "!room:matrix.example.org",
            "txn-4",
            "hello matrix");

    assertFalse(result.accepted());
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
          "/_matrix/client/v3/rooms/",
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

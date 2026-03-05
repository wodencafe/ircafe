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

class MatrixSyncClientTest {

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixSyncClient syncClient = new MatrixSyncClient(proxyResolver);

  @Test
  void syncParsesRoomTimelineMessagesAndNextBatch() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    String body =
        """
        {
          "next_batch":"s72595_4483_1934",
          "account_data":{
            "events":[
              {
                "type":"m.direct",
                "content":{
                  "@bob:matrix.example.org":["!room:matrix.example.org"]
                }
              }
            ]
          },
          "rooms":{
            "join":{
              "!room:matrix.example.org":{
                "timeline":{
                  "events":[
                    {
                      "type":"m.room.message",
                      "event_id":"$event1",
                      "sender":"@alice:matrix.example.org",
                      "origin_server_ts":1710000000000,
                      "content":{"msgtype":"m.text","body":"hello"}
                    },
                    {
                      "type":"m.room.message",
                      "event_id":"$event2",
                      "sender":"@alice:matrix.example.org",
                      "origin_server_ts":1710000001000,
                      "content":{"msgtype":"m.emote","body":"waves"}
                    },
                    {
                      "type":"m.room.topic",
                      "event_id":"$topic",
                      "sender":"@alice:matrix.example.org",
                      "content":{"topic":"ignored"}
                    }
                  ]
                },
                "ephemeral":{
                  "events":[
                    {
                      "type":"m.typing",
                      "content":{"user_ids":["@bob:matrix.example.org","@alice:matrix.example.org"]}
                    },
                    {
                      "type":"m.receipt",
                      "content":{
                        "$event1":{
                          "m.read":{
                            "@alice:matrix.example.org":{"ts":1710000004000}
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
          }
        }
        """;
    try (TestServer server = TestServer.start(200, body)) {
      MatrixSyncClient.SyncResult result =
          syncClient.sync(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "since-token",
              1500);

      assertTrue(result.success());
      assertEquals("s72595_4483_1934", result.nextBatch());
      assertEquals(2, result.events().size());
      MatrixSyncClient.RoomTimelineEvent first = result.events().get(0);
      assertEquals("!room:matrix.example.org", first.roomId());
      assertEquals("@alice:matrix.example.org", first.sender());
      assertEquals("$event1", first.eventId());
      assertEquals("m.text", first.msgType());
      assertEquals("hello", first.body());
      assertEquals(1710000000000L, first.originServerTs());
      assertEquals(
          "@bob:matrix.example.org", result.directPeerByRoom().get("!room:matrix.example.org"));
      assertEquals(1, result.typingEvents().size());
      assertEquals("!room:matrix.example.org", result.typingEvents().get(0).roomId());
      assertEquals(
          List.of("@bob:matrix.example.org", "@alice:matrix.example.org"),
          result.typingEvents().get(0).userIds());
      assertEquals(1, result.readReceipts().size());
      assertEquals("!room:matrix.example.org", result.readReceipts().get(0).roomId());
      assertEquals("$event1", result.readReceipts().get(0).eventId());
      assertEquals("@alice:matrix.example.org", result.readReceipts().get(0).userId());
      assertEquals(1710000004000L, result.readReceipts().get(0).timestampMs());
      assertEquals("Bearer secret-token", server.lastAuthorizationHeader().get());
      assertTrue(server.lastQuery().get().contains("timeout=1500"));
      assertTrue(server.lastQuery().get().contains("since=since-token"));
    }
  }

  @Test
  void syncReportsHttpFailure() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(500, "{\"errcode\":\"M_UNKNOWN\"}")) {
      MatrixSyncClient.SyncResult result =
          syncClient.sync(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "",
              0);

      assertFalse(result.success());
      assertEquals("HTTP 500 from sync endpoint", result.detail());
      assertTrue(result.events().isEmpty());
      assertTrue(result.directPeerByRoom().isEmpty());
      assertTrue(result.typingEvents().isEmpty());
      assertTrue(result.readReceipts().isEmpty());
    }
  }

  @Test
  void syncRejectsBlankAccessToken() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixSyncClient.SyncResult result =
        syncClient.sync(
            "matrix", serverConfig("matrix", "matrix.example.org", 8448, true), " ", "", 0);

    assertFalse(result.success());
    assertEquals("access token is blank", result.detail());
    assertTrue(result.events().isEmpty());
    assertTrue(result.directPeerByRoom().isEmpty());
    assertTrue(result.typingEvents().isEmpty());
    assertTrue(result.readReceipts().isEmpty());
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
          "/_matrix/client/v3/sync",
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

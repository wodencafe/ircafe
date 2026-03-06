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
                      "content":{
                        "msgtype":"m.emote",
                        "body":"waves",
                        "m.relates_to":{"m.in_reply_to":{"event_id":"$event0"}}
                      }
                    },
                    {
                      "type":"m.room.topic",
                      "event_id":"$topic",
                      "sender":"@alice:matrix.example.org",
                      "content":{"topic":"ignored"}
                    },
                    {
                      "type":"m.room.member",
                      "event_id":"$member1",
                      "sender":"@bob:matrix.example.org",
                      "state_key":"@bob:matrix.example.org",
                      "origin_server_ts":1710000002000,
                      "content":{"membership":"join","displayname":"Bob"},
                      "unsigned":{
                        "prev_content":{"membership":"leave","displayname":"Old Bob"}
                      }
                    },
                    {
                      "type":"m.room.message",
                      "event_id":"$edit1",
                      "sender":"@alice:matrix.example.org",
                      "origin_server_ts":1710000002500,
                      "content":{
                        "msgtype":"m.text",
                        "body":"* hello edited",
                        "m.new_content":{"msgtype":"m.text","body":"hello edited"},
                        "m.relates_to":{"rel_type":"m.replace","event_id":"$event1"}
                      }
                    },
                    {
                      "type":"m.reaction",
                      "event_id":"$react1",
                      "sender":"@bob:matrix.example.org",
                      "origin_server_ts":1710000003000,
                      "content":{
                        "m.relates_to":{
                          "rel_type":"m.annotation",
                          "event_id":"$event1",
                          "key":":+1:"
                        }
                      }
                    },
                    {
                      "type":"m.room.redaction",
                      "event_id":"$redact1",
                      "sender":"@bob:matrix.example.org",
                      "origin_server_ts":1710000003500,
                      "redacts":"$event2",
                      "content":{"reason":"cleanup"}
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
      assertEquals("", first.replyToEventId());
      assertEquals(1710000000000L, first.originServerTs());
      MatrixSyncClient.RoomTimelineEvent second = result.events().get(1);
      assertEquals("$event2", second.eventId());
      assertEquals("m.emote", second.msgType());
      assertEquals("waves", second.body());
      assertEquals("$event0", second.replyToEventId());
      assertEquals(1710000001000L, second.originServerTs());
      assertEquals(1, result.membershipEvents().size());
      MatrixSyncClient.RoomMembershipEvent membership = result.membershipEvents().getFirst();
      assertEquals("!room:matrix.example.org", membership.roomId());
      assertEquals("@bob:matrix.example.org", membership.userId());
      assertEquals("$member1", membership.eventId());
      assertEquals("join", membership.membership());
      assertEquals("leave", membership.prevMembership());
      assertEquals("Bob", membership.displayName());
      assertEquals("Old Bob", membership.prevDisplayName());
      assertEquals(1710000002000L, membership.originServerTs());
      assertEquals(1, result.messageEditEvents().size());
      MatrixSyncClient.RoomMessageEditEvent edit = result.messageEditEvents().getFirst();
      assertEquals("!room:matrix.example.org", edit.roomId());
      assertEquals("@alice:matrix.example.org", edit.sender());
      assertEquals("$edit1", edit.eventId());
      assertEquals("$event1", edit.targetEventId());
      assertEquals("m.text", edit.msgType());
      assertEquals("hello edited", edit.body());
      assertEquals(1710000002500L, edit.originServerTs());
      assertEquals(1, result.reactionEvents().size());
      MatrixSyncClient.RoomReactionEvent reaction = result.reactionEvents().getFirst();
      assertEquals("!room:matrix.example.org", reaction.roomId());
      assertEquals("@bob:matrix.example.org", reaction.sender());
      assertEquals("$react1", reaction.eventId());
      assertEquals("$event1", reaction.targetEventId());
      assertEquals(":+1:", reaction.reaction());
      assertEquals(1710000003000L, reaction.originServerTs());
      assertEquals(1, result.redactionEvents().size());
      MatrixSyncClient.RoomRedactionEvent redaction = result.redactionEvents().getFirst();
      assertEquals("!room:matrix.example.org", redaction.roomId());
      assertEquals("@bob:matrix.example.org", redaction.sender());
      assertEquals("$redact1", redaction.eventId());
      assertEquals("$event2", redaction.redactsEventId());
      assertEquals("cleanup", redaction.reason());
      assertEquals(1710000003500L, redaction.originServerTs());
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
      assertTrue(result.membershipEvents().isEmpty());
      assertTrue(result.messageEditEvents().isEmpty());
      assertTrue(result.reactionEvents().isEmpty());
      assertTrue(result.redactionEvents().isEmpty());
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
    assertTrue(result.membershipEvents().isEmpty());
    assertTrue(result.messageEditEvents().isEmpty());
    assertTrue(result.reactionEvents().isEmpty());
    assertTrue(result.redactionEvents().isEmpty());
    assertTrue(result.directPeerByRoom().isEmpty());
    assertTrue(result.typingEvents().isEmpty());
    assertTrue(result.readReceipts().isEmpty());
  }

  @Test
  void syncParsesReplyContextFromAlternateRelationShapes() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    String body =
        """
        {
          "next_batch":"s-next",
          "rooms":{
            "join":{
              "!room:matrix.example.org":{
                "timeline":{
                  "events":[
                    {
                      "type":"m.room.message",
                      "event_id":"$stable",
                      "sender":"@alice:matrix.example.org",
                      "origin_server_ts":1710000100000,
                      "content":{
                        "msgtype":"m.text",
                        "body":"stable",
                        "m.relates_to":{"m.in_reply_to":{"event_id":"$root-stable"}}
                      }
                    },
                    {
                      "type":"m.room.message",
                      "event_id":"$legacy",
                      "sender":"@alice:matrix.example.org",
                      "origin_server_ts":1710000101000,
                      "content":{
                        "msgtype":"m.text",
                        "body":"legacy",
                        "m.relates_to":{"in_reply_to":{"event_id":"$root-legacy"}}
                      }
                    },
                    {
                      "type":"m.room.message",
                      "event_id":"$top-level",
                      "sender":"@alice:matrix.example.org",
                      "origin_server_ts":1710000102000,
                      "content":{
                        "msgtype":"m.text",
                        "body":"top-level",
                        "m.in_reply_to":{"event_id":"$root-top"}
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
              "",
              0);

      assertTrue(result.success());
      assertEquals(3, result.events().size());
      assertEquals("$root-stable", result.events().get(0).replyToEventId());
      assertEquals("$root-legacy", result.events().get(1).replyToEventId());
      assertEquals("$root-top", result.events().get(2).replyToEventId());
    }
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

package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.hooks.events.UnknownEvent;

class PircbotxBridgeListenerUnknownCtcpTest {

  @Test
  void onUnknownEmitsCtcpRequestForCustomPrivateCtcp() {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    List<ServerIrcEvent> seen = new ArrayList<>();
    bus.subscribe(seen::add);

    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.selfNickHint.set("me");
    PircbotxBridgeListener listener = newListener(conn, bus);
    String line = ":alice!ident@host.example PRIVMSG me :\u0001WODEN hello world\u0001";
    UnknownEvent unknown =
        new UnknownEvent(
            null,
            "me",
            "alice",
            "PRIVMSG",
            line,
            List.of("me", "\u0001WODEN hello world\u0001"),
            ImmutableMap.of());

    listener.onUnknown(unknown);

    IrcEvent.CtcpRequestReceived ctcp =
        seen.stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.CtcpRequestReceived.class::isInstance)
            .map(IrcEvent.CtcpRequestReceived.class::cast)
            .findFirst()
            .orElse(null);

    assertNotNull(ctcp, "events=" + seen);
    assertEquals("alice", ctcp.from());
    assertEquals("WODEN", ctcp.command());
    assertEquals("hello world", ctcp.argument());
    assertNull(ctcp.channel());
  }

  @Test
  void onUnknownDropsPrivateCtcpThatIsNotAddressedToSelf() {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    List<ServerIrcEvent> seen = new ArrayList<>();
    bus.subscribe(seen::add);

    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.selfNickHint.set("me");
    PircbotxBridgeListener listener = newListener(conn, bus);
    String line = ":alice!ident@host.example PRIVMSG bob :\u0001WODEN ping\u0001";
    UnknownEvent unknown =
        new UnknownEvent(
            null,
            "bob",
            "alice",
            "PRIVMSG",
            line,
            List.of("bob", "\u0001WODEN ping\u0001"),
            ImmutableMap.of());

    listener.onUnknown(unknown);

    assertTrue(
        seen.stream()
            .map(ServerIrcEvent::event)
            .noneMatch(IrcEvent.CtcpRequestReceived.class::isInstance));
  }

  private static PircbotxBridgeListener newListener(
      PircbotxConnectionState conn, FlowableProcessor<ServerIrcEvent> bus) {
    return new PircbotxBridgeListener(
        "libera",
        conn,
        bus,
        c -> {},
        (c, reason) -> {},
        (bot, fromNick, message) -> false,
        false,
        false,
        false,
        null,
        new NoOpPlaybackCursorProvider());
  }
}

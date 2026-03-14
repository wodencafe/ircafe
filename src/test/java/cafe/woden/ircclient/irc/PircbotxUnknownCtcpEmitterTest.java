package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.hooks.events.UnknownEvent;

class PircbotxUnknownCtcpEmitterTest {

  @Test
  void maybeEmitPublishesPrivateCtcpRequest() {
    List<ServerIrcEvent> seen = new ArrayList<>();
    PircbotxUnknownCtcpEmitter emitter =
        new PircbotxUnknownCtcpEmitter(
            "libera", seen::add, (bot, nick) -> false, (bot, nick) -> false, bot -> "me");

    String line = ":alice!ident@host.example PRIVMSG me :\u0001WODEN hello world\u0001";
    UnknownEvent event =
        new UnknownEvent(
            null,
            "me",
            "alice",
            "PRIVMSG",
            line,
            List.of("me", "\u0001WODEN hello world\u0001"),
            ImmutableMap.of());

    assertTrue(
        emitter.maybeEmit(
            event,
            line,
            PircbotxLineParseUtil.normalizeIrcLineForParsing(line),
            PircbotxInboundLineParsers.parseIrcLine(
                PircbotxLineParseUtil.normalizeIrcLineForParsing(line))));

    IrcEvent.CtcpRequestReceived ctcp =
        assertInstanceOf(IrcEvent.CtcpRequestReceived.class, seen.getFirst().event());
    assertEquals("alice", ctcp.from());
    assertEquals("WODEN", ctcp.command());
    assertEquals("hello world", ctcp.argument());
    assertEquals(null, ctcp.channel());
  }

  @Test
  void maybeEmitSuppressesPrivateCtcpNotAddressedToSelf() {
    List<ServerIrcEvent> seen = new ArrayList<>();
    PircbotxUnknownCtcpEmitter emitter =
        new PircbotxUnknownCtcpEmitter(
            "libera", seen::add, (bot, nick) -> false, (bot, nick) -> false, bot -> "me");

    String line = ":alice!ident@host.example PRIVMSG bob :\u0001WODEN ping\u0001";
    UnknownEvent event =
        new UnknownEvent(
            null,
            "bob",
            "alice",
            "PRIVMSG",
            line,
            List.of("bob", "\u0001WODEN ping\u0001"),
            ImmutableMap.of());

    assertTrue(
        emitter.maybeEmit(
            event,
            line,
            PircbotxLineParseUtil.normalizeIrcLineForParsing(line),
            PircbotxInboundLineParsers.parseIrcLine(
                PircbotxLineParseUtil.normalizeIrcLineForParsing(line))));
    assertTrue(seen.isEmpty());
  }
}

package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.UserHostmask;
import org.pircbotx.hooks.events.FingerEvent;
import org.pircbotx.hooks.events.PingEvent;

class PircbotxInboundCtcpHandlerTest {

  @Test
  void onGenericCtcpEmitsPrivatePingAndAutoReplies() {
    List<ServerIrcEvent> seen = new ArrayList<>();
    List<String> replies = new ArrayList<>();
    PircbotxInboundCtcpHandler handler =
        newHandler(":alice!ident@host.example PRIVMSG me :\u0001PING 123\u0001", seen, replies);

    PingEvent event =
        new PingEvent(mock(PircBotX.class), mock(UserHostmask.class), user("alice"), null, "123");

    handler.onGenericCtcp(event);

    assertEquals(1, seen.size());
    IrcEvent.CtcpRequestReceived ctcp =
        assertInstanceOf(IrcEvent.CtcpRequestReceived.class, seen.getFirst().event());
    assertEquals("alice", ctcp.from());
    assertEquals("PING", ctcp.command());
    assertEquals("123", ctcp.argument());
    assertNull(ctcp.channel());
    assertEquals(List.of("alice|\u0001PING 123\u0001"), replies);
  }

  @Test
  void onGenericCtcpDropsPrivatePingAddressedToAnotherNick() {
    List<ServerIrcEvent> seen = new ArrayList<>();
    List<String> replies = new ArrayList<>();
    PircbotxInboundCtcpHandler handler =
        newHandler(":alice!ident@host.example PRIVMSG bob :\u0001PING 123\u0001", seen, replies);

    PingEvent event =
        new PingEvent(mock(PircBotX.class), mock(UserHostmask.class), user("alice"), null, "123");

    handler.onGenericCtcp(event);

    assertTrue(seen.isEmpty());
    assertTrue(replies.isEmpty());
  }

  @Test
  void onFingerEmitsPrivateFingerAndAutoReplies() {
    List<ServerIrcEvent> seen = new ArrayList<>();
    List<String> replies = new ArrayList<>();
    PircbotxInboundCtcpHandler handler =
        newHandler(":alice!ident@host.example PRIVMSG me :\u0001FINGER\u0001", seen, replies);

    FingerEvent event =
        new FingerEvent(mock(PircBotX.class), mock(UserHostmask.class), user("alice"), null);

    handler.onFinger(event);

    assertEquals(1, seen.size());
    IrcEvent.CtcpRequestReceived ctcp =
        assertInstanceOf(IrcEvent.CtcpRequestReceived.class, seen.getFirst().event());
    assertEquals("alice", ctcp.from());
    assertEquals("FINGER", ctcp.command());
    assertNull(ctcp.argument());
    assertNull(ctcp.channel());
    assertEquals(List.of("alice|\u0001FINGER\u0001"), replies);
  }

  private static PircbotxInboundCtcpHandler newHandler(
      String rawLine, List<ServerIrcEvent> seen, List<String> replies) {
    return new PircbotxInboundCtcpHandler(
        "libera",
        () -> "me",
        (bot, nick) -> "me".equalsIgnoreCase(String.valueOf(nick)),
        (bot, nick) -> false,
        bot -> "me",
        bot -> "me",
        event -> rawLine,
        event -> null,
        (channel, user) -> {},
        seen::add,
        (bot, fromNick, message) -> replies.add(fromNick + "|" + message));
  }

  private static User user(String nick) {
    User user = mock(User.class);
    when(user.getNick()).thenReturn(nick);
    return user;
  }
}

package cafe.woden.ircclient.irc.pircbotx.emit;

import static cafe.woden.ircclient.irc.pircbotx.PircbotxRuntimeTestFixtures.runtime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.UserHostmask;
import org.pircbotx.hooks.events.IncomingChatRequestEvent;
import org.pircbotx.hooks.events.IncomingFileTransferEvent;

class PircbotxDccRequestEmitterTest {

  @Test
  void emitsChatOfferAsDccCtcpRequest() throws Exception {
    List<ServerIrcEvent> seen = new ArrayList<>();
    PircbotxDccRequestEmitter emitter =
        new PircbotxDccRequestEmitter("libera", seen::add, runtime().serverTime());
    IncomingChatRequestEvent event =
        new IncomingChatRequestEvent(
            mock(PircBotX.class),
            mock(UserHostmask.class),
            user("alice"),
            InetAddress.getByName("127.0.0.1"),
            54321,
            null,
            false);

    emitter.onIncomingChatRequest(event);

    IrcEvent.CtcpRequestReceived request = emittedRequest(seen);
    assertEquals("alice", request.from());
    assertEquals("DCC", request.command());
    assertEquals("CHAT chat 127.0.0.1 54321", request.argument());
    assertEquals(null, request.channel());
  }

  @Test
  void emitsFileOfferWithTokenAsDccCtcpRequest() throws Exception {
    List<ServerIrcEvent> seen = new ArrayList<>();
    PircbotxDccRequestEmitter emitter =
        new PircbotxDccRequestEmitter("libera", seen::add, runtime().serverTime());
    IncomingFileTransferEvent event =
        new IncomingFileTransferEvent(
            mock(PircBotX.class),
            mock(UserHostmask.class),
            user("alice"),
            "notes for birb.txt",
            "notes for birb.txt",
            InetAddress.getByName("192.0.2.10"),
            4123,
            8192L,
            "offer-token",
            true);

    emitter.onIncomingFileTransfer(event);

    IrcEvent.CtcpRequestReceived request = emittedRequest(seen);
    assertEquals("alice", request.from());
    assertEquals("DCC", request.command());
    assertEquals(
        "SEND \"notes for birb.txt\" 192.0.2.10 4123 8192 offer-token", request.argument());
  }

  private static IrcEvent.CtcpRequestReceived emittedRequest(List<ServerIrcEvent> seen) {
    assertEquals(1, seen.size());
    return assertInstanceOf(IrcEvent.CtcpRequestReceived.class, seen.getFirst().event());
  }

  private static User user(String nick) {
    User user = mock(User.class);
    when(user.getNick()).thenReturn(nick);
    return user;
  }
}

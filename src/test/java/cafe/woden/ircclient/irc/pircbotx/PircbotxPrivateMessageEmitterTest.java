package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxPrivateMessageEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxRosterEmitter;
import cafe.woden.ircclient.irc.pircbotx.support.Ircv3MultilineAccumulator;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.events.PrivateMessageEvent;

class PircbotxPrivateMessageEmitterTest {

  @Test
  void onPrivateMessageEmitsStructuredPrivateMessage() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxPrivateMessageEmitter emitter = newEmitter(conn, events, event -> "me", bot -> "me");

    PrivateMessageEvent event = privateMessage("alice", "hello there");

    emitter.onPrivateMessage(event);

    assertEquals(1, events.size());
    IrcEvent.PrivateMessage message =
        assertInstanceOf(IrcEvent.PrivateMessage.class, events.getFirst().event());
    assertEquals("alice", message.from());
    assertEquals("hello there", message.text());
    assertEquals("me", message.ircv3Tags().get("ircafe/pm-target"));
  }

  @Test
  void onPrivateMessageEmitsPrivateActionForCtcpAction() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxPrivateMessageEmitter emitter = newEmitter(conn, events, event -> "me", bot -> "me");

    PrivateMessageEvent event = privateMessage("alice", "\u0001ACTION waves\u0001");

    emitter.onPrivateMessage(event);

    assertEquals(1, events.size());
    IrcEvent.PrivateAction action =
        assertInstanceOf(IrcEvent.PrivateAction.class, events.getFirst().event());
    assertEquals("alice", action.from());
    assertEquals("waves", action.action());
  }

  @Test
  void onPrivateMessageSuppressesSelfPlaybackBootstrapCommand() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxPrivateMessageEmitter emitter =
        newEmitter(conn, events, event -> "*playback", bot -> "me");

    PrivateMessageEvent event = privateMessage("me", "play * 19");

    emitter.onPrivateMessage(event);

    assertEquals(0, events.size());
  }

  @Test
  void onPrivateMessageSuppressesCapturedStatusDiscoveryRows() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.zncListNetworksCaptureActive.set(true);
    conn.zncListNetworksCaptureStartedMs.set(System.currentTimeMillis());
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxPrivateMessageEmitter emitter = newEmitter(conn, events, event -> "me", bot -> "me");

    PrivateMessageEvent event = privateMessage("*status", "| Network |");

    emitter.onPrivateMessage(event);

    assertEquals(0, events.size());
  }

  private static PircbotxPrivateMessageEmitter newEmitter(
      PircbotxConnectionState conn,
      List<ServerIrcEvent> events,
      java.util.function.Function<Object, String> privateTargetResolver,
      java.util.function.Function<PircBotX, String> selfNickResolver) {
    PircbotxRosterEmitter rosterEmitter =
        new PircbotxRosterEmitter("libera", conn, new ServerIsupportState(), events::add);
    PircbotxBouncerDiscoveryCoordinator bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            "libera",
            conn,
            false,
            true,
            new BouncerBackendRegistry(List.of()),
            BouncerDiscoveryEventPort.noOp());
    PircbotxChatHistoryBatchCollector batches =
        new PircbotxChatHistoryBatchCollector("libera", events::add);
    return new PircbotxPrivateMessageEmitter(
        "libera",
        conn,
        rosterEmitter,
        bouncerDiscovery,
        batches,
        new Ircv3MultilineAccumulator(),
        events::add,
        selfNickResolver,
        privateTargetResolver);
  }

  private static PrivateMessageEvent privateMessage(String nick, String message) {
    PrivateMessageEvent event = mock(PrivateMessageEvent.class);
    PircBotX bot = mock(PircBotX.class);
    User user = mock(User.class);
    when(user.getNick()).thenReturn(nick);
    when(event.getBot()).thenReturn(bot);
    when(event.getUser()).thenReturn(user);
    when(event.getMessage()).thenReturn(message);
    return event;
  }
}

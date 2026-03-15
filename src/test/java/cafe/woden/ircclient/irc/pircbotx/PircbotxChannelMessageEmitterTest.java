package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChannelMessageEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.support.Ircv3MultilineAccumulator;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.hooks.events.MessageEvent;

class PircbotxChannelMessageEmitterTest {

  @Test
  void onMessageEmitsStructuredChannelMessage() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxChannelMessageEmitter emitter = newEmitter(conn, events);

    MessageEvent event = message("#ircafe", "alice", "hello channel");

    emitter.onMessage(event);

    assertEquals(1, events.size());
    IrcEvent.ChannelMessage message =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.getFirst().event());
    assertEquals("#ircafe", message.channel());
    assertEquals("alice", message.from());
    assertEquals("hello channel", message.text());
  }

  @Test
  void onMessageEmitsChannelActionForCtcpActionPayload() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxChannelMessageEmitter emitter = newEmitter(conn, events);

    MessageEvent event = message("#ircafe", "alice", "\u0001ACTION waves\u0001");

    emitter.onMessage(event);

    assertEquals(1, events.size());
    IrcEvent.ChannelAction action =
        assertInstanceOf(IrcEvent.ChannelAction.class, events.getFirst().event());
    assertEquals("#ircafe", action.channel());
    assertEquals("alice", action.from());
    assertEquals("waves", action.action());
  }

  @Test
  void onMessageSuppressesReplayCapturedForActivePlaybackTarget() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxChannelMessageEmitter emitter = newEmitter(conn, events);
    conn.zncPlaybackCapture.start(
        "libera", "#ircafe", Instant.now().minusSeconds(60), null, events::add);

    MessageEvent event = message("#ircafe", "alice", "captured line");

    emitter.onMessage(event);

    assertEquals(0, events.size());
    conn.zncPlaybackCapture.cancelActive("test");
  }

  private static PircbotxChannelMessageEmitter newEmitter(
      PircbotxConnectionState conn, List<ServerIrcEvent> events) {
    PircbotxRosterEmitter rosterEmitter =
        new PircbotxRosterEmitter("libera", conn, new ServerIsupportState(), events::add);
    PircbotxChatHistoryBatchCollector batches =
        new PircbotxChatHistoryBatchCollector("libera", events::add);
    return new PircbotxChannelMessageEmitter(
        "libera", conn, rosterEmitter, batches, new Ircv3MultilineAccumulator(), events::add);
  }

  private static MessageEvent message(String channelName, String nick, String text) {
    MessageEvent event = mock(MessageEvent.class);
    Channel channel = mock(Channel.class);
    User user = mock(User.class);
    when(channel.getName()).thenReturn(channelName);
    when(user.getNick()).thenReturn(nick);
    when(event.getChannel()).thenReturn(channel);
    when(event.getUser()).thenReturn(user);
    when(event.getMessage()).thenReturn(text);
    return event;
  }
}

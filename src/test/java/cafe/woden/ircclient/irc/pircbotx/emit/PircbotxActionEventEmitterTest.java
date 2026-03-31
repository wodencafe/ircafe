package cafe.woden.ircclient.irc.pircbotx.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.events.ActionEvent;

class PircbotxActionEventEmitterTest {

  @Test
  void onActionEmitsChannelActionWhenChannelPresent() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxActionEventEmitter emitter = newEmitter(conn, events, event -> "", bot -> "me");

    ActionEvent event = action("alice", "waves", "#ircafe");

    emitter.onAction(event);

    assertEquals(1, events.size());
    IrcEvent.ChannelAction action =
        assertInstanceOf(IrcEvent.ChannelAction.class, events.getFirst().event());
    assertEquals("#ircafe", action.channel());
    assertEquals("alice", action.from());
    assertEquals("waves", action.action());
  }

  @Test
  void onActionEmitsPrivateActionWithPrivateTargetTag() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxActionEventEmitter emitter = newEmitter(conn, events, event -> "me", bot -> "me");

    ActionEvent event = action("alice", "waves", null);

    emitter.onAction(event);

    assertEquals(1, events.size());
    IrcEvent.PrivateAction action =
        assertInstanceOf(IrcEvent.PrivateAction.class, events.getFirst().event());
    assertEquals("alice", action.from());
    assertEquals("waves", action.action());
    assertEquals("me", action.ircv3Tags().get("ircafe/pm-target"));
  }

  private static PircbotxActionEventEmitter newEmitter(
      PircbotxConnectionState conn,
      List<ServerIrcEvent> events,
      java.util.function.Function<Object, String> privateTargetResolver,
      java.util.function.Function<PircBotX, String> selfNickResolver) {
    PircbotxRosterEmitter rosterEmitter =
        new PircbotxRosterEmitter("libera", conn, new ServerIsupportState(), events::add);
    PircbotxChatHistoryBatchCollector batches =
        new PircbotxChatHistoryBatchCollector("libera", events::add);
    return new PircbotxActionEventEmitter(
        "libera",
        conn,
        rosterEmitter,
        batches,
        events::add,
        selfNickResolver,
        privateTargetResolver);
  }

  private static ActionEvent action(String nick, String action, String channelName) {
    ActionEvent event = mock(ActionEvent.class);
    PircBotX bot = mock(PircBotX.class);
    User user = mock(User.class);
    when(user.getNick()).thenReturn(nick);
    when(event.getBot()).thenReturn(bot);
    when(event.getUser()).thenReturn(user);
    when(event.getAction()).thenReturn(action);
    if (channelName != null) {
      Channel channel = mock(Channel.class);
      when(channel.getName()).thenReturn(channelName);
      when(event.getChannel()).thenReturn(channel);
    }
    return event;
  }
}

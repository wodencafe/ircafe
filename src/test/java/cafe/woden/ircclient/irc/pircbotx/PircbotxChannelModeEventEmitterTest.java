package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import com.google.common.collect.ImmutableSortedSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.hooks.events.ModeEvent;
import org.pircbotx.hooks.events.OpEvent;

class PircbotxChannelModeEventEmitterTest {

  @Test
  void onModeEmitsRosterAndLiveModeObservation() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxChannelModeEventEmitter emitter = newEmitter(events);

    Channel channel = channel("#ircafe");
    User user = mock(User.class);
    when(user.getNick()).thenReturn("oper");
    ModeEvent event = mock(ModeEvent.class);
    when(event.getChannel()).thenReturn(channel);
    when(event.getUser()).thenReturn(user);
    when(event.getMode()).thenReturn("+o alice");

    emitter.onMode(event);

    assertEquals(2, events.size());
    assertInstanceOf(IrcEvent.NickListUpdated.class, events.get(0).event());
    IrcEvent.ChannelModeObserved observed =
        assertInstanceOf(IrcEvent.ChannelModeObserved.class, events.get(1).event());
    assertEquals("#ircafe", observed.channel());
    assertEquals("oper", observed.by());
    assertEquals("+o alice", observed.details());
    assertEquals(IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT, observed.provenance());
  }

  @Test
  void onOpRefreshesRoster() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxChannelModeEventEmitter emitter = newEmitter(events);

    OpEvent event = mock(OpEvent.class);
    Channel channel = channel("#ircafe");
    when(event.getChannel()).thenReturn(channel);

    emitter.onOp(event);

    assertEquals(1, events.size());
    assertInstanceOf(IrcEvent.NickListUpdated.class, events.getFirst().event());
  }

  private static PircbotxChannelModeEventEmitter newEmitter(List<ServerIrcEvent> events) {
    PircbotxRosterEmitter rosterEmitter =
        new PircbotxRosterEmitter(
            "libera",
            new PircbotxConnectionState("libera"),
            new ServerIsupportState(),
            events::add);
    return new PircbotxChannelModeEventEmitter(
        "libera",
        rosterEmitter,
        events::add,
        PircbotxChannelModeEventEmitterTest::nickFromEvent,
        PircbotxChannelModeEventEmitterTest::modeDetailsFromEvent);
  }

  private static Channel channel(String name) {
    Channel channel = mock(Channel.class);
    when(channel.getName()).thenReturn(name);
    ImmutableSortedSet<User> users =
        ImmutableSortedSet.orderedBy(
                Comparator.comparing(User::getNick, String.CASE_INSENSITIVE_ORDER))
            .build();
    when(channel.getUsers()).thenReturn(users);
    when(channel.getOwners()).thenReturn(users);
    when(channel.getSuperOps()).thenReturn(users);
    when(channel.getOps()).thenReturn(users);
    when(channel.getHalfOps()).thenReturn(users);
    when(channel.getVoices()).thenReturn(users);
    return channel;
  }

  private static String nickFromEvent(Object event) {
    if (event instanceof ModeEvent modeEvent && modeEvent.getUser() != null) {
      return modeEvent.getUser().getNick();
    }
    return "";
  }

  private static String modeDetailsFromEvent(Object event, String channelName) {
    if (!(event instanceof ModeEvent modeEvent)) return null;
    return modeEvent.getMode();
  }
}

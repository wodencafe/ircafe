package cafe.woden.ircclient.irc.pircbotx.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import com.google.common.collect.ImmutableSortedSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.Channel;
import org.pircbotx.User;

class PircbotxRosterEmitterTest {

  @Test
  void maybeEmitHostmaskObservedDedupesRepeatedHostmask() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxRosterEmitter emitter =
        new PircbotxRosterEmitter("libera", conn, new ServerIsupportState(), events::add);

    User user = mock(User.class);
    when(user.getNick()).thenReturn("alice");
    when(user.getHostmask()).thenReturn("alice!ident@host.example");

    emitter.maybeEmitHostmaskObserved("#ircafe", user);
    emitter.maybeEmitHostmaskObserved("#ircafe", user);

    assertEquals(1, events.size());
    IrcEvent.UserHostmaskObserved observed =
        assertInstanceOf(IrcEvent.UserHostmaskObserved.class, events.getFirst().event());
    assertEquals("#ircafe", observed.channel());
    assertEquals("alice!ident@host.example", observed.hostmask());
  }

  @Test
  void emitRosterUsesNegotiatedPrefixesAndRanks() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    ServerIsupportState isupportState = new ServerIsupportState();
    isupportState.applyIsupportToken("libera", "PREFIX", "(qaohv)!&@%+");

    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxRosterEmitter emitter =
        new PircbotxRosterEmitter("libera", conn, isupportState, events::add);

    User owner = mock(User.class);
    when(owner.getNick()).thenReturn("alice");
    User voice = mock(User.class);
    when(voice.getNick()).thenReturn("bob");

    Channel channel = mock(Channel.class);
    when(channel.getName()).thenReturn("#ircafe");
    ImmutableSortedSet<User> users =
        ImmutableSortedSet.orderedBy(
                Comparator.comparing(User::getNick, String.CASE_INSENSITIVE_ORDER))
            .add(voice, owner)
            .build();
    ImmutableSortedSet<User> owners =
        ImmutableSortedSet.orderedBy(
                Comparator.comparing(User::getNick, String.CASE_INSENSITIVE_ORDER))
            .add(owner)
            .build();
    ImmutableSortedSet<User> voices =
        ImmutableSortedSet.orderedBy(
                Comparator.comparing(User::getNick, String.CASE_INSENSITIVE_ORDER))
            .add(voice)
            .build();
    ImmutableSortedSet<User> none =
        ImmutableSortedSet.orderedBy(
                Comparator.comparing(User::getNick, String.CASE_INSENSITIVE_ORDER))
            .build();
    when(channel.getUsers()).thenReturn(users);
    when(channel.getOwners()).thenReturn(owners);
    when(channel.getSuperOps()).thenReturn(none);
    when(channel.getOps()).thenReturn(none);
    when(channel.getHalfOps()).thenReturn(none);
    when(channel.getVoices()).thenReturn(voices);

    emitter.emitRoster(channel);

    assertEquals(1, events.size());
    IrcEvent.NickListUpdated nickList =
        assertInstanceOf(IrcEvent.NickListUpdated.class, events.getFirst().event());
    assertEquals(
        List.of("alice", "bob"), nickList.nicks().stream().map(IrcEvent.NickInfo::nick).toList());
    assertEquals("!", nickList.nicks().get(0).prefix());
    assertEquals("+", nickList.nicks().get(1).prefix());
    assertEquals(1, nickList.operatorCount());
  }
}

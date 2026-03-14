package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.hooks.events.UnknownEvent;
import org.pircbotx.hooks.events.UserListEvent;

class PircbotxBridgeListenerRosterTest {

  @Test
  void emitRosterUsesNegotiatedPrefixCharactersAndRanks() throws Exception {
    FlowableProcessor<ServerIrcEvent> bus =
        PublishProcessor.<ServerIrcEvent>create().toSerialized();
    List<ServerIrcEvent> seen = new ArrayList<>();
    bus.subscribe(seen::add);

    ServerIsupportState isupportState = new ServerIsupportState();
    PircbotxBridgeListener listener =
        new PircbotxBridgeListenerFactory(
                new BouncerBackendRegistry(List.<BouncerNetworkMappingStrategy>of()),
                null,
                new NoOpPlaybackCursorProvider(),
                isupportState,
                new SojuProperties(Map.of(), new SojuProperties.Discovery(false)),
                new ZncProperties(Map.of(), new ZncProperties.Discovery(false)))
            .create(
                "libera",
                new PircbotxConnectionState("libera"),
                bus,
                c -> {},
                (c, reason) -> {},
                (bot, fromNick, message) -> false,
                false);

    listener.onUnknown(
        new UnknownEvent(
            null,
            "me",
            "irc.example",
            "005",
            ":irc.example 005 me PREFIX=(qaohv)!&@%+ :are supported by this server",
            List.of("me", "PREFIX=(qaohv)!&@%+"),
            ImmutableMap.of()));

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

    UserListEvent event = mock(UserListEvent.class);
    when(event.getChannel()).thenReturn(channel);
    listener.onUserList(event);

    ServerIrcEvent emitted = seen.getLast();
    IrcEvent.NickListUpdated nickListUpdated =
        assertInstanceOf(IrcEvent.NickListUpdated.class, emitted.event());

    assertEquals(
        List.of("alice", "bob"),
        nickListUpdated.nicks().stream().map(IrcEvent.NickInfo::nick).toList());
    assertEquals("!", nickListUpdated.nicks().get(0).prefix());
    assertEquals("+", nickListUpdated.nicks().get(1).prefix());
    assertEquals(1, nickListUpdated.operatorCount());
  }
}

package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import com.google.common.collect.ImmutableSortedSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.UserChannelDao;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.NickChangeEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.QuitEvent;
import org.pircbotx.snapshot.ChannelSnapshot;
import org.pircbotx.snapshot.UserChannelDaoSnapshot;
import org.pircbotx.snapshot.UserSnapshot;

class PircbotxMembershipEventEmitterTest {

  @Test
  void onJoinEmitsJoinedChannelForSelfAndRosterSnapshot() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxMembershipEventEmitter emitter = newEmitter(conn, events);

    Channel channel = channel("#ircafe");
    User user = user("me");
    JoinEvent event = mock(JoinEvent.class);
    PircBotX bot = mock(PircBotX.class);
    when(event.getChannel()).thenReturn(channel);
    when(event.getUser()).thenReturn(user);
    when(event.getBot()).thenReturn(bot);

    emitter.onJoin(event);

    assertEquals(2, events.size());
    assertInstanceOf(IrcEvent.JoinedChannel.class, events.get(0).event());
    assertInstanceOf(IrcEvent.NickListUpdated.class, events.get(1).event());
  }

  @Test
  void onPartEmitsUserPartAndRefreshesRoster() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxMembershipEventEmitter emitter = newEmitter(conn, events);

    Channel channel = channel("#ircafe");
    UserSnapshot user = userSnapshot("alice");
    @SuppressWarnings("unchecked")
    UserChannelDao<User, Channel> dao = mock(UserChannelDao.class);
    PircBotX bot = mock(PircBotX.class);
    when(bot.getUserChannelDao()).thenReturn(dao);
    when(dao.containsChannel("#ircafe")).thenReturn(true);
    when(dao.getChannel("#ircafe")).thenReturn(channel);

    PartEvent event = mock(PartEvent.class);
    when(event.getBot()).thenReturn(bot);
    when(event.getUser()).thenReturn(user);
    when(event.getChannelName()).thenReturn("#ircafe");
    when(event.getReason()).thenReturn("bye");

    emitter.onPart(event);

    assertEquals(2, events.size());
    IrcEvent.UserPartedChannel parted =
        assertInstanceOf(IrcEvent.UserPartedChannel.class, events.get(0).event());
    assertEquals("#ircafe", parted.channel());
    assertEquals("alice", parted.nick());
    assertInstanceOf(IrcEvent.NickListUpdated.class, events.get(1).event());
  }

  @Test
  void onQuitUsesSnapshotChannelsWhenAvailable() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxMembershipEventEmitter emitter = newEmitter(conn, events);

    Channel liveChannel = channel("#ircafe");
    @SuppressWarnings("unchecked")
    UserChannelDao<User, Channel> dao = mock(UserChannelDao.class);
    PircBotX bot = mock(PircBotX.class);
    when(bot.getUserChannelDao()).thenReturn(dao);
    when(dao.containsChannel("#ircafe")).thenReturn(true);
    when(dao.getChannel("#ircafe")).thenReturn(liveChannel);

    UserSnapshot user = mock(UserSnapshot.class);
    when(user.getNick()).thenReturn("alice");
    ChannelSnapshot channelSnapshot = mock(ChannelSnapshot.class);
    when(channelSnapshot.getName()).thenReturn("#ircafe");
    UserChannelDaoSnapshot daoSnapshot = mock(UserChannelDaoSnapshot.class);
    ImmutableSortedSet<ChannelSnapshot> snapshotChannels =
        ImmutableSortedSet.orderedBy(
                Comparator.comparing(ChannelSnapshot::getName, String.CASE_INSENSITIVE_ORDER))
            .add(channelSnapshot)
            .build();
    when(daoSnapshot.getChannels(user)).thenReturn(snapshotChannels);

    QuitEvent event = mock(QuitEvent.class);
    when(event.getBot()).thenReturn(bot);
    when(event.getUser()).thenReturn(user);
    when(event.getReason()).thenReturn("gone");
    when(event.getUserChannelDaoSnapshot()).thenReturn(daoSnapshot);

    emitter.onQuit(event);

    assertEquals(2, events.size());
    IrcEvent.UserQuitChannel quit =
        assertInstanceOf(IrcEvent.UserQuitChannel.class, events.get(0).event());
    assertEquals("#ircafe", quit.channel());
    assertEquals("alice", quit.nick());
    assertInstanceOf(IrcEvent.NickListUpdated.class, events.get(1).event());
  }

  @Test
  void onNickChangeUpdatesSelfHintAndEmitsPerChannelChange() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.selfNickHint.set("me");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxMembershipEventEmitter emitter = newEmitter(conn, events);

    Channel channel = channel("#ircafe");
    User user = user("newme");
    @SuppressWarnings("unchecked")
    UserChannelDao<User, Channel> dao = mock(UserChannelDao.class);
    PircBotX bot = mock(PircBotX.class);
    when(bot.getUserChannelDao()).thenReturn(dao);
    ImmutableSortedSet<Channel> channels =
        ImmutableSortedSet.orderedBy(
                Comparator.comparing(Channel::getName, String.CASE_INSENSITIVE_ORDER))
            .add(channel)
            .build();
    when(dao.getChannels(user)).thenReturn(channels);

    NickChangeEvent event = mock(NickChangeEvent.class);
    when(event.getBot()).thenReturn(bot);
    when(event.getUser()).thenReturn(user);
    when(event.getOldNick()).thenReturn("me");
    when(event.getNewNick()).thenReturn("newme");

    emitter.onNickChange(event);

    assertEquals("newme", conn.selfNickHint.get());
    assertEquals(3, events.size());
    assertInstanceOf(IrcEvent.NickChanged.class, events.get(0).event());
    assertInstanceOf(IrcEvent.UserNickChangedChannel.class, events.get(1).event());
    assertInstanceOf(IrcEvent.NickListUpdated.class, events.get(2).event());
  }

  private static PircbotxMembershipEventEmitter newEmitter(
      PircbotxConnectionState conn, List<ServerIrcEvent> events) {
    PircbotxRosterEmitter rosterEmitter =
        new PircbotxRosterEmitter("libera", conn, new ServerIsupportState(), events::add);
    return new PircbotxMembershipEventEmitter(
        "libera",
        conn,
        rosterEmitter,
        events::add,
        (bot, nick) -> "me".equalsIgnoreCase(nick) || "newme".equalsIgnoreCase(nick),
        conn.selfNickHint::set,
        bot -> "me");
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

  private static User user(String nick) {
    User user = mock(User.class);
    when(user.getNick()).thenReturn(nick);
    return user;
  }

  private static UserSnapshot userSnapshot(String nick) {
    UserSnapshot user = mock(UserSnapshot.class);
    when(user.getNick()).thenReturn(nick);
    return user;
  }
}

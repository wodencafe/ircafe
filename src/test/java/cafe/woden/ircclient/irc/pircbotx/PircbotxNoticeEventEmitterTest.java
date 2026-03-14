package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.ServerIsupportState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.hooks.events.NoticeEvent;

class PircbotxNoticeEventEmitterTest {

  @Test
  void onNoticeEmitsStructuredNoticeForChannelTarget() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxNoticeEventEmitter emitter = newEmitter(conn, events, event -> "services");

    NoticeEvent event = notice("services", "hello there", "#ircafe");

    emitter.onNotice(event);

    assertEquals(1, events.size());
    IrcEvent.Notice notice = assertInstanceOf(IrcEvent.Notice.class, events.getFirst().event());
    assertEquals("services", notice.from());
    assertEquals("#ircafe", notice.target());
    assertEquals("hello there", notice.text());
  }

  @Test
  void onNoticeEmitsAlisChannelListEntryAndNotice() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxNoticeEventEmitter emitter = newEmitter(conn, events, event -> "alis");

    NoticeEvent event = notice("alis", "#java 1200 :Java discussion", null);

    emitter.onNotice(event);

    assertEquals(2, events.size());
    IrcEvent.ChannelListEntry entry =
        assertInstanceOf(IrcEvent.ChannelListEntry.class, events.get(0).event());
    IrcEvent.Notice notice = assertInstanceOf(IrcEvent.Notice.class, events.get(1).event());
    assertEquals("#java", entry.channel());
    assertEquals("alis", notice.from());
    assertEquals("#java 1200 :Java discussion", notice.text());
  }

  @Test
  void onNoticeSuppressesCapturedStatusDiscoveryRows() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.zncListNetworksCaptureActive.set(true);
    conn.zncListNetworksCaptureStartedMs.set(System.currentTimeMillis());
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxNoticeEventEmitter emitter = newEmitter(conn, events, event -> "*status");

    NoticeEvent event = notice("*status", "| Network |", null);

    emitter.onNotice(event);

    assertEquals(0, events.size());
  }

  private static PircbotxNoticeEventEmitter newEmitter(
      PircbotxConnectionState conn,
      List<ServerIrcEvent> events,
      java.util.function.Function<Object, String> senderNickResolver) {
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
    PircbotxServerResponseEmitter serverResponses =
        new PircbotxServerResponseEmitter("libera", events::add);
    return new PircbotxNoticeEventEmitter(
        "libera",
        conn,
        rosterEmitter,
        bouncerDiscovery,
        batches,
        new Ircv3MultilineAccumulator(),
        serverResponses,
        events::add,
        senderNickResolver);
  }

  private static NoticeEvent notice(String nick, String text, String channelName) {
    NoticeEvent event = mock(NoticeEvent.class);
    User user = mock(User.class);
    when(user.getNick()).thenReturn(nick);
    when(event.getUser()).thenReturn(user);
    when(event.getNotice()).thenReturn(text);
    if (channelName != null) {
      Channel channel = mock(Channel.class);
      when(channel.getName()).thenReturn(channelName);
      when(event.getChannel()).thenReturn(channel);
    }
    return event;
  }
}

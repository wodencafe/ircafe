package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.UserHostmask;

class PircbotxAwayNotifyInputParserTest {

  @Test
  void capAckUpdatesConnectionStateAndEmitsCapabilityEvent() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxAwayNotifyInputParser parser =
        new PircbotxAwayNotifyInputParser(dummyBot(), "libera", conn, out::add);

    parser.processCommand(
        "*",
        source("server"),
        "CAP",
        ":server CAP me ACK :echo-message typing",
        List.of("me", "ACK", ":echo-message typing"),
        ImmutableMap.of());

    assertTrue(conn.echoMessageCapAcked.get());
    assertTrue(conn.typingCapAcked.get());
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.Ircv3CapabilityChanged cap
                        && "ACK".equalsIgnoreCase(cap.subcommand())
                        && "echo-message".equalsIgnoreCase(cap.capability())
                        && cap.enabled()));
  }

  @Test
  void capAckTracksStandardRepliesState() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxAwayNotifyInputParser parser =
        new PircbotxAwayNotifyInputParser(dummyBot(), "libera", conn, out::add);

    parser.processCommand(
        "*",
        source("server"),
        "CAP",
        ":server CAP me ACK :standard-replies",
        List.of("me", "ACK", ":standard-replies"),
        ImmutableMap.of());

    assertTrue(conn.standardRepliesCapAcked.get());
  }

  @Test
  void finalChathistoryCapAlsoUpdatesChatHistoryState() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxAwayNotifyInputParser parser =
        new PircbotxAwayNotifyInputParser(dummyBot(), "libera", conn, out::add);

    parser.processCommand(
        "*",
        source("server"),
        "CAP",
        ":server CAP me ACK :chathistory",
        List.of("me", "ACK", ":chathistory"),
        ImmutableMap.of());

    assertTrue(conn.chatHistoryCapAcked.get());
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.Ircv3CapabilityChanged cap
                        && "ACK".equalsIgnoreCase(cap.subcommand())
                        && "chathistory".equalsIgnoreCase(cap.capability())
                        && cap.enabled()));
  }

  @Test
  void capNewEmitsCapabilityAvailabilityEvent() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxAwayNotifyInputParser parser =
        new PircbotxAwayNotifyInputParser(dummyBot(), "libera", conn, out::add);

    parser.processCommand(
        "*",
        source("server"),
        "CAP",
        ":server CAP me NEW :draft/react",
        List.of("me", "NEW", ":draft/react"),
        ImmutableMap.of());

    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.Ircv3CapabilityChanged cap
                        && "NEW".equalsIgnoreCase(cap.subcommand())
                        && "draft/react".equalsIgnoreCase(cap.capability())));
  }

  @Test
  void setnameAndChghostAreEmitted() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxAwayNotifyInputParser parser =
        new PircbotxAwayNotifyInputParser(dummyBot(), "libera", conn, out::add);

    UserHostmask alice = source("alice");
    parser.processCommand(
        "#ircafe",
        alice,
        "SETNAME",
        ":alice!u@h SETNAME :Alice Liddell",
        List.of(":Alice Liddell"),
        ImmutableMap.of());

    parser.processCommand(
        "#ircafe",
        alice,
        "CHGHOST",
        ":alice!u@h CHGHOST ~alice gateway/example",
        List.of("~alice", "gateway/example"),
        ImmutableMap.of());

    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.UserSetNameObserved sn
                        && "alice".equals(sn.nick())
                        && "Alice Liddell".equals(sn.realName())));
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.UserHostChanged ch
                        && "alice".equals(ch.nick())
                        && "~alice".equals(ch.user())
                        && "gateway/example".equals(ch.host())));
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.UserHostmaskObserved hm
                        && "alice".equals(hm.nick())
                        && "alice!~alice@gateway/example".equals(hm.hostmask())));
  }

  @Test
  void extendedJoinEmitsAccountAndRealNameSignals() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> out = new ArrayList<>();
    PircBotX bot = dummyBot();
    bot.getUserChannelDao().createChannel("#ircafe");
    PircbotxAwayNotifyInputParser parser =
        new PircbotxAwayNotifyInputParser(bot, "libera", conn, out::add);

    parser.processCommand(
        "#ircafe",
        source("alice"),
        "JOIN",
        ":alice!u@h JOIN #ircafe alice-account :Alice Liddell",
        List.of("#ircafe", "alice-account", ":Alice Liddell"),
        ImmutableMap.of());

    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                    e ->
                    e instanceof IrcEvent.UserAccountStateObserved ac
                        && "alice".equals(ac.nick())
                        && IrcEvent.AccountState.LOGGED_IN == ac.accountState()
                        && "alice-account".equals(ac.accountName())));
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                    e ->
                    e instanceof IrcEvent.UserSetNameObserved sn
                        && "alice".equals(sn.nick())
                        && "Alice Liddell".equals(sn.realName())));
  }

  @Test
  void typingReplyAndReactTagsAreObservedOnTaggedMessage() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxAwayNotifyInputParser parser =
        new PircbotxAwayNotifyInputParser(dummyBot(), "libera", conn, out::add);

    parser.processCommand(
        "#ircafe",
        source("bob"),
        "TAGMSG",
        "@typing=active;+draft/reply=abc123;+draft/react=:+1;+msgid=xyz :bob!u@h TAGMSG #ircafe",
        List.of("#ircafe"),
        ImmutableMap.of(
            "typing", "active",
            "draft/reply", "abc123",
            "draft/react", ":+1",
            "msgid", "xyz"));

    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.UserTypingObserved t
                        && "bob".equals(t.from())
                        && "#ircafe".equals(t.target())
                        && "active".equals(t.state())));
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.MessageReplyObserved r
                        && "bob".equals(r.from())
                        && "#ircafe".equals(r.target())
                        && "abc123".equals(r.replyToMsgId())));
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.MessageReactObserved r
                        && "bob".equals(r.from())
                        && "#ircafe".equals(r.target())
                        && ":+1".equals(r.reaction())
                        && "abc123".equals(r.messageId())));
  }

  @Test
  void markreadWithoutSourceStillEmitsReadMarker() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxAwayNotifyInputParser parser =
        new PircbotxAwayNotifyInputParser(dummyBot(), "libera", conn, out::add);

    parser.processCommand(
        "#ircafe",
        source("server"),
        "MARKREAD",
        "MARKREAD #ircafe :2026-02-16T12:30:00.000Z",
        List.of("#ircafe", ":2026-02-16T12:30:00.000Z"),
        ImmutableMap.of());

    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.ReadMarkerObserved rm
                        && "server".equals(rm.from())
                        && "#ircafe".equals(rm.target())
                        && "2026-02-16T12:30:00.000Z".equals(rm.marker())));
  }

  @Test
  void standardReplyCommandEmitsStructuredEventWithIdentityMetadata() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxAwayNotifyInputParser parser =
        new PircbotxAwayNotifyInputParser(dummyBot(), "libera", conn, out::add);

    parser.processCommand(
        "*",
        source("server"),
        "FAIL",
        "@label=req-42;msgid=srv-1 :server FAIL CHATHISTORY INVALID_PARAMS timestamp=bad :Invalid selector",
        List.of("CHATHISTORY", "INVALID_PARAMS", "timestamp=bad", ":Invalid selector"),
        ImmutableMap.of("label", "req-42", "msgid", "srv-1"));

    IrcEvent.StandardReply ev =
        out.stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.StandardReply.class::isInstance)
            .map(IrcEvent.StandardReply.class::cast)
            .findFirst()
            .orElse(null);

    assertTrue(ev != null);
    assertEquals(IrcEvent.StandardReplyKind.FAIL, ev.kind());
    assertEquals("CHATHISTORY", ev.command());
    assertEquals("INVALID_PARAMS", ev.code());
    assertEquals("timestamp=bad", ev.context());
    assertEquals("Invalid selector", ev.description());
    assertEquals("srv-1", ev.messageId());
    assertEquals("req-42", ev.ircv3Tags().get("label"));
  }

  private static UserHostmask source(String nick) {
    UserHostmask s = mock(UserHostmask.class);
    when(s.getNick()).thenReturn(nick);
    return s;
  }

  private static PircBotX dummyBot() {
    Configuration configuration =
        new Configuration.Builder()
            .setName("ircafe-test")
            .addServer("example.invalid", 6667)
            .buildConfiguration();
    return new PircBotX(configuration);
  }
}

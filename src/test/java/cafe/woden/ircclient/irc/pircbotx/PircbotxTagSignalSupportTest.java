package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxTagSignalSupportTest {

  @Test
  void emitsTaggedMessageSignalsForChannelTarget() {
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxTagSignalSupport support = new PircbotxTagSignalSupport("libera", out::add);

    support.emitObservedSignals(
        Instant.parse("2026-03-22T12:00:00Z"),
        "bob",
        "#ircafe",
        "TAGMSG",
        List.of("#ircafe"),
        ImmutableMap.of(
            "typing", "active",
            "draft/reply", "abc123",
            "draft/react", ":+1",
            "draft/delete", "abc123"));

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
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.MessageRedactionObserved r
                        && "bob".equals(r.from())
                        && "#ircafe".equals(r.target())
                        && "abc123".equals(r.messageId())));
  }

  @Test
  void channelContextOverridesDirectMessageTargetAndTagLookupUnescapesValues() {
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxTagSignalSupport support = new PircbotxTagSignalSupport("libera", out::add);

    support.emitObservedSignals(
        Instant.parse("2026-03-22T12:05:00Z"),
        "bob",
        "me",
        "TAGMSG",
        List.of("me"),
        ImmutableMap.of(
            "+draft/channel-context", "#ircafe",
            "draft/unreact", ":+1:",
            "+draft/reply", "abc123",
            "+read-marker", "timestamp=2026-03-22T12\\:05\\:00Z"));

    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.MessageUnreactObserved r
                        && "bob".equals(r.from())
                        && "#ircafe".equals(r.target())
                        && ":+1:".equals(r.reaction())
                        && "abc123".equals(r.messageId())));
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.ReadMarkerObserved rm
                        && "bob".equals(rm.from())
                        && "#ircafe".equals(rm.target())
                        && "timestamp=2026-03-22T12;05;00Z".equals(rm.marker())));
    assertEquals(
        "abc123",
        PircbotxTagSignalSupport.firstTag(
            ImmutableMap.of("+draft/reply", "abc123"), "draft/reply", "+draft/reply"));
  }
}

package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxStandardReplySupportTest {

  @Test
  void emitsStructuredStandardReplyEvent() {
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxStandardReplySupport support = new PircbotxStandardReplySupport("libera", out::add);

    boolean handled =
        support.emitIfSupported(
            Instant.parse("2026-03-22T12:15:00Z"),
            "FAIL",
            "@label=req-42;msgid=srv-1 :server FAIL CHATHISTORY INVALID_PARAMS timestamp=bad :Invalid selector",
            List.of("CHATHISTORY", "INVALID_PARAMS", "timestamp=bad", ":Invalid selector"),
            ImmutableMap.of("label", "req-42", "msgid", "srv-1"));

    assertTrue(handled);
    IrcEvent.StandardReply reply =
        out.stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.StandardReply.class::isInstance)
            .map(IrcEvent.StandardReply.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(IrcEvent.StandardReplyKind.FAIL, reply.kind());
    assertEquals("CHATHISTORY", reply.command());
    assertEquals("INVALID_PARAMS", reply.code());
    assertEquals("timestamp=bad", reply.context());
    assertEquals("Invalid selector", reply.description());
    assertEquals("srv-1", reply.messageId());
    assertEquals("req-42", reply.ircv3Tags().get("label"));
  }

  @Test
  void ignoresNonStandardReplyCommands() {
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxStandardReplySupport support = new PircbotxStandardReplySupport("libera", out::add);

    boolean handled =
        support.emitIfSupported(
            Instant.parse("2026-03-22T12:20:00Z"),
            "PRIVMSG",
            ":bob!u@h PRIVMSG #ircafe :hello",
            List.of("#ircafe", ":hello"),
            ImmutableMap.of());

    assertFalse(handled);
    assertTrue(out.isEmpty());
  }
}

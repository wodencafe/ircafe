package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.irc.pircbotx.parse.ParsedCapLine;
import org.junit.jupiter.api.Test;

class PircbotxMultilineCapStateSupportTest {

  private final PircbotxMultilineCapStateSupport support = new PircbotxMultilineCapStateSupport();

  @Test
  void ackWithExplicitLimitsUpdatesNegotiatedAndOfferedValues() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");

    support.observe(
        ParsedCapLine.parse(
            "ACK",
            ":multiline=max-bytes=4096,max-lines=5 draft/multiline=max-bytes=2048,max-lines=3"),
        conn);

    assertEquals(4096L, conn.multilineOfferedMaxBytes.get());
    assertEquals(5L, conn.multilineOfferedMaxLines.get());
    assertEquals(4096L, conn.multilineMaxBytes.get());
    assertEquals(5L, conn.multilineMaxLines.get());
    assertEquals(2048L, conn.draftMultilineOfferedMaxBytes.get());
    assertEquals(3L, conn.draftMultilineOfferedMaxLines.get());
    assertEquals(2048L, conn.draftMultilineMaxBytes.get());
    assertEquals(3L, conn.draftMultilineMaxLines.get());
  }

  @Test
  void ackWithoutExplicitLimitsReusesPreviouslyOfferedValues() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    support.observe(ParsedCapLine.parse("LS", ":multiline=max-bytes=3072,max-lines=4"), conn);

    support.observe(ParsedCapLine.parse("ACK", ":multiline"), conn);

    assertEquals(3072L, conn.multilineMaxBytes.get());
    assertEquals(4L, conn.multilineMaxLines.get());
  }

  @Test
  void delClearsNegotiatedAndOfferedValues() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    support.observe(ParsedCapLine.parse("ACK", ":multiline=max-bytes=3072,max-lines=4"), conn);

    support.observe(ParsedCapLine.parse("DEL", ":multiline"), conn);

    assertEquals(0L, conn.multilineOfferedMaxBytes.get());
    assertEquals(0L, conn.multilineOfferedMaxLines.get());
    assertEquals(0L, conn.multilineMaxBytes.get());
    assertEquals(0L, conn.multilineMaxLines.get());
  }
}

package cafe.woden.ircclient.irc.pircbotx.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
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

    assertEquals(4096L, conn.multilineOfferedMaxBytes(false));
    assertEquals(5L, conn.multilineOfferedMaxLines(false));
    assertEquals(4096L, conn.capabilitySnapshot().multilineMaxBytes());
    assertEquals(5L, conn.capabilitySnapshot().multilineMaxLines());
    assertEquals(2048L, conn.multilineOfferedMaxBytes(true));
    assertEquals(3L, conn.multilineOfferedMaxLines(true));
    assertEquals(2048L, conn.capabilitySnapshot().draftMultilineMaxBytes());
    assertEquals(3L, conn.capabilitySnapshot().draftMultilineMaxLines());
  }

  @Test
  void ackWithoutExplicitLimitsReusesPreviouslyOfferedValues() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    support.observe(ParsedCapLine.parse("LS", ":multiline=max-bytes=3072,max-lines=4"), conn);

    support.observe(ParsedCapLine.parse("ACK", ":multiline"), conn);

    assertEquals(3072L, conn.capabilitySnapshot().multilineMaxBytes());
    assertEquals(4L, conn.capabilitySnapshot().multilineMaxLines());
  }

  @Test
  void delClearsNegotiatedAndOfferedValues() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    support.observe(ParsedCapLine.parse("ACK", ":multiline=max-bytes=3072,max-lines=4"), conn);

    support.observe(ParsedCapLine.parse("DEL", ":multiline"), conn);

    assertEquals(0L, conn.multilineOfferedMaxBytes(false));
    assertEquals(0L, conn.multilineOfferedMaxLines(false));
    assertEquals(0L, conn.capabilitySnapshot().multilineMaxBytes());
    assertEquals(0L, conn.capabilitySnapshot().multilineMaxLines());
  }
}

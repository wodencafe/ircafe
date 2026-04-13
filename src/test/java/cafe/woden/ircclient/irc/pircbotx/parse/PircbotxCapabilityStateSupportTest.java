package cafe.woden.ircclient.irc.pircbotx.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import org.junit.jupiter.api.Test;

class PircbotxCapabilityStateSupportTest {

  @Test
  void aliasCapabilitiesUpdateSharedConnectionFlags() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PircbotxCapabilityStateSupport support = new PircbotxCapabilityStateSupport("libera", conn);

    support.apply("chathistory", true, "ACK");
    support.apply("draft/read-marker", true, "ACK");
    support.apply("draft/extended-monitor", true, "ACK");

    assertTrue(conn.capabilitySnapshot().chatHistoryCapAcked());
    assertTrue(conn.capabilitySnapshot().readMarkerCapAcked());
    assertTrue(conn.capabilitySnapshot().extendedMonitorCapAcked());

    support.apply("draft/chathistory", false, "DEL");
    support.apply("read-marker", false, "DEL");
    support.apply("extended-monitor", false, "DEL");

    assertFalse(conn.capabilitySnapshot().chatHistoryCapAcked());
    assertFalse(conn.capabilitySnapshot().readMarkerCapAcked());
    assertFalse(conn.capabilitySnapshot().extendedMonitorCapAcked());
  }

  @Test
  void tagOnlyCapabilityNamesDoNotChangeTrackedCapabilityState() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PircbotxCapabilityStateSupport support = new PircbotxCapabilityStateSupport("libera", conn);

    PircbotxConnectionState.CapabilitySnapshot before = conn.capabilitySnapshot();

    support.apply("draft/typing", true, "ACK");
    support.apply("typing", false, "DEL");
    support.apply("draft/reply", true, "ACK");
    support.apply("draft/react", true, "ACK");
    support.apply("draft/unreact", true, "ACK");
    support.apply("draft/channel-context", true, "ACK");

    assertEquals(before, conn.capabilitySnapshot());
  }

  @Test
  void disablingMultilineCapabilitiesClearsNegotiatedLimits() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PircbotxCapabilityStateSupport support = new PircbotxCapabilityStateSupport("libera", conn);

    conn.setNegotiatedMultilineMaxBytes(false, 4096L);
    conn.setNegotiatedMultilineMaxLines(false, 5L);
    conn.setNegotiatedMultilineMaxBytes(true, 2048L);
    conn.setNegotiatedMultilineMaxLines(true, 3L);

    support.apply("multiline", false, "DEL");
    support.apply("draft/multiline", false, "DEL");

    assertFalse(conn.capabilitySnapshot().multilineCapAcked());
    assertFalse(conn.capabilitySnapshot().draftMultilineCapAcked());
    assertTrue(conn.capabilitySnapshot().multilineMaxBytes() == 0L);
    assertTrue(conn.capabilitySnapshot().multilineMaxLines() == 0L);
    assertTrue(conn.capabilitySnapshot().draftMultilineMaxBytes() == 0L);
    assertTrue(conn.capabilitySnapshot().draftMultilineMaxLines() == 0L);
  }
}

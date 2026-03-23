package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PircbotxCapabilityStateSupportTest {

  @Test
  void aliasCapabilitiesUpdateSharedConnectionFlags() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PircbotxCapabilityStateSupport support = new PircbotxCapabilityStateSupport("libera", conn);

    support.apply("chathistory", true, "ACK");
    support.apply("draft/typing", true, "ACK");
    support.apply("draft/read-marker", true, "ACK");
    support.apply("draft/extended-monitor", true, "ACK");

    assertTrue(conn.chatHistoryCapAcked.get());
    assertTrue(conn.typingCapAcked.get());
    assertTrue(conn.readMarkerCapAcked.get());
    assertTrue(conn.extendedMonitorCapAcked.get());

    support.apply("draft/chathistory", false, "DEL");
    support.apply("typing", false, "DEL");
    support.apply("read-marker", false, "DEL");
    support.apply("extended-monitor", false, "DEL");

    assertFalse(conn.chatHistoryCapAcked.get());
    assertFalse(conn.typingCapAcked.get());
    assertFalse(conn.readMarkerCapAcked.get());
    assertFalse(conn.extendedMonitorCapAcked.get());
  }

  @Test
  void disablingMultilineCapabilitiesClearsNegotiatedLimits() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PircbotxCapabilityStateSupport support = new PircbotxCapabilityStateSupport("libera", conn);

    conn.multilineMaxBytes.set(4096L);
    conn.multilineMaxLines.set(5L);
    conn.draftMultilineMaxBytes.set(2048L);
    conn.draftMultilineMaxLines.set(3L);

    support.apply("multiline", false, "DEL");
    support.apply("draft/multiline", false, "DEL");

    assertFalse(conn.multilineCapAcked.get());
    assertFalse(conn.draftMultilineCapAcked.get());
    assertTrue(conn.multilineMaxBytes.get() == 0L);
    assertTrue(conn.multilineMaxLines.get() == 0L);
    assertTrue(conn.draftMultilineMaxBytes.get() == 0L);
    assertTrue(conn.draftMultilineMaxLines.get() == 0L);
  }
}

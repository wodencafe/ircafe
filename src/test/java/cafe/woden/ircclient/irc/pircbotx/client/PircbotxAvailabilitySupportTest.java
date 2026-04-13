package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;

class PircbotxAvailabilitySupportTest {

  private final PircbotxAvailabilitySupport support = new PircbotxAvailabilitySupport();

  @Test
  void draftUnreactAvailabilityFallsBackToDraftReact() {
    PircbotxConnectionState connection = liveConnection();
    connection.setMessageTagsCapAcked(true);

    assertTrue(support.isDraftUnreactAvailable(connection));
  }

  @Test
  void draftReplyAvailabilityUsesMessageTags() {
    PircbotxConnectionState connection = liveConnection();
    connection.setMessageTagsCapAcked(true);

    assertTrue(support.isDraftReplyAvailable(connection));
    assertTrue(support.isDraftReactAvailable(connection));
  }

  @Test
  void negotiatedMultilineMaxLinesUsesDraftValueAndClamps() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.setDraftMultilineCapAcked(true);
    connection.setDraftMultilineLimits(0L, (long) Integer.MAX_VALUE + 25L);

    assertEquals(Integer.MAX_VALUE, support.negotiatedMultilineMaxLines(connection));
  }

  @Test
  void monitorAvailabilityAllowsServerSupportWithoutCapabilityAck() {
    PircbotxConnectionState connection = liveConnection();
    connection.updateMonitorSupport(true, 0L);

    assertTrue(support.isMonitorAvailable(connection));
  }

  @Test
  void negotiatedMonitorLimitBoundsNegativeAndHugeValues() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.updateMonitorSupport(false, -5L);
    assertEquals(0, support.negotiatedMonitorLimit(connection));

    connection.updateMonitorSupport(false, (long) Integer.MAX_VALUE + 10L);
    assertEquals(Integer.MAX_VALUE, support.negotiatedMonitorLimit(connection));
  }

  @Test
  void zncBouncerDetectedIncludesPlaybackCapability() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.setZncPlaybackCapAcked(true);

    assertTrue(support.isZncBouncerDetected(connection));
  }

  @Test
  void echoMessageAvailabilityRequiresLiveBot() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.setEchoMessageCapAcked(true);

    assertFalse(support.isEchoMessageAvailable(connection));
  }

  private static PircbotxConnectionState liveConnection() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.setBot(mock(PircBotX.class));
    return connection;
  }
}

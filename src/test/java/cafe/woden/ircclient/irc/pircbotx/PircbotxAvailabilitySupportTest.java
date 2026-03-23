package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;

class PircbotxAvailabilitySupportTest {

  private final PircbotxAvailabilitySupport support = new PircbotxAvailabilitySupport();

  @Test
  void draftUnreactAvailabilityFallsBackToDraftReact() {
    PircbotxConnectionState connection = liveConnection();
    connection.draftReactCapAcked.set(true);

    assertTrue(support.isDraftUnreactAvailable(connection));
  }

  @Test
  void negotiatedMultilineMaxLinesUsesDraftValueAndClamps() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.draftMultilineCapAcked.set(true);
    connection.draftMultilineMaxLines.set((long) Integer.MAX_VALUE + 25L);

    assertEquals(Integer.MAX_VALUE, support.negotiatedMultilineMaxLines(connection));
  }

  @Test
  void monitorAvailabilityAllowsServerSupportWithoutCapabilityAck() {
    PircbotxConnectionState connection = liveConnection();
    connection.monitorSupported.set(true);

    assertTrue(support.isMonitorAvailable(connection));
  }

  @Test
  void negotiatedMonitorLimitBoundsNegativeAndHugeValues() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.monitorMaxTargets.set(-5L);
    assertEquals(0, support.negotiatedMonitorLimit(connection));

    connection.monitorMaxTargets.set((long) Integer.MAX_VALUE + 10L);
    assertEquals(Integer.MAX_VALUE, support.negotiatedMonitorLimit(connection));
  }

  @Test
  void zncBouncerDetectedIncludesPlaybackCapability() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.zncPlaybackCapAcked.set(true);

    assertTrue(support.isZncBouncerDetected(connection));
  }

  @Test
  void echoMessageAvailabilityRequiresLiveBot() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.echoMessageCapAcked.set(true);

    assertFalse(support.isEchoMessageAvailable(connection));
  }

  private static PircbotxConnectionState liveConnection() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.botRef.set(mock(PircBotX.class));
    return connection;
  }
}

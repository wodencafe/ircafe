package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputRaw;

class PircbotxLagProbeSupportTest {

  private final PircbotxLagProbeSupport support = new PircbotxLagProbeSupport();

  @Test
  void requestLagProbeSendsPingAndStoresProbeToken() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendRaw()).thenReturn(outputRaw);
    connection.setBot(bot);
    connection.markRegistrationComplete();

    support.requestLagProbe("libera", connection);

    verify(outputRaw)
        .rawLine(argThat(value -> value != null && value.startsWith("PING :ircafe-lag-")));
    assertTrue(connection.currentLagProbeToken().startsWith("ircafe-lag-"));
    assertTrue(connection.currentLagProbeSentAtMs() > 0L);
  }

  @Test
  void lagProbeReadyRequiresLiveBotAndCompletedRegistration() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");

    assertFalse(support.isLagProbeReady(connection));

    connection.setBot(mock(PircBotX.class));
    assertFalse(support.isLagProbeReady(connection));

    connection.markRegistrationComplete();
    assertTrue(support.isLagProbeReady(connection));
  }

  @Test
  void lastMeasuredLagMsReturnsFreshObservedSample() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.observePassiveLagSample(125L, 2_000L);

    OptionalLong lag = support.lastMeasuredLagMs(connection, 2_500L);

    assertTrue(lag.isPresent());
    assertEquals(125L, lag.getAsLong());
  }

  @Test
  void lastMeasuredLagMsReturnsEmptyWhenStale() {
    PircbotxConnectionState connection = new PircbotxConnectionState("libera");
    connection.observePassiveLagSample(125L, 2_000L);

    OptionalLong lag = support.lastMeasuredLagMs(connection, 123_001L);

    assertFalse(lag.isPresent());
  }
}

package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;

class PircbotxAwayNotifyInputParserMockVerifyTest {

  @Test
  void replayedRpl324LineEmitsSnapshotModeObservation() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    @SuppressWarnings("unchecked")
    Consumer<ServerIrcEvent> sink = mock(Consumer.class);
    PircbotxAwayNotifyInputParser parser =
        new PircbotxAwayNotifyInputParser(
            dummyBot(), "libera", conn, sink, new Ircv3StsPolicyService());

    String line = ":osmium.libera.chat 324 me ##politics +CLTcnrt";
    List<String> parsed = List.of("me", "##politics", "+CLTcnrt");

    parser.processServerResponse(324, line, parsed);

    ArgumentCaptor<ServerIrcEvent> captor = ArgumentCaptor.forClass(ServerIrcEvent.class);
    verify(sink, atLeastOnce()).accept(captor.capture());
    IrcEvent.ChannelModeObserved observed =
        captor.getAllValues().stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.ChannelModeObserved.class::isInstance)
            .map(IrcEvent.ChannelModeObserved.class::cast)
            .findFirst()
            .orElse(null);

    assertNotNull(observed);
    assertEquals("##politics", observed.channel());
    assertEquals("+CLTcnrt", observed.details());
    assertEquals(IrcEvent.ChannelModeKind.SNAPSHOT, observed.kind());
    assertTrue(
        observed.provenance() == IrcEvent.ChannelModeProvenance.NUMERIC_324
            || observed.provenance() == IrcEvent.ChannelModeProvenance.NUMERIC_324_FALLBACK);
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

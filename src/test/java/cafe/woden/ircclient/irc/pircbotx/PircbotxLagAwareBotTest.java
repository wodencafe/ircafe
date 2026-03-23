package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.Test;
import org.pircbotx.Configuration;

class PircbotxLagAwareBotTest {

  @Test
  void outboundPingTokenRecognizesBuiltInAndCustomPingFormats() {
    assertEquals("1742097600", PircbotxLagAwareBot.outboundPingToken("PING 1742097600"));
    assertEquals(
        "ircafe-lag-token", PircbotxLagAwareBot.outboundPingToken("PING :ircafe-lag-token"));
    assertEquals("", PircbotxLagAwareBot.outboundPingToken("PRIVMSG #ircafe :hello"));
  }

  @Test
  void sendRawLineToServerRecordsOutboundPingTokens() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    TestLagAwareBot bot = new TestLagAwareBot(configuration());
    bot.setLagProbeObserver(conn::beginLagProbe);
    bot.setOutputWriter(new StringWriter());

    bot.sendLine("PING 1742097600");

    assertEquals("1742097600", conn.currentLagProbeToken());
    assertTrue(conn.currentLagProbeSentAtMs() > 0L);
  }

  private static Configuration configuration() {
    return new Configuration.Builder()
        .setName("ircafe-test")
        .addServer("example.invalid", 6667)
        .buildConfiguration();
  }

  private static final class TestLagAwareBot extends PircbotxLagAwareBot {
    private TestLagAwareBot(Configuration configuration) {
      super(configuration);
    }

    void setOutputWriter(Writer writer) {
      this.outputWriter = writer;
    }

    void sendLine(String line) throws IOException {
      super.sendRawLineToServer(line);
    }
  }
}

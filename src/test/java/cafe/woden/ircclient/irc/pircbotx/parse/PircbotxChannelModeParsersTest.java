package cafe.woden.ircclient.irc.pircbotx.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxChannelModeParsersTest {

  @Test
  void parseRpl324ParsesRawLine() {
    PircbotxChannelModeParsers.ParsedRpl324 parsed =
        PircbotxChannelModeParsers.parseRpl324(":server 324 me #ircafe +nt key");

    assertNotNull(parsed);
    assertEquals("#ircafe", parsed.channel());
    assertEquals("+nt key", parsed.details());
  }

  @Test
  void parseRpl324FallbackRecoversFromParsedTokens() {
    PircbotxChannelModeParsers.ParsedRpl324 parsed =
        PircbotxChannelModeParsers.parseRpl324Fallback(
            null, List.of("me", "#ircafe", "+nt", ":key"));

    assertNotNull(parsed);
    assertEquals("#ircafe", parsed.channel());
    assertEquals("+nt key", parsed.details());
  }

  @Test
  void parseRpl324FallbackReturnsNullWhenChannelMissing() {
    assertNull(PircbotxChannelModeParsers.parseRpl324Fallback(null, List.of("me", " ", "+nt")));
  }
}

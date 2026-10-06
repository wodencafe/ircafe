package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreDisplayTextTest {
  private static final Instant AT = Instant.parse("2026-03-03T12:00:00Z");

  @Test
  void numericResponsePreservesRawEnvelopeAndMetadataWhileRenderingTrailingText() {
    String raw = "@label=whois :irc.example 311 me alice user host * :Alice Smith";
    Map<String, String> tags = Map.of("label", "whois");
    assertEquals(
        new IrcEvent.ServerResponseLine(AT, 311, "Alice Smith", raw, "123", tags),
        QuasselCoreDisplayText.serverResponse(AT, "display fallback", raw, "123", tags));
  }

  @Test
  void statusTextUsesDisplayFallbackWhenRawTextIsAbsent() {
    assertEquals(
        new IrcEvent.ServerResponseLine(
            AT, 0, "Connected to Core", "Connected to Core", "", Map.of()),
        QuasselCoreDisplayText.serverResponse(AT, " Connected to Core ", null, "", null));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "311 me alice user host * :Alice Smith|311",
        ":irc.example 001 me :Welcome|1",
        "@label=x :irc.example 730 me :alice|730",
        "1234 malformed|0",
        ":server|0",
        "@label=only|0",
        "Server is connected|0"
      })
  void distinguishesNumericsFromOrdinaryStatusText(String raw, int code) {
    assertEquals(code, QuasselCoreDisplayText.extractNumericCode(raw));
  }

  @Test
  void numericWithoutTrailingTextKeepsParametersAsTheRenderedMessage() {
    var response =
        QuasselCoreDisplayText.serverResponse(
            AT, "fallback", ":irc.example 005 me MONITOR=100", "", Map.of());
    assertEquals(5, response.code());
    assertEquals("me MONITOR=100", response.message());
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "alice is now known as bob|bob",
        "alice IS NOW KNOWN AS bob extra|bob",
        "bob|bob",
        "changed nick bob|bob"
      })
  void extractsNickChangesFromCoreDisplayFormats(String content, String nick) {
    assertEquals(nick, QuasselCoreDisplayText.parseNickChange(content, "alice"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void emptyNickChangeRetainsCurrentNick(String content) {
    assertEquals("alice", QuasselCoreDisplayText.parseNickChange(content, "alice"));
  }

  @Test
  void decodesTopicAndModeDisplayText() {
    assertEquals(
        "New topic", QuasselCoreDisplayText.parseTopic("alice changed topic to \"New topic\""));
    assertEquals("New topic", QuasselCoreDisplayText.parseTopic("'New topic'"));
    assertEquals("+o bob", QuasselCoreDisplayText.parseModeDetails("alice set mode +o bob"));
    assertEquals("+nt", QuasselCoreDisplayText.parseModeDetails("+nt"));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "alice kicked bob (cleanup)|bob|cleanup",
        "kicked bob (cleanup)|bob|cleanup",
        "bob cleanup|bob|cleanup",
        "bob|bob|"
      })
  void decodesKickTargetAndReason(String content, String nick, String reason) {
    assertEquals(
        new QuasselCoreDisplayText.KickDetails(nick, reason == null ? "" : reason),
        QuasselCoreDisplayText.parseKickDetails(content));
  }

  @Test
  void normalizesPartAndQuitReasonsWithoutDiscardingPlainText() {
    assertEquals("Leaving", QuasselCoreDisplayText.normalizeReason("alice has left (Leaving)"));
    assertEquals("Leaving", QuasselCoreDisplayText.normalizeReason(" Leaving "));
    assertEquals("", QuasselCoreDisplayText.normalizeReason(null));
  }

  @Test
  void extractsInvitationChannelAndSenderNick() {
    assertEquals(
        "#cafe", QuasselCoreDisplayText.firstChannelToken("Invited to (#cafe) #cafe later"));
    assertEquals("alice", QuasselCoreDisplayText.extractNick(" alice!user@host "));
    assertEquals("irc.example", QuasselCoreDisplayText.extractNick("irc.example"));
    assertEquals("", QuasselCoreDisplayText.extractNick(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"#cafe", "&local", "+modeless", "!safe"})
  void recognizesTheExistingChannelPrefixes(String channel) {
    assertTrue(QuasselCoreDisplayText.looksLikeChannel(channel));
    assertFalse(QuasselCoreDisplayText.looksLikeChannel(channel.substring(0, 1)));
    assertFalse(QuasselCoreDisplayText.looksLikeChannel("alice"));
  }
}

package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxListParsersTest {

  @Test
  void formatsRpl322ListEntryWithNickTarget() {
    String rendered =
        PircbotxListParsers.tryFormatListNumeric(
            "322", List.of("me", "#ircafe", "128"), "IRCafe development chat", "me");

    assertEquals("#ircafe (128): IRCafe development chat", rendered);
  }

  @Test
  void formatsRpl322ListEntryWhenServerUsesStarTarget() {
    String rendered =
        PircbotxListParsers.tryFormatListNumeric(
            "322", List.of("*", "#help", "9"), "help and support", "me");

    assertEquals("#help (9): help and support", rendered);
  }

  @Test
  void formatsRpl322WithoutTopic() {
    String rendered =
        PircbotxListParsers.tryFormatListNumeric("322", List.of("me", "#quiet", "4"), "", "me");

    assertEquals("#quiet (4)", rendered);
  }

  @Test
  void formatsRpl322PreservesExplicitZeroVisibleUsers() {
    String rendered =
        PircbotxListParsers.tryFormatListNumeric("322", List.of("me", "#empty", "0"), "", "me");

    assertEquals("#empty (0)", rendered);
  }

  @Test
  void returnsNullForMalformedRpl322Entry() {
    String rendered =
        PircbotxListParsers.tryFormatListNumeric(
            "322", List.of("me", "not-a-channel", "10"), "broken", "me");

    assertNull(rendered);
  }

  @Test
  void formatsListStartAndEndNumerics() {
    assertEquals(
        "Channel list follows",
        PircbotxListParsers.tryFormatListNumeric("321", List.of("me", "Channel"), "", "me"));
    assertEquals(
        "End of /LIST", PircbotxListParsers.tryFormatListNumeric("323", List.of("me"), "", "me"));
    assertEquals(
        "End of channel listing",
        PircbotxListParsers.tryFormatListNumeric(
            "323", List.of("me"), "End of channel listing", "me"));
  }

  @Test
  void returnsNullForNonListNumeric() {
    assertNull(
        PircbotxListParsers.tryFormatListNumeric(
            "401", List.of("me", "nick"), "No such nick", "me"));
  }

  @Test
  void parseListEntryReturnsStructuredRow() {
    PircbotxListParsers.ListEntry entry =
        PircbotxListParsers.parseListEntry(
            "322", List.of("me", "#ircafe", "128"), "IRCafe development chat", "me");

    assertNotNull(entry);
    assertEquals("#ircafe", entry.channel());
    assertEquals(128, entry.visibleUsers());
    assertEquals("IRCafe development chat", entry.topic());
  }
}

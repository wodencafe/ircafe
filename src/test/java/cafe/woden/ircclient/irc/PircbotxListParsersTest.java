package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void parseListEntryFindsChannelEvenWithoutNickHint() {
    PircbotxListParsers.ListEntry entry =
        PircbotxListParsers.parseListEntry(
            "322", List.of("wodencafe2", "#libera", "42833"), "The Libera.Chat Community", "");

    assertNotNull(entry);
    assertEquals("#libera", entry.channel());
    assertEquals(42833, entry.visibleUsers());
    assertTrue(entry.hasVisibleUsers());
  }

  @Test
  void parseAlisNoticeEntryReadsListRows() {
    PircbotxListParsers.ListEntry entry =
        PircbotxListParsers.parseAlisNoticeEntry(
            "ALIS", "#java 1200 [+nt] Java discussion and help");

    assertNotNull(entry);
    assertEquals("#java", entry.channel());
    assertEquals(1200, entry.visibleUsers());
    assertEquals("[+nt] Java discussion and help", entry.topic());
  }

  @Test
  void parseAlisNoticeEntryAcceptsServerSenderFallback() {
    PircbotxListParsers.ListEntry entry =
        PircbotxListParsers.parseAlisNoticeEntry("server", "#java 1200 :Java discussion");

    assertNotNull(entry);
    assertEquals("#java", entry.channel());
    assertEquals(1200, entry.visibleUsers());
  }

  @Test
  void parseAlisNoticeEntryIgnoresHelpLines() {
    PircbotxListParsers.ListEntry entry =
        PircbotxListParsers.parseAlisNoticeEntry("alis", "Syntax: LIST <pattern> [options]");
    assertNull(entry);
  }

  @Test
  void parseAlisNoticeEndSummaryRecognizesEndMarker() {
    assertEquals(
        "End of output.", PircbotxListParsers.parseAlisNoticeEndSummary("alis", "End of output."));
    assertEquals(
        "End of output.",
        PircbotxListParsers.parseAlisNoticeEndSummary("server", "End of output."));
    assertNull(PircbotxListParsers.parseAlisNoticeEndSummary("alis", "Returning maximum of 64"));
  }

  @Test
  void parseBanListEntryAndEndSummary() {
    PircbotxListParsers.BanListEntry entry =
        PircbotxListParsers.parseBanListEntry(
            "367", List.of("me", "#ircafe", "*!*@bad.host", "ChanOp", "1739900000"));
    assertNotNull(entry);
    assertEquals("#ircafe", entry.channel());
    assertEquals("*!*@bad.host", entry.mask());
    assertEquals("ChanOp", entry.setBy());
    assertEquals(1_739_900_000L, entry.setAtEpochSeconds());

    assertEquals(
        "#ircafe",
        PircbotxListParsers.parseBanListEndChannel(
            "368", List.of("me", "#ircafe", "End of Channel Ban List")));
    assertEquals(
        "End of Channel Ban List",
        PircbotxListParsers.parseBanListEndSummary("368", "End of Channel Ban List"));
  }
}

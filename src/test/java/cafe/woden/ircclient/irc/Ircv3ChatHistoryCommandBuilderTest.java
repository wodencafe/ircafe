package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class Ircv3ChatHistoryCommandBuilderTest {

  @Test
  void buildsBeforeWithTimestampSelector() {
    String line =
        Ircv3ChatHistoryCommandBuilder.buildBeforeByTimestamp(
            "#ircafe", Instant.parse("2026-02-16T12:34:56.789Z"), 75);
    assertEquals("CHATHISTORY BEFORE #ircafe timestamp=2026-02-16T12:34:56.789Z 75", line);
  }

  @Test
  void buildsBeforeWithMessageIdSelector() {
    String line = Ircv3ChatHistoryCommandBuilder.buildBeforeByMessageId("#ircafe", "abc123", 20);
    assertEquals("CHATHISTORY BEFORE #ircafe msgid=abc123 20", line);
  }

  @Test
  void normalizesSelectorKeyAndRejectsInvalidSelector() {
    String line =
        Ircv3ChatHistoryCommandBuilder.buildBefore(
            "#ircafe", "TIMESTAMP=2026-02-16T12:34:56.000Z", 10);
    assertEquals("CHATHISTORY BEFORE #ircafe timestamp=2026-02-16T12:34:56.000Z 10", line);

    assertThrows(
        IllegalArgumentException.class,
        () -> Ircv3ChatHistoryCommandBuilder.buildBefore("#ircafe", "bad-selector", 10));
  }

  @Test
  void buildsLatestWithWildcardAndSelector() {
    assertEquals(
        "CHATHISTORY LATEST #ircafe * 25",
        Ircv3ChatHistoryCommandBuilder.buildLatest("#ircafe", "*", 25));
    assertEquals(
        "CHATHISTORY LATEST #ircafe msgid=abc123 30",
        Ircv3ChatHistoryCommandBuilder.buildLatest("#ircafe", "msgid=abc123", 30));
  }

  @Test
  void buildsAroundAndBetween() {
    assertEquals(
        "CHATHISTORY AROUND #ircafe msgid=abc123 40",
        Ircv3ChatHistoryCommandBuilder.buildAround("#ircafe", "msgid=abc123", 40));
    assertEquals(
        "CHATHISTORY BETWEEN #ircafe timestamp=2026-02-16T00:00:00.000Z * 60",
        Ircv3ChatHistoryCommandBuilder.buildBetween(
            "#ircafe", "timestamp=2026-02-16T00:00:00.000Z", "*", 60));
  }
}

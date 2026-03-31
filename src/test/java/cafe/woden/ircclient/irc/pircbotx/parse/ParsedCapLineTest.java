package cafe.woden.ircclient.irc.pircbotx.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ParsedCapLineTest {

  @Test
  void parseNormalizesActionAndCapabilityTokens() {
    ParsedCapLine parsed = ParsedCapLine.parse("ack", ":message-tags typing");

    assertEquals("ACK", parsed.action());
    assertEquals("message-tags typing", parsed.normalizedCaps());
    assertEquals(List.of("message-tags", "typing"), parsed.tokens());
    assertTrue(parsed.hasTokens());
    assertTrue(parsed.isAction("ACK", "LS"));
  }

  @Test
  void parseDropsBlankTokens() {
    ParsedCapLine parsed = ParsedCapLine.parse("LS", "  :message-tags   typing   ");

    assertEquals(List.of("message-tags", "typing"), parsed.tokens());
  }

  @Test
  void parseHandlesMissingCapList() {
    ParsedCapLine parsed = ParsedCapLine.parse(null, null);

    assertEquals("", parsed.action());
    assertEquals("", parsed.normalizedCaps());
    assertEquals(List.of(), parsed.tokens());
    assertFalse(parsed.hasTokens());
    assertFalse(parsed.isAction("ACK"));
  }
}

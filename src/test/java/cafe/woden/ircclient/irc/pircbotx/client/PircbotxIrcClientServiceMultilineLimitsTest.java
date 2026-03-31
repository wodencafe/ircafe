package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.listener.*;
import cafe.woden.ircclient.irc.playback.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxIrcClientServiceMultilineLimitsTest {

  @Test
  void multilinePayloadUtf8BytesCountsNewlineSeparators() {
    long bytes = PircbotxIrcClientService.multilinePayloadUtf8Bytes(List.of("hello", "world"));
    assertEquals(11L, bytes);
  }

  @Test
  void multilinePayloadUtf8BytesCountsUtf8Codepoints() {
    long bytes = PircbotxIrcClientService.multilinePayloadUtf8Bytes(List.of("🙂", "é"));
    // "🙂" is 4 bytes in UTF-8, "é" is 2 bytes, plus one '\n' separator.
    assertEquals(7L, bytes);
  }

  @Test
  void requireWithinMultilineMaxBytesThrowsWhenExceeded() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PircbotxIrcClientService.requireWithinMultilineMaxBytes(
                    5L, List.of("hello", "world"), "libera"));
    assertEquals(
        "Message exceeds negotiated IRCv3 multiline max-bytes 11 > 5 for libera", ex.getMessage());
  }

  @Test
  void requireWithinMultilineMaxLinesThrowsWhenExceeded() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PircbotxIrcClientService.requireWithinMultilineMaxLines(
                    1L, List.of("hello", "world"), "libera"));
    assertEquals(
        "Message exceeds negotiated IRCv3 multiline max-lines 2 > 1 for libera", ex.getMessage());
  }
}

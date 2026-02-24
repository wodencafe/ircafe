package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ircv3MultilineAccumulatorTest {

  @Test
  void nonBatchLinePassesThroughUnchanged() {
    Ircv3MultilineAccumulator acc = new Ircv3MultilineAccumulator();

    Ircv3MultilineAccumulator.FoldResult out =
        acc.fold(
            "PRIVMSG", "alice", "#ircafe", Instant.EPOCH, "hello", "m1", Map.of("msgid", "m1"));

    assertFalse(out.suppressed());
    assertEquals("hello", out.text());
    assertEquals("m1", out.messageId());
    assertEquals("m1", out.tags().get("msgid"));
  }

  @Test
  void concatChunksAreBufferedUntilFinalLine() {
    Ircv3MultilineAccumulator acc = new Ircv3MultilineAccumulator();

    Ircv3MultilineAccumulator.FoldResult first =
        acc.fold(
            "PRIVMSG",
            "alice",
            "#ircafe",
            Instant.EPOCH,
            "line one",
            "m1",
            Map.of("batch", "b1", "draft/multiline-concat", "1", "msgid", "m1"));
    assertTrue(first.suppressed());

    Ircv3MultilineAccumulator.FoldResult second =
        acc.fold(
            "PRIVMSG",
            "alice",
            "#ircafe",
            Instant.EPOCH.plusSeconds(1),
            "line two",
            "",
            Map.of("batch", "b1"));

    assertFalse(second.suppressed());
    assertEquals("line one\nline two", second.text());
    assertEquals("m1", second.messageId());
    assertTrue(second.tags().containsKey("batch"));
    assertFalse(second.tags().containsKey("draft/multiline-concat"));
    assertFalse(second.tags().containsKey("multiline-concat"));
  }

  @Test
  void batchTaggedNonConcatWithoutBufferPassesThrough() {
    Ircv3MultilineAccumulator acc = new Ircv3MultilineAccumulator();

    Ircv3MultilineAccumulator.FoldResult out =
        acc.fold(
            "NOTICE",
            "server",
            "status",
            Instant.EPOCH,
            "regular batched line",
            "m2",
            Map.of("batch", "history-1", "msgid", "m2"));

    assertFalse(out.suppressed());
    assertEquals("regular batched line", out.text());
    assertEquals("m2", out.messageId());
  }
}

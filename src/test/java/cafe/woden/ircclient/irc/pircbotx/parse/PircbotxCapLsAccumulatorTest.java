package cafe.woden.ircclient.irc.pircbotx.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class PircbotxCapLsAccumulatorTest {
  @Test
  void retainsAllChunksAndValuesWhileAllowingInterleavedTraffic() throws Exception {
    var accumulator = new PircbotxCapLsAccumulator();
    assertNull(accumulator.accept(":server CAP * LS * :message-tags server-time"));
    assertEquals("PING :token", accumulator.accept("PING :token"));
    assertNull(accumulator.accept(":server CAP * LS * :sasl=PLAIN,EXTERNAL"));
    assertEquals(
        ":server CAP * LS :message-tags server-time sasl=PLAIN,EXTERNAL batch",
        accumulator.accept(":server CAP * LS batch"));
    assertEquals(
        ":server CAP me LS :echo-message", accumulator.accept(":server CAP me LS :echo-message"));
  }

  @Test
  void capsMemoryAndClearsConnectionState() throws Exception {
    var accumulator = new PircbotxCapLsAccumulator();
    assertNull(accumulator.accept(":server CAP * LS * :message-tags"));
    assertThrows(
        IOException.class, () -> accumulator.accept(":server CAP * LS * :" + "x".repeat(65_536)));
    assertEquals(":server CAP * LS :batch", accumulator.accept(":server CAP * LS :batch"));
    assertNull(accumulator.accept(":server CAP * LS * :server-time"));
    accumulator.clear();
    assertEquals(":server CAP * LS :", accumulator.accept(":server CAP * LS :"));
  }
}

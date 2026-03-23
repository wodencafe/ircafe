package cafe.woden.ircclient.irc.pircbotx.capability;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxSaslAuthenticateFramingTest {

  private final PircbotxSaslAuthenticateFraming framing = new PircbotxSaslAuthenticateFraming();

  @Test
  void plusWithoutBufferedDataProducesEmptyPayload() throws Exception {
    assertArrayEquals(new byte[0], framing.acceptServerPayload("+").orElseThrow());
  }

  @Test
  void exactChunkWaitsForTerminatorBeforeDecoding() throws Exception {
    byte[] payload = "x".repeat(300).getBytes(StandardCharsets.UTF_8);
    String base64 = Base64.getEncoder().encodeToString(payload);
    assertEquals(400, base64.length());

    assertTrue(framing.acceptServerPayload(base64).isEmpty());
    assertArrayEquals(payload, framing.acceptServerPayload("+").orElseThrow());
  }

  @Test
  void shortChunkDecodesImmediately() throws Exception {
    byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
    String base64 = Base64.getEncoder().encodeToString(payload);

    assertArrayEquals(payload, framing.acceptServerPayload(base64).orElseThrow());
  }

  @Test
  void clientResponseSplitsOnChunkBoundaryAndAppendsTerminator() {
    assertEquals(
        List.of("a".repeat(400), "a".repeat(400), "+"),
        framing.encodeClientResponse("a".repeat(800)));
  }

  @Test
  void emptyClientResponseUsesAuthenticatePlus() {
    assertEquals(List.of("+"), framing.encodeClientResponse(""));
  }
}

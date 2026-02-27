package cafe.woden.ircclient.ignore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IgnoreMaskNormalizerPropertyTest {

  @Test
  void normalizeIsIdempotentAndProducesWhitespaceFreeMasks() {
    Random random = new Random(0x1CAFEEL);
    for (int i = 0; i < 1_000; i++) {
      String input = randomMaskLikeInput(random);
      String normalized = IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask(input);
      String normalizedAgain = IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask(normalized);
      assertEquals(normalized, normalizedAgain);

      if (normalized.isBlank()) continue;
      assertFalse(
          normalized.chars().anyMatch(Character::isWhitespace),
          () -> "normalized mask must not contain whitespace: '" + normalized + "'");
      assertTrue(
          normalized.contains("@"),
          () -> "normalized mask should include host part: " + normalized);
      assertTrue(
          normalized.contains("!"),
          () -> "normalized mask should include ident separator: " + normalized);
    }
  }

  @Test
  void normalizePreservesCaseInNickAndHostSegments() {
    String normalized = IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask("  BadNick ");
    assertEquals("BadNick!*@*", normalized);

    String hostMask = IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask("MiXeD.Host");
    assertEquals("*!*@MiXeD.Host", hostMask);
    assertTrue(hostMask.toLowerCase(Locale.ROOT).contains("mixed.host"));
  }

  private static String randomMaskLikeInput(Random random) {
    int len = random.nextInt(20);
    String alphabet =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@.*_:-/ \\t\\n";
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < len; i++) {
      out.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    return out.toString();
  }
}

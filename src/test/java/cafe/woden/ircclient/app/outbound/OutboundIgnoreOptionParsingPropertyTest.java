package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ignore.api.IgnoreLevels;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OutboundIgnoreOptionParsingPropertyTest {

  @Test
  void parseIrssiDurationMsAcceptsRandomValidCompoundValues() {
    Random random = new Random(20260227L);
    String[] units = {"ms", "s", "m", "h", "d", "w"};
    long[] factors = {1L, 1_000L, 60_000L, 3_600_000L, 86_400_000L, 604_800_000L};

    for (int i = 0; i < 500; i++) {
      int parts = 1 + random.nextInt(3);
      StringBuilder token = new StringBuilder();
      long expected = 0L;
      for (int p = 0; p < parts; p++) {
        int amount = 1 + random.nextInt(60);
        int unitIdx = random.nextInt(units.length);
        expected += amount * factors[unitIdx];

        if (p > 0 && random.nextBoolean()) {
          token.append(random.nextBoolean() ? "_" : " ");
        }
        String unit =
            random.nextBoolean() ? units[unitIdx].toUpperCase(Locale.ROOT) : units[unitIdx];
        token.append(amount).append(unit);
      }

      OptionalLong parsed = parseIrssiDurationMs(token.toString());
      assertTrue(parsed.isPresent(), () -> "expected valid duration for: " + token);
      assertEquals(expected, parsed.getAsLong(), () -> "parsed duration mismatch for: " + token);
    }
  }

  @Test
  void parseIrssiDurationMsRejectsInvalidTokens() {
    for (String invalid : List.of("", " ", "nope", "10x", "1h-30m", "m10", "10minutesx")) {
      OptionalLong parsed = parseIrssiDurationMs(invalid);
      assertTrue(
          parsed.isEmpty(), () -> "expected invalid duration token to be rejected: " + invalid);
    }
  }

  @Test
  void parseIrssiDurationMsSaturatesOnOverflow() {
    OptionalLong parsed = parseIrssiDurationMs("9223372036854775807w");
    assertTrue(parsed.isPresent());
    assertEquals(Long.MAX_VALUE, parsed.getAsLong());
  }

  @Test
  void parseIrssiLevelsTokenNormalizesKnownLevelVariants() {
    Random random = new Random(0xBEEFL);
    List<String> known = new ArrayList<>(IgnoreLevels.KNOWN);
    for (int i = 0; i < 300; i++) {
      StringBuilder token = new StringBuilder();
      List<String> expected = new ArrayList<>();
      int parts = 1 + random.nextInt(4);
      for (int p = 0; p < parts; p++) {
        String level = known.get(random.nextInt(known.size()));
        String rendered = randomCase(level, random);
        if (random.nextBoolean()) rendered = "+" + rendered;
        if (random.nextInt(4) == 0) rendered = "-" + rendered;
        if (p > 0) token.append(",");
        token.append(rendered);
        expected.add(level.toUpperCase(Locale.ROOT));
      }

      @SuppressWarnings("unchecked")
      List<String> parsed = (List<String>) invokePrivate("parseIrssiLevelsToken", token.toString());
      assertEquals(expected, parsed, () -> "parsed levels mismatch for token: " + token);
    }
  }

  private static OptionalLong parseIrssiDurationMs(String token) {
    return (OptionalLong) invokePrivate("parseIrssiDurationMs", token);
  }

  private static Object invokePrivate(String methodName, String arg) {
    try {
      Method method =
          OutboundIgnoreCommandService.class.getDeclaredMethod(methodName, String.class);
      method.setAccessible(true);
      return method.invoke(null, arg);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static String randomCase(String text, Random random) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isLetter(c) && random.nextBoolean()) {
        out.append(Character.toLowerCase(c));
      } else if (Character.isLetter(c)) {
        out.append(Character.toUpperCase(c));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}

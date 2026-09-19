package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmojiShortcodeSupportTest {
  @ParameterizedTest
  @CsvSource({
    ":+1:,👍",
    ":thumbsup:,👍",
    "thumbsup,👍",
    ":heart:,❤️",
    ":eyes:,👀",
    ":woman_technologist:,👩‍💻",
    ":us:,🇺🇸",
    ":tada:,🎉"
  })
  void resolvesWholeShortcodesAndAliases(String token, String emoji) {
    assertEquals(emoji, EmojiShortcodeSupport.resolve(token));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"👍🏽", "👩‍💻", "🇺🇸", ":custom_reaction:", "hello :heart:", "<html>hello"})
  void preservesUnicodeAndUnknownText(String token) {
    assertEquals(token, EmojiShortcodeSupport.resolve(token));
  }
}

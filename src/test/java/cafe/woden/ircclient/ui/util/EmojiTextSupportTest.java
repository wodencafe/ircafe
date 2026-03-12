package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EmojiTextSupportTest {

  @Test
  void splitKeepsZwjFamilyAsSingleEmojiSegment() {
    List<EmojiTextSupport.Segment> segments = EmojiTextSupport.split("hi 👨‍👩‍👧‍👦 there");

    assertEquals(3, segments.size());
    assertEquals("hi ", segments.get(0).text());
    assertFalse(segments.get(0).emoji());
    assertEquals("👨‍👩‍👧‍👦", segments.get(1).text());
    assertTrue(segments.get(1).emoji());
    assertEquals(" there", segments.get(2).text());
    assertFalse(segments.get(2).emoji());
  }

  @Test
  void containsEmojiRecognizesKeycapAndFlagSequences() {
    assertTrue(EmojiTextSupport.containsEmoji("1️⃣"));
    assertTrue(EmojiTextSupport.containsEmoji("🇺🇸"));
    assertFalse(EmojiTextSupport.containsEmoji("plain ascii only"));
  }
}

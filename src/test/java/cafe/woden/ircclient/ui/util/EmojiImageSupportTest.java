package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class EmojiImageSupportTest {

  @Test
  void bundledTwemojiAssetsAreAvailable() {
    assertTrue(EmojiImageSupport.bundledAssetsAvailable());
  }

  @Test
  void loadsBundledEmojiImagesIncludingVariationSelectorFallbacks() {
    BufferedImage grin = EmojiImageSupport.imageFor("😀", 18);
    BufferedImage heart = EmojiImageSupport.imageFor("❤️", 18);

    assertNotNull(grin);
    assertNotNull(heart);
    assertTrue(grin.getWidth() > 0);
    assertTrue(heart.getWidth() > 0);
  }
}

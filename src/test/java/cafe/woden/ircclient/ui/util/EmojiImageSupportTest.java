package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
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

  @Test
  void loadAdmissionRemainsBoundedAndResponsiveDuringAnEdtBurst() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(128);
    AtomicInteger accepted = new AtomicInteger();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Runnable load =
        () -> {
          started.countDown();
          try {
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("load timed out");
          } catch (Throwable ex) {
            failure.compareAndSet(null, ex);
          } finally {
            finished.countDown();
          }
        };
    try {
      assertTrue(EmojiImageSupport.executeLoad(load));
      accepted.incrementAndGet();
      assertTrue(started.await(2, TimeUnit.SECONDS));
      CompletableFuture<Void> burst = new CompletableFuture<>();
      SwingUtilities.invokeLater(
          () -> {
            for (int i = 0; i < 1000; i++) {
              if (EmojiImageSupport.executeLoad(load)) accepted.incrementAndGet();
            }
            burst.complete(null);
          });
      burst.get(2, TimeUnit.SECONDS);
      assertTrue(accepted.get() <= 128, "scroll bursts must not enqueue unlimited image loads");
      assertFalse(EmojiImageSupport.executeLoad(load), "a full loader defers further work");
      for (int i = accepted.get(); i < 128; i++) finished.countDown();
    } finally {
      release.countDown();
    }
    assertTrue(finished.await(5, TimeUnit.SECONDS));
    assertNull(failure.get());
  }
}

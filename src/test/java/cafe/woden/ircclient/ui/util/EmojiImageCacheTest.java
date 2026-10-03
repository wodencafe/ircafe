package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class EmojiImageCacheTest {
  @Test
  void missingAssetsAreRememberedAcrossSizesAndEvictedByRecency() {
    AtomicInteger loads = new AtomicInteger();
    EmojiImageCache cache =
        new EmojiImageCache(
            text -> {
              loads.incrementAndGet();
              return null;
            },
            2,
            2,
            4096);

    assertNull(cache.imageFor("missing-a", 16));
    assertNull(cache.imageFor("missing-a", 32));
    assertEquals(1, loads.get(), "missing assets must not be decoded on each repaint or resize");
    cache.imageFor("missing-b", 16);
    assertTrue(cache.lookup("missing-a", 24).resolved());
    cache.imageFor("missing-c", 16);
    assertFalse(cache.lookup("missing-b", 16).resolved(), "the oldest missing key is evicted");
    cache.imageFor("missing-b", 16);
    assertEquals(4, loads.get());
  }

  @Test
  void decodedImagesAreBoundedAsWellAsMissingAssets() {
    AtomicInteger loads = new AtomicInteger();
    EmojiImageCache cache =
        new EmojiImageCache(
            text -> {
              loads.incrementAndGet();
              return image();
            },
            2,
            1,
            4096);
    cache.imageFor("a", 16);
    cache.imageFor("b", 16);
    cache.imageFor("c", 16);
    cache.imageFor("a", 16);
    assertEquals(4, loads.get(), "decoded source images must also be evicted");
  }

  @Test
  void scaledImagesRespectEntryAndByteLimits() {
    EmojiImageCache entries = new EmojiImageCache(text -> image(), 2, 2, 4096);
    entries.imageFor("a", 8);
    entries.imageFor("a", 9);
    entries.imageFor("a", 10);
    assertFalse(entries.lookup("a", 8).resolved());
    assertNotNull(entries.lookup("a", 9).image());
    assertNotNull(entries.lookup("a", 10).image());

    EmojiImageCache bytes = new EmojiImageCache(text -> image(), 2, 10, 600);
    bytes.imageFor("a", 8);
    bytes.imageFor("a", 12);
    assertFalse(bytes.lookup("a", 8).resolved(), "256 + 576 bytes exceeds the 600-byte budget");
    assertNotNull(bytes.lookup("a", 12).image());
  }

  @Test
  void oversizedRequestsAreClampedAndReuseTheSameImage() {
    EmojiImageCache cache = new EmojiImageCache(text -> image(), 2, 2, 1024 * 1024);
    BufferedImage oversized = cache.imageFor("a", Integer.MAX_VALUE);
    assertEquals(256, oversized.getWidth());
    assertSame(oversized, cache.imageFor("a", 256));
  }

  @Test
  void edtLookupsNeverInvokeTheDecoder() throws Exception {
    AtomicInteger loads = new AtomicInteger();
    EmojiImageCache cache =
        new EmojiImageCache(
            text -> {
              loads.incrementAndGet();
              return image();
            },
            2,
            2,
            4096);
    SwingUtilities.invokeAndWait(
        () -> {
          assertNull(cache.imageFor("a", 16));
          assertFalse(cache.lookup("a", 16).resolved());
        });
    assertEquals(0, loads.get());
    BufferedImage loaded = cache.imageFor("a", 16);
    SwingUtilities.invokeAndWait(() -> assertSame(loaded, cache.imageFor("a", 16)));
    assertEquals(1, loads.get());
  }

  @Test
  void cacheLookupsStayResponsiveWhileTheDecoderIsBlocked() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    EmojiImageCache cache =
        new EmojiImageCache(
            text -> {
              started.countDown();
              try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("load timed out");
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
              }
              return image();
            },
            2,
            2,
            4096);
    Thread loader = Thread.startVirtualThread(() -> cache.imageFor("a", 16));
    try {
      assertTrue(started.await(2, TimeUnit.SECONDS));
      CompletableFuture<EmojiImageSupport.CachedImage> lookup = new CompletableFuture<>();
      SwingUtilities.invokeLater(() -> lookup.complete(cache.lookup("a", 16)));
      assertFalse(lookup.get(2, TimeUnit.SECONDS).resolved());
    } finally {
      release.countDown();
      loader.join(2000);
    }
    assertFalse(loader.isAlive());
    assertNotNull(cache.lookup("a", 16).image());
  }

  private static BufferedImage image() {
    return new BufferedImage(72, 72, BufferedImage.TYPE_INT_ARGB);
  }
}

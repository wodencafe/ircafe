package cafe.woden.ircclient.ui.util;

import cafe.woden.ircclient.util.VirtualThreads;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads bundled Twemoji PNG assets and scales them to the requested UI size. */
public final class EmojiImageSupport {

  private static final Logger log = LoggerFactory.getLogger(EmojiImageSupport.class);

  static final String BUNDLED_TWEMOJI_RESOURCE = "emoji/twemoji-72x72.zip";
  private static final String ZIP_PREFIX = "72x72/";

  private static final EmojiImageCache CACHE =
      new EmojiImageCache(EmojiImageSupport::loadRawImage, 512, 1024, 16L * 1024 * 1024);
  private static Semaphore loadSlots = new Semaphore(128);
  private static ExecutorService loaderExecutor;

  private static final Object ZIP_LOCK = new Object();
  private static volatile Path extractedZipPath;

  private EmojiImageSupport() {}

  /** Loads on background callers; EDT callers only receive already-cached images. */
  public static BufferedImage imageFor(String emojiText, int sizePx) {
    return CACHE.imageFor(Objects.toString(emojiText, ""), sizePx);
  }

  /** A resolved lookup may have no image when the bundled asset is unavailable. */
  public record CachedImage(boolean resolved, BufferedImage image) {}

  /** Cache-only lookup, safe during painting. */
  public static CachedImage cachedImageFor(String emojiText, int sizePx) {
    return CACHE.lookup(Objects.toString(emojiText, ""), sizePx);
  }

  /**
   * Submits at most 128 pending/running loads without blocking the EDT. The shared worker is owned
   * by VirtualThreadsLifecycle's fallback teardown; views cancel their own tasks when detached.
   */
  public static synchronized boolean executeLoad(Runnable task) {
    if (loaderExecutor == null || loaderExecutor.isShutdown()) {
      loaderExecutor = VirtualThreads.newSingleThreadExecutor("ircafe-emoji-images");
      loadSlots = new Semaphore(128);
    }
    Semaphore slots = loadSlots;
    if (!slots.tryAcquire()) return false;
    try {
      loaderExecutor.execute(
          () -> {
            try {
              task.run();
            } finally {
              slots.release();
            }
          });
      return true;
    } catch (RejectedExecutionException ex) {
      slots.release();
      return false;
    }
  }

  static boolean bundledAssetsAvailable() {
    return loadRawImage("😀") != null;
  }

  private static BufferedImage loadRawImage(String emojiText) {
    Path zipPath = ensureExtractedZip();
    if (zipPath == null) {
      return null;
    }

    List<String> candidates = candidateEntryNames(emojiText);
    try (ZipFile zip = new ZipFile(zipPath.toFile())) {
      for (String candidate : candidates) {
        ZipEntry entry = zip.getEntry(candidate);
        if (entry == null) {
          continue;
        }
        try (InputStream in = zip.getInputStream(entry)) {
          BufferedImage image = ImageIO.read(in);
          if (image != null) {
            return image;
          }
        }
      }
    } catch (IOException e) {
      log.warn("[ircafe] loading bundled emoji asset failed", e);
    }
    return null;
  }

  private static List<String> candidateEntryNames(String emojiText) {
    int[] codePoints = emojiText.codePoints().toArray();
    ArrayList<String> candidates = new ArrayList<>(2);
    addCandidate(candidates, codePoints);

    int strippedCount = 0;
    for (int codePoint : codePoints) {
      if (codePoint != 0xFE0F && codePoint != 0xFE0E) {
        strippedCount++;
      }
    }
    if (strippedCount != codePoints.length) {
      int[] stripped = new int[strippedCount];
      int idx = 0;
      for (int codePoint : codePoints) {
        if (codePoint != 0xFE0F && codePoint != 0xFE0E) {
          stripped[idx++] = codePoint;
        }
      }
      addCandidate(candidates, stripped);
    }

    return List.copyOf(candidates);
  }

  private static void addCandidate(List<String> out, int[] codePoints) {
    if (codePoints.length == 0) {
      return;
    }
    StringBuilder name = new StringBuilder(ZIP_PREFIX);
    for (int i = 0; i < codePoints.length; i++) {
      if (i > 0) {
        name.append('-');
      }
      name.append(Integer.toHexString(codePoints[i]));
    }
    name.append(".png");
    String candidate = name.toString();
    if (!out.contains(candidate)) {
      out.add(candidate);
    }
  }

  static BufferedImage scale(BufferedImage source, int sizePx) {
    if (source.getWidth() == sizePx && source.getHeight() == sizePx) {
      return source;
    }

    BufferedImage scaled = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = scaled.createGraphics();
    try {
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.drawImage(source, 0, 0, sizePx, sizePx, null);
    } finally {
      g.dispose();
    }
    return scaled;
  }

  private static Path ensureExtractedZip() {
    Path current = extractedZipPath;
    if (current != null && Files.exists(current)) {
      return current;
    }

    synchronized (ZIP_LOCK) {
      current = extractedZipPath;
      if (current != null && Files.exists(current)) {
        return current;
      }
      try (InputStream in =
          EmojiImageSupport.class.getClassLoader().getResourceAsStream(BUNDLED_TWEMOJI_RESOURCE)) {
        if (in == null) {
          return null;
        }
        Path tmp = Files.createTempFile("ircafe-twemoji-", ".zip");
        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        tmp.toFile().deleteOnExit();
        extractedZipPath = tmp;
        return tmp;
      } catch (IOException e) {
        log.warn("[ircafe] extracting bundled emoji assets failed", e);
        return null;
      }
    }
  }
}

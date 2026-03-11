package cafe.woden.ircclient.ui.util;

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
import java.util.concurrent.ConcurrentHashMap;
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

  private static final ConcurrentHashMap<String, BufferedImage> RAW_CACHE =
      new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<ScaleKey, BufferedImage> SCALED_CACHE =
      new ConcurrentHashMap<>();

  private static final Object ZIP_LOCK = new Object();
  private static volatile Path extractedZipPath;

  private EmojiImageSupport() {}

  public static BufferedImage imageFor(String emojiText, int sizePx) {
    String text = Objects.toString(emojiText, "");
    if (text.isEmpty() || sizePx <= 0) {
      return null;
    }
    BufferedImage raw = RAW_CACHE.computeIfAbsent(text, EmojiImageSupport::loadRawImage);
    if (raw == null) {
      return null;
    }
    int clampedSize = Math.max(8, sizePx);
    ScaleKey key = new ScaleKey(text, clampedSize);
    return SCALED_CACHE.computeIfAbsent(key, k -> scale(raw, clampedSize));
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

  private static BufferedImage scale(BufferedImage source, int sizePx) {
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

  private record ScaleKey(String emojiText, int sizePx) {}
}

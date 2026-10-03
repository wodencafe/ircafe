package cafe.woden.ircclient.ui.util;

import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Function;

/** Bounded image and missing-asset caches; decoding and scaling never hold the cache lock. */
final class EmojiImageCache {
  static final int MAX_IMAGE_SIZE = 256;
  private static final int MAX_EMOJI_TEXT_LENGTH = 128;
  private static final EmojiImageSupport.CachedImage PENDING =
      new EmojiImageSupport.CachedImage(false, null);
  private static final EmojiImageSupport.CachedImage MISSING =
      new EmojiImageSupport.CachedImage(true, null);

  private final Function<String, BufferedImage> loader;
  private final int maxRawEntries;
  private final int maxScaledEntries;
  private final long maxScaledBytes;
  private final LinkedHashMap<String, Optional<BufferedImage>> raw =
      new LinkedHashMap<>(16, 0.75f, true);
  private final LinkedHashMap<ScaleKey, BufferedImage> scaled =
      new LinkedHashMap<>(16, 0.75f, true);
  private long scaledBytes;

  EmojiImageCache(
      Function<String, BufferedImage> loader,
      int maxRawEntries,
      int maxScaledEntries,
      long maxScaledBytes) {
    this.loader = loader;
    this.maxRawEntries = maxRawEntries;
    this.maxScaledEntries = maxScaledEntries;
    this.maxScaledBytes = maxScaledBytes;
  }

  synchronized EmojiImageSupport.CachedImage lookup(String text, int size) {
    if (!valid(text, size)) return MISSING;
    BufferedImage image = scaled.get(new ScaleKey(text, clampSize(size)));
    if (image != null) return new EmojiImageSupport.CachedImage(true, image);
    Optional<BufferedImage> source = raw.get(text);
    return source != null && source.isEmpty() ? MISSING : PENDING;
  }

  BufferedImage imageFor(String text, int size) {
    EmojiImageSupport.CachedImage cached = lookup(text, size);
    if (cached.resolved() || EventQueue.isDispatchThread()) return cached.image();
    Optional<BufferedImage> source;
    synchronized (this) {
      source = raw.get(text);
    }
    if (source == null) {
      source = Optional.ofNullable(loader.apply(text));
      synchronized (this) {
        raw.put(text, source);
        while (raw.size() > maxRawEntries) raw.pollFirstEntry();
      }
    }
    if (source.isEmpty()) return null;
    BufferedImage image = EmojiImageSupport.scale(source.get(), clampSize(size));
    synchronized (this) {
      BufferedImage previous = scaled.put(new ScaleKey(text, clampSize(size)), image);
      scaledBytes += bytes(image) - (previous == null ? 0 : bytes(previous));
      while (scaled.size() > maxScaledEntries || scaledBytes > maxScaledBytes) {
        scaledBytes -= bytes(scaled.pollFirstEntry().getValue());
      }
    }
    return image;
  }

  static int clampSize(int size) {
    return Math.clamp(size, 8, MAX_IMAGE_SIZE);
  }

  private static boolean valid(String text, int size) {
    return text != null && !text.isEmpty() && text.length() <= MAX_EMOJI_TEXT_LENGTH && size > 0;
  }

  private static long bytes(BufferedImage image) {
    return (long) image.getWidth() * image.getHeight() * Integer.BYTES;
  }

  private record ScaleKey(String text, int size) {}
}

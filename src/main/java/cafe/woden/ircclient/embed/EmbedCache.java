package cafe.woden.ircclient.embed;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU cache for embed results with size-based eviction.
 */
@Component
public class EmbedCache {

    private final long maxSizeBytes;
    private long currentSizeBytes = 0;

    private final LinkedHashMap<String, CacheEntry> cache;

    public EmbedCache(EmbedSettings settings) {
        this.maxSizeBytes = settings.cacheSizeBytes();
        this.cache = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                if (currentSizeBytes > maxSizeBytes) {
                    currentSizeBytes -= eldest.getValue().sizeBytes;
                    return true;
                }
                return false;
            }
        };
    }

    /**
     * Get cached embed result for URL.
     */
    public synchronized EmbedResult get(String url) {
        CacheEntry entry = cache.get(url);
        return entry != null ? entry.result : null;
    }

    /**
     * Cache an embed result.
     */
    public synchronized void put(String url, EmbedResult result) {
        long size = estimateSize(result);

        // Remove existing entry if present
        CacheEntry existing = cache.remove(url);
        if (existing != null) {
            currentSizeBytes -= existing.sizeBytes;
        }

        // Add new entry
        cache.put(url, new CacheEntry(result, size));
        currentSizeBytes += size;

        // Trigger LRU eviction if needed
        while (currentSizeBytes > maxSizeBytes && cache.size() > 1) {
            var it = cache.entrySet().iterator();
            if (it.hasNext()) {
                var entry = it.next();
                currentSizeBytes -= entry.getValue().sizeBytes;
                it.remove();
            }
        }
    }

    /**
     * Remove cached embed for URL.
     */
    public synchronized void remove(String url) {
        CacheEntry entry = cache.remove(url);
        if (entry != null) {
            currentSizeBytes -= entry.sizeBytes;
        }
    }

    /**
     * Clear the entire cache.
     */
    public synchronized void clear() {
        cache.clear();
        currentSizeBytes = 0;
    }

    /**
     * Get current cache size in bytes.
     */
    public synchronized long getCurrentSizeBytes() {
        return currentSizeBytes;
    }

    /**
     * Get number of cached entries.
     */
    public synchronized int size() {
        return cache.size();
    }

    private long estimateSize(EmbedResult result) {
        long size = 100; // Base overhead

        if (result instanceof EmbedResult.ImageEmbed img) {
            size += estimateImageSize(img.original());
            size += estimateImageSize(img.thumbnail());
        } else if (result instanceof EmbedResult.VideoEmbed vid) {
            if (vid.thumbnail() != null) {
                size += estimateImageSize(vid.thumbnail());
            }
            if (vid.title() != null) {
                size += vid.title().length() * 2L;
            }
        } else if (result instanceof EmbedResult.LinkPreview link) {
            if (link.title() != null) size += link.title().length() * 2L;
            if (link.description() != null) size += link.description().length() * 2L;
            if (link.siteName() != null) size += link.siteName().length() * 2L;
            if (link.favicon() != null) size += estimateImageSize(link.favicon());
            if (link.ogImage() != null) size += estimateImageSize(link.ogImage());
        }

        return size;
    }

    private long estimateImageSize(BufferedImage img) {
        if (img == null) return 0;
        // Approximate: width * height * bytes per pixel
        return (long) img.getWidth() * img.getHeight() * 4;
    }

    private record CacheEntry(EmbedResult result, long sizeBytes) {}
}

package cafe.woden.ircclient.embed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for inline embeds.
 */
@ConfigurationProperties(prefix = "irc.embeds")
public record EmbedSettings(
    boolean enabled,
    boolean imagesEnabled,
    boolean videosEnabled,
    boolean linkPreviewsEnabled,
    int maxThumbnailWidth,
    int maxThumbnailHeight,
    long cacheSizeBytes,
    int fetchTimeoutMs
) {
    public EmbedSettings {
        // Defaults
        if (maxThumbnailWidth <= 0) maxThumbnailWidth = 400;
        if (maxThumbnailHeight <= 0) maxThumbnailHeight = 300;
        if (cacheSizeBytes <= 0) cacheSizeBytes = 52428800L; // 50MB
        if (fetchTimeoutMs <= 0) fetchTimeoutMs = 10000;
    }

    /** Default settings for when configuration is not provided */
    public static EmbedSettings defaults() {
        return new EmbedSettings(true, true, true, true, 400, 300, 52428800L, 10000);
    }
}

package cafe.woden.ircclient.embed;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies URLs by their embed type based on patterns.
 */
@Component
public class UrlClassifier {

    // Image file extensions
    private static final Pattern IMAGE_PATTERN =
        Pattern.compile("(?i)\\.(png|jpe?g|gif|webp|svg|bmp)(\\?.*)?$");

    // YouTube URLs - captures video ID
    private static final Pattern YOUTUBE_PATTERN =
        Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([\\w-]+)");

    // Vimeo URLs - captures video ID
    private static final Pattern VIMEO_PATTERN =
        Pattern.compile("vimeo\\.com/(\\d+)");

    // Direct video file extensions
    private static final Pattern DIRECT_VIDEO_PATTERN =
        Pattern.compile("(?i)\\.(mp4|webm|mov|mkv)(\\?.*)?$");

    private final EmbedSettings settings;

    public UrlClassifier(EmbedSettings settings) {
        this.settings = settings;
    }

    /**
     * Classify a URL by its embed type.
     */
    public EmbedType classify(String url) {
        if (url == null || url.isBlank() || !settings.enabled()) {
            return EmbedType.NONE;
        }

        // Check for images
        if (settings.imagesEnabled() && IMAGE_PATTERN.matcher(url).find()) {
            return EmbedType.IMAGE;
        }

        // Check for videos
        if (settings.videosEnabled()) {
            if (YOUTUBE_PATTERN.matcher(url).find() ||
                VIMEO_PATTERN.matcher(url).find() ||
                DIRECT_VIDEO_PATTERN.matcher(url).find()) {
                return EmbedType.VIDEO;
            }
        }

        // General link preview (must start with http/https)
        if (settings.linkPreviewsEnabled() &&
            (url.startsWith("http://") || url.startsWith("https://"))) {
            return EmbedType.LINK_PREVIEW;
        }

        return EmbedType.NONE;
    }

    /**
     * Extract YouTube video ID from URL.
     */
    public Optional<String> extractYouTubeId(String url) {
        if (url == null) return Optional.empty();
        Matcher m = YOUTUBE_PATTERN.matcher(url);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /**
     * Extract Vimeo video ID from URL.
     */
    public Optional<String> extractVimeoId(String url) {
        if (url == null) return Optional.empty();
        Matcher m = VIMEO_PATTERN.matcher(url);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /**
     * Determine the video provider for a URL.
     */
    public Optional<EmbedResult.VideoProvider> getVideoProvider(String url) {
        if (url == null) return Optional.empty();

        if (YOUTUBE_PATTERN.matcher(url).find()) {
            return Optional.of(EmbedResult.VideoProvider.YOUTUBE);
        }
        if (VIMEO_PATTERN.matcher(url).find()) {
            return Optional.of(EmbedResult.VideoProvider.VIMEO);
        }
        if (DIRECT_VIDEO_PATTERN.matcher(url).find()) {
            return Optional.of(EmbedResult.VideoProvider.DIRECT);
        }

        return Optional.empty();
    }
}

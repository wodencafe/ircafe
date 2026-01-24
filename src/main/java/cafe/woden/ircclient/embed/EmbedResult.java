package cafe.woden.ircclient.embed;

import javax.swing.*;
import java.awt.image.BufferedImage;

/**
 * Sealed interface representing the result of fetching embed data.
 */
public sealed interface EmbedResult {

    /** The original URL this result is for */
    String url();

    /** Successfully loaded image embed - stores both original and thumbnail */
    record ImageEmbed(String url, BufferedImage original, BufferedImage thumbnail, int originalWidth, int originalHeight) implements EmbedResult {}

    /** Successfully loaded video embed with thumbnail */
    record VideoEmbed(String url, BufferedImage thumbnail, String videoId, VideoProvider provider, String title) implements EmbedResult {}

    /** Successfully loaded link preview */
    record LinkPreview(String url, String title, String description, String siteName, BufferedImage favicon, BufferedImage ogImage) implements EmbedResult {}

    /** Loading state placeholder */
    record Loading(String url) implements EmbedResult {}

    /** Failed to load embed */
    record Failed(String url, String errorMessage) implements EmbedResult {}

    /** Video providers */
    enum VideoProvider {
        YOUTUBE,
        VIMEO,
        DIRECT
    }
}

package cafe.woden.ircclient.ui.chat.embed;

/**
 * Minimal metadata used to render a Discord/Signal-style link preview card.
 */
public record LinkPreview(
    String url,
    String title,
    String description,
    String siteName,
    String imageUrl,
    /** Number of media attachments represented by {@code imageUrl}. */
    int mediaCount
) {
}

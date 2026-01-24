package cafe.woden.ircclient.embed;

/**
 * Types of embeds that can be displayed inline in chat.
 */
public enum EmbedType {
    /** Direct image URLs (png, jpg, gif, webp, svg, bmp) */
    IMAGE,

    /** Video URLs (YouTube, Vimeo, direct video files) */
    VIDEO,

    /** General link with OpenGraph preview */
    LINK_PREVIEW,

    /** URL that should not be embedded */
    NONE
}

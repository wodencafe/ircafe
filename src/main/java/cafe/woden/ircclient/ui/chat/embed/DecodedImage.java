package cafe.woden.ircclient.ui.chat.embed;

/** Marker interface for decoded images used by the chat embed renderer. */
sealed interface DecodedImage permits StaticImageDecoded, AnimatedGifDecoded {}

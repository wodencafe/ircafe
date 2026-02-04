package cafe.woden.ircclient.ui.chat.embed;

sealed interface DecodedImage permits StaticImageDecoded, AnimatedGifDecoded {}

package cafe.woden.ircclient.ui.chat.embed;

import java.awt.image.BufferedImage;

/** Decoded static image (PNG/JPG/WebP/etc.). */
record StaticImageDecoded(BufferedImage image) implements DecodedImage {}

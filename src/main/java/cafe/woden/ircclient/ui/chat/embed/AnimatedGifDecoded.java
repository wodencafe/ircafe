package cafe.woden.ircclient.ui.chat.embed;

import java.awt.image.BufferedImage;
import java.util.List;

record AnimatedGifDecoded(List<BufferedImage> frames, int[] delaysMs) implements DecodedImage {}

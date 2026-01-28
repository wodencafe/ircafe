package cafe.woden.ircclient.ui.chat.embed;

import com.madgag.gif.fmsware.GifDecoder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Decode raw image bytes into a usable in-memory representation for Swing rendering. */
final class ImageDecodeUtil {

  private ImageDecodeUtil() {}

  static DecodedImage decode(String url, byte[] bytes) throws IOException {
    if (bytes == null || bytes.length == 0) {
      throw new IOException("Empty image bytes");
    }

    if (looksLikeGif(url, bytes)) {
      return decodeGif(bytes);
    }

    // Static image (PNG/JPG/WebP via ImageIO plugin, etc.)
    BufferedImage img = readStatic(bytes);
    return new StaticImageDecoded(img);
  }

  static boolean looksLikeGif(String url, byte[] bytes) {
    if (bytes != null && bytes.length >= 6) {
      // GIF87a / GIF89a
      if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
        return true;
      }
    }
    return hasExtension(url, ".gif");
  }

  static boolean looksLikeWebp(String url, byte[] bytes) {
    // WebP signature: RIFF .... WEBP
    if (bytes != null && bytes.length >= 12) {
      boolean riff = bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F';
      boolean webp = bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
      if (riff && webp) return true;
    }
    return hasExtension(url, ".webp");
  }

  private static AnimatedGifDecoded decodeGif(byte[] bytes) throws IOException {
    GifDecoder dec = new GifDecoder();
    int status = dec.read(new ByteArrayInputStream(bytes));
    // STATUS_OK is 0 in this library.
    if (status != 0) {
      throw new IOException("GIF decode failed (status=" + status + ")");
    }

    int n = dec.getFrameCount();
    if (n <= 0) {
      throw new IOException("GIF has no frames");
    }

    ArrayList<BufferedImage> frames = new ArrayList<>(n);
    int[] delays = new int[n];

    for (int i = 0; i < n; i++) {
      BufferedImage f = dec.getFrame(i);
      if (f == null) {
        throw new IOException("GIF frame " + i + " is null");
      }
      frames.add(f);

      int d = dec.getDelay(i); // documented as milliseconds
      if (d <= 0) d = 80;
      // Some GIFs specify ultra-low delays that make Swing timers busy-loop.
      if (d < 20) d = 20;
      delays[i] = d;
    }

    return new AnimatedGifDecoded(frames, delays);
  }

  private static BufferedImage readStatic(byte[] bytes) throws IOException {
    try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
      BufferedImage img = ImageIO.read(in);
      if (img == null) {
        throw new IOException("Unsupported/unknown image format");
      }
      return img;
    }
  }

  private static boolean hasExtension(String url, String ext) {
    if (url == null || url.isBlank()) return false;
    try {
      String p = URI.create(url).getPath();
      if (p == null) return false;
      return p.toLowerCase(Locale.ROOT).endsWith(ext);
    } catch (Exception ignored) {
      return false;
    }
  }
}

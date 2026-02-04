package cafe.woden.ircclient.ui.chat.embed;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Locale;

final class FileUtil {

  private FileUtil() {}

  static void openBytesWithDesktop(String url, byte[] bytes) throws IOException {
    if (bytes == null || bytes.length == 0) {
      // Fall back to browser.
      try {
        Desktop.getDesktop().browse(URI.create(url));
      } catch (Exception ignored) {
      }
      return;
    }

    File f = writeTempFile(url, bytes);
    Desktop.getDesktop().open(f);
  }

  static File writeTempFile(String url, byte[] bytes) throws IOException {
    String ext = extensionFromUrl(url);
    File f = Files.createTempFile("ircafe-image-", ext).toFile();
    Files.write(f.toPath(), bytes);
    f.deleteOnExit();
    return f;
  }

  private static String extensionFromUrl(String url) {
    try {
      String p = URI.create(url).getPath();
      if (p == null) return ".img";
      p = p.toLowerCase(Locale.ROOT);
      if (p.endsWith(".png")) return ".png";
      if (p.endsWith(".jpg")) return ".jpg";
      if (p.endsWith(".jpeg")) return ".jpeg";
      if (p.endsWith(".gif")) return ".gif";
      if (p.endsWith(".webp")) return ".webp";
    } catch (Exception ignored) {
    }
    return ".img";
  }
}

package cafe.woden.ircclient.ui.icons;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.Taskbar;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized access to app icons bundled in resources.
 */
public final class AppIcons {

  private static final String ICONS_DIR = "icons/";
  private static final int[] WINDOW_ICON_SIZES = {16, 32, 48, 64, 128, 256};

  private static volatile List<Image> cachedWindowIcons;
  private static volatile Image cachedTaskbarIcon;
  private static volatile ImageIcon cachedAboutIcon;

  private AppIcons() {
  }

  /**
   * Returns the multi-size icon list for {@code JFrame#setIconImages(...)}.
   */
  public static List<Image> windowIcons() {
    List<Image> icons = cachedWindowIcons;
    if (icons != null) {
      return icons;
    }

    List<Image> out = new ArrayList<>();
    for (int size : WINDOW_ICON_SIZES) {
      Image img = readPng(ICONS_DIR + "ircafe_" + size + ".png");
      if (img != null) {
        out.add(img);
      }
    }

    // Fallback: if resources are missing, try the original 1024 and let AWT scale.
    if (out.isEmpty()) {
      Image big = readPng(ICONS_DIR + "ircafe_1024.png");
      if (big != null) {
        out.add(big);
      }
    }

    cachedWindowIcons = List.copyOf(out);
    return cachedWindowIcons;
  }

  /**
   * Best-effort taskbar/dock icon (some platforms support it).
   */
  public static Image taskbarIcon() {
    Image img = cachedTaskbarIcon;
    if (img != null) {
      return img;
    }
    Image best = readPng(ICONS_DIR + "ircafe_256.png");
    if (best == null) {
      best = readPng(ICONS_DIR + "ircafe_128.png");
    }
    cachedTaskbarIcon = best;
    return cachedTaskbarIcon;
  }

  public static void tryInstallTaskbarIcon() {
    try {
      if (!Taskbar.isTaskbarSupported()) {
        return;
      }
      Image icon = taskbarIcon();
      if (icon != null) {
        Taskbar.getTaskbar().setIconImage(icon);
      }
    } catch (Throwable ignored) {
      // best-effort
    }
  }

  /**
   * About dialog icon.
   */
  public static ImageIcon aboutIcon() {
    ImageIcon icon = cachedAboutIcon;
    if (icon != null) {
      return icon;
    }
    Image img = readPng(ICONS_DIR + "ircafe_128.png");
    if (img == null) {
      img = readPng(ICONS_DIR + "ircafe_64.png");
    }
    cachedAboutIcon = (img == null) ? null : new ImageIcon(img);
    return cachedAboutIcon;
  }

  /**
   * Returns a tray PNG as a byte-backed stream (safe for callers to consume).
   */
  public static InputStream trayPngStream() {
    byte[] bytes = readResourceBytes("icons/tray/ircafe_tray_16.png");
    if (bytes == null || bytes.length == 0) {
      bytes = readResourceBytes("icons/tray/ircafe_tray_32.png");
    }
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    return new ByteArrayInputStream(bytes);
  }

  private static Image readPng(String resourcePath) {
    URL url = AppIcons.class.getClassLoader().getResource(resourcePath);
    if (url == null) {
      return null;
    }
    try (InputStream in = url.openStream()) {
      return ImageIO.read(in);
    } catch (IOException e) {
      return null;
    }
  }

  private static byte[] readResourceBytes(String resourcePath) {
    try (InputStream in = AppIcons.class.getClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        return null;
      }
      return in.readAllBytes();
    } catch (IOException e) {
      return null;
    }
  }
}

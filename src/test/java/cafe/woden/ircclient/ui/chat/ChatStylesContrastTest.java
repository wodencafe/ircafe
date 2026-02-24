package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock(value = "UIManager", mode = ResourceAccessMode.READ_WRITE)
class ChatStylesContrastTest {

  private static final String[] SNAPSHOT_KEYS = {
    "TextPane.background",
    "TextPane.foreground",
    "Label.foreground",
    "Label.disabledForeground",
    "Component.linkColor",
    "TextPane.selectionBackground",
    "Component.warningColor",
    "Component.errorColor"
  };

  private Map<String, Object> snapshot;

  @BeforeEach
  void snapshotUiManager() {
    snapshot = new LinkedHashMap<>();
    for (String key : SNAPSHOT_KEYS) {
      snapshot.put(key, UIManager.get(key));
    }
  }

  @AfterEach
  void restoreUiManager() {
    if (snapshot == null) return;
    snapshot.forEach(UIManager::put);
  }

  @Test
  void lightThemesUseDarkReadableTranscriptText() {
    Color bg = new Color(0xFA, 0xFB, 0xFD);
    UIManager.put("TextPane.background", bg);
    UIManager.put("TextPane.foreground", new Color(0xEA, 0xED, 0xF2));
    UIManager.put("Label.foreground", new Color(0x26, 0x2D, 0x36));
    UIManager.put("Label.disabledForeground", new Color(0xD9, 0xDE, 0xE6));
    UIManager.put("Component.linkColor", new Color(0xBE, 0xCF, 0xF5));

    ChatStyles styles = new ChatStyles(null);

    Color message = fg(styles.message());
    assertNotNull(message);
    assertTrue(relativeLuminance(message) < relativeLuminance(bg));
    assertTrue(contrastRatio(message, bg) >= 4.5);

    Color timestamp = fg(styles.timestamp());
    assertNotNull(timestamp);
    assertTrue(relativeLuminance(timestamp) < relativeLuminance(bg));
    assertTrue(contrastRatio(timestamp, bg) >= 2.6);
  }

  @Test
  void darkThemesUseLightReadableTranscriptText() {
    Color bg = new Color(0x17, 0x1B, 0x21);
    UIManager.put("TextPane.background", bg);
    UIManager.put("TextPane.foreground", new Color(0x1D, 0x22, 0x2A));
    UIManager.put("Label.foreground", new Color(0x2A, 0x31, 0x3B));
    UIManager.put("Label.disabledForeground", new Color(0x2F, 0x36, 0x41));
    UIManager.put("Component.linkColor", new Color(0x29, 0x42, 0x70));

    ChatStyles styles = new ChatStyles(null);

    Color message = fg(styles.message());
    assertNotNull(message);
    assertTrue(relativeLuminance(message) > relativeLuminance(bg));
    assertTrue(contrastRatio(message, bg) >= 4.5);

    Color status = fg(styles.status());
    assertNotNull(status);
    assertTrue(relativeLuminance(status) > relativeLuminance(bg));
    assertTrue(contrastRatio(status, bg) >= 2.6);
  }

  private static Color fg(AttributeSet attrs) {
    return StyleConstants.getForeground(attrs);
  }

  private static double contrastRatio(Color fg, Color bg) {
    if (fg == null || bg == null) return 0.0;

    double l1 = relativeLuminance(fg);
    double l2 = relativeLuminance(bg);
    if (l1 < l2) {
      double t = l1;
      l1 = l2;
      l2 = t;
    }
    return (l1 + 0.05) / (l2 + 0.05);
  }

  private static double relativeLuminance(Color c) {
    double r = srgbToLinear(c.getRed());
    double g = srgbToLinear(c.getGreen());
    double b = srgbToLinear(c.getBlue());
    return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
  }

  private static double srgbToLinear(int channel) {
    double v = channel / 255.0;
    return (v <= 0.04045) ? (v / 12.92) : Math.pow((v + 0.055) / 1.055, 2.4);
  }
}

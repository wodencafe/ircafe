package cafe.woden.ircclient.ui.settings.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import cafe.woden.ircclient.ui.settings.SettingsColorSupport;
import cafe.woden.ircclient.ui.settings.theme.ThemeAccentSettings;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import org.junit.jupiter.api.Test;

class AppearanceAccentPreviewFunctionalTest {
  @Test
  void nimbusThemeAccentChipPaintsTheColorUsedToChooseItsTextContrast() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    SwingUtilities.invokeAndWait(
        () -> {
          var originalLaf = UIManager.getLookAndFeel();
          Object originalAccent = UIManager.get("Component.accentColor");
          try {
            UIManager.setLookAndFeel(new NimbusLookAndFeel());
            Color accent = new ColorUIResource(0x9E5016);
            UIManager.put("Component.accentColor", accent);
            AccentControls controls =
                AppearanceAccentControlsFactory.build(new ThemeAccentSettings(null, 70));
            controls.chip.setSize(80, 24);
            BufferedImage image = new BufferedImage(80, 24, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
              controls.chip.printAll(graphics);
            } finally {
              graphics.dispose();
            }
            Color paintedBackground = new Color(image.getRGB(1, 1));
            assertEquals(accent.getRGB(), paintedBackground.getRGB());
            assertTrue(
                SettingsColorSupport.contrastRatio(controls.chip.getForeground(), paintedBackground)
                    >= 4.5);
          } catch (javax.swing.UnsupportedLookAndFeelException e) {
            throw new IllegalStateException(e);
          } finally {
            UIManager.put("Component.accentColor", originalAccent);
            try {
              UIManager.setLookAndFeel(originalLaf);
            } catch (javax.swing.UnsupportedLookAndFeelException e) {
              throw new IllegalStateException(e);
            }
          }
        });
  }
}

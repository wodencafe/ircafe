package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThemeAppearanceServiceTest {

  private final ThemeAppearanceService service = new ThemeAppearanceService();

  private final String initialLookAndFeelClassName =
      UIManager.getLookAndFeel() != null ? UIManager.getLookAndFeel().getClass().getName() : null;

  @Test
  void disablingAccentRestoresPreviousUiDefaults() throws Exception {
    onEdt(
        () -> {
          try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          Color baselineFocus = new Color(0x44, 0x55, 0x66);
          Color baselineSelection = new Color(0x2E, 0x3F, 0x50);
          UIManager.put("Component.focusColor", baselineFocus);
          UIManager.put("TextComponent.selectionBackground", baselineSelection);

          service.applyAccentOverrides(new ThemeAccentSettings("#FF5500", 100));

          Color afterApplyFocus = UIManager.getColor("Component.focusColor");
          Color afterApplySelection = UIManager.getColor("TextComponent.selectionBackground");
          assertNotEquals(baselineFocus, afterApplyFocus);
          assertNotEquals(baselineSelection, afterApplySelection);

          service.applyAccentOverrides(new ThemeAccentSettings(null, 70));

          assertEquals(baselineFocus, UIManager.getColor("Component.focusColor"));
          assertEquals(baselineSelection, UIManager.getColor("TextComponent.selectionBackground"));
        });
  }

  @Test
  void disablingAccentAfterLookAndFeelSwitchDoesNotRestoreStaleValues() throws Exception {
    onEdt(
        () -> {
          try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          Color darkBaselineFocus = new Color(0x32, 0x42, 0x52);
          UIManager.put("Component.focusColor", darkBaselineFocus);
          service.applyAccentOverrides(new ThemeAccentSettings("#22AAEE", 80));

          try {
            UIManager.setLookAndFeel(new FlatLightLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          Color lightBaselineFocus = new Color(0x88, 0x66, 0x44);
          UIManager.put("Component.focusColor", lightBaselineFocus);

          service.applyAccentOverrides(new ThemeAccentSettings(null, 70));

          assertEquals(lightBaselineFocus, UIManager.getColor("Component.focusColor"));
          assertNotEquals(darkBaselineFocus, UIManager.getColor("Component.focusColor"));
        });
  }

  @Test
  void uiFontOverrideAppliesAndRestoresDefaults() throws Exception {
    onEdt(
        () -> {
          try {
            UIManager.setLookAndFeel(new FlatLightLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          Font baselineDefault = UIManager.getFont("defaultFont");
          if (baselineDefault == null) {
            throw new IllegalStateException("defaultFont baseline missing");
          }

          int targetSize = baselineDefault.getSize() == 16 ? 17 : 16;
          String targetFamily =
              "Dialog".equalsIgnoreCase(baselineDefault.getFamily()) ? "Monospaced" : "Dialog";

          service.applyCommonTweaks(
              new ThemeTweakSettings(
                  ThemeTweakSettings.ThemeDensity.AUTO, 10, true, targetFamily, targetSize));

          Font afterApply = UIManager.getFont("defaultFont");
          assertNotEquals(baselineDefault.getSize(), afterApply.getSize());
          assertEquals(targetSize, afterApply.getSize());
          assertEquals(targetFamily, afterApply.getFamily());

          service.applyCommonTweaks(
              new ThemeTweakSettings(
                  ThemeTweakSettings.ThemeDensity.AUTO, 10, false, targetFamily, targetSize));

          Font afterDisable = UIManager.getFont("defaultFont");
          assertEquals(baselineDefault.getFamily(), afterDisable.getFamily());
          assertEquals(baselineDefault.getSize(), afterDisable.getSize());
        });
  }

  @Test
  void uiFontOverrideDoesNotLeakAcrossLookAndFeelClassSwitches() throws Exception {
    onEdt(
        () -> {
          try {
            UIManager.setLookAndFeel(new FlatLightLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          Font lightTreeBaseline = uiFont("Tree.font");
          int targetSize = lightTreeBaseline.getSize() + 4;

          try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          Font darkTreeBaseline = uiFont("Tree.font");
          service.applyCommonTweaks(
              new ThemeTweakSettings(
                  ThemeTweakSettings.ThemeDensity.AUTO, 10, true, darkTreeBaseline.getFamily(), targetSize));
          Font darkAfterApply = uiFont("Tree.font");
          assertEquals(targetSize, darkAfterApply.getSize());

          try {
            UIManager.setLookAndFeel(new FlatLightLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          service.applyCommonTweaks(
              new ThemeTweakSettings(
                  ThemeTweakSettings.ThemeDensity.AUTO,
                  10,
                  false,
                  darkTreeBaseline.getFamily(),
                  targetSize));

          Font lightAfterRestore = uiFont("Tree.font");
          assertEquals(lightTreeBaseline.getFamily(), lightAfterRestore.getFamily());
          assertEquals(lightTreeBaseline.getSize(), lightAfterRestore.getSize());
        });
  }

  @AfterAll
  void restoreLookAndFeel() throws Exception {
    if (initialLookAndFeelClassName == null || initialLookAndFeelClassName.isBlank()) return;
    onEdt(
        () -> {
          try {
            UIManager.setLookAndFeel(initialLookAndFeelClassName);
          } catch (Exception ignored) {
          }
        });
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }

  private static Font uiFont(String key) {
    Font f = UIManager.getFont(key);
    if (f != null) return f;
    Font fallback = UIManager.getFont("defaultFont");
    if (fallback != null) return fallback;
    return new Font("Dialog", Font.PLAIN, 12);
  }
}

package cafe.woden.ircclient.ui.settings.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.userlist.UserListNickCellRenderer;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NimbusUiFontFunctionalTest {
  @ParameterizedTest
  @ValueSource(strings = {"nimbus", "nimbus-dark-orange", "nimbus-dark-blue", "nimbus-orange"})
  void fontOverrideReachesExistingAndNewComponentsAndRestoresDefaults(String theme)
      throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          String originalLaf = UIManager.getLookAndFeel().getClass().getName();
          ThemeLookAndFeelInstaller installer =
              new ThemeLookAndFeelInstaller(
                  new ThemePresetRegistry(), new NimbusThemeOverrideService());
          ThemeAppearanceService service = new ThemeAppearanceService();
          ThemeTweakSettingsBus tweaks = mock(ThemeTweakSettingsBus.class);
          ThemeManager manager =
              new ThemeManager(
                  mock(ChatStyles.class),
                  mock(ChatTranscriptStore.class),
                  null,
                  null,
                  tweaks,
                  new ThemeCatalog(),
                  service,
                  installer);
          try {
            manager.installLookAndFeel(theme);
            JPanel panel = components();
            Map<Class<?>, Font> baseline = fonts(panel);
            for (ThemeTweakSettings settings :
                new ThemeTweakSettings[] {
                  fontSettings(Font.MONOSPACED, 16), fontSettings(Font.MONOSPACED, 24),
                  fontSettings(Font.MONOSPACED, 18), fontSettings(Font.SERIF, 18)
                }) {
              when(tweaks.get()).thenReturn(settings);
              manager.applyAppearance(false);
              service.applyNimbusDensityToComponentTree(panel, settings);
              SwingUtilities.updateComponentTreeUI(panel);
              assertFonts(panel, settings.uiFontSize(), settings.uiFontFamily());
              assertFonts(components(), settings.uiFontSize(), settings.uiFontFamily());
              assertEquals(settings.uiFontSize(), UIManager.getFont("List.font").getSize());
              var laf = UIManager.getLookAndFeel();
              manager.applyAppearance(false);
              assertSame(
                  laf, UIManager.getLookAndFeel(), "unchanged fonts must not reinstall Nimbus");
            }
            when(tweaks.get())
                .thenReturn(new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.COZY, 10));
            manager.applyAppearance(false);
            service.applyNimbusDensityToComponentTree(panel, null);
            SwingUtilities.updateComponentTreeUI(panel);
            assertEquals(baseline, fonts(panel));
            assertEquals(baseline, fonts(components()));
          } finally {
            service.applyCommonTweaks(
                new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10));
            installer.install("nimbus");
            try {
              UIManager.setLookAndFeel(originalLaf);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          }
        });
  }

  @Test
  void startupFontOverrideSurvivesNimbusAndFlatThemeSwitches() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          String originalLaf = UIManager.getLookAndFeel().getClass().getName();
          ThemeTweakSettingsBus tweaks = mock(ThemeTweakSettingsBus.class);
          when(tweaks.get()).thenReturn(fontSettings(Font.MONOSPACED, 16));
          ThemeAppearanceService service = new ThemeAppearanceService();
          ThemeLookAndFeelInstaller installer =
              new ThemeLookAndFeelInstaller(
                  new ThemePresetRegistry(), new NimbusThemeOverrideService());
          ThemeManager manager =
              new ThemeManager(
                  mock(ChatStyles.class),
                  mock(ChatTranscriptStore.class),
                  null,
                  null,
                  tweaks,
                  new ThemeCatalog(),
                  service,
                  installer);
          try {
            for (String theme :
                new String[] {
                  "nimbus-dark-orange",
                  "darcula",
                  "nimbus-dark-blue",
                  "nimbus-orange",
                  "nimbus-dark-orange"
                }) {
              manager.installLookAndFeel(theme);
              assertFonts(components(), 16, Font.MONOSPACED);
              if (theme.startsWith("nimbus")) {
                JLabel customLabel = new JLabel("Custom font");
                UIDefaults local = new UIDefaults();
                local.put("Label.font", new FontUIResource(Font.SERIF, Font.BOLD, 20));
                customLabel.putClientProperty("Nimbus.Overrides", local);
                assertEquals(20, customLabel.getFont().getSize());
                assertEquals(Font.SERIF, customLabel.getFont().getFamily());
                assertTrue(customLabel.getFont().isBold());
                assertSame(local, customLabel.getClientProperty("Nimbus.Overrides"));
              }
            }
          } finally {
            service.applyCommonTweaks(
                new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10));
            installer.install("nimbus");
            try {
              UIManager.setLookAndFeel(originalLaf);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          }
        });
  }

  private static ThemeTweakSettings fontSettings(String family, int size) {
    return new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.COZY, 10, true, family, size);
  }

  private static JPanel components() {
    JPanel panel = new JPanel();
    panel.add(new JLabel("Users"));
    JList<String> list = new JList<>(new String[] {"mrcheese"});
    list.setCellRenderer(new UserListNickCellRenderer(null, null, null, null, "compact"));
    panel.add(list);
    panel.add(new JTree());
    panel.add(new JMenu("File"));
    panel.add(new JTextField("Labelled input"));
    return panel;
  }

  private static Map<Class<?>, Font> fonts(JPanel panel) {
    Map<Class<?>, Font> result = new LinkedHashMap<>();
    for (var child : panel.getComponents()) result.put(child.getClass(), child.getFont());
    return result;
  }

  private static void assertFonts(JPanel panel, int size, String family) {
    for (var child : panel.getComponents()) {
      JComponent component = (JComponent) child;
      assertEquals(
          size,
          component.getFont().getSize(),
          () ->
              component.getClass().getSimpleName()
                  + " actual="
                  + component.getFont()
                  + " default="
                  + UIManager.getFont("defaultFont")
                  + " label="
                  + UIManager.getFont("Label.font")
                  + " nativeLabel="
                  + UIManager.getLookAndFeelDefaults().getFont("Label.font"));
      assertEquals(family, component.getFont().getFamily());
      if (component instanceof JList<?> list) {
        // The renderer inherits the actual list font on every repaint.
        var renderer =
            ((UserListNickCellRenderer) list.getCellRenderer())
                .getListCellRendererComponent(list, null, 0, false, false);
        assertEquals(size, renderer.getFont().getSize(), "user names");
        assertEquals(family, renderer.getFont().getFamily());
        assertTrue(list.getFixedCellHeight() < 0 || list.getFixedCellHeight() >= size);
      }
    }
  }
}

package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThemeValidatorTest {

  private static final double MIN_TEXT_CONTRAST = 3.0;
  private static final double MIN_SELECTION_CONTRAST = 2.2;
  private static final double MIN_ACCENT_CONTRAST = 1.5;

  private final ThemeManager themeManager =
      new ThemeManager(
          Mockito.mock(ChatStyles.class),
          Mockito.mock(ChatTranscriptStore.class),
          null,
          null,
          null);

  private final String initialLookAndFeelClassName =
      UIManager.getLookAndFeel() != null ? UIManager.getLookAndFeel().getClass().getName() : null;

  @Test
  void supportedThemesHaveUniqueIdsAndLabels() {
    ThemeManager.ThemeOption[] themes = themeManager.supportedThemes();
    assertTrue(themes.length > 0, "Expected at least one supported theme");

    Set<String> ids = new HashSet<>();
    for (ThemeManager.ThemeOption theme : themes) {
      assertNotNull(theme, "Theme option cannot be null");
      assertNotNull(theme.id(), "Theme id cannot be null");
      assertFalse(theme.id().isBlank(), "Theme id cannot be blank");
      assertNotNull(theme.label(), "Theme label cannot be null");
      assertFalse(theme.label().isBlank(), "Theme label cannot be blank");
      assertTrue(ids.add(theme.id()), () -> "Duplicate theme id found: " + theme.id());
    }
  }

  @Test
  void featuredThemesAreIncludedInSupportedThemes() {
    Set<String> supportedIds = new HashSet<>();
    for (ThemeManager.ThemeOption theme : themeManager.supportedThemes()) {
      supportedIds.add(theme.id());
    }

    ThemeManager.ThemeOption[] featured = themeManager.featuredThemes();
    assertTrue(featured.length > 0, "Expected at least one featured theme");
    for (ThemeManager.ThemeOption theme : featured) {
      assertTrue(
          supportedIds.contains(theme.id()),
          () -> "Featured theme is not in supported themes: " + theme.id());
    }
  }

  @Test
  void eachSupportedThemeAppliesAndKeepsCoreContrastReadable() throws Exception {
    for (ThemeManager.ThemeOption theme : themeManager.supportedThemes()) {
      onEdt(() -> themeManager.installLookAndFeel(theme.id()));
      onEdt(() -> assertCoreReadability(theme));
    }
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

  private static void assertCoreReadability(ThemeManager.ThemeOption theme) {
    String themeId = theme.id();
    assertNotNull(UIManager.getLookAndFeel(), () -> themeId + ": no active LookAndFeel");

    Color panelBg = firstNonNullColor("Panel.background", "control", "nimbusBase");
    Color labelFg = firstNonNullColor("Label.foreground", "textText", "controlText");
    assertContrastAtLeast(
        themeId, "Label.foreground vs Panel.background", labelFg, panelBg, MIN_TEXT_CONTRAST);

    Color textBg = firstNonNullColor("TextField.background", "TextComponent.background", "Panel.background");
    Color textFg = firstNonNullColor("TextField.foreground", "TextComponent.foreground", "Label.foreground");
    assertContrastAtLeast(
        themeId, "TextField.foreground vs TextField.background", textFg, textBg, MIN_TEXT_CONTRAST);

    Color selectionBg = firstNonNullColor("List.selectionBackground", "TextComponent.selectionBackground");
    Color selectionFg = firstNonNullColor("List.selectionForeground", "TextComponent.selectionForeground", "List.foreground");
    assertContrastAtLeast(
        themeId,
        "List.selectionForeground vs List.selectionBackground",
        selectionFg,
        selectionBg,
        MIN_SELECTION_CONTRAST);

    Color accent = firstNonNullColor("@accentColor", "Component.focusColor", "nimbusFocus");
    assertContrastAtLeast(themeId, "Accent vs Panel.background", accent, panelBg, MIN_ACCENT_CONTRAST);
  }

  private static Color firstNonNullColor(String... keys) {
    if (keys == null) return null;
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      Color c = UIManager.getColor(key);
      if (c != null) return c;
    }
    return null;
  }

  private static void assertContrastAtLeast(
      String themeId, String pairLabel, Color fg, Color bg, double minimum) {
    assertNotNull(fg, () -> themeId + ": missing color for " + pairLabel + " (foreground)");
    assertNotNull(bg, () -> themeId + ": missing color for " + pairLabel + " (background)");

    double ratio = contrastRatio(fg, bg);
    assertTrue(
        ratio >= minimum,
        () ->
            themeId
                + ": low contrast for "
                + pairLabel
                + " ("
                + String.format("%.2f", ratio)
                + " < "
                + minimum
                + ")");
  }

  private static double contrastRatio(Color c1, Color c2) {
    double l1 = relativeLuminance(c1);
    double l2 = relativeLuminance(c2);
    double lighter = Math.max(l1, l2);
    double darker = Math.min(l1, l2);
    return (lighter + 0.05) / (darker + 0.05);
  }

  private static double relativeLuminance(Color c) {
    double r = srgbToLinear(c.getRed() / 255.0);
    double g = srgbToLinear(c.getGreen() / 255.0);
    double b = srgbToLinear(c.getBlue() / 255.0);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  private static double srgbToLinear(double value) {
    return value <= 0.04045 ? (value / 12.92) : Math.pow((value + 0.055) / 1.055, 2.4);
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}

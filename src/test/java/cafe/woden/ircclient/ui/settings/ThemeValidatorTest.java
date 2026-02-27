package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThemeValidatorTest {

  private static final double MIN_TEXT_CONTRAST = 3.0;
  private static final double MIN_SELECTION_CONTRAST = 2.2;
  private static final double MIN_ACCENT_CONTRAST = 1.2;

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

  @Test
  void canSwitchBetweenDarculaAndDarkLaf() throws Exception {
    ThemeManager.ThemeOption darcula = findThemeById("darcula");
    ThemeManager.ThemeOption darklaf = findThemeById("darklaf");
    assertNotNull(darcula, "darcula theme must be present");
    assertNotNull(darklaf, "darklaf theme must be present");

    onEdt(() -> themeManager.applyTheme(darcula.id()));
    onEdt(() -> assertCoreReadability(darcula));

    onEdt(() -> themeManager.applyTheme(darklaf.id()));
    onEdt(() -> assertCoreReadability(darklaf));

    onEdt(() -> themeManager.applyTheme(darcula.id()));
    onEdt(() -> assertCoreReadability(darcula));
  }

  @Test
  void darklafThemeAppliesAsDarkTone() throws Exception {
    ThemeManager.ThemeOption darklaf = findThemeById("darklaf");
    assertNotNull(darklaf, "darklaf theme must be present");

    onEdt(() -> themeManager.installLookAndFeel(darklaf.id()));
    onEdt(
        () -> {
          Color bg = firstNonNullColor("Panel.background", "TextComponent.background", "control");
          assertNotNull(bg, "darklaf: missing background color");
          assertTrue(
              relativeLuminance(bg) < 0.45,
              () ->
                  "darklaf should be dark-toned, but background luminance was "
                      + String.format("%.3f", relativeLuminance(bg)));
        });
  }

  @Test
  void switchingAwayFromNimbusDarkClearsNimbusSpecificOverrides() throws Exception {
    ThemeManager.ThemeOption nimbusDark = findThemeById("nimbus-dark");
    ThemeManager.ThemeOption darcula = findThemeById("darcula");
    assertNotNull(nimbusDark, "nimbus-dark theme must be present");
    assertNotNull(darcula, "darcula theme must be present");

    final Color[] nimbusMenuBg = new Color[1];
    final Color[] nimbusSelectionBg = new Color[1];
    final Color[] nimbusFocus = new Color[1];

    onEdt(() -> themeManager.installLookAndFeel(nimbusDark.id()));
    onEdt(
        () -> {
          nimbusMenuBg[0] = UIManager.getColor("MenuBar.background");
          nimbusSelectionBg[0] = UIManager.getColor("nimbusSelectionBackground");
          nimbusFocus[0] = UIManager.getColor("nimbusFocus");

          assertNotNull(nimbusMenuBg[0], "nimbus-dark: expected menu background");
          assertNotNull(nimbusSelectionBg[0], "nimbus-dark: expected selection background");
          assertNotNull(nimbusFocus[0], "nimbus-dark: expected focus color");
        });

    onEdt(() -> themeManager.installLookAndFeel(darcula.id()));
    onEdt(
        () -> {
          Color menuBgAfter = UIManager.getColor("MenuBar.background");
          Color selectionAfter = UIManager.getColor("nimbusSelectionBackground");
          Color focusAfter = UIManager.getColor("nimbusFocus");

          if (menuBgAfter != null) {
            assertNotEquals(
                nimbusMenuBg[0],
                menuBgAfter,
                "MenuBar.background leaked from nimbus-dark into darcula");
          }
          if (selectionAfter != null) {
            assertNotEquals(
                nimbusSelectionBg[0],
                selectionAfter,
                "nimbusSelectionBackground leaked from nimbus-dark into darcula");
          }
          if (focusAfter != null) {
            assertNotEquals(
                nimbusFocus[0], focusAfter, "nimbusFocus leaked from nimbus-dark into darcula");
          }
        });
  }

  @Test
  void nimbusDarkSetsDarkMenuButtonAndComboDefaults() throws Exception {
    ThemeManager.ThemeOption nimbusDark = findThemeById("nimbus-dark");
    assertNotNull(nimbusDark, "nimbus-dark theme must be present");

    onEdt(() -> themeManager.installLookAndFeel(nimbusDark.id()));
    onEdt(
        () -> {
          Color menuBg = UIManager.getColor("MenuBar.background");
          Color menuFg = UIManager.getColor("Menu.foreground");
          Color buttonBg = UIManager.getColor("Button.background");
          Color comboBg = UIManager.getColor("ComboBox.background");

          assertNotNull(menuBg, "nimbus-dark: MenuBar.background should be set");
          assertNotNull(menuFg, "nimbus-dark: Menu.foreground should be set");
          assertNotNull(buttonBg, "nimbus-dark: Button.background should be set");
          assertNotNull(comboBg, "nimbus-dark: ComboBox.background should be set");

          assertContrastAtLeast(
              "nimbus-dark",
              "Menu foreground vs MenuBar background",
              menuFg,
              menuBg,
              MIN_TEXT_CONTRAST);

          assertTrue(
              relativeLuminance(menuBg) < 0.35, "nimbus-dark: menu background should stay dark");
          assertTrue(
              relativeLuminance(buttonBg) < 0.45,
              "nimbus-dark: button background should stay dark");
          assertTrue(
              relativeLuminance(comboBg) < 0.40, "nimbus-dark: combo background should stay dark");

          assertNotEquals(
              menuBg, buttonBg, "nimbus-dark: menu and button should use different shades");
          assertNotEquals(
              buttonBg, comboBg, "nimbus-dark: button and combo should use different shades");
        });
  }

  @Test
  void nimbusDarkComponentsHaveReadableIdleStates() throws Exception {
    ThemeManager.ThemeOption nimbusDark = findThemeById("nimbus-dark");
    assertNotNull(nimbusDark, "nimbus-dark theme must be present");

    onEdt(() -> themeManager.installLookAndFeel(nimbusDark.id()));
    onEdt(
        () -> {
          JMenuBar menuBar = new JMenuBar();
          JMenu menu = new JMenu("File");
          JMenuItem item = new JMenuItem("Open");
          menu.add(item);
          menuBar.add(menu);

          JButton button = new JButton("Send");
          JComboBox<String> combo = new JComboBox<>(new String[] {"All", "Dark"});

          Color menuBarBg = menuBar.getBackground();
          Color menuFg = menu.getForeground();
          Color menuItemEnabledFg =
              firstNonNullColor(
                  "MenuItem[Enabled].textForeground", "MenuItem.foreground", "Label.foreground");
          Color buttonBg = button.getBackground();
          Color comboBg = combo.getBackground();

          assertContrastAtLeast(
              "nimbus-dark",
              "JMenu foreground vs JMenuBar background",
              menuFg,
              menuBarBg,
              MIN_TEXT_CONTRAST);
          assertContrastAtLeast(
              "nimbus-dark",
              "MenuItem enabled text vs JMenuBar background",
              menuItemEnabledFg,
              menuBarBg,
              MIN_TEXT_CONTRAST);
          assertTrue(
              relativeLuminance(buttonBg) < 0.35,
              () -> "nimbus-dark: JButton idle background is too bright: " + buttonBg);
          assertTrue(
              relativeLuminance(comboBg) < 0.35,
              () -> "nimbus-dark: JComboBox idle background is too bright: " + comboBg);
        });
  }

  @Test
  void nimbusDarkBlueUsesTintedTextFieldAndTextPaneBackgrounds() throws Exception {
    ThemeManager.ThemeOption nimbusDark = findThemeById("nimbus-dark");
    ThemeManager.ThemeOption nimbusDarkBlue = findThemeById("nimbus-dark-blue");
    assertNotNull(nimbusDark, "nimbus-dark theme must be present");
    assertNotNull(nimbusDarkBlue, "nimbus-dark-blue theme must be present");

    final Color[] baseFieldBg = new Color[1];

    // Prime with a tinted variant first to ensure switching to plain nimbus-dark clears any
    // previously applied dark-variant-specific keys.
    onEdt(() -> themeManager.installLookAndFeel(nimbusDarkBlue.id()));
    onEdt(() -> themeManager.installLookAndFeel(nimbusDark.id()));
    onEdt(
        () -> {
          JTextField field = new JTextField("base");
          baseFieldBg[0] = field.getBackground();
        });

    onEdt(() -> themeManager.installLookAndFeel(nimbusDarkBlue.id()));
    onEdt(
        () -> {
          JTextField tintedField = new JTextField("tinted");
          JTextPane tintedPane = new JTextPane();

          Color tintedFieldBg = tintedField.getBackground();
          Color tintedPaneBg = tintedPane.getBackground();

          assertNotNull(tintedFieldBg, "nimbus-dark-blue: JTextField background should be set");
          assertNotNull(tintedPaneBg, "nimbus-dark-blue: JTextPane background should be set");
          assertNotEquals(
              baseFieldBg[0],
              tintedFieldBg,
              "nimbus-dark-blue: JTextField background should differ from base nimbus-dark");

          assertTrue(
              tintedFieldBg.getBlue() > tintedFieldBg.getRed() + 6,
              () ->
                  "nimbus-dark-blue: JTextField background should keep a blue tint, got "
                      + tintedFieldBg);
          assertTrue(
              tintedPaneBg.getBlue() > tintedPaneBg.getRed() + 4,
              () ->
                  "nimbus-dark-blue: JTextPane background should keep a blue tint, got "
                      + tintedPaneBg);
        });
  }

  @RepeatedTest(5)
  void nimbusDarkBlueSwitchCycleRemainsTintedAcrossRepeatedRuns() throws Exception {
    ThemeManager.ThemeOption nimbusDark = findThemeById("nimbus-dark");
    ThemeManager.ThemeOption nimbusDarkBlue = findThemeById("nimbus-dark-blue");
    assertNotNull(nimbusDark, "nimbus-dark theme must be present");
    assertNotNull(nimbusDarkBlue, "nimbus-dark-blue theme must be present");

    onEdt(() -> themeManager.installLookAndFeel(nimbusDarkBlue.id()));
    onEdt(() -> themeManager.installLookAndFeel(nimbusDark.id()));

    onEdt(() -> themeManager.installLookAndFeel(nimbusDarkBlue.id()));
    onEdt(
        () -> {
          Color blueField = new JTextField("blue").getBackground();
          Color bluePane = new JTextPane().getBackground();
          assertTrue(
              blueField.getBlue() > blueField.getRed() + 6,
              () ->
                  "nimbus-dark-blue: repeated-cycle JTextField background should stay blue-tinted, got "
                      + blueField);
          assertTrue(
              bluePane.getBlue() > bluePane.getRed() + 4,
              () ->
                  "nimbus-dark-blue: repeated-cycle JTextPane background should stay blue-tinted, got "
                      + bluePane);
        });
  }

  @Test
  void nimbusDarkOrangePopupMenusUseReadableTintedPalette() throws Exception {
    ThemeManager.ThemeOption nimbusDarkOrange = findThemeById("nimbus-dark-orange");
    assertNotNull(nimbusDarkOrange, "nimbus-dark-orange theme must be present");

    onEdt(() -> themeManager.installLookAndFeel(nimbusDarkOrange.id()));
    onEdt(
        () -> {
          JPopupMenu menu = new JPopupMenu();
          JMenuItem copy = new JMenuItem("Copy");
          JMenuItem disabled = new JMenuItem("Disabled");
          disabled.setEnabled(false);
          menu.add(copy);
          menu.add(disabled);

          PopupMenuThemeSupport.prepareForDisplay(menu);

          Color popupBg = menu.getBackground();
          Color popupFg =
              firstNonNullColor("PopupMenu.foreground", "MenuItem.foreground", "Label.foreground");
          Color itemBg = copy.getBackground();
          Color itemFg = copy.getForeground();

          assertNotNull(popupBg, "nimbus-dark-orange: popup background should be set");
          assertNotNull(itemBg, "nimbus-dark-orange: menu item background should be set");
          assertNotNull(itemFg, "nimbus-dark-orange: menu item foreground should be set");
          assertNotNull(popupFg, "nimbus-dark-orange: popup foreground should be set");

          assertTrue(
              relativeLuminance(popupBg) < 0.40,
              () -> "nimbus-dark-orange: popup background should stay dark, got " + popupBg);
          assertTrue(
              popupBg.getRed() >= popupBg.getBlue(),
              () -> "nimbus-dark-orange: popup background should keep warm tint, got " + popupBg);

          assertContrastAtLeast(
              "nimbus-dark-orange",
              "Popup foreground vs popup background",
              popupFg,
              popupBg,
              MIN_TEXT_CONTRAST);
          assertContrastAtLeast(
              "nimbus-dark-orange",
              "Menu item foreground vs menu item background",
              itemFg,
              itemBg,
              MIN_TEXT_CONTRAST);
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

  private static void assertCoreReadability(ThemeManager.ThemeOption theme) {
    String themeId = theme.id();
    assertNotNull(UIManager.getLookAndFeel(), () -> themeId + ": no active LookAndFeel");

    Color panelBg = firstNonNullColor("Panel.background", "control", "nimbusBase");
    Color labelFg = firstNonNullColor("Label.foreground", "textText", "controlText");
    assertContrastAtLeast(
        themeId, "Label.foreground vs Panel.background", labelFg, panelBg, MIN_TEXT_CONTRAST);

    Color textBg =
        firstNonNullColor("TextField.background", "TextComponent.background", "Panel.background");
    Color textFg =
        firstNonNullColor("TextField.foreground", "TextComponent.foreground", "Label.foreground");
    assertContrastAtLeast(
        themeId, "TextField.foreground vs TextField.background", textFg, textBg, MIN_TEXT_CONTRAST);

    Color selectionBg =
        firstNonNullColor(
            "TextComponent.selectionBackground",
            "List.selectionBackground",
            "textHighlight",
            "nimbusSelectionBackground",
            "Table.selectionBackground",
            "Tree.selectionBackground");
    Color selectionFg =
        firstNonNullColor(
            "TextComponent.selectionForeground",
            "List.selectionForeground",
            "textHighlightText",
            "nimbusSelectedText",
            "List.foreground",
            "Label.foreground");
    assertContrastAtLeast(
        themeId,
        "List.selectionForeground vs List.selectionBackground",
        selectionFg,
        selectionBg,
        MIN_SELECTION_CONTRAST);

    Color accent =
        firstNonNullColor(
            "@accentColor",
            "Component.focusColor",
            "nimbusFocus",
            "textHighlight",
            "List.selectionBackground",
            "Label.foreground");
    boolean systemPack = theme.pack() == ThemeManager.ThemePack.SYSTEM;
    double accentMin = systemPack ? 1.25 : MIN_ACCENT_CONTRAST;
    assertContrastAtLeast(themeId, "Accent vs Panel.background", accent, panelBg, accentMin);
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

  private ThemeManager.ThemeOption findThemeById(String id) {
    if (id == null || id.isBlank()) return null;
    for (ThemeManager.ThemeOption theme : themeManager.supportedThemes()) {
      if (theme == null || theme.id() == null) continue;
      if (theme.id().equalsIgnoreCase(id)) return theme;
    }
    return null;
  }
}

package cafe.woden.ircclient.ui.settings.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.stream.Stream;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Painter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.synth.Region;
import javax.swing.plaf.synth.SynthConstants;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthLookAndFeel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NimbusThemePaletteTest {
  private final NimbusThemeOverrideService overrides = new NimbusThemeOverrideService();
  private final ThemeLookAndFeelInstaller installer =
      new ThemeLookAndFeelInstaller(new ThemePresetRegistry(), overrides);
  private final String originalLaf = UIManager.getLookAndFeel().getClass().getName();

  static Stream<String> variants() {
    return new NimbusThemeOverrideService().variantIds().stream().sorted();
  }

  @AfterEach
  void restoreDefaults() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          overrides.clearDarkOverrides();
          overrides.clearTintOverrides();
          try {
            UIManager.setLookAndFeel(originalLaf);
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
  }

  @ParameterizedTest
  @MethodSource("variants")
  void bodyAndSelectionTextRemainReadable(String theme) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          installer.install(theme);
          JComboBox<String> combo = new JComboBox<>(new String[] {"Example"});
          assertDropdownArrowPointsDown(combo);
          var style = SynthLookAndFeel.getStyle(combo, Region.COMBO_BOX);
          var context = new SynthContext(combo, Region.COMBO_BOX, style, SynthConstants.ENABLED);
          BufferedImage rendered = new BufferedImage(100, 30, BufferedImage.TYPE_INT_RGB);
          var graphics = rendered.createGraphics();
          try {
            style.getPainter(context).paintComboBoxBackground(context, graphics, 0, 0, 100, 30);
          } finally {
            graphics.dispose();
          }
          BufferedImage expected = paintSurface("ComboBox[Enabled]");
          for (int y = 1; y < 29; y++) {
            assertEquals(
                expected.getRGB(50, y),
                rendered.getRGB(50, y),
                "Rendered dropdown must use the theme painter");
          }
          for (String component :
              new String[] {
                "TextField",
                "PasswordField",
                "FormattedTextField",
                "TextPane",
                "TextArea",
                "List",
                "Table"
              }) {
            assertContrast(
                component,
                UIManager.getColor(component + ".foreground"),
                UIManager.getColor(component + ".background"));
          }
          for (String component : new String[] {"List", "Table", "Tree", "TextComponent"}) {
            assertContrast(
                component + " selection",
                UIManager.getColor(component + ".selectionForeground"),
                UIManager.getColor(component + ".selectionBackground"));
          }
          assertPainterContrast(
              "TabbedPane:TabbedPaneTab[Selected]",
              "TabbedPane:TabbedPaneTab[Selected].textForeground");
          assertPainterContrast("ComboBox[Enabled+Selected]", "ComboBox.selectionForeground");
          assertPainterContrast("ComboBox[Enabled]", "ComboBox.foreground");
          assertPainterContrast("ComboBox[MouseOver]", "ComboBox.foreground");
        });
  }

  @ParameterizedTest
  @MethodSource("variants")
  void switchingBackToPlainNimbusRestoresDefaults(String theme) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          installer.install("nimbus");
          String[] keys = {
            "TextPane.background",
            "PasswordField[Disabled].backgroundPainter",
            "Spinner:Panel:\"Spinner.formattedTextField\"[Disabled].backgroundPainter",
            "Spinner:\"Spinner.nextButton\"[Enabled].foregroundPainter",
            "ScrollBar:ScrollBarThumb[Enabled].backgroundPainter",
            "TextField[Enabled].background",
            "List[Selected].textForeground",
            "TextComponent.background",
            "ComboBox.background",
            "TabbedPane:TabbedPaneTab[Selected].backgroundPainter",
            "ComboBox[Enabled].backgroundPainter",
            "TableHeader.background",
            "Component.borderColor"
          };
          Object[] baseline = Stream.of(keys).map(UIManager::get).toArray();
          installer.install(theme);
          installer.install("nimbus");
          for (int i = 0; i < keys.length; i++) {
            Object restored = UIManager.get(keys[i]);
            if (baseline[i] instanceof Painter<?>) {
              assertEquals(baseline[i].getClass(), restored.getClass(), keys[i]);
            } else {
              assertEquals(baseline[i], restored, keys[i]);
            }
          }
        });
  }

  private static void assertDropdownArrowPointsDown(JComboBox<?> combo) {
    JComponent arrow = null;
    for (var child : combo.getComponents()) {
      if (child instanceof JComponent component
          && "ComboBox.arrowButton".equals(component.getName())) {
        arrow = component;
      }
    }
    assertNotNull(arrow);
    var style = SynthLookAndFeel.getStyle(arrow, Region.ARROW_BUTTON);
    var context = new SynthContext(arrow, Region.ARROW_BUTTON, style, SynthConstants.ENABLED);
    BufferedImage glyph = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
    var graphics = glyph.createGraphics();
    try {
      style
          .getPainter(context)
          .paintArrowButtonForeground(
              context, graphics, 0, 0, 20, 20, javax.swing.SwingConstants.SOUTH);
    } finally {
      graphics.dispose();
    }
    int upperPixels = 0;
    int lowerPixels = 0;
    for (int x = 0; x < 20; x++) {
      for (int y = 0; y < 20; y++) {
        if ((glyph.getRGB(x, y) >>> 24) != 0) {
          if (y < 10) upperPixels++;
          else lowerPixels++;
        }
      }
    }
    assertTrue(
        upperPixels > lowerPixels && lowerPixels > 0,
        "Dropdown glyph must point down after Nimbus rotates it");
  }

  private static void assertPainterContrast(String state, String foregroundKey) {
    BufferedImage image = paintSurface(state);
    for (int y = 1; y < image.getHeight() - 1; y++) {
      assertContrast(state, UIManager.getColor(foregroundKey), new Color(image.getRGB(50, y)));
    }
  }

  @SuppressWarnings("unchecked")
  private static BufferedImage paintSurface(String state) {
    Painter<JComponent> painter = (Painter<JComponent>) UIManager.get(state + ".backgroundPainter");
    assertNotNull(painter, state);
    BufferedImage image = new BufferedImage(100, 30, BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    try {
      painter.paint(g, new JPanel(), image.getWidth(), image.getHeight());
    } finally {
      g.dispose();
    }
    return image;
  }

  private static void assertContrast(String label, Color foreground, Color background) {
    assertNotNull(foreground, label + " foreground");
    assertNotNull(background, label + " background");
    double ratio = ThemeColorUtils.contrastRatio(foreground, background);
    assertTrue(
        ratio >= 4.5,
        () -> label + " contrast was " + ratio + " (" + foreground + " on " + background + ")");
  }
}

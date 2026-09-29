package cafe.woden.ircclient.ui.settings.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.settings.PreferencesUiSupport;
import cafe.woden.ircclient.ui.settings.SettingsColorSupport;
import cafe.woden.ircclient.ui.settings.theme.ThemeManager;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TranslationPreferencesFunctionalTest {
  @Test
  void translationFieldsRemainReadableAndProperlySizedAcrossThemeChanges() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    SwingUtilities.invokeAndWait(
        () -> {
          var original = UIManager.getLookAndFeel();
          var manager =
              new ThemeManager(
                  Mockito.mock(ChatStyles.class),
                  Mockito.mock(ChatTranscriptStore.class),
                  null,
                  null,
                  null);
          var closeables = new ArrayList<AutoCloseable>();
          JFrame frame = new JFrame();
          try {
            manager.installLookAndFeel("nimbus-dark-orange");
            var controls = TranslationControlsSupport.buildControls(null, closeables);
            frame.setContentPane(controls.panel());
            frame.setSize(680, 1100);
            frame.setVisible(true);
            for (String theme :
                new String[] {
                  "nimbus-dark-orange", "nimbus-orange", "nimbus-dark-blue", "nimbus-dark-orange"
                }) {
              manager.applyTheme(theme);
              frame.validate();
              assertTrue(controls.apiKey().getInsets().top >= 4, theme + " password padding");
              assertEquals(
                  controls.endpoint().getHeight(),
                  controls.apiKey().getHeight(),
                  theme + " field height");
              assertTrue(
                  controls.targetLanguage().getWidth()
                      >= controls.targetLanguage().getPreferredSize().width,
                  theme + " language choice must not be truncated");
              for (boolean enabled : new boolean[] {false, true}) {
                if (controls.enabled().isSelected() != enabled) controls.enabled().doClick();
                assertReadable(controls.endpoint());
                assertReadable(controls.apiKey());
                for (JSpinner spinner :
                    new JSpinner[] {
                      controls.requestTimeoutSeconds(),
                      controls.maxRequestChars(),
                      controls.maxConcurrentRequests()
                    }) {
                  assertReadable(((JSpinner.DefaultEditor) spinner.getEditor()).getTextField());
                }
              }
            }
          } finally {
            frame.dispose();
            for (AutoCloseable closeable : closeables) {
              try {
                closeable.close();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            }
            manager.installLookAndFeel("nimbus");
            try {
              UIManager.setLookAndFeel(original);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          }
        });
  }

  @Test
  void wrappingHelpTextKeepsThePanelBackgroundAfterThemeSwitching() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    SwingUtilities.invokeAndWait(
        () -> {
          var original = UIManager.getLookAndFeel();
          var manager =
              new ThemeManager(
                  Mockito.mock(ChatStyles.class),
                  Mockito.mock(ChatTranscriptStore.class),
                  null,
                  null,
                  null);
          JFrame frame = new JFrame();
          try {
            manager.installLookAndFeel("nimbus-dark-orange");
            var help = PreferencesUiSupport.subtleInfoTextWith("Language detection help");
            frame.add(help);
            frame.setSize(450, 100);
            frame.setVisible(true);
            for (String theme : new String[] {"nimbus-orange", "nimbus-dark-orange"}) {
              manager.applyTheme(theme);
              frame.validate();
              assertFalse(help.isOpaque());
              assertEquals(0, help.getBackground().getAlpha());
              BufferedImage rendered = paint(help, Color.MAGENTA);
              assertEquals(
                  Color.MAGENTA.getRGB(),
                  rendered.getRGB(help.getWidth() - 2, help.getHeight() - 2),
                  "Help text must not paint an editor background");
            }
          } finally {
            frame.dispose();
            manager.installLookAndFeel("nimbus");
            try {
              UIManager.setLookAndFeel(original);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          }
        });
  }

  private static void assertReadable(JTextComponent field) {
    BufferedImage rendered = paint(field, Color.MAGENTA);
    Color background = new Color(rendered.getRGB(3, field.getHeight() / 2));
    Color text = field.isEnabled() ? field.getForeground() : field.getDisabledTextColor();
    assertTrue(
        SettingsColorSupport.contrastRatio(text, background) >= 4.5,
        () ->
            field.getClass().getSimpleName()
                + " enabled="
                + field.isEnabled()
                + ": "
                + text
                + " on "
                + background);
  }

  private static BufferedImage paint(JComponent component, Color background) {
    var image =
        new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    try {
      graphics.setColor(background);
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
      component.printAll(graphics);
    } finally {
      graphics.dispose();
    }
    return image;
  }
}

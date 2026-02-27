package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThemeSwitchingFunctionalTest {

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
  void nimbusDarkBlueToDarkSwitchCycleKeepsDistinctTintedTextSurfaces() throws Exception {
    ThemeManager.ThemeOption nimbusDark = findThemeById("nimbus-dark");
    ThemeManager.ThemeOption nimbusDarkBlue = findThemeById("nimbus-dark-blue");
    assertNotNull(nimbusDark, "nimbus-dark theme must be present");
    assertNotNull(nimbusDarkBlue, "nimbus-dark-blue theme must be present");

    for (int i = 0; i < 5; i++) {
      onEdt(() -> themeManager.installLookAndFeel(nimbusDarkBlue.id()));
      onEdt(() -> themeManager.installLookAndFeel(nimbusDark.id()));

      Color darkFieldBg = onEdtCall(() -> new JTextField("dark").getBackground());
      Color darkPaneBg = onEdtCall(() -> new JTextPane().getBackground());

      onEdt(() -> themeManager.installLookAndFeel(nimbusDarkBlue.id()));

      Color blueFieldBg = onEdtCall(() -> new JTextField("blue").getBackground());
      Color bluePaneBg = onEdtCall(() -> new JTextPane().getBackground());

      assertNotEquals(darkFieldBg, blueFieldBg);
      assertNotEquals(darkPaneBg, bluePaneBg);
      assertTrue(
          blueFieldBg.getBlue() > blueFieldBg.getRed() + 6,
          () -> "nimbus-dark-blue JTextField background should stay blue-tinted: " + blueFieldBg);
      assertTrue(
          bluePaneBg.getBlue() > bluePaneBg.getRed() + 4,
          () -> "nimbus-dark-blue JTextPane background should stay blue-tinted: " + bluePaneBg);
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

  private ThemeManager.ThemeOption findThemeById(String id) {
    if (id == null || id.isBlank()) return null;
    for (ThemeManager.ThemeOption theme : themeManager.supportedThemes()) {
      if (theme == null || theme.id() == null) continue;
      if (theme.id().equalsIgnoreCase(id)) return theme;
    }
    return null;
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        return supplier.get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    java.util.concurrent.atomic.AtomicReference<T> out =
        new java.util.concurrent.atomic.AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            out.set(supplier.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    return out.get();
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}

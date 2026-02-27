package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.InOrder;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThemeLookAndFeelInstallerTest {

  private final String initialLookAndFeelClassName =
      UIManager.getLookAndFeel() != null ? UIManager.getLookAndFeel().getClass().getName() : null;

  @Test
  void installClearsNimbusOverrideStateBeforeApplyingNimbusVariant() throws Exception {
    ThemePresetRegistry presetRegistry = mock(ThemePresetRegistry.class);
    NimbusThemeOverrideService nimbusOverrides = mock(NimbusThemeOverrideService.class);
    when(nimbusOverrides.variantIds()).thenReturn(Set.of("nimbus-dark-blue"));
    when(nimbusOverrides.applyVariant(anyString())).thenReturn(true);

    ThemeLookAndFeelInstaller installer =
        new ThemeLookAndFeelInstaller(presetRegistry, nimbusOverrides);
    assertNotNull(installer);

    onEdt(() -> installer.install("nimbus-dark-blue"));

    InOrder inOrder = inOrder(nimbusOverrides);
    inOrder.verify(nimbusOverrides).clearDarkOverrides();
    inOrder.verify(nimbusOverrides).clearTintOverrides();
    inOrder.verify(nimbusOverrides, atLeastOnce()).applyVariant("nimbus-dark-blue");
  }

  @Test
  void installClearsNimbusOverrideStateForNonNimbusThemes() throws Exception {
    ThemePresetRegistry presetRegistry = mock(ThemePresetRegistry.class);
    NimbusThemeOverrideService nimbusOverrides = mock(NimbusThemeOverrideService.class);
    when(nimbusOverrides.variantIds()).thenReturn(Set.of("nimbus-dark-blue"));

    ThemeLookAndFeelInstaller installer =
        new ThemeLookAndFeelInstaller(presetRegistry, nimbusOverrides);
    onEdt(() -> installer.install("darcula"));

    verify(nimbusOverrides).clearDarkOverrides();
    verify(nimbusOverrides).clearTintOverrides();
    verify(nimbusOverrides, never()).applyVariant(anyString());
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
}

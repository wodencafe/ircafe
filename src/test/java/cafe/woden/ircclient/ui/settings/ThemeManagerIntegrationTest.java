package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ThemeManagerIntegrationTest {

  @Test
  void applyThemeFromBackgroundThreadRunsOnEdtAndRefreshesInOrder() throws Exception {
    Fixture fixture = createFixture();

    Thread caller = new Thread(() -> fixture.manager.applyTheme("darcula"), "apply-theme-caller");
    caller.start();
    caller.join();
    flushEdt();

    assertEquals(
        List.of(
            "install",
            "applyCommonTweaks",
            "applyAccentOverrides",
            "settingsRefresh",
            "syncTextComponents",
            "chatReload",
            "restyle"),
        fixture.steps);
    assertStepsRanOnEdt(fixture);
  }

  @Test
  void applyAppearanceFromBackgroundThreadRunsOnEdtWithoutLookAndFeelInstall() throws Exception {
    Fixture fixture = createFixture();

    Thread caller = new Thread(() -> fixture.manager.applyAppearance(true), "apply-appearance-caller");
    caller.start();
    caller.join();
    flushEdt();

    assertEquals(
        List.of(
            "applyCommonTweaks",
            "applyAccentOverrides",
            "settingsRefresh",
            "syncTextComponents",
            "chatReload",
            "restyle"),
        fixture.steps);
    assertFalse(fixture.steps.contains("install"));
    assertStepsRanOnEdt(fixture);
  }

  private static Fixture createFixture() {
    ChatStyles chatStyles = Mockito.mock(ChatStyles.class);
    ChatTranscriptStore transcripts = Mockito.mock(ChatTranscriptStore.class);
    UiSettingsBus settingsBus = Mockito.mock(UiSettingsBus.class);
    ThemeAccentSettingsBus accentBus = Mockito.mock(ThemeAccentSettingsBus.class);
    ThemeTweakSettingsBus tweakBus = Mockito.mock(ThemeTweakSettingsBus.class);
    ThemeCatalog themeCatalog = Mockito.mock(ThemeCatalog.class);
    ThemeAppearanceService appearanceService = Mockito.mock(ThemeAppearanceService.class);
    ThemeLookAndFeelInstaller lookAndFeelInstaller = Mockito.mock(ThemeLookAndFeelInstaller.class);
    ThemeTextComponentPaletteSyncService textComponentPaletteSyncService =
        Mockito.mock(ThemeTextComponentPaletteSyncService.class);

    List<String> steps = new CopyOnWriteArrayList<>();
    Map<String, Boolean> onEdtByStep = new ConcurrentHashMap<>();

    Mockito.when(accentBus.get()).thenReturn(new ThemeAccentSettings("#4C88D0", 70));
    Mockito.when(tweakBus.get())
        .thenReturn(new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10));

    Mockito.doAnswer(
            invocation -> {
              recordStep("install", steps, onEdtByStep);
              return null;
            })
        .when(lookAndFeelInstaller)
        .install(Mockito.anyString());
    Mockito.doAnswer(
            invocation -> {
              recordStep("applyCommonTweaks", steps, onEdtByStep);
              return null;
            })
        .when(appearanceService)
        .applyCommonTweaks(Mockito.any());
    Mockito.doAnswer(
            invocation -> {
              recordStep("applyAccentOverrides", steps, onEdtByStep);
              return null;
            })
        .when(appearanceService)
        .applyAccentOverrides(Mockito.any());
    Mockito.doAnswer(
            invocation -> {
              recordStep("settingsRefresh", steps, onEdtByStep);
              return null;
            })
        .when(settingsBus)
        .refresh();
    Mockito.doAnswer(
            invocation -> {
              recordStep("syncTextComponents", steps, onEdtByStep);
              return null;
            })
        .when(textComponentPaletteSyncService)
        .syncAllWindows();
    Mockito.doAnswer(
            invocation -> {
              recordStep("chatReload", steps, onEdtByStep);
              return null;
            })
        .when(chatStyles)
        .reload();
    Mockito.doAnswer(
            invocation -> {
              recordStep("restyle", steps, onEdtByStep);
              return null;
            })
        .when(transcripts)
        .restyleAllDocumentsCoalesced();

    ThemeManager manager =
        new ThemeManager(
            chatStyles,
            transcripts,
            settingsBus,
            accentBus,
            tweakBus,
            themeCatalog,
            appearanceService,
            lookAndFeelInstaller,
            textComponentPaletteSyncService);

    return new Fixture(manager, steps, onEdtByStep);
  }

  private static void recordStep(String step, List<String> steps, Map<String, Boolean> onEdtByStep) {
    steps.add(step);
    onEdtByStep.put(step, SwingUtilities.isEventDispatchThread());
  }

  private static void assertStepsRanOnEdt(Fixture fixture) {
    for (String step : fixture.steps) {
      assertEquals(Boolean.TRUE, fixture.onEdtByStep.get(step), step + " should run on the EDT");
    }
  }

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }

  private record Fixture(
      ThemeManager manager,
      List<String> steps,
      Map<String, Boolean> onEdtByStep) {}
}

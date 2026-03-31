package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.ui.settings.theme.ChatThemeSettings;
import cafe.woden.ircclient.ui.settings.theme.ThemeAccentSettings;
import cafe.woden.ircclient.ui.settings.theme.ThemeTweakSettings;
import org.junit.jupiter.api.Test;

class PreferencesDialogAppearanceRollbackPlanTest {

  @Test
  void noChangesProducesNoRollbackWork() {
    UiSettings ui = mock(UiSettings.class);
    ThemeAccentSettings accent = new ThemeAccentSettings("#336699", 42);
    ThemeTweakSettings tweaks =
        new ThemeTweakSettings(
            ThemeTweakSettings.ThemeDensity.COZY,
            8,
            true,
            ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY,
            13);
    ChatThemeSettings chat =
        new ChatThemeSettings(
            ChatThemeSettings.Preset.DEFAULT, null, null, null, 35, null, null, null, null, null);

    PreferencesDialog.AppearanceRollbackPlan plan =
        PreferencesDialog.planAppearanceRollback(
            "darcula", "Darcula", ui, ui, true, accent, accent, true, tweaks, tweaks, true, chat,
            chat);

    assertFalse(plan.hasAnyWork());
    assertFalse(plan.applyTheme());
    assertFalse(plan.applyAppearance());
    assertFalse(plan.refreshChatStyles());
  }

  @Test
  void themeChangePrefersThemeApplyOverAppearanceOrChatRefresh() {
    UiSettings committedUi = mock(UiSettings.class);
    UiSettings liveUi = mock(UiSettings.class);
    ThemeAccentSettings committedAccent = new ThemeAccentSettings("#336699", 42);
    ThemeAccentSettings liveAccent = new ThemeAccentSettings("#6699CC", 65);
    ThemeTweakSettings committedTweaks =
        new ThemeTweakSettings(
            ThemeTweakSettings.ThemeDensity.AUTO,
            10,
            false,
            ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY,
            13);
    ThemeTweakSettings liveTweaks =
        new ThemeTweakSettings(
            ThemeTweakSettings.ThemeDensity.SPACIOUS,
            14,
            true,
            ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY,
            16);
    ChatThemeSettings committedChat =
        new ChatThemeSettings(
            ChatThemeSettings.Preset.DEFAULT, null, null, null, 35, null, null, null, null, null);
    ChatThemeSettings liveChat =
        new ChatThemeSettings(
            ChatThemeSettings.Preset.ACCENTED, null, null, null, 60, null, null, null, null, null);

    PreferencesDialog.AppearanceRollbackPlan plan =
        PreferencesDialog.planAppearanceRollback(
            "darcula",
            "light",
            committedUi,
            liveUi,
            true,
            committedAccent,
            liveAccent,
            true,
            committedTweaks,
            liveTweaks,
            true,
            committedChat,
            liveChat);

    assertTrue(plan.applyTheme());
    assertFalse(plan.applyAppearance());
    assertFalse(plan.refreshChatStyles());
  }

  @Test
  void accentOrTweakChangesRequestAppearanceApply() {
    UiSettings ui = mock(UiSettings.class);
    ThemeAccentSettings committedAccent = new ThemeAccentSettings("#336699", 42);
    ThemeAccentSettings liveAccent = new ThemeAccentSettings("#336699", 60);
    ThemeTweakSettings tweaks =
        new ThemeTweakSettings(
            ThemeTweakSettings.ThemeDensity.AUTO,
            10,
            false,
            ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY,
            13);
    ChatThemeSettings chat =
        new ChatThemeSettings(
            ChatThemeSettings.Preset.DEFAULT, null, null, null, 35, null, null, null, null, null);

    PreferencesDialog.AppearanceRollbackPlan plan =
        PreferencesDialog.planAppearanceRollback(
            "darcula",
            "darcula",
            ui,
            ui,
            true,
            committedAccent,
            liveAccent,
            true,
            tweaks,
            tweaks,
            true,
            chat,
            chat);

    assertTrue(plan.restoreAccentSettings());
    assertTrue(plan.applyAppearance());
    assertFalse(plan.applyTheme());
    assertFalse(plan.refreshChatStyles());
  }

  @Test
  void chatThemeOnlyChangesRequestChatRefresh() {
    UiSettings ui = mock(UiSettings.class);
    ThemeAccentSettings accent = new ThemeAccentSettings("#336699", 42);
    ThemeTweakSettings tweaks =
        new ThemeTweakSettings(
            ThemeTweakSettings.ThemeDensity.AUTO,
            10,
            false,
            ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY,
            13);
    ChatThemeSettings committedChat =
        new ChatThemeSettings(
            ChatThemeSettings.Preset.DEFAULT, null, null, null, 35, null, null, null, null, null);
    ChatThemeSettings liveChat =
        new ChatThemeSettings(
            ChatThemeSettings.Preset.DEFAULT,
            "#AAAAAA",
            null,
            null,
            35,
            null,
            null,
            null,
            null,
            null);

    PreferencesDialog.AppearanceRollbackPlan plan =
        PreferencesDialog.planAppearanceRollback(
            "darcula",
            "darcula",
            ui,
            ui,
            true,
            accent,
            accent,
            true,
            tweaks,
            tweaks,
            true,
            committedChat,
            liveChat);

    assertTrue(plan.restoreChatThemeSettings());
    assertFalse(plan.applyTheme());
    assertFalse(plan.applyAppearance());
    assertTrue(plan.refreshChatStyles());
  }

  @Test
  void busAvailabilityGuardsRestoreWork() {
    UiSettings ui = mock(UiSettings.class);
    ThemeAccentSettings committedAccent = new ThemeAccentSettings("#336699", 42);
    ThemeAccentSettings liveAccent = new ThemeAccentSettings("#336699", 60);
    ThemeTweakSettings tweaks =
        new ThemeTweakSettings(
            ThemeTweakSettings.ThemeDensity.AUTO,
            10,
            false,
            ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY,
            13);
    ChatThemeSettings chat =
        new ChatThemeSettings(
            ChatThemeSettings.Preset.DEFAULT, null, null, null, 35, null, null, null, null, null);

    PreferencesDialog.AppearanceRollbackPlan plan =
        PreferencesDialog.planAppearanceRollback(
            "darcula",
            "darcula",
            ui,
            ui,
            false,
            committedAccent,
            liveAccent,
            true,
            tweaks,
            tweaks,
            true,
            chat,
            chat);

    assertFalse(plan.restoreAccentSettings());
    assertFalse(plan.applyTheme());
    assertFalse(plan.applyAppearance());
    assertFalse(plan.refreshChatStyles());
    assertFalse(plan.hasAnyWork());
  }
}

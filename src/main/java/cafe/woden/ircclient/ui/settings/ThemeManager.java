package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import java.awt.Window;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ThemeManager {

  public enum ThemeTone {
    SYSTEM,
    DARK,
    LIGHT
  }

  public enum ThemePack {
    SYSTEM,
    FLATLAF,
    DARKLAF,
    RETRO,
    MODERN,
    IRCAFE,
    INTELLIJ
  }

  public record ThemeOption(
      String id, String label, ThemeTone tone, ThemePack pack, boolean featured) {
    public boolean isDark() {
      return tone == ThemeTone.DARK;
    }
  }

  private final ChatStyles chatStyles;
  private final ChatTranscriptStore transcripts;
  private final UiSettingsBus settingsBus;
  private final ThemeAccentSettingsBus accentSettingsBus;
  private final ThemeTweakSettingsBus tweakSettingsBus;
  private final ThemeCatalog themeCatalog;
  private final ThemeAppearanceService appearanceService;
  private final ThemeLookAndFeelInstaller lookAndFeelInstaller;
  private final ThemeTextComponentPaletteSyncService textComponentPaletteSyncService;

  public ThemeManager(
      ChatStyles chatStyles,
      ChatTranscriptStore transcripts,
      UiSettingsBus settingsBus,
      ThemeAccentSettingsBus accentSettingsBus,
      ThemeTweakSettingsBus tweakSettingsBus) {
    this(
        chatStyles,
        transcripts,
        settingsBus,
        accentSettingsBus,
        tweakSettingsBus,
        new ThemeCatalog(),
        new ThemeAppearanceService(),
        new ThemeLookAndFeelInstaller(new ThemePresetRegistry(), new NimbusThemeOverrideService()),
        new ThemeTextComponentPaletteSyncService());
  }

  public ThemeManager(
      ChatStyles chatStyles,
      ChatTranscriptStore transcripts,
      UiSettingsBus settingsBus,
      ThemeAccentSettingsBus accentSettingsBus,
      ThemeTweakSettingsBus tweakSettingsBus,
      ThemeCatalog themeCatalog,
      ThemeAppearanceService appearanceService,
      ThemeLookAndFeelInstaller lookAndFeelInstaller) {
    this(
        chatStyles,
        transcripts,
        settingsBus,
        accentSettingsBus,
        tweakSettingsBus,
        themeCatalog,
        appearanceService,
        lookAndFeelInstaller,
        new ThemeTextComponentPaletteSyncService());
  }

  @Autowired
  public ThemeManager(
      ChatStyles chatStyles,
      ChatTranscriptStore transcripts,
      UiSettingsBus settingsBus,
      ThemeAccentSettingsBus accentSettingsBus,
      ThemeTweakSettingsBus tweakSettingsBus,
      ThemeCatalog themeCatalog,
      ThemeAppearanceService appearanceService,
      ThemeLookAndFeelInstaller lookAndFeelInstaller,
      ThemeTextComponentPaletteSyncService textComponentPaletteSyncService) {
    this.chatStyles = chatStyles;
    this.transcripts = transcripts;
    this.settingsBus = settingsBus;
    this.accentSettingsBus = accentSettingsBus;
    this.tweakSettingsBus = tweakSettingsBus;
    this.themeCatalog = themeCatalog;
    this.appearanceService = appearanceService;
    this.lookAndFeelInstaller = lookAndFeelInstaller;
    this.textComponentPaletteSyncService =
        textComponentPaletteSyncService != null
            ? textComponentPaletteSyncService
            : new ThemeTextComponentPaletteSyncService();
  }

  public ThemeOption[] supportedThemes() {
    return themeCatalog.supportedThemes();
  }

  public ThemeOption[] featuredThemes() {
    return themeCatalog.featuredThemes();
  }

  public ThemeOption[] themesForPicker(boolean includeAllIntelliJThemes) {
    return themeCatalog.themesForPicker(includeAllIntelliJThemes);
  }

  public void installLookAndFeel(String themeId) {
    runOnEdt(
        () -> {
          lookAndFeelInstaller.install(themeId);
          applyAppearanceOverrides();
          safeRun(chatStyles::reload);
        });
  }

  public void applyTheme(String themeId) {
    runOnEdt(
        () -> {
          boolean animateFlat = isFlatLafActive() || ThemeIdUtils.isLikelyFlatTarget(themeId);
          runWithFlatAnimation(
              animateFlat,
              true,
              () -> {
                lookAndFeelInstaller.install(themeId);
                applyAppearanceOverrides();
                refreshUiAndStyles();
              });
        });
  }

  /**
   * Apply accent/tweak UI defaults without changing the current Look & Feel. Used for live preview
   * (e.g. accent slider/color) to avoid the heavier LAF reset.
   */
  public void applyAppearance(boolean animate) {
    runOnEdt(
        () -> {
          boolean animateFlat = animate && isFlatLafActive();
          runWithFlatAnimation(
              animateFlat,
              animateFlat,
              () -> {
                applyAppearanceOverrides();
                refreshUiAndStyles();
              });
        });
  }

  public void applyAppearance() {
    applyAppearance(true);
  }

  public void refreshChatStyles() {
    runOnEdt(this::refreshChatStylesInternal);
  }

  private void applyAppearanceOverrides() {
    appearanceService.applyCommonTweaks(tweakSettingsBus != null ? tweakSettingsBus.get() : null);
    appearanceService.applyAccentOverrides(
        accentSettingsBus != null ? accentSettingsBus.get() : null);
    SvgIcons.clearCache();
  }

  private void refreshUiAndStyles() {
    if (isFlatLafActive()) {
      safeRun(FlatLaf::updateUI);
    }

    for (Window w : Window.getWindows()) {
      safeRun(
          () -> {
            SwingUtilities.updateComponentTreeUI(w);
          });
    }

    safeRun(
        () -> {
          if (settingsBus != null) settingsBus.refresh();
        });

    safeRun(textComponentPaletteSyncService::syncAllWindows);

    for (Window w : Window.getWindows()) {
      safeRun(
          () -> {
            w.invalidate();
            w.repaint();
          });
    }

    refreshChatStylesInternal();
  }

  private void refreshChatStylesInternal() {
    safeRun(chatStyles::reload);
    safeRun(transcripts::restyleAllDocumentsCoalesced);
  }

  private static void runWithFlatAnimation(
      boolean animateFlat, boolean stopWhenNoSnapshot, Runnable work) {
    boolean snap = false;

    if (animateFlat || stopWhenNoSnapshot) {
      stopFlatAnimationQuietly();
    }

    if (animateFlat) {
      try {
        for (Window w : Window.getWindows()) {
          if (w != null && w.isShowing()) {
            FlatAnimatedLafChange.showSnapshot();
            snap = true;
            break;
          }
        }
      } catch (Exception ignored) {
        snap = false;
      }
    }

    try {
      work.run();
    } finally {
      if (snap) {
        try {
          FlatAnimatedLafChange.hideSnapshotWithAnimation();
        } catch (Exception ignored) {
          stopFlatAnimationQuietly();
        }
      } else if (stopWhenNoSnapshot || animateFlat) {
        stopFlatAnimationQuietly();
      }
    }
  }

  private static void stopFlatAnimationQuietly() {
    safeRun(FlatAnimatedLafChange::stop);
  }

  private static boolean isFlatLafActive() {
    return UIManager.getLookAndFeel() instanceof FlatLaf;
  }

  private static void safeRun(Runnable action) {
    try {
      action.run();
    } catch (Exception ignored) {
    }
  }

  private static void runOnEdt(Runnable r) {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
    } else {
      SwingUtilities.invokeLater(r);
    }
  }
}

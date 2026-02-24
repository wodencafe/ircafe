package cafe.woden.ircclient.ui.settings;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
class ThemeLookAndFeelInstaller {

  private static final Logger log = LoggerFactory.getLogger(ThemeLookAndFeelInstaller.class);

  private static final String NIMBUS_LAF_CLASS = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
  private static final String METAL_LAF_CLASS = "javax.swing.plaf.metal.MetalLookAndFeel";
  private static final String MOTIF_LAF_CLASS = "com.sun.java.swing.plaf.motif.MotifLookAndFeel";
  private static final String WINDOWS_LAF_CLASS =
      "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
  private static final String GTK_LAF_CLASS = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";

  private interface InstallAction {
    void install() throws Exception;
  }

  private final ThemePresetRegistry presetRegistry;
  private final NimbusThemeOverrideService nimbusOverrides;
  private final Map<String, InstallAction> installActions;

  ThemeLookAndFeelInstaller(
      ThemePresetRegistry presetRegistry, NimbusThemeOverrideService nimbusOverrides) {
    this.presetRegistry = presetRegistry;
    this.nimbusOverrides = nimbusOverrides;
    this.installActions = buildInstallActions();
  }

  void install(String themeId) {
    String raw = themeId != null ? themeId.trim() : "";
    if (raw.isEmpty()) raw = "darcula";

    String lower = raw.toLowerCase(Locale.ROOT);
    if (!nimbusOverrides.isDarkVariant(lower)) {
      nimbusOverrides.clearDarkOverrides();
    }
    if (!nimbusOverrides.isTintVariant(lower)) {
      nimbusOverrides.clearTintOverrides();
    }

    if (lower.startsWith(IntelliJThemePack.ID_PREFIX)) {
      String className = raw.substring(raw.indexOf(':') + 1).trim();
      if (trySetLookAndFeelByClassName(className)) {
        if (!isFlatLafActive()) clearFlatAccentDefaults();
        return;
      }
    } else if (ThemeIdUtils.looksLikeClassName(raw)) {
      if (trySetLookAndFeelByClassName(raw)) {
        if (!isFlatLafActive()) clearFlatAccentDefaults();
        return;
      }
    }

    try {
      InstallAction action = installActions.get(lower);
      if (action != null) {
        action.install();
      } else {
        ThemePresetRegistry.ThemePreset preset = presetRegistry.byId(lower);
        if (preset != null) {
          installFlatPreset(preset);
        } else {
          UIManager.setLookAndFeel(new FlatDarculaLaf());
        }
      }

      if (!isFlatLafActive()) {
        clearFlatAccentDefaults();
      }
    } catch (Exception e) {
      log.warn("[ircafe] Could not set Look & Feel '{}'", raw, e);
    }
  }

  private Map<String, InstallAction> buildInstallActions() {
    Map<String, InstallAction> out = new LinkedHashMap<>();

    out.put("system", () -> UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()));
    out.put("nimbus", () -> applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS));

    nimbusOverrides
        .variantIds()
        .forEach((id) -> out.put(id, () -> applyNimbusVariantLookAndFeelOrFallback(id)));

    out.put("metal", () -> applyMetalLookAndFeelOrFallback(false));
    out.put("metal-ocean", () -> applyMetalLookAndFeelOrFallback(false));
    out.put("metal-steel", () -> applyMetalLookAndFeelOrFallback(true));

    out.put("motif", () -> applyLegacySystemLookAndFeelOrFallback(MOTIF_LAF_CLASS));
    out.put("windows", () -> applyLegacySystemLookAndFeelOrFallback(WINDOWS_LAF_CLASS));
    out.put("gtk", () -> applyLegacySystemLookAndFeelOrFallback(GTK_LAF_CLASS));

    out.put("light", () -> UIManager.setLookAndFeel(new FlatLightLaf()));
    out.put("dark", () -> UIManager.setLookAndFeel(new FlatDarkLaf()));
    out.put("darcula", () -> UIManager.setLookAndFeel(new FlatDarculaLaf()));

    out.put(
        "darklaf",
        () -> installDarkLafOrFallback(DarkLafSupport::installDefault, FlatDarculaLaf::new));
    out.put(
        "darklaf-darcula",
        () -> installDarkLafOrFallback(DarkLafSupport::installDarcula, FlatDarculaLaf::new));
    out.put(
        "darklaf-solarized-dark",
        () -> installDarkLafOrFallback(DarkLafSupport::installSolarizedDark, FlatDarculaLaf::new));
    out.put(
        "darklaf-high-contrast-dark",
        () ->
            installDarkLafOrFallback(DarkLafSupport::installHighContrastDark, FlatDarculaLaf::new));
    out.put(
        "darklaf-light",
        () -> installDarkLafOrFallback(DarkLafSupport::installLight, FlatLightLaf::new));
    out.put(
        "darklaf-high-contrast-light",
        () ->
            installDarkLafOrFallback(DarkLafSupport::installHighContrastLight, FlatLightLaf::new));
    out.put(
        "darklaf-intellij",
        () -> installDarkLafOrFallback(DarkLafSupport::installIntelliJ, FlatLightLaf::new));

    return Map.copyOf(out);
  }

  private void installFlatPreset(ThemePresetRegistry.ThemePreset preset) throws Exception {
    if (preset.dark()) {
      FlatDarkLaf laf = new FlatDarkLaf();
      laf.setExtraDefaults(preset.extraDefaults());
      UIManager.setLookAndFeel(laf);
      return;
    }

    FlatLightLaf laf = new FlatLightLaf();
    laf.setExtraDefaults(preset.extraDefaults());
    UIManager.setLookAndFeel(laf);
  }

  private static void installDarkLafOrFallback(
      BooleanSupplier darkLafInstallAction, Supplier<LookAndFeel> fallbackFactory)
      throws Exception {
    if (!darkLafInstallAction.getAsBoolean()) {
      UIManager.setLookAndFeel(fallbackFactory.get());
      return;
    }
    ensureBaselineAccentContrast();
  }

  private void applyLegacySystemLookAndFeelOrFallback(String className) throws Exception {
    if (className == null
        || className.isBlank()
        || !isLookAndFeelInstalled(className)
        || !trySetLookAndFeelByClassName(className)) {
      UIManager.setLookAndFeel(new FlatDarculaLaf());
    }
  }

  private void applyNimbusVariantLookAndFeelOrFallback(String themeId) throws Exception {
    if (!nimbusOverrides.applyVariant(themeId)) {
      if (ThemeLookAndFeelUtils.isNimbusDebugEnabled()) {
        nimbusDebug(
            "[ircafe][nimbus] variant '"
                + themeId
                + "' not recognized, falling back to plain Nimbus");
      }
      applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
      return;
    }

    applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);

    LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName())) {
      nimbusOverrides.applyVariant(themeId);
      if (ThemeLookAndFeelUtils.isNimbusDebugEnabled()) {
        nimbusDebug("[ircafe][nimbus] re-applied '" + themeId + "' after Nimbus LAF install");
      }
    } else if (ThemeLookAndFeelUtils.isNimbusDebugEnabled()) {
      nimbusDebug(
          "[ircafe][nimbus] expected Nimbus after '"
              + themeId
              + "' but got '"
              + (laf != null ? laf.getClass().getName() : "null")
              + "'");
    }
  }

  private static void nimbusDebug(String message) {
    log.warn(message);
    System.err.println(message);
  }

  private void applyMetalLookAndFeelOrFallback(boolean steel) throws Exception {
    MetalLookAndFeel.setCurrentTheme(steel ? new DefaultMetalTheme() : new OceanTheme());
    applyLegacySystemLookAndFeelOrFallback(METAL_LAF_CLASS);

    if (steel) {
      UIManager.put("Component.focusColor", ThemeColorUtils.uiColor(0x2D, 0x5B, 0x8A));
    }
  }

  private static void clearFlatAccentDefaults() {
    UIManager.put("@accentColor", null);
    UIManager.put("@accentBaseColor", null);
    UIManager.put("@accentBase2Color", null);
  }

  private static void ensureBaselineAccentContrast() {
    Color panelBg = UIManager.getColor("Panel.background");
    if (panelBg == null) panelBg = UIManager.getColor("control");
    if (panelBg == null) return;

    Color focus = UIManager.getColor("Component.focusColor");
    if (focus == null) focus = UIManager.getColor("List.selectionBackground");
    if (focus == null) focus = UIManager.getColor("TextComponent.selectionBackground");
    if (focus != null) {
      UIManager.put(
          "Component.focusColor",
          ThemeColorUtils.ensureContrastAgainstBackground(focus, panelBg, 1.25));
    }

    Color link = UIManager.getColor("Component.linkColor");
    if (link == null) link = focus;
    if (link != null) {
      UIManager.put(
          "Component.linkColor",
          ThemeColorUtils.ensureContrastAgainstBackground(link, panelBg, 1.25));
    }
  }

  private static boolean isLookAndFeelInstalled(String className) {
    if (className == null || className.isBlank()) return false;
    return ThemeLookAndFeelUtils.installedLookAndFeelClassNames()
        .contains(className.toLowerCase(Locale.ROOT));
  }

  private boolean trySetLookAndFeelByClassName(String className) {
    if (className == null || className.isBlank()) return false;
    try {
      if (IntelliJThemePack.install(className)) return true;
    } catch (Exception ignored) {
    }

    try {
      Class<?> clazz = Class.forName(className);
      Object instance = clazz.getDeclaredConstructor().newInstance();
      if (instance instanceof LookAndFeel laf) {
        UIManager.setLookAndFeel(laf);
      } else {
        UIManager.setLookAndFeel(className);
      }
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean isFlatLafActive() {
    LookAndFeel laf = UIManager.getLookAndFeel();
    return laf instanceof FlatLaf;
  }
}

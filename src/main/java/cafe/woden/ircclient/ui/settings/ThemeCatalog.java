package cafe.woden.ircclient.ui.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
class ThemeCatalog {

  private record LegacySystemThemeDefinition(
      String id,
      String label,
      ThemeManager.ThemeTone tone,
      String lafClassName,
      boolean featured) {}

  private static final String NIMBUS_LAF_CLASS = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
  private static final String METAL_LAF_CLASS = "javax.swing.plaf.metal.MetalLookAndFeel";
  private static final String MOTIF_LAF_CLASS = "com.sun.java.swing.plaf.motif.MotifLookAndFeel";
  private static final String WINDOWS_LAF_CLASS = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
  private static final String GTK_LAF_CLASS = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";

  private static final LegacySystemThemeDefinition[] LEGACY_SYSTEM_THEME_DEFINITIONS =
      new LegacySystemThemeDefinition[] {
          new LegacySystemThemeDefinition(
              "nimbus", "Nimbus", ThemeManager.ThemeTone.LIGHT, NIMBUS_LAF_CLASS, true),
          new LegacySystemThemeDefinition(
              "nimbus-dark", "Nimbus (Dark)", ThemeManager.ThemeTone.DARK, NIMBUS_LAF_CLASS, false),
          new LegacySystemThemeDefinition(
              "nimbus-dark-amber",
              "Nimbus (Dark Amber)",
              ThemeManager.ThemeTone.DARK,
              NIMBUS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "nimbus-dark-blue",
              "Nimbus (Dark Blue)",
              ThemeManager.ThemeTone.DARK,
              NIMBUS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "nimbus-dark-violet",
              "Nimbus (Dark Violet)",
              ThemeManager.ThemeTone.DARK,
              NIMBUS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "nimbus-dark-green",
              "Nimbus (Dark Green)",
              ThemeManager.ThemeTone.DARK,
              NIMBUS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "nimbus-dark-orange",
              "Nimbus (Dark Orange)",
              ThemeManager.ThemeTone.DARK,
              NIMBUS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "nimbus-dark-magenta",
              "Nimbus (Dark Magenta)",
              ThemeManager.ThemeTone.DARK,
              NIMBUS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "nimbus-orange",
              "Nimbus (Orange)",
              ThemeManager.ThemeTone.LIGHT,
              NIMBUS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "nimbus-green",
              "Nimbus (Green)",
              ThemeManager.ThemeTone.LIGHT,
              NIMBUS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "nimbus-blue", "Nimbus (Blue)", ThemeManager.ThemeTone.LIGHT, NIMBUS_LAF_CLASS, false),
          new LegacySystemThemeDefinition(
              "nimbus-violet",
              "Nimbus (Violet)",
              ThemeManager.ThemeTone.LIGHT,
              NIMBUS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "nimbus-magenta",
              "Nimbus (Magenta)",
              ThemeManager.ThemeTone.LIGHT,
              NIMBUS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "nimbus-amber",
              "Nimbus (Amber)",
              ThemeManager.ThemeTone.LIGHT,
              NIMBUS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "metal-ocean", "Metal (Ocean)", ThemeManager.ThemeTone.LIGHT, METAL_LAF_CLASS, true),
          new LegacySystemThemeDefinition(
              "metal-steel", "Metal (Steel)", ThemeManager.ThemeTone.LIGHT, METAL_LAF_CLASS, false),
          new LegacySystemThemeDefinition(
              "motif", "Motif", ThemeManager.ThemeTone.LIGHT, MOTIF_LAF_CLASS, true),
          new LegacySystemThemeDefinition(
              "windows",
              "Windows Classic",
              ThemeManager.ThemeTone.SYSTEM,
              WINDOWS_LAF_CLASS,
              false),
          new LegacySystemThemeDefinition(
              "gtk", "GTK", ThemeManager.ThemeTone.SYSTEM, GTK_LAF_CLASS, false)
      };

  private static final ThemeManager.ThemeOption[] BASE_THEMES =
      new ThemeManager.ThemeOption[] {
          new ThemeManager.ThemeOption(
              "system", "Native (System)", ThemeManager.ThemeTone.SYSTEM, ThemeManager.ThemePack.SYSTEM, true),

          new ThemeManager.ThemeOption(
              "dark", "Flat Dark", ThemeManager.ThemeTone.DARK, ThemeManager.ThemePack.FLATLAF, true),
          new ThemeManager.ThemeOption(
              "darcula",
              "Flat Darcula",
              ThemeManager.ThemeTone.DARK,
              ThemeManager.ThemePack.FLATLAF,
              true),
          new ThemeManager.ThemeOption(
              "light", "Flat Light", ThemeManager.ThemeTone.LIGHT, ThemeManager.ThemePack.FLATLAF, true),

          new ThemeManager.ThemeOption(
              "crt-green", "CRT Green", ThemeManager.ThemeTone.DARK, ThemeManager.ThemePack.RETRO, false),
          new ThemeManager.ThemeOption(
              "cde-blue", "CDE Blue", ThemeManager.ThemeTone.LIGHT, ThemeManager.ThemePack.RETRO, false),

          new ThemeManager.ThemeOption(
              "tokyo-night",
              "Tokyo Night",
              ThemeManager.ThemeTone.DARK,
              ThemeManager.ThemePack.MODERN,
              true),
          new ThemeManager.ThemeOption(
              "catppuccin-mocha",
              "Catppuccin Mocha",
              ThemeManager.ThemeTone.DARK,
              ThemeManager.ThemePack.MODERN,
              false),
          new ThemeManager.ThemeOption(
              "gruvbox-dark",
              "Gruvbox Dark",
              ThemeManager.ThemeTone.DARK,
              ThemeManager.ThemePack.MODERN,
              false),
          new ThemeManager.ThemeOption(
              "github-soft-light",
              "GitHub Soft Light",
              ThemeManager.ThemeTone.LIGHT,
              ThemeManager.ThemePack.MODERN,
              true),

          new ThemeManager.ThemeOption(
              "blue-dark", "Flat Blue (Dark)", ThemeManager.ThemeTone.DARK, ThemeManager.ThemePack.IRCAFE, true),
          new ThemeManager.ThemeOption(
              "violet-nebula",
              "Violet Nebula",
              ThemeManager.ThemeTone.DARK,
              ThemeManager.ThemePack.IRCAFE,
              true),
          new ThemeManager.ThemeOption(
              "high-contrast-dark",
              "High Contrast Dark",
              ThemeManager.ThemeTone.DARK,
              ThemeManager.ThemePack.IRCAFE,
              true),
          new ThemeManager.ThemeOption(
              "graphite-mono",
              "Graphite Mono",
              ThemeManager.ThemeTone.DARK,
              ThemeManager.ThemePack.IRCAFE,
              false),
          new ThemeManager.ThemeOption(
              "forest-dark",
              "Forest Dark",
              ThemeManager.ThemeTone.DARK,
              ThemeManager.ThemePack.IRCAFE,
              false),
          new ThemeManager.ThemeOption(
              "ruby-night", "Ruby Night", ThemeManager.ThemeTone.DARK, ThemeManager.ThemePack.IRCAFE, false),
          new ThemeManager.ThemeOption(
              "solarized-dark",
              "Solarized Dark",
              ThemeManager.ThemeTone.DARK,
              ThemeManager.ThemePack.IRCAFE,
              false),
          new ThemeManager.ThemeOption(
              "sunset-dark", "Sunset Dark", ThemeManager.ThemeTone.DARK, ThemeManager.ThemePack.IRCAFE, false),
          new ThemeManager.ThemeOption(
              "terminal-amber",
              "Terminal Amber",
              ThemeManager.ThemeTone.DARK,
              ThemeManager.ThemePack.IRCAFE,
              false),
          new ThemeManager.ThemeOption(
              "teal-deep", "Teal Deep", ThemeManager.ThemeTone.DARK, ThemeManager.ThemePack.IRCAFE, false),
          new ThemeManager.ThemeOption(
              "orange", "Flat Orange (Dark)", ThemeManager.ThemeTone.DARK, ThemeManager.ThemePack.IRCAFE, false),

          new ThemeManager.ThemeOption(
              "nordic-light",
              "Nordic Light",
              ThemeManager.ThemeTone.LIGHT,
              ThemeManager.ThemePack.IRCAFE,
              true),
          new ThemeManager.ThemeOption(
              "blue-light", "Flat Blue (Light)", ThemeManager.ThemeTone.LIGHT, ThemeManager.ThemePack.IRCAFE, true),
          new ThemeManager.ThemeOption(
              "arctic-light",
              "Arctic Light",
              ThemeManager.ThemeTone.LIGHT,
              ThemeManager.ThemePack.IRCAFE,
              false),
          new ThemeManager.ThemeOption(
              "mint-light", "Mint Light", ThemeManager.ThemeTone.LIGHT, ThemeManager.ThemePack.IRCAFE, false),
          new ThemeManager.ThemeOption(
              "solarized-light",
              "Solarized Light",
              ThemeManager.ThemeTone.LIGHT,
              ThemeManager.ThemePack.IRCAFE,
              false)
      };

  private static volatile ThemeManager.ThemeOption[] cachedThemes;
  private static volatile ThemeManager.ThemeOption[] cachedThemesWithAllIntelliJ;

  ThemeManager.ThemeOption[] supportedThemes() {
    return allThemes().clone();
  }

  ThemeManager.ThemeOption[] featuredThemes() {
    ThemeManager.ThemeOption[] all = allThemes();
    List<ThemeManager.ThemeOption> featured =
        Arrays.stream(all).filter(ThemeManager.ThemeOption::featured).toList();

    List<ThemeManager.ThemeOption> out = new ArrayList<>(featured.size());
    addFeaturedById(out, featured, "darcula");
    addFeaturedById(out, featured, "darklaf");

    for (ThemeManager.ThemeOption t : featured) {
      if (t == null || t.id() == null) continue;
      if ("darcula".equalsIgnoreCase(t.id())) continue;
      if ("darklaf".equalsIgnoreCase(t.id())) continue;
      out.add(t);
    }

    return out.toArray(ThemeManager.ThemeOption[]::new);
  }

  ThemeManager.ThemeOption[] themesForPicker(boolean includeAllIntelliJThemes) {
    if (!includeAllIntelliJThemes) {
      return supportedThemes();
    }

    ThemeManager.ThemeOption[] cached = cachedThemesWithAllIntelliJ;
    if (cached != null) return cached.clone();

    List<ThemeManager.ThemeOption> out = new ArrayList<>();
    Collections.addAll(out, BASE_THEMES);
    out.addAll(darkLafThemes());
    out.addAll(legacySystemThemes());

    List<IntelliJThemePack.PackTheme> pack = IntelliJThemePack.listThemes();
    if (!pack.isEmpty()) {
      Set<String> seen = new HashSet<>();
      for (ThemeManager.ThemeOption o : out) {
        if (o != null && o.id() != null) seen.add(o.id());
      }

      for (IntelliJThemePack.PackTheme t : pack) {
        if (t == null || t.id() == null || t.id().isBlank()) continue;
        if (!seen.add(t.id())) continue;

        ThemeManager.ThemeTone tone = t.dark() ? ThemeManager.ThemeTone.DARK : ThemeManager.ThemeTone.LIGHT;
        out.add(new ThemeManager.ThemeOption(t.id(), "IntelliJ: " + t.label(), tone, ThemeManager.ThemePack.INTELLIJ, false));
      }
    }

    cached = out.toArray(ThemeManager.ThemeOption[]::new);
    cachedThemesWithAllIntelliJ = cached;
    return cached.clone();
  }

  private static ThemeManager.ThemeOption[] allThemes() {
    ThemeManager.ThemeOption[] cached = cachedThemes;
    if (cached != null) return cached;

    List<ThemeManager.ThemeOption> out = new ArrayList<>();
    Collections.addAll(out, BASE_THEMES);
    out.addAll(darkLafThemes());
    out.addAll(legacySystemThemes());
    out.addAll(buildCuratedIntelliJThemes());

    cached = out.toArray(ThemeManager.ThemeOption[]::new);
    cachedThemes = cached;
    return cached;
  }

  private static List<ThemeManager.ThemeOption> darkLafThemes() {
    if (!DarkLafSupport.isAvailable()) return List.of();
    return List.of(
        new ThemeManager.ThemeOption(
            "darklaf", "DarkLaf (One Dark)", ThemeManager.ThemeTone.DARK, ThemeManager.ThemePack.DARKLAF, true),
        new ThemeManager.ThemeOption(
            "darklaf-darcula",
            "DarkLaf (Darcula)",
            ThemeManager.ThemeTone.DARK,
            ThemeManager.ThemePack.DARKLAF,
            false),
        new ThemeManager.ThemeOption(
            "darklaf-solarized-dark",
            "DarkLaf (Solarized Dark)",
            ThemeManager.ThemeTone.DARK,
            ThemeManager.ThemePack.DARKLAF,
            false),
        new ThemeManager.ThemeOption(
            "darklaf-high-contrast-dark",
            "DarkLaf (High Contrast Dark)",
            ThemeManager.ThemeTone.DARK,
            ThemeManager.ThemePack.DARKLAF,
            false),
        new ThemeManager.ThemeOption(
            "darklaf-light",
            "DarkLaf (Solarized Light)",
            ThemeManager.ThemeTone.LIGHT,
            ThemeManager.ThemePack.DARKLAF,
            false),
        new ThemeManager.ThemeOption(
            "darklaf-high-contrast-light",
            "DarkLaf (High Contrast Light)",
            ThemeManager.ThemeTone.LIGHT,
            ThemeManager.ThemePack.DARKLAF,
            false),
        new ThemeManager.ThemeOption(
            "darklaf-intellij",
            "DarkLaf (IntelliJ)",
            ThemeManager.ThemeTone.LIGHT,
            ThemeManager.ThemePack.DARKLAF,
            false));
  }

  private static List<ThemeManager.ThemeOption> legacySystemThemes() {
    Set<String> installed = ThemeLookAndFeelUtils.installedLookAndFeelClassNames();
    if (installed.isEmpty()) return List.of();

    List<ThemeManager.ThemeOption> out = new ArrayList<>();
    for (LegacySystemThemeDefinition def : LEGACY_SYSTEM_THEME_DEFINITIONS) {
      if (def == null || def.lafClassName() == null || def.lafClassName().isBlank()) continue;
      if (!installed.contains(def.lafClassName().toLowerCase(Locale.ROOT))) continue;
      out.add(
          new ThemeManager.ThemeOption(
              def.id(), def.label(), def.tone(), ThemeManager.ThemePack.SYSTEM, def.featured()));
    }

    return out;
  }

  private static List<ThemeManager.ThemeOption> buildCuratedIntelliJThemes() {
    List<IntelliJThemePack.PackTheme> pack = IntelliJThemePack.listThemes();
    if (pack.isEmpty()) return List.of();

    String[] priority =
        new String[] {
          "tokyo night",
          "catppuccin",
          "gruvbox",
          "github dark",
          "github light",
          "one dark",
          "dracula",
          "arc dark",
          "monokai",
          "nord",
          "solarized dark",
          "solarized light",
          "gradianto",
          "github",
          "material",
          "cobalt"
        };

    final int maxThemes = 16;

    Set<String> chosenIds = new HashSet<>();
    List<ThemeManager.ThemeOption> curated = new ArrayList<>();

    java.util.function.Consumer<IntelliJThemePack.PackTheme> add =
        t -> {
          if (t == null) return;
          if (!chosenIds.add(t.id())) return;

          ThemeManager.ThemeTone tone =
              t.dark() ? ThemeManager.ThemeTone.DARK : ThemeManager.ThemeTone.LIGHT;
          boolean featured = curated.size() < 3;

          curated.add(
              new ThemeManager.ThemeOption(
                  t.id(), "IntelliJ: " + t.label(), tone, ThemeManager.ThemePack.INTELLIJ, featured));
        };

    for (String fragment : priority) {
      if (curated.size() >= maxThemes) break;
      String lowerFragment = fragment.toLowerCase(Locale.ROOT);

      for (IntelliJThemePack.PackTheme t : pack) {
        if (t == null) continue;

        String name = t.label() != null ? t.label().toLowerCase(Locale.ROOT) : "";
        String className = t.lafClassName() != null ? t.lafClassName().toLowerCase(Locale.ROOT) : "";
        if (name.contains(lowerFragment) || className.contains(lowerFragment.replace(" ", ""))) {
          add.accept(t);
          break;
        }
      }
    }

    if (curated.size() < maxThemes) {
      for (IntelliJThemePack.PackTheme t : pack) {
        if (curated.size() >= maxThemes) break;
        if (t != null && t.dark()) add.accept(t);
      }
      for (IntelliJThemePack.PackTheme t : pack) {
        if (curated.size() >= maxThemes) break;
        if (t != null && !t.dark()) add.accept(t);
      }
    }

    return curated;
  }

  private static void addFeaturedById(
      List<ThemeManager.ThemeOption> out, List<ThemeManager.ThemeOption> featured, String wantedId) {
    if (out == null || featured == null || wantedId == null || wantedId.isBlank()) return;

    for (ThemeManager.ThemeOption t : featured) {
      if (t == null || t.id() == null) continue;
      if (t.id().equalsIgnoreCase(wantedId)) {
        out.add(t);
        return;
      }
    }
  }
}

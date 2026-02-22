package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import java.awt.Color;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ThemeManager {

  private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

  public enum ThemeTone {
    SYSTEM,
    DARK,
    LIGHT
  }

  public enum ThemePack {
    SYSTEM,
    FLATLAF,
    RETRO,
    MODERN,
    IRCAFE,
    INTELLIJ
  }

  public record ThemeOption(String id, String label, ThemeTone tone, ThemePack pack, boolean featured) {
    public boolean isDark() {
      return tone == ThemeTone.DARK;
    }
  }

  private record LegacySystemThemeDefinition(
      String id,
      String label,
      ThemeTone tone,
      String lafClassName,
      boolean featured
  ) {
  }

  private static final Map<String, String> ORANGE_DARK_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#2F241E"),
      Map.entry("@foreground", "#F1DEC9"),
      Map.entry("@componentBackground", "#3A2C24"),
      Map.entry("@buttonBackground", "#4A3328"),
      Map.entry("@menuBackground", "#281F1A"),
      Map.entry("@accentColor", "#E48A33"),
      Map.entry("@accentBaseColor", "#D8751D"),
      Map.entry("@accentBase2Color", "#F0A14F"),
      Map.entry("Component.focusColor", "#F0A14F"),
      Map.entry("Component.linkColor", "#FFB367"),
      Map.entry("TextComponent.selectionBackground", "#A65414"),
      Map.entry("TextComponent.selectionForeground", "#FFF4E8"),
      Map.entry("List.selectionBackground", "#A65414"),
      Map.entry("Table.selectionBackground", "#A65414"),
      Map.entry("Tree.selectionBackground", "#A65414")
  );

  private static final Map<String, String> BLUE_DARK_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#1E2734"),
      Map.entry("@foreground", "#DCEBFF"),
      Map.entry("@componentBackground", "#273447"),
      Map.entry("@buttonBackground", "#2C3E56"),
      Map.entry("@menuBackground", "#18212C"),
      Map.entry("@accentColor", "#4F8AD9"),
      Map.entry("@accentBaseColor", "#3B78C9"),
      Map.entry("@accentBase2Color", "#6DA2EA"),
      Map.entry("Component.focusColor", "#6DA2EA"),
      Map.entry("Component.linkColor", "#8BC0FF"),
      Map.entry("TextComponent.selectionBackground", "#2F5F9E"),
      Map.entry("TextComponent.selectionForeground", "#F3F8FF"),
      Map.entry("List.selectionBackground", "#2F5F9E"),
      Map.entry("Table.selectionBackground", "#2F5F9E"),
      Map.entry("Tree.selectionBackground", "#2F5F9E")
  );

  private static final Map<String, String> BLUE_LIGHT_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#EEF5FF"),
      Map.entry("@foreground", "#1F3552"),
      Map.entry("@componentBackground", "#FAFCFF"),
      Map.entry("@buttonBackground", "#DCEBFF"),
      Map.entry("@menuBackground", "#E2EEFF"),
      Map.entry("@accentColor", "#2E6FBE"),
      Map.entry("@accentBaseColor", "#2E6FBE"),
      Map.entry("@accentBase2Color", "#4C88D0"),
      Map.entry("Component.focusColor", "#4C88D0"),
      Map.entry("Component.linkColor", "#1D5DAA"),
      Map.entry("TextComponent.selectionBackground", "#B8D6FF"),
      Map.entry("TextComponent.selectionForeground", "#10253F"),
      Map.entry("List.selectionBackground", "#B8D6FF"),
      Map.entry("Table.selectionBackground", "#B8D6FF"),
      Map.entry("Tree.selectionBackground", "#B8D6FF")
  );

  private static final Map<String, String> NORDIC_LIGHT_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#ECEFF4"),
      Map.entry("@foreground", "#2E3440"),
      Map.entry("@componentBackground", "#F8FAFD"),
      Map.entry("@buttonBackground", "#E5E9F0"),
      Map.entry("@menuBackground", "#E2E8F1"),
      Map.entry("@accentColor", "#5E81AC"),
      Map.entry("@accentBaseColor", "#5E81AC"),
      Map.entry("@accentBase2Color", "#81A1C1"),
      Map.entry("Component.focusColor", "#81A1C1"),
      Map.entry("Component.linkColor", "#4C78A8"),
      Map.entry("TextComponent.selectionBackground", "#C9DAEE"),
      Map.entry("TextComponent.selectionForeground", "#1E2633"),
      Map.entry("List.selectionBackground", "#C9DAEE"),
      Map.entry("Table.selectionBackground", "#C9DAEE"),
      Map.entry("Tree.selectionBackground", "#C9DAEE")
  );

  private static final Map<String, String> SOLARIZED_DARK_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#002B36"),
      Map.entry("@foreground", "#93A1A1"),
      Map.entry("@componentBackground", "#073642"),
      Map.entry("@buttonBackground", "#0B3A46"),
      Map.entry("@menuBackground", "#00232C"),
      Map.entry("@accentColor", "#268BD2"),
      Map.entry("@accentBaseColor", "#268BD2"),
      Map.entry("@accentBase2Color", "#2AA198"),
      Map.entry("Component.focusColor", "#2AA198"),
      Map.entry("Component.linkColor", "#268BD2"),
      Map.entry("TextComponent.selectionBackground", "#0A4A5C"),
      Map.entry("TextComponent.selectionForeground", "#EEE8D5"),
      Map.entry("List.selectionBackground", "#0A4A5C"),
      Map.entry("Table.selectionBackground", "#0A4A5C"),
      Map.entry("Tree.selectionBackground", "#0A4A5C")
  );

  private static final Map<String, String> SOLARIZED_LIGHT_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#FDF6E3"),
      Map.entry("@foreground", "#586E75"),
      Map.entry("@componentBackground", "#FFFBF0"),
      Map.entry("@buttonBackground", "#EEE8D5"),
      Map.entry("@menuBackground", "#F5EFD9"),
      Map.entry("@accentColor", "#268BD2"),
      Map.entry("@accentBaseColor", "#268BD2"),
      Map.entry("@accentBase2Color", "#2AA198"),
      Map.entry("Component.focusColor", "#2AA198"),
      Map.entry("Component.linkColor", "#1E6FB0"),
      Map.entry("TextComponent.selectionBackground", "#D9ECFF"),
      Map.entry("TextComponent.selectionForeground", "#073642"),
      Map.entry("List.selectionBackground", "#D9ECFF"),
      Map.entry("Table.selectionBackground", "#D9ECFF"),
      Map.entry("Tree.selectionBackground", "#D9ECFF")
  );

  private static final Map<String, String> FOREST_DARK_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#1E2A22"),
      Map.entry("@foreground", "#D6E8DC"),
      Map.entry("@componentBackground", "#26362C"),
      Map.entry("@buttonBackground", "#2D3F33"),
      Map.entry("@menuBackground", "#19241D"),
      Map.entry("@accentColor", "#4FA36C"),
      Map.entry("@accentBaseColor", "#4FA36C"),
      Map.entry("@accentBase2Color", "#6FBD89"),
      Map.entry("Component.focusColor", "#6FBD89"),
      Map.entry("Component.linkColor", "#7CCB97"),
      Map.entry("TextComponent.selectionBackground", "#2F6A44"),
      Map.entry("TextComponent.selectionForeground", "#F3FAF6"),
      Map.entry("List.selectionBackground", "#2F6A44"),
      Map.entry("Table.selectionBackground", "#2F6A44"),
      Map.entry("Tree.selectionBackground", "#2F6A44")
  );

  private static final Map<String, String> MINT_LIGHT_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#F2FBF7"),
      Map.entry("@foreground", "#20443A"),
      Map.entry("@componentBackground", "#FCFFFD"),
      Map.entry("@buttonBackground", "#DDF3EA"),
      Map.entry("@menuBackground", "#E6F7F0"),
      Map.entry("@accentColor", "#2E8F76"),
      Map.entry("@accentBaseColor", "#2E8F76"),
      Map.entry("@accentBase2Color", "#42A48A"),
      Map.entry("Component.focusColor", "#42A48A"),
      Map.entry("Component.linkColor", "#1F7D66"),
      Map.entry("TextComponent.selectionBackground", "#BDE8D9"),
      Map.entry("TextComponent.selectionForeground", "#10352C"),
      Map.entry("List.selectionBackground", "#BDE8D9"),
      Map.entry("Table.selectionBackground", "#BDE8D9"),
      Map.entry("Tree.selectionBackground", "#BDE8D9")
  );

  private static final Map<String, String> RUBY_NIGHT_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#241E22"),
      Map.entry("@foreground", "#EBD8DF"),
      Map.entry("@componentBackground", "#2E252A"),
      Map.entry("@buttonBackground", "#3A2C33"),
      Map.entry("@menuBackground", "#1E181B"),
      Map.entry("@accentColor", "#C74B67"),
      Map.entry("@accentBaseColor", "#C74B67"),
      Map.entry("@accentBase2Color", "#D96883"),
      Map.entry("Component.focusColor", "#D96883"),
      Map.entry("Component.linkColor", "#E07E97"),
      Map.entry("TextComponent.selectionBackground", "#7C2F44"),
      Map.entry("TextComponent.selectionForeground", "#FFF1F5"),
      Map.entry("List.selectionBackground", "#7C2F44"),
      Map.entry("Table.selectionBackground", "#7C2F44"),
      Map.entry("Tree.selectionBackground", "#7C2F44")
  );

  private static final Map<String, String> ARCTIC_LIGHT_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#F7FAFF"),
      Map.entry("@foreground", "#2A3A4E"),
      Map.entry("@componentBackground", "#FCFEFF"),
      Map.entry("@buttonBackground", "#E8F0FF"),
      Map.entry("@menuBackground", "#EDF3FF"),
      Map.entry("@accentColor", "#4B7BD8"),
      Map.entry("@accentBaseColor", "#4B7BD8"),
      Map.entry("@accentBase2Color", "#6A97EC"),
      Map.entry("Component.focusColor", "#6A97EC"),
      Map.entry("Component.linkColor", "#356BCF"),
      Map.entry("TextComponent.selectionBackground", "#CCE0FF"),
      Map.entry("TextComponent.selectionForeground", "#142843"),
      Map.entry("List.selectionBackground", "#CCE0FF"),
      Map.entry("Table.selectionBackground", "#CCE0FF"),
      Map.entry("Tree.selectionBackground", "#CCE0FF")
  );

  private static final Map<String, String> GRAPHITE_MONO_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#252525"),
      Map.entry("@foreground", "#E4E4E4"),
      Map.entry("@componentBackground", "#2E2E2E"),
      Map.entry("@buttonBackground", "#373737"),
      Map.entry("@menuBackground", "#1F1F1F"),
      Map.entry("@accentColor", "#9FA3A8"),
      Map.entry("@accentBaseColor", "#9FA3A8"),
      Map.entry("@accentBase2Color", "#B6BABF"),
      Map.entry("Component.focusColor", "#B6BABF"),
      Map.entry("Component.linkColor", "#C2C7CC"),
      Map.entry("TextComponent.selectionBackground", "#525252"),
      Map.entry("TextComponent.selectionForeground", "#F8F8F8"),
      Map.entry("List.selectionBackground", "#525252"),
      Map.entry("Table.selectionBackground", "#525252"),
      Map.entry("Tree.selectionBackground", "#525252")
  );

  private static final Map<String, String> TEAL_DEEP_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#1A2A2C"),
      Map.entry("@foreground", "#D5E7E6"),
      Map.entry("@componentBackground", "#213437"),
      Map.entry("@buttonBackground", "#284045"),
      Map.entry("@menuBackground", "#152124"),
      Map.entry("@accentColor", "#2FA7A0"),
      Map.entry("@accentBaseColor", "#2FA7A0"),
      Map.entry("@accentBase2Color", "#4DBCB5"),
      Map.entry("Component.focusColor", "#4DBCB5"),
      Map.entry("Component.linkColor", "#5CC9C3"),
      Map.entry("TextComponent.selectionBackground", "#216E6A"),
      Map.entry("TextComponent.selectionForeground", "#F1FCFB"),
      Map.entry("List.selectionBackground", "#216E6A"),
      Map.entry("Table.selectionBackground", "#216E6A"),
      Map.entry("Tree.selectionBackground", "#216E6A")
  );

  private static final Map<String, String> SUNSET_DARK_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#2A2124"),
      Map.entry("@foreground", "#F2DFD8"),
      Map.entry("@componentBackground", "#34292D"),
      Map.entry("@buttonBackground", "#402F35"),
      Map.entry("@menuBackground", "#231B1E"),
      Map.entry("@accentColor", "#E28743"),
      Map.entry("@accentBaseColor", "#E28743"),
      Map.entry("@accentBase2Color", "#C76A56"),
      Map.entry("Component.focusColor", "#C76A56"),
      Map.entry("Component.linkColor", "#F2A367"),
      Map.entry("TextComponent.selectionBackground", "#7B3B45"),
      Map.entry("TextComponent.selectionForeground", "#FFF3EE"),
      Map.entry("List.selectionBackground", "#7B3B45"),
      Map.entry("Table.selectionBackground", "#7B3B45"),
      Map.entry("Tree.selectionBackground", "#7B3B45")
  );

  private static final Map<String, String> TERMINAL_AMBER_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#151515"),
      Map.entry("@foreground", "#F2C98A"),
      Map.entry("@componentBackground", "#1D1D1D"),
      Map.entry("@buttonBackground", "#262626"),
      Map.entry("@menuBackground", "#101010"),
      Map.entry("@accentColor", "#E0A84A"),
      Map.entry("@accentBaseColor", "#E0A84A"),
      Map.entry("@accentBase2Color", "#F2BF68"),
      Map.entry("Component.focusColor", "#F2BF68"),
      Map.entry("Component.linkColor", "#FFC978"),
      Map.entry("TextComponent.selectionBackground", "#6F5121"),
      Map.entry("TextComponent.selectionForeground", "#FFF6E6"),
      Map.entry("List.selectionBackground", "#6F5121"),
      Map.entry("Table.selectionBackground", "#6F5121"),
      Map.entry("Tree.selectionBackground", "#6F5121")
  );

  private static final Map<String, String> HIGH_CONTRAST_DARK_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#101214"),
      Map.entry("@foreground", "#F5F7FA"),
      Map.entry("@componentBackground", "#171B1F"),
      Map.entry("@buttonBackground", "#1E242A"),
      Map.entry("@menuBackground", "#0C0F12"),
      Map.entry("@accentColor", "#5CA9FF"),
      Map.entry("@accentBaseColor", "#5CA9FF"),
      Map.entry("@accentBase2Color", "#85C1FF"),
      Map.entry("Component.focusColor", "#85C1FF"),
      Map.entry("Component.linkColor", "#8CC5FF"),
      Map.entry("TextComponent.selectionBackground", "#254A72"),
      Map.entry("TextComponent.selectionForeground", "#FFFFFF"),
      Map.entry("List.selectionBackground", "#254A72"),
      Map.entry("Table.selectionBackground", "#254A72"),
      Map.entry("Tree.selectionBackground", "#254A72")
  );

  private static final Map<String, String> CRT_GREEN_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#0B100B"),
      Map.entry("@foreground", "#9BF2A6"),
      Map.entry("@componentBackground", "#101710"),
      Map.entry("@buttonBackground", "#152015"),
      Map.entry("@menuBackground", "#090E09"),
      Map.entry("@accentColor", "#57D36E"),
      Map.entry("@accentBaseColor", "#42BF5C"),
      Map.entry("@accentBase2Color", "#7EEA92"),
      Map.entry("Component.focusColor", "#7EEA92"),
      Map.entry("Component.linkColor", "#8EF7A3"),
      Map.entry("TextComponent.selectionBackground", "#1F5C2A"),
      Map.entry("TextComponent.selectionForeground", "#E8FFE9"),
      Map.entry("List.selectionBackground", "#1F5C2A"),
      Map.entry("Table.selectionBackground", "#1F5C2A"),
      Map.entry("Tree.selectionBackground", "#1F5C2A")
  );

  private static final Map<String, String> CDE_BLUE_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#D5DCE9"),
      Map.entry("@foreground", "#13243A"),
      Map.entry("@componentBackground", "#E2E8F2"),
      Map.entry("@buttonBackground", "#C9D3E2"),
      Map.entry("@menuBackground", "#C5CFDF"),
      Map.entry("@accentColor", "#2D63A8"),
      Map.entry("@accentBaseColor", "#2D63A8"),
      Map.entry("@accentBase2Color", "#4F80BE"),
      Map.entry("Component.focusColor", "#4F80BE"),
      Map.entry("Component.linkColor", "#285A99"),
      Map.entry("TextComponent.selectionBackground", "#AFC3E5"),
      Map.entry("TextComponent.selectionForeground", "#0D1D33"),
      Map.entry("List.selectionBackground", "#AFC3E5"),
      Map.entry("Table.selectionBackground", "#AFC3E5"),
      Map.entry("Tree.selectionBackground", "#AFC3E5")
  );

  private static final Map<String, String> TOKYO_NIGHT_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#1A1B26"),
      Map.entry("@foreground", "#C0CAF5"),
      Map.entry("@componentBackground", "#202331"),
      Map.entry("@buttonBackground", "#2A2F45"),
      Map.entry("@menuBackground", "#171925"),
      Map.entry("@accentColor", "#7AA2F7"),
      Map.entry("@accentBaseColor", "#7AA2F7"),
      Map.entry("@accentBase2Color", "#9AB8FF"),
      Map.entry("Component.focusColor", "#9AB8FF"),
      Map.entry("Component.linkColor", "#A9C2FF"),
      Map.entry("TextComponent.selectionBackground", "#3A4B7A"),
      Map.entry("TextComponent.selectionForeground", "#F3F6FF"),
      Map.entry("List.selectionBackground", "#3A4B7A"),
      Map.entry("Table.selectionBackground", "#3A4B7A"),
      Map.entry("Tree.selectionBackground", "#3A4B7A")
  );

  private static final Map<String, String> CATPPUCCIN_MOCHA_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#1E1E2E"),
      Map.entry("@foreground", "#CDD6F4"),
      Map.entry("@componentBackground", "#24273A"),
      Map.entry("@buttonBackground", "#313244"),
      Map.entry("@menuBackground", "#181825"),
      Map.entry("@accentColor", "#89B4FA"),
      Map.entry("@accentBaseColor", "#89B4FA"),
      Map.entry("@accentBase2Color", "#B4BEFE"),
      Map.entry("Component.focusColor", "#B4BEFE"),
      Map.entry("Component.linkColor", "#A6C8FF"),
      Map.entry("TextComponent.selectionBackground", "#45475A"),
      Map.entry("TextComponent.selectionForeground", "#F5F7FF"),
      Map.entry("List.selectionBackground", "#45475A"),
      Map.entry("Table.selectionBackground", "#45475A"),
      Map.entry("Tree.selectionBackground", "#45475A")
  );

  private static final Map<String, String> GRUVBOX_DARK_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#282828"),
      Map.entry("@foreground", "#EBDBB2"),
      Map.entry("@componentBackground", "#32302F"),
      Map.entry("@buttonBackground", "#3C3836"),
      Map.entry("@menuBackground", "#1D2021"),
      Map.entry("@accentColor", "#D79921"),
      Map.entry("@accentBaseColor", "#D79921"),
      Map.entry("@accentBase2Color", "#FABD2F"),
      Map.entry("Component.focusColor", "#FABD2F"),
      Map.entry("Component.linkColor", "#FFD266"),
      Map.entry("TextComponent.selectionBackground", "#665C54"),
      Map.entry("TextComponent.selectionForeground", "#FBF1C7"),
      Map.entry("List.selectionBackground", "#665C54"),
      Map.entry("Table.selectionBackground", "#665C54"),
      Map.entry("Tree.selectionBackground", "#665C54")
  );

  private static final Map<String, String> GITHUB_SOFT_LIGHT_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#FFFFFF"),
      Map.entry("@foreground", "#24292F"),
      Map.entry("@componentBackground", "#F6F8FA"),
      Map.entry("@buttonBackground", "#EFF2F5"),
      Map.entry("@menuBackground", "#F3F5F7"),
      Map.entry("@accentColor", "#0969DA"),
      Map.entry("@accentBaseColor", "#0969DA"),
      Map.entry("@accentBase2Color", "#218BFF"),
      Map.entry("Component.focusColor", "#218BFF"),
      Map.entry("Component.linkColor", "#0550AE"),
      Map.entry("TextComponent.selectionBackground", "#DDF4FF"),
      Map.entry("TextComponent.selectionForeground", "#0A3069"),
      Map.entry("List.selectionBackground", "#DDF4FF"),
      Map.entry("Table.selectionBackground", "#DDF4FF"),
      Map.entry("Tree.selectionBackground", "#DDF4FF")
  );

  private static final Map<String, String> VIOLET_NEBULA_DEFAULTS = Map.ofEntries(
      Map.entry("@background", "#1B1629"),
      Map.entry("@foreground", "#E9E3FF"),
      Map.entry("@componentBackground", "#241D37"),
      Map.entry("@buttonBackground", "#2D2343"),
      Map.entry("@menuBackground", "#161126"),
      Map.entry("@accentColor", "#8A63F5"),
      Map.entry("@accentBaseColor", "#7A54EC"),
      Map.entry("@accentBase2Color", "#A07CFF"),
      Map.entry("Component.focusColor", "#B292FF"),
      Map.entry("Component.linkColor", "#C2A7FF"),
      Map.entry("TextComponent.selectionBackground", "#4A3688"),
      Map.entry("TextComponent.selectionForeground", "#F7F3FF"),
      Map.entry("List.selectionBackground", "#4A3688"),
      Map.entry("Table.selectionBackground", "#4A3688"),
      Map.entry("Tree.selectionBackground", "#4A3688")
  );

  private static final String NIMBUS_LAF_CLASS = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
  private static final String METAL_LAF_CLASS = "javax.swing.plaf.metal.MetalLookAndFeel";
  private static final String MOTIF_LAF_CLASS = "com.sun.java.swing.plaf.motif.MotifLookAndFeel";
  private static final String WINDOWS_LAF_CLASS = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
  private static final String GTK_LAF_CLASS = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";

  private static final LegacySystemThemeDefinition[] LEGACY_SYSTEM_THEME_DEFINITIONS =
      new LegacySystemThemeDefinition[] {
          new LegacySystemThemeDefinition("nimbus", "Nimbus", ThemeTone.LIGHT, NIMBUS_LAF_CLASS, true),
          new LegacySystemThemeDefinition("metal", "Metal", ThemeTone.LIGHT, METAL_LAF_CLASS, true),
          new LegacySystemThemeDefinition("motif", "Motif", ThemeTone.LIGHT, MOTIF_LAF_CLASS, true),
          new LegacySystemThemeDefinition("windows", "Windows Classic", ThemeTone.SYSTEM, WINDOWS_LAF_CLASS, false),
          new LegacySystemThemeDefinition("gtk", "GTK", ThemeTone.SYSTEM, GTK_LAF_CLASS, false)
      };

  private static final ThemeOption[] BASE_THEMES = new ThemeOption[] {
      new ThemeOption("system", "Native (System)", ThemeTone.SYSTEM, ThemePack.SYSTEM, true),

      // FlatLaf base themes
      new ThemeOption("dark", "Flat Dark", ThemeTone.DARK, ThemePack.FLATLAF, true),
      new ThemeOption("darcula", "Flat Darcula", ThemeTone.DARK, ThemePack.FLATLAF, true),
      new ThemeOption("light", "Flat Light", ThemeTone.LIGHT, ThemePack.FLATLAF, true),

      // Retro-styled custom variants
      new ThemeOption("crt-green", "CRT Green", ThemeTone.DARK, ThemePack.RETRO, false),
      new ThemeOption("cde-blue", "CDE Blue", ThemeTone.LIGHT, ThemePack.RETRO, false),

      // Modern curated custom variants
      new ThemeOption("tokyo-night", "Tokyo Night", ThemeTone.DARK, ThemePack.MODERN, true),
      new ThemeOption("catppuccin-mocha", "Catppuccin Mocha", ThemeTone.DARK, ThemePack.MODERN, false),
      new ThemeOption("gruvbox-dark", "Gruvbox Dark", ThemeTone.DARK, ThemePack.MODERN, false),
      new ThemeOption("github-soft-light", "GitHub Soft Light", ThemeTone.LIGHT, ThemePack.MODERN, true),

      // IRCafe curated variants
      new ThemeOption("blue-dark", "Flat Blue (Dark)", ThemeTone.DARK, ThemePack.IRCAFE, true),
      new ThemeOption("violet-nebula", "Violet Nebula", ThemeTone.DARK, ThemePack.IRCAFE, true),
      new ThemeOption("high-contrast-dark", "High Contrast Dark", ThemeTone.DARK, ThemePack.IRCAFE, true),
      new ThemeOption("graphite-mono", "Graphite Mono", ThemeTone.DARK, ThemePack.IRCAFE, false),
      new ThemeOption("forest-dark", "Forest Dark", ThemeTone.DARK, ThemePack.IRCAFE, false),
      new ThemeOption("ruby-night", "Ruby Night", ThemeTone.DARK, ThemePack.IRCAFE, false),
      new ThemeOption("solarized-dark", "Solarized Dark", ThemeTone.DARK, ThemePack.IRCAFE, false),
      new ThemeOption("sunset-dark", "Sunset Dark", ThemeTone.DARK, ThemePack.IRCAFE, false),
      new ThemeOption("terminal-amber", "Terminal Amber", ThemeTone.DARK, ThemePack.IRCAFE, false),
      new ThemeOption("teal-deep", "Teal Deep", ThemeTone.DARK, ThemePack.IRCAFE, false),
      new ThemeOption("orange", "Flat Orange (Dark)", ThemeTone.DARK, ThemePack.IRCAFE, false),

      new ThemeOption("nordic-light", "Nordic Light", ThemeTone.LIGHT, ThemePack.IRCAFE, true),
      new ThemeOption("blue-light", "Flat Blue (Light)", ThemeTone.LIGHT, ThemePack.IRCAFE, true),
      new ThemeOption("arctic-light", "Arctic Light", ThemeTone.LIGHT, ThemePack.IRCAFE, false),
      new ThemeOption("mint-light", "Mint Light", ThemeTone.LIGHT, ThemePack.IRCAFE, false),
      new ThemeOption("solarized-light", "Solarized Light", ThemeTone.LIGHT, ThemePack.IRCAFE, false)
  };

  private static List<ThemeOption> legacySystemThemes() {
    Set<String> installed = installedLookAndFeelClassNames();
    if (installed.isEmpty()) return List.of();

    List<ThemeOption> out = new ArrayList<>();
    for (LegacySystemThemeDefinition def : LEGACY_SYSTEM_THEME_DEFINITIONS) {
      if (def == null || def.lafClassName() == null || def.lafClassName().isBlank()) continue;
      if (!installed.contains(def.lafClassName().toLowerCase(Locale.ROOT))) continue;
      out.add(new ThemeOption(def.id(), def.label(), def.tone(), ThemePack.SYSTEM, def.featured()));
    }
    return out;
  }

  private static Set<String> installedLookAndFeelClassNames() {
    Set<String> out = new HashSet<>();
    try {
      UIManager.LookAndFeelInfo[] infos = UIManager.getInstalledLookAndFeels();
      if (infos == null) return out;
      for (UIManager.LookAndFeelInfo info : infos) {
        if (info == null || info.getClassName() == null || info.getClassName().isBlank()) continue;
        out.add(info.getClassName().toLowerCase(Locale.ROOT));
      }
    } catch (Exception ignored) {
    }
    return out;
  }

  private static volatile ThemeOption[] CACHED_THEMES;
  private static volatile ThemeOption[] CACHED_THEMES_WITH_ALL_INTELLIJ;

  private static ThemeOption[] allThemes() {
    ThemeOption[] cached = CACHED_THEMES;
    if (cached != null) return cached;

    List<ThemeOption> out = new ArrayList<>();
    Collections.addAll(out, BASE_THEMES);
    out.addAll(legacySystemThemes());

    // Keep it sane — only include a small curated subset from the IntelliJ Themes Pack.
    out.addAll(buildCuratedIntelliJThemes());

    cached = out.toArray(ThemeOption[]::new);
    CACHED_THEMES = cached;
    return cached;
  }

  /**
   * Themes for the searchable Theme Selector dialog.
   *
   * <p>When {@code includeAllIntelliJThemes} is true, this expands to include the entire IntelliJ
   * Themes Pack list (potentially hundreds of themes). We keep {@link #supportedThemes()} curated
   * so that menus and dropdowns stay compact.
   */
  public ThemeOption[] themesForPicker(boolean includeAllIntelliJThemes) {
    if (!includeAllIntelliJThemes) {
      return supportedThemes();
    }

    ThemeOption[] cached = CACHED_THEMES_WITH_ALL_INTELLIJ;
    if (cached != null) return cached.clone();

    List<ThemeOption> out = new ArrayList<>();
    Collections.addAll(out, BASE_THEMES);
    out.addAll(legacySystemThemes());

    // Include all IntelliJ themes (not curated) for the picker.
    List<IntelliJThemePack.PackTheme> pack = IntelliJThemePack.listThemes();
    if (!pack.isEmpty()) {
      Set<String> seen = new HashSet<>();
      // Seed with existing ids so we don't accidentally duplicate.
      for (ThemeOption o : out) {
        if (o != null && o.id() != null) seen.add(o.id());
      }

      for (IntelliJThemePack.PackTheme t : pack) {
        if (t == null || t.id() == null || t.id().isBlank()) continue;
        if (!seen.add(t.id())) continue;
        ThemeTone tone = t.dark() ? ThemeTone.DARK : ThemeTone.LIGHT;
        out.add(new ThemeOption(
            t.id(),
            "IntelliJ: " + t.label(),
            tone,
            ThemePack.INTELLIJ,
            false));
      }
    }

    cached = out.toArray(ThemeOption[]::new);
    CACHED_THEMES_WITH_ALL_INTELLIJ = cached;
    return cached.clone();
  }

  private static List<ThemeOption> buildCuratedIntelliJThemes() {
    List<IntelliJThemePack.PackTheme> pack = IntelliJThemePack.listThemes();
    if (pack.isEmpty()) return List.of();

    // Prioritized picks by name fragments (case-insensitive). We pick the first match for each.
    // If a fragment does not exist in the installed pack version, it is skipped.
    String[] priority = new String[] {
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

    // Hard cap so we don't flood menus/dropdowns with hundreds of themes.
    // (The Theme Selector dialog can optionally show the full IntelliJ Themes Pack list.)
    final int MAX = 16;

    Set<String> chosenIds = new HashSet<>();
    List<ThemeOption> curated = new ArrayList<>();

    java.util.function.Consumer<IntelliJThemePack.PackTheme> add = t -> {
      if (t == null) return;
      if (!chosenIds.add(t.id())) return;
      ThemeTone tone = t.dark() ? ThemeTone.DARK : ThemeTone.LIGHT;

      // Only a few get featured to keep the Settings → Theme menu from exploding.
      boolean featured = curated.size() < 3;

      curated.add(new ThemeOption(
          t.id(),
          "IntelliJ: " + t.label(),
          tone,
          ThemePack.INTELLIJ,
          featured));
    };

    for (String frag : priority) {
      if (curated.size() >= MAX) break;
      String f = frag.toLowerCase(Locale.ROOT);

      for (IntelliJThemePack.PackTheme t : pack) {
        if (t == null) continue;
        String name = t.label() != null ? t.label().toLowerCase(Locale.ROOT) : "";
        String cn = t.lafClassName() != null ? t.lafClassName().toLowerCase(Locale.ROOT) : "";
        if (name.contains(f) || cn.contains(f.replace(" ", ""))) {
          add.accept(t);
          break;
        }
      }
    }

    // If we didn't find enough, fill with a few additional dark themes (then light) so users still get variety.
    if (curated.size() < MAX) {
      for (IntelliJThemePack.PackTheme t : pack) {
        if (curated.size() >= MAX) break;
        if (t != null && t.dark()) add.accept(t);
      }
      for (IntelliJThemePack.PackTheme t : pack) {
        if (curated.size() >= MAX) break;
        if (t != null && !t.dark()) add.accept(t);
      }
    }

    return curated;
  }

  private final ChatStyles chatStyles;
  private final ChatTranscriptStore transcripts;
  private final UiSettingsBus settingsBus;
  private final ThemeAccentSettingsBus accentSettingsBus;
  private final ThemeTweakSettingsBus tweakSettingsBus;

  public ThemeManager(ChatStyles chatStyles, ChatTranscriptStore transcripts, UiSettingsBus settingsBus, ThemeAccentSettingsBus accentSettingsBus, ThemeTweakSettingsBus tweakSettingsBus) {
    this.chatStyles = chatStyles;
    this.transcripts = transcripts;
    this.settingsBus = settingsBus;
    this.accentSettingsBus = accentSettingsBus;
    this.tweakSettingsBus = tweakSettingsBus;
  }

  public ThemeOption[] supportedThemes() {
    return allThemes().clone();
  }

  public ThemeOption[] featuredThemes() {
    return Arrays.stream(allThemes())
        .filter(ThemeOption::featured)
        .toArray(ThemeOption[]::new);
  }

  public void installLookAndFeel(String themeId) {
    runOnEdt(() -> {
      setLookAndFeel(themeId);
      applyCommonTweaks(tweakSettingsBus != null ? tweakSettingsBus.get() : null);
      applyAccentOverrides(accentSettingsBus != null ? accentSettingsBus.get() : null);

      // Ensure chat styles pick up the correct UI defaults for the chosen LAF.
      chatStyles.reload();
    });
  }

  public void applyTheme(String themeId) {
    runOnEdt(() -> {
      boolean snap = false;
      try {
        // Only animate if there is at least one showing window.
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

      try {
        setLookAndFeel(themeId);
        applyCommonTweaks(tweakSettingsBus != null ? tweakSettingsBus.get() : null);
        applyAccentOverrides(accentSettingsBus != null ? accentSettingsBus.get() : null);

        // FlatLaf can update all windows; we also run a componentTreeUI update for safety.
        try {
          FlatLaf.updateUI();
        } catch (Exception ignored) {
        }

        for (Window w : Window.getWindows()) {
          try {
            SwingUtilities.updateComponentTreeUI(w);
            w.invalidate();
            w.repaint();
          } catch (Exception ignored) {
          }
        }

        // Re-apply any explicit fonts after LAF update.
        try {
          settingsBus.refresh();
        } catch (Exception ignored) {
        }

        // Recompute chat attribute sets from new UI defaults and re-style existing docs.
        try {
          chatStyles.reload();
        } catch (Exception ignored) {
        }
        try {
          transcripts.restyleAllDocumentsCoalesced();
        } catch (Exception ignored) {
        }
      } finally {
        if (snap) {
          try {
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
          } catch (Exception ignored) {
          }
        }
      }
    });
  }

  /**
   * Apply accent/tweak UI defaults without changing the current Look & Feel.
   * Used for live preview (e.g. accent slider/color) to avoid the heavier LAF reset.
   */
  public void applyAppearance(boolean animate) {
    runOnEdt(() -> {
      boolean snap = false;

      if (animate) {
        try {
          // Only animate if there is at least one showing window.
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
        applyCommonTweaks(tweakSettingsBus != null ? tweakSettingsBus.get() : null);
        applyAccentOverrides(accentSettingsBus != null ? accentSettingsBus.get() : null);
        SvgIcons.clearCache();

        try {
          FlatLaf.updateUI();
        } catch (Exception ignored) {
        }

        for (Window w : Window.getWindows()) {
          try {
            SwingUtilities.updateComponentTreeUI(w);
            w.invalidate();
            w.repaint();
          } catch (Exception ignored) {
          }
        }

        // Re-apply any explicit fonts after UI defaults update.
        try {
          settingsBus.refresh();
        } catch (Exception ignored) {
        }

        // Recompute chat attribute sets from new UI defaults and re-style existing docs.
        try {
          chatStyles.reload();
        } catch (Exception ignored) {
        }
        try {
          transcripts.restyleAllDocumentsCoalesced();
        } catch (Exception ignored) {
        }
      } finally {
        if (snap) {
          try {
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
          } catch (Exception ignored) {
          }
        }
      }
    });
  }

  public void applyAppearance() {
    applyAppearance(true);
  }


  public void refreshChatStyles() {
    runOnEdt(() -> {
      try {
        chatStyles.reload();
      } catch (Exception ignored) {
      }
      try {
        transcripts.restyleAllDocumentsCoalesced();
      } catch (Exception ignored) {
      }
    });
  }


  private void setLookAndFeel(String themeId) {
    String raw = themeId != null ? themeId.trim() : "";
    // Default to Darcula (our preferred "A" default) when no theme is configured.
    if (raw.isEmpty()) raw = "darcula";

    String lower = raw.toLowerCase(Locale.ROOT);

    // Allow advanced users to set an IntelliJ themes-pack LAF directly via config:
    //   ircafe.ui.theme: "ij:com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme"
    // or any other LookAndFeel class name on the classpath.
    if (lower.startsWith(IntelliJThemePack.ID_PREFIX)) {
      String className = raw.substring(raw.indexOf(':') + 1).trim();
      if (trySetLookAndFeelByClassName(className)) return;
    } else if (looksLikeClassName(raw)) {
      if (trySetLookAndFeelByClassName(raw)) return;
    }

    try {
      switch (lower) {
        case "system" -> UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        case "nimbus" -> applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
        case "metal" -> applyLegacySystemLookAndFeelOrFallback(METAL_LAF_CLASS);
        case "motif" -> applyLegacySystemLookAndFeelOrFallback(MOTIF_LAF_CLASS);
        case "windows" -> applyLegacySystemLookAndFeelOrFallback(WINDOWS_LAF_CLASS);
        case "gtk" -> applyLegacySystemLookAndFeelOrFallback(GTK_LAF_CLASS);
        case "light" -> UIManager.setLookAndFeel(new FlatLightLaf());
        case "darcula" -> UIManager.setLookAndFeel(new FlatDarculaLaf());
        case "crt-green" -> {
          FlatDarkLaf crtGreen = new FlatDarkLaf();
          crtGreen.setExtraDefaults(CRT_GREEN_DEFAULTS);
          UIManager.setLookAndFeel(crtGreen);
        }
        case "cde-blue" -> {
          FlatLightLaf cdeBlue = new FlatLightLaf();
          cdeBlue.setExtraDefaults(CDE_BLUE_DEFAULTS);
          UIManager.setLookAndFeel(cdeBlue);
        }
        case "tokyo-night" -> {
          FlatDarkLaf tokyoNight = new FlatDarkLaf();
          tokyoNight.setExtraDefaults(TOKYO_NIGHT_DEFAULTS);
          UIManager.setLookAndFeel(tokyoNight);
        }
        case "catppuccin-mocha" -> {
          FlatDarkLaf catppuccinMocha = new FlatDarkLaf();
          catppuccinMocha.setExtraDefaults(CATPPUCCIN_MOCHA_DEFAULTS);
          UIManager.setLookAndFeel(catppuccinMocha);
        }
        case "gruvbox-dark" -> {
          FlatDarkLaf gruvboxDark = new FlatDarkLaf();
          gruvboxDark.setExtraDefaults(GRUVBOX_DARK_DEFAULTS);
          UIManager.setLookAndFeel(gruvboxDark);
        }
        case "github-soft-light" -> {
          FlatLightLaf githubSoftLight = new FlatLightLaf();
          githubSoftLight.setExtraDefaults(GITHUB_SOFT_LIGHT_DEFAULTS);
          UIManager.setLookAndFeel(githubSoftLight);
        }
        case "blue-dark" -> {
          FlatDarkLaf blueDark = new FlatDarkLaf();
          blueDark.setExtraDefaults(BLUE_DARK_DEFAULTS);
          UIManager.setLookAndFeel(blueDark);
        }
        case "graphite-mono" -> {
          FlatDarkLaf graphiteMono = new FlatDarkLaf();
          graphiteMono.setExtraDefaults(GRAPHITE_MONO_DEFAULTS);
          UIManager.setLookAndFeel(graphiteMono);
        }
        case "forest-dark" -> {
          FlatDarkLaf forestDark = new FlatDarkLaf();
          forestDark.setExtraDefaults(FOREST_DARK_DEFAULTS);
          UIManager.setLookAndFeel(forestDark);
        }
        case "high-contrast-dark" -> {
          FlatDarkLaf highContrastDark = new FlatDarkLaf();
          highContrastDark.setExtraDefaults(HIGH_CONTRAST_DARK_DEFAULTS);
          UIManager.setLookAndFeel(highContrastDark);
        }
        case "ruby-night" -> {
          FlatDarkLaf rubyNight = new FlatDarkLaf();
          rubyNight.setExtraDefaults(RUBY_NIGHT_DEFAULTS);
          UIManager.setLookAndFeel(rubyNight);
        }
        case "violet-nebula" -> {
          FlatDarkLaf violetNebula = new FlatDarkLaf();
          violetNebula.setExtraDefaults(VIOLET_NEBULA_DEFAULTS);
          UIManager.setLookAndFeel(violetNebula);
        }
        case "solarized-dark" -> {
          FlatDarkLaf solarizedDark = new FlatDarkLaf();
          solarizedDark.setExtraDefaults(SOLARIZED_DARK_DEFAULTS);
          UIManager.setLookAndFeel(solarizedDark);
        }
        case "sunset-dark" -> {
          FlatDarkLaf sunsetDark = new FlatDarkLaf();
          sunsetDark.setExtraDefaults(SUNSET_DARK_DEFAULTS);
          UIManager.setLookAndFeel(sunsetDark);
        }
        case "terminal-amber" -> {
          FlatDarkLaf terminalAmber = new FlatDarkLaf();
          terminalAmber.setExtraDefaults(TERMINAL_AMBER_DEFAULTS);
          UIManager.setLookAndFeel(terminalAmber);
        }
        case "teal-deep" -> {
          FlatDarkLaf tealDeep = new FlatDarkLaf();
          tealDeep.setExtraDefaults(TEAL_DEEP_DEFAULTS);
          UIManager.setLookAndFeel(tealDeep);
        }
        case "orange" -> {
          FlatDarkLaf orange = new FlatDarkLaf();
          orange.setExtraDefaults(ORANGE_DARK_DEFAULTS);
          UIManager.setLookAndFeel(orange);
        }
        case "arctic-light" -> {
          FlatLightLaf arcticLight = new FlatLightLaf();
          arcticLight.setExtraDefaults(ARCTIC_LIGHT_DEFAULTS);
          UIManager.setLookAndFeel(arcticLight);
        }
        case "blue-light" -> {
          FlatLightLaf blueLight = new FlatLightLaf();
          blueLight.setExtraDefaults(BLUE_LIGHT_DEFAULTS);
          UIManager.setLookAndFeel(blueLight);
        }
        case "mint-light" -> {
          FlatLightLaf mintLight = new FlatLightLaf();
          mintLight.setExtraDefaults(MINT_LIGHT_DEFAULTS);
          UIManager.setLookAndFeel(mintLight);
        }
        case "nordic-light" -> {
          FlatLightLaf nordicLight = new FlatLightLaf();
          nordicLight.setExtraDefaults(NORDIC_LIGHT_DEFAULTS);
          UIManager.setLookAndFeel(nordicLight);
        }
        case "solarized-light" -> {
          FlatLightLaf solarizedLight = new FlatLightLaf();
          solarizedLight.setExtraDefaults(SOLARIZED_LIGHT_DEFAULTS);
          UIManager.setLookAndFeel(solarizedLight);
        }
        // Fail soft to Darcula instead of FlatDark so bad/unknown ids still look polished.
        default -> UIManager.setLookAndFeel(new FlatDarculaLaf());
      }
    } catch (Exception e) {
      // Fail soft; keep existing LAF.
      log.warn("[ircafe] Could not set Look & Feel '{}'", raw, e);
    }
  }

  private void applyLegacySystemLookAndFeelOrFallback(String className) throws Exception {
    if (className == null || className.isBlank() || !isLookAndFeelInstalled(className)
        || !trySetLookAndFeelByClassName(className)) {
      UIManager.setLookAndFeel(new FlatDarculaLaf());
    }
  }

  private static boolean isLookAndFeelInstalled(String className) {
    if (className == null || className.isBlank()) return false;
    return installedLookAndFeelClassNames().contains(className.toLowerCase(Locale.ROOT));
  }

  private boolean trySetLookAndFeelByClassName(String className) {
    try {
      return IntelliJThemePack.install(className);
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean looksLikeClassName(String raw) {
    if (raw == null) return false;
    String s = raw.trim();
    if (!s.contains(".")) return false;

    // Heuristic: allow common package prefixes or any segment starting with uppercase.
    if (s.startsWith("com.") || s.startsWith("org.") || s.startsWith("net.") || s.startsWith("io.")) return true;

    String last = s.substring(s.lastIndexOf('.') + 1);
    return !last.isBlank() && Character.isUpperCase(last.charAt(0));
  }

  private void applyCommonTweaks(ThemeTweakSettings tweaks) {
    ThemeTweakSettings t = tweaks != null ? tweaks : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.COZY, 10);

    // Rounded corners and a softer modern vibe.
    int arc = t.cornerRadius();
    UIManager.put("Component.arc", arc);
    UIManager.put("Button.arc", arc);
    UIManager.put("TextComponent.arc", arc);
    UIManager.put("ProgressBar.arc", arc);
    UIManager.put("ScrollPane.arc", arc);

    // Density presets (padding/row-height). Keep it simple and global.
    ThemeTweakSettings.ThemeDensity d = t.density();
    int rowHeight = switch (d) {
      case COMPACT -> 20;
      case SPACIOUS -> 28;
      default -> 24;
    };

    UIManager.put("Tree.rowHeight", rowHeight);
    UIManager.put("Table.rowHeight", rowHeight);
    UIManager.put("List.cellHeight", rowHeight);

    Insets buttonMargin = switch (d) {
      case COMPACT -> new Insets(4, 10, 4, 10);
      case SPACIOUS -> new Insets(8, 14, 8, 14);
      default -> new Insets(6, 12, 6, 12);
    };

    Insets textMargin = switch (d) {
      case COMPACT -> new Insets(4, 6, 4, 6);
      case SPACIOUS -> new Insets(8, 10, 8, 10);
      default -> new Insets(6, 8, 6, 8);
    };

    UIManager.put("Button.margin", buttonMargin);
    UIManager.put("ToggleButton.margin", buttonMargin);
    UIManager.put("RadioButton.margin", buttonMargin);
    UIManager.put("CheckBox.margin", buttonMargin);

    UIManager.put("TextComponent.margin", textMargin);
    UIManager.put("TextField.margin", textMargin);
    UIManager.put("PasswordField.margin", textMargin);
    UIManager.put("TextArea.margin", textMargin);

    // FlatLaf supports a few "padding" defaults. Unknown keys are ignored.
    UIManager.put("ComboBox.padding", textMargin);
  }



  private void applyAccentOverrides(ThemeAccentSettings accent) {
    if (accent == null || !accent.enabled()) return;

    Color chosen = parseHexColor(accent.accentColor());
    if (chosen == null) return;

    Color themeAccent = UIManager.getColor("@accentColor");
    if (themeAccent == null) themeAccent = UIManager.getColor("Component.focusColor");
    if (themeAccent == null) themeAccent = new Color(0x2D, 0x6B, 0xFF);

    double s = Math.max(0, Math.min(100, accent.strength())) / 100.0;
    Color blended = mix(themeAccent, chosen, s);

    boolean dark = isDark(UIManager.getColor("Panel.background"));
    Color focus = dark ? lighten(blended, 0.20) : darken(blended, 0.10);
    Color link = dark ? lighten(blended, 0.28) : darken(blended, 0.12);

    // Core FlatLaf accent keys (accept Color values).
    UIManager.put("@accentColor", blended);
    UIManager.put("@accentBaseColor", blended);
    UIManager.put("@accentBase2Color", focus);

    // A few Swing defaults that "feel" like the accent.
    UIManager.put("Component.focusColor", focus);
    UIManager.put("Component.linkColor", link);

    // Selection colors: blend accent into the existing background.
    Color bg = UIManager.getColor("TextComponent.background");
    if (bg == null) bg = UIManager.getColor("Panel.background");
    if (bg == null) bg = UIManager.getColor("control");
    if (bg == null) bg = dark ? Color.DARK_GRAY : Color.LIGHT_GRAY;

    double selMix = dark ? 0.55 : 0.35;
    Color selectionBg = mix(bg, blended, selMix);
    Color selectionFg = bestTextColor(selectionBg);

    UIManager.put("TextComponent.selectionBackground", selectionBg);
    UIManager.put("TextComponent.selectionForeground", selectionFg);
    UIManager.put("List.selectionBackground", selectionBg);
    UIManager.put("List.selectionForeground", selectionFg);
    UIManager.put("Table.selectionBackground", selectionBg);
    UIManager.put("Table.selectionForeground", selectionFg);
    UIManager.put("Tree.selectionBackground", selectionBg);
    UIManager.put("Tree.selectionForeground", selectionFg);
  }

  private static Color parseHexColor(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return null;

    if (s.startsWith("#")) s = s.substring(1);
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);

    if (s.length() == 3) {
      char r = s.charAt(0);
      char g = s.charAt(1);
      char b = s.charAt(2);
      s = "" + r + r + g + g + b + b;
    }

    if (s.length() != 6) return null;

    try {
      int rgb = Integer.parseInt(s, 16);
      return new Color(rgb);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Color mix(Color a, Color b, double t) {
    if (a == null) return b;
    if (b == null) return a;
    double tt = Math.max(0, Math.min(1, t));
    int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * tt);
    int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * tt);
    int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * tt);
    return new Color(clamp255(r), clamp255(g), clamp255(bl));
  }

  private static Color lighten(Color c, double amount) {
    return mix(c, Color.WHITE, amount);
  }

  private static Color darken(Color c, double amount) {
    return mix(c, Color.BLACK, amount);
  }

  private static int clamp255(int v) {
    return Math.max(0, Math.min(255, v));
  }

  private static boolean isDark(Color c) {
    if (c == null) return true;
    double lum = relativeLuminance(c);
    return lum < 0.45;
  }

  private static double relativeLuminance(Color c) {
    // sRGB relative luminance
    double r = srgbToLinear(c.getRed() / 255.0);
    double g = srgbToLinear(c.getGreen() / 255.0);
    double b = srgbToLinear(c.getBlue() / 255.0);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  private static double srgbToLinear(double v) {
    return (v <= 0.04045) ? (v / 12.92) : Math.pow((v + 0.055) / 1.055, 2.4);
  }

  private static Color bestTextColor(Color bg) {
    if (bg == null) return Color.WHITE;
    return relativeLuminance(bg) > 0.55 ? Color.BLACK : Color.WHITE;
  }

  private static void runOnEdt(Runnable r) {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
    } else {
      SwingUtilities.invokeLater(r);
    }
  }
}

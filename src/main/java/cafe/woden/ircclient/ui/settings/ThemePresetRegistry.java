package cafe.woden.ircclient.ui.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
class ThemePresetRegistry {

  record ThemePreset(String id, boolean dark, Map<String, String> extraDefaults) {}

  private static final Map<String, String> ORANGE_DARK_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#A65414"));

  private static final Map<String, String> BLUE_DARK_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#2F5F9E"));

  private static final Map<String, String> BLUE_LIGHT_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#B8D6FF"));

  private static final Map<String, String> NORDIC_LIGHT_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#C9DAEE"));

  private static final Map<String, String> SOLARIZED_DARK_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#0A4A5C"));

  private static final Map<String, String> SOLARIZED_LIGHT_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#D9ECFF"));

  private static final Map<String, String> FOREST_DARK_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#2F6A44"));

  private static final Map<String, String> MINT_LIGHT_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#BDE8D9"));

  private static final Map<String, String> RUBY_NIGHT_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#7C2F44"));

  private static final Map<String, String> ARCTIC_LIGHT_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#CCE0FF"));

  private static final Map<String, String> GRAPHITE_MONO_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#525252"));

  private static final Map<String, String> TEAL_DEEP_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#216E6A"));

  private static final Map<String, String> SUNSET_DARK_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#7B3B45"));

  private static final Map<String, String> TERMINAL_AMBER_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#6F5121"));

  private static final Map<String, String> HIGH_CONTRAST_DARK_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#254A72"));

  private static final Map<String, String> CRT_GREEN_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#1F5C2A"));

  private static final Map<String, String> CDE_BLUE_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("List.selectionForeground", "#0D1D33"),
          Map.entry("Table.selectionBackground", "#AFC3E5"),
          Map.entry("Table.selectionForeground", "#0D1D33"),
          Map.entry("Tree.selectionBackground", "#AFC3E5"),
          Map.entry("Tree.selectionForeground", "#0D1D33"));

  private static final Map<String, String> TOKYO_NIGHT_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#3A4B7A"));

  private static final Map<String, String> CATPPUCCIN_MOCHA_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#45475A"));

  private static final Map<String, String> GRUVBOX_DARK_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#665C54"));

  private static final Map<String, String> GITHUB_SOFT_LIGHT_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#DDF4FF"));

  private static final Map<String, String> VIOLET_NEBULA_DEFAULTS =
      Map.ofEntries(
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
          Map.entry("Tree.selectionBackground", "#4A3688"));

  private static final Map<String, ThemePreset> PRESETS_BY_ID = buildPresetsById();

  ThemePreset byId(String id) {
    if (id == null || id.isBlank()) return null;
    return PRESETS_BY_ID.get(id.toLowerCase(java.util.Locale.ROOT));
  }

  private static Map<String, ThemePreset> buildPresetsById() {
    Map<String, ThemePreset> map = new LinkedHashMap<>();

    add(map, "crt-green", true, CRT_GREEN_DEFAULTS);
    add(map, "cde-blue", false, CDE_BLUE_DEFAULTS);

    add(map, "tokyo-night", true, TOKYO_NIGHT_DEFAULTS);
    add(map, "catppuccin-mocha", true, CATPPUCCIN_MOCHA_DEFAULTS);
    add(map, "gruvbox-dark", true, GRUVBOX_DARK_DEFAULTS);
    add(map, "github-soft-light", false, GITHUB_SOFT_LIGHT_DEFAULTS);

    add(map, "blue-dark", true, BLUE_DARK_DEFAULTS);
    add(map, "violet-nebula", true, VIOLET_NEBULA_DEFAULTS);
    add(map, "high-contrast-dark", true, HIGH_CONTRAST_DARK_DEFAULTS);
    add(map, "graphite-mono", true, GRAPHITE_MONO_DEFAULTS);
    add(map, "forest-dark", true, FOREST_DARK_DEFAULTS);
    add(map, "ruby-night", true, RUBY_NIGHT_DEFAULTS);
    add(map, "solarized-dark", true, SOLARIZED_DARK_DEFAULTS);
    add(map, "sunset-dark", true, SUNSET_DARK_DEFAULTS);
    add(map, "terminal-amber", true, TERMINAL_AMBER_DEFAULTS);
    add(map, "teal-deep", true, TEAL_DEEP_DEFAULTS);
    add(map, "orange", true, ORANGE_DARK_DEFAULTS);

    add(map, "nordic-light", false, NORDIC_LIGHT_DEFAULTS);
    add(map, "blue-light", false, BLUE_LIGHT_DEFAULTS);
    add(map, "arctic-light", false, ARCTIC_LIGHT_DEFAULTS);
    add(map, "mint-light", false, MINT_LIGHT_DEFAULTS);
    add(map, "solarized-light", false, SOLARIZED_LIGHT_DEFAULTS);

    return Map.copyOf(map);
  }

  private static void add(
      Map<String, ThemePreset> map, String id, boolean dark, Map<String, String> defaults) {
    map.put(id, new ThemePreset(id, dark, defaults));
  }
}

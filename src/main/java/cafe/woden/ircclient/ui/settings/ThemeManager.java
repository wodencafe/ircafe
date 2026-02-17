package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Window;
import java.util.Map;
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

  public record ThemeOption(String id, String label) {}

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

  private static final ThemeOption[] THEMES = new ThemeOption[] {
      new ThemeOption("system", "Native (System)"),
      new ThemeOption("dark", "Flat Dark"),
      new ThemeOption("darcula", "Flat Darcula"),
      new ThemeOption("blue-dark", "Flat Blue (Dark)"),
      new ThemeOption("graphite-mono", "Graphite Mono"),
      new ThemeOption("forest-dark", "Forest Dark"),
      new ThemeOption("high-contrast-dark", "High Contrast Dark"),
      new ThemeOption("ruby-night", "Ruby Night"),
      new ThemeOption("violet-nebula", "Violet Nebula"),
      new ThemeOption("solarized-dark", "Solarized Dark"),
      new ThemeOption("sunset-dark", "Sunset Dark"),
      new ThemeOption("terminal-amber", "Terminal Amber"),
      new ThemeOption("teal-deep", "Teal Deep"),
      new ThemeOption("orange", "Flat Orange (Dark)"),
      new ThemeOption("light", "Flat Light"),
      new ThemeOption("arctic-light", "Arctic Light"),
      new ThemeOption("blue-light", "Flat Blue (Light)"),
      new ThemeOption("mint-light", "Mint Light"),
      new ThemeOption("nordic-light", "Nordic Light"),
      new ThemeOption("solarized-light", "Solarized Light")
  };

  private final ChatStyles chatStyles;
  private final ChatTranscriptStore transcripts;
  private final UiSettingsBus settingsBus;

  public ThemeManager(ChatStyles chatStyles, ChatTranscriptStore transcripts, UiSettingsBus settingsBus) {
    this.chatStyles = chatStyles;
    this.transcripts = transcripts;
    this.settingsBus = settingsBus;
  }

  public ThemeOption[] supportedThemes() {
    return THEMES.clone();
  }

  public void installLookAndFeel(String themeId) {
    runOnEdt(() -> {
      setLookAndFeel(themeId);
      applyCommonTweaks();

      // Ensure chat styles pick up the correct UI defaults for the chosen LAF.
      chatStyles.reload();
    });
  }

  public void applyTheme(String themeId) {
    runOnEdt(() -> {
      setLookAndFeel(themeId);
      applyCommonTweaks();

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
      settingsBus.refresh();

      // Recompute chat attribute sets from new UI defaults and re-style existing docs.
      chatStyles.reload();
      transcripts.restyleAllDocuments();
    });
  }

  private void setLookAndFeel(String themeId) {
    String id = normalize(themeId);

    try {
      switch (id) {
        case "system" -> UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        case "light" -> UIManager.setLookAndFeel(new FlatLightLaf());
        case "darcula" -> UIManager.setLookAndFeel(new FlatDarculaLaf());
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
        default -> UIManager.setLookAndFeel(new FlatDarkLaf());
      }
    } catch (Exception e) {
      // Fail soft; keep existing LAF.
      log.warn("[ircafe] Could not set Look & Feel \'{}\'", id, e);
    }
  }

  private void applyCommonTweaks() {
    // Rounded corners and a softer modern vibe.
    UIManager.put("Component.arc", 10);
    UIManager.put("Button.arc", 10);
    UIManager.put("TextComponent.arc", 10);
  }

  private static String normalize(String s) {
    if (s == null) return "dark";
    return s.trim().toLowerCase();
  }

  private static void runOnEdt(Runnable r) {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
    } else {
      SwingUtilities.invokeLater(r);
    }
  }
}

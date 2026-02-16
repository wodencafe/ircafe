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

  private static final ThemeOption[] THEMES = new ThemeOption[] {
      new ThemeOption("system", "Native (System)"),
      new ThemeOption("dark", "Flat Dark"),
      new ThemeOption("darcula", "Flat Darcula"),
      new ThemeOption("blue-dark", "Flat Blue (Dark)"),
      new ThemeOption("orange", "Flat Orange (Dark)"),
      new ThemeOption("light", "Flat Light"),
      new ThemeOption("blue-light", "Flat Blue (Light)")
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
        case "orange" -> {
          FlatDarkLaf orange = new FlatDarkLaf();
          orange.setExtraDefaults(ORANGE_DARK_DEFAULTS);
          UIManager.setLookAndFeel(orange);
        }
        case "blue-light" -> {
          FlatLightLaf blueLight = new FlatLightLaf();
          blueLight.setExtraDefaults(BLUE_LIGHT_DEFAULTS);
          UIManager.setLookAndFeel(blueLight);
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

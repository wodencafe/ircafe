package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Window;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Applies and switches Look & Feel at runtime.
 */
@Component
@Lazy
public class ThemeManager {

  public record ThemeOption(String id, String label) {}

  private static final ThemeOption[] THEMES = new ThemeOption[] {
      new ThemeOption("dark", "Flat Dark"),
      new ThemeOption("darcula", "Flat Darcula"),
      new ThemeOption("light", "Flat Light")
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

  /**
   * Startup: install LAF before any UI beans are constructed.
   */
  public void installLookAndFeel(String themeId) {
    runOnEdt(() -> {
      setLookAndFeel(themeId);
      applyCommonTweaks();

      // Ensure chat styles pick up the correct UI defaults for the chosen LAF.
      chatStyles.reload();
    });
  }

  /**
   * Runtime: switch LAF + refresh all windows and restyle transcripts.
   */
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
    LookAndFeel laf = switch (id) {
      case "light" -> new FlatLightLaf();
      case "darcula" -> new FlatDarculaLaf();
      default -> new FlatDarkLaf();
    };

    try {
      UIManager.setLookAndFeel(laf);
    } catch (Exception e) {
      // Fail soft; keep existing LAF.
      System.err.println("[ircafe] Could not set Look & Feel '" + id + "': " + e);
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

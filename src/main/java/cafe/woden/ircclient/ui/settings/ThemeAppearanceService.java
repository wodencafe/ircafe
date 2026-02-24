package cafe.woden.ircclient.ui.settings;

import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
class ThemeAppearanceService {

  private static final String[] COMMON_TWEAK_OVERRIDE_KEYS = {
    "Component.arc",
    "Button.arc",
    "TextComponent.arc",
    "ProgressBar.arc",
    "ScrollPane.arc",
    "Tree.rowHeight",
    "Table.rowHeight",
    "List.cellHeight",
    "Button.margin",
    "ToggleButton.margin",
    "RadioButton.margin",
    "CheckBox.margin",
    "TextComponent.margin",
    "TextField.margin",
    "PasswordField.margin",
    "TextArea.margin",
    "ComboBox.padding"
  };

  private static final String[] ACCENT_OVERRIDE_KEYS = {
    "@accentColor",
    "@accentBaseColor",
    "@accentBase2Color",
    "Component.focusColor",
    "Component.linkColor",
    "TextComponent.selectionBackground",
    "TextComponent.selectionForeground",
    "List.selectionBackground",
    "List.selectionForeground",
    "Table.selectionBackground",
    "Table.selectionForeground",
    "Tree.selectionBackground",
    "Tree.selectionForeground"
  };

  private static final Object NULL_SENTINEL = new Object();
  private static final String[] UI_FONT_PRIORITY_KEYS = {
    "defaultFont",
    "Label.font",
    "Button.font",
    "Table.font",
    "TableHeader.font",
    "TextField.font",
    "TextArea.font",
    "CheckBox.font",
    "ComboBox.font",
    "Tree.font",
    "TabbedPane.font",
    "TitledBorder.font",
    "MenuBar.font",
    "Menu.font",
    "MenuItem.font",
    "CheckBoxMenuItem.font",
    "RadioButtonMenuItem.font",
    "PopupMenu.font",
    "MenuItem.acceleratorFont",
    "CheckBoxMenuItem.acceleratorFont",
    "RadioButtonMenuItem.acceleratorFont"
  };

  private final Map<String, Object> accentBaselineValues = new HashMap<>();
  private String accentBaselineLafClassName;
  private final Map<Object, Object> uiFontBaselineValues = new HashMap<>();
  private String uiFontBaselineLafClassName;

  void applyCommonTweaks(ThemeTweakSettings tweaks) {
    ThemeTweakSettings resolved =
        tweaks != null ? tweaks : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10);

    clearCommonTweakOverrides();
    applyUiFontOverrides(resolved);

    if (!isFlatLafActive()) {
      return;
    }

    int arc = resolved.cornerRadius();
    UIManager.put("Component.arc", arc);
    UIManager.put("Button.arc", arc);
    UIManager.put("TextComponent.arc", arc);
    UIManager.put("ProgressBar.arc", arc);
    UIManager.put("ScrollPane.arc", arc);

    ThemeTweakSettings.ThemeDensity density = resolved.density();
    if (density == ThemeTweakSettings.ThemeDensity.AUTO) {
      return;
    }

    int rowHeight =
        switch (density) {
          case COMPACT -> 20;
          case SPACIOUS -> 28;
          default -> 22;
        };

    UIManager.put("Tree.rowHeight", rowHeight);
    UIManager.put("Table.rowHeight", rowHeight);
    UIManager.put("List.cellHeight", rowHeight);

    Insets buttonMargin =
        switch (density) {
          case COMPACT -> new Insets(4, 10, 4, 10);
          case SPACIOUS -> new Insets(8, 14, 8, 14);
          default -> new Insets(5, 10, 5, 10);
        };

    Insets textMargin =
        switch (density) {
          case COMPACT -> new Insets(4, 6, 4, 6);
          case SPACIOUS -> new Insets(8, 10, 8, 10);
          default -> new Insets(5, 7, 5, 7);
        };

    UIManager.put("Button.margin", buttonMargin);
    UIManager.put("ToggleButton.margin", buttonMargin);
    UIManager.put("RadioButton.margin", buttonMargin);
    UIManager.put("CheckBox.margin", buttonMargin);

    UIManager.put("TextComponent.margin", textMargin);
    UIManager.put("TextField.margin", textMargin);
    UIManager.put("PasswordField.margin", textMargin);
    UIManager.put("TextArea.margin", textMargin);
    UIManager.put("ComboBox.padding", textMargin);
  }

  private void applyUiFontOverrides(ThemeTweakSettings tweaks) {
    restorePreviousUiFontOverridesIfCompatible();

    if (tweaks == null || !tweaks.uiFontOverrideEnabled()) return;

    captureUiFontBaseline();

    Font defaultFont = UIManager.getFont("defaultFont");
    if (defaultFont == null) defaultFont = UIManager.getFont("Label.font");
    if (defaultFont == null) {
      defaultFont =
          new Font(
              ThemeTweakSettings.DEFAULT_UI_FONT_FAMILY,
              Font.PLAIN,
              ThemeTweakSettings.DEFAULT_UI_FONT_SIZE);
    }

    float baseSize = Math.max(8f, defaultFont.getSize2D());
    float scale = tweaks.uiFontSize() / baseSize;

    for (Map.Entry<Object, Object> entry : uiFontBaselineValues.entrySet()) {
      Object key = entry.getKey();
      Object value = entry.getValue();
      if (!(value instanceof Font font)) continue;

      int scaledSize = Math.max(8, Math.round(font.getSize2D() * scale));
      Font replacement = new Font(tweaks.uiFontFamily(), font.getStyle(), scaledSize);
      UIManager.put(key, new FontUIResource(replacement));
    }

    UIManager.put(
        "defaultFont",
        new FontUIResource(new Font(tweaks.uiFontFamily(), Font.PLAIN, tweaks.uiFontSize())));
  }

  void applyAccentOverrides(ThemeAccentSettings accent) {
    restorePreviousAccentOverridesIfCompatible();

    if (accent == null || !accent.enabled()) return;

    Color chosen = ThemeColorUtils.parseHexColor(accent.accentColor());
    if (chosen == null) return;

    Color themeAccent = UIManager.getColor("@accentColor");
    if (themeAccent == null) themeAccent = UIManager.getColor("Component.focusColor");
    if (themeAccent == null) themeAccent = new Color(0x2D, 0x6B, 0xFF);

    double strength = Math.max(0, Math.min(100, accent.strength())) / 100.0;
    Color blended = ThemeColorUtils.mix(themeAccent, chosen, strength);

    Color panelBg = UIManager.getColor("Panel.background");
    if (panelBg == null) panelBg = UIManager.getColor("control");

    boolean dark = ThemeColorUtils.isDark(panelBg);
    Color focus =
        dark ? ThemeColorUtils.lighten(blended, 0.20) : ThemeColorUtils.darken(blended, 0.10);
    Color link =
        dark ? ThemeColorUtils.lighten(blended, 0.28) : ThemeColorUtils.darken(blended, 0.12);

    if (!isFlatLafActive() && panelBg != null) {
      focus = ThemeColorUtils.ensureContrastAgainstBackground(focus, panelBg, 1.25);
      link = ThemeColorUtils.ensureContrastAgainstBackground(link, panelBg, 1.25);
    }

    captureAccentBaseline();

    if (isFlatLafActive()) {
      UIManager.put("@accentColor", blended);
      UIManager.put("@accentBaseColor", blended);
      UIManager.put("@accentBase2Color", focus);
    }

    UIManager.put("Component.focusColor", focus);
    UIManager.put("Component.linkColor", link);

    Color bg = UIManager.getColor("TextComponent.background");
    if (bg == null) bg = UIManager.getColor("Panel.background");
    if (bg == null) bg = UIManager.getColor("control");
    if (bg == null) bg = dark ? Color.DARK_GRAY : Color.LIGHT_GRAY;

    double selMix = dark ? 0.55 : 0.35;
    Color selectionBg = ThemeColorUtils.mix(bg, blended, selMix);
    Color selectionFg = ThemeColorUtils.bestTextColor(selectionBg);

    UIManager.put("TextComponent.selectionBackground", selectionBg);
    UIManager.put("TextComponent.selectionForeground", selectionFg);
    UIManager.put("List.selectionBackground", selectionBg);
    UIManager.put("List.selectionForeground", selectionFg);
    UIManager.put("Table.selectionBackground", selectionBg);
    UIManager.put("Table.selectionForeground", selectionFg);
    UIManager.put("Tree.selectionBackground", selectionBg);
    UIManager.put("Tree.selectionForeground", selectionFg);
  }

  private void captureAccentBaseline() {
    accentBaselineValues.clear();
    for (String key : ACCENT_OVERRIDE_KEYS) {
      Object value = UIManager.get(key);
      accentBaselineValues.put(key, value != null ? value : NULL_SENTINEL);
    }
    accentBaselineLafClassName = ThemeLookAndFeelUtils.currentLookAndFeelClassName();
  }

  private void restorePreviousAccentOverridesIfCompatible() {
    if (accentBaselineValues.isEmpty()) return;

    String currentLafClassName = ThemeLookAndFeelUtils.currentLookAndFeelClassName();
    if (!Objects.equals(currentLafClassName, accentBaselineLafClassName)) {
      accentBaselineValues.clear();
      accentBaselineLafClassName = null;
      return;
    }

    for (Map.Entry<String, Object> entry : accentBaselineValues.entrySet()) {
      UIManager.put(entry.getKey(), entry.getValue() == NULL_SENTINEL ? null : entry.getValue());
    }

    accentBaselineValues.clear();
    accentBaselineLafClassName = null;
  }

  private void captureUiFontBaseline() {
    uiFontBaselineValues.clear();

    UIDefaults defaults = UIManager.getDefaults();
    for (Map.Entry<Object, Object> entry : defaults.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof Font) {
        uiFontBaselineValues.put(entry.getKey(), value);
      }
    }

    Object defaultFont = UIManager.get("defaultFont");
    if (!uiFontBaselineValues.containsKey("defaultFont")) {
      uiFontBaselineValues.put("defaultFont", defaultFont != null ? defaultFont : NULL_SENTINEL);
    }

    // Some LAFs expose menu fonts via LazyValue entries; resolve them explicitly.
    for (String key : UI_FONT_PRIORITY_KEYS) {
      if (key == null || key.isBlank()) continue;
      if (uiFontBaselineValues.containsKey(key)) continue;
      Font font = UIManager.getFont(key);
      if (font != null) {
        uiFontBaselineValues.put(key, font);
      }
    }

    uiFontBaselineLafClassName = ThemeLookAndFeelUtils.currentLookAndFeelClassName();
  }

  private void restorePreviousUiFontOverridesIfCompatible() {
    if (uiFontBaselineValues.isEmpty()) return;

    String currentLafClassName = ThemeLookAndFeelUtils.currentLookAndFeelClassName();
    if (!Objects.equals(currentLafClassName, uiFontBaselineLafClassName)) {
      uiFontBaselineValues.clear();
      uiFontBaselineLafClassName = null;
      return;
    }

    for (Map.Entry<Object, Object> entry : uiFontBaselineValues.entrySet()) {
      UIManager.put(entry.getKey(), entry.getValue() == NULL_SENTINEL ? null : entry.getValue());
    }

    uiFontBaselineValues.clear();
    uiFontBaselineLafClassName = null;
  }

  private static void clearCommonTweakOverrides() {
    for (String key : COMMON_TWEAK_OVERRIDE_KEYS) {
      UIManager.put(key, null);
    }
  }

  private static boolean isFlatLafActive() {
    return UIManager.getLookAndFeel() instanceof FlatLaf;
  }
}

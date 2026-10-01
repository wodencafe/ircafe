package cafe.woden.ircclient.ui.settings.theme;

import cafe.woden.ircclient.ui.settings.SettingsRangeSupport;
import cafe.woden.ircclient.ui.util.UiColorKeys;
import cafe.woden.ircclient.ui.util.UiDefaultKeys;
import cafe.woden.ircclient.ui.util.UiFontKeys;
import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@InterfaceLayer
@Lazy
class ThemeAppearanceService {

  private static final String NIMBUS_LAF_CLASS = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
  private static final String NIMBUS_OVERRIDES = "Nimbus.Overrides";
  private static final String NIMBUS_OVERRIDES_INHERIT_DEFAULTS =
      "Nimbus.Overrides.InheritDefaults";
  private static final String DENSITY_NIMBUS_OVERRIDES_MARKER = "ircafe.nimbusDensityOverrides";
  private static final String PREVIOUS_NIMBUS_OVERRIDES = "ircafe.previousNimbusOverrides";
  private static final String PREVIOUS_NIMBUS_INHERIT_DEFAULTS =
      "ircafe.previousNimbusOverridesInheritDefaults";
  private static final String PREVIOUS_LIST_FIXED_CELL_HEIGHT =
      "ircafe.previousListFixedCellHeight";
  private static final String PREVIOUS_TABLE_ROW_HEIGHT = "ircafe.previousTableRowHeight";
  private static final String PREVIOUS_TREE_ROW_HEIGHT = "ircafe.previousTreeRowHeight";

  private static final String[] COMMON_TWEAK_OVERRIDE_KEYS = {
    UiDefaultKeys.COMPONENT_ARC,
    UiDefaultKeys.BUTTON_ARC,
    UiDefaultKeys.TEXT_COMPONENT_ARC,
    UiDefaultKeys.PROGRESS_BAR_ARC,
    UiDefaultKeys.SCROLL_PANE_ARC,
    UiDefaultKeys.TREE_ROW_HEIGHT,
    UiDefaultKeys.TABLE_ROW_HEIGHT,
    UiDefaultKeys.LIST_CELL_HEIGHT,
    UiDefaultKeys.BUTTON_MARGIN,
    UiDefaultKeys.TOGGLE_BUTTON_MARGIN,
    UiDefaultKeys.RADIO_BUTTON_MARGIN,
    UiDefaultKeys.CHECK_BOX_MARGIN,
    UiDefaultKeys.TEXT_COMPONENT_MARGIN,
    UiDefaultKeys.TEXT_FIELD_MARGIN,
    UiDefaultKeys.PASSWORD_FIELD_MARGIN,
    UiDefaultKeys.TEXT_AREA_MARGIN,
    UiDefaultKeys.COMBO_BOX_PADDING,
    UiDefaultKeys.BUTTON_CONTENT_MARGINS,
    UiDefaultKeys.TOGGLE_BUTTON_CONTENT_MARGINS,
    UiDefaultKeys.CHECK_BOX_CONTENT_MARGINS,
    UiDefaultKeys.RADIO_BUTTON_CONTENT_MARGINS,
    UiDefaultKeys.TEXT_FIELD_CONTENT_MARGINS,
    UiDefaultKeys.PASSWORD_FIELD_CONTENT_MARGINS,
    UiDefaultKeys.FORMATTED_TEXT_FIELD_CONTENT_MARGINS,
    UiDefaultKeys.TEXT_AREA_CONTENT_MARGINS,
    UiDefaultKeys.TEXT_PANE_CONTENT_MARGINS,
    UiDefaultKeys.EDITOR_PANE_CONTENT_MARGINS,
    UiDefaultKeys.COMBO_BOX_CONTENT_MARGINS,
    UiDefaultKeys.COMBO_BOX_RENDERER_CONTENT_MARGINS,
    UiDefaultKeys.COMBO_BOX_LIST_RENDERER_CONTENT_MARGINS,
    UiDefaultKeys.COMBO_BOX_TEXT_FIELD_CONTENT_MARGINS,
    UiDefaultKeys.MENU_BAR_CONTENT_MARGINS,
    UiDefaultKeys.MENU_BAR_MENU_CONTENT_MARGINS,
    UiDefaultKeys.MENU_CONTENT_MARGINS,
    UiDefaultKeys.MENU_ITEM_CONTENT_MARGINS,
    UiDefaultKeys.CHECK_BOX_MENU_ITEM_CONTENT_MARGINS,
    UiDefaultKeys.RADIO_BUTTON_MENU_ITEM_CONTENT_MARGINS,
    UiDefaultKeys.POPUP_MENU_CONTENT_MARGINS,
    UiDefaultKeys.TABBED_PANE_TAB_CONTENT_MARGINS,
    UiDefaultKeys.TABBED_PANE_TAB_AREA_CONTENT_MARGINS,
    UiDefaultKeys.TABLE_HEADER_RENDERER_CONTENT_MARGINS,
    UiDefaultKeys.TREE_RENDERER_MARGINS,
    UiDefaultKeys.TOOL_TIP_CONTENT_MARGINS
  };

  private static final String[] ACCENT_OVERRIDE_KEYS = {
    UiColorKeys.ACCENT_COLOR,
    UiColorKeys.ACCENT_BASE_COLOR,
    UiColorKeys.ACCENT_BASE_2_COLOR,
    UiColorKeys.COMPONENT_FOCUS_COLOR,
    UiColorKeys.COMPONENT_LINK_COLOR,
    UiColorKeys.TEXT_COMPONENT_SELECTION_BACKGROUND,
    UiColorKeys.TEXT_COMPONENT_SELECTION_FOREGROUND,
    UiColorKeys.LIST_SELECTION_BACKGROUND,
    UiColorKeys.LIST_SELECTION_FOREGROUND,
    UiColorKeys.TABLE_SELECTION_BACKGROUND,
    UiColorKeys.TABLE_SELECTION_FOREGROUND,
    UiColorKeys.TREE_SELECTION_BACKGROUND,
    UiColorKeys.TREE_SELECTION_FOREGROUND
  };

  private static final Object NULL_SENTINEL = new Object();
  private final Map<String, Object> accentBaselineValues = new HashMap<>();
  private String accentBaselineLafClassName;
  private final Map<String, Object> commonTweakBaselineValues = new HashMap<>();
  private String commonTweakBaselineLafClassName;
  private final Map<Object, Object> uiFontBaselineValues = new HashMap<>();
  private String uiFontBaselineLafClassName;
  private final Map<Object, Object> nimbusFontBaselineValues = new HashMap<>();
  private UIDefaults nimbusFontBaselineDefaults;

  private record NimbusDensityMetrics(
      int rowHeight,
      int rowFontPadding,
      InsetsUIResource buttonMargins,
      InsetsUIResource optionMargins,
      InsetsUIResource textMargins,
      InsetsUIResource textAreaMargins,
      InsetsUIResource comboPadding,
      InsetsUIResource comboRendererMargins,
      InsetsUIResource comboTextFieldMargins,
      InsetsUIResource menuBarMargins,
      InsetsUIResource menuBarMenuMargins,
      InsetsUIResource menuItemMargins,
      InsetsUIResource popupMargins,
      InsetsUIResource tabMargins,
      InsetsUIResource tabAreaMargins,
      InsetsUIResource tableHeaderMargins,
      InsetsUIResource treeRendererMargins,
      InsetsUIResource tooltipMargins) {}

  void applyCommonTweaks(ThemeTweakSettings tweaks) {
    ThemeTweakSettings resolved =
        tweaks != null ? tweaks : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10);

    restorePreviousCommonTweakOverridesIfCompatible();
    clearApplicationCommonTweakOverrides();
    applyUiFontOverrides(resolved);

    if (isNimbusActive()) {
      captureCommonTweakBaseline();
      applyNimbusDensityTweaks(resolved.density(), UIManager.getLookAndFeelDefaults());
      return;
    }

    if (!isFlatLafActive()) {
      return;
    }

    int arc = resolved.cornerRadius();
    UIManager.put(UiDefaultKeys.COMPONENT_ARC, arc);
    UIManager.put(UiDefaultKeys.BUTTON_ARC, arc);
    UIManager.put(UiDefaultKeys.TEXT_COMPONENT_ARC, arc);
    UIManager.put(UiDefaultKeys.PROGRESS_BAR_ARC, arc);
    UIManager.put(UiDefaultKeys.SCROLL_PANE_ARC, arc);

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

    UIManager.put(UiDefaultKeys.TREE_ROW_HEIGHT, rowHeight);
    UIManager.put(UiDefaultKeys.TABLE_ROW_HEIGHT, rowHeight);
    UIManager.put(UiDefaultKeys.LIST_CELL_HEIGHT, rowHeight);

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

    UIManager.put(UiDefaultKeys.BUTTON_MARGIN, buttonMargin);
    UIManager.put(UiDefaultKeys.TOGGLE_BUTTON_MARGIN, buttonMargin);
    UIManager.put(UiDefaultKeys.RADIO_BUTTON_MARGIN, buttonMargin);
    UIManager.put(UiDefaultKeys.CHECK_BOX_MARGIN, buttonMargin);

    UIManager.put(UiDefaultKeys.TEXT_COMPONENT_MARGIN, textMargin);
    UIManager.put(UiDefaultKeys.TEXT_FIELD_MARGIN, textMargin);
    UIManager.put(UiDefaultKeys.PASSWORD_FIELD_MARGIN, textMargin);
    UIManager.put(UiDefaultKeys.TEXT_AREA_MARGIN, textMargin);
    UIManager.put(UiDefaultKeys.COMBO_BOX_PADDING, textMargin);
  }

  void applyNimbusDensityToComponentTree(java.awt.Component root, ThemeTweakSettings tweaks) {
    if (root == null) return;
    if (!isNimbusActive()) {
      restoreNimbusDensityComponentOverrides(root);
      return;
    }

    ThemeTweakSettings resolved =
        tweaks != null ? tweaks : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10);
    NimbusDensityMetrics metrics = nimbusDensityMetrics(resolved.density());
    int rowHeight = rowHeight(metrics.rowHeight(), metrics.rowFontPadding());
    UIDefaults overrides = nimbusDensityDefaults(metrics, rowHeight);
    if (resolved.uiFontOverrideEnabled()) {
      // Equal spacing tables suppress property-change events. Include the font so a
      // size/family change also invalidates each component's cached Nimbus style.
      overrides.put(UiFontKeys.DEFAULT_FONT, UIManager.getFont(UiFontKeys.DEFAULT_FONT));
    }
    applyNimbusDensityComponentOverrides(root, overrides, rowHeight);
  }

  private static void applyNimbusDensityTweaks(
      ThemeTweakSettings.ThemeDensity density, UIDefaults defaults) {
    NimbusDensityMetrics metrics = nimbusDensityMetrics(density);
    int rowHeight = rowHeight(metrics.rowHeight(), metrics.rowFontPadding());
    UIDefaults densityDefaults = nimbusDensityDefaults(metrics, rowHeight);

    for (Map.Entry<Object, Object> entry : densityDefaults.entrySet()) {
      defaults.put(entry.getKey(), entry.getValue());
      // Nimbus caches compiled styles. Notify its defaults listener as well as updating
      // the LAF table, including when the density matches the previous theme.
      UIManager.put(entry.getKey(), null);
      UIManager.put(entry.getKey(), entry.getValue());
    }
  }

  private static UIDefaults nimbusDensityDefaults(NimbusDensityMetrics metrics, int rowHeight) {
    UIDefaults defaults = new UIDefaults();
    defaults.put(UiDefaultKeys.TREE_ROW_HEIGHT, rowHeight);
    defaults.put(UiDefaultKeys.TABLE_ROW_HEIGHT, rowHeight);
    defaults.put(UiDefaultKeys.LIST_CELL_HEIGHT, rowHeight);

    defaults.put(UiDefaultKeys.BUTTON_CONTENT_MARGINS, metrics.buttonMargins());
    defaults.put(UiDefaultKeys.TOGGLE_BUTTON_CONTENT_MARGINS, metrics.buttonMargins());
    defaults.put(UiDefaultKeys.CHECK_BOX_CONTENT_MARGINS, metrics.optionMargins());
    defaults.put(UiDefaultKeys.RADIO_BUTTON_CONTENT_MARGINS, metrics.optionMargins());

    defaults.put(UiDefaultKeys.TEXT_FIELD_CONTENT_MARGINS, metrics.textMargins());
    defaults.put(UiDefaultKeys.PASSWORD_FIELD_CONTENT_MARGINS, metrics.textMargins());
    defaults.put(UiDefaultKeys.FORMATTED_TEXT_FIELD_CONTENT_MARGINS, metrics.textMargins());
    defaults.put(UiDefaultKeys.TEXT_AREA_CONTENT_MARGINS, metrics.textAreaMargins());
    defaults.put(UiDefaultKeys.TEXT_PANE_CONTENT_MARGINS, metrics.textAreaMargins());
    defaults.put(UiDefaultKeys.EDITOR_PANE_CONTENT_MARGINS, metrics.textAreaMargins());

    defaults.put(UiDefaultKeys.COMBO_BOX_CONTENT_MARGINS, metrics.comboPadding());
    defaults.put(UiDefaultKeys.COMBO_BOX_PADDING, metrics.comboPadding());
    defaults.put(UiDefaultKeys.COMBO_BOX_RENDERER_CONTENT_MARGINS, metrics.comboRendererMargins());
    defaults.put(
        UiDefaultKeys.COMBO_BOX_LIST_RENDERER_CONTENT_MARGINS, metrics.comboRendererMargins());
    defaults.put(
        UiDefaultKeys.COMBO_BOX_TEXT_FIELD_CONTENT_MARGINS, metrics.comboTextFieldMargins());

    defaults.put(UiDefaultKeys.MENU_BAR_CONTENT_MARGINS, metrics.menuBarMargins());
    defaults.put(UiDefaultKeys.MENU_BAR_MENU_CONTENT_MARGINS, metrics.menuBarMenuMargins());
    defaults.put(UiDefaultKeys.MENU_CONTENT_MARGINS, metrics.menuItemMargins());
    defaults.put(UiDefaultKeys.MENU_ITEM_CONTENT_MARGINS, metrics.menuItemMargins());
    defaults.put(UiDefaultKeys.CHECK_BOX_MENU_ITEM_CONTENT_MARGINS, metrics.menuItemMargins());
    defaults.put(UiDefaultKeys.RADIO_BUTTON_MENU_ITEM_CONTENT_MARGINS, metrics.menuItemMargins());
    defaults.put(UiDefaultKeys.POPUP_MENU_CONTENT_MARGINS, metrics.popupMargins());

    defaults.put(UiDefaultKeys.TABBED_PANE_TAB_CONTENT_MARGINS, metrics.tabMargins());
    defaults.put(UiDefaultKeys.TABBED_PANE_TAB_AREA_CONTENT_MARGINS, metrics.tabAreaMargins());
    defaults.put(UiDefaultKeys.TABLE_HEADER_RENDERER_CONTENT_MARGINS, metrics.tableHeaderMargins());
    defaults.put(UiDefaultKeys.TREE_RENDERER_MARGINS, metrics.treeRendererMargins());
    defaults.put(UiDefaultKeys.TOOL_TIP_CONTENT_MARGINS, metrics.tooltipMargins());
    return defaults;
  }

  private static NimbusDensityMetrics nimbusDensityMetrics(
      ThemeTweakSettings.ThemeDensity density) {
    ThemeTweakSettings.ThemeDensity effectiveDensity =
        density == ThemeTweakSettings.ThemeDensity.AUTO
            ? ThemeTweakSettings.ThemeDensity.COZY
            : density;

    return switch (effectiveDensity) {
      case COMPACT ->
          new NimbusDensityMetrics(
              22,
              7,
              insets(4, 11, 4, 11),
              insets(1, 1, 1, 1),
              insets(4, 6, 4, 6),
              insets(4, 6, 4, 6),
              insets(2, 4, 2, 4),
              insets(2, 5, 2, 5),
              insets(1, 6, 1, 4),
              insets(2, 6, 2, 6),
              insets(2, 6, 3, 6),
              insets(2, 12, 3, 12),
              insets(4, 1, 4, 1),
              insets(3, 9, 4, 9),
              insets(3, 9, 4, 9),
              insets(2, 6, 3, 6),
              insets(2, 1, 2, 6),
              insets(4, 5, 4, 5));
      case SPACIOUS ->
          new NimbusDensityMetrics(
              32,
              14,
              insets(8, 16, 8, 16),
              insets(4, 4, 4, 4),
              insets(8, 9, 8, 9),
              insets(7, 9, 7, 9),
              insets(6, 8, 6, 8),
              insets(6, 8, 6, 8),
              insets(4, 9, 4, 6),
              insets(5, 9, 5, 9),
              insets(5, 10, 6, 10),
              insets(6, 16, 7, 16),
              insets(8, 2, 8, 2),
              insets(7, 14, 8, 14),
              insets(6, 14, 7, 14),
              insets(5, 8, 6, 8),
              insets(4, 2, 4, 8),
              insets(7, 8, 7, 8));
      case COZY, AUTO ->
          new NimbusDensityMetrics(
              26,
              10,
              insets(6, 14, 6, 14),
              insets(2, 3, 2, 3),
              insets(6, 7, 6, 7),
              insets(5, 7, 5, 7),
              insets(4, 6, 4, 6),
              insets(4, 6, 4, 6),
              insets(2, 7, 2, 5),
              insets(3, 7, 3, 7),
              insets(3, 8, 4, 8),
              insets(4, 14, 5, 14),
              insets(6, 1, 6, 1),
              insets(5, 12, 6, 12),
              insets(4, 12, 5, 12),
              insets(3, 7, 5, 7),
              insets(3, 1, 3, 7),
              insets(5, 6, 5, 6));
    };
  }

  private static int rowHeight(int baseRowHeight, int fontPadding) {
    Font font = UIManager.getFont(UiFontKeys.DEFAULT_FONT);
    if (font == null) font = UIManager.getFont(UiFontKeys.LABEL_FONT);
    if (font == null) return baseRowHeight;
    return Math.max(baseRowHeight, Math.round(font.getSize2D()) + fontPadding);
  }

  private static InsetsUIResource insets(int top, int left, int bottom, int right) {
    return new InsetsUIResource(top, left, bottom, right);
  }

  private void applyUiFontOverrides(ThemeTweakSettings tweaks) {
    restorePreviousUiFontOverridesIfCompatible();

    if (tweaks == null || !tweaks.uiFontOverrideEnabled()) return;

    captureUiFontBaseline();

    Font defaultFont = UIManager.getFont(UiFontKeys.DEFAULT_FONT);
    if (defaultFont == null) defaultFont = UIManager.getFont(UiFontKeys.LABEL_FONT);
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
      putUiFont(key, new FontUIResource(replacement));
    }

    putUiFont(
        UiFontKeys.DEFAULT_FONT,
        new FontUIResource(new Font(tweaks.uiFontFamily(), Font.PLAIN, tweaks.uiFontSize())));
    if (isNimbusActive()) NimbusFontStyleFactory.install();
  }

  private void putUiFont(Object key, FontUIResource font) {
    // Nimbus compiles both defaults tables. Keep them consistent so a native lazy font
    // cannot shadow the developer override while Synth is building a component style.
    if (nimbusFontBaselineDefaults != null) nimbusFontBaselineDefaults.put(key, font);
    UIManager.put(key, font);
  }

  void applyAccentOverrides(ThemeAccentSettings accent) {
    restorePreviousAccentOverridesIfCompatible();

    if (accent == null || !accent.enabled()) return;

    Color chosen = ThemeColorUtils.parseHexColor(accent.accentColor());
    if (chosen == null) return;

    Color themeAccent = UIManager.getColor(UiColorKeys.ACCENT_COLOR);
    if (themeAccent == null) themeAccent = UIManager.getColor(UiColorKeys.COMPONENT_FOCUS_COLOR);
    if (themeAccent == null) themeAccent = new Color(0x2D, 0x6B, 0xFF);

    double strength = SettingsRangeSupport.normalizeThemePercent(accent.strength()) / 100.0;
    Color blended = ThemeColorUtils.mix(themeAccent, chosen, strength);

    Color panelBg = UIManager.getColor(UiColorKeys.PANEL_BACKGROUND);
    if (panelBg == null) panelBg = UIManager.getColor(UiColorKeys.CONTROL);

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
      UIManager.put(UiColorKeys.ACCENT_COLOR, blended);
      UIManager.put(UiColorKeys.ACCENT_BASE_COLOR, blended);
      UIManager.put(UiColorKeys.ACCENT_BASE_2_COLOR, focus);
    }

    UIManager.put(UiColorKeys.COMPONENT_FOCUS_COLOR, focus);
    UIManager.put(UiColorKeys.COMPONENT_LINK_COLOR, link);

    Color bg = UIManager.getColor(UiColorKeys.TEXT_COMPONENT_BACKGROUND);
    if (bg == null) bg = UIManager.getColor(UiColorKeys.PANEL_BACKGROUND);
    if (bg == null) bg = UIManager.getColor(UiColorKeys.CONTROL);
    if (bg == null) bg = dark ? Color.DARK_GRAY : Color.LIGHT_GRAY;

    double selMix = dark ? 0.55 : 0.35;
    Color selectionBg = ThemeColorUtils.mix(bg, blended, selMix);
    Color selectionFg = ThemeColorUtils.bestTextColor(selectionBg);

    UIManager.put(UiColorKeys.TEXT_COMPONENT_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TEXT_COMPONENT_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.LIST_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.LIST_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.TABLE_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TABLE_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.TREE_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TREE_SELECTION_FOREGROUND, selectionFg);
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
    // Font values can be lazy or active (including Nimbus renderer and state fonts).
    // Resolve every font key before applying any replacements, using effective defaults.
    for (Object key : defaults.keySet().toArray()) {
      if (key instanceof String name && name.toLowerCase(Locale.ROOT).endsWith("font")) {
        Font font = defaults.getFont(key);
        if (font != null) uiFontBaselineValues.put(key, font);
      }
    }

    Object defaultFont = UIManager.get(UiFontKeys.DEFAULT_FONT);
    if (!uiFontBaselineValues.containsKey(UiFontKeys.DEFAULT_FONT)) {
      uiFontBaselineValues.put(
          UiFontKeys.DEFAULT_FONT, defaultFont != null ? defaultFont : NULL_SENTINEL);
    }

    if (isNimbusActive()) {
      nimbusFontBaselineDefaults = UIManager.getLookAndFeelDefaults();
      Map<Object, Object> nativeDefaults = new HashMap<>(nimbusFontBaselineDefaults);
      for (Object key : uiFontBaselineValues.keySet()) {
        nimbusFontBaselineValues.put(key, nativeDefaults.getOrDefault(key, NULL_SENTINEL));
      }
    }

    uiFontBaselineLafClassName = ThemeLookAndFeelUtils.currentLookAndFeelClassName();
  }

  private void restorePreviousUiFontOverridesIfCompatible() {
    if (nimbusFontBaselineDefaults != null) {
      NimbusFontStyleFactory.restore();
      // Restore the table we changed, even when a new Nimbus variant has been installed.
      for (Map.Entry<Object, Object> entry : nimbusFontBaselineValues.entrySet()) {
        nimbusFontBaselineDefaults.put(entry.getKey(), denullSentinel(entry.getValue()));
      }
      nimbusFontBaselineValues.clear();
      nimbusFontBaselineDefaults = null;
    }
    if (uiFontBaselineValues.isEmpty()) return;

    String currentLafClassName = ThemeLookAndFeelUtils.currentLookAndFeelClassName();
    if (!Objects.equals(currentLafClassName, uiFontBaselineLafClassName)) {
      // UIManager.put() writes developer defaults, which survive LAF switches.
      // If a font override was applied under another LAF class, clear those keys so the
      // newly-installed LAF can supply its own defaults (e.g. Tree.font in DarkLaf).
      for (Object key : uiFontBaselineValues.keySet()) {
        UIManager.put(key, null);
      }
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

  private void captureCommonTweakBaseline() {
    if (!commonTweakBaselineValues.isEmpty()) return;

    UIDefaults defaults = UIManager.getLookAndFeelDefaults();
    for (String key : COMMON_TWEAK_OVERRIDE_KEYS) {
      Object value = defaults.get(key);
      commonTweakBaselineValues.put(key, value != null ? value : NULL_SENTINEL);
    }
    commonTweakBaselineLafClassName = ThemeLookAndFeelUtils.currentLookAndFeelClassName();
  }

  private void restorePreviousCommonTweakOverridesIfCompatible() {
    if (commonTweakBaselineValues.isEmpty()) return;

    String currentLafClassName = ThemeLookAndFeelUtils.currentLookAndFeelClassName();
    if (!Objects.equals(currentLafClassName, commonTweakBaselineLafClassName)) {
      commonTweakBaselineValues.clear();
      commonTweakBaselineLafClassName = null;
      return;
    }

    UIDefaults defaults = UIManager.getLookAndFeelDefaults();
    for (Map.Entry<String, Object> entry : commonTweakBaselineValues.entrySet()) {
      defaults.put(entry.getKey(), entry.getValue() == NULL_SENTINEL ? null : entry.getValue());
    }

    commonTweakBaselineValues.clear();
    commonTweakBaselineLafClassName = null;
  }

  private static void clearApplicationCommonTweakOverrides() {
    for (String key : COMMON_TWEAK_OVERRIDE_KEYS) {
      UIManager.put(key, null);
    }
  }

  private static void applyNimbusDensityComponentOverrides(
      java.awt.Component component, UIDefaults densityDefaults, int rowHeight) {
    if (component instanceof JComponent jc) {
      installNimbusDensityOverrides(jc, densityDefaults);
      applyNimbusRowHeightOverride(jc, rowHeight);
    }

    if (component instanceof JMenu menu) {
      for (java.awt.Component child : menu.getMenuComponents()) {
        applyNimbusDensityComponentOverrides(child, densityDefaults, rowHeight);
      }
    }

    if (component instanceof Container container) {
      for (java.awt.Component child : container.getComponents()) {
        applyNimbusDensityComponentOverrides(child, densityDefaults, rowHeight);
      }
    }
  }

  private static void restoreNimbusDensityComponentOverrides(java.awt.Component component) {
    if (component instanceof JComponent jc) {
      restoreNimbusDensityOverrides(jc);
      restoreNimbusRowHeightOverride(jc);
    }

    if (component instanceof JMenu menu) {
      for (java.awt.Component child : menu.getMenuComponents()) {
        restoreNimbusDensityComponentOverrides(child);
      }
    }

    if (component instanceof Container container) {
      for (java.awt.Component child : container.getComponents()) {
        restoreNimbusDensityComponentOverrides(child);
      }
    }
  }

  private static void installNimbusDensityOverrides(
      JComponent component, UIDefaults densityDefaults) {
    if (!Boolean.TRUE.equals(component.getClientProperty(DENSITY_NIMBUS_OVERRIDES_MARKER))) {
      component.putClientProperty(
          PREVIOUS_NIMBUS_OVERRIDES, nullSentinel(component.getClientProperty(NIMBUS_OVERRIDES)));
      component.putClientProperty(
          PREVIOUS_NIMBUS_INHERIT_DEFAULTS,
          nullSentinel(component.getClientProperty(NIMBUS_OVERRIDES_INHERIT_DEFAULTS)));
      component.putClientProperty(DENSITY_NIMBUS_OVERRIDES_MARKER, Boolean.TRUE);
    }

    UIDefaults merged = new UIDefaults();
    Object previous = denullSentinel(component.getClientProperty(PREVIOUS_NIMBUS_OVERRIDES));
    if (previous instanceof UIDefaults previousDefaults) {
      merged.putAll(previousDefaults);
    }
    merged.putAll(densityDefaults);

    component.putClientProperty(NIMBUS_OVERRIDES, merged);
    component.putClientProperty(NIMBUS_OVERRIDES_INHERIT_DEFAULTS, Boolean.TRUE);
  }

  private static void restoreNimbusDensityOverrides(JComponent component) {
    if (!Boolean.TRUE.equals(component.getClientProperty(DENSITY_NIMBUS_OVERRIDES_MARKER))) {
      return;
    }

    component.putClientProperty(
        NIMBUS_OVERRIDES, denullSentinel(component.getClientProperty(PREVIOUS_NIMBUS_OVERRIDES)));
    component.putClientProperty(
        NIMBUS_OVERRIDES_INHERIT_DEFAULTS,
        denullSentinel(component.getClientProperty(PREVIOUS_NIMBUS_INHERIT_DEFAULTS)));
    component.putClientProperty(PREVIOUS_NIMBUS_OVERRIDES, null);
    component.putClientProperty(PREVIOUS_NIMBUS_INHERIT_DEFAULTS, null);
    component.putClientProperty(DENSITY_NIMBUS_OVERRIDES_MARKER, null);
  }

  private static void applyNimbusRowHeightOverride(JComponent component, int rowHeight) {
    if (component instanceof JList<?> list) {
      rememberIntClientProperty(list, PREVIOUS_LIST_FIXED_CELL_HEIGHT, list.getFixedCellHeight());
      list.setFixedCellHeight(rowHeight);
    } else if (component instanceof JTable table) {
      rememberIntClientProperty(table, PREVIOUS_TABLE_ROW_HEIGHT, table.getRowHeight());
      table.setRowHeight(rowHeight);
    } else if (component instanceof JTree tree) {
      rememberIntClientProperty(tree, PREVIOUS_TREE_ROW_HEIGHT, tree.getRowHeight());
      tree.setRowHeight(rowHeight);
    }
  }

  private static void restoreNimbusRowHeightOverride(JComponent component) {
    if (component instanceof JList<?> list) {
      restoreIntClientProperty(list, PREVIOUS_LIST_FIXED_CELL_HEIGHT, list::setFixedCellHeight);
    } else if (component instanceof JTable table) {
      restoreIntClientProperty(table, PREVIOUS_TABLE_ROW_HEIGHT, table::setRowHeight);
    } else if (component instanceof JTree tree) {
      restoreIntClientProperty(tree, PREVIOUS_TREE_ROW_HEIGHT, tree::setRowHeight);
    }
  }

  private static void rememberIntClientProperty(JComponent component, String key, int value) {
    if (component.getClientProperty(key) == null) {
      component.putClientProperty(key, value);
    }
  }

  private static void restoreIntClientProperty(
      JComponent component, String key, java.util.function.IntConsumer setter) {
    Object previous = component.getClientProperty(key);
    if (previous instanceof Integer value) {
      setter.accept(value);
      component.putClientProperty(key, null);
    }
  }

  private static Object nullSentinel(Object value) {
    return value != null ? value : NULL_SENTINEL;
  }

  private static Object denullSentinel(Object value) {
    return value == NULL_SENTINEL ? null : value;
  }

  private static boolean isFlatLafActive() {
    return UIManager.getLookAndFeel() instanceof FlatLaf;
  }

  private static boolean isNimbusActive() {
    return Objects.equals(NIMBUS_LAF_CLASS, ThemeLookAndFeelUtils.currentLookAndFeelClassName());
  }
}

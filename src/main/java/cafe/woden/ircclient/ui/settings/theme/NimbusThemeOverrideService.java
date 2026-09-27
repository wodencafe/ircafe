package cafe.woden.ircclient.ui.settings.theme;

import cafe.woden.ircclient.ui.util.UiColorKeys;
import cafe.woden.ircclient.ui.util.UiDefaultKeys;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.Painter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@InterfaceLayer
@Lazy
class NimbusThemeOverrideService {

  private static final Logger log = LoggerFactory.getLogger(NimbusThemeOverrideService.class);

  private record NimbusVariantSpec(boolean darkVariant, Runnable applyOverrides) {}

  private record NimbusSurfacePainter(Color top, Color bottom, Color border)
      implements Painter<JComponent> {
    @Override
    public void paint(Graphics2D g, JComponent object, int width, int height) {
      if (width <= 0 || height <= 0) return;
      g.setPaint(new GradientPaint(0, 0, top, 0, Math.max(1, height), bottom));
      g.fillRect(0, 0, width, height);
      g.setColor(border);
      g.drawLine(0, height - 1, width, height - 1);
    }
  }

  private record NimbusArrowPainter(Color color, int direction) implements Painter<JComponent> {
    @Override
    public void paint(Graphics2D g, JComponent object, int width, int height) {
      int cx = width / 2;
      int cy = height / 2;
      int radius = Math.min(4, Math.min(width, height) / 3);
      g.setColor(color);
      if (direction == javax.swing.SwingConstants.WEST) {
        // Nimbus rotates the canonical west-facing glyph for combos and scrollbars.
        g.fillPolygon(
            new int[] {cx + radius, cx + radius, cx - radius},
            new int[] {cy - radius, cy + radius, cy},
            3);
      } else {
        int sign = direction == javax.swing.SwingConstants.NORTH ? -1 : 1;
        g.fillPolygon(
            new int[] {cx - radius, cx + radius, cx},
            new int[] {cy - sign * radius / 2, cy - sign * radius / 2, cy + sign * radius},
            3);
      }
    }
  }

  private record NimbusThumbPainter(Color color) implements Painter<JComponent> {
    @Override
    public void paint(Graphics2D g, JComponent object, int width, int height) {
      g.setColor(color);
      g.fillRoundRect(3, 3, Math.max(0, width - 6), Math.max(0, height - 6), 6, 6);
    }
  }

  private static final String[] SPINNER_BUTTON_PREFIXES = {
    "Spinner:\"Spinner.nextButton\"", "Spinner:\"Spinner.previousButton\""
  };
  private static final String[] BUTTON_STATES = {
    "Enabled", "Disabled", "Focused", "MouseOver", "Pressed", "Focused+MouseOver", "Focused+Pressed"
  };
  private static final String[] SCROLLBAR_STATES = {"Enabled", "Disabled", "MouseOver", "Pressed"};

  private static void applyAuxiliaryPainters() {
    Color panel = UIManager.getColor(UiColorKeys.PANEL_BACKGROUND);
    Color field = UIManager.getColor(UiColorKeys.TEXT_FIELD_BACKGROUND);
    Color text = UIManager.getColor(UiColorKeys.TEXT_FIELD_FOREGROUND);
    Color border = UIManager.getColor(UiColorKeys.NIMBUS_BORDER);
    Color focus = UIManager.getColor(UiColorKeys.NIMBUS_FOCUS);
    Color disabled = UIManager.getColor(UiColorKeys.NIMBUS_DISABLED_TEXT);
    for (String prefix : SPINNER_BUTTON_PREFIXES) {
      int direction =
          prefix.contains("nextButton")
              ? javax.swing.SwingConstants.NORTH
              : javax.swing.SwingConstants.SOUTH;
      for (String state : BUTTON_STATES) {
        boolean inactive = state.equals("Disabled");
        Color bg = inactive ? mix(field, panel, 0.7) : field;
        if (state.contains("MouseOver")) bg = mix(bg, focus, 0.18);
        if (state.contains("Pressed")) bg = mix(bg, panel, 0.3);
        String key = prefix + "[" + state + "]";
        UIManager.put(
            key + ".backgroundPainter",
            new NimbusInputPainter(bg, state.contains("Focused") ? focus : border));
        UIManager.put(
            key + ".foregroundPainter",
            new NimbusArrowPainter(
                ThemeColorUtils.ensureContrastAgainstBackground(
                    inactive ? disabled : text, bg, 4.5),
                direction));
      }
    }
    Color thumb =
        ThemeColorUtils.ensureContrastAgainstBackground(mix(panel, text, 0.35), panel, 3.0);
    for (String state : SCROLLBAR_STATES) {
      String suffix = "[" + state + "]";
      Color thumbColor = state.equals("MouseOver") || state.equals("Pressed") ? focus : thumb;
      UIManager.put(
          "ScrollBar:ScrollBarThumb" + suffix + ".backgroundPainter",
          new NimbusThumbPainter(thumbColor));
      UIManager.put(
          "ScrollBar:ScrollBarTrack" + suffix + ".backgroundPainter",
          new NimbusSurfacePainter(panel, panel, panel));
      UIManager.put(
          "ScrollBar:\"ScrollBar.button\"" + suffix + ".foregroundPainter",
          new NimbusArrowPainter(
              state.equals("Disabled") ? disabled : text, javax.swing.SwingConstants.WEST));
    }
  }

  private static Set<String> auxiliaryPainterKeys() {
    Set<String> keys = new HashSet<>();
    for (String prefix : SPINNER_BUTTON_PREFIXES) {
      for (String state : BUTTON_STATES) {
        keys.add(prefix + "[" + state + "].backgroundPainter");
        keys.add(prefix + "[" + state + "].foregroundPainter");
      }
    }
    for (String state : SCROLLBAR_STATES) {
      keys.add("ScrollBar:ScrollBarThumb[" + state + "].backgroundPainter");
      keys.add("ScrollBar:ScrollBarTrack[" + state + "].backgroundPainter");
      keys.add("ScrollBar:\"ScrollBar.button\"[" + state + "].foregroundPainter");
    }
    return keys;
  }

  private static final String SPINNER_EDITOR = "Spinner:Panel:\"Spinner.formattedTextField\"";
  private static final String[] INPUT_PREFIXES = {
    "TextField", "PasswordField", "FormattedTextField", SPINNER_EDITOR
  };
  private static final String[] INPUT_STATES = {
    "Enabled", "Disabled", "Selected", "Focused", "Focused+Selected"
  };
  private static final String[] INPUT_PROPERTIES = {
    "background", "textForeground", "backgroundPainter", "borderPainter"
  };

  private record NimbusInputPainter(Color background, Color border) implements Painter<JComponent> {
    @Override
    public void paint(Graphics2D g, JComponent object, int width, int height) {
      if (width <= 2 || height <= 2) return;
      g.setColor(background);
      g.fillRect(1, 1, width - 2, height - 2);
      g.setColor(border);
      g.drawRect(0, 0, width - 1, height - 1);
    }
  }

  private static void applyInputStates() {
    Color background = UIManager.getColor(UiColorKeys.TEXT_FIELD_BACKGROUND);
    Color foreground = UIManager.getColor(UiColorKeys.TEXT_FIELD_FOREGROUND);
    Color panel = UIManager.getColor(UiColorKeys.PANEL_BACKGROUND);
    Color border = UIManager.getColor(UiColorKeys.NIMBUS_BORDER);
    Color disabledBackground = toUiResource(mix(background, panel, 0.7));
    Color disabledForeground =
        toUiResource(
            ThemeColorUtils.ensureContrastAgainstBackground(
                UIManager.getColor(UiColorKeys.NIMBUS_DISABLED_TEXT), disabledBackground, 4.5));
    Painter<JComponent> noBorder = (g, c, w, h) -> {};
    for (String prefix : INPUT_PREFIXES) {
      for (String state : INPUT_STATES) {
        boolean disabled = state.equals("Disabled");
        Color bg = disabled ? disabledBackground : background;
        String key = prefix + "[" + state + "]";
        UIManager.put(key + ".background", bg);
        UIManager.put(
            key + ".textForeground",
            disabled
                ? disabledForeground
                : state.contains("Selected")
                    ? UIManager.getColor(UiColorKeys.NIMBUS_SELECTED_TEXT)
                    : foreground);
        UIManager.put(
            key + ".backgroundPainter",
            new NimbusInputPainter(
                bg,
                state.contains("Focused") ? UIManager.getColor(UiColorKeys.NIMBUS_FOCUS) : border));
        UIManager.put(key + ".borderPainter", noBorder);
      }
    }
  }

  private static Set<String> inputOverrideKeys() {
    Set<String> keys = new HashSet<>();
    for (String prefix : INPUT_PREFIXES) {
      for (String state : INPUT_STATES) {
        for (String property : INPUT_PROPERTIES) {
          keys.add(prefix + "[" + state + "]." + property);
        }
      }
    }
    return keys;
  }

  private static final String[] TEXT_SURFACE_PREFIXES = {
    "TextField", "PasswordField", "FormattedTextField", "TextArea", "TextPane", "EditorPane"
  };

  private static final String[] SELECTION_STATE_PREFIXES = {
    "List[Selected]", "List:\"List.cellRenderer\"[Selected]", "Table[Enabled+Selected]"
  };

  private static void applyTextSurfaceStates() {
    for (String prefix : SELECTION_STATE_PREFIXES) {
      UIManager.put(
          prefix + ".textForeground", UIManager.getColor(UiColorKeys.NIMBUS_SELECTED_TEXT));
      UIManager.put(
          prefix + ".textBackground", UIManager.getColor(UiColorKeys.NIMBUS_SELECTION_BACKGROUND));
    }
    // Synth resolves state colors before component colors. Keep newly created editors in
    // sync with the palette used by existing windows during a theme switch.
    for (String prefix : TEXT_SURFACE_PREFIXES) {
      UIManager.put(prefix + "[Enabled].background", UIManager.getColor(prefix + ".background"));
      UIManager.put(
          prefix + "[Enabled].textForeground", UIManager.getColor(prefix + ".foreground"));
      UIManager.put(
          prefix + "[Selected].textForeground",
          UIManager.getColor(UiColorKeys.NIMBUS_SELECTED_TEXT));
      UIManager.put(
          prefix + "[Selected].textBackground",
          UIManager.getColor(UiColorKeys.NIMBUS_SELECTION_BACKGROUND));
    }
  }

  private static final String[] NIMBUS_DARK_OVERRIDE_KEYS = {
    UiColorKeys.TEXT_COMPONENT_BACKGROUND,
    UiColorKeys.CONTROL,
    UiColorKeys.INFO,
    UiColorKeys.NIMBUS_BASE,
    UiColorKeys.NIMBUS_BLUE_GREY,
    UiColorKeys.NIMBUS_BORDER,
    UiColorKeys.NIMBUS_LIGHT_BACKGROUND,
    UiColorKeys.NIMBUS_FOCUS,
    UiColorKeys.NIMBUS_SELECTION_BACKGROUND,
    UiColorKeys.NIMBUS_SELECTED_TEXT,
    UiColorKeys.NIMBUS_DISABLED_TEXT,
    UiColorKeys.NIMBUS_INFO_BLUE,
    UiColorKeys.NIMBUS_ALERT_YELLOW,
    UiColorKeys.NIMBUS_ORANGE,
    UiColorKeys.NIMBUS_RED,
    UiColorKeys.NIMBUS_GREEN,
    UiColorKeys.TEXT_HIGHLIGHT,
    UiColorKeys.TEXT_HIGHLIGHT_TEXT,
    UiColorKeys.TEXT,
    UiColorKeys.TEXT_FOREGROUND,
    UiColorKeys.TEXT_TEXT,
    UiColorKeys.CONTROL_TEXT,
    UiColorKeys.CONTROL_DK_SHADOW,
    UiColorKeys.CONTROL_SHADOW,
    UiColorKeys.CONTROL_LT_HIGHLIGHT,
    UiColorKeys.LABEL_FOREGROUND,
    UiColorKeys.PANEL_BACKGROUND,
    UiColorKeys.MENU,
    UiColorKeys.COMPONENT_FOCUS_COLOR,
    UiColorKeys.COMPONENT_ACCENT_COLOR,
    UiColorKeys.COMPONENT_LINK_COLOR,
    UiColorKeys.COMPONENT_BORDER_COLOR,
    UiColorKeys.COMPONENT_WARNING_COLOR,
    UiColorKeys.COMPONENT_WARNING_OUTLINE_COLOR,
    UiColorKeys.COMPONENT_WARNING_BORDER_COLOR,
    UiColorKeys.COMPONENT_WARNING_FOCUSED_BORDER_COLOR,
    UiColorKeys.COMPONENT_WARNING_FOCUS_COLOR,
    UiColorKeys.COMPONENT_ERROR_COLOR,
    UiColorKeys.COMPONENT_ERROR_OUTLINE_COLOR,
    UiColorKeys.COMPONENT_ERROR_BORDER_COLOR,
    UiColorKeys.COMPONENT_ERROR_FOCUSED_BORDER_COLOR,
    UiColorKeys.COMPONENT_ERROR_FOCUS_COLOR,
    UiColorKeys.TEXT_FIELD_BACKGROUND,
    UiColorKeys.TEXT_FIELD_FOREGROUND,
    UiColorKeys.TEXT_FIELD_BORDER_COLOR,
    UiColorKeys.TEXT_AREA_BACKGROUND,
    UiColorKeys.TEXT_AREA_FOREGROUND,
    UiColorKeys.TEXT_COMPONENT_SELECTION_BACKGROUND,
    UiColorKeys.TEXT_COMPONENT_SELECTION_FOREGROUND,
    UiColorKeys.COMBO_BOX_BACKGROUND,
    UiColorKeys.COMBO_BOX_FOREGROUND,
    UiColorKeys.COMBO_BOX_DISABLED,
    UiColorKeys.COMBO_BOX_DISABLED_TEXT,
    UiColorKeys.COMBO_BOX_SELECTION_BACKGROUND,
    UiColorKeys.COMBO_BOX_SELECTION_FOREGROUND,
    UiDefaultKeys.COMBO_BOX_RENDERER_USE_LIST_COLORS,
    UiColorKeys.COMBO_BOX_LIST_RENDERER_BACKGROUND,
    UiColorKeys.COMBO_BOX_LIST_RENDERER_TEXT_FOREGROUND,
    UiColorKeys.COMBO_BOX_LIST_RENDERER_SELECTED_BACKGROUND,
    UiColorKeys.COMBO_BOX_LIST_RENDERER_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.COMBO_BOX_RENDERER_BACKGROUND,
    UiColorKeys.COMBO_BOX_RENDERER_TEXT_FOREGROUND,
    UiColorKeys.COMBO_BOX_RENDERER_SELECTED_BACKGROUND,
    UiColorKeys.COMBO_BOX_RENDERER_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.COMBO_BOX_RENDERER_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.COMBO_BOX_TEXT_FIELD_BACKGROUND,
    UiColorKeys.COMBO_BOX_TEXT_FIELD_TEXT_FOREGROUND,
    UiColorKeys.COMBO_BOX_TEXT_FIELD_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.COMBO_BOX_ARROW_BUTTON_BACKGROUND,
    UiColorKeys.COMBO_BOX_ARROW_BUTTON_FOREGROUND,
    UiDefaultKeys.COMBO_BOX_DISABLED_EDITABLE_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_DISABLED_PRESSED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_DISABLED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_EDITABLE_ENABLED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_EDITABLE_FOCUSED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_EDITABLE_MOUSE_OVER_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_EDITABLE_PRESSED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ENABLED_SELECTED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ENABLED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_FOCUSED_MOUSE_OVER_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_FOCUSED_PRESSED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_FOCUSED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_MOUSE_OVER_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_PRESSED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_DISABLED_EDITABLE_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_EDITABLE_ENABLED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_EDITABLE_MOUSE_OVER_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_EDITABLE_PRESSED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_EDITABLE_SELECTED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_DISABLED_FOREGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_ENABLED_FOREGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_MOUSE_OVER_FOREGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_PRESSED_FOREGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_SELECTED_FOREGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_TEXT_FIELD_DISABLED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_TEXT_FIELD_ENABLED_BACKGROUND_PAINTER,
    UiDefaultKeys.COMBO_BOX_TEXT_FIELD_SELECTED_BACKGROUND_PAINTER,
    UiColorKeys.BUTTON_BACKGROUND,
    UiColorKeys.BUTTON_FOREGROUND,
    UiColorKeys.BUTTON_DISABLED_TEXT,
    UiColorKeys.BUTTON_SELECT,
    UiColorKeys.BUTTON_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.BUTTON_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.BUTTON_PRESSED_TEXT_FOREGROUND,
    UiColorKeys.BUTTON_DEFAULT_TEXT_FOREGROUND,
    UiColorKeys.BUTTON_DEFAULT_PRESSED_TEXT_FOREGROUND,
    UiColorKeys.TOGGLE_BUTTON_BACKGROUND,
    UiColorKeys.TOGGLE_BUTTON_FOREGROUND,
    UiColorKeys.TOGGLE_BUTTON_DISABLED_TEXT,
    UiColorKeys.TOGGLE_BUTTON_SELECT,
    UiColorKeys.CHECK_BOX_BACKGROUND,
    UiColorKeys.CHECK_BOX_FOREGROUND,
    UiColorKeys.CHECK_BOX_DISABLED_TEXT,
    UiColorKeys.RADIO_BUTTON_BACKGROUND,
    UiColorKeys.RADIO_BUTTON_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_DISABLED_TEXT,
    UiColorKeys.LIST_BACKGROUND,
    UiColorKeys.LIST_FOREGROUND,
    UiColorKeys.LIST_SELECTION_BACKGROUND,
    UiColorKeys.LIST_SELECTION_FOREGROUND,
    UiColorKeys.TABLE_BACKGROUND,
    UiColorKeys.TABLE_FOREGROUND,
    UiColorKeys.TABLE_GRID_COLOR,
    UiColorKeys.TABLE_SELECTION_BACKGROUND,
    UiColorKeys.TABLE_SELECTION_FOREGROUND,
    UiColorKeys.TREE_TEXT_FOREGROUND,
    UiColorKeys.TREE_TEXT_BACKGROUND,
    UiColorKeys.TREE_SELECTION_FOREGROUND,
    UiColorKeys.TREE_SELECTION_BACKGROUND,
    UiColorKeys.MENU_BAR_BACKGROUND,
    UiColorKeys.MENU_BAR_FOREGROUND,
    UiColorKeys.MENU_BAR_BORDER_COLOR,
    UiColorKeys.MENU_BACKGROUND,
    UiColorKeys.MENU_FOREGROUND,
    UiColorKeys.MENU_SELECTION_BACKGROUND,
    UiColorKeys.MENU_SELECTION_FOREGROUND,
    UiColorKeys.MENU_DISABLED_FOREGROUND,
    UiColorKeys.MENU_BAR_MENU_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.MENU_BAR_MENU_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.MENU_BAR_MENU_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.MENU_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.MENU_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.MENU_ENABLED_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.MENU_ITEM_BACKGROUND,
    UiColorKeys.MENU_ITEM_FOREGROUND,
    UiColorKeys.MENU_ITEM_SELECTION_BACKGROUND,
    UiColorKeys.MENU_ITEM_SELECTION_FOREGROUND,
    UiColorKeys.MENU_ITEM_DISABLED_FOREGROUND,
    UiColorKeys.MENU_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND,
    UiColorKeys.MENU_ITEM_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.MENU_ITEM_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.MENU_ITEM_MOUSE_OVER_TEXT_FOREGROUND,
    UiColorKeys.MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND,
    UiColorKeys.MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_BACKGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_BACKGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_DISABLED_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_MOUSE_OVER_TEXT_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_MOUSE_OVER_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_BACKGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_BACKGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_DISABLED_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_MOUSE_OVER_TEXT_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_MOUSE_OVER_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.POPUP_MENU_BACKGROUND,
    UiColorKeys.POPUP_MENU_FOREGROUND,
    UiColorKeys.POPUP_MENU_BORDER_COLOR,
    UiColorKeys.POPUP_MENU_SEPARATOR_FOREGROUND,
    UiColorKeys.POPUP_MENU_SEPARATOR_BACKGROUND,
    UiColorKeys.SEPARATOR_FOREGROUND,
    UiColorKeys.SEPARATOR_BACKGROUND,
    UiColorKeys.TOOL_BAR_SEPARATOR_COLOR,
    UiColorKeys.SPLIT_PANE_BACKGROUND,
    UiColorKeys.SPLIT_PANE_FOREGROUND,
    UiColorKeys.SPLIT_PANE_DIVIDER_DRAGGING_COLOR,
    UiColorKeys.SCROLL_PANE_BORDER_COLOR,
    UiColorKeys.PASSWORD_FIELD_BACKGROUND,
    UiColorKeys.PASSWORD_FIELD_BORDER_COLOR,
    UiColorKeys.PASSWORD_FIELD_FOREGROUND,
    UiColorKeys.FORMATTED_TEXT_FIELD_BACKGROUND,
    UiColorKeys.FORMATTED_TEXT_FIELD_BORDER_COLOR,
    UiColorKeys.FORMATTED_TEXT_FIELD_FOREGROUND,
    UiColorKeys.TEXT_PANE_BACKGROUND,
    UiColorKeys.TEXT_PANE_FOREGROUND,
    UiColorKeys.EDITOR_PANE_BACKGROUND,
    UiColorKeys.EDITOR_PANE_FOREGROUND,
    UiColorKeys.TREE_BACKGROUND,
    UiColorKeys.TABLE_HEADER_BACKGROUND,
    UiColorKeys.TABLE_HEADER_FOREGROUND,
    UiColorKeys.TABLE_HEADER_RENDERER_BACKGROUND,
    UiColorKeys.TABLE_HEADER_RENDERER_FOREGROUND,
    UiColorKeys.VIEWPORT_BACKGROUND,
    UiColorKeys.VIEWPORT_FOREGROUND,
    UiColorKeys.SPINNER_BACKGROUND,
    UiColorKeys.SPINNER_FOREGROUND,
    UiColorKeys.SPINNER_FORMATTED_TEXT_FIELD_BACKGROUND,
    UiColorKeys.SPINNER_FORMATTED_TEXT_FIELD_FOREGROUND,
    UiColorKeys.TOOL_TIP_BACKGROUND,
    UiColorKeys.TOOL_TIP_FOREGROUND,
    UiColorKeys.TABBED_PANE_BACKGROUND,
    UiColorKeys.TABBED_PANE_FOREGROUND,
    UiColorKeys.TABBED_PANE_DISABLED,
    UiColorKeys.TABBED_PANE_DISABLED_TEXT,
    UiColorKeys.TABBED_PANE_DARK_SHADOW,
    UiColorKeys.TABBED_PANE_HIGHLIGHT,
    UiColorKeys.TABBED_PANE_SHADOW,
    UiColorKeys.TABBED_PANE_FOCUS,
    UiColorKeys.TABBED_PANE_TAB_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.TABBED_PANE_TAB_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.TABBED_PANE_TAB_MOUSE_OVER_TEXT_FOREGROUND,
    UiColorKeys.TABBED_PANE_TAB_PRESSED_TEXT_FOREGROUND,
    UiColorKeys.TABBED_PANE_TAB_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.TABBED_PANE_TAB_MOUSE_OVER_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.TABBED_PANE_TAB_PRESSED_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.TABBED_PANE_TAB_FOCUSED_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.TABBED_PANE_TAB_FOCUSED_MOUSE_OVER_SELECTED_TEXT_FOREGROUND,
    UiColorKeys.TABBED_PANE_TAB_FOCUSED_PRESSED_SELECTED_TEXT_FOREGROUND,
    UiDefaultKeys.TABBED_PANE_TAB_AREA_DISABLED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_AREA_ENABLED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_AREA_MOUSE_OVER_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_AREA_PRESSED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_DISABLED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_DISABLED_SELECTED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_ENABLED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_MOUSE_OVER_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_PRESSED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_SELECTED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_MOUSE_OVER_SELECTED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_PRESSED_SELECTED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_FOCUSED_SELECTED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_FOCUSED_MOUSE_OVER_SELECTED_BACKGROUND_PAINTER,
    UiDefaultKeys.TABBED_PANE_TAB_FOCUSED_PRESSED_SELECTED_BACKGROUND_PAINTER
  };

  private static final String[] NIMBUS_TINT_OVERRIDE_KEYS = {
    UiColorKeys.CONTROL,
    UiColorKeys.INFO,
    UiColorKeys.NIMBUS_BASE,
    UiColorKeys.NIMBUS_BLUE_GREY,
    UiColorKeys.NIMBUS_BORDER,
    UiColorKeys.NIMBUS_LIGHT_BACKGROUND,
    UiColorKeys.NIMBUS_FOCUS,
    UiColorKeys.NIMBUS_SELECTION_BACKGROUND,
    UiColorKeys.NIMBUS_SELECTED_TEXT,
    UiColorKeys.NIMBUS_DISABLED_TEXT,
    UiColorKeys.NIMBUS_INFO_BLUE,
    UiColorKeys.NIMBUS_ORANGE,
    UiColorKeys.NIMBUS_ALERT_YELLOW,
    UiColorKeys.NIMBUS_RED,
    UiColorKeys.NIMBUS_GREEN,
    UiColorKeys.TEXT_HIGHLIGHT,
    UiColorKeys.TEXT_HIGHLIGHT_TEXT,
    UiColorKeys.TEXT,
    UiColorKeys.TEXT_FOREGROUND,
    UiColorKeys.TEXT_TEXT,
    UiColorKeys.CONTROL_TEXT,
    UiColorKeys.LABEL_FOREGROUND,
    UiColorKeys.PANEL_BACKGROUND,
    UiColorKeys.MENU,
    UiColorKeys.COMPONENT_FOCUS_COLOR,
    UiColorKeys.COMPONENT_ACCENT_COLOR,
    UiColorKeys.COMPONENT_LINK_COLOR,
    UiColorKeys.TEXT_FIELD_BACKGROUND,
    UiColorKeys.TEXT_FIELD_FOREGROUND,
    UiColorKeys.TEXT_AREA_BACKGROUND,
    UiColorKeys.TEXT_AREA_FOREGROUND,
    UiColorKeys.LIST_BACKGROUND,
    UiColorKeys.LIST_FOREGROUND,
    UiColorKeys.TABLE_BACKGROUND,
    UiColorKeys.TABLE_FOREGROUND,
    UiColorKeys.TREE_TEXT_BACKGROUND,
    UiColorKeys.TREE_TEXT_FOREGROUND,
    UiColorKeys.TEXT_COMPONENT_SELECTION_BACKGROUND,
    UiColorKeys.TEXT_COMPONENT_SELECTION_FOREGROUND,
    UiColorKeys.LIST_SELECTION_BACKGROUND,
    UiColorKeys.LIST_SELECTION_FOREGROUND,
    UiColorKeys.TABLE_SELECTION_BACKGROUND,
    UiColorKeys.TABLE_SELECTION_FOREGROUND,
    UiColorKeys.TREE_SELECTION_BACKGROUND,
    UiColorKeys.TREE_SELECTION_FOREGROUND,
    UiColorKeys.MENU_BAR_BACKGROUND,
    UiColorKeys.MENU_BAR_FOREGROUND,
    UiColorKeys.MENU_BACKGROUND,
    UiColorKeys.MENU_FOREGROUND,
    UiColorKeys.MENU_SELECTION_BACKGROUND,
    UiColorKeys.MENU_SELECTION_FOREGROUND,
    UiColorKeys.MENU_ITEM_BACKGROUND,
    UiColorKeys.MENU_ITEM_FOREGROUND,
    UiColorKeys.MENU_ITEM_SELECTION_BACKGROUND,
    UiColorKeys.MENU_ITEM_SELECTION_FOREGROUND,
    UiColorKeys.MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND,
    UiColorKeys.MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_BACKGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_BACKGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND,
    UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_BACKGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_BACKGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND,
    UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND,
    UiColorKeys.POPUP_MENU_BACKGROUND,
    UiColorKeys.POPUP_MENU_FOREGROUND,
    UiColorKeys.POPUP_MENU_BORDER_COLOR,
    UiColorKeys.BUTTON_SELECT,
    UiColorKeys.TOGGLE_BUTTON_SELECT,
    UiColorKeys.TABBED_PANE_FOCUS
  };

  private static final Map<String, NimbusVariantSpec> NIMBUS_VARIANTS =
      Map.ofEntries(
          Map.entry(
              "nimbus-dark",
              new NimbusVariantSpec(true, NimbusThemeOverrideService::applyNimbusDarkOverrides)),
          Map.entry(
              "nimbus-dark-amber",
              new NimbusVariantSpec(
                  true, NimbusThemeOverrideService::applyNimbusDarkAmberOverrides)),
          Map.entry(
              "nimbus-dark-blue",
              new NimbusVariantSpec(
                  true, NimbusThemeOverrideService::applyNimbusDarkBlueOverrides)),
          Map.entry(
              "nimbus-dark-violet",
              new NimbusVariantSpec(
                  true, NimbusThemeOverrideService::applyNimbusDarkVioletOverrides)),
          Map.entry(
              "nimbus-dark-green",
              new NimbusVariantSpec(
                  true, NimbusThemeOverrideService::applyNimbusDarkGreenOverrides)),
          Map.entry(
              "nimbus-dark-orange",
              new NimbusVariantSpec(
                  true, NimbusThemeOverrideService::applyNimbusDarkOrangeOverrides)),
          Map.entry(
              "nimbus-dark-magenta",
              new NimbusVariantSpec(
                  true, NimbusThemeOverrideService::applyNimbusDarkMagentaOverrides)),
          Map.entry(
              "nimbus-orange",
              new NimbusVariantSpec(false, NimbusThemeOverrideService::applyNimbusOrangeOverrides)),
          Map.entry(
              "nimbus-green",
              new NimbusVariantSpec(false, NimbusThemeOverrideService::applyNimbusGreenOverrides)),
          Map.entry(
              "nimbus-blue",
              new NimbusVariantSpec(false, NimbusThemeOverrideService::applyNimbusBlueOverrides)),
          Map.entry(
              "nimbus-violet",
              new NimbusVariantSpec(false, NimbusThemeOverrideService::applyNimbusVioletOverrides)),
          Map.entry(
              "nimbus-magenta",
              new NimbusVariantSpec(
                  false, NimbusThemeOverrideService::applyNimbusMagentaOverrides)),
          Map.entry(
              "nimbus-amber",
              new NimbusVariantSpec(false, NimbusThemeOverrideService::applyNimbusAmberOverrides)));

  private static final Set<String> NIMBUS_DARK_VARIANTS = nimbusVariantIds(true);
  private static final Set<String> NIMBUS_TINT_VARIANTS = nimbusVariantIds(false);

  Set<String> variantIds() {
    return NIMBUS_VARIANTS.keySet();
  }

  boolean isDarkVariant(String themeIdLower) {
    if (themeIdLower == null || themeIdLower.isBlank()) return false;
    return NIMBUS_DARK_VARIANTS.contains(themeIdLower.toLowerCase(Locale.ROOT));
  }

  boolean isTintVariant(String themeIdLower) {
    if (themeIdLower == null || themeIdLower.isBlank()) return false;
    return NIMBUS_TINT_VARIANTS.contains(themeIdLower.toLowerCase(Locale.ROOT));
  }

  boolean applyVariant(String themeIdLower) {
    if (themeIdLower == null || themeIdLower.isBlank()) return false;
    NimbusVariantSpec spec = NIMBUS_VARIANTS.get(themeIdLower.toLowerCase(Locale.ROOT));
    if (spec == null) return false;
    spec.applyOverrides().run();
    applyTextSurfaceStates();
    applyInputStates();
    applyAuxiliaryPainters();
    logNimbusSnapshot("applyVariant", themeIdLower);
    return true;
  }

  void synchronizeLookAndFeelDefaults() {
    // Nimbus compiles styles by iterating MultiUIDefaults.entrySet(), which can contain
    // both the original LAF entry and its developer override. Mirror our values into the
    // new LAF table so iteration order cannot resurrect stock colors or painters.
    Set<String> keys = new HashSet<>(java.util.List.of(NIMBUS_DARK_OVERRIDE_KEYS));
    keys.addAll(java.util.List.of(NIMBUS_TINT_OVERRIDE_KEYS));
    keys.addAll(inputOverrideKeys());
    keys.addAll(auxiliaryPainterKeys());
    for (String prefix : TEXT_SURFACE_PREFIXES) {
      keys.add(prefix + "[Enabled].background");
      keys.add(prefix + "[Enabled].textForeground");
      keys.add(prefix + "[Selected].textForeground");
      keys.add(prefix + "[Selected].textBackground");
    }
    for (String prefix : SELECTION_STATE_PREFIXES) {
      keys.add(prefix + ".textForeground");
      keys.add(prefix + ".textBackground");
    }
    var defaults = UIManager.getLookAndFeelDefaults();
    for (String key : keys) {
      Object value = UIManager.get(key);
      if (value != null) {
        defaults.put(key, value);
        // The new LAF may already have compiled a prefix during installation. Force a
        // change event even when the developer override equals the previous value.
        UIManager.put(key, null);
        UIManager.put(key, value);
      }
    }
  }

  void clearDarkOverrides() {
    clearNimbusDarkOverrides();
  }

  void clearTintOverrides() {
    clearNimbusTintOverrides();
  }

  private static Set<String> nimbusVariantIds(boolean darkVariants) {
    Set<String> out = new HashSet<>();
    NIMBUS_VARIANTS.forEach(
        (id, spec) -> {
          if (spec.darkVariant() == darkVariants) out.add(id);
        });
    return Set.copyOf(out);
  }

  private static void applyNimbusDarkOverrides() {
    ColorUIResource control = uiColor(0x23, 0x27, 0x2D);
    ColorUIResource bg = uiColor(0x1F, 0x23, 0x29);
    ColorUIResource text = uiColor(0xE6, 0xEA, 0xF0);
    ColorUIResource disabledText = uiColor(0x8A, 0x92, 0x9D);
    // Keep Nimbus Dark's accent/focus more subdued than FlatLaf defaults.
    ColorUIResource focus = uiColor(0x58, 0x78, 0xA2);
    ColorUIResource link = uiColor(0x89, 0xA7, 0xCF);
    ColorUIResource selectionBg = uiColor(0x33, 0x45, 0x59);
    ColorUIResource selectionFg = uiColor(0xF3, 0xF7, 0xFD);
    ColorUIResource menuBg = uiColor(0x23, 0x27, 0x2D);
    ColorUIResource menuSelectionBg = uiColor(0x34, 0x40, 0x4E);
    ColorUIResource border = uiColor(0x45, 0x50, 0x5E);
    ColorUIResource separator = uiColor(0x39, 0x42, 0x4E);
    ColorUIResource warning = uiColor(0xD1, 0xA7, 0x61);
    ColorUIResource error = uiColor(0xC8, 0x6D, 0x6D);

    UIManager.put(UiColorKeys.CONTROL, control);
    UIManager.put(UiColorKeys.INFO, control);
    UIManager.put(UiColorKeys.NIMBUS_BASE, uiColor(0x1A, 0x24, 0x31));
    UIManager.put(UiColorKeys.NIMBUS_BLUE_GREY, uiColor(0x2A, 0x31, 0x3A));
    UIManager.put(UiColorKeys.NIMBUS_BORDER, border);
    UIManager.put(UiColorKeys.NIMBUS_LIGHT_BACKGROUND, bg);
    UIManager.put(UiColorKeys.NIMBUS_FOCUS, focus);
    UIManager.put(UiColorKeys.NIMBUS_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.NIMBUS_SELECTED_TEXT, selectionFg);
    UIManager.put(UiColorKeys.NIMBUS_DISABLED_TEXT, disabledText);
    UIManager.put(UiColorKeys.NIMBUS_INFO_BLUE, uiColor(0x53, 0x6C, 0x85));
    UIManager.put(UiColorKeys.NIMBUS_ALERT_YELLOW, warning);
    UIManager.put(UiColorKeys.NIMBUS_ORANGE, uiColor(0xC7, 0x84, 0x49));
    UIManager.put(UiColorKeys.NIMBUS_RED, error);
    UIManager.put(UiColorKeys.NIMBUS_GREEN, uiColor(0x6F, 0xAD, 0x7A));
    UIManager.put(UiColorKeys.TEXT_HIGHLIGHT, selectionBg);
    UIManager.put(UiColorKeys.TEXT_HIGHLIGHT_TEXT, selectionFg);
    UIManager.put(UiColorKeys.TEXT, text);
    UIManager.put(UiColorKeys.TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.TEXT_TEXT, text);
    UIManager.put(UiColorKeys.CONTROL_TEXT, text);
    UIManager.put(UiColorKeys.CONTROL_DK_SHADOW, uiColor(0x1B, 0x1F, 0x24));
    UIManager.put(UiColorKeys.CONTROL_SHADOW, uiColor(0x31, 0x37, 0x41));
    UIManager.put(UiColorKeys.CONTROL_LT_HIGHLIGHT, uiColor(0x4A, 0x52, 0x5F));
    UIManager.put(UiColorKeys.LABEL_FOREGROUND, text);
    UIManager.put(UiColorKeys.PANEL_BACKGROUND, control);
    UIManager.put(UiColorKeys.MENU, menuBg);
    UIManager.put(UiColorKeys.COMPONENT_FOCUS_COLOR, focus);
    UIManager.put(UiColorKeys.COMPONENT_ACCENT_COLOR, focus);
    UIManager.put(UiColorKeys.COMPONENT_LINK_COLOR, link);
    UIManager.put(UiColorKeys.COMPONENT_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.COMPONENT_WARNING_COLOR, warning);
    UIManager.put(UiColorKeys.COMPONENT_WARNING_OUTLINE_COLOR, warning);
    UIManager.put(UiColorKeys.COMPONENT_WARNING_BORDER_COLOR, warning);
    UIManager.put(UiColorKeys.COMPONENT_WARNING_FOCUSED_BORDER_COLOR, warning);
    UIManager.put(UiColorKeys.COMPONENT_WARNING_FOCUS_COLOR, warning);
    UIManager.put(UiColorKeys.COMPONENT_ERROR_COLOR, error);
    UIManager.put(UiColorKeys.COMPONENT_ERROR_OUTLINE_COLOR, error);
    UIManager.put(UiColorKeys.COMPONENT_ERROR_BORDER_COLOR, error);
    UIManager.put(UiColorKeys.COMPONENT_ERROR_FOCUSED_BORDER_COLOR, error);
    UIManager.put(UiColorKeys.COMPONENT_ERROR_FOCUS_COLOR, error);

    UIManager.put(UiColorKeys.TEXT_FIELD_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TEXT_FIELD_FOREGROUND, text);
    UIManager.put(UiColorKeys.TEXT_FIELD_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.TEXT_AREA_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TEXT_AREA_FOREGROUND, text);
    UIManager.put(UiColorKeys.TEXT_COMPONENT_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TEXT_COMPONENT_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.COMBO_BOX_BACKGROUND, bg);
    UIManager.put(UiColorKeys.COMBO_BOX_FOREGROUND, text);
    UIManager.put(UiColorKeys.COMBO_BOX_DISABLED, control);
    UIManager.put(UiColorKeys.COMBO_BOX_DISABLED_TEXT, disabledText);
    UIManager.put(UiColorKeys.COMBO_BOX_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.COMBO_BOX_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiDefaultKeys.COMBO_BOX_RENDERER_USE_LIST_COLORS, Boolean.TRUE);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_BACKGROUND, bg);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_SELECTED_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_BACKGROUND, bg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_SELECTED_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.COMBO_BOX_TEXT_FIELD_BACKGROUND, bg);
    UIManager.put(UiColorKeys.COMBO_BOX_TEXT_FIELD_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.COMBO_BOX_TEXT_FIELD_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.COMBO_BOX_ARROW_BUTTON_BACKGROUND, control);
    UIManager.put(UiColorKeys.COMBO_BOX_ARROW_BUTTON_FOREGROUND, text);
    UIManager.put(UiColorKeys.BUTTON_BACKGROUND, control);
    UIManager.put(UiColorKeys.BUTTON_FOREGROUND, text);
    UIManager.put(UiColorKeys.BUTTON_DISABLED_TEXT, disabledText);
    UIManager.put(UiColorKeys.BUTTON_SELECT, selectionBg);
    UIManager.put(UiColorKeys.BUTTON_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.BUTTON_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.BUTTON_PRESSED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.BUTTON_DEFAULT_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.BUTTON_DEFAULT_PRESSED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.TOGGLE_BUTTON_BACKGROUND, control);
    UIManager.put(UiColorKeys.TOGGLE_BUTTON_FOREGROUND, text);
    UIManager.put(UiColorKeys.TOGGLE_BUTTON_DISABLED_TEXT, disabledText);
    UIManager.put(UiColorKeys.TOGGLE_BUTTON_SELECT, selectionBg);
    UIManager.put(UiColorKeys.CHECK_BOX_BACKGROUND, control);
    UIManager.put(UiColorKeys.CHECK_BOX_FOREGROUND, text);
    UIManager.put(UiColorKeys.CHECK_BOX_DISABLED_TEXT, disabledText);
    UIManager.put(UiColorKeys.RADIO_BUTTON_BACKGROUND, control);
    UIManager.put(UiColorKeys.RADIO_BUTTON_FOREGROUND, text);
    UIManager.put(UiColorKeys.RADIO_BUTTON_DISABLED_TEXT, disabledText);

    UIManager.put(UiColorKeys.LIST_BACKGROUND, bg);
    UIManager.put(UiColorKeys.LIST_FOREGROUND, text);
    UIManager.put(UiColorKeys.LIST_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.LIST_SELECTION_FOREGROUND, selectionFg);

    UIManager.put(UiColorKeys.TABLE_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TABLE_FOREGROUND, text);
    UIManager.put(UiColorKeys.TABLE_GRID_COLOR, separator);
    UIManager.put(UiColorKeys.TABLE_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TABLE_SELECTION_FOREGROUND, selectionFg);

    UIManager.put(UiColorKeys.TREE_TEXT_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TREE_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.TREE_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TREE_SELECTION_FOREGROUND, selectionFg);

    UIManager.put(UiColorKeys.MENU_BAR_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.MENU_BAR_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_BAR_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.MENU_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.MENU_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.MENU_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_DISABLED_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.MENU_BAR_MENU_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_BAR_MENU_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.MENU_BAR_MENU_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.MENU_ENABLED_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ITEM_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.MENU_ITEM_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_ITEM_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ITEM_DISABLED_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.MENU_ITEM_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_ITEM_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.MENU_ITEM_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_FOREGROUND, text);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_DISABLED_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_MOUSE_OVER_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(
        UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(
        UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_FOREGROUND, text);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_DISABLED_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(
        UiColorKeys.RADIO_BUTTON_MENU_ITEM_MOUSE_OVER_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(
        UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(
        UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.POPUP_MENU_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.POPUP_MENU_FOREGROUND, text);
    UIManager.put(UiColorKeys.POPUP_MENU_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.POPUP_MENU_SEPARATOR_FOREGROUND, separator);
    UIManager.put(UiColorKeys.POPUP_MENU_SEPARATOR_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.SEPARATOR_FOREGROUND, separator);
    UIManager.put(UiColorKeys.SEPARATOR_BACKGROUND, control);
    UIManager.put(UiColorKeys.TOOL_BAR_SEPARATOR_COLOR, separator);
    UIManager.put(UiColorKeys.SPLIT_PANE_BACKGROUND, control);
    UIManager.put(UiColorKeys.SPLIT_PANE_FOREGROUND, separator);
    UIManager.put(UiColorKeys.SPLIT_PANE_DIVIDER_DRAGGING_COLOR, uiColor(0x50, 0x5F, 0x74));
    UIManager.put(UiColorKeys.SCROLL_PANE_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.TABBED_PANE_FOCUS, focus);

    // Add nuanced per-control surfaces so Nimbus Dark feels less flat.
    applyNimbusDarkComponentShades(
        control,
        bg,
        menuBg,
        uiColor(0x1A, 0x24, 0x31),
        uiColor(0x2A, 0x31, 0x3A),
        border,
        separator,
        text,
        disabledText,
        selectionBg,
        selectionFg,
        focus,
        uiColor(0x50, 0x5F, 0x74));
  }

  private static void applyNimbusDarkAmberOverrides() {
    applyNimbusDarkAccentOverrides(
        uiColor(0x38, 0x29, 0x15),
        uiColor(0x39, 0x31, 0x24),
        uiColor(0xCC, 0x8E, 0x33),
        uiColor(0xEB, 0xB8, 0x6D),
        uiColor(0x5E, 0x42, 0x1C),
        uiColor(0x6C, 0x4C, 0x20),
        uiColor(0xAA, 0x8A, 0x5F),
        uiColor(0xCC, 0x8E, 0x33),
        uiColor(0xD3, 0xA4, 0x5B),
        uiColor(0xC7, 0x73, 0x63),
        uiColor(0x73, 0xAB, 0x7F),
        uiColor(0x7B, 0x64, 0x40));
  }

  private static void applyNimbusDarkBlueOverrides() {
    applyNimbusDarkAccentOverrides(
        uiColor(0x1A, 0x28, 0x3E),
        uiColor(0x2A, 0x36, 0x49),
        uiColor(0x5E, 0x8F, 0xD9),
        uiColor(0x90, 0xB7, 0xF2),
        uiColor(0x2E, 0x49, 0x70),
        uiColor(0x35, 0x53, 0x7E),
        uiColor(0x72, 0x93, 0xBB),
        uiColor(0x6A, 0x96, 0xD8),
        uiColor(0xBE, 0xA5, 0x6B),
        uiColor(0xB7, 0x70, 0x67),
        uiColor(0x73, 0xA9, 0x82),
        uiColor(0x55, 0x6B, 0x87));
  }

  private static void applyNimbusDarkVioletOverrides() {
    applyNimbusDarkAccentOverrides(
        uiColor(0x2C, 0x24, 0x43),
        uiColor(0x34, 0x2E, 0x4A),
        uiColor(0x8F, 0x72, 0xD9),
        uiColor(0xB8, 0xA0, 0xF4),
        uiColor(0x49, 0x36, 0x70),
        uiColor(0x53, 0x3D, 0x7D),
        uiColor(0x7A, 0x7F, 0xC0),
        uiColor(0xA3, 0x78, 0xC5),
        uiColor(0xC0, 0xA5, 0x73),
        uiColor(0xC0, 0x72, 0x8B),
        uiColor(0x74, 0xA9, 0x89),
        uiColor(0x6A, 0x59, 0x91));
  }

  private static void applyNimbusDarkGreenOverrides() {
    applyNimbusDarkAccentOverrides(
        uiColor(0x1F, 0x33, 0x27),
        uiColor(0x2A, 0x3F, 0x31),
        uiColor(0x57, 0xB8, 0x79),
        uiColor(0x86, 0xD6, 0xA2),
        uiColor(0x24, 0x4D, 0x34),
        uiColor(0x2A, 0x5A, 0x3D),
        uiColor(0x6E, 0xA3, 0x88),
        uiColor(0x72, 0xB7, 0x8B),
        uiColor(0xA3, 0xD3, 0xA4),
        uiColor(0xB9, 0x6E, 0x68),
        uiColor(0x57, 0xB8, 0x79),
        uiColor(0x45, 0x6A, 0x55));
  }

  private static void applyNimbusDarkOrangeOverrides() {
    applyNimbusDarkAccentOverrides(
        uiColor(0x3D, 0x24, 0x18),
        uiColor(0x4A, 0x2F, 0x24),
        uiColor(0xE0, 0x7A, 0x2C),
        uiColor(0xF0, 0xA7, 0x66),
        uiColor(0x70, 0x40, 0x22),
        uiColor(0x7E, 0x4A, 0x24),
        uiColor(0xB0, 0x83, 0x5C),
        uiColor(0xE0, 0x7A, 0x2C),
        uiColor(0xE8, 0xA9, 0x5A),
        uiColor(0xC5, 0x6E, 0x5F),
        uiColor(0x77, 0xAA, 0x82),
        uiColor(0x89, 0x61, 0x46));
  }

  private static void applyNimbusDarkMagentaOverrides() {
    applyNimbusDarkAccentOverrides(
        uiColor(0x35, 0x1F, 0x33),
        uiColor(0x43, 0x27, 0x44),
        uiColor(0xC4, 0x6B, 0xD1),
        uiColor(0xE1, 0x9B, 0xE8),
        uiColor(0x5E, 0x2F, 0x62),
        uiColor(0x6B, 0x36, 0x70),
        uiColor(0x9C, 0x7C, 0xAD),
        uiColor(0xC2, 0x6F, 0xC9),
        uiColor(0xD7, 0x9A, 0xE0),
        uiColor(0xC2, 0x76, 0x90),
        uiColor(0x78, 0xAA, 0x86),
        uiColor(0x7A, 0x54, 0x7C));
  }

  private static void applyNimbusDarkAccentOverrides(
      ColorUIResource nimbusBase,
      ColorUIResource nimbusBlueGrey,
      ColorUIResource focus,
      ColorUIResource link,
      ColorUIResource selectionBg,
      ColorUIResource menuSelectionBg,
      ColorUIResource nimbusInfoBlue,
      ColorUIResource nimbusOrange,
      ColorUIResource nimbusAlertYellow,
      ColorUIResource nimbusRed,
      ColorUIResource nimbusGreen,
      ColorUIResource splitPaneDraggingColor) {
    applyNimbusDarkOverrides();

    // Tint the dark neutral surfaces for each Nimbus dark accent variant.
    // Without this, all dark variants inherit the same gray base from applyNimbusDarkOverrides().
    ColorUIResource control =
        toUiResource(mix(uiColor(0x27, 0x2B, 0x30), mix(nimbusBlueGrey, nimbusBase, 0.35), 0.45));
    ColorUIResource bg = new ColorUIResource(darken(control, 0.10));
    ColorUIResource menuBg = new ColorUIResource(darken(control, 0.05));
    ColorUIResource border = new ColorUIResource(lighten(control, 0.10));
    ColorUIResource separator = new ColorUIResource(lighten(control, 0.05));

    // Chef's-kiss pass: tint the supporting neutrals so the whole theme reads as one palette.
    ColorUIResource controlShadow = new ColorUIResource(darken(control, 0.20));
    ColorUIResource controlDkShadow = new ColorUIResource(darken(control, 0.34));
    ColorUIResource controlLtHighlight = new ColorUIResource(lighten(control, 0.12));
    ColorUIResource viewportBg = bg;
    ColorUIResource tableHeaderBg = new ColorUIResource(lighten(control, 0.04));
    ColorUIResource tooltipBg = new ColorUIResource(lighten(menuBg, 0.04));

    UIManager.put(UiColorKeys.CONTROL, control);
    UIManager.put(UiColorKeys.INFO, control);
    UIManager.put(UiColorKeys.PANEL_BACKGROUND, control);
    UIManager.put(UiColorKeys.MENU, menuBg);

    UIManager.put(UiColorKeys.NIMBUS_LIGHT_BACKGROUND, bg);
    UIManager.put(UiColorKeys.NIMBUS_BORDER, border);

    UIManager.put(UiColorKeys.COMPONENT_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.CONTROL_SHADOW, controlShadow);
    UIManager.put(UiColorKeys.CONTROL_DK_SHADOW, controlDkShadow);
    UIManager.put(UiColorKeys.CONTROL_LT_HIGHLIGHT, controlLtHighlight);

    UIManager.put(UiColorKeys.TEXT_FIELD_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TEXT_FIELD_BORDER_COLOR, border);

    UIManager.put(UiColorKeys.PASSWORD_FIELD_BACKGROUND, bg);
    UIManager.put(UiColorKeys.PASSWORD_FIELD_BORDER_COLOR, border);

    UIManager.put(UiColorKeys.FORMATTED_TEXT_FIELD_BACKGROUND, bg);
    UIManager.put(UiColorKeys.FORMATTED_TEXT_FIELD_BORDER_COLOR, border);

    UIManager.put(UiColorKeys.TEXT_AREA_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TEXT_PANE_BACKGROUND, bg);
    UIManager.put(UiColorKeys.EDITOR_PANE_BACKGROUND, bg);

    UIManager.put(UiColorKeys.LIST_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TREE_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TREE_TEXT_BACKGROUND, bg);

    UIManager.put(UiColorKeys.TABLE_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TABLE_GRID_COLOR, separator);
    UIManager.put(UiColorKeys.TABLE_HEADER_BACKGROUND, tableHeaderBg);
    UIManager.put(UiColorKeys.TABLE_HEADER_RENDERER_BACKGROUND, tableHeaderBg);

    UIManager.put(UiColorKeys.VIEWPORT_BACKGROUND, viewportBg);

    UIManager.put(UiColorKeys.COMBO_BOX_BACKGROUND, bg);
    UIManager.put(UiColorKeys.COMBO_BOX_DISABLED, control);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_BACKGROUND, bg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_BACKGROUND, bg);
    UIManager.put(UiColorKeys.COMBO_BOX_TEXT_FIELD_BACKGROUND, bg);
    UIManager.put(UiColorKeys.COMBO_BOX_ARROW_BUTTON_BACKGROUND, control);

    UIManager.put(UiColorKeys.SPINNER_BACKGROUND, bg);
    UIManager.put(UiColorKeys.SPINNER_FORMATTED_TEXT_FIELD_BACKGROUND, bg);

    UIManager.put(UiColorKeys.BUTTON_BACKGROUND, control);
    UIManager.put(UiColorKeys.TOGGLE_BUTTON_BACKGROUND, control);
    UIManager.put(UiColorKeys.CHECK_BOX_BACKGROUND, control);
    UIManager.put(UiColorKeys.RADIO_BUTTON_BACKGROUND, control);

    UIManager.put(UiColorKeys.MENU_BAR_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.MENU_BAR_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.MENU_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.MENU_ITEM_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.POPUP_MENU_BACKGROUND, menuBg);

    UIManager.put(UiColorKeys.POPUP_MENU_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.POPUP_MENU_SEPARATOR_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.POPUP_MENU_SEPARATOR_FOREGROUND, separator);
    UIManager.put(UiColorKeys.SCROLL_PANE_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.SEPARATOR_FOREGROUND, separator);
    UIManager.put(UiColorKeys.SEPARATOR_BACKGROUND, control);
    UIManager.put(UiColorKeys.TOOL_BAR_SEPARATOR_COLOR, separator);

    UIManager.put(UiColorKeys.TOOL_TIP_BACKGROUND, tooltipBg);

    UIManager.put(UiColorKeys.SPLIT_PANE_BACKGROUND, control);
    UIManager.put(UiColorKeys.SPLIT_PANE_FOREGROUND, separator);

    ColorUIResource selectionFg = uiColor(0xF3, 0xF7, 0xFD);
    UIManager.put(UiColorKeys.NIMBUS_BASE, nimbusBase);
    UIManager.put(UiColorKeys.NIMBUS_BLUE_GREY, nimbusBlueGrey);
    UIManager.put(UiColorKeys.NIMBUS_FOCUS, focus);
    UIManager.put(UiColorKeys.NIMBUS_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.NIMBUS_INFO_BLUE, nimbusInfoBlue);
    UIManager.put(UiColorKeys.NIMBUS_ORANGE, nimbusOrange);
    UIManager.put(UiColorKeys.NIMBUS_ALERT_YELLOW, nimbusAlertYellow);
    UIManager.put(UiColorKeys.NIMBUS_RED, nimbusRed);
    UIManager.put(UiColorKeys.NIMBUS_GREEN, nimbusGreen);
    UIManager.put(UiColorKeys.COMPONENT_FOCUS_COLOR, focus);
    UIManager.put(UiColorKeys.COMPONENT_ACCENT_COLOR, focus);
    UIManager.put(UiColorKeys.COMPONENT_LINK_COLOR, link);
    UIManager.put(UiColorKeys.TEXT_HIGHLIGHT, selectionBg);
    UIManager.put(UiColorKeys.TEXT_HIGHLIGHT_TEXT, selectionFg);
    UIManager.put(UiColorKeys.TEXT_COMPONENT_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TEXT_COMPONENT_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.LIST_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.LIST_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.TABLE_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TABLE_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.TREE_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TREE_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.MENU_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ITEM_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.BUTTON_SELECT, selectionBg);
    UIManager.put(UiColorKeys.TOGGLE_BUTTON_SELECT, selectionBg);

    applyNimbusDarkComponentShades(
        control,
        bg,
        menuBg,
        nimbusBase,
        nimbusBlueGrey,
        border,
        separator,
        uiColor(0xE6, 0xEA, 0xF0),
        uiColor(0x8A, 0x92, 0x9D),
        selectionBg,
        selectionFg,
        focus,
        splitPaneDraggingColor);

    // Match text on newly tinted surfaces.
    Object labelText = UIManager.get(UiColorKeys.LABEL_FOREGROUND);
    if (labelText != null) {
      UIManager.put(UiColorKeys.PASSWORD_FIELD_FOREGROUND, labelText);
      UIManager.put(UiColorKeys.FORMATTED_TEXT_FIELD_FOREGROUND, labelText);
      UIManager.put(UiColorKeys.TEXT_PANE_FOREGROUND, labelText);
      UIManager.put(UiColorKeys.EDITOR_PANE_FOREGROUND, labelText);
      UIManager.put(UiColorKeys.TABLE_HEADER_FOREGROUND, labelText);
      UIManager.put(UiColorKeys.TABLE_HEADER_RENDERER_FOREGROUND, labelText);
      UIManager.put(UiColorKeys.TOOL_TIP_FOREGROUND, labelText);
      UIManager.put(UiColorKeys.VIEWPORT_FOREGROUND, labelText);
      UIManager.put(UiColorKeys.SPINNER_FOREGROUND, labelText);
      UIManager.put(UiColorKeys.SPINNER_FORMATTED_TEXT_FIELD_FOREGROUND, labelText);
    }

    UIManager.put(UiColorKeys.TABBED_PANE_FOCUS, focus);
    UIManager.put(UiColorKeys.SPLIT_PANE_DIVIDER_DRAGGING_COLOR, splitPaneDraggingColor);
  }

  private static void applyNimbusDarkComponentShades(
      ColorUIResource control,
      ColorUIResource bg,
      ColorUIResource menuBg,
      ColorUIResource nimbusBase,
      ColorUIResource nimbusBlueGrey,
      ColorUIResource border,
      ColorUIResource separator,
      ColorUIResource text,
      ColorUIResource disabledText,
      ColorUIResource selectionBg,
      ColorUIResource selectionFg,
      ColorUIResource focus,
      ColorUIResource splitPaneDraggingColor) {
    Color panelBase = mix(control, bg, 0.42);
    ColorUIResource panelBg = toUiResource(panelBase);
    ColorUIResource buttonBg = toUiResource(lighten(control, 0.07));
    ColorUIResource toggleBg = toUiResource(lighten(control, 0.04));
    ColorUIResource checkBg = toUiResource(lighten(control, 0.02));

    // Keep text input surfaces visibly tinted and clearly separated from panel chrome.
    // Bias more strongly toward accent/base hues so dark variants don't collapse into neutral gray.
    Color fieldTint = mix(mix(nimbusBase, focus, 0.56), nimbusBlueGrey, 0.22);
    Color fieldBase = mix(bg, fieldTint, 0.62);
    Color fieldSurface =
        ThemeColorUtils.ensureContrastAgainstBackground(lighten(fieldBase, 0.07), panelBase, 1.22);
    ColorUIResource fieldBg =
        toUiResource(ThemeColorUtils.ensureContrastAgainstBackground(fieldSurface, text, 5.0));

    Color areaTint = mix(mix(nimbusBlueGrey, focus, 0.46), nimbusBase, 0.18);
    Color areaBase = mix(bg, areaTint, 0.52);
    Color areaSurface =
        ThemeColorUtils.ensureContrastAgainstBackground(lighten(areaBase, 0.05), panelBase, 1.16);
    ColorUIResource areaBg =
        toUiResource(ThemeColorUtils.ensureContrastAgainstBackground(areaSurface, text, 5.0));

    ColorUIResource listBg = toUiResource(darken(bg, 0.015));
    ColorUIResource tableBg = toUiResource(darken(bg, 0.02));
    ColorUIResource treeBg = toUiResource(darken(bg, 0.01));
    ColorUIResource viewportBg = tableBg;

    ColorUIResource menuBarBg = toUiResource(lighten(mix(menuBg, nimbusBase, 0.24), 0.05));
    ColorUIResource popupBg = toUiResource(lighten(mix(menuBg, nimbusBase, 0.18), 0.035));
    ColorUIResource tooltipBg = toUiResource(lighten(menuBg, 0.07));
    ColorUIResource comboArrowBg = toUiResource(lighten(control, 0.05));
    ColorUIResource splitPaneBg = toUiResource(darken(control, 0.03));

    ColorUIResource tableHeaderBg = toUiResource(lighten(control, 0.09));
    ColorUIResource controlShadow = toUiResource(darken(control, 0.22));
    ColorUIResource controlDkShadow = toUiResource(darken(control, 0.36));
    ColorUIResource controlLtHighlight = toUiResource(lighten(control, 0.14));

    UIManager.put(UiColorKeys.CONTROL_SHADOW, controlShadow);
    UIManager.put(UiColorKeys.CONTROL_DK_SHADOW, controlDkShadow);
    UIManager.put(UiColorKeys.CONTROL_LT_HIGHLIGHT, controlLtHighlight);

    UIManager.put(UiColorKeys.PANEL_BACKGROUND, panelBg);

    UIManager.put(UiColorKeys.BUTTON_BACKGROUND, buttonBg);
    UIManager.put(UiColorKeys.BUTTON_FOREGROUND, text);
    UIManager.put(UiColorKeys.BUTTON_DISABLED_TEXT, disabledText);
    UIManager.put(UiColorKeys.BUTTON_SELECT, selectionBg);
    UIManager.put(UiColorKeys.BUTTON_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.BUTTON_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.BUTTON_PRESSED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.BUTTON_DEFAULT_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.BUTTON_DEFAULT_PRESSED_TEXT_FOREGROUND, selectionFg);

    UIManager.put(UiColorKeys.TOGGLE_BUTTON_BACKGROUND, toggleBg);
    UIManager.put(UiColorKeys.TOGGLE_BUTTON_FOREGROUND, text);
    UIManager.put(UiColorKeys.TOGGLE_BUTTON_DISABLED_TEXT, disabledText);
    UIManager.put(UiColorKeys.TOGGLE_BUTTON_SELECT, selectionBg);

    UIManager.put(UiColorKeys.CHECK_BOX_BACKGROUND, checkBg);
    UIManager.put(UiColorKeys.CHECK_BOX_FOREGROUND, text);
    UIManager.put(UiColorKeys.CHECK_BOX_DISABLED_TEXT, disabledText);
    UIManager.put(UiColorKeys.RADIO_BUTTON_BACKGROUND, checkBg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_FOREGROUND, text);
    UIManager.put(UiColorKeys.RADIO_BUTTON_DISABLED_TEXT, disabledText);

    UIManager.put(UiColorKeys.TEXT_FIELD_BACKGROUND, fieldBg);
    UIManager.put(UiColorKeys.TEXT_FIELD_FOREGROUND, text);
    UIManager.put(UiColorKeys.TEXT_FIELD_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.TEXT_COMPONENT_BACKGROUND, fieldBg);
    UIManager.put(
        UiColorKeys.TEXT_FIELD_INACTIVE_BACKGROUND, toUiResource(mix(fieldBg, panelBg, 0.55)));
    UIManager.put(UiColorKeys.PASSWORD_FIELD_BACKGROUND, fieldBg);
    UIManager.put(UiColorKeys.PASSWORD_FIELD_FOREGROUND, text);
    UIManager.put(UiColorKeys.PASSWORD_FIELD_BORDER_COLOR, border);
    UIManager.put(
        UiColorKeys.PASSWORD_FIELD_INACTIVE_BACKGROUND, toUiResource(mix(fieldBg, panelBg, 0.55)));
    UIManager.put(UiColorKeys.FORMATTED_TEXT_FIELD_BACKGROUND, fieldBg);
    UIManager.put(UiColorKeys.FORMATTED_TEXT_FIELD_FOREGROUND, text);
    UIManager.put(UiColorKeys.FORMATTED_TEXT_FIELD_BORDER_COLOR, border);
    UIManager.put(
        UiColorKeys.FORMATTED_TEXT_FIELD_INACTIVE_BACKGROUND,
        toUiResource(mix(fieldBg, panelBg, 0.55)));

    UIManager.put(UiColorKeys.TEXT_AREA_BACKGROUND, areaBg);
    UIManager.put(UiColorKeys.TEXT_AREA_FOREGROUND, text);
    UIManager.put(UiColorKeys.TEXT_PANE_BACKGROUND, areaBg);
    UIManager.put(UiColorKeys.TEXT_PANE_FOREGROUND, text);
    UIManager.put(UiColorKeys.EDITOR_PANE_BACKGROUND, areaBg);
    UIManager.put(UiColorKeys.EDITOR_PANE_FOREGROUND, text);

    UIManager.put(UiColorKeys.COMBO_BOX_BACKGROUND, fieldBg);
    UIManager.put(UiColorKeys.COMBO_BOX_FOREGROUND, text);
    UIManager.put(UiColorKeys.COMBO_BOX_DISABLED, panelBg);
    UIManager.put(UiColorKeys.COMBO_BOX_DISABLED_TEXT, disabledText);
    UIManager.put(UiColorKeys.COMBO_BOX_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.COMBO_BOX_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_BACKGROUND, fieldBg);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_SELECTED_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_BACKGROUND, fieldBg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_SELECTED_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.COMBO_BOX_TEXT_FIELD_BACKGROUND, fieldBg);
    UIManager.put(UiColorKeys.COMBO_BOX_TEXT_FIELD_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.COMBO_BOX_TEXT_FIELD_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.COMBO_BOX_ARROW_BUTTON_BACKGROUND, comboArrowBg);
    UIManager.put(UiColorKeys.COMBO_BOX_ARROW_BUTTON_FOREGROUND, text);
    applyNimbusComboBox(
        fieldBg,
        comboArrowBg,
        panelBg,
        text,
        disabledText,
        selectionBg,
        selectionFg,
        focus,
        border);

    UIManager.put(UiColorKeys.LIST_BACKGROUND, listBg);
    UIManager.put(UiColorKeys.LIST_FOREGROUND, text);
    UIManager.put(UiColorKeys.LIST_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.LIST_SELECTION_FOREGROUND, selectionFg);

    UIManager.put(UiColorKeys.TABLE_BACKGROUND, tableBg);
    UIManager.put(UiColorKeys.TABLE_FOREGROUND, text);
    UIManager.put(UiColorKeys.TABLE_GRID_COLOR, separator);
    UIManager.put(UiColorKeys.TABLE_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TABLE_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.TABLE_HEADER_BACKGROUND, tableHeaderBg);
    UIManager.put(UiColorKeys.TABLE_HEADER_FOREGROUND, text);
    UIManager.put(UiColorKeys.TABLE_HEADER_RENDERER_BACKGROUND, tableHeaderBg);
    UIManager.put(UiColorKeys.TABLE_HEADER_RENDERER_FOREGROUND, text);

    UIManager.put(UiColorKeys.TREE_BACKGROUND, treeBg);
    UIManager.put(UiColorKeys.TREE_TEXT_BACKGROUND, treeBg);
    UIManager.put(UiColorKeys.TREE_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.TREE_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TREE_SELECTION_FOREGROUND, selectionFg);

    UIManager.put(UiColorKeys.VIEWPORT_BACKGROUND, viewportBg);
    UIManager.put(UiColorKeys.VIEWPORT_FOREGROUND, text);
    UIManager.put(UiColorKeys.SPINNER_BACKGROUND, fieldBg);
    UIManager.put(UiColorKeys.SPINNER_FOREGROUND, text);
    UIManager.put(UiColorKeys.SPINNER_FORMATTED_TEXT_FIELD_BACKGROUND, fieldBg);
    UIManager.put(UiColorKeys.SPINNER_FORMATTED_TEXT_FIELD_FOREGROUND, text);

    UIManager.put(UiColorKeys.MENU_BAR_BACKGROUND, menuBarBg);
    UIManager.put(UiColorKeys.MENU_BAR_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_BAR_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.MENU_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.MENU_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_SELECTION_BACKGROUND, toUiResource(lighten(menuBg, 0.08)));
    UIManager.put(UiColorKeys.MENU_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_DISABLED_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.MENU_BAR_MENU_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_BAR_MENU_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.MENU_BAR_MENU_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.MENU_ENABLED_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);

    UIManager.put(UiColorKeys.MENU_ITEM_BACKGROUND, popupBg);
    UIManager.put(UiColorKeys.MENU_ITEM_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_ITEM_SELECTION_BACKGROUND, toUiResource(lighten(menuBg, 0.08)));
    UIManager.put(UiColorKeys.MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ITEM_DISABLED_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.MENU_ITEM_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_ITEM_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.MENU_ITEM_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND, disabledText);

    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_BACKGROUND, popupBg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_FOREGROUND, text);
    UIManager.put(
        UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_BACKGROUND, toUiResource(lighten(menuBg, 0.08)));
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_DISABLED_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_MOUSE_OVER_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(
        UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(
        UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND, disabledText);

    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_BACKGROUND, popupBg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_FOREGROUND, text);
    UIManager.put(
        UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_BACKGROUND,
        toUiResource(lighten(menuBg, 0.08)));
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_DISABLED_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(
        UiColorKeys.RADIO_BUTTON_MENU_ITEM_MOUSE_OVER_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(
        UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(
        UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND, disabledText);

    UIManager.put(UiColorKeys.POPUP_MENU_BACKGROUND, popupBg);
    UIManager.put(UiColorKeys.POPUP_MENU_FOREGROUND, text);
    UIManager.put(UiColorKeys.POPUP_MENU_BORDER_COLOR, border);
    UIManager.put(UiDefaultKeys.POPUP_MENU_OPAQUE, Boolean.TRUE);
    UIManager.put(UiColorKeys.POPUP_MENU_SEPARATOR_FOREGROUND, separator);
    UIManager.put(UiColorKeys.POPUP_MENU_SEPARATOR_BACKGROUND, popupBg);
    UIManager.put(UiDefaultKeys.MENU_ITEM_OPAQUE, Boolean.TRUE);
    UIManager.put(UiDefaultKeys.CHECK_BOX_MENU_ITEM_OPAQUE, Boolean.TRUE);
    UIManager.put(UiDefaultKeys.RADIO_BUTTON_MENU_ITEM_OPAQUE, Boolean.TRUE);

    UIManager.put(UiColorKeys.SEPARATOR_FOREGROUND, separator);
    UIManager.put(UiColorKeys.SEPARATOR_BACKGROUND, panelBg);
    UIManager.put(UiColorKeys.TOOL_BAR_SEPARATOR_COLOR, separator);
    UIManager.put(UiColorKeys.SPLIT_PANE_BACKGROUND, splitPaneBg);
    UIManager.put(UiColorKeys.SPLIT_PANE_FOREGROUND, separator);
    UIManager.put(UiColorKeys.SPLIT_PANE_DIVIDER_DRAGGING_COLOR, splitPaneDraggingColor);
    UIManager.put(UiColorKeys.SCROLL_PANE_BORDER_COLOR, border);

    UIManager.put(UiColorKeys.TOOL_TIP_BACKGROUND, tooltipBg);
    UIManager.put(UiColorKeys.TOOL_TIP_FOREGROUND, text);
    UIManager.put(UiColorKeys.TABBED_PANE_FOCUS, focus);

    applyNimbusTabbedPane(panelBg, text, disabledText, selectionBg, selectionFg, focus, border);
  }

  private static void applyNimbusComboBox(
      Color fieldBg,
      Color arrowBg,
      Color panelBg,
      Color text,
      Color disabledText,
      Color selectionBg,
      Color selectionFg,
      Color focus,
      Color border) {
    Color enabledTop = lighten(fieldBg, 0.05);
    Color enabledBottom = darken(fieldBg, 0.04);
    Color hoverTop =
        ThemeColorUtils.ensureContrastAgainstBackground(lighten(fieldBg, 0.08), text, 4.5);
    Color hoverBottom = lighten(fieldBg, 0.02);
    Color pressedTop = darken(fieldBg, 0.03);
    Color pressedBottom = darken(fieldBg, 0.10);
    Color focusedTop = lighten(mix(fieldBg, focus, 0.16), 0.06);
    Color focusedBottom = darken(mix(fieldBg, focus, 0.12), 0.04);
    Color selectedTop = selectionBg;
    Color selectedBottom = darken(selectionBg, 0.04);
    Color disabledTop = mix(fieldBg, panelBg, 0.64);
    Color disabledBottom = mix(fieldBg, panelBg, 0.78);

    Color arrowTop = lighten(arrowBg, 0.06);
    Color arrowBottom = darken(arrowBg, 0.06);
    Color arrowHoverTop = lighten(arrowBg, 0.13);
    Color arrowHoverBottom = lighten(arrowBg, 0.03);
    Color arrowPressedTop = darken(arrowBg, 0.04);
    Color arrowPressedBottom = darken(arrowBg, 0.12);
    Color arrowSelectedTop = lighten(mix(selectionBg, focus, 0.12), 0.07);
    Color arrowSelectedBottom = darken(selectionBg, 0.07);

    ColorUIResource readableText =
        toUiResource(ThemeColorUtils.ensureContrastAgainstBackground(text, fieldBg, 4.5));
    ColorUIResource readableDisabledText =
        toUiResource(
            ThemeColorUtils.ensureContrastAgainstBackground(disabledText, disabledBottom, 2.0));
    ColorUIResource readableSelectionText =
        toUiResource(
            ThemeColorUtils.ensureContrastAgainstBackground(selectionFg, selectionBg, 4.5));

    Painter<JComponent> enabledPainter =
        new NimbusSurfacePainter(enabledTop, enabledBottom, border);
    Painter<JComponent> hoverPainter = new NimbusSurfacePainter(hoverTop, hoverBottom, focus);
    Painter<JComponent> pressedPainter =
        new NimbusSurfacePainter(pressedTop, pressedBottom, border);
    Painter<JComponent> focusedPainter = new NimbusSurfacePainter(focusedTop, focusedBottom, focus);
    Painter<JComponent> selectedPainter =
        new NimbusSurfacePainter(selectedTop, selectedBottom, focus);
    Painter<JComponent> disabledPainter =
        new NimbusSurfacePainter(disabledTop, disabledBottom, darken(border, 0.10));

    Painter<JComponent> arrowEnabledPainter =
        new NimbusSurfacePainter(arrowTop, arrowBottom, border);
    Painter<JComponent> arrowHoverPainter =
        new NimbusSurfacePainter(arrowHoverTop, arrowHoverBottom, focus);
    Painter<JComponent> arrowPressedPainter =
        new NimbusSurfacePainter(arrowPressedTop, arrowPressedBottom, border);
    Painter<JComponent> arrowSelectedPainter =
        new NimbusSurfacePainter(arrowSelectedTop, arrowSelectedBottom, focus);
    Painter<JComponent> arrowDisabledPainter =
        new NimbusSurfacePainter(disabledTop, disabledBottom, darken(border, 0.10));

    Painter<JComponent> arrowGlyph =
        new NimbusArrowPainter(readableText, javax.swing.SwingConstants.WEST);
    Painter<JComponent> arrowGlyphSelected =
        new NimbusArrowPainter(readableSelectionText, javax.swing.SwingConstants.WEST);
    Painter<JComponent> arrowGlyphDisabled =
        new NimbusArrowPainter(readableDisabledText, javax.swing.SwingConstants.WEST);

    UIManager.put(UiColorKeys.COMBO_BOX_FOREGROUND, readableText);
    UIManager.put(UiColorKeys.COMBO_BOX_DISABLED_TEXT, readableDisabledText);
    UIManager.put(UiColorKeys.COMBO_BOX_SELECTION_FOREGROUND, readableSelectionText);
    UIManager.put(UiColorKeys.COMBO_BOX_TEXT_FIELD_TEXT_FOREGROUND, readableText);
    UIManager.put(UiColorKeys.COMBO_BOX_TEXT_FIELD_SELECTED_TEXT_FOREGROUND, readableSelectionText);
    UIManager.put(UiColorKeys.COMBO_BOX_ARROW_BUTTON_FOREGROUND, readableText);

    UIManager.put(UiDefaultKeys.COMBO_BOX_DISABLED_EDITABLE_BACKGROUND_PAINTER, disabledPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_DISABLED_PRESSED_BACKGROUND_PAINTER, disabledPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_DISABLED_BACKGROUND_PAINTER, disabledPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_EDITABLE_ENABLED_BACKGROUND_PAINTER, enabledPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_EDITABLE_FOCUSED_BACKGROUND_PAINTER, focusedPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_EDITABLE_MOUSE_OVER_BACKGROUND_PAINTER, hoverPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_EDITABLE_PRESSED_BACKGROUND_PAINTER, pressedPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_ENABLED_SELECTED_BACKGROUND_PAINTER, selectedPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_ENABLED_BACKGROUND_PAINTER, enabledPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_FOCUSED_MOUSE_OVER_BACKGROUND_PAINTER, focusedPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_FOCUSED_PRESSED_BACKGROUND_PAINTER, pressedPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_FOCUSED_BACKGROUND_PAINTER, focusedPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_MOUSE_OVER_BACKGROUND_PAINTER, hoverPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_PRESSED_BACKGROUND_PAINTER, pressedPainter);

    UIManager.put(
        UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_DISABLED_EDITABLE_BACKGROUND_PAINTER,
        arrowDisabledPainter);
    UIManager.put(
        UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_EDITABLE_ENABLED_BACKGROUND_PAINTER,
        arrowEnabledPainter);
    UIManager.put(
        UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_EDITABLE_MOUSE_OVER_BACKGROUND_PAINTER,
        arrowHoverPainter);
    UIManager.put(
        UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_EDITABLE_PRESSED_BACKGROUND_PAINTER,
        arrowPressedPainter);
    UIManager.put(
        UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_EDITABLE_SELECTED_BACKGROUND_PAINTER,
        arrowSelectedPainter);
    UIManager.put(
        UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_DISABLED_FOREGROUND_PAINTER, arrowGlyphDisabled);
    UIManager.put(UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_ENABLED_FOREGROUND_PAINTER, arrowGlyph);
    UIManager.put(UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_MOUSE_OVER_FOREGROUND_PAINTER, arrowGlyph);
    UIManager.put(UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_PRESSED_FOREGROUND_PAINTER, arrowGlyph);
    UIManager.put(
        UiDefaultKeys.COMBO_BOX_ARROW_BUTTON_SELECTED_FOREGROUND_PAINTER, arrowGlyphSelected);

    UIManager.put(UiDefaultKeys.COMBO_BOX_TEXT_FIELD_DISABLED_BACKGROUND_PAINTER, disabledPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_TEXT_FIELD_ENABLED_BACKGROUND_PAINTER, enabledPainter);
    UIManager.put(UiDefaultKeys.COMBO_BOX_TEXT_FIELD_SELECTED_BACKGROUND_PAINTER, selectedPainter);
  }

  private static void applyNimbusTabbedPane(
      Color panelBg,
      Color text,
      Color disabledText,
      Color selectionBg,
      Color selectionFg,
      Color focus,
      Color border) {
    ColorUIResource tabArea = toUiResource(darken(panelBg, 0.06));
    ColorUIResource inactiveFg =
        toUiResource(ThemeColorUtils.ensureContrastAgainstBackground(text, tabArea, 4.5));
    ColorUIResource disabledFg =
        toUiResource(ThemeColorUtils.ensureContrastAgainstBackground(disabledText, tabArea, 2.0));
    ColorUIResource selectedFg =
        toUiResource(
            ThemeColorUtils.ensureContrastAgainstBackground(selectionFg, selectionBg, 4.5));

    Color inactiveTop = lighten(panelBg, 0.04);
    Color inactiveBottom = darken(panelBg, 0.04);
    Color hoverTop = lighten(panelBg, 0.10);
    Color hoverBottom = lighten(panelBg, 0.02);
    Color pressedTop = darken(panelBg, 0.03);
    Color pressedBottom = darken(panelBg, 0.09);
    Color selectedTop = selectionBg;
    Color selectedBottom = darken(selectionBg, 0.04);
    Color selectedPressedTop = darken(selectedTop, 0.05);
    Color selectedPressedBottom = darken(selectedBottom, 0.08);
    Color disabledTop = mix(inactiveTop, panelBg, 0.65);
    Color disabledBottom = mix(inactiveBottom, panelBg, 0.70);

    Painter<JComponent> areaPainter =
        new NimbusSurfacePainter(darken(tabArea, 0.01), darken(tabArea, 0.06), border);
    Painter<JComponent> disabledAreaPainter =
        new NimbusSurfacePainter(darken(tabArea, 0.04), darken(tabArea, 0.08), border);
    Painter<JComponent> inactivePainter =
        new NimbusSurfacePainter(inactiveTop, inactiveBottom, border);
    Painter<JComponent> hoverPainter = new NimbusSurfacePainter(hoverTop, hoverBottom, focus);
    Painter<JComponent> pressedPainter =
        new NimbusSurfacePainter(pressedTop, pressedBottom, border);
    Painter<JComponent> selectedPainter =
        new NimbusSurfacePainter(selectedTop, selectedBottom, focus);
    Painter<JComponent> selectedPressedPainter =
        new NimbusSurfacePainter(selectedPressedTop, selectedPressedBottom, focus);
    Painter<JComponent> disabledPainter =
        new NimbusSurfacePainter(disabledTop, disabledBottom, darken(border, 0.08));

    UIManager.put(UiColorKeys.TABBED_PANE_BACKGROUND, tabArea);
    UIManager.put(UiColorKeys.TABBED_PANE_FOREGROUND, inactiveFg);
    UIManager.put(UiColorKeys.TABBED_PANE_DISABLED, toUiResource(darken(panelBg, 0.03)));
    UIManager.put(UiColorKeys.TABBED_PANE_DISABLED_TEXT, disabledFg);
    UIManager.put(UiColorKeys.TABBED_PANE_DARK_SHADOW, toUiResource(darken(border, 0.16)));
    UIManager.put(UiColorKeys.TABBED_PANE_HIGHLIGHT, toUiResource(lighten(panelBg, 0.12)));
    UIManager.put(UiColorKeys.TABBED_PANE_SHADOW, border);

    UIManager.put(UiColorKeys.TABBED_PANE_TAB_DISABLED_TEXT_FOREGROUND, disabledFg);
    UIManager.put(UiColorKeys.TABBED_PANE_TAB_ENABLED_TEXT_FOREGROUND, inactiveFg);
    UIManager.put(UiColorKeys.TABBED_PANE_TAB_MOUSE_OVER_TEXT_FOREGROUND, inactiveFg);
    UIManager.put(UiColorKeys.TABBED_PANE_TAB_PRESSED_TEXT_FOREGROUND, inactiveFg);
    UIManager.put(UiColorKeys.TABBED_PANE_TAB_SELECTED_TEXT_FOREGROUND, selectedFg);
    UIManager.put(UiColorKeys.TABBED_PANE_TAB_MOUSE_OVER_SELECTED_TEXT_FOREGROUND, selectedFg);
    UIManager.put(UiColorKeys.TABBED_PANE_TAB_PRESSED_SELECTED_TEXT_FOREGROUND, selectedFg);
    UIManager.put(UiColorKeys.TABBED_PANE_TAB_FOCUSED_SELECTED_TEXT_FOREGROUND, selectedFg);
    UIManager.put(
        UiColorKeys.TABBED_PANE_TAB_FOCUSED_MOUSE_OVER_SELECTED_TEXT_FOREGROUND, selectedFg);
    UIManager.put(UiColorKeys.TABBED_PANE_TAB_FOCUSED_PRESSED_SELECTED_TEXT_FOREGROUND, selectedFg);

    UIManager.put(
        UiDefaultKeys.TABBED_PANE_TAB_AREA_DISABLED_BACKGROUND_PAINTER, disabledAreaPainter);
    UIManager.put(UiDefaultKeys.TABBED_PANE_TAB_AREA_ENABLED_BACKGROUND_PAINTER, areaPainter);
    UIManager.put(UiDefaultKeys.TABBED_PANE_TAB_AREA_MOUSE_OVER_BACKGROUND_PAINTER, areaPainter);
    UIManager.put(UiDefaultKeys.TABBED_PANE_TAB_AREA_PRESSED_BACKGROUND_PAINTER, areaPainter);
    UIManager.put(UiDefaultKeys.TABBED_PANE_TAB_DISABLED_BACKGROUND_PAINTER, disabledPainter);
    UIManager.put(
        UiDefaultKeys.TABBED_PANE_TAB_DISABLED_SELECTED_BACKGROUND_PAINTER, disabledPainter);
    UIManager.put(UiDefaultKeys.TABBED_PANE_TAB_ENABLED_BACKGROUND_PAINTER, inactivePainter);
    UIManager.put(UiDefaultKeys.TABBED_PANE_TAB_MOUSE_OVER_BACKGROUND_PAINTER, hoverPainter);
    UIManager.put(UiDefaultKeys.TABBED_PANE_TAB_PRESSED_BACKGROUND_PAINTER, pressedPainter);
    UIManager.put(UiDefaultKeys.TABBED_PANE_TAB_SELECTED_BACKGROUND_PAINTER, selectedPainter);
    UIManager.put(
        UiDefaultKeys.TABBED_PANE_TAB_MOUSE_OVER_SELECTED_BACKGROUND_PAINTER, selectedPainter);
    UIManager.put(
        UiDefaultKeys.TABBED_PANE_TAB_PRESSED_SELECTED_BACKGROUND_PAINTER, selectedPressedPainter);
    UIManager.put(
        UiDefaultKeys.TABBED_PANE_TAB_FOCUSED_SELECTED_BACKGROUND_PAINTER, selectedPainter);
    UIManager.put(
        UiDefaultKeys.TABBED_PANE_TAB_FOCUSED_MOUSE_OVER_SELECTED_BACKGROUND_PAINTER,
        selectedPainter);
    UIManager.put(
        UiDefaultKeys.TABBED_PANE_TAB_FOCUSED_PRESSED_SELECTED_BACKGROUND_PAINTER,
        selectedPressedPainter);
  }

  private static void applyNimbusOrangeOverrides() {
    applyNimbusTintOverrides(
        uiColor(0xEF, 0xD9, 0xC2),
        uiColor(0xFA, 0xEE, 0xDE),
        uiColor(0xE8, 0xD1, 0xB8),
        uiColor(0x2F, 0x22, 0x14),
        uiColor(0xC7, 0xA7, 0x86),
        uiColor(0xB8, 0x63, 0x1F),
        uiColor(0x9A, 0x4F, 0x16),
        uiColor(0xBF, 0x67, 0x20),
        uiColor(0xFF, 0xF8, 0xEE),
        uiColor(0xB8, 0x63, 0x1F),
        uiColor(0xA8, 0x60, 0x22),
        uiColor(0xA3, 0x95, 0x88),
        uiColor(0xD8, 0x7A, 0x29),
        uiColor(0xD1, 0x8A, 0x30),
        uiColor(0x7A, 0x95, 0xB0),
        uiColor(0xB2, 0x62, 0x5A),
        uiColor(0x6E, 0x9B, 0x6E),
        uiColor(0x8E, 0x7D, 0x6B));
  }

  private static void applyNimbusGreenOverrides() {
    applyNimbusTintOverrides(
        uiColor(0xDA, 0xE9, 0xD6),
        uiColor(0xEC, 0xF5, 0xE8),
        uiColor(0xD1, 0xE0, 0xCC),
        uiColor(0x1F, 0x2B, 0x1F),
        uiColor(0xA5, 0xBC, 0x9E),
        uiColor(0x3E, 0x7D, 0x49),
        uiColor(0x2E, 0x6A, 0x3A),
        uiColor(0x4A, 0x89, 0x55),
        uiColor(0xF5, 0xFF, 0xF5),
        uiColor(0x3E, 0x7D, 0x49),
        uiColor(0x4E, 0x7A, 0x49),
        uiColor(0x93, 0xA5, 0x91),
        uiColor(0xB3, 0x7D, 0x44),
        uiColor(0xBE, 0x9B, 0x3C),
        uiColor(0x5B, 0x84, 0xB3),
        uiColor(0xAF, 0x5E, 0x58),
        uiColor(0x5F, 0x9C, 0x6A),
        uiColor(0x86, 0x9D, 0x80));
  }

  private static void applyNimbusBlueOverrides() {
    applyNimbusTintOverrides(
        uiColor(0xD8, 0xE4, 0xF3),
        uiColor(0xEB, 0xF3, 0xFD),
        uiColor(0xD0, 0xDE, 0xEE),
        uiColor(0x1C, 0x27, 0x35),
        uiColor(0x9F, 0xB2, 0xC8),
        uiColor(0x2F, 0x69, 0xB3),
        uiColor(0x24, 0x58, 0x9A),
        uiColor(0x2F, 0x69, 0xB3),
        uiColor(0xF3, 0xF8, 0xFF),
        uiColor(0x2B, 0x5F, 0xA3),
        uiColor(0x2D, 0x5E, 0x9D),
        uiColor(0x8C, 0x9F, 0xB7),
        uiColor(0xC2, 0x8B, 0x4A),
        uiColor(0xC7, 0xA2, 0x48),
        uiColor(0x3E, 0x75, 0xBF),
        uiColor(0xB2, 0x62, 0x5A),
        uiColor(0x66, 0x9E, 0x71),
        uiColor(0x84, 0x95, 0xAC));
  }

  private static void applyNimbusVioletOverrides() {
    applyNimbusTintOverrides(
        uiColor(0xE5, 0xDE, 0xF3),
        uiColor(0xF2, 0xED, 0xFA),
        uiColor(0xDC, 0xD3, 0xED),
        uiColor(0x2B, 0x22, 0x38),
        uiColor(0xB5, 0xA7, 0xCB),
        uiColor(0x6B, 0x4F, 0xA8),
        uiColor(0x5C, 0x42, 0x95),
        uiColor(0x73, 0x58, 0xB2),
        uiColor(0xFA, 0xF6, 0xFF),
        uiColor(0x68, 0x4E, 0xA3),
        uiColor(0x6D, 0x52, 0xA3),
        uiColor(0x9C, 0x93, 0xB2),
        uiColor(0xC5, 0x88, 0x4B),
        uiColor(0xC8, 0xA0, 0x52),
        uiColor(0x5F, 0x86, 0xBC),
        uiColor(0xB2, 0x62, 0x75),
        uiColor(0x68, 0x9D, 0x77),
        uiColor(0x93, 0x83, 0xA8));
  }

  private static void applyNimbusMagentaOverrides() {
    applyNimbusTintOverrides(
        uiColor(0xF0, 0xDB, 0xE8),
        uiColor(0xFB, 0xEF, 0xF6),
        uiColor(0xEA, 0xD0, 0xE0),
        uiColor(0x3A, 0x20, 0x30),
        uiColor(0xC8, 0xA1, 0xB7),
        uiColor(0xB1, 0x46, 0x86),
        uiColor(0x9A, 0x2F, 0x71),
        uiColor(0xB1, 0x46, 0x86),
        uiColor(0xFF, 0xF3, 0xFA),
        uiColor(0xA4, 0x3C, 0x7A),
        uiColor(0xA7, 0x3D, 0x79),
        uiColor(0xB1, 0x97, 0xA8),
        uiColor(0xC8, 0x89, 0x48),
        uiColor(0xCC, 0x9E, 0x4A),
        uiColor(0x6A, 0x86, 0xBC),
        uiColor(0xB3, 0x5A, 0x71),
        uiColor(0x6B, 0xA0, 0x77),
        uiColor(0xA0, 0x7D, 0x92));
  }

  private static void applyNimbusAmberOverrides() {
    applyNimbusTintOverrides(
        uiColor(0xF2, 0xE7, 0xCD),
        uiColor(0xFB, 0xF4, 0xE4),
        uiColor(0xE9, 0xDB, 0xB7),
        uiColor(0x35, 0x28, 0x13),
        uiColor(0xC8, 0xB2, 0x83),
        uiColor(0xB7, 0x86, 0x24),
        uiColor(0x9D, 0x6F, 0x19),
        uiColor(0xBF, 0x8E, 0x2E),
        uiColor(0xFF, 0xF9, 0xEF),
        uiColor(0xB1, 0x7F, 0x20),
        uiColor(0xA1, 0x74, 0x1D),
        uiColor(0xAA, 0x9C, 0x85),
        uiColor(0xCD, 0x8D, 0x35),
        uiColor(0xD2, 0xA0, 0x3E),
        uiColor(0x66, 0x86, 0xB5),
        uiColor(0xB3, 0x5F, 0x5C),
        uiColor(0x6F, 0x9F, 0x72),
        uiColor(0x99, 0x84, 0x5E));
  }

  private static void applyNimbusTintOverrides(
      ColorUIResource control,
      ColorUIResource bg,
      ColorUIResource menuBg,
      ColorUIResource text,
      ColorUIResource border,
      ColorUIResource focus,
      ColorUIResource link,
      ColorUIResource selectionBg,
      ColorUIResource selectionFg,
      ColorUIResource menuSelectionBg,
      ColorUIResource nimbusBase,
      ColorUIResource nimbusBlueGrey,
      ColorUIResource nimbusOrange,
      ColorUIResource nimbusAlertYellow,
      ColorUIResource nimbusInfoBlue,
      ColorUIResource nimbusRed,
      ColorUIResource nimbusGreen,
      ColorUIResource disabledText) {
    // Keep large surfaces quiet; reserve the saturated hue for focus and selection.
    control = toUiResource(lighten(control, 0.62));
    bg = toUiResource(lighten(bg, 0.78));
    menuBg = control;
    nimbusBlueGrey = toUiResource(mix(control, border, 0.18));
    selectionFg = uiColor(0xFF, 0xFF, 0xFF);
    selectionBg =
        toUiResource(
            ThemeColorUtils.ensureContrastAgainstBackground(selectionBg, selectionFg, 5.0));
    menuSelectionBg = selectionBg;
    disabledText =
        toUiResource(ThemeColorUtils.ensureContrastAgainstBackground(disabledText, control, 3.0));

    UIManager.put(UiColorKeys.CONTROL, control);
    UIManager.put(UiColorKeys.INFO, bg);
    UIManager.put(UiColorKeys.NIMBUS_BASE, nimbusBase);
    UIManager.put(UiColorKeys.NIMBUS_BLUE_GREY, nimbusBlueGrey);
    UIManager.put(UiColorKeys.NIMBUS_BORDER, border);
    UIManager.put(UiColorKeys.NIMBUS_LIGHT_BACKGROUND, bg);
    UIManager.put(UiColorKeys.NIMBUS_FOCUS, focus);
    UIManager.put(UiColorKeys.NIMBUS_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.NIMBUS_SELECTED_TEXT, selectionFg);
    UIManager.put(UiColorKeys.NIMBUS_DISABLED_TEXT, disabledText);
    UIManager.put(UiColorKeys.NIMBUS_INFO_BLUE, nimbusInfoBlue);
    UIManager.put(UiColorKeys.NIMBUS_ORANGE, nimbusOrange);
    UIManager.put(UiColorKeys.NIMBUS_ALERT_YELLOW, nimbusAlertYellow);
    UIManager.put(UiColorKeys.NIMBUS_RED, nimbusRed);
    UIManager.put(UiColorKeys.NIMBUS_GREEN, nimbusGreen);
    UIManager.put(UiColorKeys.TEXT_HIGHLIGHT, selectionBg);
    UIManager.put(UiColorKeys.TEXT_HIGHLIGHT_TEXT, selectionFg);
    UIManager.put(UiColorKeys.TEXT, text);
    UIManager.put(UiColorKeys.TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.TEXT_TEXT, text);
    UIManager.put(UiColorKeys.CONTROL_TEXT, text);
    UIManager.put(UiColorKeys.LABEL_FOREGROUND, text);
    UIManager.put(UiColorKeys.PANEL_BACKGROUND, control);
    UIManager.put(UiColorKeys.MENU, menuBg);
    UIManager.put(UiColorKeys.COMPONENT_FOCUS_COLOR, focus);
    UIManager.put(UiColorKeys.COMPONENT_ACCENT_COLOR, focus);
    UIManager.put(UiColorKeys.COMPONENT_LINK_COLOR, link);
    UIManager.put(UiColorKeys.TEXT_FIELD_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TEXT_FIELD_FOREGROUND, text);
    UIManager.put(UiColorKeys.TEXT_AREA_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TEXT_AREA_FOREGROUND, text);
    UIManager.put(UiColorKeys.LIST_BACKGROUND, bg);
    UIManager.put(UiColorKeys.LIST_FOREGROUND, text);
    UIManager.put(UiColorKeys.TABLE_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TABLE_FOREGROUND, text);
    UIManager.put(UiColorKeys.TREE_TEXT_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TREE_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.TEXT_COMPONENT_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TEXT_COMPONENT_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.LIST_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.LIST_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.TABLE_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TABLE_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.TREE_SELECTION_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.TREE_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_BAR_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.MENU_BAR_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.MENU_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.MENU_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ITEM_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.MENU_ITEM_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_ITEM_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_FOREGROUND, text);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(
        UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(
        UiColorKeys.CHECK_BOX_MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_FOREGROUND, text);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_BACKGROUND, menuSelectionBg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_SELECTION_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_ENABLED_TEXT_FOREGROUND, text);
    UIManager.put(
        UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_MOUSE_OVER_TEXT_FOREGROUND, selectionFg);
    UIManager.put(
        UiColorKeys.RADIO_BUTTON_MENU_ITEM_ACCELERATOR_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.POPUP_MENU_BACKGROUND, menuBg);
    UIManager.put(UiColorKeys.POPUP_MENU_FOREGROUND, text);
    UIManager.put(UiColorKeys.POPUP_MENU_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.BUTTON_SELECT, selectionBg);
    UIManager.put(UiColorKeys.TOGGLE_BUTTON_SELECT, selectionBg);
    UIManager.put(UiColorKeys.TABBED_PANE_FOCUS, focus);
    applyNimbusLightSurfaces(
        control, bg, text, disabledText, border, focus, selectionBg, selectionFg);
  }

  private static void applyNimbusLightSurfaces(
      ColorUIResource control,
      ColorUIResource bg,
      ColorUIResource text,
      ColorUIResource disabledText,
      ColorUIResource border,
      ColorUIResource focus,
      ColorUIResource selectionBg,
      ColorUIResource selectionFg) {
    UIManager.put(UiColorKeys.TEXT_COMPONENT_BACKGROUND, bg);
    UIManager.put(UiColorKeys.PASSWORD_FIELD_BACKGROUND, bg);
    UIManager.put(UiColorKeys.PASSWORD_FIELD_FOREGROUND, text);
    UIManager.put(UiColorKeys.FORMATTED_TEXT_FIELD_BACKGROUND, bg);
    UIManager.put(UiColorKeys.FORMATTED_TEXT_FIELD_FOREGROUND, text);
    UIManager.put(UiColorKeys.TEXT_PANE_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TEXT_PANE_FOREGROUND, text);
    UIManager.put(UiColorKeys.EDITOR_PANE_BACKGROUND, bg);
    UIManager.put(UiColorKeys.EDITOR_PANE_FOREGROUND, text);
    UIManager.put(UiColorKeys.TREE_BACKGROUND, bg);
    UIManager.put(UiColorKeys.VIEWPORT_BACKGROUND, bg);
    UIManager.put(UiColorKeys.VIEWPORT_FOREGROUND, text);
    UIManager.put(UiColorKeys.TABLE_HEADER_BACKGROUND, control);
    UIManager.put(UiColorKeys.TABLE_HEADER_FOREGROUND, text);
    UIManager.put(UiColorKeys.TABLE_HEADER_RENDERER_BACKGROUND, control);
    UIManager.put(UiColorKeys.TABLE_HEADER_RENDERER_FOREGROUND, text);
    UIManager.put(UiColorKeys.TABLE_GRID_COLOR, border);
    UIManager.put(UiColorKeys.COMPONENT_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.SCROLL_PANE_BORDER_COLOR, border);
    UIManager.put(UiColorKeys.SEPARATOR_FOREGROUND, border);
    UIManager.put(UiColorKeys.TOOL_TIP_BACKGROUND, bg);
    UIManager.put(UiColorKeys.TOOL_TIP_FOREGROUND, text);
    UIManager.put(UiColorKeys.COMBO_BOX_BACKGROUND, bg);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_BACKGROUND, bg);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_SELECTED_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.COMBO_BOX_LIST_RENDERER_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_BACKGROUND, bg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_TEXT_FOREGROUND, text);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_SELECTED_BACKGROUND, selectionBg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_SELECTED_TEXT_FOREGROUND, selectionFg);
    UIManager.put(UiColorKeys.COMBO_BOX_RENDERER_DISABLED_TEXT_FOREGROUND, disabledText);
    UIManager.put(UiColorKeys.SPINNER_BACKGROUND, bg);
    UIManager.put(UiColorKeys.SPINNER_FOREGROUND, text);
    UIManager.put(UiColorKeys.SPINNER_FORMATTED_TEXT_FIELD_BACKGROUND, bg);
    UIManager.put(UiColorKeys.SPINNER_FORMATTED_TEXT_FIELD_FOREGROUND, text);
    applyNimbusComboBox(
        bg, control, control, text, disabledText, selectionBg, selectionFg, focus, border);
    applyNimbusTabbedPane(control, text, disabledText, selectionBg, selectionFg, focus, border);
  }

  private static ColorUIResource uiColor(int r, int g, int b) {
    return ThemeColorUtils.uiColor(r, g, b);
  }

  private static ColorUIResource toUiResource(Color c) {
    return new ColorUIResource(c);
  }

  private static void logNimbusSnapshot(String stage, String themeId) {
    if (!ThemeLookAndFeelUtils.isNimbusDebugEnabled()) return;

    Color uiTextFieldBg = UIManager.getColor(UiColorKeys.TEXT_FIELD_BACKGROUND);
    Color uiTextPaneBg = UIManager.getColor(UiColorKeys.TEXT_PANE_BACKGROUND);
    Color uiTextComponentBg = UIManager.getColor(UiColorKeys.TEXT_COMPONENT_BACKGROUND);
    Color uiLightBg = UIManager.getColor(UiColorKeys.NIMBUS_LIGHT_BACKGROUND);
    Color uiBase = UIManager.getColor(UiColorKeys.NIMBUS_BASE);
    Color uiBlueGrey = UIManager.getColor(UiColorKeys.NIMBUS_BLUE_GREY);
    Color uiPanelBg = UIManager.getColor(UiColorKeys.PANEL_BACKGROUND);

    Color resolvedTextFieldBg = null;
    Color resolvedTextPaneBg = null;
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        resolvedTextFieldBg = new JTextField().getBackground();
      } catch (Exception ignored) {
      }
      try {
        resolvedTextPaneBg = new JTextPane().getBackground();
      } catch (Exception ignored) {
      }
    }

    String message =
        String.format(
            Locale.ROOT,
            "[ircafe][nimbus] stage=%s theme=%s laf=%s ui.textFieldBg=%s ui.textPaneBg=%s ui.textComponentBg=%s ui.nimbusLightBg=%s ui.nimbusBase=%s ui.nimbusBlueGrey=%s ui.panelBg=%s comp.textFieldBg=%s comp.textPaneBg=%s",
            stage,
            themeId,
            ThemeLookAndFeelUtils.currentLookAndFeelClassName(),
            toHexOrNull(uiTextFieldBg),
            toHexOrNull(uiTextPaneBg),
            toHexOrNull(uiTextComponentBg),
            toHexOrNull(uiLightBg),
            toHexOrNull(uiBase),
            toHexOrNull(uiBlueGrey),
            toHexOrNull(uiPanelBg),
            toHexOrNull(resolvedTextFieldBg),
            toHexOrNull(resolvedTextPaneBg));
    log.warn(message);
    System.err.println(message);
  }

  private static String toHexOrNull(Color c) {
    if (c == null) return "null";
    return String.format(
        "#%02X%02X%02X(%d,%d,%d)",
        c.getRed(), c.getGreen(), c.getBlue(), c.getRed(), c.getGreen(), c.getBlue());
  }

  private static void clearNimbusDarkOverrides() {
    for (String key : inputOverrideKeys()) UIManager.put(key, null);
    for (String key : auxiliaryPainterKeys()) UIManager.put(key, null);
    for (String prefix : SELECTION_STATE_PREFIXES) {
      UIManager.put(prefix + ".textForeground", null);
      UIManager.put(prefix + ".textBackground", null);
    }
    for (String prefix : TEXT_SURFACE_PREFIXES) {
      UIManager.put(prefix + "[Enabled].background", null);
      UIManager.put(prefix + "[Enabled].textForeground", null);
      UIManager.put(prefix + "[Selected].textForeground", null);
      UIManager.put(prefix + "[Selected].textBackground", null);
    }
    for (String key : NIMBUS_DARK_OVERRIDE_KEYS) {
      try {
        UIManager.put(key, null);
      } catch (Exception ignored) {
      }
    }
  }

  private static void clearNimbusTintOverrides() {
    for (String key : NIMBUS_TINT_OVERRIDE_KEYS) {
      try {
        UIManager.put(key, null);
      } catch (Exception ignored) {
      }
    }
  }

  private static Color mix(Color a, Color b, double t) {
    return ThemeColorUtils.mix(a, b, t);
  }

  private static Color lighten(Color c, double amount) {
    return ThemeColorUtils.lighten(c, amount);
  }

  private static Color darken(Color c, double amount) {
    return ThemeColorUtils.darken(c, amount);
  }
}

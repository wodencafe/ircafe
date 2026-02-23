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
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;
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
    DARKLAF,
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

  private static final String[] NIMBUS_DARK_OVERRIDE_KEYS = {
      "control",
      "info",
      "nimbusBase",
      "nimbusBlueGrey",
      "nimbusBorder",
      "nimbusLightBackground",
      "nimbusFocus",
      "nimbusSelectionBackground",
      "nimbusSelectedText",
      "nimbusDisabledText",
      "nimbusInfoBlue",
      "nimbusAlertYellow",
      "nimbusOrange",
      "nimbusRed",
      "nimbusGreen",
      "textHighlight",
      "textHighlightText",
      "text",
      "textForeground",
      "textText",
      "controlText",
      "controlDkShadow",
      "controlShadow",
      "controlLtHighlight",
      "Label.foreground",
      "Panel.background",
      "menu",
      "Component.focusColor",
      "Component.accentColor",
      "Component.linkColor",
      "Component.borderColor",
      "Component.warningColor",
      "Component.warning.outlineColor",
      "Component.warning.borderColor",
      "Component.warning.focusedBorderColor",
      "Component.warning.focusColor",
      "Component.errorColor",
      "Component.error.outlineColor",
      "Component.error.borderColor",
      "Component.error.focusedBorderColor",
      "Component.error.focusColor",
      "TextField.background",
      "TextField.foreground",
      "TextField.borderColor",
      "TextArea.background",
      "TextArea.foreground",
      "TextComponent.selectionBackground",
      "TextComponent.selectionForeground",
      "ComboBox.background",
      "ComboBox.foreground",
      "ComboBox.disabled",
      "ComboBox.disabledText",
      "ComboBox.selectionBackground",
      "ComboBox.selectionForeground",
      "ComboBox.rendererUseListColors",
      "ComboBox:\"ComboBox.listRenderer\".background",
      "ComboBox:\"ComboBox.listRenderer\".textForeground",
      "ComboBox:\"ComboBox.listRenderer\"[Selected].background",
      "ComboBox:\"ComboBox.listRenderer\"[Selected].textForeground",
      "ComboBox:\"ComboBox.renderer\".background",
      "ComboBox:\"ComboBox.renderer\".textForeground",
      "ComboBox:\"ComboBox.renderer\"[Selected].background",
      "ComboBox:\"ComboBox.renderer\"[Selected].textForeground",
      "ComboBox:\"ComboBox.renderer\"[Disabled].textForeground",
      "ComboBox:\"ComboBox.textField\".background",
      "ComboBox:\"ComboBox.textField\".textForeground",
      "ComboBox:\"ComboBox.textField\"[Selected].textForeground",
      "ComboBox:\"ComboBox.arrowButton\".background",
      "ComboBox:\"ComboBox.arrowButton\".foreground",
      "Button.background",
      "Button.foreground",
      "Button.disabledText",
      "Button.select",
      "Button[Enabled].textForeground",
      "Button[Disabled].textForeground",
      "Button[Pressed].textForeground",
      "Button[Default].textForeground",
      "Button[Default+Pressed].textForeground",
      "ToggleButton.background",
      "ToggleButton.foreground",
      "ToggleButton.disabledText",
      "ToggleButton.select",
      "CheckBox.background",
      "CheckBox.foreground",
      "CheckBox.disabledText",
      "RadioButton.background",
      "RadioButton.foreground",
      "RadioButton.disabledText",
      "List.background",
      "List.foreground",
      "List.selectionBackground",
      "List.selectionForeground",
      "Table.background",
      "Table.foreground",
      "Table.gridColor",
      "Table.selectionBackground",
      "Table.selectionForeground",
      "Tree.textForeground",
      "Tree.textBackground",
      "Tree.selectionForeground",
      "Tree.selectionBackground",
      "MenuBar.background",
      "MenuBar.foreground",
      "MenuBar.borderColor",
      "Menu.background",
      "Menu.foreground",
      "Menu.selectionBackground",
      "Menu.selectionForeground",
      "Menu.disabledForeground",
      "MenuBar:Menu[Enabled].textForeground",
      "MenuBar:Menu[Disabled].textForeground",
      "MenuBar:Menu[Selected].textForeground",
      "Menu[Enabled].textForeground",
      "Menu[Disabled].textForeground",
      "Menu[Enabled+Selected].textForeground",
      "MenuItem.background",
      "MenuItem.foreground",
      "MenuItem.selectionBackground",
      "MenuItem.selectionForeground",
      "MenuItem.disabledForeground",
      "Menu:MenuItemAccelerator[MouseOver].textForeground",
      "MenuItem[Enabled].textForeground",
      "MenuItem[Disabled].textForeground",
      "MenuItem[MouseOver].textForeground",
      "MenuItem:MenuItemAccelerator[MouseOver].textForeground",
      "MenuItem:MenuItemAccelerator[Disabled].textForeground",
      "CheckBoxMenuItem.background",
      "CheckBoxMenuItem.foreground",
      "CheckBoxMenuItem.selectionBackground",
      "CheckBoxMenuItem.selectionForeground",
      "CheckBoxMenuItem.disabledForeground",
      "CheckBoxMenuItem[Enabled].textForeground",
      "CheckBoxMenuItem[Disabled].textForeground",
      "CheckBoxMenuItem[MouseOver].textForeground",
      "CheckBoxMenuItem[MouseOver+Selected].textForeground",
      "CheckBoxMenuItem:MenuItemAccelerator[MouseOver].textForeground",
      "RadioButtonMenuItem.background",
      "RadioButtonMenuItem.foreground",
      "RadioButtonMenuItem.selectionBackground",
      "RadioButtonMenuItem.selectionForeground",
      "RadioButtonMenuItem.disabledForeground",
      "RadioButtonMenuItem[Enabled].textForeground",
      "RadioButtonMenuItem[Disabled].textForeground",
      "RadioButtonMenuItem[MouseOver].textForeground",
      "RadioButtonMenuItem[MouseOver+Selected].textForeground",
      "RadioButtonMenuItem:MenuItemAccelerator[MouseOver].textForeground",
      "PopupMenu.background",
      "PopupMenu.foreground",
      "PopupMenu.borderColor",
      "PopupMenuSeparator.foreground",
      "PopupMenuSeparator.background",
      "Separator.foreground",
      "Separator.background",
      "ToolBar.separatorColor",
      "SplitPane.background",
      "SplitPane.foreground",
      "SplitPaneDivider.draggingColor",
      "ScrollPane.borderColor",
      "TabbedPane.focus"
  };

  private static final String[] NIMBUS_TINT_OVERRIDE_KEYS = {
      "control",
      "info",
      "nimbusBase",
      "nimbusBlueGrey",
      "nimbusBorder",
      "nimbusLightBackground",
      "nimbusFocus",
      "nimbusSelectionBackground",
      "nimbusSelectedText",
      "nimbusDisabledText",
      "nimbusInfoBlue",
      "nimbusOrange",
      "nimbusAlertYellow",
      "nimbusRed",
      "nimbusGreen",
      "textHighlight",
      "textHighlightText",
      "text",
      "textForeground",
      "textText",
      "controlText",
      "Label.foreground",
      "Panel.background",
      "menu",
      "Component.focusColor",
      "Component.accentColor",
      "Component.linkColor",
      "TextField.background",
      "TextField.foreground",
      "TextArea.background",
      "TextArea.foreground",
      "List.background",
      "List.foreground",
      "Table.background",
      "Table.foreground",
      "Tree.textBackground",
      "Tree.textForeground",
      "TextComponent.selectionBackground",
      "TextComponent.selectionForeground",
      "List.selectionBackground",
      "List.selectionForeground",
      "Table.selectionBackground",
      "Table.selectionForeground",
      "Tree.selectionBackground",
      "Tree.selectionForeground",
      "MenuBar.background",
      "MenuBar.foreground",
      "Menu.background",
      "Menu.foreground",
      "Menu.selectionBackground",
      "Menu.selectionForeground",
      "MenuItem.background",
      "MenuItem.foreground",
      "MenuItem.selectionBackground",
      "MenuItem.selectionForeground",
      "CheckBoxMenuItem.background",
      "CheckBoxMenuItem.foreground",
      "CheckBoxMenuItem.selectionBackground",
      "CheckBoxMenuItem.selectionForeground",
      "RadioButtonMenuItem.background",
      "RadioButtonMenuItem.foreground",
      "RadioButtonMenuItem.selectionBackground",
      "RadioButtonMenuItem.selectionForeground",
      "PopupMenu.background",
      "PopupMenu.foreground",
      "PopupMenu.borderColor",
      "Button.select",
      "ToggleButton.select",
      "TabbedPane.focus"
  };

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
      Map.entry("List.selectionForeground", "#0D1D33"),
      Map.entry("Table.selectionBackground", "#AFC3E5"),
      Map.entry("Table.selectionForeground", "#0D1D33"),
      Map.entry("Tree.selectionBackground", "#AFC3E5"),
      Map.entry("Tree.selectionForeground", "#0D1D33")
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
      new LegacySystemThemeDefinition("nimbus-dark", "Nimbus (Dark)", ThemeTone.DARK, NIMBUS_LAF_CLASS, false),
      new LegacySystemThemeDefinition("nimbus-dark-amber", "Nimbus (Dark Amber)", ThemeTone.DARK, NIMBUS_LAF_CLASS, false),
      new LegacySystemThemeDefinition("nimbus-dark-blue", "Nimbus (Dark Blue)", ThemeTone.DARK, NIMBUS_LAF_CLASS, false),
      new LegacySystemThemeDefinition("nimbus-dark-violet", "Nimbus (Dark Violet)", ThemeTone.DARK, NIMBUS_LAF_CLASS, false),
      new LegacySystemThemeDefinition("nimbus-orange", "Nimbus (Orange)", ThemeTone.LIGHT, NIMBUS_LAF_CLASS, false),
      new LegacySystemThemeDefinition("nimbus-green", "Nimbus (Green)", ThemeTone.LIGHT, NIMBUS_LAF_CLASS, false),
      new LegacySystemThemeDefinition("nimbus-blue", "Nimbus (Blue)", ThemeTone.LIGHT, NIMBUS_LAF_CLASS, false),
      new LegacySystemThemeDefinition("nimbus-violet", "Nimbus (Violet)", ThemeTone.LIGHT, NIMBUS_LAF_CLASS, false),
      new LegacySystemThemeDefinition("nimbus-magenta", "Nimbus (Magenta)", ThemeTone.LIGHT, NIMBUS_LAF_CLASS, false),
      new LegacySystemThemeDefinition("nimbus-amber", "Nimbus (Amber)", ThemeTone.LIGHT, NIMBUS_LAF_CLASS, false),
      new LegacySystemThemeDefinition("metal-ocean", "Metal (Ocean)", ThemeTone.LIGHT, METAL_LAF_CLASS, true),
      new LegacySystemThemeDefinition("metal-steel", "Metal (Steel)", ThemeTone.LIGHT, METAL_LAF_CLASS, false),
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

  private static List<ThemeOption> darkLafThemes() {
    if (!DarkLafSupport.isAvailable()) return List.of();
    return List.of(
        new ThemeOption("darklaf", "DarkLaf (One Dark)", ThemeTone.DARK, ThemePack.DARKLAF, true),
        new ThemeOption("darklaf-darcula", "DarkLaf (Darcula)", ThemeTone.DARK, ThemePack.DARKLAF, false),
        new ThemeOption("darklaf-solarized-dark", "DarkLaf (Solarized Dark)", ThemeTone.DARK, ThemePack.DARKLAF, false),
        new ThemeOption("darklaf-high-contrast-dark", "DarkLaf (High Contrast Dark)", ThemeTone.DARK, ThemePack.DARKLAF, false),
        new ThemeOption("darklaf-light", "DarkLaf (Solarized Light)", ThemeTone.LIGHT, ThemePack.DARKLAF, false),
        new ThemeOption("darklaf-high-contrast-light", "DarkLaf (High Contrast Light)", ThemeTone.LIGHT, ThemePack.DARKLAF, false),
        new ThemeOption("darklaf-intellij", "DarkLaf (IntelliJ)", ThemeTone.LIGHT, ThemePack.DARKLAF, false));
  }

  private static ThemeOption[] allThemes() {
    ThemeOption[] cached = CACHED_THEMES;
    if (cached != null) return cached;

    List<ThemeOption> out = new ArrayList<>();
    Collections.addAll(out, BASE_THEMES);
    out.addAll(darkLafThemes());
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
    out.addAll(darkLafThemes());
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
    ThemeOption[] all = allThemes();
    List<ThemeOption> featured = Arrays.stream(all)
        .filter(ThemeOption::featured)
        .toList();

    List<ThemeOption> out = new ArrayList<>(featured.size());
    addFeaturedById(out, featured, "darcula");
    addFeaturedById(out, featured, "darklaf");
    for (ThemeOption t : featured) {
      if (t == null || t.id() == null) continue;
      if ("darcula".equalsIgnoreCase(t.id())) continue;
      if ("darklaf".equalsIgnoreCase(t.id())) continue;
      out.add(t);
    }
    return out.toArray(ThemeOption[]::new);
  }

  private static void addFeaturedById(List<ThemeOption> out, List<ThemeOption> featured, String wantedId) {
    if (out == null || featured == null || wantedId == null || wantedId.isBlank()) return;
    for (ThemeOption t : featured) {
      if (t == null || t.id() == null) continue;
      if (t.id().equalsIgnoreCase(wantedId)) {
        out.add(t);
        return;
      }
    }
  }

  public void installLookAndFeel(String themeId) {
    runOnEdt(() -> {
      setLookAndFeel(themeId);
      applyCommonTweaks(tweakSettingsBus != null ? tweakSettingsBus.get() : null);
      applyAccentOverrides(accentSettingsBus != null ? accentSettingsBus.get() : null);
      SvgIcons.clearCache();

      // Ensure chat styles pick up the correct UI defaults for the chosen LAF.
      chatStyles.reload();
    });
  }

  public void applyTheme(String themeId) {
    runOnEdt(() -> {
      boolean snap = false;
      boolean animateFlat = isFlatLafActive() || isLikelyFlatTarget(themeId);
      try {
        // Clear any stale animation layer from previous LAF transitions.
        try {
          FlatAnimatedLafChange.stop();
        } catch (Exception ignored) {
        }
        if (animateFlat) {
          // Only animate if there is at least one showing window.
          for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing()) {
              FlatAnimatedLafChange.showSnapshot();
              snap = true;
              break;
            }
          }
        }
      } catch (Exception ignored) {
        snap = false;
      }

      try {
        setLookAndFeel(themeId);
        applyCommonTweaks(tweakSettingsBus != null ? tweakSettingsBus.get() : null);
        applyAccentOverrides(accentSettingsBus != null ? accentSettingsBus.get() : null);
        SvgIcons.clearCache();

        if (isFlatLafActive()) {
          // FlatLaf can update all windows; we also run a componentTreeUI update for safety.
          try {
            FlatLaf.updateUI();
          } catch (Exception ignored) {
          }
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
            try {
              FlatAnimatedLafChange.stop();
            } catch (Exception ignored2) {
            }
          }
        } else {
          try {
            FlatAnimatedLafChange.stop();
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
      boolean animateFlat = animate && isFlatLafActive();

      if (animateFlat) {
        try {
          // Clear any stale animation layer from previous LAF transitions.
          try {
            FlatAnimatedLafChange.stop();
          } catch (Exception ignored) {
          }
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

        if (isFlatLafActive()) {
          try {
            FlatLaf.updateUI();
          } catch (Exception ignored) {
          }
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
            try {
              FlatAnimatedLafChange.stop();
            } catch (Exception ignored2) {
            }
          }
        } else if (animateFlat) {
          try {
            FlatAnimatedLafChange.stop();
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
    if (!isNimbusDarkVariant(lower)) {
      clearNimbusDarkOverrides();
    }
    if (!isNimbusTintVariant(lower)) {
      clearNimbusTintOverrides();
    }

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
        case "nimbus-dark" -> applyNimbusDarkLookAndFeelOrFallback();
        case "nimbus-dark-amber" -> applyNimbusDarkAmberLookAndFeelOrFallback();
        case "nimbus-dark-blue" -> applyNimbusDarkBlueLookAndFeelOrFallback();
        case "nimbus-dark-violet" -> applyNimbusDarkVioletLookAndFeelOrFallback();
        case "nimbus-orange" -> applyNimbusOrangeLookAndFeelOrFallback();
        case "nimbus-green" -> applyNimbusGreenLookAndFeelOrFallback();
        case "nimbus-blue" -> applyNimbusBlueLookAndFeelOrFallback();
        case "nimbus-violet" -> applyNimbusVioletLookAndFeelOrFallback();
        case "nimbus-magenta" -> applyNimbusMagentaLookAndFeelOrFallback();
        case "nimbus-amber" -> applyNimbusAmberLookAndFeelOrFallback();
        // Keep legacy "metal" id mapped to Ocean for backward compatibility.
        case "metal", "metal-ocean" -> applyMetalLookAndFeelOrFallback(false);
        case "metal-steel" -> applyMetalLookAndFeelOrFallback(true);
        case "motif" -> applyLegacySystemLookAndFeelOrFallback(MOTIF_LAF_CLASS);
        case "windows" -> applyLegacySystemLookAndFeelOrFallback(WINDOWS_LAF_CLASS);
        case "gtk" -> applyLegacySystemLookAndFeelOrFallback(GTK_LAF_CLASS);
        case "light" -> UIManager.setLookAndFeel(new FlatLightLaf());
        case "darcula" -> UIManager.setLookAndFeel(new FlatDarculaLaf());
        case "darklaf" -> {
          if (!DarkLafSupport.installDefault()) {
            UIManager.setLookAndFeel(new FlatDarculaLaf());
          } else {
            ensureBaselineAccentContrast();
          }
        }
        case "darklaf-darcula" -> {
          if (!DarkLafSupport.installDarcula()) {
            UIManager.setLookAndFeel(new FlatDarculaLaf());
          } else {
            ensureBaselineAccentContrast();
          }
        }
        case "darklaf-solarized-dark" -> {
          if (!DarkLafSupport.installSolarizedDark()) {
            UIManager.setLookAndFeel(new FlatDarculaLaf());
          } else {
            ensureBaselineAccentContrast();
          }
        }
        case "darklaf-high-contrast-dark" -> {
          if (!DarkLafSupport.installHighContrastDark()) {
            UIManager.setLookAndFeel(new FlatDarculaLaf());
          } else {
            ensureBaselineAccentContrast();
          }
        }
        case "darklaf-light" -> {
          if (!DarkLafSupport.installLight()) {
            UIManager.setLookAndFeel(new FlatLightLaf());
          } else {
            ensureBaselineAccentContrast();
          }
        }
        case "darklaf-high-contrast-light" -> {
          if (!DarkLafSupport.installHighContrastLight()) {
            UIManager.setLookAndFeel(new FlatLightLaf());
          } else {
            ensureBaselineAccentContrast();
          }
        }
        case "darklaf-intellij" -> {
          if (!DarkLafSupport.installIntelliJ()) {
            UIManager.setLookAndFeel(new FlatLightLaf());
          } else {
            ensureBaselineAccentContrast();
          }
        }
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

      if (!isFlatLafActive()) {
        clearFlatAccentDefaults();
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

  private void applyMetalLookAndFeelOrFallback(boolean steel) throws Exception {
    // Must be set before applying MetalLookAndFeel.
    MetalLookAndFeel.setCurrentTheme(steel ? new DefaultMetalTheme() : new OceanTheme());
    applyLegacySystemLookAndFeelOrFallback(METAL_LAF_CLASS);

    if (steel) {
      // Steel's default focus color is very close to its panel tone on some JDKs.
      UIManager.put("Component.focusColor", uiColor(0x2D, 0x5B, 0x8A));
    }
  }

  private void applyNimbusDarkLookAndFeelOrFallback() throws Exception {
    applyNimbusDarkOverrides();
    applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
    LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName())) {
      // Nimbus caches painters/state maps during install; re-applying here keeps idle states dark.
      applyNimbusDarkOverrides();
    }
  }

  private void applyNimbusDarkAmberLookAndFeelOrFallback() throws Exception {
    applyNimbusDarkAmberOverrides();
    applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
    LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName())) {
      applyNimbusDarkAmberOverrides();
    }
  }

  private void applyNimbusDarkBlueLookAndFeelOrFallback() throws Exception {
    applyNimbusDarkBlueOverrides();
    applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
    LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName())) {
      applyNimbusDarkBlueOverrides();
    }
  }

  private void applyNimbusDarkVioletLookAndFeelOrFallback() throws Exception {
    applyNimbusDarkVioletOverrides();
    applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
    LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName())) {
      applyNimbusDarkVioletOverrides();
    }
  }

  private void applyNimbusOrangeLookAndFeelOrFallback() throws Exception {
    applyNimbusOrangeOverrides();
    applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
    LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName())) {
      // Nimbus caches painters/state maps during install; re-apply after install for consistency.
      applyNimbusOrangeOverrides();
    }
  }

  private void applyNimbusGreenLookAndFeelOrFallback() throws Exception {
    applyNimbusGreenOverrides();
    applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
    LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName())) {
      applyNimbusGreenOverrides();
    }
  }

  private void applyNimbusBlueLookAndFeelOrFallback() throws Exception {
    applyNimbusBlueOverrides();
    applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
    LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName())) {
      applyNimbusBlueOverrides();
    }
  }

  private void applyNimbusVioletLookAndFeelOrFallback() throws Exception {
    applyNimbusVioletOverrides();
    applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
    LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName())) {
      applyNimbusVioletOverrides();
    }
  }

  private void applyNimbusMagentaLookAndFeelOrFallback() throws Exception {
    applyNimbusMagentaOverrides();
    applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
    LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName())) {
      applyNimbusMagentaOverrides();
    }
  }

  private void applyNimbusAmberLookAndFeelOrFallback() throws Exception {
    applyNimbusAmberOverrides();
    applyLegacySystemLookAndFeelOrFallback(NIMBUS_LAF_CLASS);
    LookAndFeel laf = UIManager.getLookAndFeel();
    if (laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName())) {
      applyNimbusAmberOverrides();
    }
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

    UIManager.put("control", control);
    UIManager.put("info", control);
    UIManager.put("nimbusBase", uiColor(0x1A, 0x24, 0x31));
    UIManager.put("nimbusBlueGrey", uiColor(0x2A, 0x31, 0x3A));
    UIManager.put("nimbusBorder", border);
    UIManager.put("nimbusLightBackground", bg);
    UIManager.put("nimbusFocus", focus);
    UIManager.put("nimbusSelectionBackground", selectionBg);
    UIManager.put("nimbusSelectedText", selectionFg);
    UIManager.put("nimbusDisabledText", disabledText);
    UIManager.put("nimbusInfoBlue", uiColor(0x53, 0x6C, 0x85));
    UIManager.put("nimbusAlertYellow", warning);
    UIManager.put("nimbusOrange", uiColor(0xC7, 0x84, 0x49));
    UIManager.put("nimbusRed", error);
    UIManager.put("nimbusGreen", uiColor(0x6F, 0xAD, 0x7A));
    UIManager.put("textHighlight", selectionBg);
    UIManager.put("textHighlightText", selectionFg);
    UIManager.put("text", text);
    UIManager.put("textForeground", text);
    UIManager.put("textText", text);
    UIManager.put("controlText", text);
    UIManager.put("controlDkShadow", uiColor(0x1B, 0x1F, 0x24));
    UIManager.put("controlShadow", uiColor(0x31, 0x37, 0x41));
    UIManager.put("controlLtHighlight", uiColor(0x4A, 0x52, 0x5F));
    UIManager.put("Label.foreground", text);
    UIManager.put("Panel.background", control);
    UIManager.put("menu", menuBg);
    UIManager.put("Component.focusColor", focus);
    UIManager.put("Component.accentColor", focus);
    UIManager.put("Component.linkColor", link);
    UIManager.put("Component.borderColor", border);
    UIManager.put("Component.warningColor", warning);
    UIManager.put("Component.warning.outlineColor", warning);
    UIManager.put("Component.warning.borderColor", warning);
    UIManager.put("Component.warning.focusedBorderColor", warning);
    UIManager.put("Component.warning.focusColor", warning);
    UIManager.put("Component.errorColor", error);
    UIManager.put("Component.error.outlineColor", error);
    UIManager.put("Component.error.borderColor", error);
    UIManager.put("Component.error.focusedBorderColor", error);
    UIManager.put("Component.error.focusColor", error);

    UIManager.put("TextField.background", bg);
    UIManager.put("TextField.foreground", text);
    UIManager.put("TextField.borderColor", border);
    UIManager.put("TextArea.background", bg);
    UIManager.put("TextArea.foreground", text);
    UIManager.put("TextComponent.selectionBackground", selectionBg);
    UIManager.put("TextComponent.selectionForeground", selectionFg);
    UIManager.put("ComboBox.background", bg);
    UIManager.put("ComboBox.foreground", text);
    UIManager.put("ComboBox.disabled", control);
    UIManager.put("ComboBox.disabledText", disabledText);
    UIManager.put("ComboBox.selectionBackground", selectionBg);
    UIManager.put("ComboBox.selectionForeground", selectionFg);
    UIManager.put("ComboBox.rendererUseListColors", Boolean.TRUE);
    UIManager.put("ComboBox:\"ComboBox.listRenderer\".background", bg);
    UIManager.put("ComboBox:\"ComboBox.listRenderer\".textForeground", text);
    UIManager.put("ComboBox:\"ComboBox.listRenderer\"[Selected].background", selectionBg);
    UIManager.put("ComboBox:\"ComboBox.listRenderer\"[Selected].textForeground", selectionFg);
    UIManager.put("ComboBox:\"ComboBox.renderer\".background", bg);
    UIManager.put("ComboBox:\"ComboBox.renderer\".textForeground", text);
    UIManager.put("ComboBox:\"ComboBox.renderer\"[Selected].background", selectionBg);
    UIManager.put("ComboBox:\"ComboBox.renderer\"[Selected].textForeground", selectionFg);
    UIManager.put("ComboBox:\"ComboBox.renderer\"[Disabled].textForeground", disabledText);
    UIManager.put("ComboBox:\"ComboBox.textField\".background", bg);
    UIManager.put("ComboBox:\"ComboBox.textField\".textForeground", text);
    UIManager.put("ComboBox:\"ComboBox.textField\"[Selected].textForeground", selectionFg);
    UIManager.put("ComboBox:\"ComboBox.arrowButton\".background", control);
    UIManager.put("ComboBox:\"ComboBox.arrowButton\".foreground", text);
    UIManager.put("Button.background", control);
    UIManager.put("Button.foreground", text);
    UIManager.put("Button.disabledText", disabledText);
    UIManager.put("Button.select", selectionBg);
    UIManager.put("Button[Enabled].textForeground", text);
    UIManager.put("Button[Disabled].textForeground", disabledText);
    UIManager.put("Button[Pressed].textForeground", selectionFg);
    UIManager.put("Button[Default].textForeground", text);
    UIManager.put("Button[Default+Pressed].textForeground", selectionFg);
    UIManager.put("ToggleButton.background", control);
    UIManager.put("ToggleButton.foreground", text);
    UIManager.put("ToggleButton.disabledText", disabledText);
    UIManager.put("ToggleButton.select", selectionBg);
    UIManager.put("CheckBox.background", control);
    UIManager.put("CheckBox.foreground", text);
    UIManager.put("CheckBox.disabledText", disabledText);
    UIManager.put("RadioButton.background", control);
    UIManager.put("RadioButton.foreground", text);
    UIManager.put("RadioButton.disabledText", disabledText);

    UIManager.put("List.background", bg);
    UIManager.put("List.foreground", text);
    UIManager.put("List.selectionBackground", selectionBg);
    UIManager.put("List.selectionForeground", selectionFg);

    UIManager.put("Table.background", bg);
    UIManager.put("Table.foreground", text);
    UIManager.put("Table.gridColor", separator);
    UIManager.put("Table.selectionBackground", selectionBg);
    UIManager.put("Table.selectionForeground", selectionFg);

    UIManager.put("Tree.textBackground", bg);
    UIManager.put("Tree.textForeground", text);
    UIManager.put("Tree.selectionBackground", selectionBg);
    UIManager.put("Tree.selectionForeground", selectionFg);

    UIManager.put("MenuBar.background", menuBg);
    UIManager.put("MenuBar.foreground", text);
    UIManager.put("MenuBar.borderColor", border);
    UIManager.put("Menu.background", menuBg);
    UIManager.put("Menu.foreground", text);
    UIManager.put("Menu.selectionBackground", menuSelectionBg);
    UIManager.put("Menu.selectionForeground", selectionFg);
    UIManager.put("Menu.disabledForeground", disabledText);
    UIManager.put("MenuBar:Menu[Enabled].textForeground", text);
    UIManager.put("MenuBar:Menu[Disabled].textForeground", disabledText);
    UIManager.put("MenuBar:Menu[Selected].textForeground", selectionFg);
    UIManager.put("Menu[Enabled].textForeground", text);
    UIManager.put("Menu[Disabled].textForeground", disabledText);
    UIManager.put("Menu[Enabled+Selected].textForeground", selectionFg);
    UIManager.put("Menu:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("MenuItem.background", menuBg);
    UIManager.put("MenuItem.foreground", text);
    UIManager.put("MenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("MenuItem.selectionForeground", selectionFg);
    UIManager.put("MenuItem.disabledForeground", disabledText);
    UIManager.put("MenuItem[Enabled].textForeground", text);
    UIManager.put("MenuItem[Disabled].textForeground", disabledText);
    UIManager.put("MenuItem[MouseOver].textForeground", selectionFg);
    UIManager.put("MenuItem:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("MenuItem:MenuItemAccelerator[Disabled].textForeground", disabledText);
    UIManager.put("CheckBoxMenuItem.background", menuBg);
    UIManager.put("CheckBoxMenuItem.foreground", text);
    UIManager.put("CheckBoxMenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("CheckBoxMenuItem.selectionForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem.disabledForeground", disabledText);
    UIManager.put("CheckBoxMenuItem[Enabled].textForeground", text);
    UIManager.put("CheckBoxMenuItem[Disabled].textForeground", disabledText);
    UIManager.put("CheckBoxMenuItem[MouseOver].textForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem[MouseOver+Selected].textForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem.background", menuBg);
    UIManager.put("RadioButtonMenuItem.foreground", text);
    UIManager.put("RadioButtonMenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("RadioButtonMenuItem.selectionForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem.disabledForeground", disabledText);
    UIManager.put("RadioButtonMenuItem[Enabled].textForeground", text);
    UIManager.put("RadioButtonMenuItem[Disabled].textForeground", disabledText);
    UIManager.put("RadioButtonMenuItem[MouseOver].textForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem[MouseOver+Selected].textForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("PopupMenu.background", menuBg);
    UIManager.put("PopupMenu.foreground", text);
    UIManager.put("PopupMenu.borderColor", border);
    UIManager.put("PopupMenuSeparator.foreground", separator);
    UIManager.put("PopupMenuSeparator.background", menuBg);
    UIManager.put("Separator.foreground", separator);
    UIManager.put("Separator.background", control);
    UIManager.put("ToolBar.separatorColor", separator);
    UIManager.put("SplitPane.background", control);
    UIManager.put("SplitPane.foreground", separator);
    UIManager.put("SplitPaneDivider.draggingColor", uiColor(0x50, 0x5F, 0x74));
    UIManager.put("ScrollPane.borderColor", border);
    UIManager.put("TabbedPane.focus", focus);
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
        uiColor(0x7B, 0x64, 0x40)
    );
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
        uiColor(0x55, 0x6B, 0x87)
    );
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
        uiColor(0x6A, 0x59, 0x91)
    );
  }

  private static void applyNimbusDarkAccentOverrides(ColorUIResource nimbusBase,
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

    ColorUIResource selectionFg = uiColor(0xF3, 0xF7, 0xFD);
    UIManager.put("nimbusBase", nimbusBase);
    UIManager.put("nimbusBlueGrey", nimbusBlueGrey);
    UIManager.put("nimbusFocus", focus);
    UIManager.put("nimbusSelectionBackground", selectionBg);
    UIManager.put("nimbusInfoBlue", nimbusInfoBlue);
    UIManager.put("nimbusOrange", nimbusOrange);
    UIManager.put("nimbusAlertYellow", nimbusAlertYellow);
    UIManager.put("nimbusRed", nimbusRed);
    UIManager.put("nimbusGreen", nimbusGreen);
    UIManager.put("Component.focusColor", focus);
    UIManager.put("Component.accentColor", focus);
    UIManager.put("Component.linkColor", link);
    UIManager.put("textHighlight", selectionBg);
    UIManager.put("textHighlightText", selectionFg);
    UIManager.put("TextComponent.selectionBackground", selectionBg);
    UIManager.put("TextComponent.selectionForeground", selectionFg);
    UIManager.put("List.selectionBackground", selectionBg);
    UIManager.put("List.selectionForeground", selectionFg);
    UIManager.put("Table.selectionBackground", selectionBg);
    UIManager.put("Table.selectionForeground", selectionFg);
    UIManager.put("Tree.selectionBackground", selectionBg);
    UIManager.put("Tree.selectionForeground", selectionFg);
    UIManager.put("Menu.selectionBackground", menuSelectionBg);
    UIManager.put("Menu.selectionForeground", selectionFg);
    UIManager.put("MenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("MenuItem.selectionForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("CheckBoxMenuItem.selectionForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("RadioButtonMenuItem.selectionForeground", selectionFg);
    UIManager.put("Button.select", selectionBg);
    UIManager.put("ToggleButton.select", selectionBg);
    UIManager.put("TabbedPane.focus", focus);
    UIManager.put("SplitPaneDivider.draggingColor", splitPaneDraggingColor);
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
        uiColor(0x8E, 0x7D, 0x6B)
    );
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
        uiColor(0x86, 0x9D, 0x80)
    );
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
        uiColor(0x84, 0x95, 0xAC)
    );
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
        uiColor(0x93, 0x83, 0xA8)
    );
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
        uiColor(0xA0, 0x7D, 0x92)
    );
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
        uiColor(0x99, 0x84, 0x5E)
    );
  }

  private static void applyNimbusTintOverrides(ColorUIResource control,
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
    UIManager.put("control", control);
    UIManager.put("info", bg);
    UIManager.put("nimbusBase", nimbusBase);
    UIManager.put("nimbusBlueGrey", nimbusBlueGrey);
    UIManager.put("nimbusBorder", border);
    UIManager.put("nimbusLightBackground", bg);
    UIManager.put("nimbusFocus", focus);
    UIManager.put("nimbusSelectionBackground", selectionBg);
    UIManager.put("nimbusSelectedText", selectionFg);
    UIManager.put("nimbusDisabledText", disabledText);
    UIManager.put("nimbusInfoBlue", nimbusInfoBlue);
    UIManager.put("nimbusOrange", nimbusOrange);
    UIManager.put("nimbusAlertYellow", nimbusAlertYellow);
    UIManager.put("nimbusRed", nimbusRed);
    UIManager.put("nimbusGreen", nimbusGreen);
    UIManager.put("textHighlight", selectionBg);
    UIManager.put("textHighlightText", selectionFg);
    UIManager.put("text", text);
    UIManager.put("textForeground", text);
    UIManager.put("textText", text);
    UIManager.put("controlText", text);
    UIManager.put("Label.foreground", text);
    UIManager.put("Panel.background", control);
    UIManager.put("menu", menuBg);
    UIManager.put("Component.focusColor", focus);
    UIManager.put("Component.accentColor", focus);
    UIManager.put("Component.linkColor", link);
    UIManager.put("TextField.background", bg);
    UIManager.put("TextField.foreground", text);
    UIManager.put("TextArea.background", bg);
    UIManager.put("TextArea.foreground", text);
    UIManager.put("List.background", bg);
    UIManager.put("List.foreground", text);
    UIManager.put("Table.background", bg);
    UIManager.put("Table.foreground", text);
    UIManager.put("Tree.textBackground", bg);
    UIManager.put("Tree.textForeground", text);
    UIManager.put("TextComponent.selectionBackground", selectionBg);
    UIManager.put("TextComponent.selectionForeground", selectionFg);
    UIManager.put("List.selectionBackground", selectionBg);
    UIManager.put("List.selectionForeground", selectionFg);
    UIManager.put("Table.selectionBackground", selectionBg);
    UIManager.put("Table.selectionForeground", selectionFg);
    UIManager.put("Tree.selectionBackground", selectionBg);
    UIManager.put("Tree.selectionForeground", selectionFg);
    UIManager.put("MenuBar.background", menuBg);
    UIManager.put("MenuBar.foreground", text);
    UIManager.put("Menu.background", menuBg);
    UIManager.put("Menu.foreground", text);
    UIManager.put("Menu.selectionBackground", menuSelectionBg);
    UIManager.put("Menu.selectionForeground", selectionFg);
    UIManager.put("MenuItem.background", menuBg);
    UIManager.put("MenuItem.foreground", text);
    UIManager.put("MenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("MenuItem.selectionForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem.background", menuBg);
    UIManager.put("CheckBoxMenuItem.foreground", text);
    UIManager.put("CheckBoxMenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("CheckBoxMenuItem.selectionForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem.background", menuBg);
    UIManager.put("RadioButtonMenuItem.foreground", text);
    UIManager.put("RadioButtonMenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("RadioButtonMenuItem.selectionForeground", selectionFg);
    UIManager.put("PopupMenu.background", menuBg);
    UIManager.put("PopupMenu.foreground", text);
    UIManager.put("PopupMenu.borderColor", border);
    UIManager.put("Button.select", selectionBg);
    UIManager.put("ToggleButton.select", selectionBg);
    UIManager.put("TabbedPane.focus", focus);
  }

  private static ColorUIResource uiColor(int r, int g, int b) {
    return new ColorUIResource(r, g, b);
  }

  private static void clearNimbusDarkOverrides() {
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

  private static boolean isNimbusTintVariant(String lower) {
    return "nimbus-orange".equals(lower)
        || "nimbus-green".equals(lower)
        || "nimbus-blue".equals(lower)
        || "nimbus-violet".equals(lower)
        || "nimbus-magenta".equals(lower)
        || "nimbus-amber".equals(lower);
  }

  private static boolean isNimbusDarkVariant(String lower) {
    return "nimbus-dark".equals(lower)
        || "nimbus-dark-amber".equals(lower)
        || "nimbus-dark-blue".equals(lower)
        || "nimbus-dark-violet".equals(lower);
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
      UIManager.put("Component.focusColor", ensureContrastAgainstBackground(focus, panelBg, 1.25));
    }

    Color link = UIManager.getColor("Component.linkColor");
    if (link == null) link = focus;
    if (link != null) {
      UIManager.put("Component.linkColor", ensureContrastAgainstBackground(link, panelBg, 1.25));
    }
  }

  private static boolean isLookAndFeelInstalled(String className) {
    if (className == null || className.isBlank()) return false;
    return installedLookAndFeelClassNames().contains(className.toLowerCase(Locale.ROOT));
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
    ThemeTweakSettings t = tweaks != null ? tweaks : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10);
    clearCommonTweakOverrides();

    // Keep non-Flat look and feels as close to their native defaults as possible.
    if (!isFlatLafActive()) {
      return;
    }

    // Rounded corners and a softer modern vibe for FlatLaf variants.
    int arc = t.cornerRadius();
    UIManager.put("Component.arc", arc);
    UIManager.put("Button.arc", arc);
    UIManager.put("TextComponent.arc", arc);
    UIManager.put("ProgressBar.arc", arc);
    UIManager.put("ScrollPane.arc", arc);

    // Auto means "use the selected theme's built-in spacing".
    ThemeTweakSettings.ThemeDensity d = t.density();
    if (d == ThemeTweakSettings.ThemeDensity.AUTO) {
      return;
    }

    int rowHeight = switch (d) {
      case COMPACT -> 20;
      case SPACIOUS -> 28;
      default -> 22;
    };

    UIManager.put("Tree.rowHeight", rowHeight);
    UIManager.put("Table.rowHeight", rowHeight);
    UIManager.put("List.cellHeight", rowHeight);

    Insets buttonMargin = switch (d) {
      case COMPACT -> new Insets(4, 10, 4, 10);
      case SPACIOUS -> new Insets(8, 14, 8, 14);
      default -> new Insets(5, 10, 5, 10);
    };

    Insets textMargin = switch (d) {
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

  private static void clearCommonTweakOverrides() {
    for (String key : COMMON_TWEAK_OVERRIDE_KEYS) {
      // Null clears the developer override and falls back to the active LAF defaults.
      UIManager.put(key, null);
    }
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

    Color panelBg = UIManager.getColor("Panel.background");
    if (panelBg == null) panelBg = UIManager.getColor("control");
    boolean dark = isDark(panelBg);
    Color focus = dark ? lighten(blended, 0.20) : darken(blended, 0.10);
    Color link = dark ? lighten(blended, 0.28) : darken(blended, 0.12);

    // Keep accents readable on non-Flat LAFs that may have very close default panel tones.
    if (!isFlatLafActive() && panelBg != null) {
      focus = ensureContrastAgainstBackground(focus, panelBg, 1.25);
      link = ensureContrastAgainstBackground(link, panelBg, 1.25);
    }

    if (isFlatLafActive()) {
      // Core FlatLaf accent keys (accept Color values).
      UIManager.put("@accentColor", blended);
      UIManager.put("@accentBaseColor", blended);
      UIManager.put("@accentBase2Color", focus);
    }

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

  private static Color ensureContrastAgainstBackground(Color fg, Color bg, double minRatio) {
    if (fg == null || bg == null) return fg;
    if (contrastRatio(fg, bg) >= minRatio) return fg;

    Color best = fg;
    double bestRatio = contrastRatio(fg, bg);

    for (int i = 1; i <= 12; i++) {
      double t = i / 12.0;
      Color lighter = mix(fg, Color.WHITE, t);
      Color darker = mix(fg, Color.BLACK, t);
      double lighterRatio = contrastRatio(lighter, bg);
      double darkerRatio = contrastRatio(darker, bg);

      if (lighterRatio >= minRatio || darkerRatio >= minRatio) {
        if (lighterRatio >= minRatio && darkerRatio >= minRatio) {
          return lighterRatio >= darkerRatio ? lighter : darker;
        }
        return lighterRatio >= minRatio ? lighter : darker;
      }

      if (lighterRatio > bestRatio) {
        best = lighter;
        bestRatio = lighterRatio;
      }
      if (darkerRatio > bestRatio) {
        best = darker;
        bestRatio = darkerRatio;
      }
    }

    return best;
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

  private static double contrastRatio(Color c1, Color c2) {
    if (c1 == null || c2 == null) return 1.0;
    double l1 = relativeLuminance(c1);
    double l2 = relativeLuminance(c2);
    double lighter = Math.max(l1, l2);
    double darker = Math.min(l1, l2);
    return (lighter + 0.05) / (darker + 0.05);
  }

  private static Color bestTextColor(Color bg) {
    if (bg == null) return Color.WHITE;
    return relativeLuminance(bg) > 0.55 ? Color.BLACK : Color.WHITE;
  }

  private static boolean isFlatLafActive() {
    LookAndFeel laf = UIManager.getLookAndFeel();
    return laf instanceof FlatLaf;
  }

  private static boolean isLikelyFlatTarget(String themeId) {
    String raw = themeId != null ? themeId.trim() : "";
    if (raw.isEmpty()) return true; // defaults to darcula

    if (raw.regionMatches(true, 0, IntelliJThemePack.ID_PREFIX, 0, IntelliJThemePack.ID_PREFIX.length())) {
      return true;
    }
    if (looksLikeClassName(raw)) {
      return raw.toLowerCase(Locale.ROOT).contains("flatlaf");
    }

    String lower = raw.toLowerCase(Locale.ROOT);
    return switch (lower) {
      case "system", "nimbus", "nimbus-dark", "nimbus-dark-amber", "nimbus-dark-blue", "nimbus-dark-violet", "nimbus-orange", "nimbus-green", "nimbus-blue", "nimbus-violet", "nimbus-magenta", "nimbus-amber", "metal", "metal-ocean", "metal-steel", "motif", "windows", "gtk", "darklaf", "darklaf-darcula", "darklaf-solarized-dark", "darklaf-high-contrast-dark", "darklaf-light", "darklaf-high-contrast-light", "darklaf-intellij" -> false;
      default -> true;
    };
  }

  private static void runOnEdt(Runnable r) {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
    } else {
      SwingUtilities.invokeLater(r);
    }
  }
}

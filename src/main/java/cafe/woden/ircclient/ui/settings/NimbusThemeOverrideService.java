package cafe.woden.ircclient.ui.settings;

import java.awt.Color;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
class NimbusThemeOverrideService {

  private static final Logger log = LoggerFactory.getLogger(NimbusThemeOverrideService.class);

  private record NimbusVariantSpec(boolean darkVariant, Runnable applyOverrides) {}

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
      "MenuItem:MenuItemAccelerator[Enabled].textForeground",
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
      "CheckBoxMenuItem:MenuItemAccelerator[Enabled].textForeground",
      "CheckBoxMenuItem:MenuItemAccelerator[MouseOver].textForeground",
      "CheckBoxMenuItem:MenuItemAccelerator[Disabled].textForeground",
      "RadioButtonMenuItem.background",
      "RadioButtonMenuItem.foreground",
      "RadioButtonMenuItem.selectionBackground",
      "RadioButtonMenuItem.selectionForeground",
      "RadioButtonMenuItem.disabledForeground",
      "RadioButtonMenuItem[Enabled].textForeground",
      "RadioButtonMenuItem[Disabled].textForeground",
      "RadioButtonMenuItem[MouseOver].textForeground",
      "RadioButtonMenuItem[MouseOver+Selected].textForeground",
      "RadioButtonMenuItem:MenuItemAccelerator[Enabled].textForeground",
      "RadioButtonMenuItem:MenuItemAccelerator[MouseOver].textForeground",
      "RadioButtonMenuItem:MenuItemAccelerator[Disabled].textForeground",
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
      "PasswordField.background",
      "PasswordField.borderColor",
      "PasswordField.foreground",
      "FormattedTextField.background",
      "FormattedTextField.borderColor",
      "FormattedTextField.foreground",
      "TextPane.background",
      "TextPane.foreground",
      "EditorPane.background",
      "EditorPane.foreground",
      "Tree.background",
      "TableHeader.background",
      "TableHeader.foreground",
      "TableHeader:\"TableHeader.renderer\".background",
      "TableHeader:\"TableHeader.renderer\".foreground",
      "Viewport.background",
      "Viewport.foreground",
      "Spinner.background",
      "Spinner.foreground",
      "Spinner:\"Spinner.formattedTextField\".background",
      "Spinner:\"Spinner.formattedTextField\".foreground",
      "ToolTip.background",
      "ToolTip.foreground",
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
      "MenuItem:MenuItemAccelerator[Enabled].textForeground",
      "MenuItem:MenuItemAccelerator[MouseOver].textForeground",
      "MenuItem:MenuItemAccelerator[Disabled].textForeground",
      "CheckBoxMenuItem.background",
      "CheckBoxMenuItem.foreground",
      "CheckBoxMenuItem.selectionBackground",
      "CheckBoxMenuItem.selectionForeground",
      "CheckBoxMenuItem:MenuItemAccelerator[Enabled].textForeground",
      "CheckBoxMenuItem:MenuItemAccelerator[MouseOver].textForeground",
      "CheckBoxMenuItem:MenuItemAccelerator[Disabled].textForeground",
      "RadioButtonMenuItem.background",
      "RadioButtonMenuItem.foreground",
      "RadioButtonMenuItem.selectionBackground",
      "RadioButtonMenuItem.selectionForeground",
      "RadioButtonMenuItem:MenuItemAccelerator[Enabled].textForeground",
      "RadioButtonMenuItem:MenuItemAccelerator[MouseOver].textForeground",
      "RadioButtonMenuItem:MenuItemAccelerator[Disabled].textForeground",
      "PopupMenu.background",
      "PopupMenu.foreground",
      "PopupMenu.borderColor",
      "Button.select",
      "ToggleButton.select",
      "TabbedPane.focus"
  };

  private static final Map<String, NimbusVariantSpec> NIMBUS_VARIANTS =
      Map.ofEntries(
          Map.entry("nimbus-dark", new NimbusVariantSpec(true, NimbusThemeOverrideService::applyNimbusDarkOverrides)),
          Map.entry(
              "nimbus-dark-amber",
              new NimbusVariantSpec(true, NimbusThemeOverrideService::applyNimbusDarkAmberOverrides)),
          Map.entry(
              "nimbus-dark-blue",
              new NimbusVariantSpec(true, NimbusThemeOverrideService::applyNimbusDarkBlueOverrides)),
          Map.entry(
              "nimbus-dark-violet",
              new NimbusVariantSpec(true, NimbusThemeOverrideService::applyNimbusDarkVioletOverrides)),
          Map.entry(
              "nimbus-dark-green",
              new NimbusVariantSpec(true, NimbusThemeOverrideService::applyNimbusDarkGreenOverrides)),
          Map.entry(
              "nimbus-dark-orange",
              new NimbusVariantSpec(true, NimbusThemeOverrideService::applyNimbusDarkOrangeOverrides)),
          Map.entry(
              "nimbus-dark-magenta",
              new NimbusVariantSpec(true, NimbusThemeOverrideService::applyNimbusDarkMagentaOverrides)),
          Map.entry("nimbus-orange", new NimbusVariantSpec(false, NimbusThemeOverrideService::applyNimbusOrangeOverrides)),
          Map.entry("nimbus-green", new NimbusVariantSpec(false, NimbusThemeOverrideService::applyNimbusGreenOverrides)),
          Map.entry("nimbus-blue", new NimbusVariantSpec(false, NimbusThemeOverrideService::applyNimbusBlueOverrides)),
          Map.entry(
              "nimbus-violet",
              new NimbusVariantSpec(false, NimbusThemeOverrideService::applyNimbusVioletOverrides)),
          Map.entry(
              "nimbus-magenta",
              new NimbusVariantSpec(false, NimbusThemeOverrideService::applyNimbusMagentaOverrides)),
          Map.entry("nimbus-amber", new NimbusVariantSpec(false, NimbusThemeOverrideService::applyNimbusAmberOverrides)));

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
    logNimbusSnapshot("applyVariant", themeIdLower);
    return true;
  }

  void clearDarkOverrides() {
    clearNimbusDarkOverrides();
  }

  void clearTintOverrides() {
    clearNimbusTintOverrides();
  }

  private static Set<String> nimbusVariantIds(boolean darkVariants) {
    Set<String> out = new HashSet<>();
    NIMBUS_VARIANTS.forEach((id, spec) -> {
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
    UIManager.put("MenuItem:MenuItemAccelerator[Enabled].textForeground", text);
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
    UIManager.put("CheckBoxMenuItem:MenuItemAccelerator[Enabled].textForeground", text);
    UIManager.put("CheckBoxMenuItem:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem:MenuItemAccelerator[Disabled].textForeground", disabledText);
    UIManager.put("RadioButtonMenuItem.background", menuBg);
    UIManager.put("RadioButtonMenuItem.foreground", text);
    UIManager.put("RadioButtonMenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("RadioButtonMenuItem.selectionForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem.disabledForeground", disabledText);
    UIManager.put("RadioButtonMenuItem[Enabled].textForeground", text);
    UIManager.put("RadioButtonMenuItem[Disabled].textForeground", disabledText);
    UIManager.put("RadioButtonMenuItem[MouseOver].textForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem[MouseOver+Selected].textForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem:MenuItemAccelerator[Enabled].textForeground", text);
    UIManager.put("RadioButtonMenuItem:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem:MenuItemAccelerator[Disabled].textForeground", disabledText);
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
        uiColor(0x45, 0x6A, 0x55)
    );
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
        uiColor(0x89, 0x61, 0x46)
    );
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
        uiColor(0x7A, 0x54, 0x7C)
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

  // Tint the dark neutral surfaces for each Nimbus dark accent variant.
  // Without this, all dark variants inherit the same gray base from applyNimbusDarkOverrides().
  ColorUIResource control = new ColorUIResource(mix(nimbusBlueGrey, nimbusBase, 0.35));
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

  UIManager.put("control", control);
  UIManager.put("info", control);
  UIManager.put("Panel.background", control);
  UIManager.put("menu", menuBg);

  UIManager.put("nimbusLightBackground", bg);
  UIManager.put("nimbusBorder", border);

  UIManager.put("Component.borderColor", border);
  UIManager.put("controlShadow", controlShadow);
  UIManager.put("controlDkShadow", controlDkShadow);
  UIManager.put("controlLtHighlight", controlLtHighlight);

  UIManager.put("TextField.background", bg);
  UIManager.put("TextField.borderColor", border);

  UIManager.put("PasswordField.background", bg);
  UIManager.put("PasswordField.borderColor", border);

  UIManager.put("FormattedTextField.background", bg);
  UIManager.put("FormattedTextField.borderColor", border);

  UIManager.put("TextArea.background", bg);
  UIManager.put("TextPane.background", bg);
  UIManager.put("EditorPane.background", bg);

  UIManager.put("List.background", bg);
  UIManager.put("Tree.background", bg);
  UIManager.put("Tree.textBackground", bg);

  UIManager.put("Table.background", bg);
  UIManager.put("Table.gridColor", separator);
  UIManager.put("TableHeader.background", tableHeaderBg);
  UIManager.put("TableHeader:\"TableHeader.renderer\".background", tableHeaderBg);

  UIManager.put("Viewport.background", viewportBg);

  UIManager.put("ComboBox.background", bg);
  UIManager.put("ComboBox.disabled", control);
  UIManager.put("ComboBox:\"ComboBox.listRenderer\".background", bg);
  UIManager.put("ComboBox:\"ComboBox.renderer\".background", bg);
  UIManager.put("ComboBox:\"ComboBox.textField\".background", bg);
  UIManager.put("ComboBox:\"ComboBox.arrowButton\".background", control);

  UIManager.put("Spinner.background", bg);
  UIManager.put("Spinner:\"Spinner.formattedTextField\".background", bg);

  UIManager.put("Button.background", control);
  UIManager.put("ToggleButton.background", control);
  UIManager.put("CheckBox.background", control);
  UIManager.put("RadioButton.background", control);

  UIManager.put("MenuBar.background", menuBg);
  UIManager.put("MenuBar.borderColor", border);
  UIManager.put("Menu.background", menuBg);
  UIManager.put("MenuItem.background", menuBg);
  UIManager.put("CheckBoxMenuItem.background", menuBg);
  UIManager.put("RadioButtonMenuItem.background", menuBg);
  UIManager.put("PopupMenu.background", menuBg);

  UIManager.put("PopupMenu.borderColor", border);
  UIManager.put("PopupMenuSeparator.background", menuBg);
  UIManager.put("PopupMenuSeparator.foreground", separator);
  UIManager.put("ScrollPane.borderColor", border);
  UIManager.put("Separator.foreground", separator);
  UIManager.put("Separator.background", control);
  UIManager.put("ToolBar.separatorColor", separator);

  UIManager.put("ToolTip.background", tooltipBg);

  UIManager.put("SplitPane.background", control);
  UIManager.put("SplitPane.foreground", separator);

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
  Object labelText = UIManager.get("Label.foreground");
  if (labelText != null) {
    UIManager.put("PasswordField.foreground", labelText);
    UIManager.put("FormattedTextField.foreground", labelText);
    UIManager.put("TextPane.foreground", labelText);
    UIManager.put("EditorPane.foreground", labelText);
    UIManager.put("TableHeader.foreground", labelText);
    UIManager.put("TableHeader:\"TableHeader.renderer\".foreground", labelText);
    UIManager.put("ToolTip.foreground", labelText);
    UIManager.put("Viewport.foreground", labelText);
    UIManager.put("Spinner.foreground", labelText);
    UIManager.put("Spinner:\"Spinner.formattedTextField\".foreground", labelText);
  }

  UIManager.put("TabbedPane.focus", focus);
  UIManager.put("SplitPaneDivider.draggingColor", splitPaneDraggingColor);
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
    // The prior values were too close to panel tone for some dark variants.
    Color fieldBase = mix(mix(bg, nimbusBase, 0.34), focus, 0.16);
    Color fieldSurface = ThemeColorUtils.ensureContrastAgainstBackground(lighten(fieldBase, 0.12), panelBase, 1.18);
    ColorUIResource fieldBg = toUiResource(fieldSurface);

    Color areaBase = mix(mix(bg, nimbusBlueGrey, 0.26), focus, 0.10);
    Color areaSurface = ThemeColorUtils.ensureContrastAgainstBackground(lighten(areaBase, 0.10), panelBase, 1.12);
    ColorUIResource areaBg = toUiResource(areaSurface);

    ColorUIResource listBg = toUiResource(darken(bg, 0.015));
    ColorUIResource tableBg = toUiResource(darken(bg, 0.02));
    ColorUIResource treeBg = toUiResource(darken(bg, 0.01));
    ColorUIResource viewportBg = tableBg;

    ColorUIResource menuBarBg = toUiResource(lighten(menuBg, 0.03));
    ColorUIResource popupBg = toUiResource(lighten(menuBg, 0.015));
    ColorUIResource tooltipBg = toUiResource(lighten(menuBg, 0.07));
    ColorUIResource comboArrowBg = toUiResource(lighten(control, 0.05));
    ColorUIResource splitPaneBg = toUiResource(darken(control, 0.03));

    ColorUIResource tableHeaderBg = toUiResource(lighten(control, 0.09));
    ColorUIResource controlShadow = toUiResource(darken(control, 0.22));
    ColorUIResource controlDkShadow = toUiResource(darken(control, 0.36));
    ColorUIResource controlLtHighlight = toUiResource(lighten(control, 0.14));

    UIManager.put("controlShadow", controlShadow);
    UIManager.put("controlDkShadow", controlDkShadow);
    UIManager.put("controlLtHighlight", controlLtHighlight);

    UIManager.put("Panel.background", panelBg);

    UIManager.put("Button.background", buttonBg);
    UIManager.put("Button.foreground", text);
    UIManager.put("Button.disabledText", disabledText);
    UIManager.put("Button.select", selectionBg);
    UIManager.put("Button[Enabled].textForeground", text);
    UIManager.put("Button[Disabled].textForeground", disabledText);
    UIManager.put("Button[Pressed].textForeground", selectionFg);
    UIManager.put("Button[Default].textForeground", text);
    UIManager.put("Button[Default+Pressed].textForeground", selectionFg);

    UIManager.put("ToggleButton.background", toggleBg);
    UIManager.put("ToggleButton.foreground", text);
    UIManager.put("ToggleButton.disabledText", disabledText);
    UIManager.put("ToggleButton.select", selectionBg);

    UIManager.put("CheckBox.background", checkBg);
    UIManager.put("CheckBox.foreground", text);
    UIManager.put("CheckBox.disabledText", disabledText);
    UIManager.put("RadioButton.background", checkBg);
    UIManager.put("RadioButton.foreground", text);
    UIManager.put("RadioButton.disabledText", disabledText);

    UIManager.put("TextField.background", fieldBg);
    UIManager.put("TextField.foreground", text);
    UIManager.put("TextField.borderColor", border);
    UIManager.put("TextComponent.background", fieldBg);
    UIManager.put("TextField.inactiveBackground", toUiResource(mix(fieldBg, panelBg, 0.55)));
    UIManager.put("PasswordField.background", fieldBg);
    UIManager.put("PasswordField.foreground", text);
    UIManager.put("PasswordField.borderColor", border);
    UIManager.put("PasswordField.inactiveBackground", toUiResource(mix(fieldBg, panelBg, 0.55)));
    UIManager.put("FormattedTextField.background", fieldBg);
    UIManager.put("FormattedTextField.foreground", text);
    UIManager.put("FormattedTextField.borderColor", border);
    UIManager.put("FormattedTextField.inactiveBackground", toUiResource(mix(fieldBg, panelBg, 0.55)));

    UIManager.put("TextArea.background", areaBg);
    UIManager.put("TextArea.foreground", text);
    UIManager.put("TextPane.background", areaBg);
    UIManager.put("TextPane.foreground", text);
    UIManager.put("EditorPane.background", areaBg);
    UIManager.put("EditorPane.foreground", text);

    UIManager.put("ComboBox.background", fieldBg);
    UIManager.put("ComboBox.foreground", text);
    UIManager.put("ComboBox.disabled", panelBg);
    UIManager.put("ComboBox.disabledText", disabledText);
    UIManager.put("ComboBox.selectionBackground", selectionBg);
    UIManager.put("ComboBox.selectionForeground", selectionFg);
    UIManager.put("ComboBox:\"ComboBox.listRenderer\".background", fieldBg);
    UIManager.put("ComboBox:\"ComboBox.listRenderer\".textForeground", text);
    UIManager.put("ComboBox:\"ComboBox.listRenderer\"[Selected].background", selectionBg);
    UIManager.put("ComboBox:\"ComboBox.listRenderer\"[Selected].textForeground", selectionFg);
    UIManager.put("ComboBox:\"ComboBox.renderer\".background", fieldBg);
    UIManager.put("ComboBox:\"ComboBox.renderer\".textForeground", text);
    UIManager.put("ComboBox:\"ComboBox.renderer\"[Selected].background", selectionBg);
    UIManager.put("ComboBox:\"ComboBox.renderer\"[Selected].textForeground", selectionFg);
    UIManager.put("ComboBox:\"ComboBox.renderer\"[Disabled].textForeground", disabledText);
    UIManager.put("ComboBox:\"ComboBox.textField\".background", fieldBg);
    UIManager.put("ComboBox:\"ComboBox.textField\".textForeground", text);
    UIManager.put("ComboBox:\"ComboBox.textField\"[Selected].textForeground", selectionFg);
    UIManager.put("ComboBox:\"ComboBox.arrowButton\".background", comboArrowBg);
    UIManager.put("ComboBox:\"ComboBox.arrowButton\".foreground", text);

    UIManager.put("List.background", listBg);
    UIManager.put("List.foreground", text);
    UIManager.put("List.selectionBackground", selectionBg);
    UIManager.put("List.selectionForeground", selectionFg);

    UIManager.put("Table.background", tableBg);
    UIManager.put("Table.foreground", text);
    UIManager.put("Table.gridColor", separator);
    UIManager.put("Table.selectionBackground", selectionBg);
    UIManager.put("Table.selectionForeground", selectionFg);
    UIManager.put("TableHeader.background", tableHeaderBg);
    UIManager.put("TableHeader.foreground", text);
    UIManager.put("TableHeader:\"TableHeader.renderer\".background", tableHeaderBg);
    UIManager.put("TableHeader:\"TableHeader.renderer\".foreground", text);

    UIManager.put("Tree.background", treeBg);
    UIManager.put("Tree.textBackground", treeBg);
    UIManager.put("Tree.textForeground", text);
    UIManager.put("Tree.selectionBackground", selectionBg);
    UIManager.put("Tree.selectionForeground", selectionFg);

    UIManager.put("Viewport.background", viewportBg);
    UIManager.put("Viewport.foreground", text);
    UIManager.put("Spinner.background", fieldBg);
    UIManager.put("Spinner.foreground", text);
    UIManager.put("Spinner:\"Spinner.formattedTextField\".background", fieldBg);
    UIManager.put("Spinner:\"Spinner.formattedTextField\".foreground", text);

    UIManager.put("MenuBar.background", menuBarBg);
    UIManager.put("MenuBar.foreground", text);
    UIManager.put("MenuBar.borderColor", border);
    UIManager.put("Menu.background", menuBg);
    UIManager.put("Menu.foreground", text);
    UIManager.put("Menu.selectionBackground", toUiResource(lighten(menuBg, 0.08)));
    UIManager.put("Menu.selectionForeground", selectionFg);
    UIManager.put("Menu.disabledForeground", disabledText);
    UIManager.put("MenuBar:Menu[Enabled].textForeground", text);
    UIManager.put("MenuBar:Menu[Disabled].textForeground", disabledText);
    UIManager.put("MenuBar:Menu[Selected].textForeground", selectionFg);
    UIManager.put("Menu[Enabled].textForeground", text);
    UIManager.put("Menu[Disabled].textForeground", disabledText);
    UIManager.put("Menu[Enabled+Selected].textForeground", selectionFg);
    UIManager.put("Menu:MenuItemAccelerator[MouseOver].textForeground", selectionFg);

    UIManager.put("MenuItem.background", popupBg);
    UIManager.put("MenuItem.foreground", text);
    UIManager.put("MenuItem.selectionBackground", toUiResource(lighten(menuBg, 0.08)));
    UIManager.put("MenuItem.selectionForeground", selectionFg);
    UIManager.put("MenuItem.disabledForeground", disabledText);
    UIManager.put("MenuItem[Enabled].textForeground", text);
    UIManager.put("MenuItem[Disabled].textForeground", disabledText);
    UIManager.put("MenuItem[MouseOver].textForeground", selectionFg);
    UIManager.put("MenuItem:MenuItemAccelerator[Enabled].textForeground", text);
    UIManager.put("MenuItem:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("MenuItem:MenuItemAccelerator[Disabled].textForeground", disabledText);

    UIManager.put("CheckBoxMenuItem.background", popupBg);
    UIManager.put("CheckBoxMenuItem.foreground", text);
    UIManager.put("CheckBoxMenuItem.selectionBackground", toUiResource(lighten(menuBg, 0.08)));
    UIManager.put("CheckBoxMenuItem.selectionForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem.disabledForeground", disabledText);
    UIManager.put("CheckBoxMenuItem[Enabled].textForeground", text);
    UIManager.put("CheckBoxMenuItem[Disabled].textForeground", disabledText);
    UIManager.put("CheckBoxMenuItem[MouseOver].textForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem[MouseOver+Selected].textForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem:MenuItemAccelerator[Enabled].textForeground", text);
    UIManager.put("CheckBoxMenuItem:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem:MenuItemAccelerator[Disabled].textForeground", disabledText);

    UIManager.put("RadioButtonMenuItem.background", popupBg);
    UIManager.put("RadioButtonMenuItem.foreground", text);
    UIManager.put("RadioButtonMenuItem.selectionBackground", toUiResource(lighten(menuBg, 0.08)));
    UIManager.put("RadioButtonMenuItem.selectionForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem.disabledForeground", disabledText);
    UIManager.put("RadioButtonMenuItem[Enabled].textForeground", text);
    UIManager.put("RadioButtonMenuItem[Disabled].textForeground", disabledText);
    UIManager.put("RadioButtonMenuItem[MouseOver].textForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem[MouseOver+Selected].textForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem:MenuItemAccelerator[Enabled].textForeground", text);
    UIManager.put("RadioButtonMenuItem:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem:MenuItemAccelerator[Disabled].textForeground", disabledText);

    UIManager.put("PopupMenu.background", popupBg);
    UIManager.put("PopupMenu.foreground", text);
    UIManager.put("PopupMenu.borderColor", border);
    UIManager.put("PopupMenuSeparator.foreground", separator);
    UIManager.put("PopupMenuSeparator.background", popupBg);

    UIManager.put("Separator.foreground", separator);
    UIManager.put("Separator.background", panelBg);
    UIManager.put("ToolBar.separatorColor", separator);
    UIManager.put("SplitPane.background", splitPaneBg);
    UIManager.put("SplitPane.foreground", separator);
    UIManager.put("SplitPaneDivider.draggingColor", splitPaneDraggingColor);
    UIManager.put("ScrollPane.borderColor", border);

    UIManager.put("ToolTip.background", tooltipBg);
    UIManager.put("ToolTip.foreground", text);
    UIManager.put("TabbedPane.focus", focus);
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
    UIManager.put("MenuItem:MenuItemAccelerator[Enabled].textForeground", text);
    UIManager.put("MenuItem:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("MenuItem:MenuItemAccelerator[Disabled].textForeground", disabledText);
    UIManager.put("CheckBoxMenuItem.background", menuBg);
    UIManager.put("CheckBoxMenuItem.foreground", text);
    UIManager.put("CheckBoxMenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("CheckBoxMenuItem.selectionForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem:MenuItemAccelerator[Enabled].textForeground", text);
    UIManager.put("CheckBoxMenuItem:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("CheckBoxMenuItem:MenuItemAccelerator[Disabled].textForeground", disabledText);
    UIManager.put("RadioButtonMenuItem.background", menuBg);
    UIManager.put("RadioButtonMenuItem.foreground", text);
    UIManager.put("RadioButtonMenuItem.selectionBackground", menuSelectionBg);
    UIManager.put("RadioButtonMenuItem.selectionForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem:MenuItemAccelerator[Enabled].textForeground", text);
    UIManager.put("RadioButtonMenuItem:MenuItemAccelerator[MouseOver].textForeground", selectionFg);
    UIManager.put("RadioButtonMenuItem:MenuItemAccelerator[Disabled].textForeground", disabledText);
    UIManager.put("PopupMenu.background", menuBg);
    UIManager.put("PopupMenu.foreground", text);
    UIManager.put("PopupMenu.borderColor", border);
    UIManager.put("Button.select", selectionBg);
    UIManager.put("ToggleButton.select", selectionBg);
    UIManager.put("TabbedPane.focus", focus);
  }

  private static ColorUIResource uiColor(int r, int g, int b) {
    return ThemeColorUtils.uiColor(r, g, b);
  }

  private static ColorUIResource toUiResource(Color c) {
    return new ColorUIResource(c);
  }

  private static void logNimbusSnapshot(String stage, String themeId) {
    if (!ThemeLookAndFeelUtils.isNimbusDebugEnabled()) return;

    Color uiTextFieldBg = UIManager.getColor("TextField.background");
    Color uiTextPaneBg = UIManager.getColor("TextPane.background");
    Color uiTextComponentBg = UIManager.getColor("TextComponent.background");
    Color uiLightBg = UIManager.getColor("nimbusLightBackground");
    Color uiBase = UIManager.getColor("nimbusBase");
    Color uiBlueGrey = UIManager.getColor("nimbusBlueGrey");
    Color uiPanelBg = UIManager.getColor("Panel.background");

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

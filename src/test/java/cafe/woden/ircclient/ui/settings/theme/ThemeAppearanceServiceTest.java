package cafe.woden.ircclient.ui.settings.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.util.UiColorKeys;
import cafe.woden.ircclient.ui.util.UiDefaultKeys;
import cafe.woden.ircclient.ui.util.UiFontKeys;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThemeAppearanceServiceTest {

  private final ThemeAppearanceService service = new ThemeAppearanceService();

  private final String initialLookAndFeelClassName =
      UIManager.getLookAndFeel() != null ? UIManager.getLookAndFeel().getClass().getName() : null;

  @Test
  void nimbusDensityReachesPasswordFieldsCreatedAfterAThemeSwitch() throws Exception {
    onEdt(
        () -> {
          for (ThemeTweakSettings.ThemeDensity density :
              new ThemeTweakSettings.ThemeDensity[] {
                ThemeTweakSettings.ThemeDensity.COMPACT,
                ThemeTweakSettings.ThemeDensity.SPACIOUS,
                ThemeTweakSettings.ThemeDensity.AUTO
              }) {
            try {
              UIManager.setLookAndFeel(new javax.swing.plaf.nimbus.NimbusLookAndFeel());
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
            // Installing a theme can compile the defaults before preferences creates its fields.
            new JTextField().getPreferredSize();
            service.applyCommonTweaks(new ThemeTweakSettings(density, 10));
            assertEquals(
                UIManager.getInsets(UiDefaultKeys.PASSWORD_FIELD_CONTENT_MARGINS),
                new javax.swing.JPasswordField().getInsets(),
                density.name());
          }
        });
  }

  @Test
  void disablingAccentRestoresPreviousUiDefaults() throws Exception {
    onEdt(
        () -> {
          try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          Color baselineFocus = new Color(0x44, 0x55, 0x66);
          Color baselineSelection = new Color(0x2E, 0x3F, 0x50);
          UIManager.put(UiColorKeys.COMPONENT_FOCUS_COLOR, baselineFocus);
          UIManager.put(UiColorKeys.TEXT_COMPONENT_SELECTION_BACKGROUND, baselineSelection);

          service.applyAccentOverrides(ThemeAppearanceSettingsTestFixtures.accent("#FF5500", 100));

          Color afterApplyFocus = UIManager.getColor(UiColorKeys.COMPONENT_FOCUS_COLOR);
          Color afterApplySelection =
              UIManager.getColor(UiColorKeys.TEXT_COMPONENT_SELECTION_BACKGROUND);
          assertNotEquals(baselineFocus, afterApplyFocus);
          assertNotEquals(baselineSelection, afterApplySelection);

          service.applyAccentOverrides(ThemeAppearanceSettingsTestFixtures.accentDefaults());

          assertEquals(baselineFocus, UIManager.getColor(UiColorKeys.COMPONENT_FOCUS_COLOR));
          assertEquals(
              baselineSelection,
              UIManager.getColor(UiColorKeys.TEXT_COMPONENT_SELECTION_BACKGROUND));
        });
  }

  @Test
  void disablingAccentAfterLookAndFeelSwitchDoesNotRestoreStaleValues() throws Exception {
    onEdt(
        () -> {
          try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          Color darkBaselineFocus = new Color(0x32, 0x42, 0x52);
          UIManager.put(UiColorKeys.COMPONENT_FOCUS_COLOR, darkBaselineFocus);
          service.applyAccentOverrides(ThemeAppearanceSettingsTestFixtures.accent("#22AAEE", 80));

          try {
            UIManager.setLookAndFeel(new FlatLightLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          Color lightBaselineFocus = new Color(0x88, 0x66, 0x44);
          UIManager.put(UiColorKeys.COMPONENT_FOCUS_COLOR, lightBaselineFocus);

          service.applyAccentOverrides(ThemeAppearanceSettingsTestFixtures.accentDefaults());

          assertEquals(lightBaselineFocus, UIManager.getColor(UiColorKeys.COMPONENT_FOCUS_COLOR));
          assertNotEquals(darkBaselineFocus, UIManager.getColor(UiColorKeys.COMPONENT_FOCUS_COLOR));
        });
  }

  @Test
  void uiFontOverrideAppliesAndRestoresDefaults() throws Exception {
    onEdt(
        () -> {
          try {
            UIManager.setLookAndFeel(new FlatLightLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          Font baselineDefault = UIManager.getFont(UiFontKeys.DEFAULT_FONT);
          if (baselineDefault == null) {
            throw new IllegalStateException("defaultFont baseline missing");
          }

          int targetSize = baselineDefault.getSize() == 16 ? 17 : 16;
          String targetFamily =
              "Dialog".equalsIgnoreCase(baselineDefault.getFamily()) ? "Monospaced" : "Dialog";

          service.applyCommonTweaks(
              ThemeAppearanceSettingsTestFixtures.tweakBuilder()
                  .uiFontOverrideEnabled(true)
                  .uiFontFamily(targetFamily)
                  .uiFontSize(targetSize)
                  .build());

          Font afterApply = UIManager.getFont(UiFontKeys.DEFAULT_FONT);
          assertNotEquals(baselineDefault.getSize(), afterApply.getSize());
          assertEquals(targetSize, afterApply.getSize());
          assertEquals(targetFamily, afterApply.getFamily());

          service.applyCommonTweaks(
              ThemeAppearanceSettingsTestFixtures.tweakBuilder()
                  .uiFontFamily(targetFamily)
                  .uiFontSize(targetSize)
                  .build());

          Font afterDisable = UIManager.getFont(UiFontKeys.DEFAULT_FONT);
          assertEquals(baselineDefault.getFamily(), afterDisable.getFamily());
          assertEquals(baselineDefault.getSize(), afterDisable.getSize());
        });
  }

  @Test
  void uiFontOverrideDoesNotLeakAcrossLookAndFeelClassSwitches() throws Exception {
    onEdt(
        () -> {
          try {
            UIManager.setLookAndFeel(new FlatLightLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          // Remove any developer-default font overrides left by other tests so baseline comes from
          // the newly installed LAF.
          UIManager.put(UiFontKeys.DEFAULT_FONT, null);
          UIManager.put(UiFontKeys.TREE_FONT, null);

          Font lightTreeBaseline = uiFont(UiFontKeys.TREE_FONT);
          int targetSize = lightTreeBaseline.getSize() + 4;

          try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          UIManager.put(UiFontKeys.DEFAULT_FONT, null);
          UIManager.put(UiFontKeys.TREE_FONT, null);

          Font darkTreeBaseline = uiFont(UiFontKeys.TREE_FONT);
          service.applyCommonTweaks(
              ThemeAppearanceSettingsTestFixtures.tweakBuilder()
                  .uiFontOverrideEnabled(true)
                  .uiFontFamily(darkTreeBaseline.getFamily())
                  .uiFontSize(targetSize)
                  .build());
          Font darkAfterApply = uiFont(UiFontKeys.TREE_FONT);
          assertEquals(targetSize, darkAfterApply.getSize());

          try {
            UIManager.setLookAndFeel(new FlatLightLaf());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }

          service.applyCommonTweaks(
              ThemeAppearanceSettingsTestFixtures.tweakBuilder()
                  .uiFontFamily(darkTreeBaseline.getFamily())
                  .uiFontSize(targetSize)
                  .build());

          Font lightAfterRestore = uiFont(UiFontKeys.TREE_FONT);
          assertEquals(lightTreeBaseline.getSize(), lightAfterRestore.getSize());
          assertNotEquals(targetSize, lightAfterRestore.getSize());
        });
  }

  @Test
  void nimbusAutoDensityAppliesCozySizingDefaults() throws Exception {
    onEdt(
        () -> {
          installNimbus();

          service.applyCommonTweaks(ThemeAppearanceSettingsTestFixtures.tweakDefaults());

          assertEquals(26, UIManager.getInt(UiDefaultKeys.TREE_ROW_HEIGHT));
          assertEquals(26, UIManager.getInt(UiDefaultKeys.TABLE_ROW_HEIGHT));
          assertEquals(26, UIManager.getInt(UiDefaultKeys.LIST_CELL_HEIGHT));

          Insets menuItem = uiInsets(UiDefaultKeys.MENU_ITEM_CONTENT_MARGINS);
          assertTrue(menuItem.top >= 4);
          assertTrue(menuItem.bottom >= 5);

          Insets tab = uiInsets(UiDefaultKeys.TABBED_PANE_TAB_CONTENT_MARGINS);
          assertTrue(tab.left >= 12);
          assertTrue(tab.right >= 12);
        });
  }

  @Test
  void nimbusDensityChoicesChangeRowsAndMenus() throws Exception {
    onEdt(
        () -> {
          installNimbus();

          service.applyCommonTweaks(
              ThemeAppearanceSettingsTestFixtures.tweak(
                  ThemeTweakSettings.ThemeDensity.COMPACT, 10));
          int compactRowHeight = UIManager.getInt(UiDefaultKeys.TREE_ROW_HEIGHT);
          Insets compactMenuItem = uiInsets(UiDefaultKeys.MENU_ITEM_CONTENT_MARGINS);

          service.applyCommonTweaks(
              ThemeAppearanceSettingsTestFixtures.tweak(
                  ThemeTweakSettings.ThemeDensity.SPACIOUS, 10));
          int spaciousRowHeight = UIManager.getInt(UiDefaultKeys.TREE_ROW_HEIGHT);
          Insets spaciousMenuItem = uiInsets(UiDefaultKeys.MENU_ITEM_CONTENT_MARGINS);

          assertTrue(spaciousRowHeight > compactRowHeight);
          assertTrue(spaciousMenuItem.top > compactMenuItem.top);
          assertTrue(spaciousMenuItem.bottom > compactMenuItem.bottom);
        });
  }

  @Test
  void nimbusDensityRefreshResizesExistingComponents() throws Exception {
    onEdt(
        () -> {
          installNimbus();

          JPanel root = new JPanel();
          JButton button = new JButton("Button");
          JTextField textField = new JTextField("Text", 8);
          JComboBox<String> comboBox = new JComboBox<>(new String[] {"One", "Two"});
          JTabbedPane tabs = new JTabbedPane();
          tabs.addTab("Tab", new JLabel("Content"));
          JList<String> list = new JList<>(new String[] {"a", "b"});
          JTable table = new JTable(2, 2);
          JTree tree = new JTree();
          root.add(button);
          root.add(textField);
          root.add(comboBox);
          root.add(tabs);
          root.add(list);
          root.add(table);
          root.add(tree);

          ThemeTweakSettings compact =
              ThemeAppearanceSettingsTestFixtures.tweak(
                  ThemeTweakSettings.ThemeDensity.COMPACT, 10);
          service.applyCommonTweaks(compact);
          service.applyNimbusDensityToComponentTree(root, compact);
          SwingUtilities.updateComponentTreeUI(root);

          Dimension compactButton = button.getPreferredSize();
          Dimension compactTextField = textField.getPreferredSize();
          Dimension compactComboBox = comboBox.getPreferredSize();
          Dimension compactTabs = tabs.getPreferredSize();
          int compactListRow = list.getFixedCellHeight();
          int compactTableRow = table.getRowHeight();
          int compactTreeRow = tree.getRowHeight();

          ThemeTweakSettings spacious =
              ThemeAppearanceSettingsTestFixtures.tweak(
                  ThemeTweakSettings.ThemeDensity.SPACIOUS, 10);
          service.applyCommonTweaks(spacious);
          service.applyNimbusDensityToComponentTree(root, spacious);
          SwingUtilities.updateComponentTreeUI(root);

          assertTrue(button.getPreferredSize().height > compactButton.height);
          assertTrue(textField.getPreferredSize().height > compactTextField.height);
          assertTrue(comboBox.getPreferredSize().height > compactComboBox.height);
          assertTrue(tabs.getPreferredSize().height > compactTabs.height);
          assertTrue(list.getFixedCellHeight() > compactListRow);
          assertTrue(table.getRowHeight() > compactTableRow);
          assertTrue(tree.getRowHeight() > compactTreeRow);
        });
  }

  @Test
  void nimbusDensityKeepsRowsLargeEnoughForUiFontOverride() throws Exception {
    onEdt(
        () -> {
          installNimbus();

          service.applyCommonTweaks(
              ThemeAppearanceSettingsTestFixtures.tweakBuilder()
                  .density(ThemeTweakSettings.ThemeDensity.COMPACT)
                  .uiFontOverrideEnabled(true)
                  .uiFontFamily("Dialog")
                  .uiFontSize(24)
                  .build());

          assertTrue(UIManager.getInt(UiDefaultKeys.TREE_ROW_HEIGHT) >= 31);

          service.applyCommonTweaks(
              ThemeAppearanceSettingsTestFixtures.tweak(
                  ThemeTweakSettings.ThemeDensity.COMPACT, 10));
        });
  }

  @AfterAll
  void restoreLookAndFeel() throws Exception {
    if (initialLookAndFeelClassName == null || initialLookAndFeelClassName.isBlank()) return;
    onEdt(
        () -> {
          try {
            UIManager.setLookAndFeel(initialLookAndFeelClassName);
          } catch (Exception ignored) {
          }
        });
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }

  private static Font uiFont(String key) {
    Font f = UIManager.getFont(key);
    if (f != null) return f;
    Font fallback = UIManager.getFont(UiFontKeys.DEFAULT_FONT);
    if (fallback != null) return fallback;
    return new Font("Dialog", Font.PLAIN, 12);
  }

  private static void installNimbus() {
    try {
      UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Insets uiInsets(String key) {
    Object value = UIManager.get(key);
    if (value instanceof Insets insets) return insets;
    throw new AssertionError(key + " should be an Insets value but was " + value);
  }
}

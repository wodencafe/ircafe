package cafe.woden.ircclient.ui.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

/** Keeps popup menus visually in sync after runtime Look-and-Feel/theme switches. */
public final class PopupMenuThemeSupport {

  private static final String NIMBUS_LAF_CLASS = "javax.swing.plaf.nimbus.NimbusLookAndFeel";

  private PopupMenuThemeSupport() {}

  public static void prepareForDisplay(JPopupMenu menu) {
    if (menu == null) return;
    try {
      SwingUtilities.updateComponentTreeUI(menu);
    } catch (Exception ignored) {
    }
    if (isNimbusActive()) {
      applyNimbusPopupPalette(menu);
    }
  }

  private static boolean isNimbusActive() {
    LookAndFeel laf = UIManager.getLookAndFeel();
    return laf != null && NIMBUS_LAF_CLASS.equals(laf.getClass().getName());
  }

  private static void applyNimbusPopupPalette(JPopupMenu menu) {
    Color popupBg = firstColor("PopupMenu.background", "MenuItem.background", "Menu.background");
    Color popupFg = firstColor("PopupMenu.foreground", "MenuItem.foreground", "Label.foreground");
    Color itemBg = firstColor("MenuItem.background", "PopupMenu.background", "Menu.background");
    Color itemFg = firstColor("MenuItem.foreground", "PopupMenu.foreground", "Label.foreground");
    Color disabledFg = firstColor("MenuItem.disabledForeground", "Label.disabledForeground");
    Color sepFg = firstColor("PopupMenuSeparator.foreground", "Separator.foreground");
    Color sepBg = firstColor("PopupMenuSeparator.background", "PopupMenu.background");
    Color borderColor = firstColor("PopupMenu.borderColor", "nimbusBorder", "Separator.foreground");
    Font menuFont = firstFont("Menu.font", "MenuBar.font", "MenuItem.font", "Label.font");
    Font menuItemFont = firstFont("MenuItem.font", "Menu.font", "Label.font");
    Font checkItemFont = firstFont("CheckBoxMenuItem.font", "MenuItem.font", "Menu.font", "Label.font");
    Font radioItemFont = firstFont("RadioButtonMenuItem.font", "MenuItem.font", "Menu.font", "Label.font");

    applyPopupPalette(
        menu,
        popupBg,
        popupFg,
        itemBg,
        itemFg,
        disabledFg,
        sepFg,
        sepBg,
        borderColor,
        menuFont,
        menuItemFont,
        checkItemFont,
        radioItemFont);
  }

  private static void applyPopupPalette(
      JPopupMenu popup,
      Color popupBg,
      Color popupFg,
      Color itemBg,
      Color itemFg,
      Color disabledFg,
      Color sepFg,
      Color sepBg,
      Color borderColor,
      Font menuFont,
      Font menuItemFont,
      Font checkItemFont,
      Font radioItemFont) {
    if (popupBg != null) popup.setBackground(popupBg);
    if (popupFg != null) popup.setForeground(popupFg);
    if (menuItemFont != null) popup.setFont(menuItemFont);
    popup.setOpaque(true);

    if (borderColor != null) {
      Border border = popup.getBorder();
      if (!(border instanceof LineBorder lb) || !borderColor.equals(lb.getLineColor())) {
        popup.setBorder(new LineBorder(borderColor));
      }
    }

    for (Component child : popup.getComponents()) {
      applyComponentPalette(
          child,
          itemBg,
          itemFg,
          disabledFg,
          sepFg,
          sepBg,
          menuFont,
          menuItemFont,
          checkItemFont,
          radioItemFont);
      if (child instanceof JMenu jm) {
        JPopupMenu sub = jm.getPopupMenu();
        if (sub != null) {
          applyPopupPalette(
              sub,
              popupBg,
              popupFg,
              itemBg,
              itemFg,
              disabledFg,
              sepFg,
              sepBg,
              borderColor,
              menuFont,
              menuItemFont,
              checkItemFont,
              radioItemFont);
        }
      } else if (child instanceof Container ctr) {
        applyContainerPalette(
            ctr,
            itemBg,
            itemFg,
            disabledFg,
            sepFg,
            sepBg,
            menuFont,
            menuItemFont,
            checkItemFont,
            radioItemFont);
      }
    }
  }

  private static void applyContainerPalette(
      Container container,
      Color itemBg,
      Color itemFg,
      Color disabledFg,
      Color sepFg,
      Color sepBg,
      Font menuFont,
      Font menuItemFont,
      Font checkItemFont,
      Font radioItemFont) {
    if (container == null) return;
    for (Component child : container.getComponents()) {
      applyComponentPalette(
          child,
          itemBg,
          itemFg,
          disabledFg,
          sepFg,
          sepBg,
          menuFont,
          menuItemFont,
          checkItemFont,
          radioItemFont);
      if (child instanceof Container ctr) {
        applyContainerPalette(
            ctr,
            itemBg,
            itemFg,
            disabledFg,
            sepFg,
            sepBg,
            menuFont,
            menuItemFont,
            checkItemFont,
            radioItemFont);
      }
    }
  }

  private static void applyComponentPalette(
      Component component,
      Color itemBg,
      Color itemFg,
      Color disabledFg,
      Color sepFg,
      Color sepBg,
      Font menuFont,
      Font menuItemFont,
      Font checkItemFont,
      Font radioItemFont) {
    if (component instanceof JMenuItem item) {
      if (itemBg != null) item.setBackground(itemBg);
      if (item.isEnabled()) {
        if (itemFg != null) item.setForeground(itemFg);
      } else if (disabledFg != null) {
        item.setForeground(disabledFg);
      }
      if (item instanceof JMenu) {
        if (menuFont != null) item.setFont(menuFont);
      } else if (item instanceof JCheckBoxMenuItem) {
        if (checkItemFont != null) item.setFont(checkItemFont);
      } else if (item instanceof JRadioButtonMenuItem) {
        if (radioItemFont != null) item.setFont(radioItemFont);
      } else if (menuItemFont != null) {
        item.setFont(menuItemFont);
      }
      item.setOpaque(true);
      return;
    }
    if (component instanceof JSeparator separator) {
      if (sepBg != null) separator.setBackground(sepBg);
      if (sepFg != null) separator.setForeground(sepFg);
      return;
    }
    if (component instanceof JComponent jc) {
      jc.setOpaque(true);
    }
  }

  private static Color firstColor(String... keys) {
    if (keys == null) return null;
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      Color c = UIManager.getColor(key);
      if (c != null) return c;
    }
    return null;
  }

  private static Font firstFont(String... keys) {
    if (keys == null) return null;
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      Font f = UIManager.getFont(key);
      if (f != null) return f;
    }
    return null;
  }
}

package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.settings.PreferencesDialog;
import cafe.woden.ircclient.ui.settings.ThemeManager;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Window;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * App-wide menu bar.
 */
@Component
@Lazy
public class AppMenuBar extends JMenuBar {

  public AppMenuBar(PreferencesDialog preferencesDialog,
                    UiSettingsBus settingsBus,
                    ThemeManager themeManager,
                    RuntimeConfigStore runtimeConfig,
                    ServerDialogs serverDialogs,
                    ServerTreeDockable serverTree) {

    // File
    JMenu file = new JMenu("File");
    JMenuItem exit = new JMenuItem("Exit");
    exit.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      if (w != null) w.dispose();
      else System.exit(0);
    });
    file.add(exit);

    // Edit (placeholder for future)
    JMenu edit = new JMenu("Edit");

    // Insert (placeholder for future)
    JMenu insert = new JMenu("Insert");

    // Settings
    JMenu settings = new JMenu("Settings");

    JMenu theme = new JMenu("Theme");
    ButtonGroup themeGroup = new ButtonGroup();
    for (ThemeManager.ThemeOption opt : themeManager.supportedThemes()) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(opt.label());
      themeGroup.add(item);
      theme.add(item);

      item.addActionListener(e -> {
        UiSettings cur = settingsBus.get();
        UiSettings next = cur.withTheme(opt.id());
        settingsBus.set(next);
        runtimeConfig.rememberUiSettings(next.theme(), next.chatFontFamily(), next.chatFontSize());
        themeManager.applyTheme(next.theme());
      });

      if (opt.id().equalsIgnoreCase(settingsBus.get().theme())) {
        item.setSelected(true);
      }
    }
    settings.add(theme);

    JMenuItem prefs = new JMenuItem("Preferences...");
    prefs.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      preferencesDialog.open(w);
    });
    settings.addSeparator();
    settings.add(prefs);

    // Window (placeholder for future docking helpers)
    JMenu window = new JMenu("Window");

    JMenuItem moveUp = new JMenuItem("Move Node Up");
    moveUp.addActionListener(e -> serverTree.moveSelectedNodeUp());

    JMenuItem moveDown = new JMenuItem("Move Node Down");
    moveDown.addActionListener(e -> serverTree.moveSelectedNodeDown());

    JMenuItem closeNode = new JMenuItem("Close Node");
    closeNode.addActionListener(e -> serverTree.closeSelectedNode());

    // Enable/disable based on the currently selected node in the server tree.
    // We update when the menu is opened so it always reflects the latest selection.
    window.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        moveUp.setEnabled(serverTree.canMoveSelectedNodeUp());
        moveDown.setEnabled(serverTree.canMoveSelectedNodeDown());
        closeNode.setEnabled(serverTree.canCloseSelectedNode());
      }

      @Override
      public void menuDeselected(MenuEvent e) {
        // no-op
      }

      @Override
      public void menuCanceled(MenuEvent e) {
        // no-op
      }
    });

    window.add(moveUp);
    window.add(moveDown);
    window.addSeparator();
    window.add(closeNode);

    // Servers
    JMenu servers = new JMenu("Servers");
    JMenuItem addServer = new JMenuItem("Add Server...");
    addServer.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      serverDialogs.openAddServer(w);
    });
    JMenuItem editServers = new JMenuItem("Edit Servers...");
    editServers.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      serverDialogs.openManageServers(w);
    });
    servers.add(addServer);
    servers.add(editServers);

    // Help
    JMenu help = new JMenu("Help");
    JMenuItem about = new JMenuItem("About");
    about.addActionListener(e -> javax.swing.JOptionPane.showMessageDialog(
        SwingUtilities.getWindowAncestor(this),
        "IRCafe\nA modern Java IRC client.",
        "About IRCafe",
        javax.swing.JOptionPane.INFORMATION_MESSAGE));
    help.add(about);

    add(file);
    add(servers);
    add(edit);
    add(insert);
    add(settings);
    add(window);
    add(help);
  }
}

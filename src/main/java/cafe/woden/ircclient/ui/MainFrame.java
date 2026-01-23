package cafe.woden.ircclient.ui;

import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.app.RootDockingPanel;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

@Component
@Lazy
public class MainFrame extends JFrame {
  private final UiController controller;
  private final StatusBar statusBar;

  // Dockables (Spring beans)
  private final ServerTreeDockable serverTree;
  private final ChatDockable chat;
  private final UserListDockable users;
  private final MessageInputDockable input;

  public MainFrame(
      UiController controller,
      ServerTreeDockable serverTree,
      ChatDockable chat,
      UserListDockable users,
      MessageInputDockable input,
      StatusBar statusBar
  ) {
    super("WodenCafe IRC");
    this.controller = controller;
    this.serverTree = serverTree;
    this.chat = chat;
    this.users = users;
    this.input = input;
    this.statusBar = statusBar;

    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setSize(1100, 700);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    Docking.initialize(this);
    // DockingUI.initialize(); // TODO: Investigate this.

    RootDockingPanel root = new RootDockingPanel(this);
    add(root, BorderLayout.CENTER);
    add(statusBar, BorderLayout.SOUTH);

    Docking.registerDockable(chat);
    Docking.registerDockable(serverTree);
    Docking.registerDockable(users);
    Docking.registerDockable(input);

    // First dock must be to an empty root container.
    Docking.dock(chat, this);

    Docking.dock(serverTree, chat, DockingRegion.WEST, 0.22);
    Docking.dock(users, chat, DockingRegion.EAST, 0.18);
    Docking.dock(input, chat, DockingRegion.SOUTH, 0.10);


    addWindowListener(new WindowAdapter() {
      @Override public void windowOpened(WindowEvent e) {
        // Controller must use event dispatch thread.
        controller.start();
      }

      @Override public void windowClosed(WindowEvent e) {
        controller.stop();
      }
    });
  }
}

package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.ui.NickContextMenuFactory;
import java.awt.Component;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;

class ChatNickContextCoordinatorTest {

  @Test
  void nickContextMenuForBuildsMenuWithIgnoreStatusMarks() {
    NickContextMenuFactory factory = new NickContextMenuFactory();
    NickContextMenuFactory.NickContextMenu menu =
        factory.create(
            new NickContextMenuFactory.Callbacks() {
              @Override
              public void openQuery(TargetRef ctx, String nick) {}

              @Override
              public void emitUserAction(
                  TargetRef ctx,
                  String nick,
                  cafe.woden.ircclient.app.api.UserActionRequest.Action action) {}

              @Override
              public void promptIgnore(
                  TargetRef ctx, String nick, boolean removing, boolean soft) {}

              @Override
              public void requestDccAction(
                  TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {}
            });
    IgnoreStatusService ignoreStatusService = mock(IgnoreStatusService.class);
    UserListStore userListStore = mock(UserListStore.class);
    Component parent = new JPanel();
    TargetRef channel = new TargetRef("libera", "#ircafe");

    when(userListStore.get("libera", "#ircafe"))
        .thenReturn(List.of(new NickInfo("alice", "", "alice!user@example.org")));
    when(ignoreStatusService.status("libera", "alice", "alice!user@example.org"))
        .thenReturn(new IgnoreStatusService.Status(true, false, true, "alice!user@example.org"));

    ChatNickContextCoordinator coordinator =
        new ChatNickContextCoordinator(
            null, ignoreStatusService, userListStore, menu, () -> channel, parent);

    JPopupMenu popup = coordinator.nickContextMenuFor("alice");

    assertNotNull(popup);
    JMenuItem unignore = findMenuItem(popup, "Unignore...");
    JMenuItem softUnignore = findMenuItem(popup, "Soft Unignore...");
    assertNotNull(unignore);
    assertNotNull(softUnignore);
    assertTrue(unignore.isEnabled());
    assertFalse(softUnignore.isEnabled());
  }

  @Test
  void nickContextMenuForReturnsNullWhenNoActiveTarget() {
    NickContextMenuFactory factory = new NickContextMenuFactory();
    NickContextMenuFactory.NickContextMenu menu =
        factory.create(
            new NickContextMenuFactory.Callbacks() {
              @Override
              public void openQuery(TargetRef ctx, String nick) {}

              @Override
              public void emitUserAction(
                  TargetRef ctx,
                  String nick,
                  cafe.woden.ircclient.app.api.UserActionRequest.Action action) {}

              @Override
              public void promptIgnore(
                  TargetRef ctx, String nick, boolean removing, boolean soft) {}

              @Override
              public void requestDccAction(
                  TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {}
            });
    UserListStore userListStore = mock(UserListStore.class);
    ChatNickContextCoordinator coordinator =
        new ChatNickContextCoordinator(null, null, userListStore, menu, () -> null, new JPanel());

    assertNull(coordinator.nickContextMenuFor("alice"));
  }

  @Test
  void promptIgnoreReturnsEarlyWhenIgnoreServiceMissing() {
    NickContextMenuFactory factory = new NickContextMenuFactory();
    NickContextMenuFactory.NickContextMenu menu =
        factory.create(
            new NickContextMenuFactory.Callbacks() {
              @Override
              public void openQuery(TargetRef ctx, String nick) {}

              @Override
              public void emitUserAction(
                  TargetRef ctx,
                  String nick,
                  cafe.woden.ircclient.app.api.UserActionRequest.Action action) {}

              @Override
              public void promptIgnore(
                  TargetRef ctx, String nick, boolean removing, boolean soft) {}

              @Override
              public void requestDccAction(
                  TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {}
            });
    UserListStore userListStore = mock(UserListStore.class);
    IgnoreStatusService ignoreStatusService = mock(IgnoreStatusService.class);
    ChatNickContextCoordinator coordinator =
        new ChatNickContextCoordinator(
            null,
            ignoreStatusService,
            userListStore,
            menu,
            () -> new TargetRef("libera", "#ircafe"),
            new JPanel());

    coordinator.promptIgnore(new TargetRef("libera", "#ircafe"), "alice", false, false);

    verifyNoInteractions(ignoreStatusService, userListStore);
  }

  @Test
  void promptIgnoreReturnsEarlyForBlankNick() {
    NickContextMenuFactory factory = new NickContextMenuFactory();
    NickContextMenuFactory.NickContextMenu menu =
        factory.create(
            new NickContextMenuFactory.Callbacks() {
              @Override
              public void openQuery(TargetRef ctx, String nick) {}

              @Override
              public void emitUserAction(
                  TargetRef ctx,
                  String nick,
                  cafe.woden.ircclient.app.api.UserActionRequest.Action action) {}

              @Override
              public void promptIgnore(
                  TargetRef ctx, String nick, boolean removing, boolean soft) {}

              @Override
              public void requestDccAction(
                  TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {}
            });
    IgnoreListService ignoreListService = mock(IgnoreListService.class);
    IgnoreStatusService ignoreStatusService = mock(IgnoreStatusService.class);
    UserListStore userListStore = mock(UserListStore.class);
    ChatNickContextCoordinator coordinator =
        new ChatNickContextCoordinator(
            ignoreListService,
            ignoreStatusService,
            userListStore,
            menu,
            () -> new TargetRef("libera", "#ircafe"),
            new JPanel());

    coordinator.promptIgnore(new TargetRef("libera", "#ircafe"), " ", false, false);

    verifyNoInteractions(ignoreListService, ignoreStatusService, userListStore);
  }

  private static JMenuItem findMenuItem(JPopupMenu popupMenu, String text) {
    if (popupMenu == null || text == null) return null;
    for (Component component : popupMenu.getComponents()) {
      if (component instanceof JMenuItem menuItem && text.equals(menuItem.getText())) {
        return menuItem;
      }
    }
    return null;
  }
}

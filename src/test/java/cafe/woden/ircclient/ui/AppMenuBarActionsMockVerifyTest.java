package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.ApplicationShutdownCoordinator;
import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.diagnostics.RuntimeJfrService;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.settings.PreferencesDialog;
import cafe.woden.ircclient.ui.settings.ThemeManager;
import cafe.woden.ircclient.ui.settings.ThemeSelectionDialog;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class AppMenuBarActionsMockVerifyTest {

  @Test
  void exitActionTriggersApplicationShutdown() throws Exception {
    ApplicationShutdownCoordinator shutdownCoordinator = mock(ApplicationShutdownCoordinator.class);
    AppMenuBar menuBar = onEdtCall(() -> newMenuBar(shutdownCoordinator, null, null, null));

    JMenuItem exit = findMenuItem(menuBar, "Exit");
    assertNotNull(exit);

    onEdt(exit::doClick);

    verify(shutdownCoordinator).shutdown();
  }

  @Test
  void findInCurrentBufferActionOpensChatFindBar() throws Exception {
    ChatDockable chat = mock(ChatDockable.class);
    AppMenuBar menuBar =
        onEdtCall(() -> newMenuBar(mock(ApplicationShutdownCoordinator.class), chat, null, null));

    JMenuItem findCurrent = findMenuItem(menuBar, "Find in Current Buffer");
    assertNotNull(findCurrent);

    onEdt(findCurrent::doClick);

    verify(chat).openFindBar();
  }

  @Test
  void openSelectedNodeActionRoutesToServerTree() throws Exception {
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    AppMenuBar menuBar =
        onEdtCall(
            () -> newMenuBar(mock(ApplicationShutdownCoordinator.class), null, serverTree, null));

    JMenuItem openSelected = findMenuItem(menuBar, "Open Selected Node in Chat Dock");
    assertNotNull(openSelected);

    onEdt(openSelected::doClick);

    verify(serverTree).openSelectedNodeInChatDock();
  }

  @Test
  void editServersActionOpensManageServersDialog() throws Exception {
    ServerDialogs serverDialogs = mock(ServerDialogs.class);
    AppMenuBar menuBar =
        onEdtCall(
            () ->
                newMenuBar(mock(ApplicationShutdownCoordinator.class), null, null, serverDialogs));

    JMenuItem editServers = findMenuItem(menuBar, "Edit Servers...");
    assertNotNull(editServers);

    onEdt(editServers::doClick);

    verify(serverDialogs).openManageServers(any());
  }

  private static AppMenuBar newMenuBar(
      ApplicationShutdownCoordinator shutdownCoordinator,
      ChatDockable chatOverride,
      ServerTreeDockable treeOverride,
      ServerDialogs dialogsOverride) {
    PreferencesDialog preferencesDialog = mock(PreferencesDialog.class);
    NickColorOverridesDialog nickColorOverridesDialog = mock(NickColorOverridesDialog.class);
    IgnoreListDialog ignoreListDialog = mock(IgnoreListDialog.class);
    ThemeSelectionDialog themeSelectionDialog = mock(ThemeSelectionDialog.class);
    ThemeManager themeManager = mock(ThemeManager.class);
    when(themeManager.featuredThemes()).thenReturn(new ThemeManager.ThemeOption[0]);
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    RuntimeJfrService runtimeJfrService = mock(RuntimeJfrService.class);
    ServerDialogs serverDialogs =
        dialogsOverride != null ? dialogsOverride : mock(ServerDialogs.class);
    ChatDockable chat = chatOverride != null ? chatOverride : mock(ChatDockable.class);
    ServerTreeDockable serverTree =
        treeOverride != null ? treeOverride : mock(ServerTreeDockable.class);
    UserListDockable users = mock(UserListDockable.class);
    ActiveInputRouter activeInputRouter = new ActiveInputRouter();
    ActiveTargetPort targetCoordinator = mock(ActiveTargetPort.class);

    AbstractAction noopAction =
        new AbstractAction("noop") {
          @Override
          public void actionPerformed(java.awt.event.ActionEvent e) {}
        };
    when(serverTree.moveNodeUpAction()).thenReturn(noopAction);
    when(serverTree.moveNodeDownAction()).thenReturn(noopAction);
    when(serverTree.closeNodeAction()).thenReturn(noopAction);

    return new AppMenuBar(
        preferencesDialog,
        nickColorOverridesDialog,
        ignoreListDialog,
        themeSelectionDialog,
        themeManager,
        settingsBus,
        null,
        null,
        null,
        null,
        runtimeJfrService,
        serverDialogs,
        null,
        chat,
        serverTree,
        users,
        activeInputRouter,
        targetCoordinator,
        shutdownCoordinator);
  }

  private static JMenuItem findMenuItem(Component root, String text) {
    if (root == null || text == null) return null;
    if (root instanceof JMenuItem item && text.equals(item.getText())) {
      return item;
    }
    if (root instanceof javax.swing.JMenu menu) {
      Component[] children = menu.getMenuComponents();
      for (Component child : children) {
        JMenuItem found = findMenuItem(child, text);
        if (found != null) return found;
      }
      return null;
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JMenuItem found = findMenuItem(child, text);
      if (found != null) return found;
    }
    return null;
  }

  private static void onEdt(ThrowingRunnable runnable)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        runnable.run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        return supplier.get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    final java.util.concurrent.atomic.AtomicReference<T> out =
        new java.util.concurrent.atomic.AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            out.set(supplier.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    return out.get();
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}

package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeDockableServerAutoConnectMenuTest {

  @Test
  void persistedServerPopupShowsStartupAutoConnectToggleAndPersistsSelection() throws Exception {
    onEdt(
        () -> {
          try {
            ServerCatalog serverCatalog = mock(ServerCatalog.class);
            RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
            when(serverCatalog.entries()).thenReturn(List.of());
            when(serverCatalog.updates()).thenReturn(Flowable.never());
            when(serverCatalog.findEntry("libera"))
                .thenReturn(Optional.of(ServerEntry.persistent(server("libera"))));
            when(runtimeConfig.readServerAutoConnectOnStart("libera", true)).thenReturn(true);

            ServerTreeDockable dockable = newDockable(serverCatalog, runtimeConfig);
            invokeAddServerRoot(dockable, "libera");

            JPopupMenu menu = buildPopupMenuForServerRoot(dockable, "libera");
            assertNotNull(menu);

            JCheckBoxMenuItem autoConnect =
                findCheckBoxMenuItem(menu, "Auto-connect \"libera\" on startup");
            assertNotNull(autoConnect);
            assertTrue(autoConnect.isSelected());

            autoConnect.doClick();
            verify(runtimeConfig).rememberServerAutoConnectOnStart("libera", false);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void ephemeralServerPopupDoesNotShowStartupAutoConnectToggle() throws Exception {
    onEdt(
        () -> {
          try {
            ServerCatalog serverCatalog = mock(ServerCatalog.class);
            RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
            when(serverCatalog.entries()).thenReturn(List.of());
            when(serverCatalog.updates()).thenReturn(Flowable.never());
            when(serverCatalog.findEntry("libera"))
                .thenReturn(Optional.of(ServerEntry.ephemeral(server("libera"), "soju-root")));

            ServerTreeDockable dockable = newDockable(serverCatalog, runtimeConfig);
            invokeAddServerRoot(dockable, "libera");

            JPopupMenu menu = buildPopupMenuForServerRoot(dockable, "libera");
            assertNotNull(menu);
            assertNull(findCheckBoxMenuItem(menu, "Auto-connect \"libera\" on startup"));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void serverPopupAllowsDisconnectWhileConnecting() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable(null, null);
            invokeAddServerRoot(dockable, "libera");
            dockable.setServerConnectionState("libera", ConnectionState.CONNECTING);

            JPopupMenu menu = buildPopupMenuForServerRoot(dockable, "libera");
            assertNotNull(menu);

            JMenuItem disconnect = findMenuItem(menu, "Disconnect \"libera\"");
            assertNotNull(disconnect);
            assertTrue(disconnect.isEnabled());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void quasselServerPopupShowsManageNetworksAction() throws Exception {
    onEdt(
        () -> {
          try {
            ServerCatalog serverCatalog = mock(ServerCatalog.class);
            RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
            when(serverCatalog.entries()).thenReturn(List.of());
            when(serverCatalog.updates()).thenReturn(Flowable.never());
            when(serverCatalog.findEntry("quassel"))
                .thenReturn(
                    Optional.of(
                        ServerEntry.persistent(
                            server("quassel", IrcProperties.Server.Backend.QUASSEL_CORE))));

            ServerTreeDockable dockable = newDockable(serverCatalog, runtimeConfig);
            invokeAddServerRoot(dockable, "quassel");

            JPopupMenu menu = buildPopupMenuForServerRoot(dockable, "quassel");
            assertNotNull(menu);
            assertNotNull(findMenuItem(menu, "Manage Quassel Networks..."));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void regularIrcServerPopupDoesNotShowManageQuasselNetworksAction() throws Exception {
    onEdt(
        () -> {
          try {
            ServerCatalog serverCatalog = mock(ServerCatalog.class);
            RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
            when(serverCatalog.entries()).thenReturn(List.of());
            when(serverCatalog.updates()).thenReturn(Flowable.never());
            when(serverCatalog.findEntry("libera"))
                .thenReturn(Optional.of(ServerEntry.persistent(server("libera"))));

            ServerTreeDockable dockable = newDockable(serverCatalog, runtimeConfig);
            invokeAddServerRoot(dockable, "libera");

            JPopupMenu menu = buildPopupMenuForServerRoot(dockable, "libera");
            assertNotNull(menu);
            assertNull(findMenuItem(menu, "Manage Quassel Networks..."));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void quasselManageNetworksMenuItemEmitsRequest() throws Exception {
    onEdt(
        () -> {
          Disposable requestSub = null;
          try {
            ServerCatalog serverCatalog = mock(ServerCatalog.class);
            RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
            when(serverCatalog.entries()).thenReturn(List.of());
            when(serverCatalog.updates()).thenReturn(Flowable.never());
            when(serverCatalog.findEntry("quassel"))
                .thenReturn(
                    Optional.of(
                        ServerEntry.persistent(
                            server("quassel", IrcProperties.Server.Backend.QUASSEL_CORE))));

            ServerTreeDockable dockable = newDockable(serverCatalog, runtimeConfig);
            invokeAddServerRoot(dockable, "quassel");

            AtomicReference<String> requestedServer = new AtomicReference<>();
            requestSub = dockable.quasselNetworkManagerRequests().subscribe(requestedServer::set);

            JPopupMenu menu = buildPopupMenuForServerRoot(dockable, "quassel");
            assertNotNull(menu);
            JMenuItem manage = findMenuItem(menu, "Manage Quassel Networks...");
            assertNotNull(manage);
            manage.doClick();

            assertTrue("quassel".equals(requestedServer.get()));
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            if (requestSub != null) requestSub.dispose();
          }
        });
  }

  private static ServerTreeDockable newDockable(
      ServerCatalog serverCatalog, RuntimeConfigStore runtimeConfig) {
    return new ServerTreeDockable(
        serverCatalog,
        runtimeConfig,
        null,
        null,
        null,
        new ConnectButton(),
        new DisconnectButton(),
        null,
        null,
        null,
        null);
  }

  private static void invokeAddServerRoot(ServerTreeDockable dockable, String serverId)
      throws Exception {
    Method m = ServerTreeDockable.class.getDeclaredMethod("addServerRoot", String.class);
    m.setAccessible(true);
    m.invoke(dockable, serverId);
  }

  @SuppressWarnings("unchecked")
  private static JPopupMenu buildPopupMenuForServerRoot(
      ServerTreeDockable dockable, String serverId) throws Exception {
    Field serversField = ServerTreeDockable.class.getDeclaredField("servers");
    serversField.setAccessible(true);
    Map<String, ?> servers = (Map<String, ?>) serversField.get(dockable);

    Object serverNodes = servers.get(serverId);
    if (serverNodes == null) return null;

    Field serverNodeField = serverNodes.getClass().getDeclaredField("serverNode");
    serverNodeField.setAccessible(true);
    DefaultMutableTreeNode serverNode = (DefaultMutableTreeNode) serverNodeField.get(serverNodes);

    Field menuBuilderField = ServerTreeDockable.class.getDeclaredField("contextMenuBuilder");
    menuBuilderField.setAccessible(true);
    ServerTreeContextMenuBuilder menuBuilder =
        (ServerTreeContextMenuBuilder) menuBuilderField.get(dockable);
    return menuBuilder.build(new TreePath(serverNode.getPath()));
  }

  private static JCheckBoxMenuItem findCheckBoxMenuItem(JPopupMenu menu, String text) {
    if (menu == null || text == null) return null;
    for (java.awt.Component c : menu.getComponents()) {
      if (!(c instanceof JCheckBoxMenuItem item)) continue;
      if (text.equals(item.getText())) return item;
    }
    return null;
  }

  private static JMenuItem findMenuItem(JPopupMenu menu, String text) {
    if (menu == null || text == null) return null;
    for (java.awt.Component c : menu.getComponents()) {
      if (!(c instanceof JMenuItem item)) continue;
      if (text.equals(item.getText())) return item;
    }
    return null;
  }

  private static IrcProperties.Server server(String id) {
    return server(id, IrcProperties.Server.Backend.IRC);
  }

  private static IrcProperties.Server server(String id, IrcProperties.Server.Backend backend) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        null,
        List.of(),
        List.of(),
        null,
        backend);
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}

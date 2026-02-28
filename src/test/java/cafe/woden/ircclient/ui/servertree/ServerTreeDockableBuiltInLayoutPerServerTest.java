package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerTreeDockableBuiltInLayoutPerServerTest {

  @TempDir Path tempDir;

  @Test
  void defaultLayoutPlacesMovableBuiltInsUnderOther() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable(null);
            invokeAddServerRoot(dockable, "libera");

            Object serverNodes = serverNodes(dockable, "libera");
            DefaultMutableTreeNode serverNode = nodeField(serverNodes, "serverNode");
            DefaultMutableTreeNode otherNode = nodeField(serverNodes, "otherNode");
            DefaultMutableTreeNode monitorNode = nodeField(serverNodes, "monitorNode");
            DefaultMutableTreeNode interceptorsNode = nodeField(serverNodes, "interceptorsNode");

            assertNotNull(serverNode);
            assertNotNull(otherNode);
            assertNotNull(monitorNode);
            assertNotNull(interceptorsNode);

            assertEquals(otherNode, leafNode(dockable, new TargetRef("libera", "status")).getParent());
            assertEquals(otherNode, leafNode(dockable, TargetRef.notifications("libera")).getParent());
            assertEquals(otherNode, leafNode(dockable, TargetRef.logViewer("libera")).getParent());
            assertEquals(otherNode, leafNode(dockable, TargetRef.weechatFilters("libera")).getParent());
            assertEquals(otherNode, leafNode(dockable, TargetRef.ignores("libera")).getParent());
            assertEquals(otherNode, monitorNode.getParent());
            assertEquals(otherNode, interceptorsNode.getParent());
            assertEquals(
                serverNode, leafNode(dockable, TargetRef.channelList("libera")).getParent());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void persistedLayoutPlacesConfiguredBuiltInsAtServerLevel() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore runtime =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of(server("libera"))));
    runtime.rememberServerTreeBuiltInLayout(
        "libera",
        new RuntimeConfigStore.ServerTreeBuiltInLayout(
            List.of(
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.SERVER,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.MONITOR),
            List.of(
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.NOTIFICATIONS,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.LOG_VIEWER,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.FILTERS,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.IGNORES,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.INTERCEPTORS)));

    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable(runtime);
            invokeAddServerRoot(dockable, "libera");

            Object serverNodes = serverNodes(dockable, "libera");
            DefaultMutableTreeNode serverNode = nodeField(serverNodes, "serverNode");
            DefaultMutableTreeNode otherNode = nodeField(serverNodes, "otherNode");
            DefaultMutableTreeNode monitorNode = nodeField(serverNodes, "monitorNode");
            DefaultMutableTreeNode interceptorsNode = nodeField(serverNodes, "interceptorsNode");

            assertEquals(serverNode, leafNode(dockable, new TargetRef("libera", "status")).getParent());
            assertEquals(serverNode, monitorNode.getParent());
            assertEquals(otherNode, interceptorsNode.getParent());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void movingBuiltInOutOfOtherPersistsLayoutForServer() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore runtime =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of(server("libera"))));

    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable(runtime);
            invokeAddServerRoot(dockable, "libera");

            Object serverNodes = serverNodes(dockable, "libera");
            DefaultMutableTreeNode serverNode = nodeField(serverNodes, "serverNode");
            DefaultMutableTreeNode otherNode = nodeField(serverNodes, "otherNode");
            DefaultMutableTreeNode statusNode = leafNode(dockable, new TargetRef("libera", "status"));
            DefaultTreeModel model = model(dockable);

            model.removeNodeFromParent(statusNode);
            int insertIdx = serverNode.getIndex(otherNode);
            model.insertNodeInto(statusNode, serverNode, Math.max(0, insertIdx));

            invokePersistBuiltInLayoutFromTree(dockable, "libera");

            RuntimeConfigStore.ServerTreeBuiltInLayout persisted =
                runtime.readServerTreeBuiltInLayoutByServer().get("libera");
            assertNotNull(persisted);
            assertEquals(
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.SERVER, persisted.rootOrder().get(0));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static ServerTreeDockable newDockable(RuntimeConfigStore runtimeConfig) {
    return new ServerTreeDockable(
        null,
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

  private static IrcProperties.Server server(String id) {
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
        List.of(),
        List.of(),
        null);
  }

  private static void invokeAddServerRoot(ServerTreeDockable dockable, String serverId)
      throws Exception {
    Method m = ServerTreeDockable.class.getDeclaredMethod("addServerRoot", String.class);
    m.setAccessible(true);
    m.invoke(dockable, serverId);
  }

  private static void invokePersistBuiltInLayoutFromTree(ServerTreeDockable dockable, String serverId)
      throws Exception {
    Method m = ServerTreeDockable.class.getDeclaredMethod("persistBuiltInLayoutFromTree", String.class);
    m.setAccessible(true);
    m.invoke(dockable, serverId);
  }

  @SuppressWarnings("unchecked")
  private static Object serverNodes(ServerTreeDockable dockable, String serverId) throws Exception {
    Field f = ServerTreeDockable.class.getDeclaredField("servers");
    f.setAccessible(true);
    Map<String, Object> servers = (Map<String, Object>) f.get(dockable);
    return servers.get(serverId);
  }

  private static DefaultMutableTreeNode nodeField(Object holder, String field) throws Exception {
    Field f = holder.getClass().getDeclaredField(field);
    f.setAccessible(true);
    return (DefaultMutableTreeNode) f.get(holder);
  }

  @SuppressWarnings("unchecked")
  private static DefaultMutableTreeNode leafNode(ServerTreeDockable dockable, TargetRef ref)
      throws Exception {
    Field f = ServerTreeDockable.class.getDeclaredField("leaves");
    f.setAccessible(true);
    Map<TargetRef, DefaultMutableTreeNode> leaves =
        (Map<TargetRef, DefaultMutableTreeNode>) f.get(dockable);
    return leaves.get(ref);
  }

  private static DefaultTreeModel model(ServerTreeDockable dockable) throws Exception {
    Field f = ServerTreeDockable.class.getDeclaredField("model");
    f.setAccessible(true);
    return (DefaultTreeModel) f.get(dockable);
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}

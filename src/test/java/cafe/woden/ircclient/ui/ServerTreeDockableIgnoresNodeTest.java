package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerEntry;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeDockableIgnoresNodeTest {

  @Test
  void serverRootIncludesIgnoresLeafAfterFilters() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef filtersRef = TargetRef.weechatFilters("libera");
            TargetRef ignoresRef = TargetRef.ignores("libera");

            DefaultMutableTreeNode filtersNode = findLeafNode(dockable, filtersRef);
            DefaultMutableTreeNode ignoresNode = findLeafNode(dockable, ignoresRef);

            assertNotNull(filtersNode);
            assertNotNull(ignoresNode);
            assertEquals(filtersNode.getParent(), ignoresNode.getParent());

            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) ignoresNode.getParent();
            int filtersIdx = parent.getIndex(filtersNode);
            int ignoresIdx = parent.getIndex(ignoresNode);
            assertEquals(filtersIdx + 1, ignoresIdx);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void removingServerWhileIgnoresIsSelectedFallsBackToRemainingServerStatus() throws Exception {
    AtomicReference<ServerTreeDockable> dockableRef = new AtomicReference<>();

    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            dockableRef.set(dockable);
            invokeAddServerRoot(dockable, "libera");
            invokeAddServerRoot(dockable, "oftc");

            TargetRef selectedIgnores = TargetRef.ignores("libera");
            dockable.selectTarget(selectedIgnores);
            assertEquals(selectedIgnores, selectedTargetRef(dockable));

            invokeSyncServers(dockable, List.of(serverEntry("oftc")));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });

    // Let syncServers()'s invokeLater fallback run.
    onEdt(
        () -> {
          try {
            TargetRef selected = selectedTargetRef(dockableRef.get());
            assertEquals(new TargetRef("oftc", "status"), selected);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static ServerTreeDockable newDockable() {
    return new ServerTreeDockable(
        null,
        null,
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

  private static void invokeSyncServers(ServerTreeDockable dockable, List<ServerEntry> entries)
      throws Exception {
    Method m = ServerTreeDockable.class.getDeclaredMethod("syncServers", List.class);
    m.setAccessible(true);
    m.invoke(dockable, entries);
  }

  private static TargetRef selectedTargetRef(ServerTreeDockable dockable) throws Exception {
    Method m = ServerTreeDockable.class.getDeclaredMethod("selectedTargetRef");
    m.setAccessible(true);
    return (TargetRef) m.invoke(dockable);
  }

  private static ServerEntry serverEntry(String id) {
    return ServerEntry.persistent(
        new IrcProperties.Server(
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
            null));
  }

  @SuppressWarnings("unchecked")
  private static DefaultMutableTreeNode findLeafNode(ServerTreeDockable dockable, TargetRef ref)
      throws Exception {
    Field leavesField = ServerTreeDockable.class.getDeclaredField("leaves");
    leavesField.setAccessible(true);
    Map<TargetRef, DefaultMutableTreeNode> leaves =
        (Map<TargetRef, DefaultMutableTreeNode>) leavesField.get(dockable);
    return leaves.get(ref);
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}

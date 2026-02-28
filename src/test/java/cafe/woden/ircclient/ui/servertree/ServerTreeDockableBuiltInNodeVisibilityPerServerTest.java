package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeDockableBuiltInNodeVisibilityPerServerTest {

  @Test
  void hidingNotificationsForOneServerDoesNotAffectOthers() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");
            invokeAddServerRoot(dockable, "oftc");

            TargetRef liberaNotifications = TargetRef.notifications("libera");
            TargetRef oftcNotifications = TargetRef.notifications("oftc");
            assertTrue(hasLeaf(dockable, liberaNotifications));
            assertTrue(hasLeaf(dockable, oftcNotifications));

            dockable.setNotificationsNodeVisibleForServer("libera", false);

            assertFalse(dockable.isNotificationsNodeVisibleForServer("libera"));
            assertTrue(dockable.isNotificationsNodeVisibleForServer("oftc"));
            assertFalse(hasLeaf(dockable, liberaNotifications));
            assertTrue(hasLeaf(dockable, oftcNotifications));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void hidingServerNodeFallsBackToNotificationsForThatServer() throws Exception {
    onEdt(
        () -> {
          try {
            String serverId = "libera";
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, serverId);

            TargetRef statusRef = new TargetRef(serverId, "status");
            TargetRef notificationsRef = TargetRef.notifications(serverId);
            assertTrue(hasLeaf(dockable, statusRef));
            assertTrue(hasLeaf(dockable, notificationsRef));

            dockable.selectTarget(statusRef);
            assertEquals(statusRef, selectedTargetRef(dockable));

            dockable.setServerNodeVisibleForServer(serverId, false);

            assertFalse(dockable.isServerNodeVisibleForServer(serverId));
            assertFalse(hasLeaf(dockable, statusRef));
            assertEquals(notificationsRef, selectedTargetRef(dockable));
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

  private static TargetRef selectedTargetRef(ServerTreeDockable dockable) throws Exception {
    Method m = ServerTreeDockable.class.getDeclaredMethod("selectedTargetRef");
    m.setAccessible(true);
    return (TargetRef) m.invoke(dockable);
  }

  @SuppressWarnings("unchecked")
  private static boolean hasLeaf(ServerTreeDockable dockable, TargetRef ref) throws Exception {
    Field f = ServerTreeDockable.class.getDeclaredField("leaves");
    f.setAccessible(true);
    Map<TargetRef, DefaultMutableTreeNode> leaves =
        (Map<TargetRef, DefaultMutableTreeNode>) f.get(dockable);
    return leaves.containsKey(ref);
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}

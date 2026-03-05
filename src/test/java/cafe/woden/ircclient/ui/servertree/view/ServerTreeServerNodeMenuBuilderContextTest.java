package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeServerNodeMenuBuilderContextTest {

  @Test
  void contextDelegatesServerNodeMenuOperations() {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("server");
    Action upAction =
        new AbstractAction("Up") {
          @Override
          public void actionPerformed(java.awt.event.ActionEvent e) {}
        };
    Action downAction =
        new AbstractAction("Down") {
          @Override
          public void actionPerformed(java.awt.event.ActionEvent e) {}
        };

    AtomicReference<String> connectedServer = new AtomicReference<>();
    AtomicReference<String> disconnectedServer = new AtomicReference<>();
    AtomicReference<String> openedServerInfo = new AtomicReference<>();
    AtomicReference<String> openedQuasselSetup = new AtomicReference<>();
    AtomicReference<String> openedQuasselNetworkManager = new AtomicReference<>();
    AtomicReference<String> promptedInterceptorServer = new AtomicReference<>();
    AtomicReference<String> savedEphemeralServer = new AtomicReference<>();
    AtomicReference<String> editedServer = new AtomicReference<>();
    AtomicReference<String> rememberedAutoConnectServer = new AtomicReference<>();
    AtomicReference<Boolean> rememberedAutoConnectEnabled = new AtomicReference<>();
    AtomicReference<String> refreshedBackendId = new AtomicReference<>();
    AtomicReference<String> autoConnectBackendId = new AtomicReference<>();
    AtomicReference<String> autoConnectOrigin = new AtomicReference<>();
    AtomicReference<Boolean> autoConnectEnabled = new AtomicReference<>();

    ServerTreeServerNodeMenuBuilder.Context context =
        ServerTreeServerNodeMenuBuilder.context(
            value -> value == node,
            serverId -> "Pretty " + serverId,
            serverId -> ConnectionState.CONNECTED,
            serverId -> "diagnostics",
            serverId -> Optional.<ServerEntry>empty(),
            () -> upAction,
            () -> downAction,
            connectedServer::set,
            disconnectedServer::set,
            openedServerInfo::set,
            openedQuasselSetup::set,
            openedQuasselNetworkManager::set,
            () -> true,
            promptedInterceptorServer::set,
            () -> true,
            savedEphemeralServer::set,
            editedServer::set,
            () -> true,
            (serverId, defaultValue) -> !defaultValue,
            (serverId, enabledValue) -> {
              rememberedAutoConnectServer.set(serverId);
              rememberedAutoConnectEnabled.set(enabledValue);
            },
            serverId -> ServerTreeBouncerBackends.SOJU,
            (backendId, serverId) ->
                ServerTreeBouncerBackends.SOJU.equals(backendId)
                    ? "soju-origin"
                    : ServerTreeBouncerBackends.ZNC.equals(backendId)
                        ? "znc-origin"
                        : "generic-origin",
            (backendId, originId, networkKey) -> ServerTreeBouncerBackends.SOJU.equals(backendId),
            serverId -> "display-" + serverId,
            (backendId, originId, networkKey, enabledValue) -> {
              autoConnectBackendId.set(backendId);
              autoConnectOrigin.set(originId);
              autoConnectEnabled.set(enabledValue);
            },
            refreshedBackendId::set,
            value -> "libera");

    assertTrue(context.isRootServerNode(node));
    assertEquals("Pretty libera", context.prettyServerLabel("libera"));
    assertEquals(ConnectionState.CONNECTED, context.connectionStateForServer("libera"));
    assertEquals("diagnostics", context.connectionDiagnosticsTipForServer("libera"));
    assertTrue(context.serverEntry("libera").isEmpty());
    assertSame(upAction, context.moveNodeUpAction());
    assertSame(downAction, context.moveNodeDownAction());

    context.requestConnectServer("libera");
    context.requestDisconnectServer("libera");
    context.openServerInfoDialog("libera");
    context.openQuasselSetup("libera");
    context.openQuasselNetworkManager("libera");
    context.promptAndAddInterceptor("libera");
    assertEquals("libera", connectedServer.get());
    assertEquals("libera", disconnectedServer.get());
    assertEquals("libera", openedServerInfo.get());
    assertEquals("libera", openedQuasselSetup.get());
    assertEquals("libera", openedQuasselNetworkManager.get());
    assertEquals("libera", promptedInterceptorServer.get());

    assertTrue(context.interceptorStoreAvailable());
    assertTrue(context.serverDialogsAvailable());
    context.openSaveEphemeralServer("libera");
    context.openEditServer("libera");
    assertEquals("libera", savedEphemeralServer.get());
    assertEquals("libera", editedServer.get());

    assertTrue(context.runtimeConfigAvailable());
    assertFalse(context.readServerAutoConnectOnStart("libera", true));
    context.rememberServerAutoConnectOnStart("libera", false);
    assertEquals("libera", rememberedAutoConnectServer.get());
    assertFalse(rememberedAutoConnectEnabled.get());

    assertEquals(ServerTreeBouncerBackends.SOJU, context.backendIdForEphemeralServer("libera"));
    assertEquals("soju-origin", context.originForServer(ServerTreeBouncerBackends.SOJU, "libera"));
    assertEquals("znc-origin", context.originForServer(ServerTreeBouncerBackends.ZNC, "libera"));
    assertEquals(
        "generic-origin", context.originForServer(ServerTreeBouncerBackends.GENERIC, "libera"));
    assertEquals("display-libera", context.serverDisplayNameOrDefault("libera"));
    assertTrue(context.isAutoConnectEnabled(ServerTreeBouncerBackends.SOJU, "o", "n"));
    assertFalse(context.isAutoConnectEnabled(ServerTreeBouncerBackends.ZNC, "o", "n"));
    assertFalse(context.isAutoConnectEnabled(ServerTreeBouncerBackends.GENERIC, "o", "n"));
    context.setAutoConnectEnabled(
        ServerTreeBouncerBackends.SOJU, "soju-origin", "display-libera", true);
    context.refreshAutoConnectBadges(ServerTreeBouncerBackends.SOJU);
    assertEquals(ServerTreeBouncerBackends.SOJU, autoConnectBackendId.get());
    assertEquals("soju-origin", autoConnectOrigin.get());
    assertTrue(autoConnectEnabled.get());
    assertEquals(ServerTreeBouncerBackends.SOJU, refreshedBackendId.get());

    assertEquals("libera", context.owningServerIdForNode(node));
  }
}

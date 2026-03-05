package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.config.ServerEntry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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
    AtomicReference<String> sojuAutoConnectOrigin = new AtomicReference<>();
    AtomicReference<String> zncAutoConnectOrigin = new AtomicReference<>();
    AtomicReference<String> genericAutoConnectOrigin = new AtomicReference<>();
    AtomicReference<Boolean> sojuAutoConnectEnabled = new AtomicReference<>();
    AtomicReference<Boolean> zncAutoConnectEnabled = new AtomicReference<>();
    AtomicReference<Boolean> genericAutoConnectEnabled = new AtomicReference<>();
    AtomicBoolean refreshedSojuBadges = new AtomicBoolean(false);
    AtomicBoolean refreshedZncBadges = new AtomicBoolean(false);
    AtomicBoolean refreshedGenericBadges = new AtomicBoolean(false);

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
            serverId -> true,
            serverId -> false,
            serverId -> false,
            serverId -> "soju-origin",
            serverId -> "znc-origin",
            serverId -> "generic-origin",
            serverId -> "display-" + serverId,
            (originId, networkKey) -> true,
            (originId, networkKey) -> false,
            (originId, networkKey) -> false,
            (originId, networkKey, enabledValue) -> {
              sojuAutoConnectOrigin.set(originId);
              sojuAutoConnectEnabled.set(enabledValue);
            },
            (originId, networkKey, enabledValue) -> {
              zncAutoConnectOrigin.set(originId);
              zncAutoConnectEnabled.set(enabledValue);
            },
            (originId, networkKey, enabledValue) -> {
              genericAutoConnectOrigin.set(originId);
              genericAutoConnectEnabled.set(enabledValue);
            },
            () -> refreshedSojuBadges.set(true),
            () -> refreshedZncBadges.set(true),
            () -> refreshedGenericBadges.set(true),
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

    assertTrue(context.isSojuEphemeralServer("libera"));
    assertFalse(context.isZncEphemeralServer("libera"));
    assertFalse(context.isGenericEphemeralServer("libera"));
    assertEquals("soju-origin", context.sojuOriginForServer("libera"));
    assertEquals("znc-origin", context.zncOriginForServer("libera"));
    assertEquals("generic-origin", context.genericOriginForServer("libera"));
    assertEquals("display-libera", context.serverDisplayNameOrDefault("libera"));
    assertTrue(context.isSojuAutoConnectEnabled("o", "n"));
    assertFalse(context.isZncAutoConnectEnabled("o", "n"));
    assertFalse(context.isGenericAutoConnectEnabled("o", "n"));
    context.setSojuAutoConnectEnabled("soju-origin", "display-libera", true);
    context.setZncAutoConnectEnabled("znc-origin", "display-libera", false);
    context.setGenericAutoConnectEnabled("generic-origin", "display-libera", true);
    context.refreshSojuAutoConnectBadges();
    context.refreshZncAutoConnectBadges();
    context.refreshGenericAutoConnectBadges();
    assertEquals("soju-origin", sojuAutoConnectOrigin.get());
    assertEquals("znc-origin", zncAutoConnectOrigin.get());
    assertEquals("generic-origin", genericAutoConnectOrigin.get());
    assertTrue(sojuAutoConnectEnabled.get());
    assertFalse(zncAutoConnectEnabled.get());
    assertTrue(genericAutoConnectEnabled.get());
    assertTrue(refreshedSojuBadges.get());
    assertTrue(refreshedZncBadges.get());
    assertTrue(refreshedGenericBadges.get());

    assertEquals("libera", context.owningServerIdForNode(node));
  }
}

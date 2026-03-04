package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.ServerEntry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeContextMenuBuilderContextTest {

  @Test
  void contextDelegatesContextMenuOperations() {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("server");
    TargetRef channelRef = new TargetRef("libera", "#ircafe");
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
    AtomicReference<String> savedEphemeralServer = new AtomicReference<>();
    AtomicReference<String> editedServer = new AtomicReference<>();
    AtomicReference<String> rememberedAutoConnectServer = new AtomicReference<>();
    AtomicReference<Boolean> rememberedAutoConnectEnabled = new AtomicReference<>();
    AtomicReference<String> sojuAutoConnectOrigin = new AtomicReference<>();
    AtomicReference<String> zncAutoConnectOrigin = new AtomicReference<>();
    AtomicReference<Boolean> sojuAutoConnectEnabled = new AtomicReference<>();
    AtomicReference<Boolean> zncAutoConnectEnabled = new AtomicReference<>();
    AtomicBoolean refreshedSojuBadges = new AtomicBoolean(false);
    AtomicBoolean refreshedZncBadges = new AtomicBoolean(false);
    AtomicReference<TargetRef> openedPinnedChat = new AtomicReference<>();
    AtomicReference<TargetRef> clearedLogTarget = new AtomicReference<>();
    AtomicReference<String> clearedLogLabel = new AtomicReference<>();
    AtomicReference<TargetRef> joinedChannel = new AtomicReference<>();
    AtomicReference<TargetRef> disconnectedChannel = new AtomicReference<>();
    AtomicReference<TargetRef> closedChannel = new AtomicReference<>();
    AtomicReference<TargetRef> detachedChannel = new AtomicReference<>();
    AtomicReference<Boolean> autoReattach = new AtomicReference<>();
    AtomicReference<Boolean> pinned = new AtomicReference<>();
    AtomicReference<Boolean> muted = new AtomicReference<>();
    AtomicReference<TargetRef> modeDetailsTarget = new AtomicReference<>();
    AtomicReference<TargetRef> modeRefreshTarget = new AtomicReference<>();
    AtomicReference<TargetRef> modeSetTarget = new AtomicReference<>();
    AtomicReference<String> modeSetLabel = new AtomicReference<>();
    AtomicReference<TargetRef> closedTarget = new AtomicReference<>();
    AtomicReference<TargetRef> interceptorEnabledTarget = new AtomicReference<>();
    AtomicReference<Boolean> interceptorEnabled = new AtomicReference<>();
    AtomicReference<TargetRef> renamedInterceptor = new AtomicReference<>();
    AtomicReference<String> renamedInterceptorLabel = new AtomicReference<>();
    AtomicReference<TargetRef> deletedInterceptor = new AtomicReference<>();
    AtomicReference<String> deletedInterceptorLabel = new AtomicReference<>();

    ServerTreeContextMenuBuilder.Context context =
        ServerTreeContextMenuBuilder.context(
            value -> value == node,
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
            serverId -> {},
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
            serverId -> "soju-origin",
            serverId -> "znc-origin",
            serverId -> "display-" + serverId,
            (originId, networkKey) -> true,
            (originId, networkKey) -> false,
            (originId, networkKey, enabledValue) -> {
              sojuAutoConnectOrigin.set(originId);
              sojuAutoConnectEnabled.set(enabledValue);
            },
            (originId, networkKey, enabledValue) -> {
              zncAutoConnectOrigin.set(originId);
              zncAutoConnectEnabled.set(enabledValue);
            },
            () -> refreshedSojuBadges.set(true),
            () -> refreshedZncBadges.set(true),
            value -> value == node,
            value -> "libera",
            openedPinnedChat::set,
            (target, label) -> {
              clearedLogTarget.set(target);
              clearedLogLabel.set(label);
            },
            target -> true,
            joinedChannel::set,
            disconnectedChannel::set,
            closedChannel::set,
            serverId -> true,
            detachedChannel::set,
            target -> false,
            (target, value) -> autoReattach.set(value),
            target -> false,
            (target, value) -> pinned.set(value),
            target -> true,
            (target, value) -> muted.set(value),
            modeDetailsTarget::set,
            modeRefreshTarget::set,
            target -> true,
            (target, label) -> {
              modeSetTarget.set(target);
              modeSetLabel.set(label);
            },
            closedTarget::set,
            target -> null,
            (target, value) -> {
              interceptorEnabledTarget.set(target);
              interceptorEnabled.set(value);
            },
            (target, label) -> {
              renamedInterceptor.set(target);
              renamedInterceptorLabel.set(label);
            },
            (target, label) -> {
              deletedInterceptor.set(target);
              deletedInterceptorLabel.set(label);
            });

    assertTrue(context.isServerNode(node));
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
    assertEquals("libera", connectedServer.get());
    assertEquals("libera", disconnectedServer.get());
    assertEquals("libera", openedServerInfo.get());
    assertEquals("libera", openedQuasselSetup.get());
    assertEquals("libera", openedQuasselNetworkManager.get());

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
    assertEquals("soju-origin", context.sojuOriginForServer("libera"));
    assertEquals("znc-origin", context.zncOriginForServer("libera"));
    assertEquals("display-libera", context.serverDisplayNameOrDefault("libera"));
    assertTrue(context.isSojuAutoConnectEnabled("o", "n"));
    assertFalse(context.isZncAutoConnectEnabled("o", "n"));
    context.setSojuAutoConnectEnabled("soju-origin", "display-libera", true);
    context.setZncAutoConnectEnabled("znc-origin", "display-libera", false);
    context.refreshSojuAutoConnectBadges();
    context.refreshZncAutoConnectBadges();
    assertEquals("soju-origin", sojuAutoConnectOrigin.get());
    assertEquals("znc-origin", zncAutoConnectOrigin.get());
    assertTrue(sojuAutoConnectEnabled.get());
    assertFalse(zncAutoConnectEnabled.get());
    assertTrue(refreshedSojuBadges.get());
    assertTrue(refreshedZncBadges.get());

    assertTrue(context.isInterceptorsGroupNode(node));
    assertEquals("libera", context.owningServerIdForNode(node));
    context.openPinnedChat(channelRef);
    context.confirmAndRequestClearLog(channelRef, "#ircafe");
    assertSame(channelRef, openedPinnedChat.get());
    assertSame(channelRef, clearedLogTarget.get());
    assertEquals("#ircafe", clearedLogLabel.get());

    assertTrue(context.isChannelDisconnected(channelRef));
    context.requestJoinChannel(channelRef);
    context.requestDisconnectChannel(channelRef);
    context.requestCloseChannel(channelRef);
    assertSame(channelRef, joinedChannel.get());
    assertSame(channelRef, disconnectedChannel.get());
    assertSame(channelRef, closedChannel.get());

    assertTrue(context.supportsBouncerDetach("libera"));
    context.requestBouncerDetachChannel(channelRef);
    assertSame(channelRef, detachedChannel.get());
    assertFalse(context.isChannelAutoReattach(channelRef));
    context.setChannelAutoReattach(channelRef, true);
    assertTrue(autoReattach.get());
    assertFalse(context.isChannelPinned(channelRef));
    context.setChannelPinned(channelRef, true);
    assertTrue(pinned.get());
    assertTrue(context.isChannelMuted(channelRef));
    context.setChannelMuted(channelRef, false);
    assertFalse(muted.get());

    context.openChannelModeDetails(channelRef);
    context.requestChannelModeRefresh(channelRef);
    assertTrue(context.canEditChannelModes(channelRef));
    context.promptAndRequestChannelModeSet(channelRef, "#ircafe");
    assertSame(channelRef, modeDetailsTarget.get());
    assertSame(channelRef, modeRefreshTarget.get());
    assertSame(channelRef, modeSetTarget.get());
    assertEquals("#ircafe", modeSetLabel.get());

    context.requestCloseTarget(channelRef);
    assertSame(channelRef, closedTarget.get());
    context.setInterceptorEnabled(channelRef, true);
    context.promptRenameInterceptor(channelRef, "Current");
    context.confirmDeleteInterceptor(channelRef, "Current");
    assertSame(channelRef, interceptorEnabledTarget.get());
    assertTrue(interceptorEnabled.get());
    assertSame(channelRef, renamedInterceptor.get());
    assertEquals("Current", renamedInterceptorLabel.get());
    assertSame(channelRef, deletedInterceptor.get());
    assertEquals("Current", deletedInterceptorLabel.get());
  }
}

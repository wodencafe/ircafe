package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.model.InterceptorDefinition;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.junit.jupiter.api.Test;

class ServerTreeTargetNodeMenuBuilderContextTest {

  @Test
  void contextDelegatesTargetNodeMenuOperations() {
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

    ServerTreeTargetNodeMenuBuilder.Context context =
        ServerTreeTargetNodeMenuBuilder.context(
            openedPinnedChat::set,
            () -> upAction,
            () -> downAction,
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
            () -> true,
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

    context.openPinnedChat(channelRef);
    assertSame(channelRef, openedPinnedChat.get());
    assertSame(upAction, context.moveNodeUpAction());
    assertSame(downAction, context.moveNodeDownAction());

    context.confirmAndRequestClearLog(channelRef, "#ircafe");
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

    InterceptorDefinition definition = context.interceptorDefinition(channelRef);
    assertNull(definition);
    assertTrue(context.interceptorStoreAvailable());
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

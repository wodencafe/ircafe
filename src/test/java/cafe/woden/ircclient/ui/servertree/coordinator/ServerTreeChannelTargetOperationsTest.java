package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeEdtExecutor;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestEmitter;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ServerTreeChannelTargetOperationsTest {

  @Test
  void channelOperationsDelegateAndRespectPolicy() throws Exception {
    ServerTreeChannelStateCoordinator channelStateCoordinator =
        mock(ServerTreeChannelStateCoordinator.class);
    ServerTreeRequestEmitter requestEmitter = mock(ServerTreeRequestEmitter.class);
    AtomicBoolean mutedCallback = new AtomicBoolean(false);

    ServerTreeChannelTargetOperations operations =
        new ServerTreeChannelTargetOperations(new ServerTreeEdtExecutor());
    ServerTreeChannelTargetOperations.Context context =
        ServerTreeChannelTargetOperations.context(
            channelStateCoordinator, requestEmitter, (ref, muted) -> mutedCallback.set(muted));

    TargetRef channelRef = new TargetRef("libera", "#ircafe");
    when(channelStateCoordinator.isChannelAutoReattach(channelRef)).thenReturn(false);
    when(channelStateCoordinator.isChannelPinned(channelRef)).thenReturn(true);
    when(channelStateCoordinator.isChannelMuted(channelRef)).thenReturn(false);

    assertFalse(operations.isChannelAutoReattach(context, channelRef));
    assertTrue(operations.isChannelPinned(context, channelRef));
    assertFalse(operations.isChannelMuted(context, channelRef));

    operations.setChannelAutoReattach(context, channelRef, true);
    operations.setChannelPinned(context, channelRef, false);
    operations.setChannelMuted(context, channelRef, true);
    flushEdt();

    verify(channelStateCoordinator).setChannelAutoReattach(channelRef, true);
    verify(channelStateCoordinator).setChannelPinned(channelRef, false);
    verify(channelStateCoordinator).setChannelMuted(channelRef, true);
    assertTrue(mutedCallback.get());

    operations.requestJoinChannel(context, channelRef);
    operations.requestDisconnectChannel(context, channelRef);
    operations.requestCloseChannel(context, channelRef);

    verify(requestEmitter).emitJoinChannel(channelRef);
    verify(requestEmitter).emitDisconnectChannel(channelRef);
    verify(requestEmitter).emitCloseChannel(channelRef);

    operations.setCanEditChannelModes(context, (serverId, channel) -> true);
    assertTrue(operations.canEditChannelModesForTarget(context, channelRef));
  }

  @Test
  void nonChannelTargetsAreIgnored() throws Exception {
    ServerTreeChannelStateCoordinator channelStateCoordinator =
        mock(ServerTreeChannelStateCoordinator.class);
    ServerTreeRequestEmitter requestEmitter = mock(ServerTreeRequestEmitter.class);

    ServerTreeChannelTargetOperations operations =
        new ServerTreeChannelTargetOperations(new ServerTreeEdtExecutor());
    ServerTreeChannelTargetOperations.Context context =
        ServerTreeChannelTargetOperations.context(
            channelStateCoordinator, requestEmitter, (ref, muted) -> {});

    TargetRef statusRef = new TargetRef("libera", "status");

    assertTrue(operations.isChannelAutoReattach(context, statusRef));
    assertFalse(operations.isChannelPinned(context, statusRef));
    assertFalse(operations.isChannelMuted(context, statusRef));

    operations.setChannelAutoReattach(context, statusRef, true);
    operations.setChannelPinned(context, statusRef, true);
    operations.setChannelMuted(context, statusRef, true);
    operations.requestJoinChannel(context, statusRef);
    operations.requestDisconnectChannel(context, statusRef);
    operations.requestCloseChannel(context, statusRef);
    flushEdt();

    verify(channelStateCoordinator, never()).setChannelAutoReattach(statusRef, true);
    verify(channelStateCoordinator, never()).setChannelPinned(statusRef, true);
    verify(channelStateCoordinator, never()).setChannelMuted(statusRef, true);
    verify(requestEmitter, never()).emitJoinChannel(statusRef);
    verify(requestEmitter, never()).emitDisconnectChannel(statusRef);
    verify(requestEmitter, never()).emitCloseChannel(statusRef);
  }

  @Test
  void canEditChannelModesReturnsFalseOnPredicateFailure() {
    ServerTreeChannelStateCoordinator channelStateCoordinator =
        mock(ServerTreeChannelStateCoordinator.class);
    ServerTreeRequestEmitter requestEmitter = mock(ServerTreeRequestEmitter.class);

    ServerTreeChannelTargetOperations operations =
        new ServerTreeChannelTargetOperations(new ServerTreeEdtExecutor());
    ServerTreeChannelTargetOperations.Context context =
        ServerTreeChannelTargetOperations.context(
            channelStateCoordinator, requestEmitter, (ref, muted) -> {});

    TargetRef channelRef = new TargetRef("libera", "#ircafe");
    operations.setCanEditChannelModes(
        context,
        (serverId, channel) -> {
          throw new RuntimeException("boom");
        });

    assertFalse(operations.canEditChannelModesForTarget(context, channelRef));
  }

  private static void flushEdt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {});
  }
}

package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
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
        new ServerTreeChannelTargetOperations(
            new ServerTreeEdtExecutor(),
            channelStateCoordinator,
            requestEmitter,
            (ref, muted) -> mutedCallback.set(muted));

    TargetRef channelRef = new TargetRef("libera", "#ircafe");
    when(channelStateCoordinator.isChannelAutoReattach(channelRef)).thenReturn(false);
    when(channelStateCoordinator.isChannelPinned(channelRef)).thenReturn(true);
    when(channelStateCoordinator.isChannelMuted(channelRef)).thenReturn(false);

    assertFalse(operations.isChannelAutoReattach(channelRef));
    assertTrue(operations.isChannelPinned(channelRef));
    assertFalse(operations.isChannelMuted(channelRef));

    operations.setChannelAutoReattach(channelRef, true);
    operations.setChannelPinned(channelRef, false);
    operations.setChannelMuted(channelRef, true);
    flushEdt();

    verify(channelStateCoordinator).setChannelAutoReattach(channelRef, true);
    verify(channelStateCoordinator).setChannelPinned(channelRef, false);
    verify(channelStateCoordinator).setChannelMuted(channelRef, true);
    assertTrue(mutedCallback.get());

    operations.requestJoinChannel(channelRef);
    operations.requestDisconnectChannel(channelRef);
    operations.requestCloseChannel(channelRef);

    verify(requestEmitter).emitJoinChannel(channelRef);
    verify(requestEmitter).emitDisconnectChannel(channelRef);
    verify(requestEmitter).emitCloseChannel(channelRef);

    operations.setCanEditChannelModes((serverId, channel) -> true);
    assertTrue(operations.canEditChannelModesForTarget(channelRef));
  }

  @Test
  void nonChannelTargetsAreIgnored() throws Exception {
    ServerTreeChannelStateCoordinator channelStateCoordinator =
        mock(ServerTreeChannelStateCoordinator.class);
    ServerTreeRequestEmitter requestEmitter = mock(ServerTreeRequestEmitter.class);

    ServerTreeChannelTargetOperations operations =
        new ServerTreeChannelTargetOperations(
            new ServerTreeEdtExecutor(),
            channelStateCoordinator,
            requestEmitter,
            (ref, muted) -> {});

    TargetRef statusRef = new TargetRef("libera", "status");

    assertTrue(operations.isChannelAutoReattach(statusRef));
    assertFalse(operations.isChannelPinned(statusRef));
    assertFalse(operations.isChannelMuted(statusRef));

    operations.setChannelAutoReattach(statusRef, true);
    operations.setChannelPinned(statusRef, true);
    operations.setChannelMuted(statusRef, true);
    operations.requestJoinChannel(statusRef);
    operations.requestDisconnectChannel(statusRef);
    operations.requestCloseChannel(statusRef);
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
        new ServerTreeChannelTargetOperations(
            new ServerTreeEdtExecutor(),
            channelStateCoordinator,
            requestEmitter,
            (ref, muted) -> {});

    TargetRef channelRef = new TargetRef("libera", "#ircafe");
    operations.setCanEditChannelModes(
        (serverId, channel) -> {
          throw new RuntimeException("boom");
        });

    assertFalse(operations.canEditChannelModesForTarget(channelRef));
  }

  private static void flushEdt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {});
  }
}

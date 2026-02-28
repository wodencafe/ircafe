package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.NickContextMenuFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class DccActionCoordinatorTest {

  @Test
  void requestActionMapsStandardActionsToDccCommands() {
    TargetRef ctx = new TargetRef("libera", "#ircafe");
    List<TargetRef> emittedTargets = new ArrayList<>();
    List<String> emittedCommands = new ArrayList<>();

    DccActionCoordinator coordinator =
        new DccActionCoordinator(
            new JPanel(),
            (target, command) -> {
              emittedTargets.add(target);
              emittedCommands.add(command);
            });

    coordinator.requestAction(ctx, "  alice  ", NickContextMenuFactory.DccAction.CHAT);
    coordinator.requestAction(ctx, "alice", NickContextMenuFactory.DccAction.ACCEPT_CHAT);
    coordinator.requestAction(ctx, "alice", NickContextMenuFactory.DccAction.GET_FILE);
    coordinator.requestAction(ctx, "alice", NickContextMenuFactory.DccAction.CLOSE_CHAT);

    assertEquals(List.of(ctx, ctx, ctx, ctx), emittedTargets);
    assertEquals(
        List.of("/dcc chat alice", "/dcc accept alice", "/dcc get alice", "/dcc close alice"),
        emittedCommands);
  }

  @Test
  void requestActionIgnoresNullAndBlankInputs() {
    List<String> emittedCommands = new ArrayList<>();
    DccActionCoordinator coordinator =
        new DccActionCoordinator(new JPanel(), (target, command) -> emittedCommands.add(command));
    TargetRef ctx = new TargetRef("libera", "#ircafe");

    coordinator.requestAction(null, "alice", NickContextMenuFactory.DccAction.CHAT);
    coordinator.requestAction(ctx, "alice", null);
    coordinator.requestAction(ctx, "   ", NickContextMenuFactory.DccAction.CHAT);

    assertTrue(emittedCommands.isEmpty());
  }

  @Test
  void sendFileActionEmitsCommandForApprovedValidPath() {
    JFileChooser chooser = mock(JFileChooser.class);
    when(chooser.showOpenDialog(any())).thenReturn(JFileChooser.APPROVE_OPTION);
    File file = mock(File.class);
    when(file.getAbsolutePath()).thenReturn("/tmp/test.txt");
    when(chooser.getSelectedFile()).thenReturn(file);

    List<String> emittedCommands = new ArrayList<>();
    DccActionCoordinator coordinator =
        new DccActionCoordinator(
            new JPanel(),
            (target, command) -> emittedCommands.add(command),
            () -> chooser,
            owner -> {});

    coordinator.requestAction(
        new TargetRef("libera", "#ircafe"), "alice", NickContextMenuFactory.DccAction.SEND_FILE);

    verify(chooser).setDialogTitle("Send File to alice");
    verify(chooser).setFileSelectionMode(JFileChooser.FILES_ONLY);
    assertEquals(List.of("/dcc send alice /tmp/test.txt"), emittedCommands);
  }

  @Test
  void sendFileActionShowsWarningAndSuppressesCommandWhenPathContainsNewline() {
    JFileChooser chooser = mock(JFileChooser.class);
    when(chooser.showOpenDialog(any())).thenReturn(JFileChooser.APPROVE_OPTION);
    File file = mock(File.class);
    when(file.getAbsolutePath()).thenReturn("/tmp/bad\npath.txt");
    when(chooser.getSelectedFile()).thenReturn(file);
    AtomicInteger warnings = new AtomicInteger();
    List<String> emittedCommands = new ArrayList<>();

    DccActionCoordinator coordinator =
        new DccActionCoordinator(
            new JPanel(),
            (target, command) -> emittedCommands.add(command),
            () -> chooser,
            owner -> warnings.incrementAndGet());

    coordinator.requestAction(
        new TargetRef("libera", "#ircafe"), "alice", NickContextMenuFactory.DccAction.SEND_FILE);

    assertEquals(1, warnings.get());
    assertTrue(emittedCommands.isEmpty());
  }

  @Test
  void sendFileActionSkipsWhenDialogIsCancelled() {
    JFileChooser chooser = mock(JFileChooser.class);
    when(chooser.showOpenDialog(any())).thenReturn(JFileChooser.CANCEL_OPTION);
    List<String> emittedCommands = new ArrayList<>();

    DccActionCoordinator coordinator =
        new DccActionCoordinator(
            new JPanel(),
            (target, command) -> emittedCommands.add(command),
            () -> chooser,
            owner -> {});

    coordinator.requestAction(
        new TargetRef("libera", "#ircafe"), "alice", NickContextMenuFactory.DccAction.SEND_FILE);

    assertTrue(emittedCommands.isEmpty());
  }
}

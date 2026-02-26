package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SwingUiPortMonitorIntegrationTest {

  @Test
  void monitorActivationAndRefreshCallsAreSequencedOnEdt() throws Exception {
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    ChatDockable chat = mock(ChatDockable.class);
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    MentionPatternRegistry mentions = mock(MentionPatternRegistry.class);
    NotificationStore notificationStore = mock(NotificationStore.class);
    UserListDockable users = mock(UserListDockable.class);
    StatusBar statusBar = mock(StatusBar.class);
    ConnectButton connectBtn = mock(ConnectButton.class);
    DisconnectButton disconnectBtn = mock(DisconnectButton.class);
    TargetActivationBus activationBus = new TargetActivationBus();
    OutboundLineBus outboundBus = new OutboundLineBus();
    ChatDockManager chatDockManager = mock(ChatDockManager.class);
    ActiveInputRouter activeInputRouter = new ActiveInputRouter();

    SwingUiPort ui =
        new SwingUiPort(
            serverTree,
            chat,
            transcripts,
            mentions,
            notificationStore,
            users,
            statusBar,
            connectBtn,
            disconnectBtn,
            activationBus,
            outboundBus,
            chatDockManager,
            activeInputRouter);

    List<String> steps = new CopyOnWriteArrayList<>();
    Map<String, Boolean> onEdtByStep = new ConcurrentHashMap<>();
    TargetRef monitor = TargetRef.monitorGroup("libera");

    doAnswer(
            inv -> {
              TargetRef ref = inv.getArgument(0);
              assertEquals(monitor, ref);
              record("chat.setActiveTarget", steps, onEdtByStep);
              return null;
            })
        .when(chat)
        .setActiveTarget(org.mockito.ArgumentMatchers.any(TargetRef.class));

    doAnswer(
            inv -> {
              assertEquals("libera", inv.getArgument(0));
              assertEquals("alice", inv.getArgument(1));
              assertEquals(Boolean.TRUE, inv.getArgument(2, Boolean.class));
              record("tree.setPrivateMessageOnlineState", steps, onEdtByStep);
              return null;
            })
        .when(serverTree)
        .setPrivateMessageOnlineState(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyBoolean());

    doAnswer(
            inv -> {
              assertEquals("libera", inv.getArgument(0));
              assertEquals("alice", inv.getArgument(1));
              assertEquals(Boolean.TRUE, inv.getArgument(2, Boolean.class));
              record("chat.setPrivateMessageOnlineState", steps, onEdtByStep);
              return null;
            })
        .when(chat)
        .setPrivateMessageOnlineState(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyBoolean());

    doAnswer(
            inv -> {
              assertEquals("libera", inv.getArgument(0));
              record("tree.clearPrivateMessageOnlineStates", steps, onEdtByStep);
              return null;
            })
        .when(serverTree)
        .clearPrivateMessageOnlineStates(org.mockito.ArgumentMatchers.anyString());

    doAnswer(
            inv -> {
              assertEquals("libera", inv.getArgument(0));
              record("chat.clearPrivateMessageOnlineStates", steps, onEdtByStep);
              return null;
            })
        .when(chat)
        .clearPrivateMessageOnlineStates(org.mockito.ArgumentMatchers.anyString());

    Thread caller =
        new Thread(
            () -> {
              ui.setChatActiveTarget(monitor);
              ui.setPrivateMessageOnlineState("libera", "alice", true);
              ui.clearPrivateMessageOnlineStates("libera");
            },
            "monitor-ui-caller");
    caller.start();
    caller.join();
    flushEdt();

    assertEquals(
        List.of(
            "chat.setActiveTarget",
            "tree.setPrivateMessageOnlineState",
            "chat.setPrivateMessageOnlineState",
            "tree.clearPrivateMessageOnlineStates",
            "chat.clearPrivateMessageOnlineStates"),
        steps);
    for (String step : steps) {
      assertEquals(Boolean.TRUE, onEdtByStep.get(step), step + " should run on EDT");
    }
  }

  private static void record(String step, List<String> steps, Map<String, Boolean> onEdtByStep) {
    steps.add(step);
    onEdtByStep.put(step, SwingUtilities.isEventDispatchThread());
  }

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }
}

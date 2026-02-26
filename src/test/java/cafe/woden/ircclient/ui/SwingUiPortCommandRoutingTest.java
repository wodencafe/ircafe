package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
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

class SwingUiPortCommandRoutingTest {

  @Test
  void ensureTargetExistsDelegatesInOrderOnEdt() throws Exception {
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    ChatDockable chat = mock(ChatDockable.class);
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    ChatDockManager chatDockManager = mock(ChatDockManager.class);

    SwingUiPort ui =
        new SwingUiPort(
            serverTree,
            chat,
            transcripts,
            mock(MentionPatternRegistry.class),
            mock(NotificationStore.class),
            mock(UserListDockable.class),
            mock(StatusBar.class),
            mock(ConnectButton.class),
            mock(DisconnectButton.class),
            new TargetActivationBus(),
            new OutboundLineBus(),
            chatDockManager,
            new ActiveInputRouter());

    List<String> steps = new CopyOnWriteArrayList<>();
    Map<String, Boolean> onEdtByStep = new ConcurrentHashMap<>();
    TargetRef target = new TargetRef("libera", "#ircafe");

    doAnswer(
            inv -> {
              record("transcripts.ensureTargetExists", steps, onEdtByStep);
              return null;
            })
        .when(transcripts)
        .ensureTargetExists(target);

    doAnswer(
            inv -> {
              record("serverTree.ensureNode", steps, onEdtByStep);
              return null;
            })
        .when(serverTree)
        .ensureNode(target);

    Thread caller = new Thread(() -> ui.ensureTargetExists(target), "ui-port-caller");
    caller.start();
    caller.join();
    flushEdt();

    assertEquals(List.of("transcripts.ensureTargetExists", "serverTree.ensureNode"), steps);
    assertEquals(Boolean.TRUE, onEdtByStep.get("transcripts.ensureTargetExists"));
    assertEquals(Boolean.TRUE, onEdtByStep.get("serverTree.ensureNode"));
  }

  @Test
  void closeTargetDelegatesInOrderOnEdt() throws Exception {
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    ChatDockable chat = mock(ChatDockable.class);
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);

    SwingUiPort ui =
        new SwingUiPort(
            serverTree,
            chat,
            transcripts,
            mock(MentionPatternRegistry.class),
            mock(NotificationStore.class),
            mock(UserListDockable.class),
            mock(StatusBar.class),
            mock(ConnectButton.class),
            mock(DisconnectButton.class),
            new TargetActivationBus(),
            new OutboundLineBus(),
            mock(ChatDockManager.class),
            new ActiveInputRouter());

    List<String> steps = new CopyOnWriteArrayList<>();
    Map<String, Boolean> onEdtByStep = new ConcurrentHashMap<>();
    TargetRef target = new TargetRef("libera", "#channel");

    doAnswer(
            inv -> {
              record("serverTree.removeTarget", steps, onEdtByStep);
              return null;
            })
        .when(serverTree)
        .removeTarget(target);

    doAnswer(
            inv -> {
              record("chat.clearTopic", steps, onEdtByStep);
              return null;
            })
        .when(chat)
        .clearTopic(target);

    doAnswer(
            inv -> {
              record("transcripts.closeTarget", steps, onEdtByStep);
              return null;
            })
        .when(transcripts)
        .closeTarget(target);

    Thread caller = new Thread(() -> ui.closeTarget(target), "ui-port-caller-close");
    caller.start();
    caller.join();
    flushEdt();

    assertEquals(
        List.of("serverTree.removeTarget", "chat.clearTopic", "transcripts.closeTarget"), steps);
    assertEquals(Boolean.TRUE, onEdtByStep.get("serverTree.removeTarget"));
    assertEquals(Boolean.TRUE, onEdtByStep.get("chat.clearTopic"));
    assertEquals(Boolean.TRUE, onEdtByStep.get("transcripts.closeTarget"));
  }

  @Test
  void setInputEnabledDelegatesToChatAndPinnedInputsOnEdt() throws Exception {
    ChatDockable chat = mock(ChatDockable.class);
    ChatDockManager chatDockManager = mock(ChatDockManager.class);

    SwingUiPort ui =
        new SwingUiPort(
            mock(ServerTreeDockable.class),
            chat,
            mock(ChatTranscriptStore.class),
            mock(MentionPatternRegistry.class),
            mock(NotificationStore.class),
            mock(UserListDockable.class),
            mock(StatusBar.class),
            mock(ConnectButton.class),
            mock(DisconnectButton.class),
            new TargetActivationBus(),
            new OutboundLineBus(),
            chatDockManager,
            new ActiveInputRouter());

    List<String> steps = new CopyOnWriteArrayList<>();
    Map<String, Boolean> onEdtByStep = new ConcurrentHashMap<>();

    doAnswer(
            inv -> {
              record("chat.setInputEnabled", steps, onEdtByStep);
              return null;
            })
        .when(chat)
        .setInputEnabled(false);

    doAnswer(
            inv -> {
              record("chatDockManager.setPinnedInputsEnabled", steps, onEdtByStep);
              return null;
            })
        .when(chatDockManager)
        .setPinnedInputsEnabled(false);

    Thread caller = new Thread(() -> ui.setInputEnabled(false), "ui-port-caller-input");
    caller.start();
    caller.join();
    flushEdt();

    assertEquals(List.of("chat.setInputEnabled", "chatDockManager.setPinnedInputsEnabled"), steps);
    assertEquals(Boolean.TRUE, onEdtByStep.get("chat.setInputEnabled"));
    assertEquals(Boolean.TRUE, onEdtByStep.get("chatDockManager.setPinnedInputsEnabled"));
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

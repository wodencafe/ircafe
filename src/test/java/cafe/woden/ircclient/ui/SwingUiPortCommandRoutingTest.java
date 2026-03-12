package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.commands.BackendNamedCommandNames;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.shell.StatusBar;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SwingUiPortCommandRoutingTest {

  @Test
  void backendNamedCommandRequestsMergeServerTreeAndAppRequests() {
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    PublishProcessor<String> setupRequests = PublishProcessor.create();
    PublishProcessor<String> networkManagerRequests = PublishProcessor.create();
    when(serverTree.quasselSetupRequests()).thenReturn(setupRequests);
    when(serverTree.quasselNetworkManagerRequests()).thenReturn(networkManagerRequests);

    SwingUiPort ui =
        new SwingUiPort(
            serverTree,
            mock(ChatDockable.class),
            mock(ChatTranscriptStore.class),
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

    var subscriber = ui.backendNamedCommandRequests().test();
    setupRequests.onNext(" quassel ");
    networkManagerRequests.onNext(" core ");
    ui.openQuasselNetworkManager(" app ");

    subscriber.assertValues(
        new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_SETUP, "quassel"),
        new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER, "core"),
        new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER, "app"));
  }

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

    doAnswer(
            inv -> {
              record("chat.onTargetClosed", steps, onEdtByStep);
              return null;
            })
        .when(chat)
        .onTargetClosed(target);

    Thread caller = new Thread(() -> ui.closeTarget(target), "ui-port-caller-close");
    caller.start();
    caller.join();
    flushEdt();

    assertEquals(
        List.of(
            "serverTree.removeTarget",
            "chat.clearTopic",
            "transcripts.closeTarget",
            "chat.onTargetClosed"),
        steps);
    assertEquals(Boolean.TRUE, onEdtByStep.get("serverTree.removeTarget"));
    assertEquals(Boolean.TRUE, onEdtByStep.get("chat.clearTopic"));
    assertEquals(Boolean.TRUE, onEdtByStep.get("transcripts.closeTarget"));
    assertEquals(Boolean.TRUE, onEdtByStep.get("chat.onTargetClosed"));
  }

  @Test
  void setInputEnabledDelegatesThenRefreshesMainChatEligibilityOnlyOnEdt() throws Exception {
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
              record("chat.refreshDisplayedTargetInputEnabled", steps, onEdtByStep);
              return null;
            })
        .when(chat)
        .refreshDisplayedTargetInputEnabled();

    Thread caller = new Thread(() -> ui.setInputEnabled(false), "ui-port-caller-input");
    caller.start();
    caller.join();
    flushEdt();

    assertEquals(List.of("chat.setInputEnabled", "chat.refreshDisplayedTargetInputEnabled"), steps);
    assertEquals(Boolean.TRUE, onEdtByStep.get("chat.setInputEnabled"));
    assertEquals(Boolean.TRUE, onEdtByStep.get("chat.refreshDisplayedTargetInputEnabled"));
    verify(chatDockManager, never()).refreshPinnedInputEnabled(any());
  }

  @Test
  void setChannelDisconnectedRefreshesMainAndPinnedInputStateOnEdt() throws Exception {
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    ChatDockable chat = mock(ChatDockable.class);
    ChatDockManager chatDockManager = mock(ChatDockManager.class);
    TargetRef target = new TargetRef("libera", "#ircafe");

    SwingUiPort ui =
        new SwingUiPort(
            serverTree,
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
              record("serverTree.setChannelDisconnected", steps, onEdtByStep);
              return null;
            })
        .when(serverTree)
        .setChannelDisconnected(target, true);
    doAnswer(
            inv -> {
              record("chat.refreshDisplayedTargetInputEnabled", steps, onEdtByStep);
              return null;
            })
        .when(chat)
        .refreshDisplayedTargetInputEnabled();
    doAnswer(
            inv -> {
              record("chatDockManager.refreshPinnedInputEnabled", steps, onEdtByStep);
              return null;
            })
        .when(chatDockManager)
        .refreshPinnedInputEnabled(target);

    Thread caller =
        new Thread(() -> ui.setChannelDisconnected(target, true), "ui-port-caller-detached");
    caller.start();
    caller.join();
    flushEdt();

    assertEquals(
        List.of(
            "serverTree.setChannelDisconnected",
            "chat.refreshDisplayedTargetInputEnabled",
            "chatDockManager.refreshPinnedInputEnabled"),
        steps);
    assertEquals(Boolean.TRUE, onEdtByStep.get("serverTree.setChannelDisconnected"));
    assertEquals(Boolean.TRUE, onEdtByStep.get("chat.refreshDisplayedTargetInputEnabled"));
    assertEquals(Boolean.TRUE, onEdtByStep.get("chatDockManager.refreshPinnedInputEnabled"));
  }

  @Test
  void setServerConnectionStateRefreshesMainAndPinnedInputStateOnEdt() throws Exception {
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    ChatDockable chat = mock(ChatDockable.class);
    ChatDockManager chatDockManager = mock(ChatDockManager.class);

    SwingUiPort ui =
        new SwingUiPort(
            serverTree,
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
              record("serverTree.setServerConnectionState", steps, onEdtByStep);
              return null;
            })
        .when(serverTree)
        .setServerConnectionState("libera", ConnectionState.DISCONNECTED);
    doAnswer(
            inv -> {
              record("chat.refreshDisplayedTargetInputEnabled", steps, onEdtByStep);
              return null;
            })
        .when(chat)
        .refreshDisplayedTargetInputEnabled();
    doAnswer(
            inv -> {
              record("chatDockManager.refreshPinnedInputEnabledForServer", steps, onEdtByStep);
              return null;
            })
        .when(chatDockManager)
        .refreshPinnedInputEnabledForServer("libera");

    Thread caller =
        new Thread(
            () -> ui.setServerConnectionState("libera", ConnectionState.DISCONNECTED),
            "ui-port-caller-server-state");
    caller.start();
    caller.join();
    flushEdt();

    assertEquals(
        List.of(
            "serverTree.setServerConnectionState",
            "chat.refreshDisplayedTargetInputEnabled",
            "chatDockManager.refreshPinnedInputEnabledForServer"),
        steps);
    assertEquals(Boolean.TRUE, onEdtByStep.get("serverTree.setServerConnectionState"));
    assertEquals(Boolean.TRUE, onEdtByStep.get("chat.refreshDisplayedTargetInputEnabled"));
    assertEquals(
        Boolean.TRUE, onEdtByStep.get("chatDockManager.refreshPinnedInputEnabledForServer"));
  }

  @Test
  void backendNamedQuasselSetupRevealsStatusTarget() throws Exception {
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    PublishProcessor<String> setupRequests = PublishProcessor.create();
    when(serverTree.quasselSetupRequests()).thenReturn(setupRequests);
    when(serverTree.quasselNetworkManagerRequests()).thenReturn(PublishProcessor.create());

    SwingUiPort ui =
        new SwingUiPort(
            serverTree,
            mock(ChatDockable.class),
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

    var subscriber = ui.backendNamedCommandRequests().test();
    setupRequests.onNext(" testlocal ");
    flushEdt();

    TargetRef status = new TargetRef("testlocal", "status");
    verify(transcripts).ensureTargetExists(status);
    verify(serverTree).ensureNode(status);
    verify(serverTree).selectTarget(status);
    subscriber.assertValueCount(1);
    subscriber.cancel();
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

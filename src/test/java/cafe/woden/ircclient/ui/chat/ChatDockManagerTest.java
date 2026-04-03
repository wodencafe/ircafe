package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.commands.BackendNamedCommandCatalog;
import cafe.woden.ircclient.app.commands.SlashCommandPresentationCatalog;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import cafe.woden.ircclient.irc.port.IrcTypingPort;
import cafe.woden.ircclient.logging.NoOpChatRedactionAuditService;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.backend.BackendUiProfileProvider;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.coordinator.MessageActionCapabilityPolicy;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.settings.SpellcheckSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.github.andrewauclair.moderndocking.Dockable;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ChatDockManagerTest {

  @Test
  void dynamicDockableForPersistentIdSyncsTopicAndShownStateForExistingPinnedDock()
      throws Exception {
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    ChatDockable mainChat = mock(ChatDockable.class);
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    TargetActivationBus activationBus = mock(TargetActivationBus.class);
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    SpellcheckSettingsBus spellcheckSettingsBus = mock(SpellcheckSettingsBus.class);
    OutboundLineBus outboundBus = mock(OutboundLineBus.class);
    IrcTypingPort typingPort = mock(IrcTypingPort.class);
    IrcReadMarkerPort readMarkerPort = mock(IrcReadMarkerPort.class);
    BackendUiProfileProvider backendUiProfileProvider = mock(BackendUiProfileProvider.class);
    MessageActionCapabilityPolicy messageActionCapabilityPolicy =
        mock(MessageActionCapabilityPolicy.class);
    ActiveInputRouter activeInputRouter = mock(ActiveInputRouter.class);
    ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
    CommandHistoryStore commandHistoryStore = mock(CommandHistoryStore.class);
    SlashCommandPresentationCatalog slashCommandPresentationCatalog =
        new SlashCommandPresentationCatalog(List.of(), BackendNamedCommandCatalog.empty());
    ChatDockManager manager =
        new ChatDockManager(
            serverTree,
            mainChat,
            transcripts,
            activationBus,
            settingsBus,
            spellcheckSettingsBus,
            outboundBus,
            typingPort,
            readMarkerPort,
            backendUiProfileProvider,
            messageActionCapabilityPolicy,
            new NoOpChatRedactionAuditService(),
            activeInputRouter,
            slashCommandPresentationCatalog,
            chatHistoryService,
            commandHistoryStore);

    TargetRef target = new TargetRef("libera", "#ircafe");
    PinnedChatDockable pinnedDock = mock(PinnedChatDockable.class);
    openPinned(manager).put(target, pinnedDock);
    when(serverTree.isServerConnected("libera")).thenReturn(true);
    when(serverTree.isChannelDisconnected(target)).thenReturn(false);
    AtomicBoolean topicLookupOnEdt = new AtomicBoolean(false);
    when(mainChat.topicPanelHeightPxFor(target)).thenReturn(144);
    when(mainChat.topicFor(target))
        .thenAnswer(
            ignored -> {
              topicLookupOnEdt.set(SwingUtilities.isEventDispatchThread());
              return "Topic restored";
            });

    Dockable restored = manager.dynamicDockableForPersistentId(persistentId(target));

    assertSame(pinnedDock, restored);
    verify(pinnedDock).setInputEnabled(true);
    verify(mainChat).topicPanelHeightPxFor(target);
    verify(pinnedDock).setTopicPanelHeightPx(144);
    verify(mainChat).topicFor(target);
    verify(pinnedDock).setTopic("Topic restored");
    verify(chatHistoryService).onTargetSelected(target);
    verify(pinnedDock).onShown();
    assertTrue(topicLookupOnEdt.get());
  }

  @Test
  void refreshPinnedInputEnabledDisablesDetachedChannelInputs() throws Exception {
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    TargetRef target = new TargetRef("libera", "#ircafe");
    PinnedChatDockable pinnedDock = mock(PinnedChatDockable.class);
    when(serverTree.isServerConnected("libera")).thenReturn(true);
    when(serverTree.isChannelDisconnected(target)).thenReturn(true);

    ChatDockManager manager =
        new ChatDockManager(
            serverTree,
            mock(ChatDockable.class),
            mock(ChatTranscriptStore.class),
            mock(TargetActivationBus.class),
            mock(UiSettingsBus.class),
            mock(SpellcheckSettingsBus.class),
            mock(OutboundLineBus.class),
            mock(IrcTypingPort.class),
            mock(IrcReadMarkerPort.class),
            mock(BackendUiProfileProvider.class),
            mock(MessageActionCapabilityPolicy.class),
            new NoOpChatRedactionAuditService(),
            mock(ActiveInputRouter.class),
            new SlashCommandPresentationCatalog(List.of(), BackendNamedCommandCatalog.empty()),
            mock(ChatHistoryService.class),
            mock(CommandHistoryStore.class));
    openPinned(manager).put(target, pinnedDock);

    manager.refreshPinnedInputEnabled(target);

    verify(pinnedDock).setInputEnabled(false);
  }

  @Test
  void refreshPinnedInputEnabledForServerDisablesDisconnectedServerInputs() throws Exception {
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    TargetRef target = new TargetRef("libera", "#ircafe");
    PinnedChatDockable pinnedDock = mock(PinnedChatDockable.class);
    when(serverTree.isServerConnected("libera")).thenReturn(false);

    ChatDockManager manager =
        new ChatDockManager(
            serverTree,
            mock(ChatDockable.class),
            mock(ChatTranscriptStore.class),
            mock(TargetActivationBus.class),
            mock(UiSettingsBus.class),
            mock(SpellcheckSettingsBus.class),
            mock(OutboundLineBus.class),
            mock(IrcTypingPort.class),
            mock(IrcReadMarkerPort.class),
            mock(BackendUiProfileProvider.class),
            mock(MessageActionCapabilityPolicy.class),
            new NoOpChatRedactionAuditService(),
            mock(ActiveInputRouter.class),
            new SlashCommandPresentationCatalog(List.of(), BackendNamedCommandCatalog.empty()),
            mock(ChatHistoryService.class),
            mock(CommandHistoryStore.class));
    openPinned(manager).put(target, pinnedDock);

    manager.refreshPinnedInputEnabledForServer("libera");

    verify(pinnedDock).setInputEnabled(false);
  }

  @Test
  void dynamicDockableForPersistentIdReturnsNullForUnknownId() {
    ChatDockManager manager =
        new ChatDockManager(
            mock(ServerTreeDockable.class),
            mock(ChatDockable.class),
            mock(ChatTranscriptStore.class),
            mock(TargetActivationBus.class),
            mock(UiSettingsBus.class),
            mock(SpellcheckSettingsBus.class),
            mock(OutboundLineBus.class),
            mock(IrcTypingPort.class),
            mock(IrcReadMarkerPort.class),
            mock(BackendUiProfileProvider.class),
            mock(MessageActionCapabilityPolicy.class),
            new NoOpChatRedactionAuditService(),
            mock(ActiveInputRouter.class),
            new SlashCommandPresentationCatalog(List.of(), BackendNamedCommandCatalog.empty()),
            mock(ChatHistoryService.class),
            mock(CommandHistoryStore.class));

    assertNull(manager.dynamicDockableForPersistentId("chat-pinned:invalid"));
    assertNull(manager.dynamicDockableForPersistentId("other:abc"));
  }

  private static String persistentId(TargetRef target) {
    return "chat-pinned:" + b64(target.serverId()) + ":" + b64(target.key());
  }

  private static String b64(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private static Map<TargetRef, PinnedChatDockable> openPinned(ChatDockManager manager)
      throws Exception {
    Field field = ChatDockManager.class.getDeclaredField("openPinned");
    field.setAccessible(true);
    return (Map<TargetRef, PinnedChatDockable>) field.get(manager);
  }
}

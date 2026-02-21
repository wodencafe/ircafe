package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.ServerTreeDockable;
import cafe.woden.ircclient.ui.TargetActivationBus;
import cafe.woden.ircclient.ui.SwingEdt;
import cafe.woden.ircclient.ui.OutboundLineBus;
import cafe.woden.ircclient.ui.ActiveInputRouter;
import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.DockingRegion;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

@Component
@Lazy
public class ChatDockManager {

  private static final Logger log = LoggerFactory.getLogger(ChatDockManager.class);

  private final ServerTreeDockable serverTree;
  private final ChatDockable mainChat;
  private final ChatTranscriptStore transcripts;
  private final TargetActivationBus activationBus;
  private final UiSettingsBus settingsBus;
  private final OutboundLineBus outboundBus;
  private final IrcClientService irc;
  private final ActiveInputRouter activeInputRouter;
  private final CommandHistoryStore commandHistoryStore;
  private final ChatHistoryService chatHistoryService;

  /**
   * Registered pinned dockables.
   *
   * <p>ModernDocking does not support unregistering dockables, and it throws if you try to re-register a
   * different dockable instance with the same persistent ID. Closing a dock via the UI typically undocks
   * it but keeps it registered, so we keep the instance around and re-dock/re-display it on demand.
   */
  private final Map<TargetRef, PinnedChatDockable> openPinned = new ConcurrentHashMap<>();
  /**
   * Draft text for pinned dock inputs, persisted in-memory even after the dock is closed.
   * Keyed by TargetRef so reopening the same pinned target restores the draft.
   */
  private final Map<TargetRef, String> pinnedDrafts = new ConcurrentHashMap<>();
  private final CompositeDisposable disposables = new CompositeDisposable();

  private volatile boolean pinnedInputsEnabled = true;

  public ChatDockManager(ServerTreeDockable serverTree,
                         ChatDockable mainChat,
                         ChatTranscriptStore transcripts,
                         TargetActivationBus activationBus,
                         UiSettingsBus settingsBus,
                         OutboundLineBus outboundBus,
                         IrcClientService irc,
                         ActiveInputRouter activeInputRouter,
                         ChatHistoryService chatHistoryService,
                         CommandHistoryStore commandHistoryStore) {
    this.serverTree = serverTree;
    this.mainChat = mainChat;
    this.transcripts = transcripts;
    this.activationBus = activationBus;
    this.settingsBus = settingsBus;
    this.outboundBus = outboundBus;
    this.irc = irc;
    this.activeInputRouter = activeInputRouter;
    this.chatHistoryService = chatHistoryService;
    this.commandHistoryStore = commandHistoryStore;
  }

  @PostConstruct
  void wire() {
    disposables.add(
        serverTree.openPinnedChatRequests()
            .observeOn(SwingEdt.scheduler())
            .subscribe(this::openPinned,
                err -> log.error("[ircafe] pinned chat stream error", err))
    );
    disposables.add(
        mainChat.topicUpdates()
            .observeOn(SwingEdt.scheduler())
            .subscribe(this::onTopicUpdated,
                err -> log.error("[ircafe] topic update stream error", err))
    );
  }

  @PreDestroy
  void shutdown() {
    disposables.dispose();
    // Best-effort cleanup of dynamically created dockables.
    for (PinnedChatDockable d : openPinned.values()) {
      try {
        d.close();
      } catch (Exception ignored) {
      }
    }
    openPinned.clear();
  }

  /**
   * Enable/disable input bars for all currently-open pinned chat docks.
   * Newly opened pinned docks will inherit the latest value.
   */
  public void setPinnedInputsEnabled(boolean enabled) {
    this.pinnedInputsEnabled = enabled;
    for (PinnedChatDockable d : openPinned.values()) {
      try {
        d.setInputEnabled(enabled);
      } catch (Exception ignored) {
      }
    }
  }

  public void showTypingIndicator(TargetRef target, String nick, String state) {
    if (target == null || nick == null || nick.isBlank()) return;
    PinnedChatDockable dock = openPinned.get(target);
    if (dock == null) return;
    try {
      dock.showTypingIndicator(nick, state);
    } catch (Exception ignored) {
    }
  }

  public void normalizeIrcv3CapabilityUiState(String serverId, String capability) {
    String sid = java.util.Objects.toString(serverId, "").trim();
    String cap = java.util.Objects.toString(capability, "").trim().toLowerCase(java.util.Locale.ROOT);
    if (sid.isEmpty() || cap.isEmpty()) return;

    if ("typing".equals(cap) || "message-tags".equals(cap)) {
      clearTypingIndicatorsForServer(sid);
      return;
    }
    if (!"draft/reply".equals(cap) && !"draft/react".equals(cap)) return;

    boolean replySupported = isDraftReplySupportedForServer(sid);
    boolean reactSupported = isDraftReactSupportedForServer(sid);
    normalizePinnedDraftsForServer(sid, replySupported, reactSupported);
  }

  private void clearTypingIndicatorsForServer(String serverId) {
    for (Map.Entry<TargetRef, PinnedChatDockable> e : openPinned.entrySet()) {
      TargetRef target = e.getKey();
      PinnedChatDockable dock = e.getValue();
      if (target == null || dock == null) continue;
      if (!java.util.Objects.equals(target.serverId(), serverId)) continue;
      try {
        dock.clearTypingIndicator();
      } catch (Exception ignored) {
      }
      try {
        dock.refreshTypingSignalAvailability();
      } catch (Exception ignored) {
      }
    }
  }

  private void normalizePinnedDraftsForServer(String serverId, boolean replySupported, boolean reactSupported) {
    // Persisted drafts for currently hidden/closed pinned docks.
    java.util.ArrayList<TargetRef> persistedTargets = new java.util.ArrayList<>(pinnedDrafts.keySet());
    for (TargetRef target : persistedTargets) {
      if (target == null || !java.util.Objects.equals(target.serverId(), serverId)) continue;
      String before = pinnedDrafts.getOrDefault(target, "");
      String after = cafe.woden.ircclient.ui.MessageInputPanel.normalizeIrcv3DraftForCapabilities(
          before,
          replySupported,
          reactSupported);
      if (!java.util.Objects.equals(before, after)) {
        pinnedDrafts.put(target, after);
      }
    }

    // Open pinned docks currently visible.
    for (Map.Entry<TargetRef, PinnedChatDockable> e : new ArrayList<>(openPinned.entrySet())) {
      TargetRef target = e.getKey();
      PinnedChatDockable dock = e.getValue();
      if (target == null || dock == null) continue;
      if (!java.util.Objects.equals(target.serverId(), serverId)) continue;
      try {
        if (dock.normalizeIrcv3DraftForCapabilities(replySupported, reactSupported)) {
          pinnedDrafts.put(target, dock.getDraftText());
        }
      } catch (Exception ignored) {
      }
    }
  }

  private boolean isDraftReplySupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isDraftReplyAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isDraftReactSupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isDraftReactAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  public void openPinned(TargetRef target) {
    if (target == null) return;

    PinnedChatDockable dock = openPinned.get(target);
    if (dock == null) {
      transcripts.ensureTargetExists(target);

      // Restore any existing draft for this pinned target.
      String initialDraft = pinnedDrafts.getOrDefault(target, "");

      // Clicking inside a pinned dock should switch the *input*/status/users context,
      // but should NOT force the main Chat dock to change its displayed transcript.
      dock = new PinnedChatDockable(
          target,
          transcripts,
          settingsBus,
          chatHistoryService,
          commandHistoryStore,
          activationBus::activate,
          outboundBus,
          irc,
          activeInputRouter,
          (t, draft) -> {
            if (t == null) return;
            pinnedDrafts.put(t, draft == null ? "" : draft);
          },
          (t, draft) -> {
            // Only used for explicit cleanup (e.g., app shutdown). Closing a dock via the UI should
            // not destroy it because ModernDocking does not support re-registering the same ID.
            if (t == null) return;
            pinnedDrafts.put(t, draft == null ? "" : draft);
          }
      );
      dock.setDraftText(initialDraft);
      dock.setInputEnabled(pinnedInputsEnabled);
      openPinned.put(target, dock);

      Docking.registerDockable(dock);
    }

    // Keep in sync with current settings/draft even for already-registered dockables.
    dock.setInputEnabled(pinnedInputsEnabled);
    dock.setTopic(mainChat.topicFor(target));

    // Avoid clobbering undo/caret state unless we actually need to apply a different draft.
    String desiredDraft = pinnedDrafts.get(target);
    if (desiredDraft != null && !desiredDraft.equals(dock.getDraftText())) {
      dock.setDraftText(desiredDraft);
    }

    // If the user previously closed the dock via the UI, it is likely undocked but still registered.
    // Re-dock it (default: tab with main chat), then display it.
    try {
      if (!Docking.isDocked(dock)) {
        Docking.dock(dock, mainChat, DockingRegion.CENTER);
      }
      Docking.display(dock);
    } catch (Exception e) {
      // Last resort: try docking again (some failures are transient depending on window state).
      try {
        Docking.dock(dock, mainChat, DockingRegion.CENTER);
        Docking.display(dock);
      } catch (Exception ignored) {
      }
    }

    try {
      dock.onShown();
    } catch (Exception ignored) {
    }
  }

  private void onTopicUpdated(ChatDockable.TopicUpdate update) {
    if (update == null) return;
    TargetRef target = update.target();
    if (target == null) return;
    PinnedChatDockable dock = openPinned.get(target);
    if (dock == null) return;
    try {
      dock.setTopic(update.topic());
    } catch (Exception ignored) {
    }
  }
}

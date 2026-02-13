package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.ServerTreeDockable;
import cafe.woden.ircclient.ui.TargetActivationBus;
import cafe.woden.ircclient.ui.SwingEdt;
import cafe.woden.ircclient.ui.OutboundLineBus;
import cafe.woden.ircclient.ui.ActiveInputRouter;
import cafe.woden.ircclient.ui.CommandHistoryStore;
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
  private final ActiveInputRouter activeInputRouter;
  private final CommandHistoryStore commandHistoryStore;
  private final ChatHistoryService chatHistoryService;

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
                         ActiveInputRouter activeInputRouter,
                         ChatHistoryService chatHistoryService,
                         CommandHistoryStore commandHistoryStore) {
    this.serverTree = serverTree;
    this.mainChat = mainChat;
    this.transcripts = transcripts;
    this.activationBus = activationBus;
    this.settingsBus = settingsBus;
    this.outboundBus = outboundBus;
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

  public void openPinned(TargetRef target) {
    if (target == null) return;

    PinnedChatDockable dock = openPinned.get(target);
    if (dock == null) {
      transcripts.ensureTargetExists(target);

      // Restore any existing draft for this pinned target.
      String initialDraft = pinnedDrafts.getOrDefault(target, "");

      dock = new PinnedChatDockable(
          target,
          transcripts,
          settingsBus,
          chatHistoryService,
          commandHistoryStore,
          activationBus::activate,
          outboundBus,
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

    dock.setInputEnabled(pinnedInputsEnabled);

    String desiredDraft = pinnedDrafts.get(target);
    if (desiredDraft != null && !desiredDraft.equals(dock.getDraftText())) {
      dock.setDraftText(desiredDraft);
    }

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
  }
}

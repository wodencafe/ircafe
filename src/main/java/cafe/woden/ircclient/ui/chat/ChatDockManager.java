package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.ServerTreeDockable;
import cafe.woden.ircclient.ui.TargetActivationBus;
import cafe.woden.ircclient.ui.SwingEdt;
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
import javax.swing.SwingUtilities;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns dynamically-created pinned chat dockables.
 */
@Component
@Lazy
public class ChatDockManager {

  private static final Logger log = LoggerFactory.getLogger(ChatDockManager.class);

  private final ServerTreeDockable serverTree;
  private final ChatDockable mainChat;
  private final ChatTranscriptStore transcripts;
  private final TargetActivationBus activationBus;
  private final UiSettingsBus settingsBus;

  private final Map<TargetRef, PinnedChatDockable> openPinned = new ConcurrentHashMap<>();
  private final CompositeDisposable disposables = new CompositeDisposable();

  public ChatDockManager(ServerTreeDockable serverTree,
                         ChatDockable mainChat,
                         ChatTranscriptStore transcripts,
                         TargetActivationBus activationBus,
                         UiSettingsBus settingsBus) {
    this.serverTree = serverTree;
    this.mainChat = mainChat;
    this.transcripts = transcripts;
    this.activationBus = activationBus;
    this.settingsBus = settingsBus;
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
  }

  public void openPinned(TargetRef target) {
    if (target == null) return;

    PinnedChatDockable existing = openPinned.get(target);
    if (existing != null) {
      existing.requestFocusInWindow();
      return;
    }

    transcripts.ensureTargetExists(target);

    // Clicking inside a pinned dock should switch the *input*/status/users context,
    // but should NOT force the main Chat dock to change its displayed transcript.
    PinnedChatDockable dock = new PinnedChatDockable(target, transcripts, settingsBus, activationBus::activate);
    openPinned.put(target, dock);

    Docking.registerDockable(dock);
    // Default: tab it with the main chat. User can drag to split / float.
    Docking.dock(dock, mainChat, DockingRegion.CENTER);
  }
}

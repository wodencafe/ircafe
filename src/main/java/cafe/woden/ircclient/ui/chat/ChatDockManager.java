package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.ServerTreeDockable;
import cafe.woden.ircclient.ui.TargetActivationBus;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.DockingRegion;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.swing.SwingUtilities;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns dynamically-created pinned chat dockables.
 */
@Component
@Lazy
public class ChatDockManager {

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
            .subscribe(ref -> SwingUtilities.invokeLater(() -> openPinned(ref)))
    );
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

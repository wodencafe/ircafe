package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.PrivateMessageRequest;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.view.ChatViewPanel;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.text.DefaultStyledDocument;
import java.util.HashMap;
import java.util.Map;

/**
 * The main chat dockable.
 *
 * <p>Displays a single transcript at a time (selected via the server tree),
 * but keeps per-target scroll state so switching targets feels natural.
 *
 * <p>Transcripts themselves live in {@link ChatTranscriptStore} so other views
 * (e.g., pinned chat docks) can share them.
 */
@Component
@Lazy
public class ChatDockable extends ChatViewPanel implements Dockable {

  public static final String ID = "chat";

  private final ChatTranscriptStore transcripts;
  private final TargetActivationBus activationBus;

  private final FlowableProcessor<PrivateMessageRequest> openPrivate =
      PublishProcessor.<PrivateMessageRequest>create().toSerialized();

  private final Map<TargetRef, ViewState> stateByTarget = new HashMap<>();
  private final ViewState fallbackState = new ViewState();

  private TargetRef activeTarget;

  public ChatDockable(ChatTranscriptStore transcripts,
                     TargetActivationBus activationBus,
                     UiSettingsBus settingsBus) {
    super(settingsBus);
    this.transcripts = transcripts;
    this.activationBus = activationBus;

    // Show something harmless on startup; first selection will swap it.
    setDocument(new DefaultStyledDocument());

    // Keep an initial view state so the first auto-scroll behaves.
    this.activeTarget = new TargetRef("default", "status");
    stateByTarget.put(activeTarget, new ViewState());
  }

  public void setActiveTarget(TargetRef target) {
    if (target == null) return;

    // Persist state of the current target before swapping.
    if (activeTarget != null) {
      updateScrollStateFromBar();
    }

    activeTarget = target;
    transcripts.ensureTargetExists(target);
    setDocument(transcripts.document(target));
  }

  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    return openPrivate.onBackpressureBuffer();
  }

  @Override
  protected void onTranscriptClicked() {
    // Clicking the main transcript should make it the active target for input/status.
    // This does NOT change what the main chat is already displaying.
    activationBus.activate(activeTarget);
  }

  @Override
  protected boolean onNickClicked(String nick) {
    if (activeTarget == null || !activeTarget.isChannel()) return false;
    if (nick == null || nick.isBlank()) return false;

    openPrivate.onNext(new PrivateMessageRequest(activeTarget.serverId(), nick));
    return true;
  }

  @Override
  public String getPersistentID() {
    return ID;
  }

  @Override
  public String getTabText() {
    return "Chat";
  }

  @Override
  protected boolean isFollowTail() {
    return state().followTail;
  }

  @Override
  protected void setFollowTail(boolean followTail) {
    state().followTail = followTail;
  }

  @Override
  protected int getSavedScrollValue() {
    return state().scrollValue;
  }

  @Override
  protected void setSavedScrollValue(int value) {
    state().scrollValue = value;
  }

  private ViewState state() {
    if (activeTarget == null) return fallbackState;
    return stateByTarget.computeIfAbsent(activeTarget, t -> new ViewState());
  }

  private static class ViewState {
    boolean followTail = true;
    int scrollValue = 0;
  }
}

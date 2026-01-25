package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.chat.view.ChatViewPanel;
import io.github.andrewauclair.moderndocking.Dockable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * One dockable = one pinned target transcript.
 */
public class PinnedChatDockable extends ChatViewPanel implements Dockable {

  private final TargetRef target;
  private final String persistentId;

  private boolean followTail = true;
  private int savedScrollValue = 0;

  private final Consumer<TargetRef> activate;

  public PinnedChatDockable(TargetRef target,
                           ChatTranscriptStore transcripts,
                           Consumer<TargetRef> activate) {
    super();
    this.target = target;
    this.activate = activate;
    this.persistentId = "chat-pinned:" + b64(target.serverId()) + ":" + b64(target.target());

    setName(getTabText());
    setDocument(transcripts.document(target));
  }

  @Override
  protected void onTranscriptClicked() {
    if (activate != null) {
      activate.accept(target);
    }
  }

  @Override
  public String getPersistentID() {
    return persistentId;
  }

  @Override
  public String getTabText() {
    return target.target();
  }

  @Override
  protected boolean isFollowTail() {
    return followTail;
  }

  @Override
  protected void setFollowTail(boolean followTail) {
    this.followTail = followTail;
  }

  @Override
  protected int getSavedScrollValue() {
    return savedScrollValue;
  }

  @Override
  protected void setSavedScrollValue(int value) {
    this.savedScrollValue = value;
  }

  public TargetRef target() {
    return target;
  }

  private static String b64(String s) {
    if (s == null) s = "";
    return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }
}

package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.github.andrewauclair.moderndocking.Dockable;

/**
 * Legacy wrapper to keep the input bar available as a Dockable.
 *
 * <p><strong>Retired:</strong> IRCafe now embeds {@link MessageInputPanel} directly inside chat docks
 * (main chat and pinned chats). This wrapper remains only for anyone who wants to manually re-add an
 * input dock (or for transitional experiments). It is no longer wired as a Spring bean or registered
 * by default.</p>
 */
@Deprecated
public class MessageInputDockable extends MessageInputPanel implements Dockable {

  public static final String ID = "input";

  public MessageInputDockable(UiSettingsBus settingsBus) {
    super(settingsBus);
  }

  @Override
  public String getPersistentID() {
    return ID;
  }

  @Override
  public String getTabText() {
    return "Input";
  }
}

package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.github.andrewauclair.moderndocking.Dockable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Legacy wrapper to keep the input bar available as a Dockable.
 *
 * <p>The actual UI and behavior now lives in {@link MessageInputPanel},
 * which can be embedded directly inside chat docks. This wrapper exists so
 * we can migrate incrementally without breaking Spring wiring or docking
 * IDs.</p>
 */
@Component
@Lazy
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

package cafe.woden.ircclient.app;

import cafe.woden.ircclient.model.IrcEventNotificationRule;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned contract for user-facing desktop/tray notifications. */
@ApplicationLayer
public interface TrayNotificationsPort {

  void notifyHighlight(String serverId, String channel, String fromNick, String message);

  void notifyPrivateMessage(String serverId, String fromNick, String message);

  void notifyInvite(String serverId, String channel, String fromNick, String reason);

  void notifyConnectionState(String serverId, String state, String detail);

  void notifyCustom(
      String serverId,
      String target,
      String title,
      String body,
      boolean showToast,
      boolean showStatusBar,
      IrcEventNotificationRule.FocusScope focusScope,
      boolean playSound,
      String soundId,
      boolean soundUseCustom,
      String soundCustomPath);
}

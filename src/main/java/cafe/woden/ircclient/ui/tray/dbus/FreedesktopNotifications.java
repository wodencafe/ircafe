package cafe.woden.ircclient.ui.tray.dbus;

import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * Minimal org.freedesktop.Notifications interface.
 *
 * <p>We only declare what we need for capability probing in step G1.
 */
@DBusInterfaceName("org.freedesktop.Notifications")
public interface FreedesktopNotifications extends DBusInterface {

  /**
   * Signal emitted when an action (including the default body click) is invoked.
   *
   * <p>Signature: ActionInvoked(uint32 id, string action_key)
   */
  final class ActionInvoked extends DBusSignal {
    private final UInt32 id;
    private final String actionKey;

    public ActionInvoked(String path, UInt32 id, String actionKey) throws DBusException {
      super(path, id, actionKey);
      this.id = id;
      this.actionKey = actionKey;
    }

    public UInt32 id() {
      return id;
    }

    public String actionKey() {
      return actionKey;
    }
  }

  /**
   * Signal emitted when a notification is closed (expired, dismissed, etc.).
   *
   * <p>Signature: NotificationClosed(uint32 id, uint32 reason)
   */
  final class NotificationClosed extends DBusSignal {
    private final UInt32 id;
    private final UInt32 reason;

    public NotificationClosed(String path, UInt32 id, UInt32 reason) throws DBusException {
      super(path, id, reason);
      this.id = id;
      this.reason = reason;
    }

    public UInt32 id() {
      return id;
    }

    public UInt32 reason() {
      return reason;
    }
  }

  /** Returns a list of optional server capabilities (e.g. "actions"). */
  List<String> GetCapabilities();

  /**
   * Sends a desktop notification.
   *
   * <p>Signature per Desktop Notifications spec: Notify(app_name, replaces_id, app_icon, summary,
   * body, actions, hints, expire_timeout) -> id.
   */
  UInt32 Notify(
      String appName,
      UInt32 replacesId,
      String appIcon,
      String summary,
      String body,
      String[] actions,
      Map<String, Variant<?>> hints,
      int expireTimeout);
}

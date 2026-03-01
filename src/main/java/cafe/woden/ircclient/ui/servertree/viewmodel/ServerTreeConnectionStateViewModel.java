package cafe.woden.ircclient.ui.servertree.viewmodel;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.icons.SvgIcons.Palette;

/** UI presentation policy for server-node connection state controls and labels. */
public final class ServerTreeConnectionStateViewModel {

  private ServerTreeConnectionStateViewModel() {}

  public static boolean canConnect(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return st == ConnectionState.DISCONNECTED;
  }

  public static boolean canDisconnect(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return st == ConnectionState.CONNECTING
        || st == ConnectionState.CONNECTED
        || st == ConnectionState.RECONNECTING;
  }

  public static String stateLabel(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return switch (st) {
      case CONNECTED -> "Connected";
      case CONNECTING -> "Connecting";
      case RECONNECTING -> "Reconnecting";
      case DISCONNECTING -> "Disconnecting";
      case DISCONNECTED -> "Disconnected";
    };
  }

  public static String desiredIntentLabel(boolean desiredOnline) {
    return desiredOnline ? "Online" : "Offline";
  }

  public static String desiredBadge(ConnectionState state, boolean desiredOnline) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    boolean online = isOnlineState(st);
    if (desiredOnline && !online) {
      if (st == ConnectionState.DISCONNECTING) return " [connect queued]";
      return " [wanted online]";
    }
    if (!desiredOnline && online) {
      return " [disconnect queued]";
    }
    return "";
  }

  public static String intentQueueTip(ConnectionState state, boolean desiredOnline) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    boolean online = isOnlineState(st);
    if (desiredOnline && st == ConnectionState.DISCONNECTING) {
      return "Connect is queued until the current disconnect finishes.";
    }
    if (desiredOnline && st == ConnectionState.DISCONNECTED) {
      return "Wanted online; waiting for a successful connect attempt.";
    }
    if (!desiredOnline && online) {
      return "Disconnect is queued.";
    }
    return "";
  }

  public static String actionHint(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return canConnect(st)
        ? "Click the row action to connect."
        : canDisconnect(st)
            ? "Click the row action to disconnect."
            : "Connection state is changing.";
  }

  public static String serverNodeIconName(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return switch (st) {
      case CONNECTED -> "check";
      case CONNECTING, RECONNECTING, DISCONNECTING -> "refresh";
      case DISCONNECTED -> "terminal";
    };
  }

  public static Palette serverNodeIconPalette(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return switch (st) {
      case CONNECTED, CONNECTING, RECONNECTING -> Palette.TREE;
      case DISCONNECTED, DISCONNECTING -> Palette.TREE_DISABLED;
    };
  }

  public static String serverActionIconName(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return switch (st) {
      case DISCONNECTED -> "plus";
      case CONNECTED, RECONNECTING -> "exit";
      case CONNECTING, DISCONNECTING -> "refresh";
    };
  }

  private static boolean isOnlineState(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return st == ConnectionState.CONNECTED
        || st == ConnectionState.CONNECTING
        || st == ConnectionState.RECONNECTING;
  }
}

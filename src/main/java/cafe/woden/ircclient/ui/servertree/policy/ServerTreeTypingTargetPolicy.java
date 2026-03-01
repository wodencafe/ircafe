package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.app.api.TargetRef;

/** Encapsulates which targets are eligible for tree typing activity indicators. */
public final class ServerTreeTypingTargetPolicy {

  private ServerTreeTypingTargetPolicy() {}

  public static boolean supportsTypingActivity(TargetRef ref) {
    if (ref == null) return false;
    if (ref.isStatus() || ref.isUiOnly() || ref.isNotifications()) return false;
    if (ref.isChannelList() || ref.isDccTransfers()) return false;
    return ref.isChannel();
  }
}

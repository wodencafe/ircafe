package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.Objects;

/** Encapsulates fallback and startup target-selection policy for a server tree. */
public final class ServerTreeSelectionFallbackPolicy {

  public interface Context {
    boolean serverExists(String serverId);

    boolean statusVisible(String serverId);

    boolean notificationsVisible(String serverId);

    boolean logViewerVisible(String serverId);

    TargetRef statusRef(String serverId);

    TargetRef notificationsRef(String serverId);

    TargetRef logViewerRef(String serverId);

    TargetRef channelListRef(String serverId);

    TargetRef weechatFiltersRef(String serverId);

    TargetRef ignoresRef(String serverId);

    boolean hasLeaf(TargetRef ref);

    boolean isMonitorGroupAttached(String serverId);

    boolean isInterceptorsGroupAttached(String serverId);

    void selectTarget(TargetRef ref);

    boolean isDirectChildOfServerNode(TargetRef ref, String serverId);

    void selectServerNodePath(String serverId);
  }

  private final Context context;

  public ServerTreeSelectionFallbackPolicy(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  public void selectBestFallbackForServer(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty() || !context.serverExists(sid)) return;

    TargetRef statusRef = context.statusRef(sid);
    if (context.statusVisible(sid) && context.hasLeaf(statusRef)) {
      context.selectTarget(statusRef);
      return;
    }

    TargetRef notificationsRef = context.notificationsRef(sid);
    if (context.notificationsVisible(sid) && context.hasLeaf(notificationsRef)) {
      context.selectTarget(notificationsRef);
      return;
    }

    TargetRef logViewerRef = context.logViewerRef(sid);
    if (context.logViewerVisible(sid) && context.hasLeaf(logViewerRef)) {
      context.selectTarget(logViewerRef);
      return;
    }

    TargetRef channelListRef = context.channelListRef(sid);
    if (context.hasLeaf(channelListRef)) {
      context.selectTarget(channelListRef);
      return;
    }

    TargetRef filtersRef = context.weechatFiltersRef(sid);
    if (context.hasLeaf(filtersRef)) {
      context.selectTarget(filtersRef);
      return;
    }

    TargetRef ignoresRef = context.ignoresRef(sid);
    if (context.hasLeaf(ignoresRef)) {
      context.selectTarget(ignoresRef);
      return;
    }

    if (context.isMonitorGroupAttached(sid)) {
      context.selectTarget(TargetRef.monitorGroup(sid));
      return;
    }

    if (context.isInterceptorsGroupAttached(sid)) {
      context.selectTarget(TargetRef.interceptorsGroup(sid));
    }
  }

  public void selectStartupDefaultForServer(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty() || !context.serverExists(sid)) return;

    TargetRef channelListRef = context.channelListRef(sid);
    if (context.isDirectChildOfServerNode(channelListRef, sid)) {
      context.selectTarget(channelListRef);
      return;
    }

    TargetRef notificationsRef = context.notificationsRef(sid);
    if (context.isDirectChildOfServerNode(notificationsRef, sid)) {
      context.selectTarget(notificationsRef);
      return;
    }

    context.selectServerNodePath(sid);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}

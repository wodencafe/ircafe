package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import java.util.Objects;

/** Encapsulates target-type policy and leaf-label resolution for server tree nodes. */
public final class ServerTreeTargetNodePolicy {

  private final InterceptorStore interceptorStore;
  private final String notificationsLabel;
  private final String interceptorLabel;
  private final String logViewerLabel;
  private final String channelListLabel;
  private final String weechatFiltersLabel;
  private final String ignoresLabel;
  private final String dccTransfersLabel;

  public ServerTreeTargetNodePolicy(
      InterceptorStore interceptorStore,
      String notificationsLabel,
      String interceptorLabel,
      String logViewerLabel,
      String channelListLabel,
      String weechatFiltersLabel,
      String ignoresLabel,
      String dccTransfersLabel) {
    this.interceptorStore = interceptorStore;
    this.notificationsLabel = Objects.toString(notificationsLabel, "Notifications");
    this.interceptorLabel = Objects.toString(interceptorLabel, "Interceptor");
    this.logViewerLabel = Objects.toString(logViewerLabel, "Log Viewer");
    this.channelListLabel = Objects.toString(channelListLabel, "Channel List");
    this.weechatFiltersLabel = Objects.toString(weechatFiltersLabel, "Filters");
    this.ignoresLabel = Objects.toString(ignoresLabel, "Ignores");
    this.dccTransfersLabel = Objects.toString(dccTransfersLabel, "DCC Transfers");
  }

  public boolean isPrivateMessageTarget(TargetRef ref) {
    if (ref == null) return false;
    return !ref.isStatus() && !ref.isChannel() && !ref.isUiOnly();
  }

  public String leafLabel(TargetRef ref) {
    if (ref == null) return "";
    if (ref.isNotifications()) return notificationsLabel;
    if (ref.isInterceptor()) {
      String name =
          interceptorStore != null
              ? interceptorStore.interceptorName(ref.serverId(), ref.interceptorId())
              : "";
      String normalized = Objects.toString(name, "").trim();
      return normalized.isEmpty() ? interceptorLabel : normalized;
    }
    if (ref.isLogViewer()) return logViewerLabel;
    if (ref.isChannelList()) return channelListLabel;
    if (ref.isWeechatFilters()) return weechatFiltersLabel;
    if (ref.isIgnores()) return ignoresLabel;
    if (ref.isDccTransfers()) return dccTransfersLabel;
    return ref.target();
  }
}

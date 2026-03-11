package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.interceptors.InterceptorScope;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.TargetRef;
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
      String scopeServerId = InterceptorScope.scopedServerIdForTarget(ref);
      String name =
          interceptorStore != null
              ? interceptorStore.interceptorName(scopeServerId, ref.interceptorId())
              : "";
      String normalized = Objects.toString(name, "").trim();
      return normalized.isEmpty() ? interceptorLabel : normalized;
    }
    if (ref.isLogViewer()) return logViewerLabel;
    if (ref.isChannelList()) return channelListLabel;
    if (ref.isWeechatFilters()) return weechatFiltersLabel;
    if (ref.isIgnores()) return ignoresLabel;
    if (ref.isDccTransfers()) return dccTransfersLabel;
    return simplifyMatrixAddress(ref.baseTarget());
  }

  private static String simplifyMatrixAddress(String rawTarget) {
    String target = Objects.toString(rawTarget, "").trim();
    if (target.isEmpty()) return "";
    if (target.startsWith("#")) {
      String local = matrixLocalpart(target);
      if (local.isEmpty()) return target;
      String bridged = extractBridgedIrcChannel(local);
      return bridged.isEmpty() ? local : bridged;
    }
    if (target.startsWith("@") || target.startsWith("!")) {
      String local = matrixLocalpart(target);
      return local.isEmpty() ? target : local;
    }
    return target;
  }

  private static String matrixLocalpart(String value) {
    String token = Objects.toString(value, "").trim();
    if (token.isEmpty()) return "";
    int colon = token.indexOf(':');
    if (colon <= 1 || colon >= token.length() - 1) return token;
    return token.substring(0, colon);
  }

  private static String extractBridgedIrcChannel(String localAlias) {
    String alias = Objects.toString(localAlias, "").trim();
    if (alias.length() < 6 || !alias.startsWith("#")) return "";
    int marker = alias.indexOf("_#");
    if (marker < 2 || marker >= alias.length() - 1) return "";
    if (!looksLikeBridgePrefix(alias.substring(1, marker))) return "";
    String channel = alias.substring(marker + 1);
    return channel.startsWith("#") ? channel : "";
  }

  private static boolean looksLikeBridgePrefix(String prefix) {
    String value = Objects.toString(prefix, "").trim();
    if (value.isEmpty()) return false;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      boolean allowed =
          (ch >= 'a' && ch <= 'z')
              || (ch >= 'A' && ch <= 'Z')
              || (ch >= '0' && ch <= '9')
              || ch == '_'
              || ch == '-'
              || ch == '.';
      if (!allowed) return false;
    }
    return true;
  }
}

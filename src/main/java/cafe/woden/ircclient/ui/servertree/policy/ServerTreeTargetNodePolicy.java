package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.interceptors.InterceptorScope;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Encapsulates target-type policy and leaf-label resolution for server tree nodes. */
@Component
@RequiredArgsConstructor
public final class ServerTreeTargetNodePolicy {

  private static final String NOTIFICATIONS_LABEL = "Notifications";
  private static final String INTERCEPTOR_LABEL = "Interceptor";
  private static final String LOG_VIEWER_LABEL = "Log Viewer";
  private static final String CHANNEL_LIST_LABEL = "Channel List";
  private static final String WEECHAT_FILTERS_LABEL = "Filters";
  private static final String IGNORES_LABEL = "Ignores";
  private static final String DCC_TRANSFERS_LABEL = "DCC Transfers";

  private final InterceptorStore interceptorStore;

  public boolean isPrivateMessageTarget(TargetRef ref) {
    if (ref == null) return false;
    return !ref.isStatus() && !ref.isChannel() && !ref.isUiOnly();
  }

  public String leafLabel(TargetRef ref) {
    if (ref == null) return "";
    if (ref.isNotifications()) return NOTIFICATIONS_LABEL;
    if (ref.isInterceptor()) {
      String scopeServerId = InterceptorScope.scopedServerIdForTarget(ref);
      String name =
          interceptorStore != null
              ? interceptorStore.interceptorName(scopeServerId, ref.interceptorId())
              : "";
      String normalized = Objects.toString(name, "").trim();
      return normalized.isEmpty() ? INTERCEPTOR_LABEL : normalized;
    }
    if (ref.isLogViewer()) return LOG_VIEWER_LABEL;
    if (ref.isChannelList()) return CHANNEL_LIST_LABEL;
    if (ref.isWeechatFilters()) return WEECHAT_FILTERS_LABEL;
    if (ref.isIgnores()) return IGNORES_LABEL;
    if (ref.isDccTransfers()) return DCC_TRANSFERS_LABEL;
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

package cafe.woden.ircclient.interceptors;

import cafe.woden.ircclient.model.TargetRef;
import java.util.Locale;
import java.util.Objects;

/** Helpers for mapping between target refs and interceptor store scope keys. */
public final class InterceptorScope {

  private InterceptorScope() {}

  public static String normalizeScopeServerId(String serverIdOrScopeKey) {
    String raw = normalize(serverIdOrScopeKey);
    if (raw.isEmpty()) return "";
    TargetRef.QualifiedTarget parsed = TargetRef.parseQualifiedTarget(raw);
    String baseServerId = normalize(parsed.baseTarget());
    if (baseServerId.isEmpty()) return "";
    return scopedServerId(baseServerId, parsed.networkToken());
  }

  public static String scopedServerId(String serverId, String networkToken) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) return "";
    String token = normalize(networkToken).toLowerCase(Locale.ROOT);
    return token.isEmpty() ? sid : TargetRef.withNetworkQualifier(sid, token);
  }

  public static String scopedServerIdForTarget(TargetRef target) {
    if (target == null) return "";
    return scopedServerId(target.serverId(), target.networkQualifierToken());
  }

  public static String scopedServerIdForChannel(String serverId, String channel) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) return "";
    String token = TargetRef.parseQualifiedTarget(channel).networkToken();
    return scopedServerId(sid, token);
  }

  public static String baseServerId(String serverIdOrScopeKey) {
    String scope = normalizeScopeServerId(serverIdOrScopeKey);
    if (scope.isEmpty()) return "";
    return normalize(TargetRef.parseQualifiedTarget(scope).baseTarget());
  }

  public static String networkToken(String serverIdOrScopeKey) {
    String scope = normalizeScopeServerId(serverIdOrScopeKey);
    if (scope.isEmpty()) return "";
    return normalize(TargetRef.parseQualifiedTarget(scope).networkToken()).toLowerCase(Locale.ROOT);
  }

  public static TargetRef interceptorsGroupRef(String serverIdOrScopeKey) {
    String sid = baseServerId(serverIdOrScopeKey);
    if (sid.isEmpty()) return null;
    String token = networkToken(serverIdOrScopeKey);
    return token.isEmpty()
        ? TargetRef.interceptorsGroup(sid)
        : TargetRef.interceptorsGroup(sid, token);
  }

  public static TargetRef interceptorRef(String serverIdOrScopeKey, String interceptorId) {
    String sid = baseServerId(serverIdOrScopeKey);
    String iid = normalize(interceptorId);
    if (sid.isEmpty() || iid.isEmpty()) return null;
    String token = networkToken(serverIdOrScopeKey);
    return token.isEmpty()
        ? TargetRef.interceptor(sid, iid)
        : TargetRef.interceptor(sid, iid, token);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}

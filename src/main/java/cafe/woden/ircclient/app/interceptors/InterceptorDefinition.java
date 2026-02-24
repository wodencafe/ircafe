package cafe.woden.ircclient.app.interceptors;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable interceptor definition. */
public record InterceptorDefinition(
    String id,
    String name,
    boolean enabled,
    String scopeServerId,
    InterceptorRuleMode channelIncludeMode,
    String channelIncludes,
    InterceptorRuleMode channelExcludeMode,
    String channelExcludes,
    boolean actionSoundEnabled,
    boolean actionStatusBarEnabled,
    boolean actionToastEnabled,
    String actionSoundId,
    boolean actionSoundUseCustom,
    String actionSoundCustomPath,
    boolean actionScriptEnabled,
    String actionScriptPath,
    String actionScriptArgs,
    String actionScriptWorkingDirectory,
    List<InterceptorRule> rules) {
  public InterceptorDefinition {
    id = norm(id);
    name = norm(name);
    scopeServerId = norm(scopeServerId);
    channelIncludes = norm(channelIncludes);
    channelExcludes = norm(channelExcludes);
    actionSoundId = norm(actionSoundId);
    actionSoundCustomPath = norm(actionSoundCustomPath);
    actionScriptPath = norm(actionScriptPath);
    actionScriptArgs = norm(actionScriptArgs);
    actionScriptWorkingDirectory = norm(actionScriptWorkingDirectory);

    if (id.isEmpty()) throw new IllegalArgumentException("id must not be blank");
    if (name.isEmpty()) name = "Interceptor";

    if (channelIncludeMode == null) channelIncludeMode = InterceptorRuleMode.GLOB;
    if (channelExcludeMode == null) channelExcludeMode = InterceptorRuleMode.GLOB;

    if (actionSoundId.isEmpty()) actionSoundId = "NOTIF_1";
    if (actionSoundUseCustom && actionSoundCustomPath.isBlank()) actionSoundUseCustom = false;

    if (actionScriptEnabled && actionScriptPath.isBlank()) actionScriptEnabled = false;

    rules =
        rules == null ? List.of() : List.copyOf(rules.stream().filter(Objects::nonNull).toList());
  }

  /** Blank scope means this interceptor can match events from any server. */
  public boolean scopeAnyServer() {
    return scopeServerId == null || scopeServerId.isBlank();
  }

  public boolean scopeMatchesServer(String serverId) {
    String sid = norm(serverId);
    if (scopeAnyServer()) return !sid.isBlank();
    return scopeServerId.equalsIgnoreCase(sid);
  }

  public String displayScopeLabel(String ownerServerId) {
    if (scopeAnyServer()) return "Any server";
    String owner = norm(ownerServerId);
    if (!owner.isEmpty() && scopeServerId.equalsIgnoreCase(owner)) return "This server";
    return scopeServerId.toLowerCase(Locale.ROOT);
  }

  private static String norm(String s) {
    return Objects.toString(s, "").trim();
  }
}

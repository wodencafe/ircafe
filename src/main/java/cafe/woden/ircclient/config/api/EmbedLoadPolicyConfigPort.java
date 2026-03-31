package cafe.woden.ircclient.config.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for advanced embed and link loading policy state. */
@SecondaryPort
@ApplicationLayer
public interface EmbedLoadPolicyConfigPort {

  @ApplicationLayer
  record EmbedLoadPolicyScope(
      List<String> userWhitelist,
      List<String> userBlacklist,
      List<String> channelWhitelist,
      List<String> channelBlacklist,
      boolean requireVoiceOrOp,
      boolean requireLoggedIn,
      int minAccountAgeDays,
      List<String> linkWhitelist,
      List<String> linkBlacklist,
      List<String> domainWhitelist,
      List<String> domainBlacklist) {
    public EmbedLoadPolicyScope {
      userWhitelist = sanitizePolicyPatternList(userWhitelist);
      userBlacklist = sanitizePolicyPatternList(userBlacklist);
      channelWhitelist = sanitizePolicyPatternList(channelWhitelist);
      channelBlacklist = sanitizePolicyPatternList(channelBlacklist);
      linkWhitelist = sanitizePolicyPatternList(linkWhitelist);
      linkBlacklist = sanitizePolicyPatternList(linkBlacklist);
      domainWhitelist = sanitizePolicyPatternList(domainWhitelist);
      domainBlacklist = sanitizePolicyPatternList(domainBlacklist);
      if (minAccountAgeDays < 0) minAccountAgeDays = 0;
    }

    public static EmbedLoadPolicyScope defaults() {
      return new EmbedLoadPolicyScope(
          List.of(), List.of(), List.of(), List.of(), false, false, 0, List.of(), List.of(),
          List.of(), List.of());
    }

    public boolean isDefaultScope() {
      return this.equals(defaults());
    }
  }

  @ApplicationLayer
  record EmbedLoadPolicySnapshot(
      EmbedLoadPolicyScope global, Map<String, EmbedLoadPolicyScope> byServer) {
    public EmbedLoadPolicySnapshot {
      if (global == null) global = EmbedLoadPolicyScope.defaults();

      LinkedHashMap<String, EmbedLoadPolicyScope> normalized = new LinkedHashMap<>();
      if (byServer != null) {
        for (Map.Entry<String, EmbedLoadPolicyScope> entry : byServer.entrySet()) {
          String serverId = Objects.toString(entry.getKey(), "").trim();
          if (serverId.isEmpty()) continue;
          EmbedLoadPolicyScope scope =
              entry.getValue() == null ? EmbedLoadPolicyScope.defaults() : entry.getValue();
          if (scope.isDefaultScope()) continue;
          normalized.put(serverId, scope);
        }
      }
      byServer = normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    public static EmbedLoadPolicySnapshot defaults() {
      return new EmbedLoadPolicySnapshot(EmbedLoadPolicyScope.defaults(), Map.of());
    }

    public boolean isDefaultPolicy() {
      return this.equals(defaults());
    }

    public EmbedLoadPolicyScope scopeForServer(String serverId) {
      String sid = Objects.toString(serverId, "").trim();
      if (sid.isEmpty() || byServer == null || byServer.isEmpty()) return global;
      EmbedLoadPolicyScope exact = byServer.get(sid);
      if (exact != null) return exact;
      for (Map.Entry<String, EmbedLoadPolicyScope> entry : byServer.entrySet()) {
        if (sid.equalsIgnoreCase(Objects.toString(entry.getKey(), "").trim())) {
          return entry.getValue();
        }
      }
      return global;
    }
  }

  List<String> readServerIds();

  EmbedLoadPolicySnapshot readEmbedLoadPolicy();

  void rememberEmbedLoadPolicy(EmbedLoadPolicySnapshot snapshot);

  private static List<String> sanitizePolicyPatternList(List<String> patterns) {
    if (patterns == null || patterns.isEmpty()) return List.of();
    LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
    for (String pattern : patterns) {
      String value = Objects.toString(pattern, "").trim();
      if (value.isEmpty()) continue;
      String key = value.toLowerCase(Locale.ROOT);
      normalized.putIfAbsent(key, value);
    }
    if (normalized.isEmpty()) return List.of();
    return List.copyOf(normalized.values());
  }
}

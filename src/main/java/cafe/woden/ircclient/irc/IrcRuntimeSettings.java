package cafe.woden.ircclient.irc;

/** Snapshot of runtime settings needed by IRC-side services. */
public record IrcRuntimeSettings(
    boolean userhostDiscoveryEnabled,
    int userhostMinIntervalSeconds,
    int userhostMaxCommandsPerMinute,
    int userhostNickCooldownMinutes,
    int userhostMaxNicksPerCommand,
    boolean userInfoEnrichmentEnabled,
    int userInfoEnrichmentUserhostMinIntervalSeconds,
    int userInfoEnrichmentUserhostMaxCommandsPerMinute,
    int userInfoEnrichmentUserhostNickCooldownMinutes,
    int userInfoEnrichmentUserhostMaxNicksPerCommand,
    boolean userInfoEnrichmentWhoisFallbackEnabled,
    int userInfoEnrichmentWhoisMinIntervalSeconds,
    int userInfoEnrichmentWhoisNickCooldownMinutes,
    boolean userInfoEnrichmentPeriodicRefreshEnabled,
    int userInfoEnrichmentPeriodicRefreshIntervalSeconds,
    int userInfoEnrichmentPeriodicRefreshNicksPerTick) {

  public static IrcRuntimeSettings defaults() {
    return new IrcRuntimeSettings(
        true, 7, 6, 30, 5, false, 15, 3, 60, 5, false, 45, 120, false, 300, 2);
  }
}

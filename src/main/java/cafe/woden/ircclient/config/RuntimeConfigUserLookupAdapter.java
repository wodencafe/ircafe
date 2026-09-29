package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.UserLookupRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for user-lookup runtime settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigUserLookupAdapter implements UserLookupRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigUserLookupAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberUserhostDiscoveryEnabled(boolean enabled) {
    runtimeConfig.rememberUserhostDiscoveryEnabled(enabled);
  }

  @Override
  public void rememberUserhostMinIntervalSeconds(int seconds) {
    runtimeConfig.rememberUserhostMinIntervalSeconds(seconds);
  }

  @Override
  public void rememberUserhostMaxCommandsPerMinute(int maxPerMinute) {
    runtimeConfig.rememberUserhostMaxCommandsPerMinute(maxPerMinute);
  }

  @Override
  public void rememberUserhostNickCooldownMinutes(int minutes) {
    runtimeConfig.rememberUserhostNickCooldownMinutes(minutes);
  }

  @Override
  public void rememberUserhostMaxNicksPerCommand(int maxNicks) {
    runtimeConfig.rememberUserhostMaxNicksPerCommand(maxNicks);
  }

  @Override
  public void rememberUserInfoEnrichmentEnabled(boolean enabled) {
    runtimeConfig.rememberUserInfoEnrichmentEnabled(enabled);
  }

  @Override
  public void rememberUserInfoEnrichmentWhoisFallbackEnabled(boolean enabled) {
    runtimeConfig.rememberUserInfoEnrichmentWhoisFallbackEnabled(enabled);
  }

  @Override
  public void rememberUserInfoEnrichmentUserhostMinIntervalSeconds(int seconds) {
    runtimeConfig.rememberUserInfoEnrichmentUserhostMinIntervalSeconds(seconds);
  }

  @Override
  public void rememberUserInfoEnrichmentUserhostMaxCommandsPerMinute(int maxPerMinute) {
    runtimeConfig.rememberUserInfoEnrichmentUserhostMaxCommandsPerMinute(maxPerMinute);
  }

  @Override
  public void rememberUserInfoEnrichmentUserhostNickCooldownMinutes(int minutes) {
    runtimeConfig.rememberUserInfoEnrichmentUserhostNickCooldownMinutes(minutes);
  }

  @Override
  public void rememberUserInfoEnrichmentUserhostMaxNicksPerCommand(int maxNicks) {
    runtimeConfig.rememberUserInfoEnrichmentUserhostMaxNicksPerCommand(maxNicks);
  }

  @Override
  public void rememberUserInfoEnrichmentWhoisMinIntervalSeconds(int seconds) {
    runtimeConfig.rememberUserInfoEnrichmentWhoisMinIntervalSeconds(seconds);
  }

  @Override
  public void rememberUserInfoEnrichmentWhoisNickCooldownMinutes(int minutes) {
    runtimeConfig.rememberUserInfoEnrichmentWhoisNickCooldownMinutes(minutes);
  }

  @Override
  public void rememberUserInfoEnrichmentPeriodicRefreshEnabled(boolean enabled) {
    runtimeConfig.rememberUserInfoEnrichmentPeriodicRefreshEnabled(enabled);
  }

  @Override
  public void rememberUserInfoEnrichmentPeriodicRefreshIntervalSeconds(int seconds) {
    runtimeConfig.rememberUserInfoEnrichmentPeriodicRefreshIntervalSeconds(seconds);
  }

  @Override
  public void rememberUserInfoEnrichmentPeriodicRefreshNicksPerTick(int nicksPerTick) {
    runtimeConfig.rememberUserInfoEnrichmentPeriodicRefreshNicksPerTick(nicksPerTick);
  }

  @Override
  public void rememberMonitorIsonPollIntervalSeconds(int seconds) {
    runtimeConfig.rememberMonitorIsonPollIntervalSeconds(seconds);
  }
}

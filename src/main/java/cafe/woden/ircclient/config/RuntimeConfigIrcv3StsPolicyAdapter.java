package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.Ircv3StsPolicyConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for persisted IRCv3 STS policies backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigIrcv3StsPolicyAdapter implements Ircv3StsPolicyConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigIrcv3StsPolicyAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public Map<String, StsPolicySnapshot> readIrcv3StsPolicies() {
    return runtimeConfig.readIrcv3StsPolicies();
  }

  @Override
  public void rememberIrcv3StsPolicy(
      String host,
      long expiresAtEpochMs,
      Integer port,
      boolean preload,
      long durationSeconds,
      String rawValue) {
    runtimeConfig.rememberIrcv3StsPolicy(
        host, expiresAtEpochMs, port, preload, durationSeconds, rawValue);
  }

  @Override
  public void forgetIrcv3StsPolicy(String host) {
    runtimeConfig.forgetIrcv3StsPolicy(host);
  }
}

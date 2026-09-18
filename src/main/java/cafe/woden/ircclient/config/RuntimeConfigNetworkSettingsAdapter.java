package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.NetworkSettingsRuntimeConfigPort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for advanced network settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigNetworkSettingsAdapter implements NetworkSettingsRuntimeConfigPort {

  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigNetworkSettingsAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public boolean readGenericBouncerPreferLoginHint(boolean defaultValue) {
    return runtimeConfig.readGenericBouncerPreferLoginHint(defaultValue);
  }

  @Override
  public String readGenericBouncerLoginTemplate(String defaultValue) {
    return runtimeConfig.readGenericBouncerLoginTemplate(defaultValue);
  }

  @Override
  public void rememberClientProxy(IrcProperties.Proxy proxy) {
    runtimeConfig.rememberClientProxy(proxy);
  }

  @Override
  public void rememberClientHeartbeat(IrcProperties.Heartbeat heartbeat) {
    runtimeConfig.rememberClientHeartbeat(heartbeat);
  }

  @Override
  public void rememberClientFloodProtection(IrcProperties.FloodProtection floodProtection) {
    runtimeConfig.rememberClientFloodProtection(floodProtection);
  }

  @Override
  public void rememberGenericBouncerPreferLoginHint(boolean enabled) {
    runtimeConfig.rememberGenericBouncerPreferLoginHint(enabled);
  }

  @Override
  public void rememberGenericBouncerLoginTemplate(String template) {
    runtimeConfig.rememberGenericBouncerLoginTemplate(template);
  }

  @Override
  public void rememberClientTlsTrustAllCertificates(boolean trustAllCertificates) {
    runtimeConfig.rememberClientTlsTrustAllCertificates(trustAllCertificates);
  }
}

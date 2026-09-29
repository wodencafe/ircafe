package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.BouncerDiscoveryConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for bouncer discovery settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigBouncerDiscoveryAdapter implements BouncerDiscoveryConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigBouncerDiscoveryAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public List<String> readKnownChannels(String serverId) {
    return runtimeConfig.readKnownChannels(serverId);
  }

  @Override
  public boolean readServerTreeChannelAutoReattach(
      String serverId, String channel, boolean defaultValue) {
    return runtimeConfig.readServerTreeChannelAutoReattach(serverId, channel, defaultValue);
  }

  @Override
  public Map<String, Map<String, Boolean>> readGenericBouncerAutoConnectRules() {
    return runtimeConfig.readGenericBouncerAutoConnectRules();
  }

  @Override
  public void rememberGenericBouncerAutoConnectNetwork(
      String bouncerServerId, String networkName, boolean enabled) {
    runtimeConfig.rememberGenericBouncerAutoConnectNetwork(bouncerServerId, networkName, enabled);
  }

  @Override
  public void rememberSojuAutoConnectNetwork(
      String bouncerServerId, String networkName, boolean enabled) {
    runtimeConfig.rememberSojuAutoConnectNetwork(bouncerServerId, networkName, enabled);
  }

  @Override
  public void rememberZncAutoConnectNetwork(
      String bouncerServerId, String networkName, boolean enabled) {
    runtimeConfig.rememberZncAutoConnectNetwork(bouncerServerId, networkName, enabled);
  }

  @Override
  public String readGenericBouncerLoginTemplate(String defaultValue) {
    return runtimeConfig.readGenericBouncerLoginTemplate(defaultValue);
  }

  @Override
  public boolean readGenericBouncerPreferLoginHint(boolean defaultValue) {
    return runtimeConfig.readGenericBouncerPreferLoginHint(defaultValue);
  }
}

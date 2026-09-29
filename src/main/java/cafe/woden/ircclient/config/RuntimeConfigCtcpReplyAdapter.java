package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for CTCP auto-reply settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigCtcpReplyAdapter implements CtcpReplyRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigCtcpReplyAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public boolean readCtcpAutoRepliesEnabled(boolean defaultValue) {
    return runtimeConfig.readCtcpAutoRepliesEnabled(defaultValue);
  }

  @Override
  public boolean readCtcpAutoReplyVersionEnabled(boolean defaultValue) {
    return runtimeConfig.readCtcpAutoReplyVersionEnabled(defaultValue);
  }

  @Override
  public boolean readCtcpAutoReplyPingEnabled(boolean defaultValue) {
    return runtimeConfig.readCtcpAutoReplyPingEnabled(defaultValue);
  }

  @Override
  public boolean readCtcpAutoReplyTimeEnabled(boolean defaultValue) {
    return runtimeConfig.readCtcpAutoReplyTimeEnabled(defaultValue);
  }

  @Override
  public void rememberCtcpAutoRepliesEnabled(boolean enabled) {
    runtimeConfig.rememberCtcpAutoRepliesEnabled(enabled);
  }

  @Override
  public void rememberCtcpAutoReplyVersionEnabled(boolean enabled) {
    runtimeConfig.rememberCtcpAutoReplyVersionEnabled(enabled);
  }

  @Override
  public void rememberCtcpAutoReplyPingEnabled(boolean enabled) {
    runtimeConfig.rememberCtcpAutoReplyPingEnabled(enabled);
  }

  @Override
  public void rememberCtcpAutoReplyTimeEnabled(boolean enabled) {
    runtimeConfig.rememberCtcpAutoReplyTimeEnabled(enabled);
  }
}

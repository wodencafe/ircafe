package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.DiagnosticsRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for diagnostics runtime settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigDiagnosticsAdapter implements DiagnosticsRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigDiagnosticsAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public Path runtimeConfigPath() {
    return runtimeConfig.runtimeConfigPath();
  }

  @Override
  public boolean readApplicationJfrEnabled(boolean defaultValue) {
    return runtimeConfig.readApplicationJfrEnabled(defaultValue);
  }

  @Override
  public void rememberApplicationJfrEnabled(boolean enabled) {
    runtimeConfig.rememberApplicationJfrEnabled(enabled);
  }

  @Override
  public boolean readAppDiagnosticsAssertjSwingEnabled(boolean defaultValue) {
    return runtimeConfig.readAppDiagnosticsAssertjSwingEnabled(defaultValue);
  }

  @Override
  public boolean readAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(boolean defaultValue) {
    return runtimeConfig.readAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(defaultValue);
  }

  @Override
  public int readAppDiagnosticsAssertjSwingFreezeThresholdMs(int defaultValue) {
    return runtimeConfig.readAppDiagnosticsAssertjSwingFreezeThresholdMs(defaultValue);
  }

  @Override
  public int readAppDiagnosticsAssertjSwingWatchdogPollMs(int defaultValue) {
    return runtimeConfig.readAppDiagnosticsAssertjSwingWatchdogPollMs(defaultValue);
  }

  @Override
  public int readAppDiagnosticsAssertjSwingFallbackViolationReportMs(int defaultValue) {
    return runtimeConfig.readAppDiagnosticsAssertjSwingFallbackViolationReportMs(defaultValue);
  }

  @Override
  public boolean readAppDiagnosticsAssertjSwingIssuePlaySound(boolean defaultValue) {
    return runtimeConfig.readAppDiagnosticsAssertjSwingIssuePlaySound(defaultValue);
  }

  @Override
  public boolean readAppDiagnosticsAssertjSwingIssueShowNotification(boolean defaultValue) {
    return runtimeConfig.readAppDiagnosticsAssertjSwingIssueShowNotification(defaultValue);
  }

  @Override
  public boolean readAppDiagnosticsJhiccupEnabled(boolean defaultValue) {
    return runtimeConfig.readAppDiagnosticsJhiccupEnabled(defaultValue);
  }

  @Override
  public String readAppDiagnosticsJhiccupJarPath(String defaultValue) {
    return runtimeConfig.readAppDiagnosticsJhiccupJarPath(defaultValue);
  }

  @Override
  public String readAppDiagnosticsJhiccupJavaCommand(String defaultValue) {
    return runtimeConfig.readAppDiagnosticsJhiccupJavaCommand(defaultValue);
  }

  @Override
  public List<String> readAppDiagnosticsJhiccupArgs(List<String> defaultValue) {
    return runtimeConfig.readAppDiagnosticsJhiccupArgs(defaultValue);
  }

  @Override
  public void rememberAppDiagnosticsAssertjSwingEnabled(boolean enabled) {
    runtimeConfig.rememberAppDiagnosticsAssertjSwingEnabled(enabled);
  }

  @Override
  public void rememberAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(boolean enabled) {
    runtimeConfig.rememberAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(enabled);
  }

  @Override
  public void rememberAppDiagnosticsAssertjSwingFreezeThresholdMs(int ms) {
    runtimeConfig.rememberAppDiagnosticsAssertjSwingFreezeThresholdMs(ms);
  }

  @Override
  public void rememberAppDiagnosticsAssertjSwingWatchdogPollMs(int ms) {
    runtimeConfig.rememberAppDiagnosticsAssertjSwingWatchdogPollMs(ms);
  }

  @Override
  public void rememberAppDiagnosticsAssertjSwingFallbackViolationReportMs(int ms) {
    runtimeConfig.rememberAppDiagnosticsAssertjSwingFallbackViolationReportMs(ms);
  }

  @Override
  public void rememberAppDiagnosticsAssertjSwingIssuePlaySound(boolean enabled) {
    runtimeConfig.rememberAppDiagnosticsAssertjSwingIssuePlaySound(enabled);
  }

  @Override
  public void rememberAppDiagnosticsAssertjSwingIssueShowNotification(boolean enabled) {
    runtimeConfig.rememberAppDiagnosticsAssertjSwingIssueShowNotification(enabled);
  }

  @Override
  public void rememberAppDiagnosticsJhiccupEnabled(boolean enabled) {
    runtimeConfig.rememberAppDiagnosticsJhiccupEnabled(enabled);
  }

  @Override
  public void rememberAppDiagnosticsJhiccupJarPath(String jarPath) {
    runtimeConfig.rememberAppDiagnosticsJhiccupJarPath(jarPath);
  }

  @Override
  public void rememberAppDiagnosticsJhiccupJavaCommand(String javaCommand) {
    runtimeConfig.rememberAppDiagnosticsJhiccupJavaCommand(javaCommand);
  }

  @Override
  public void rememberAppDiagnosticsJhiccupArgs(List<String> args) {
    runtimeConfig.rememberAppDiagnosticsJhiccupArgs(args);
  }
}

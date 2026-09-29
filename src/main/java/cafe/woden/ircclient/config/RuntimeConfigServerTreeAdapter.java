package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.DockLayoutRuntimeConfigPort;
import cafe.woden.ircclient.config.api.LagIndicatorRuntimeConfigPort;
import cafe.woden.ircclient.config.api.MemoryUsageRuntimeConfigPort;
import cafe.woden.ircclient.config.api.SelectedTargetRuntimeConfigPort.LastSelectedTarget;
import cafe.woden.ircclient.config.api.ServerTreeBuiltInVisibilityConfigPort.ServerTreeBuiltInNodesVisibility;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelSortMode;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelState;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.config.api.ServerTreeRuntimeConfigPort;
import cafe.woden.ircclient.config.api.TrayCloseHintRuntimeConfigPort;
import cafe.woden.ircclient.config.api.UpdateNotifierRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/**
 * Secondary adapter for server-tree, session, UI settings, and shell runtime settings backed by
 * {@link RuntimeConfigStore}.
 */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigServerTreeAdapter
    implements ServerTreeRuntimeConfigPort,
        DockLayoutRuntimeConfigPort,
        LagIndicatorRuntimeConfigPort,
        MemoryUsageRuntimeConfigPort,
        TrayCloseHintRuntimeConfigPort,
        UpdateNotifierRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigServerTreeAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public boolean runtimeConfigFileExistedOnStartup() {
    return runtimeConfig.runtimeConfigFileExistedOnStartup();
  }

  @Override
  public Optional<Boolean> readTrayCloseToTrayIfPresent() {
    return runtimeConfig.readTrayCloseToTrayIfPresent();
  }

  @Override
  public boolean readTrayCloseToTrayHintShown(boolean defaultValue) {
    return runtimeConfig.readTrayCloseToTrayHintShown(defaultValue);
  }

  @Override
  public boolean readUpdateNotifierEnabled(boolean defaultValue) {
    return runtimeConfig.readUpdateNotifierEnabled(defaultValue);
  }

  @Override
  public boolean readLagIndicatorEnabled(boolean defaultValue) {
    return runtimeConfig.readLagIndicatorEnabled(defaultValue);
  }

  @Override
  public Path runtimeConfigPath() {
    return runtimeConfig.runtimeConfigPath();
  }

  @Override
  public void rememberJoinedChannel(String serverId, String channel) {
    runtimeConfig.rememberJoinedChannel(serverId, channel);
  }

  @Override
  public void forgetJoinedChannel(String serverId, String channel) {
    runtimeConfig.forgetJoinedChannel(serverId, channel);
  }

  @Override
  public List<String> readJoinedChannels(String serverId) {
    return runtimeConfig.readJoinedChannels(serverId);
  }

  @Override
  public boolean readServerTreeChannelAutoReattach(
      String serverId, String channel, boolean defaultValue) {
    return runtimeConfig.readServerTreeChannelAutoReattach(serverId, channel, defaultValue);
  }

  @Override
  public void rememberServerTreeChannel(String serverId, String channel) {
    runtimeConfig.rememberServerTreeChannel(serverId, channel);
  }

  @Override
  public void rememberServerTreeChannelAutoReattach(
      String serverId, String channel, boolean autoReattach) {
    runtimeConfig.rememberServerTreeChannelAutoReattach(serverId, channel, autoReattach);
  }

  @Override
  public boolean readServerTreeChannelPinned(
      String serverId, String channel, boolean defaultValue) {
    return runtimeConfig.readServerTreeChannelPinned(serverId, channel, defaultValue);
  }

  @Override
  public void rememberServerTreeChannelPinned(String serverId, String channel, boolean pinned) {
    runtimeConfig.rememberServerTreeChannelPinned(serverId, channel, pinned);
  }

  @Override
  public boolean readServerTreeChannelMuted(String serverId, String channel, boolean defaultValue) {
    return runtimeConfig.readServerTreeChannelMuted(serverId, channel, defaultValue);
  }

  @Override
  public void rememberServerTreeChannelMuted(String serverId, String channel, boolean muted) {
    runtimeConfig.rememberServerTreeChannelMuted(serverId, channel, muted);
  }

  @Override
  public void rememberServerTreeChannelSortMode(String serverId, ServerTreeChannelSortMode mode) {
    runtimeConfig.rememberServerTreeChannelSortMode(serverId, mode);
  }

  @Override
  public void rememberServerTreeChannelCustomOrder(String serverId, List<String> channels) {
    runtimeConfig.rememberServerTreeChannelCustomOrder(serverId, channels);
  }

  @Override
  public ServerTreeChannelState readServerTreeChannelState(String serverId) {
    return runtimeConfig.readServerTreeChannelState(serverId);
  }

  @Override
  public void rememberPrivateMessageTarget(String serverId, String nick) {
    runtimeConfig.rememberPrivateMessageTarget(serverId, nick);
  }

  @Override
  public void forgetPrivateMessageTarget(String serverId, String nick) {
    runtimeConfig.forgetPrivateMessageTarget(serverId, nick);
  }

  @Override
  public void rememberUiSettings(String theme, String chatFontFamily, int chatFontSize) {
    runtimeConfig.rememberUiSettings(theme, chatFontFamily, chatFontSize);
  }

  @Override
  public Optional<String> readStartupThemePending() {
    return runtimeConfig.readStartupThemePending();
  }

  @Override
  public void rememberStartupThemePending(String theme) {
    runtimeConfig.rememberStartupThemePending(theme);
  }

  @Override
  public void clearStartupThemePending() {
    runtimeConfig.clearStartupThemePending();
  }

  @Override
  public void rememberMemoryUsageDisplayMode(String mode) {
    runtimeConfig.rememberMemoryUsageDisplayMode(mode);
  }

  @Override
  public int readMemoryUsageRefreshIntervalMs(int defaultValue) {
    return runtimeConfig.readMemoryUsageRefreshIntervalMs(defaultValue);
  }

  @Override
  public void rememberMemoryUsageRefreshIntervalMs(int intervalMs) {
    runtimeConfig.rememberMemoryUsageRefreshIntervalMs(intervalMs);
  }

  @Override
  public void rememberMemoryUsageWarningNearMaxPercent(int percent) {
    runtimeConfig.rememberMemoryUsageWarningNearMaxPercent(percent);
  }

  @Override
  public void rememberMemoryUsageWarningTooltipEnabled(boolean enabled) {
    runtimeConfig.rememberMemoryUsageWarningTooltipEnabled(enabled);
  }

  @Override
  public void rememberMemoryUsageWarningToastEnabled(boolean enabled) {
    runtimeConfig.rememberMemoryUsageWarningToastEnabled(enabled);
  }

  @Override
  public void rememberMemoryUsageWarningPushyEnabled(boolean enabled) {
    runtimeConfig.rememberMemoryUsageWarningPushyEnabled(enabled);
  }

  @Override
  public void rememberMemoryUsageWarningSoundEnabled(boolean enabled) {
    runtimeConfig.rememberMemoryUsageWarningSoundEnabled(enabled);
  }

  @Override
  public Map<String, ServerTreeBuiltInNodesVisibility> readServerTreeBuiltInNodesVisibility() {
    return runtimeConfig.readServerTreeBuiltInNodesVisibility();
  }

  @Override
  public void rememberServerTreeBuiltInNodesVisibility(
      String serverId, ServerTreeBuiltInNodesVisibility visibility) {
    runtimeConfig.rememberServerTreeBuiltInNodesVisibility(serverId, visibility);
  }

  @Override
  public Map<String, ServerTreeBuiltInLayout> readServerTreeBuiltInLayoutByServer() {
    return runtimeConfig.readServerTreeBuiltInLayoutByServer();
  }

  @Override
  public void rememberServerTreeBuiltInLayout(String serverId, ServerTreeBuiltInLayout layout) {
    runtimeConfig.rememberServerTreeBuiltInLayout(serverId, layout);
  }

  @Override
  public Map<String, ServerTreeRootSiblingOrder> readServerTreeRootSiblingOrderByServer() {
    return runtimeConfig.readServerTreeRootSiblingOrderByServer();
  }

  @Override
  public void rememberServerTreeRootSiblingOrder(
      String serverId, ServerTreeRootSiblingOrder order) {
    runtimeConfig.rememberServerTreeRootSiblingOrder(serverId, order);
  }

  @Override
  public void rememberServerDockWidthPx(int serverDockWidthPx) {
    runtimeConfig.rememberServerDockWidthPx(serverDockWidthPx);
  }

  @Override
  public void rememberUserDockWidthPx(int userDockWidthPx) {
    runtimeConfig.rememberUserDockWidthPx(userDockWidthPx);
  }

  @Override
  public void rememberPreserveDockLayout(boolean preserveDockLayout) {
    runtimeConfig.rememberPreserveDockLayout(preserveDockLayout);
  }

  @Override
  public Optional<LastSelectedTarget> readLastSelectedTarget() {
    return runtimeConfig.readLastSelectedTarget();
  }

  @Override
  public void rememberLastSelectedTarget(String serverId, String target) {
    runtimeConfig.rememberLastSelectedTarget(serverId, target);
  }

  @Override
  public Optional<Boolean> readApplicationRootVisibleIfPresent() {
    return runtimeConfig.readApplicationRootVisibleIfPresent();
  }

  @Override
  public boolean readApplicationRootVisible(boolean defaultValue) {
    return runtimeConfig.readApplicationRootVisible(defaultValue);
  }

  @Override
  public void rememberApplicationRootVisible(boolean visible) {
    runtimeConfig.rememberApplicationRootVisible(visible);
  }

  @Override
  public void rememberServerAutoConnectOnStart(String serverId, boolean enabled) {
    runtimeConfig.rememberServerAutoConnectOnStart(serverId, enabled);
  }

  @Override
  public boolean readServerAutoConnectOnStart(String serverId, boolean defaultValue) {
    return runtimeConfig.readServerAutoConnectOnStart(serverId, defaultValue);
  }

  @Override
  public void rememberUpdateNotifierEnabled(boolean enabled) {
    runtimeConfig.rememberUpdateNotifierEnabled(enabled);
  }

  @Override
  public void rememberLagIndicatorEnabled(boolean enabled) {
    runtimeConfig.rememberLagIndicatorEnabled(enabled);
  }

  @Override
  public void rememberTrayCloseToTray(boolean enabled) {
    runtimeConfig.rememberTrayCloseToTray(enabled);
  }

  @Override
  public void rememberTrayCloseToTrayHintShown(boolean shown) {
    runtimeConfig.rememberTrayCloseToTrayHintShown(shown);
  }

  @Override
  public boolean readChatSmoothWheelScrollingEnabled(boolean defaultValue) {
    return runtimeConfig.readChatSmoothWheelScrollingEnabled(defaultValue);
  }

  @Override
  public boolean readChatHistoryLockViewportDuringLoadOlder(boolean defaultValue) {
    return runtimeConfig.readChatHistoryLockViewportDuringLoadOlder(defaultValue);
  }

  @Override
  public boolean readNickCompletionCycleWithTabEnabled(boolean defaultValue) {
    return runtimeConfig.readNickCompletionCycleWithTabEnabled(defaultValue);
  }

  @Override
  public boolean readNickCompletionAppendAddressSuffixEnabled(boolean defaultValue) {
    return runtimeConfig.readNickCompletionAppendAddressSuffixEnabled(defaultValue);
  }

  @Override
  public int readServerTreeUnreadBadgeScalePercent(int defaultValue) {
    return runtimeConfig.readServerTreeUnreadBadgeScalePercent(defaultValue);
  }

  @Override
  public boolean isIrcv3CapabilityEnabled(String capability, boolean defaultEnabled) {
    return runtimeConfig.isIrcv3CapabilityEnabled(capability, defaultEnabled);
  }

  @Override
  public void rememberIrcv3CapabilityEnabled(String capability, boolean enabled) {
    runtimeConfig.rememberIrcv3CapabilityEnabled(capability, enabled);
  }
}

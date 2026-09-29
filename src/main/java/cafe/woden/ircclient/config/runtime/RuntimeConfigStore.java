package cafe.woden.ircclient.config.runtime;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.EmbedLoadPolicyConfigPort.EmbedLoadPolicySnapshot;
import cafe.woden.ircclient.config.api.Ircv3CapabilityNameResolverPort;
import cafe.woden.ircclient.config.api.Ircv3StsPolicyConfigPort;
import cafe.woden.ircclient.config.api.NotificationRule;
import cafe.woden.ircclient.config.api.QuitMessageRuntimeConfigPort;
import cafe.woden.ircclient.config.api.SelectedTargetRuntimeConfigPort.LastSelectedTarget;
import cafe.woden.ircclient.config.api.ServerTreeBuiltInVisibilityConfigPort.ServerTreeBuiltInNodesVisibility;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelSortMode;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelState;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.config.properties.PushyProperties;
import cafe.woden.ircclient.model.FilterRule;
import cafe.woden.ircclient.model.FilterScopeOverride;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.UserCommandAlias;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@SecondaryAdapter
@InfrastructureLayer
public class RuntimeConfigStore {

  public static final String DEFAULT_QUIT_MESSAGE =
      QuitMessageRuntimeConfigPort.DEFAULT_QUIT_MESSAGE;

  private final Path file;
  private final RuntimeConfigStoreDelegates stores;

  public RuntimeConfigStore(
      @Value("${ircafe.runtime-config:${XDG_CONFIG_HOME:${user.home}/.config}/ircafe/ircafe.yml}")
          String filePath,
      IrcProperties defaults) {
    this.file = Paths.get(Objects.requireNonNullElse(filePath, "").trim());
    this.stores = new RuntimeConfigStoreDelegates(this.file, defaults);

    ensureFileExistsWithServers();
  }

  @Autowired(required = false)
  void setIrcv3CapabilityNameResolver(Ircv3CapabilityNameResolverPort ircv3CapabilityNameResolver) {
    stores.ircv3Stores.capabilityStore.setCapabilityNameResolver(ircv3CapabilityNameResolver);
  }

  /**
   * Returns true if the runtime config file already existed when IRCafe started.
   *
   * <p>This is used for one-time migrations where we want to preserve legacy behavior for existing
   * installs, while using new defaults for first-time installs.
   */
  public boolean runtimeConfigFileExistedOnStartup() {
    return stores.documentStore.fileExistedOnStartup();
  }

  public synchronized boolean readFirstRunSetupDismissed() {
    return stores.uiStores.uiFeatureToggleStore.readFirstRunSetupDismissed();
  }

  public synchronized void rememberFirstRunSetupDismissed() {
    stores.uiStores.uiFeatureToggleStore.rememberFirstRunSetupDismissed();
  }

  /**
   * Reads {@code ircafe.ui.tray.closeToTray} only if it is explicitly present in the runtime config
   * file.
   *
   * <p>If the key is absent (or the file doesn't exist), returns {@link Optional#empty()}.
   */
  public synchronized Optional<Boolean> readTrayCloseToTrayIfPresent() {
    return stores.uiStores.trayStore.readCloseToTrayIfPresent();
  }

  /**
   * Reads {@code ircafe.ui.tray.closeToTrayHintShown} from runtime config.
   *
   * <p>Returns {@code defaultValue} when the key is missing or invalid.
   */
  public synchronized boolean readTrayCloseToTrayHintShown(boolean defaultValue) {
    return stores.uiStores.trayStore.readCloseToTrayHintShown(defaultValue);
  }

  /**
   * Reads {@code ircafe.ui.invites.autoJoinOnInvite} from runtime config.
   *
   * <p>Returns {@code defaultValue} when the key is missing or invalid.
   */
  public synchronized boolean readInviteAutoJoinEnabled(boolean defaultValue) {
    return stores.uiStores.uiFeatureToggleStore.readInviteAutoJoinEnabled(defaultValue);
  }

  /**
   * Reads {@code ircafe.ui.updateNotifier.enabled} from runtime config.
   *
   * <p>Returns {@code defaultValue} when the key is missing or invalid.
   */
  public synchronized boolean readUpdateNotifierEnabled(boolean defaultValue) {
    return stores.uiStores.uiFeatureToggleStore.readUpdateNotifierEnabled(defaultValue);
  }

  /**
   * Reads {@code ircafe.ui.lagIndicator.enabled} from runtime config.
   *
   * <p>Returns {@code defaultValue} when the key is missing or invalid.
   */
  public synchronized boolean readLagIndicatorEnabled(boolean defaultValue) {
    return stores.uiStores.uiFeatureToggleStore.readLagIndicatorEnabled(defaultValue);
  }

  public Path runtimeConfigPath() {
    return file;
  }

  /**
   * Run a series of mutations with a single final disk write.
   *
   * <p>Callers should keep the action focused on {@code remember*} methods so EDT stalls are
   * minimized.
   */
  public synchronized void runMutationBatch(Runnable action) {
    stores.documentStore.runMutationBatch(action);
  }

  public synchronized void beginMutationBatch() {
    stores.documentStore.beginMutationBatch();
  }

  public synchronized void endMutationBatch() {
    stores.documentStore.endMutationBatch();
  }

  public synchronized void ensureFileExistsWithServers() {
    stores.serverStores.serverListStore.ensureFileExistsWithServers();
  }

  public synchronized void writeServers(List<IrcProperties.Server> servers) {
    stores.serverStores.serverListStore.writeServers(servers);
  }

  /** Returns configured server ids from runtime config, falling back to boot defaults. */
  public synchronized List<String> readServerIds() {
    return stores.serverStores.serverListStore.readServerIds();
  }

  /**
   * Returns runtime {@code autoJoin} entries for servers that explicitly define that key.
   *
   * <p>Only servers with an explicit {@code autoJoin} key are included. This allows callers to
   * treat runtime config as authoritative without conflating missing keys with inherited defaults.
   */
  public synchronized Map<String, List<String>> readExplicitServerAutoJoinById() {
    return stores.serverStores.serverListStore.readExplicitServerAutoJoinById();
  }

  public synchronized void rememberJoinedChannel(String serverId, String channel) {
    rememberServerTreeChannel(serverId, channel);
  }

  public synchronized void forgetJoinedChannel(String serverId, String channel) {
    forgetServerTreeChannel(serverId, channel);
  }

  public synchronized List<String> readJoinedChannels(String serverId) {
    return stores.serverStores.serverTreeChannelStateStore.readJoinedChannels(serverId);
  }

  /** Returns known channels for this server (attached + detached). */
  public synchronized List<String> readKnownChannels(String serverId) {
    return stores.serverStores.serverTreeChannelStateStore.readKnownChannels(serverId);
  }

  public synchronized boolean readServerTreeChannelAutoReattach(
      String serverId, String channel, boolean defaultValue) {
    return stores.serverStores.serverTreeChannelStateStore.readServerTreeChannelAutoReattach(
        serverId, channel, defaultValue);
  }

  public synchronized void rememberServerTreeChannel(String serverId, String channel) {
    stores.serverStores.serverTreeChannelStateStore.rememberServerTreeChannel(serverId, channel);
  }

  public synchronized void forgetServerTreeChannel(String serverId, String channel) {
    stores.serverStores.serverTreeChannelStateStore.forgetServerTreeChannel(serverId, channel);
  }

  public synchronized void rememberServerTreeChannelAutoReattach(
      String serverId, String channel, boolean autoReattach) {
    stores.serverStores.serverTreeChannelStateStore.rememberServerTreeChannelAutoReattach(
        serverId, channel, autoReattach);
  }

  public synchronized boolean readServerTreeChannelPinned(
      String serverId, String channel, boolean defaultValue) {
    return stores.serverStores.serverTreeChannelStateStore.readServerTreeChannelPinned(
        serverId, channel, defaultValue);
  }

  public synchronized void rememberServerTreeChannelPinned(
      String serverId, String channel, boolean pinned) {
    stores.serverStores.serverTreeChannelStateStore.rememberServerTreeChannelPinned(
        serverId, channel, pinned);
  }

  public synchronized boolean readServerTreeChannelMuted(
      String serverId, String channel, boolean defaultValue) {
    return stores.serverStores.serverTreeChannelStateStore.readServerTreeChannelMuted(
        serverId, channel, defaultValue);
  }

  public synchronized void rememberServerTreeChannelMuted(
      String serverId, String channel, boolean muted) {
    stores.serverStores.serverTreeChannelStateStore.rememberServerTreeChannelMuted(
        serverId, channel, muted);
  }

  public synchronized ServerTreeChannelSortMode readServerTreeChannelSortMode(
      String serverId, ServerTreeChannelSortMode defaultValue) {
    return stores.serverStores.serverTreeChannelStateStore.readServerTreeChannelSortMode(
        serverId, defaultValue);
  }

  public synchronized void rememberServerTreeChannelSortMode(
      String serverId, ServerTreeChannelSortMode mode) {
    stores.serverStores.serverTreeChannelStateStore.rememberServerTreeChannelSortMode(
        serverId, mode);
  }

  public synchronized List<String> readServerTreeChannelCustomOrder(String serverId) {
    return stores.serverStores.serverTreeChannelStateStore.readServerTreeChannelCustomOrder(
        serverId);
  }

  public synchronized void rememberServerTreeChannelCustomOrder(
      String serverId, List<String> customOrder) {
    stores.serverStores.serverTreeChannelStateStore.rememberServerTreeChannelCustomOrder(
        serverId, customOrder);
  }

  public synchronized ServerTreeChannelState readServerTreeChannelState(String serverId) {
    return stores.serverStores.serverTreeChannelStateStore.readServerTreeChannelState(serverId);
  }

  public synchronized void rememberPrivateMessageTarget(String serverId, String nick) {
    stores.serverStores.privateMessageTargetStore.rememberPrivateMessageTarget(serverId, nick);
  }

  public synchronized void forgetPrivateMessageTarget(String serverId, String nick) {
    stores.serverStores.privateMessageTargetStore.forgetPrivateMessageTarget(serverId, nick);
  }

  public synchronized List<String> readPrivateMessageTargets(String serverId) {
    return stores.serverStores.privateMessageTargetStore.readPrivateMessageTargets(serverId);
  }

  public synchronized void rememberMonitorNick(String serverId, String nick) {
    stores.serverStores.monitorRosterStore.rememberMonitorNick(serverId, nick);
  }

  public synchronized void forgetMonitorNick(String serverId, String nick) {
    stores.serverStores.monitorRosterStore.forgetMonitorNick(serverId, nick);
  }

  public synchronized void replaceMonitorNicks(String serverId, List<String> nicks) {
    stores.serverStores.monitorRosterStore.replaceMonitorNicks(serverId, nicks);
  }

  public synchronized List<String> readMonitorNicks(String serverId) {
    return stores.serverStores.monitorRosterStore.readMonitorNicks(serverId);
  }

  public synchronized void rememberNick(String serverId, String nick) {
    stores.serverStores.serverIdentityStore.rememberNick(serverId, nick);
  }

  public synchronized void rememberUiSettings(
      String theme, String chatFontFamily, int chatFontSize) {
    stores.uiStores.uiSettingsStore.rememberUiSettings(theme, chatFontFamily, chatFontSize);
  }

  /**
   * Reads {@code ircafe.ui.startupThemePending} from runtime config.
   *
   * <p>When present, this indicates startup began applying a theme but did not clear the marker.
   * The value is used as a recovery hint on the next launch.
   */
  public synchronized Optional<String> readStartupThemePending() {
    return stores.uiStores.uiSettingsStore.readStartupThemePending();
  }

  /** Persists {@code ircafe.ui.startupThemePending}. Blank values remove the key. */
  public synchronized void rememberStartupThemePending(String theme) {
    stores.uiStores.uiSettingsStore.rememberStartupThemePending(theme);
  }

  /** Removes {@code ircafe.ui.startupThemePending}. */
  public synchronized void clearStartupThemePending() {
    stores.uiStores.uiSettingsStore.clearStartupThemePending();
  }

  public synchronized void rememberMemoryUsageDisplayMode(String mode) {
    stores.uiStores.memoryUsageStore.rememberDisplayMode(mode);
  }

  public synchronized int readMemoryUsageRefreshIntervalMs(int defaultValue) {
    return stores.uiStores.memoryUsageStore.readRefreshIntervalMs(defaultValue);
  }

  public synchronized void rememberMemoryUsageRefreshIntervalMs(int intervalMs) {
    stores.uiStores.memoryUsageStore.rememberRefreshIntervalMs(intervalMs);
  }

  public synchronized void rememberMemoryUsageWarningNearMaxPercent(int percent) {
    stores.uiStores.memoryUsageStore.rememberWarningNearMaxPercent(percent);
  }

  public synchronized void rememberMemoryUsageWarningTooltipEnabled(boolean enabled) {
    stores.uiStores.memoryUsageStore.rememberWarningTooltipEnabled(enabled);
  }

  public synchronized void rememberMemoryUsageWarningToastEnabled(boolean enabled) {
    stores.uiStores.memoryUsageStore.rememberWarningToastEnabled(enabled);
  }

  public synchronized void rememberMemoryUsageWarningPushyEnabled(boolean enabled) {
    stores.uiStores.memoryUsageStore.rememberWarningPushyEnabled(enabled);
  }

  public synchronized void rememberMemoryUsageWarningSoundEnabled(boolean enabled) {
    stores.uiStores.memoryUsageStore.rememberWarningSoundEnabled(enabled);
  }

  /**
   * Reads whether runtime JFR diagnostics are enabled from {@code ircafe.ui.appDiagnostics.jfr}.
   *
   * <p>Returns {@code defaultValue} when the key is missing or invalid.
   */
  public synchronized boolean readApplicationJfrEnabled(boolean defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readApplicationJfrEnabled(defaultValue);
  }

  /**
   * Persists {@code ircafe.ui.appDiagnostics.jfr.enabled}.
   *
   * <p>This controls runtime JFR diagnostics visibility/collection in the Application -> JFR view.
   */
  public synchronized void rememberApplicationJfrEnabled(boolean enabled) {
    stores.uiStores.appDiagnosticsStore.rememberApplicationJfrEnabled(enabled);
  }

  public synchronized Map<String, ServerTreeBuiltInNodesVisibility>
      readServerTreeBuiltInNodesVisibility() {
    return stores.serverStores.serverTreeLayoutStore.readBuiltInNodesVisibility();
  }

  public synchronized void rememberServerTreeBuiltInNodesVisibility(
      String serverId, ServerTreeBuiltInNodesVisibility visibility) {
    stores.serverStores.serverTreeLayoutStore.rememberBuiltInNodesVisibility(serverId, visibility);
  }

  public synchronized Map<String, ServerTreeBuiltInLayout> readServerTreeBuiltInLayoutByServer() {
    return stores.serverStores.serverTreeLayoutStore.readBuiltInLayoutByServer();
  }

  public synchronized void rememberServerTreeBuiltInLayout(
      String serverId, ServerTreeBuiltInLayout layout) {
    stores.serverStores.serverTreeLayoutStore.rememberBuiltInLayout(serverId, layout);
  }

  public synchronized Map<String, ServerTreeRootSiblingOrder>
      readServerTreeRootSiblingOrderByServer() {
    return stores.serverStores.serverTreeLayoutStore.readRootSiblingOrderByServer();
  }

  public synchronized void rememberServerTreeRootSiblingOrder(
      String serverId, ServerTreeRootSiblingOrder order) {
    stores.serverStores.serverTreeLayoutStore.rememberRootSiblingOrder(serverId, order);
  }

  public synchronized void rememberAccentColor(String accentColor) {
    stores.uiStores.uiSettingsStore.rememberAccentColor(accentColor);
  }

  public synchronized void rememberAccentStrength(int strength) {
    stores.uiStores.uiSettingsStore.rememberAccentStrength(strength);
  }

  /**
   * Persists the docking/layout widths so the user's side-dock sizing survives restart.
   *
   * <p>Stored under {@code ircafe.ui.layout}.
   */
  public synchronized void rememberDockLayoutWidths(
      Integer serverDockWidthPx, Integer userDockWidthPx) {
    stores.uiStores.uiSettingsStore.rememberDockLayoutWidths(serverDockWidthPx, userDockWidthPx);
  }

  public synchronized void rememberServerDockWidthPx(int serverDockWidthPx) {
    stores.uiStores.uiSettingsStore.rememberServerDockWidthPx(serverDockWidthPx);
  }

  public synchronized void rememberUserDockWidthPx(int userDockWidthPx) {
    stores.uiStores.uiSettingsStore.rememberUserDockWidthPx(userDockWidthPx);
  }

  public synchronized void rememberPreserveDockLayout(boolean preserveDockLayout) {
    stores.uiStores.uiSettingsStore.rememberPreserveDockLayout(preserveDockLayout);
  }

  /** Reads {@code ircafe.ui.lastSelectedTarget} if present and valid. */
  public synchronized Optional<LastSelectedTarget> readLastSelectedTarget() {
    return stores.uiStores.uiSettingsStore.readLastSelectedTarget();
  }

  /** Persists {@code ircafe.ui.lastSelectedTarget}. Blank values clear the persisted target. */
  public synchronized void rememberLastSelectedTarget(String serverId, String target) {
    stores.uiStores.uiSettingsStore.rememberLastSelectedTarget(serverId, target);
  }

  public synchronized Optional<Boolean> readApplicationRootVisibleIfPresent() {
    return stores.uiStores.uiSettingsStore.readApplicationRootVisibleIfPresent();
  }

  public synchronized boolean readApplicationRootVisible(boolean defaultValue) {
    return readApplicationRootVisibleIfPresent().orElse(defaultValue);
  }

  public synchronized void rememberApplicationRootVisible(boolean visible) {
    stores.uiStores.uiSettingsStore.rememberApplicationRootVisible(visible);
  }

  public synchronized void rememberUiDensity(String density) {
    stores.uiStores.uiSettingsStore.rememberUiDensity(density);
  }

  public synchronized void rememberUiFontOverrideEnabled(boolean enabled) {
    stores.uiStores.uiSettingsStore.rememberUiFontOverrideEnabled(enabled);
  }

  public synchronized void rememberUiFontFamily(String family) {
    stores.uiStores.uiSettingsStore.rememberUiFontFamily(family);
  }

  public synchronized void rememberUiFontSize(int size) {
    stores.uiStores.uiSettingsStore.rememberUiFontSize(size);
  }

  public synchronized void rememberCornerRadius(int cornerRadius) {
    stores.uiStores.uiSettingsStore.rememberCornerRadius(cornerRadius);
  }

  public synchronized void rememberChatThemePreset(String preset) {
    stores.uiStores.uiSettingsStore.rememberChatThemePreset(preset);
  }

  public synchronized void rememberChatTimestampColor(String hex) {
    stores.uiStores.uiSettingsStore.rememberChatTimestampColor(hex);
  }

  public synchronized void rememberChatSystemColor(String hex) {
    stores.uiStores.uiSettingsStore.rememberChatSystemColor(hex);
  }

  public synchronized void rememberChatMessageColor(String hex) {
    stores.uiStores.uiSettingsStore.rememberChatMessageColor(hex);
  }

  public synchronized void rememberChatNoticeColor(String hex) {
    stores.uiStores.uiSettingsStore.rememberChatNoticeColor(hex);
  }

  public synchronized void rememberChatActionColor(String hex) {
    stores.uiStores.uiSettingsStore.rememberChatActionColor(hex);
  }

  public synchronized void rememberChatErrorColor(String hex) {
    stores.uiStores.uiSettingsStore.rememberChatErrorColor(hex);
  }

  public synchronized void rememberChatPresenceColor(String hex) {
    stores.uiStores.uiSettingsStore.rememberChatPresenceColor(hex);
  }

  public synchronized void rememberChatMentionBgColor(String hex) {
    stores.uiStores.uiSettingsStore.rememberChatMentionBgColor(hex);
  }

  public synchronized void rememberServerTreeUnreadChannelColor(String hex) {
    stores.uiStores.uiSettingsStore.rememberServerTreeUnreadChannelColor(hex);
  }

  public synchronized void rememberServerTreeHighlightChannelColor(String hex) {
    stores.uiStores.uiSettingsStore.rememberServerTreeHighlightChannelColor(hex);
  }

  public synchronized void rememberChatMentionStrength(int strength) {
    stores.uiStores.uiSettingsStore.rememberChatMentionStrength(strength);
  }

  public synchronized void rememberAutoConnectOnStart(boolean enabled) {
    stores.serverStores.serverAutoConnectStore.rememberAutoConnectOnStart(enabled);
  }

  /**
   * Reads persisted per-server startup auto-connect overrides.
   *
   * <p>Stored under {@code ircafe.ui.serverAutoConnectOnStartByServer.<serverId>}. Default behavior
   * is enabled, so this map usually contains only {@code false} entries.
   */
  public synchronized Map<String, Boolean> readServerAutoConnectOnStartByServer() {
    return stores.serverStores.serverAutoConnectStore.readServerAutoConnectOnStartByServer();
  }

  /**
   * Reads whether a server should auto-connect on startup.
   *
   * <p>Returns {@code defaultValue} when no override is present.
   */
  public synchronized boolean readServerAutoConnectOnStart(String serverId, boolean defaultValue) {
    return stores.serverStores.serverAutoConnectStore.readServerAutoConnectOnStart(
        serverId, defaultValue);
  }

  /**
   * Persists whether a server should auto-connect on startup.
   *
   * <p>Enabled is the default, so enabled values are removed to keep the YAML concise.
   */
  public synchronized void rememberServerAutoConnectOnStart(String serverId, boolean enabled) {
    stores.serverStores.serverAutoConnectStore.rememberServerAutoConnectOnStart(serverId, enabled);
  }

  public synchronized void rememberInviteAutoJoinEnabled(boolean enabled) {
    stores.uiStores.uiFeatureToggleStore.rememberInviteAutoJoinEnabled(enabled);
  }

  public synchronized void rememberUpdateNotifierEnabled(boolean enabled) {
    stores.uiStores.uiFeatureToggleStore.rememberUpdateNotifierEnabled(enabled);
  }

  public synchronized void rememberLagIndicatorEnabled(boolean enabled) {
    stores.uiStores.uiFeatureToggleStore.rememberLagIndicatorEnabled(enabled);
  }

  public synchronized void rememberTrayEnabled(boolean enabled) {
    stores.uiStores.trayStore.rememberEnabled(enabled);
  }

  public synchronized void rememberTrayCloseToTray(boolean enabled) {
    stores.uiStores.trayStore.rememberCloseToTray(enabled);
  }

  public synchronized void rememberTrayCloseToTrayHintShown(boolean shown) {
    stores.uiStores.trayStore.rememberCloseToTrayHintShown(shown);
  }

  public synchronized void rememberTrayMinimizeToTray(boolean enabled) {
    stores.uiStores.trayStore.rememberMinimizeToTray(enabled);
  }

  public synchronized void rememberTrayStartMinimized(boolean enabled) {
    stores.uiStores.trayStore.rememberStartMinimized(enabled);
  }

  public synchronized void rememberTrayNotifyHighlights(boolean enabled) {
    stores.uiStores.trayStore.rememberNotifyHighlights(enabled);
  }

  public synchronized void rememberTrayNotifyPrivateMessages(boolean enabled) {
    stores.uiStores.trayStore.rememberNotifyPrivateMessages(enabled);
  }

  public synchronized void rememberTrayNotifyConnectionState(boolean enabled) {
    stores.uiStores.trayStore.rememberNotifyConnectionState(enabled);
  }

  public synchronized void rememberTrayNotifyOnlyWhenUnfocused(boolean enabled) {
    stores.uiStores.trayStore.rememberNotifyOnlyWhenUnfocused(enabled);
  }

  public synchronized void rememberTrayNotifyOnlyWhenMinimizedOrHidden(boolean enabled) {
    stores.uiStores.trayStore.rememberNotifyOnlyWhenMinimizedOrHidden(enabled);
  }

  public synchronized void rememberTrayNotifySuppressWhenTargetActive(boolean enabled) {
    stores.uiStores.trayStore.rememberNotifySuppressWhenTargetActive(enabled);
  }

  public synchronized void rememberTrayLinuxDbusActionsEnabled(boolean enabled) {
    stores.uiStores.trayStore.rememberLinuxDbusActionsEnabled(enabled);
  }

  public synchronized void rememberTrayNotificationBackend(String backendToken) {
    stores.uiStores.trayStore.rememberNotificationBackend(backendToken);
  }

  public synchronized void rememberTrayNotificationSoundsEnabled(boolean enabled) {
    stores.uiStores.trayStore.rememberNotificationSoundsEnabled(enabled);
  }

  public synchronized void rememberTrayNotificationSound(String soundId) {
    stores.uiStores.trayStore.rememberNotificationSound(soundId);
  }

  public synchronized void rememberTrayNotificationSoundUseCustom(boolean useCustom) {
    stores.uiStores.trayStore.rememberNotificationSoundUseCustom(useCustom);
  }

  public synchronized void rememberTrayNotificationSoundCustomPath(String relativePath) {
    stores.uiStores.trayStore.rememberNotificationSoundCustomPath(relativePath);
  }

  public synchronized void rememberPushySettings(PushyProperties settings) {
    stores.pushyStore.rememberSettings(settings);
  }

  public synchronized void rememberNotificationRuleCooldownSeconds(int seconds) {
    stores.uiStores.notificationStore.rememberRuleCooldownSeconds(seconds);
  }

  public synchronized void rememberNotificationRules(List<NotificationRule> rules) {
    stores.uiStores.notificationStore.rememberRules(rules);
  }

  public synchronized List<UserCommandAlias> readUserCommandAliases() {
    return stores.userCommandStore.readAliases();
  }

  public synchronized boolean readUnknownCommandAsRawEnabled(boolean defaultValue) {
    return stores.userCommandStore.readUnknownCommandAsRawEnabled(defaultValue);
  }

  public synchronized String readDefaultQuitMessage() {
    return stores.uiStores.chatBehaviorStore.readDefaultQuitMessage();
  }

  public synchronized boolean readNickCompletionCycleWithTabEnabled(boolean defaultValue) {
    return stores.uiStores.chatBehaviorStore.readNickCompletionCycleWithTabEnabled(defaultValue);
  }

  public synchronized boolean readNickCompletionAppendAddressSuffixEnabled(boolean defaultValue) {
    return stores.uiStores.chatBehaviorStore.readNickCompletionAppendAddressSuffixEnabled(
        defaultValue);
  }

  public synchronized boolean readAppDiagnosticsAssertjSwingEnabled(boolean defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readAssertjSwingEnabled(defaultValue);
  }

  public synchronized boolean readAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(
      boolean defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readAssertjSwingFreezeWatchdogEnabled(defaultValue);
  }

  public synchronized int readAppDiagnosticsAssertjSwingFreezeThresholdMs(int defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readAssertjSwingFreezeThresholdMs(defaultValue);
  }

  public synchronized int readAppDiagnosticsAssertjSwingWatchdogPollMs(int defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readAssertjSwingWatchdogPollMs(defaultValue);
  }

  public synchronized int readAppDiagnosticsAssertjSwingFallbackViolationReportMs(
      int defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readAssertjSwingFallbackViolationReportMs(
        defaultValue);
  }

  public synchronized boolean readAppDiagnosticsAssertjSwingIssuePlaySound(boolean defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readAssertjSwingIssuePlaySound(defaultValue);
  }

  public synchronized boolean readAppDiagnosticsAssertjSwingIssueShowNotification(
      boolean defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readAssertjSwingIssueShowNotification(defaultValue);
  }

  public synchronized boolean readAppDiagnosticsJhiccupEnabled(boolean defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readJhiccupEnabled(defaultValue);
  }

  public synchronized String readAppDiagnosticsJhiccupJarPath(String defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readJhiccupJarPath(defaultValue);
  }

  public synchronized String readAppDiagnosticsJhiccupJavaCommand(String defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readJhiccupJavaCommand(defaultValue);
  }

  public synchronized List<String> readAppDiagnosticsJhiccupArgs(List<String> defaultValue) {
    return stores.uiStores.appDiagnosticsStore.readJhiccupArgs(defaultValue);
  }

  public synchronized String readLaunchJvmJavaCommand(String defaultValue) {
    return stores.launchJvmStore.readJavaCommand(defaultValue);
  }

  public synchronized int readLaunchJvmXmsMiB(int defaultValue) {
    return stores.launchJvmStore.readXmsMiB(defaultValue);
  }

  public synchronized int readLaunchJvmXmxMiB(int defaultValue) {
    return stores.launchJvmStore.readXmxMiB(defaultValue);
  }

  public synchronized String readLaunchJvmGc(String defaultValue) {
    return stores.launchJvmStore.readGc(defaultValue);
  }

  public synchronized List<String> readLaunchJvmArgs(List<String> defaultValue) {
    return stores.launchJvmStore.readArgs(defaultValue);
  }

  public synchronized void rememberLaunchJvmJavaCommand(String javaCommand) {
    stores.launchJvmStore.rememberJavaCommand(javaCommand);
  }

  public synchronized void rememberLaunchJvmXmsMiB(int xmsMiB) {
    stores.launchJvmStore.rememberXmsMiB(xmsMiB);
  }

  public synchronized void rememberLaunchJvmXmxMiB(int xmxMiB) {
    stores.launchJvmStore.rememberXmxMiB(xmxMiB);
  }

  public synchronized void rememberLaunchJvmGc(String gc) {
    stores.launchJvmStore.rememberGc(gc);
  }

  public synchronized void rememberLaunchJvmArgs(List<String> args) {
    stores.launchJvmStore.rememberArgs(args);
  }

  public synchronized boolean readCtcpAutoRepliesEnabled(boolean defaultValue) {
    return stores.uiStores.ctcpAutoReplyStore.readEnabled(defaultValue);
  }

  public synchronized boolean readCtcpAutoReplyVersionEnabled(boolean defaultValue) {
    return stores.uiStores.ctcpAutoReplyStore.readVersionEnabled(defaultValue);
  }

  public synchronized boolean readCtcpAutoReplyPingEnabled(boolean defaultValue) {
    return stores.uiStores.ctcpAutoReplyStore.readPingEnabled(defaultValue);
  }

  public synchronized boolean readCtcpAutoReplyTimeEnabled(boolean defaultValue) {
    return stores.uiStores.ctcpAutoReplyStore.readTimeEnabled(defaultValue);
  }

  public synchronized void rememberUserCommandAliases(List<UserCommandAlias> aliases) {
    stores.userCommandStore.rememberAliases(aliases);
  }

  public synchronized void rememberUnknownCommandAsRawEnabled(boolean enabled) {
    stores.userCommandStore.rememberUnknownCommandAsRawEnabled(enabled);
  }

  public synchronized void rememberAppDiagnosticsAssertjSwingEnabled(boolean enabled) {
    stores.uiStores.appDiagnosticsStore.rememberAssertjSwingEnabled(enabled);
  }

  public synchronized void rememberAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(
      boolean enabled) {
    stores.uiStores.appDiagnosticsStore.rememberAssertjSwingFreezeWatchdogEnabled(enabled);
  }

  public synchronized void rememberAppDiagnosticsAssertjSwingFreezeThresholdMs(int ms) {
    stores.uiStores.appDiagnosticsStore.rememberAssertjSwingFreezeThresholdMs(ms);
  }

  public synchronized void rememberAppDiagnosticsAssertjSwingWatchdogPollMs(int ms) {
    stores.uiStores.appDiagnosticsStore.rememberAssertjSwingWatchdogPollMs(ms);
  }

  public synchronized void rememberAppDiagnosticsAssertjSwingFallbackViolationReportMs(int ms) {
    stores.uiStores.appDiagnosticsStore.rememberAssertjSwingFallbackViolationReportMs(ms);
  }

  public synchronized void rememberAppDiagnosticsAssertjSwingIssuePlaySound(boolean enabled) {
    stores.uiStores.appDiagnosticsStore.rememberAssertjSwingIssuePlaySound(enabled);
  }

  public synchronized void rememberAppDiagnosticsAssertjSwingIssueShowNotification(
      boolean enabled) {
    stores.uiStores.appDiagnosticsStore.rememberAssertjSwingIssueShowNotification(enabled);
  }

  public synchronized void rememberAppDiagnosticsJhiccupEnabled(boolean enabled) {
    stores.uiStores.appDiagnosticsStore.rememberJhiccupEnabled(enabled);
  }

  public synchronized void rememberAppDiagnosticsJhiccupJarPath(String jarPath) {
    stores.uiStores.appDiagnosticsStore.rememberJhiccupJarPath(jarPath);
  }

  public synchronized void rememberAppDiagnosticsJhiccupJavaCommand(String javaCommand) {
    stores.uiStores.appDiagnosticsStore.rememberJhiccupJavaCommand(javaCommand);
  }

  public synchronized void rememberAppDiagnosticsJhiccupArgs(List<String> args) {
    stores.uiStores.appDiagnosticsStore.rememberJhiccupArgs(args);
  }

  public synchronized void rememberIrcEventNotificationRules(List<IrcEventNotificationRule> rules) {
    stores.uiStores.notificationStore.rememberIrcEventRules(rules);
  }

  public synchronized Map<String, List<InterceptorDefinition>> readInterceptorDefinitions() {
    return stores.interceptorStore.readDefinitions();
  }

  public synchronized void rememberInterceptorDefinitions(
      Map<String, List<InterceptorDefinition>> defsByServer) {
    stores.interceptorStore.rememberDefinitions(defsByServer);
  }

  // --- Chat logging / history persistence (ircafe.logging.*) ---

  public synchronized boolean readChatLoggingEnabled(boolean defaultValue) {
    return stores.chatLoggingStore.readEnabled(defaultValue);
  }

  public synchronized void rememberChatLoggingEnabled(boolean enabled) {
    stores.chatLoggingStore.rememberEnabled(enabled);
  }

  public synchronized void rememberChatLoggingLogSoftIgnoredLines(boolean enabled) {
    stores.chatLoggingStore.rememberLogSoftIgnoredLines(enabled);
  }

  public synchronized void rememberChatLoggingRedactionAuditEnabled(boolean enabled) {
    stores.chatLoggingStore.rememberRedactionAuditEnabled(enabled);
  }

  public synchronized void rememberChatLoggingLogPrivateMessages(boolean enabled) {
    stores.chatLoggingStore.rememberLogPrivateMessages(enabled);
  }

  public synchronized void rememberChatLoggingSavePrivateMessageList(boolean enabled) {
    stores.chatLoggingStore.rememberSavePrivateMessageList(enabled);
  }

  public synchronized void rememberChatLoggingDbFileBaseName(String fileBaseName) {
    stores.chatLoggingStore.rememberDbFileBaseName(fileBaseName);
  }

  public synchronized void rememberChatLoggingDbNextToRuntimeConfig(boolean nextToRuntimeConfig) {
    stores.chatLoggingStore.rememberDbNextToRuntimeConfig(nextToRuntimeConfig);
  }

  public synchronized void rememberChatLoggingKeepForever(boolean keepForever) {
    stores.chatLoggingStore.rememberKeepForever(keepForever);
  }

  public synchronized void rememberChatLoggingRetentionDays(int retentionDays) {
    stores.chatLoggingStore.rememberRetentionDays(retentionDays);
  }

  public synchronized void rememberChatLoggingWriterQueueMax(int writerQueueMax) {
    stores.chatLoggingStore.rememberWriterQueueMax(writerQueueMax);
  }

  public synchronized void rememberChatLoggingWriterBatchSize(int writerBatchSize) {
    stores.chatLoggingStore.rememberWriterBatchSize(writerBatchSize);
  }

  public synchronized void rememberImageEmbedsEnabled(boolean enabled) {
    stores.uiStores.embedStore.rememberImageEmbedsEnabled(enabled);
  }

  public synchronized void rememberImageEmbedsCollapsedByDefault(boolean collapsed) {
    stores.uiStores.embedStore.rememberImageEmbedsCollapsedByDefault(collapsed);
  }

  public synchronized void rememberImageEmbedsMaxWidthPx(int maxWidthPx) {
    stores.uiStores.embedStore.rememberImageEmbedsMaxWidthPx(maxWidthPx);
  }

  public synchronized void rememberImageEmbedsMaxHeightPx(int maxHeightPx) {
    stores.uiStores.embedStore.rememberImageEmbedsMaxHeightPx(maxHeightPx);
  }

  public synchronized void rememberImageEmbedsAnimateGifs(boolean animate) {
    stores.uiStores.embedStore.rememberImageEmbedsAnimateGifs(animate);
  }

  public synchronized void rememberLinkPreviewsEnabled(boolean enabled) {
    stores.uiStores.embedStore.rememberLinkPreviewsEnabled(enabled);
  }

  public synchronized void rememberLinkPreviewsCollapsedByDefault(boolean collapsed) {
    stores.uiStores.embedStore.rememberLinkPreviewsCollapsedByDefault(collapsed);
  }

  public synchronized void rememberEmbedCardStyle(String styleToken) {
    stores.uiStores.embedStore.rememberEmbedCardStyle(styleToken);
  }

  /** Reads advanced embed/link loading policy settings under {@code ircafe.ui.embedLoadPolicy}. */
  public synchronized EmbedLoadPolicySnapshot readEmbedLoadPolicy() {
    return stores.uiStores.embedLoadPolicyStore.read();
  }

  /**
   * Persists advanced embed/link loading policy settings under {@code ircafe.ui.embedLoadPolicy}.
   */
  public synchronized void rememberEmbedLoadPolicy(EmbedLoadPolicySnapshot snapshot) {
    stores.uiStores.embedLoadPolicyStore.remember(snapshot);
  }

  public synchronized void rememberPresenceFoldsEnabled(boolean enabled) {
    stores.uiStores.chatBehaviorStore.rememberPresenceFoldsEnabled(enabled);
  }

  public synchronized void rememberDefaultQuitMessage(String message) {
    stores.uiStores.chatBehaviorStore.rememberDefaultQuitMessage(message);
  }

  public synchronized void rememberCtcpRequestsInActiveTargetEnabled(boolean enabled) {
    stores.uiStores.chatBehaviorStore.rememberCtcpRequestsInActiveTargetEnabled(enabled);
  }

  public synchronized void rememberNickCompletionCycleWithTabEnabled(boolean enabled) {
    stores.uiStores.chatBehaviorStore.rememberNickCompletionCycleWithTabEnabled(enabled);
  }

  public synchronized void rememberNickCompletionAppendAddressSuffixEnabled(boolean enabled) {
    stores.uiStores.chatBehaviorStore.rememberNickCompletionAppendAddressSuffixEnabled(enabled);
  }

  public synchronized void rememberCtcpAutoRepliesEnabled(boolean enabled) {
    stores.uiStores.ctcpAutoReplyStore.rememberEnabled(enabled);
  }

  public synchronized void rememberCtcpAutoReplyVersionEnabled(boolean enabled) {
    stores.uiStores.ctcpAutoReplyStore.rememberVersionEnabled(enabled);
  }

  public synchronized void rememberCtcpAutoReplyPingEnabled(boolean enabled) {
    stores.uiStores.ctcpAutoReplyStore.rememberPingEnabled(enabled);
  }

  public synchronized void rememberCtcpAutoReplyTimeEnabled(boolean enabled) {
    stores.uiStores.ctcpAutoReplyStore.rememberTimeEnabled(enabled);
  }

  public synchronized void rememberTypingIndicatorsEnabled(boolean enabled) {
    stores.uiStores.chatBehaviorStore.rememberTypingIndicatorsEnabled(enabled);
  }

  public synchronized void rememberTypingIndicatorsReceiveEnabled(boolean enabled) {
    stores.uiStores.chatBehaviorStore.rememberTypingIndicatorsReceiveEnabled(enabled);
  }

  public synchronized void rememberTypingTreeIndicatorStyle(String style) {
    stores.uiStores.chatBehaviorStore.rememberTypingTreeIndicatorStyle(style);
  }

  public synchronized void rememberTypingIndicatorsTreeEnabled(boolean enabled) {
    stores.uiStores.chatBehaviorStore.rememberTypingIndicatorsTreeEnabled(enabled);
  }

  public synchronized void rememberTypingIndicatorsUsersListEnabled(boolean enabled) {
    stores.uiStores.chatBehaviorStore.rememberTypingIndicatorsUsersListEnabled(enabled);
  }

  public synchronized void rememberMatrixUserListNameDisplayMode(String mode) {
    stores.uiStores.chatBehaviorStore.rememberMatrixUserListNameDisplayMode(mode);
  }

  public synchronized void rememberTypingIndicatorsTranscriptEnabled(boolean enabled) {
    stores.uiStores.chatBehaviorStore.rememberTypingIndicatorsTranscriptEnabled(enabled);
  }

  public synchronized void rememberTypingIndicatorsSendSignalEnabled(boolean enabled) {
    stores.uiStores.chatBehaviorStore.rememberTypingIndicatorsSendSignalEnabled(enabled);
  }

  public synchronized int readServerTreeUnreadBadgeScalePercent(int defaultValue) {
    return stores.uiStores.chatBehaviorStore.readServerTreeUnreadBadgeScalePercent(defaultValue);
  }

  public synchronized void rememberServerTreeUnreadBadgeScalePercent(int percent) {
    stores.uiStores.chatBehaviorStore.rememberServerTreeUnreadBadgeScalePercent(percent);
  }

  public synchronized void rememberSpellcheckEnabled(boolean enabled) {
    stores.uiStores.spellcheckStore.rememberEnabled(enabled);
  }

  public synchronized void rememberSpellcheckUnderlineEnabled(boolean enabled) {
    stores.uiStores.spellcheckStore.rememberUnderlineEnabled(enabled);
  }

  public synchronized void rememberSpellcheckSuggestOnTabEnabled(boolean enabled) {
    stores.uiStores.spellcheckStore.rememberSuggestOnTabEnabled(enabled);
  }

  public synchronized void rememberSpellcheckHoverSuggestionsEnabled(boolean enabled) {
    stores.uiStores.spellcheckStore.rememberHoverSuggestionsEnabled(enabled);
  }

  public synchronized void rememberSpellcheckCompletionPreset(String preset) {
    stores.uiStores.spellcheckStore.rememberCompletionPreset(preset);
  }

  public synchronized void rememberSpellcheckCustomMinPrefixCompletionTokenLength(int value) {
    stores.uiStores.spellcheckStore.rememberCustomMinPrefixCompletionTokenLength(value);
  }

  public synchronized void rememberSpellcheckCustomMaxPrefixCompletionExtraChars(int value) {
    stores.uiStores.spellcheckStore.rememberCustomMaxPrefixCompletionExtraChars(value);
  }

  public synchronized void rememberSpellcheckCustomMaxPrefixLexiconCandidates(int value) {
    stores.uiStores.spellcheckStore.rememberCustomMaxPrefixLexiconCandidates(value);
  }

  public synchronized void rememberSpellcheckCustomPrefixCompletionBonusScore(int value) {
    stores.uiStores.spellcheckStore.rememberCustomPrefixCompletionBonusScore(value);
  }

  public synchronized void rememberSpellcheckCustomSourceOrderWeight(int value) {
    stores.uiStores.spellcheckStore.rememberCustomSourceOrderWeight(value);
  }

  public synchronized void rememberSpellcheckLanguageTag(String languageTag) {
    stores.uiStores.spellcheckStore.rememberLanguageTag(languageTag);
  }

  public synchronized void rememberSpellcheckCustomDictionary(List<String> words) {
    stores.uiStores.spellcheckStore.rememberCustomDictionary(words);
  }

  /**
   * Reads persisted IRCv3 STS policy snapshots under {@code ircafe.ircv3.stsPolicies}.
   *
   * <p>Entries with invalid hosts or missing/invalid expiry are ignored.
   */
  public synchronized Map<String, Ircv3StsPolicyConfigPort.StsPolicySnapshot>
      readIrcv3StsPolicies() {
    return stores.ircv3Stores.stsPolicyStore.readPolicies();
  }

  /** Persists one IRCv3 STS policy snapshot under {@code ircafe.ircv3.stsPolicies.<host>}. */
  public synchronized void rememberIrcv3StsPolicy(
      String host,
      long expiresAtEpochMs,
      Integer port,
      boolean preload,
      long durationSeconds,
      String rawValue) {
    stores.ircv3Stores.stsPolicyStore.rememberPolicy(
        host, expiresAtEpochMs, port, preload, durationSeconds, rawValue);
  }

  /** Removes a persisted IRCv3 STS policy snapshot from {@code ircafe.ircv3.stsPolicies}. */
  public synchronized void forgetIrcv3StsPolicy(String host) {
    stores.ircv3Stores.stsPolicyStore.forgetPolicy(host);
  }

  /**
   * Reads persisted IRCv3 capability request overrides under {@code ircafe.ui.ircv3Capabilities}.
   *
   * <p>Keys are normalized to lowercase, values are booleans. Missing/invalid entries are ignored.
   */
  public synchronized Map<String, Boolean> readIrcv3Capabilities() {
    return stores.ircv3Stores.capabilityStore.readCapabilities();
  }

  /**
   * Returns whether a given IRCv3 capability should be requested, falling back to {@code
   * defaultEnabled} when no explicit override is present.
   */
  public synchronized boolean isIrcv3CapabilityEnabled(String capability, boolean defaultEnabled) {
    return stores.ircv3Stores.capabilityStore.isCapabilityEnabled(capability, defaultEnabled);
  }

  /**
   * Persists an IRCv3 capability request override under {@code ircafe.ui.ircv3Capabilities}.
   *
   * <p>Default behavior is "enabled", so enabled values are removed to keep YAML concise.
   */
  public synchronized void rememberIrcv3CapabilityEnabled(String capability, boolean enabled) {
    stores.ircv3Stores.capabilityStore.rememberCapabilityEnabled(capability, enabled);
  }

  // --- WeeChat-style filters (ircafe.ui.filters.*) ---

  public synchronized void rememberFiltersEnabledByDefault(boolean enabled) {
    stores.uiStores.filterStore.rememberEnabledByDefault(enabled);
  }

  public synchronized void rememberFilterPlaceholdersEnabledByDefault(boolean enabled) {
    stores.uiStores.filterStore.rememberPlaceholdersEnabledByDefault(enabled);
  }

  public synchronized void rememberFilterPlaceholdersCollapsedByDefault(boolean collapsed) {
    stores.uiStores.filterStore.rememberPlaceholdersCollapsedByDefault(collapsed);
  }

  public synchronized void rememberFilterPlaceholderMaxPreviewLines(int maxLines) {
    stores.uiStores.filterStore.rememberPlaceholderMaxPreviewLines(maxLines);
  }

  public synchronized void rememberFilterPlaceholderMaxLinesPerRun(int maxLines) {
    stores.uiStores.filterStore.rememberPlaceholderMaxLinesPerRun(maxLines);
  }

  public synchronized void rememberFilterPlaceholderTooltipMaxTags(int maxTags) {
    stores.uiStores.filterStore.rememberPlaceholderTooltipMaxTags(maxTags);
  }

  public synchronized void rememberFilterHistoryPlaceholderMaxRunsPerBatch(int maxRuns) {
    stores.uiStores.filterStore.rememberHistoryPlaceholderMaxRunsPerBatch(maxRuns);
  }

  public synchronized void rememberFilterHistoryPlaceholdersEnabledByDefault(boolean enabled) {
    stores.uiStores.filterStore.rememberHistoryPlaceholdersEnabledByDefault(enabled);
  }

  public synchronized void rememberFilterRules(List<FilterRule> rules) {
    stores.uiStores.filterStore.rememberRules(rules);
  }

  public synchronized void rememberFilterOverrides(List<FilterScopeOverride> overrides) {
    stores.uiStores.filterStore.rememberOverrides(overrides);
  }

  public synchronized void rememberNickColoringEnabled(boolean enabled) {
    stores.uiStores.nickColorStore.rememberColoringEnabled(enabled);
  }

  public synchronized void rememberNickColorMinContrast(double minContrast) {
    stores.uiStores.nickColorStore.rememberMinContrast(minContrast);
  }

  public synchronized void rememberTimestampsEnabled(boolean enabled) {
    stores.uiStores.timestampStore.rememberEnabled(enabled);
  }

  public synchronized void rememberTimestampFormat(String format) {
    stores.uiStores.timestampStore.rememberFormat(format);
  }

  public synchronized void rememberTimestampsIncludeChatMessages(boolean includeChatMessages) {
    stores.uiStores.timestampStore.rememberIncludeChatMessages(includeChatMessages);
  }

  public synchronized void rememberTimestampsIncludePresenceMessages(
      boolean includePresenceMessages) {
    stores.uiStores.timestampStore.rememberIncludePresenceMessages(includePresenceMessages);
  }

  public synchronized void rememberChatHistoryInitialLoadLines(int lines) {
    stores.uiStores.chatHistoryStore.rememberInitialLoadLines(lines);
  }

  public synchronized void rememberChatHistoryPageSize(int pageSize) {
    stores.uiStores.chatHistoryStore.rememberPageSize(pageSize);
  }

  public synchronized void rememberChatHistoryAutoLoadWheelDebounceMs(int debounceMs) {
    stores.uiStores.chatHistoryStore.rememberAutoLoadWheelDebounceMs(debounceMs);
  }

  public synchronized void rememberChatHistoryLoadOlderChunkSize(int chunkSize) {
    stores.uiStores.chatHistoryStore.rememberLoadOlderChunkSize(chunkSize);
  }

  public synchronized void rememberChatHistoryLoadOlderChunkDelayMs(int chunkDelayMs) {
    stores.uiStores.chatHistoryStore.rememberLoadOlderChunkDelayMs(chunkDelayMs);
  }

  public synchronized void rememberChatHistoryLoadOlderChunkEdtBudgetMs(int chunkEdtBudgetMs) {
    stores.uiStores.chatHistoryStore.rememberLoadOlderChunkEdtBudgetMs(chunkEdtBudgetMs);
  }

  public synchronized void rememberChatHistoryDeferRichTextDuringBatch(boolean enabled) {
    stores.uiStores.chatHistoryStore.rememberDeferRichTextDuringBatch(enabled);
  }

  /**
   * Reads {@code ircafe.ui.chatSmoothWheelScrollingEnabled} from runtime config.
   *
   * <p>Returns {@code defaultValue} when the key is missing or invalid.
   */
  public synchronized boolean readChatSmoothWheelScrollingEnabled(boolean defaultValue) {
    return stores.uiStores.chatHistoryStore.readSmoothWheelScrollingEnabled(defaultValue);
  }

  public synchronized void rememberChatSmoothWheelScrollingEnabled(boolean enabled) {
    stores.uiStores.chatHistoryStore.rememberSmoothWheelScrollingEnabled(enabled);
  }

  public synchronized boolean readChatHistoryLockViewportDuringLoadOlder(boolean defaultValue) {
    return stores.uiStores.chatHistoryStore.readLockViewportDuringLoadOlder(defaultValue);
  }

  public synchronized void rememberChatHistoryLockViewportDuringLoadOlder(boolean enabled) {
    stores.uiStores.chatHistoryStore.rememberLockViewportDuringLoadOlder(enabled);
  }

  public synchronized void rememberChatHistoryRemoteRequestTimeoutSeconds(int seconds) {
    stores.uiStores.chatHistoryStore.rememberRemoteRequestTimeoutSeconds(seconds);
  }

  public synchronized void rememberChatHistoryRemoteZncPlaybackTimeoutSeconds(int seconds) {
    stores.uiStores.chatHistoryStore.rememberRemoteZncPlaybackTimeoutSeconds(seconds);
  }

  public synchronized void rememberChatHistoryRemoteZncPlaybackWindowMinutes(int minutes) {
    stores.uiStores.chatHistoryStore.rememberRemoteZncPlaybackWindowMinutes(minutes);
  }

  public synchronized void rememberCommandHistoryMaxSize(int maxSize) {
    stores.uiStores.chatHistoryStore.rememberCommandHistoryMaxSize(maxSize);
  }

  public synchronized void rememberChatTranscriptMaxLinesPerTarget(int maxLines) {
    stores.uiStores.chatHistoryStore.rememberTranscriptMaxLinesPerTarget(maxLines);
  }

  public synchronized void rememberClientLineColorEnabled(boolean enabled) {
    stores.uiStores.outgoingMessageStore.rememberClientLineColorEnabled(enabled);
  }

  public synchronized void rememberClientLineColor(String hex) {
    stores.uiStores.outgoingMessageStore.rememberClientLineColor(hex);
  }

  public synchronized void rememberOutgoingDeliveryIndicatorsEnabled(boolean enabled) {
    stores.uiStores.outgoingMessageStore.rememberOutgoingDeliveryIndicatorsEnabled(enabled);
  }

  public synchronized void rememberServerTreeNotificationBadgesEnabled(boolean enabled) {
    stores.uiStores.chatBehaviorStore.rememberServerTreeNotificationBadgesEnabled(enabled);
  }

  public synchronized void rememberUserhostDiscoveryEnabled(boolean enabled) {
    stores.uiStores.userLookupStore.rememberUserhostDiscoveryEnabled(enabled);
  }

  public synchronized void rememberUserhostMinIntervalSeconds(int seconds) {
    stores.uiStores.userLookupStore.rememberUserhostMinIntervalSeconds(seconds);
  }

  public synchronized void rememberUserhostMaxCommandsPerMinute(int maxPerMinute) {
    stores.uiStores.userLookupStore.rememberUserhostMaxCommandsPerMinute(maxPerMinute);
  }

  public synchronized void rememberUserhostNickCooldownMinutes(int minutes) {
    stores.uiStores.userLookupStore.rememberUserhostNickCooldownMinutes(minutes);
  }

  public synchronized void rememberUserhostMaxNicksPerCommand(int maxNicks) {
    stores.uiStores.userLookupStore.rememberUserhostMaxNicksPerCommand(maxNicks);
  }

  public synchronized void rememberMonitorIsonPollIntervalSeconds(int seconds) {
    stores.uiStores.userLookupStore.rememberMonitorIsonPollIntervalSeconds(seconds);
  }

  // --- User info enrichment fallback (ircafe.ui.userInfoEnrichment.*) ---

  public synchronized void rememberUserInfoEnrichmentEnabled(boolean enabled) {
    stores.uiStores.userLookupStore.rememberUserInfoEnrichmentEnabled(enabled);
  }

  public synchronized void rememberUserInfoEnrichmentWhoisFallbackEnabled(boolean enabled) {
    stores.uiStores.userLookupStore.rememberUserInfoEnrichmentWhoisFallbackEnabled(enabled);
  }

  public synchronized void rememberUserInfoEnrichmentUserhostMinIntervalSeconds(int seconds) {
    stores.uiStores.userLookupStore.rememberUserInfoEnrichmentUserhostMinIntervalSeconds(seconds);
  }

  public synchronized void rememberUserInfoEnrichmentUserhostMaxCommandsPerMinute(
      int maxPerMinute) {
    stores.uiStores.userLookupStore.rememberUserInfoEnrichmentUserhostMaxCommandsPerMinute(
        maxPerMinute);
  }

  public synchronized void rememberUserInfoEnrichmentUserhostNickCooldownMinutes(int minutes) {
    stores.uiStores.userLookupStore.rememberUserInfoEnrichmentUserhostNickCooldownMinutes(minutes);
  }

  public synchronized void rememberUserInfoEnrichmentUserhostMaxNicksPerCommand(int maxNicks) {
    stores.uiStores.userLookupStore.rememberUserInfoEnrichmentUserhostMaxNicksPerCommand(maxNicks);
  }

  public synchronized void rememberUserInfoEnrichmentWhoisMinIntervalSeconds(int seconds) {
    stores.uiStores.userLookupStore.rememberUserInfoEnrichmentWhoisMinIntervalSeconds(seconds);
  }

  public synchronized void rememberUserInfoEnrichmentWhoisNickCooldownMinutes(int minutes) {
    stores.uiStores.userLookupStore.rememberUserInfoEnrichmentWhoisNickCooldownMinutes(minutes);
  }

  public synchronized void rememberUserInfoEnrichmentPeriodicRefreshEnabled(boolean enabled) {
    stores.uiStores.userLookupStore.rememberUserInfoEnrichmentPeriodicRefreshEnabled(enabled);
  }

  public synchronized void rememberUserInfoEnrichmentPeriodicRefreshIntervalSeconds(int seconds) {
    stores.uiStores.userLookupStore.rememberUserInfoEnrichmentPeriodicRefreshIntervalSeconds(
        seconds);
  }

  public synchronized void rememberUserInfoEnrichmentPeriodicRefreshNicksPerTick(int nicksPerTick) {
    stores.uiStores.userLookupStore.rememberUserInfoEnrichmentPeriodicRefreshNicksPerTick(
        nicksPerTick);
  }

  public synchronized void rememberClientTlsTrustAllCertificates(boolean trustAllCertificates) {
    stores.connectionStores.clientSettingsStore.rememberTlsTrustAllCertificates(
        trustAllCertificates);
  }

  public synchronized void rememberClientHeartbeat(IrcProperties.Heartbeat heartbeat) {
    stores.connectionStores.clientSettingsStore.rememberHeartbeat(heartbeat);
  }

  public synchronized void rememberClientFloodProtection(
      IrcProperties.FloodProtection floodProtection) {
    stores.connectionStores.clientSettingsStore.rememberFloodProtection(floodProtection);
  }

  public synchronized void rememberClientProxy(IrcProperties.Proxy proxy) {
    stores.connectionStores.clientSettingsStore.rememberProxy(proxy);
  }

  public synchronized void rememberClientTranslation(IrcProperties.Client.Translation translation) {
    stores.connectionStores.clientSettingsStore.rememberTranslation(translation);
  }

  public synchronized void rememberIgnoreMask(String serverId, String mask) {
    stores.ignoreRulesStore.rememberIgnoreMask(serverId, mask);
  }

  public synchronized void rememberIgnoreMaskLevels(
      String serverId, String mask, List<String> levels) {
    stores.ignoreRulesStore.rememberIgnoreMaskLevels(serverId, mask, levels);
  }

  public synchronized void rememberIgnoreMaskChannels(
      String serverId, String mask, List<String> channels) {
    stores.ignoreRulesStore.rememberIgnoreMaskChannels(serverId, mask, channels);
  }

  public synchronized void rememberIgnoreMaskExpiresAt(
      String serverId, String mask, Long expiresAtEpochMs) {
    stores.ignoreRulesStore.rememberIgnoreMaskExpiresAt(serverId, mask, expiresAtEpochMs);
  }

  public synchronized void rememberIgnoreMaskPattern(
      String serverId, String mask, String pattern, String modeToken) {
    stores.ignoreRulesStore.rememberIgnoreMaskPattern(serverId, mask, pattern, modeToken);
  }

  public synchronized void rememberIgnoreMaskReplies(
      String serverId, String mask, boolean repliesEnabled) {
    stores.ignoreRulesStore.rememberIgnoreMaskReplies(serverId, mask, repliesEnabled);
  }

  public synchronized void forgetIgnoreMask(String serverId, String mask) {
    stores.ignoreRulesStore.forgetIgnoreMask(serverId, mask);
  }

  public synchronized void rememberSoftIgnoreMask(String serverId, String mask) {
    stores.ignoreRulesStore.rememberSoftIgnoreMask(serverId, mask);
  }

  public synchronized void forgetSoftIgnoreMask(String serverId, String mask) {
    stores.ignoreRulesStore.forgetSoftIgnoreMask(serverId, mask);
  }

  public synchronized void rememberHardIgnoreIncludesCtcp(boolean enabled) {
    stores.ignoreRulesStore.rememberHardIgnoreIncludesCtcp(enabled);
  }

  public synchronized void rememberSoftIgnoreIncludesCtcp(boolean enabled) {
    stores.ignoreRulesStore.rememberSoftIgnoreIncludesCtcp(enabled);
  }

  public synchronized void rememberNickColorOverrides(Map<String, String> overrides) {
    stores.uiStores.nickColorStore.rememberOverrides(overrides);
  }

  public synchronized void rememberSojuAutoConnectNetwork(
      String bouncerServerId, String networkName, boolean enabled) {
    stores.connectionStores.bouncerDiscoveryStore.rememberSojuAutoConnectNetwork(
        bouncerServerId, networkName, enabled);
  }

  public synchronized void rememberZncAutoConnectNetwork(
      String bouncerServerId, String networkName, boolean enabled) {
    stores.connectionStores.bouncerDiscoveryStore.rememberZncAutoConnectNetwork(
        bouncerServerId, networkName, enabled);
  }

  public synchronized Map<String, Map<String, Boolean>> readGenericBouncerAutoConnectRules() {
    return stores.connectionStores.bouncerDiscoveryStore.readGenericBouncerAutoConnectRules();
  }

  public synchronized void rememberGenericBouncerAutoConnectNetwork(
      String bouncerServerId, String networkName, boolean enabled) {
    stores.connectionStores.bouncerDiscoveryStore.rememberGenericBouncerAutoConnectNetwork(
        bouncerServerId, networkName, enabled);
  }

  public synchronized String readGenericBouncerLoginTemplate(String defaultValue) {
    return stores.connectionStores.bouncerDiscoveryStore.readGenericBouncerLoginTemplate(
        defaultValue);
  }

  public synchronized boolean readGenericBouncerPreferLoginHint(boolean defaultValue) {
    return stores.connectionStores.bouncerDiscoveryStore.readGenericBouncerPreferLoginHint(
        defaultValue);
  }

  public synchronized void rememberGenericBouncerLoginTemplate(String template) {
    stores.connectionStores.bouncerDiscoveryStore.rememberGenericBouncerLoginTemplate(template);
  }

  public synchronized void rememberGenericBouncerPreferLoginHint(boolean enabled) {
    stores.connectionStores.bouncerDiscoveryStore.rememberGenericBouncerPreferLoginHint(enabled);
  }
}

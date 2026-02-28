package cafe.woden.ircclient.config;

import java.awt.Font;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UI configuration.
 *
 * <p>These values can be overridden by the runtime YAML file imported via {@code
 * spring.config.import}.
 */
@ConfigurationProperties(prefix = "ircafe.ui")
public record UiProperties(
    String theme,
    String accentColor,
    Integer accentStrength,
    String density,
    Integer cornerRadius,
    Boolean uiFontOverrideEnabled,
    String uiFontFamily,
    Integer uiFontSize,
    String chatFontFamily,
    int chatFontSize,
    String chatThemePreset,
    String chatTimestampColor,
    String chatSystemColor,
    String chatMessageColor,
    String chatNoticeColor,
    String chatActionColor,
    String chatErrorColor,
    String chatPresenceColor,
    String chatMentionBgColor,
    Integer chatMentionStrength,
    Boolean autoConnectOnStart,
    Timestamps timestamps,
    Boolean chatMessageTimestampsEnabled,
    Integer chatHistoryInitialLoadLines,
    Integer chatHistoryPageSize,
    Integer chatHistoryAutoLoadWheelDebounceMs,
    Integer chatHistoryLoadOlderChunkSize,
    Integer chatHistoryLoadOlderChunkDelayMs,
    Integer chatHistoryLoadOlderChunkEdtBudgetMs,
    Boolean chatHistoryDeferRichTextDuringBatch,
    Integer chatHistoryRemoteRequestTimeoutSeconds,
    Integer chatHistoryRemoteZncPlaybackTimeoutSeconds,
    Integer chatHistoryRemoteZncPlaybackWindowMinutes,
    Integer commandHistoryMaxSize,
    Integer chatTranscriptMaxLinesPerTarget,
    String memoryUsageDisplayMode,
    Integer memoryUsageWarningNearMaxPercent,
    Boolean memoryUsageWarningTooltipEnabled,
    Boolean memoryUsageWarningToastEnabled,
    Boolean memoryUsageWarningPushyEnabled,
    Boolean memoryUsageWarningSoundEnabled,
    Boolean clientLineColorEnabled,
    String clientLineColor,
    /** Visual send-status indicators for outbound messages (pending spinner + confirmed dot). */
    Boolean outgoingDeliveryIndicatorsEnabled,
    /** Show unread/highlight notification badges in the server tree. */
    Boolean serverTreeNotificationBadgesEnabled,
    Boolean imageEmbedsEnabled,
    Boolean imageEmbedsCollapsedByDefault,
    Integer imageEmbedsMaxWidthPx,
    Integer imageEmbedsMaxHeightPx,
    Boolean imageEmbedsAnimateGifs,

    /**
     * Hostmask discovery settings.
     *
     * <p>IRCafe prefers the IRCv3 {@code userhost-in-names} capability (free, no extra traffic). If
     * hostmasks are still missing and you have hostmask-based ignore rules configured, IRCafe can
     * carefully use {@code USERHOST} with conservative anti-flood limits.
     */
    HostmaskDiscovery hostmaskDiscovery,

    /**
     * General user info enrichment (fallback) settings.
     *
     * <p>IRCafe prefers IRCv3 (account-notify, extended-join, account-tag, userhost-in-names)
     * because it is accurate and does not require extra traffic.
     *
     * <p>When IRCv3 support is missing, IRCafe can optionally and conservatively enrich user
     * metadata using {@code USERHOST} (hostmask + away flag) and, if explicitly enabled, {@code
     * WHOIS} (account name + away message). These are rate-limited and disabled by default.
     */
    UserInfoEnrichment userInfoEnrichment,

    /**
     * Monitor presence fallback settings.
     *
     * <p>Used only when IRC MONITOR is unavailable; IRCafe will poll presence via ISON.
     */
    MonitorFallback monitorFallback,
    Boolean linkPreviewsEnabled,
    Boolean linkPreviewsCollapsedByDefault,
    String embedCardStyle,
    Boolean nickColoringEnabled,
    Boolean presenceFoldsEnabled,

    /** Enable sending IRCv3 typing indicators. */
    Boolean typingIndicatorsEnabled,

    /** Enable displaying incoming IRCv3 typing indicators. */
    Boolean typingIndicatorsReceiveEnabled,

    /**
     * Style used for typing-activity markers in the server tree channel list.
     *
     * <p>Allowed values: {@code dots}, {@code keyboard}, {@code glow-dot}.
     */
    String typingTreeIndicatorStyle,

    /** Enable spell checking in the message input. */
    Boolean spellcheckEnabled,

    /** Enable misspelling underline/highlight rendering in the message input. */
    Boolean spellcheckUnderlineEnabled,

    /** Include dictionary suggestions in TAB completion popup. */
    Boolean spellcheckSuggestOnTabEnabled,

    /** BCP-47 language tag for spell checking (for example {@code en-US}). */
    String spellcheckLanguageTag,

    /** Custom per-user dictionary words that should not be marked misspelled. */
    List<String> spellcheckCustomDictionary,

    /**
     * Completion preset for dictionary-backed TAB suggestions.
     *
     * <p>Allowed values: {@code android-like}, {@code standard}, {@code conservative}, {@code
     * aggressive}, {@code custom}.
     */
    String spellcheckCompletionPreset,

    /** Custom minimum token length required before prefix completions are considered. */
    Integer spellcheckCustomMinPrefixCompletionTokenLength,

    /** Custom maximum number of extra characters allowed for a prefix completion candidate. */
    Integer spellcheckCustomMaxPrefixCompletionExtraChars,

    /** Custom cap for prefix lexicon candidates considered per lookup. */
    Integer spellcheckCustomMaxPrefixLexiconCandidates,

    /** Custom score bonus that boosts exact prefix completions. */
    Integer spellcheckCustomPrefixCompletionBonusScore,

    /** Custom source-order penalty weight for later suggestions in upstream lists. */
    Integer spellcheckCustomSourceOrderWeight,

    /**
     * If enabled, inbound CTCP requests are rendered into the currently active chat target (same
     * server). If disabled, they are routed to their origin target (channel/PM) instead.
     */
    Boolean ctcpRequestsInActiveTargetEnabled,

    /**
     * User-configured keyword / regex notification rules.
     *
     * <p>These are evaluated only for channel messages.
     */
    List<NotificationRuleProperties> notificationRules,

    /** User-configured IRC event notifications (kicks/modes/invites/etc). */
    List<IrcEventNotificationRuleProperties> ircEventNotificationRules,

    /** Cooldown (seconds) to dedupe repeated rule-match notifications per channel + rule. */
    Integer notificationRuleCooldownSeconds,
    double nickColorMinContrast,
    List<String> nickColors,
    Map<String, String> nickColorOverrides,
    Filters filters,
    Layout layout,
    AppDiagnostics appDiagnostics,
    Tray tray) {

  /** App defaults used when no runtime overrides exist. */
  public static final String DEFAULT_THEME = "darcula";

  /** IRCafe brand accent (cobalt). */
  public static final String DEFAULT_ACCENT_COLOR = "#2D6BFF";

  /** 0..100 blend between theme accent and chosen accent. */
  public static final int DEFAULT_ACCENT_STRENGTH = 100;

  /**
   * System tray integration.
   *
   * <p>Defaults are intentionally conservative: if the platform supports a tray, we show it, but
   * the window close button still exits by default.
   */
  public record Tray(
      Boolean enabled,
      Boolean closeToTray,
      Boolean minimizeToTray,
      Boolean startMinimized,

      /** Show desktop notifications (balloons/toasts) for channel highlights. */
      Boolean notifyHighlights,

      /** Show desktop notifications (balloons/toasts) for private messages. */
      Boolean notifyPrivateMessages,

      /** Show desktop notifications (balloons/toasts) for connection state changes. */
      Boolean notifyConnectionState,

      /** Only show desktop notifications when the main window is not focused/active. */
      Boolean notifyOnlyWhenUnfocused,

      /** Only show desktop notifications when the main window is minimized or hidden to tray. */
      Boolean notifyOnlyWhenMinimizedOrHidden,

      /** If true, suppress notifications for the currently active buffer/target. */
      Boolean notifySuppressWhenTargetActive,

      /**
       * On Linux desktops, prefer the D-Bus notification API (org.freedesktop.Notifications).
       *
       * <p>This enables click-to-open behavior on desktops that support notification actions. If
       * unsupported, IRCafe will silently fall back to {@code notify-send}.
       */
      Boolean linuxDbusActionsEnabled,

      /**
       * Desktop notification backend mode.
       *
       * <p>Allowed values: {@code auto}, {@code native-only}, {@code two-slices-only}.
       */
      String notificationBackend,

      /** Play a sound alongside desktop notifications. */
      Boolean notificationSoundsEnabled,

      /** The built-in sound id (BuiltInSound enum name) used for notifications. */
      String notificationSound,

      /**
       * If true, play a custom sound file from the runtime config directory instead of a bundled
       * sound.
       */
      Boolean notificationSoundUseCustom,

      /**
       * Relative path under the runtime config directory for the custom sound file (e.g.
       * "sounds/my.mp3").
       */
      String notificationSoundCustomPath) {
    public Tray {
      if (enabled == null) enabled = true;
      if (closeToTray == null) closeToTray = false;
      if (minimizeToTray == null) minimizeToTray = false;
      if (startMinimized == null) startMinimized = false;

      // Defaults are intentionally minimal: highlights + DMs are on, connectivity is off.
      if (notifyHighlights == null) notifyHighlights = true;
      if (notifyPrivateMessages == null) notifyPrivateMessages = true;
      if (notifyConnectionState == null) notifyConnectionState = false;

      // Default to notifying in both focused and unfocused states; users can narrow this.
      if (notifyOnlyWhenUnfocused == null) notifyOnlyWhenUnfocused = false;
      if (notifyOnlyWhenMinimizedOrHidden == null) notifyOnlyWhenMinimizedOrHidden = false;
      if (notifySuppressWhenTargetActive == null) notifySuppressWhenTargetActive = false;

      // Default to "on" - we only actually use D-Bus if the session supports actions.
      if (linuxDbusActionsEnabled == null) linuxDbusActionsEnabled = true;
      if (notificationBackend == null || notificationBackend.isBlank())
        notificationBackend = "auto";

      // Keep Phase-2 behavior (on) unless explicitly disabled.
      if (notificationSoundsEnabled == null) notificationSoundsEnabled = true;
      if (notificationSound == null || notificationSound.isBlank()) notificationSound = "NOTIF_1";

      if (notificationSoundUseCustom == null) notificationSoundUseCustom = false;
      if (notificationSoundCustomPath != null && notificationSoundCustomPath.isBlank()) {
        notificationSoundCustomPath = null;
      }

      // If the user toggles custom on but no path exists, fall back to bundled.
      if (Boolean.TRUE.equals(notificationSoundUseCustom) && notificationSoundCustomPath == null) {
        notificationSoundUseCustom = false;
      }
    }
  }

  /**
   * WeeChat-style message filters.
   *
   * <p>Filters are evaluated at render time only. They never affect logging.
   */
  public record Filters(
      Boolean enabledByDefault,
      Boolean placeholdersEnabledByDefault,
      Boolean placeholdersCollapsedByDefault,
      Integer placeholderMaxPreviewLines,
      Integer placeholderMaxLinesPerRun,
      Integer placeholderTooltipMaxTags,
      Integer historyPlaceholderMaxRunsPerBatch,
      Boolean historyPlaceholdersEnabledByDefault,
      List<FilterRuleProperties> rules,
      List<FilterScopeOverrideProperties> overrides) {
    public Filters {
      if (enabledByDefault == null) enabledByDefault = true;
      if (placeholdersEnabledByDefault == null) placeholdersEnabledByDefault = true;
      if (placeholdersCollapsedByDefault == null) placeholdersCollapsedByDefault = true;

      if (placeholderMaxPreviewLines == null || placeholderMaxPreviewLines < 0) {
        placeholderMaxPreviewLines = 3;
      }
      if (placeholderMaxPreviewLines > 25) {
        placeholderMaxPreviewLines = 25;
      }

      if (placeholderMaxLinesPerRun == null || placeholderMaxLinesPerRun < 0) {
        placeholderMaxLinesPerRun = 250;
      }
      // 0 disables the cap (unbounded).
      if (placeholderMaxLinesPerRun > 50_000) {
        placeholderMaxLinesPerRun = 50_000;
      }

      if (placeholderTooltipMaxTags == null || placeholderTooltipMaxTags < 0) {
        placeholderTooltipMaxTags = 12;
      }
      // 0 disables tag listing in tooltips.
      if (placeholderTooltipMaxTags > 500) {
        placeholderTooltipMaxTags = 500;
      }

      if (historyPlaceholderMaxRunsPerBatch == null || historyPlaceholderMaxRunsPerBatch < 0) {
        historyPlaceholderMaxRunsPerBatch = 10;
      }
      // 0 disables the per-batch cap (unbounded placeholder/hint runs during history loads).
      if (historyPlaceholderMaxRunsPerBatch > 5_000) {
        historyPlaceholderMaxRunsPerBatch = 5_000;
      }

      if (historyPlaceholdersEnabledByDefault == null) {
        historyPlaceholdersEnabledByDefault = true;
      }

      rules = (rules == null) ? List.of() : rules.stream().filter(Objects::nonNull).toList();
      overrides =
          (overrides == null) ? List.of() : overrides.stream().filter(Objects::nonNull).toList();
    }
  }

  /**
   * Docking/layout defaults.
   *
   * <p>These sizes are used as a best-effort "first open" hint. After the user drags split
   * dividers, those new sizes are preserved by the split-pane lock logic.
   */
  public record Layout(Integer serverDockWidthPx, Integer userDockWidthPx) {
    public Layout {
      if (serverDockWidthPx == null || serverDockWidthPx <= 0) serverDockWidthPx = 280;
      if (userDockWidthPx == null || userDockWidthPx <= 0) userDockWidthPx = 240;
    }
  }

  /** Optional application diagnostics integrations shown under the "Application" tree node. */
  public record AppDiagnostics(AssertjSwing assertjSwing, Jhiccup jhiccup) {
    public AppDiagnostics {
      if (assertjSwing == null) {
        assertjSwing = new AssertjSwing(null, null, null, null, null, null, null);
      }
      if (jhiccup == null) jhiccup = new Jhiccup(null, null, null, null);
    }
  }

  /** AssertJ Swing integration + EDT freeze watchdog. */
  public record AssertjSwing(
      Boolean enabled,
      Boolean edtFreezeWatchdogEnabled,
      Integer edtFreezeThresholdMs,
      Integer edtWatchdogPollMs,
      Integer edtFallbackViolationReportMs,
      Boolean onIssuePlaySound,
      Boolean onIssueShowNotification) {
    public AssertjSwing {
      if (enabled == null) enabled = true;
      if (edtFreezeWatchdogEnabled == null) edtFreezeWatchdogEnabled = true;
      if (edtFreezeThresholdMs == null || edtFreezeThresholdMs < 500) edtFreezeThresholdMs = 2500;
      if (edtFreezeThresholdMs > 120_000) edtFreezeThresholdMs = 120_000;
      if (edtWatchdogPollMs == null || edtWatchdogPollMs < 100) edtWatchdogPollMs = 500;
      if (edtWatchdogPollMs > 10_000) edtWatchdogPollMs = 10_000;
      if (edtFallbackViolationReportMs == null || edtFallbackViolationReportMs < 250) {
        edtFallbackViolationReportMs = 5000;
      }
      if (edtFallbackViolationReportMs > 120_000) edtFallbackViolationReportMs = 120_000;
      if (onIssuePlaySound == null) onIssuePlaySound = false;
      if (onIssueShowNotification == null) onIssueShowNotification = false;
    }
  }

  /** External jHiccup process integration. */
  public record Jhiccup(Boolean enabled, String jarPath, String javaCommand, List<String> args) {
    public Jhiccup {
      if (enabled == null) enabled = false;
      if (jarPath != null && jarPath.isBlank()) jarPath = null;
      if (javaCommand == null || javaCommand.isBlank()) javaCommand = "java";
      args =
          (args == null)
              ? List.of()
              : args.stream()
                  .filter(Objects::nonNull)
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .toList();
    }
  }

  /**
   * Chat/status timestamp settings.
   *
   * <p>{@code format} uses Java time patterns (same general style as {@link
   * java.text.SimpleDateFormat}, but powered by {@link java.time.format.DateTimeFormatter}).
   */
  public record Timestamps(
      Boolean enabled,
      String format,
      Boolean includeChatMessages,
      Boolean includePresenceMessages) {
    public Timestamps {
      if (enabled == null) enabled = true;
      if (format == null || format.isBlank()) format = "HH:mm:ss";
      if (includeChatMessages == null) includeChatMessages = true;
      if (includePresenceMessages == null) includePresenceMessages = true;
    }
  }

  /**
   * Hostmask discovery configuration.
   *
   * <p>Defaults are intentionally conservative:
   *
   * <ul>
   *   <li>{@code userhostEnabled=true}
   *   <li>{@code userhostMinIntervalSeconds=7}
   *   <li>{@code userhostMaxCommandsPerMinute=6}
   *   <li>{@code userhostNickCooldownMinutes=30}
   *   <li>{@code userhostMaxNicksPerCommand=5} (most servers allow up to 5)
   * </ul>
   */
  public record HostmaskDiscovery(
      Boolean userhostEnabled,
      Integer userhostMinIntervalSeconds,
      Integer userhostMaxCommandsPerMinute,
      Integer userhostNickCooldownMinutes,
      Integer userhostMaxNicksPerCommand) {
    public HostmaskDiscovery {
      if (userhostEnabled == null) userhostEnabled = true;
      if (userhostMinIntervalSeconds == null || userhostMinIntervalSeconds <= 0)
        userhostMinIntervalSeconds = 7;
      if (userhostMaxCommandsPerMinute == null || userhostMaxCommandsPerMinute <= 0)
        userhostMaxCommandsPerMinute = 6;
      if (userhostNickCooldownMinutes == null || userhostNickCooldownMinutes <= 0)
        userhostNickCooldownMinutes = 30;
      if (userhostMaxNicksPerCommand == null || userhostMaxNicksPerCommand <= 0)
        userhostMaxNicksPerCommand = 5;
      if (userhostMaxNicksPerCommand > 5) userhostMaxNicksPerCommand = 5;
    }
  }

  /**
   * General user info enrichment configuration.
   *
   * <p>This is a best-effort fallback used when IRCv3 capabilities are unavailable or disabled.
   * Defaults are intentionally conservative and disabled by default:
   *
   * <ul>
   *   <li>{@code enabled=false}
   *   <li>{@code userhostMinIntervalSeconds=15}
   *   <li>{@code userhostMaxCommandsPerMinute=3}
   *   <li>{@code userhostNickCooldownMinutes=60}
   *   <li>{@code userhostMaxNicksPerCommand=5}
   *   <li>{@code whoisFallbackEnabled=false}
   *   <li>{@code whoisMinIntervalSeconds=45}
   *   <li>{@code whoisNickCooldownMinutes=120}
   *   <li>{@code periodicRefreshEnabled=false}
   *   <li>{@code periodicRefreshIntervalSeconds=300}
   *   <li>{@code periodicRefreshNicksPerTick=2}
   * </ul>
   */
  public record UserInfoEnrichment(
      Boolean enabled,
      Integer userhostMinIntervalSeconds,
      Integer userhostMaxCommandsPerMinute,
      Integer userhostNickCooldownMinutes,
      Integer userhostMaxNicksPerCommand,
      Boolean whoisFallbackEnabled,
      Integer whoisMinIntervalSeconds,
      Integer whoisNickCooldownMinutes,
      Boolean periodicRefreshEnabled,
      Integer periodicRefreshIntervalSeconds,
      Integer periodicRefreshNicksPerTick) {
    public UserInfoEnrichment {
      if (enabled == null) enabled = false;

      if (userhostMinIntervalSeconds == null || userhostMinIntervalSeconds <= 0)
        userhostMinIntervalSeconds = 15;
      if (userhostMaxCommandsPerMinute == null || userhostMaxCommandsPerMinute <= 0)
        userhostMaxCommandsPerMinute = 3;
      if (userhostNickCooldownMinutes == null || userhostNickCooldownMinutes <= 0)
        userhostNickCooldownMinutes = 60;
      if (userhostMaxNicksPerCommand == null || userhostMaxNicksPerCommand <= 0)
        userhostMaxNicksPerCommand = 5;
      if (userhostMaxNicksPerCommand > 5) userhostMaxNicksPerCommand = 5;

      // Must be explicitly enabled (off by default).
      if (whoisFallbackEnabled == null) whoisFallbackEnabled = false;

      if (whoisMinIntervalSeconds == null || whoisMinIntervalSeconds <= 0)
        whoisMinIntervalSeconds = 45;
      if (whoisNickCooldownMinutes == null || whoisNickCooldownMinutes <= 0)
        whoisNickCooldownMinutes = 120;

      if (periodicRefreshEnabled == null) periodicRefreshEnabled = false;
      if (periodicRefreshIntervalSeconds == null || periodicRefreshIntervalSeconds <= 0)
        periodicRefreshIntervalSeconds = 300;
      if (periodicRefreshNicksPerTick == null || periodicRefreshNicksPerTick <= 0)
        periodicRefreshNicksPerTick = 2;
      if (periodicRefreshNicksPerTick > 10) periodicRefreshNicksPerTick = 10;
    }
  }

  /**
   * Monitor fallback configuration (used when IRC MONITOR is unavailable).
   *
   * <p>Defaults:
   *
   * <ul>
   *   <li>{@code isonPollIntervalSeconds=30}
   * </ul>
   */
  public record MonitorFallback(Integer isonPollIntervalSeconds) {
    public MonitorFallback {
      if (isonPollIntervalSeconds == null || isonPollIntervalSeconds <= 0) {
        isonPollIntervalSeconds = 30;
      }
      if (isonPollIntervalSeconds < 5) isonPollIntervalSeconds = 5;
      if (isonPollIntervalSeconds > 600) isonPollIntervalSeconds = 600;
    }
  }

  public UiProperties {
    if (theme == null || theme.isBlank()) {
      theme = DEFAULT_THEME;
    }

    // Accent defaults to IRCafe cobalt. Users can disable the override explicitly by setting an
    // empty string.
    // (Runtime persistence removes the key when disabled.)
    if (accentColor == null) {
      accentColor = DEFAULT_ACCENT_COLOR;
    } else if (accentColor.isBlank()) {
      accentColor = null;
    } else {
      accentColor = normalizeHexOrNull(accentColor);
    }

    // Chat theme overrides (optional)
    if (chatThemePreset != null && chatThemePreset.isBlank()) chatThemePreset = null;
    if (chatThemePreset != null) chatThemePreset = chatThemePreset.trim();
    chatTimestampColor = normalizeHexOrNull(chatTimestampColor);
    chatSystemColor = normalizeHexOrNull(chatSystemColor);
    chatMessageColor = normalizeHexOrNull(chatMessageColor);
    chatNoticeColor = normalizeHexOrNull(chatNoticeColor);
    chatActionColor = normalizeHexOrNull(chatActionColor);
    chatErrorColor = normalizeHexOrNull(chatErrorColor);
    chatPresenceColor = normalizeHexOrNull(chatPresenceColor);
    chatMentionBgColor = normalizeHexOrNull(chatMentionBgColor);
    if (chatMentionStrength == null) chatMentionStrength = 35;
    chatMentionStrength = Math.max(0, Math.min(100, chatMentionStrength));

    if (accentStrength == null) accentStrength = DEFAULT_ACCENT_STRENGTH;
    if (accentStrength < 0) accentStrength = 0;
    if (accentStrength > 100) accentStrength = 100;

    // Global LAF tweaks (cheap wins).
    if (density == null || density.isBlank()) density = "auto";
    density = density.trim().toLowerCase(Locale.ROOT);
    if (!density.equals("auto")
        && !density.equals("compact")
        && !density.equals("cozy")
        && !density.equals("spacious")) {
      density = "auto";
    }

    if (cornerRadius == null) cornerRadius = 10;
    if (cornerRadius < 0) cornerRadius = 0;
    if (cornerRadius > 20) cornerRadius = 20;

    if (uiFontOverrideEnabled == null) {
      uiFontOverrideEnabled = false;
    }
    if (uiFontFamily == null || uiFontFamily.isBlank()) {
      uiFontFamily = "Dialog";
    } else {
      uiFontFamily = uiFontFamily.trim();
    }
    if (uiFontSize == null) {
      uiFontSize = 13;
    }
    if (uiFontSize < 8) uiFontSize = 8;
    if (uiFontSize > 48) uiFontSize = 48;

    if (chatFontFamily == null || chatFontFamily.isBlank()) {
      chatFontFamily = Font.MONOSPACED;
    }
    if (chatFontSize <= 0) {
      chatFontSize = 12;
    }

    // Default: true (preserve prior behavior where IRCafe auto-connects on startup).
    if (autoConnectOnStart == null) {
      autoConnectOnStart = true;
    }

    // Default: enabled (regular chat messages also include timestamp prefix).
    if (chatMessageTimestampsEnabled == null) {
      chatMessageTimestampsEnabled = true;
    }

    if (timestamps == null) {
      timestamps = new Timestamps(true, "HH:mm:ss", chatMessageTimestampsEnabled, true);
    } else if (timestamps.includeChatMessages() == null) {
      // Back-compat: if the new nested flag is absent, fall back to the legacy top-level flag.
      timestamps =
          new Timestamps(
              timestamps.enabled(),
              timestamps.format(),
              chatMessageTimestampsEnabled,
              timestamps.includePresenceMessages());
    }
    // History defaults: conservative initial load, generous paging.
    if (chatHistoryInitialLoadLines == null) {
      chatHistoryInitialLoadLines = 100;
    }
    if (chatHistoryInitialLoadLines < 0) {
      chatHistoryInitialLoadLines = 0;
    }

    if (chatHistoryPageSize == null || chatHistoryPageSize <= 0) {
      chatHistoryPageSize = 200;
    }
    if (chatHistoryAutoLoadWheelDebounceMs == null || chatHistoryAutoLoadWheelDebounceMs <= 0) {
      chatHistoryAutoLoadWheelDebounceMs = 2000;
    }
    if (chatHistoryAutoLoadWheelDebounceMs < 100) {
      chatHistoryAutoLoadWheelDebounceMs = 100;
    }
    if (chatHistoryAutoLoadWheelDebounceMs > 30_000) {
      chatHistoryAutoLoadWheelDebounceMs = 30_000;
    }
    if (chatHistoryLoadOlderChunkSize == null || chatHistoryLoadOlderChunkSize <= 0) {
      chatHistoryLoadOlderChunkSize = 20;
    }
    if (chatHistoryLoadOlderChunkSize < 1) {
      chatHistoryLoadOlderChunkSize = 1;
    }
    if (chatHistoryLoadOlderChunkSize > 500) {
      chatHistoryLoadOlderChunkSize = 500;
    }
    if (chatHistoryLoadOlderChunkDelayMs == null || chatHistoryLoadOlderChunkDelayMs < 0) {
      chatHistoryLoadOlderChunkDelayMs = 0;
    }
    if (chatHistoryLoadOlderChunkDelayMs > 1_000) {
      chatHistoryLoadOlderChunkDelayMs = 1_000;
    }
    if (chatHistoryLoadOlderChunkEdtBudgetMs == null || chatHistoryLoadOlderChunkEdtBudgetMs <= 0) {
      chatHistoryLoadOlderChunkEdtBudgetMs = 6;
    }
    if (chatHistoryLoadOlderChunkEdtBudgetMs < 1) {
      chatHistoryLoadOlderChunkEdtBudgetMs = 1;
    }
    if (chatHistoryLoadOlderChunkEdtBudgetMs > 33) {
      chatHistoryLoadOlderChunkEdtBudgetMs = 33;
    }
    if (chatHistoryDeferRichTextDuringBatch == null) {
      chatHistoryDeferRichTextDuringBatch = false;
    }
    if (chatHistoryRemoteRequestTimeoutSeconds == null
        || chatHistoryRemoteRequestTimeoutSeconds <= 0) {
      chatHistoryRemoteRequestTimeoutSeconds = 6;
    }
    if (chatHistoryRemoteRequestTimeoutSeconds < 1) {
      chatHistoryRemoteRequestTimeoutSeconds = 1;
    }
    if (chatHistoryRemoteRequestTimeoutSeconds > 120) {
      chatHistoryRemoteRequestTimeoutSeconds = 120;
    }
    if (chatHistoryRemoteZncPlaybackTimeoutSeconds == null
        || chatHistoryRemoteZncPlaybackTimeoutSeconds <= 0) {
      chatHistoryRemoteZncPlaybackTimeoutSeconds = 18;
    }
    if (chatHistoryRemoteZncPlaybackTimeoutSeconds < 1) {
      chatHistoryRemoteZncPlaybackTimeoutSeconds = 1;
    }
    if (chatHistoryRemoteZncPlaybackTimeoutSeconds > 300) {
      chatHistoryRemoteZncPlaybackTimeoutSeconds = 300;
    }
    if (chatHistoryRemoteZncPlaybackWindowMinutes == null
        || chatHistoryRemoteZncPlaybackWindowMinutes <= 0) {
      chatHistoryRemoteZncPlaybackWindowMinutes = 360;
    }
    if (chatHistoryRemoteZncPlaybackWindowMinutes < 1) {
      chatHistoryRemoteZncPlaybackWindowMinutes = 1;
    }
    if (chatHistoryRemoteZncPlaybackWindowMinutes > 1440) {
      chatHistoryRemoteZncPlaybackWindowMinutes = 1440;
    }
    // Live per-target transcript retention cap. 0 disables trimming.
    if (chatTranscriptMaxLinesPerTarget == null) {
      chatTranscriptMaxLinesPerTarget = 4000;
    }
    if (chatTranscriptMaxLinesPerTarget < 0) {
      chatTranscriptMaxLinesPerTarget = 0;
    }
    if (chatTranscriptMaxLinesPerTarget > 200_000) {
      chatTranscriptMaxLinesPerTarget = 200_000;
    }

    // Outgoing message color default: disabled.
    if (clientLineColorEnabled == null) {
      clientLineColorEnabled = false;
    }
    // Default outgoing message color if enabled but not set explicitly.
    clientLineColor = normalizeHexOrDefault(clientLineColor, "#6AA2FF");
    // Delivery indicators default: enabled.
    if (outgoingDeliveryIndicatorsEnabled == null) {
      outgoingDeliveryIndicatorsEnabled = true;
    }
    if (serverTreeNotificationBadgesEnabled == null) {
      serverTreeNotificationBadgesEnabled = true;
    }

    if (layout == null) {
      layout = new Layout(null, null);
    }

    if (appDiagnostics == null) {
      appDiagnostics = new AppDiagnostics(null, null);
    }

    // Image embeds default: disabled.
    if (imageEmbedsEnabled == null) {
      imageEmbedsEnabled = false;
    }

    // Image embeds collapsed-by-default default: false (preserve current behavior).
    if (imageEmbedsCollapsedByDefault == null) {
      imageEmbedsCollapsedByDefault = false;
    }

    // Image embed max width default: 0 (no extra cap).
    if (imageEmbedsMaxWidthPx == null || imageEmbedsMaxWidthPx <= 0) {
      imageEmbedsMaxWidthPx = 0;
    }

    // Image embed max height default: 0 (no extra cap).
    if (imageEmbedsMaxHeightPx == null || imageEmbedsMaxHeightPx <= 0) {
      imageEmbedsMaxHeightPx = 0;
    }

    // GIF animation default: enabled.
    if (imageEmbedsAnimateGifs == null) {
      imageEmbedsAnimateGifs = true;
    }

    // Hostmask discovery defaults.
    if (hostmaskDiscovery == null) {
      hostmaskDiscovery = new HostmaskDiscovery(true, 7, 6, 30, 5);
    }

    // User info enrichment defaults (disabled by default).
    if (userInfoEnrichment == null) {
      userInfoEnrichment =
          new UserInfoEnrichment(false, 15, 3, 60, 5, false, 45, 120, false, 300, 2);
    }

    // Monitor fallback defaults.
    if (monitorFallback == null) {
      monitorFallback = new MonitorFallback(30);
    }

    // Link previews default: disabled (privacy + extra network traffic).
    if (linkPreviewsEnabled == null) {
      linkPreviewsEnabled = false;
    }

    // Link previews collapsed-by-default default: false (preserve current behavior).
    if (linkPreviewsCollapsedByDefault == null) {
      linkPreviewsCollapsedByDefault = false;
    }
    embedCardStyle = normalizeEmbedCardStyle(embedCardStyle);
    // Nick coloring defaults.
    if (nickColorMinContrast <= 0) {
      nickColorMinContrast = 3.0;
    }
    // Default: enabled.
    if (nickColoringEnabled == null) {
      nickColoringEnabled = true;
    }

    // Presence fold default: enabled.
    if (presenceFoldsEnabled == null) {
      presenceFoldsEnabled = true;
    }

    // Typing indicators default: enabled.
    if (typingIndicatorsEnabled == null) {
      typingIndicatorsEnabled = true;
    }
    // Incoming typing indicators default: follows the main typing toggle.
    if (typingIndicatorsReceiveEnabled == null) {
      typingIndicatorsReceiveEnabled = typingIndicatorsEnabled;
    }
    typingTreeIndicatorStyle = normalizeTypingTreeIndicatorStyle(typingTreeIndicatorStyle);

    // Spellcheck defaults.
    if (spellcheckEnabled == null) {
      spellcheckEnabled = true;
    }
    if (spellcheckUnderlineEnabled == null) {
      spellcheckUnderlineEnabled = true;
    }
    if (spellcheckSuggestOnTabEnabled == null) {
      spellcheckSuggestOnTabEnabled = true;
    }
    spellcheckLanguageTag = normalizeSpellcheckLanguageTag(spellcheckLanguageTag);
    if (spellcheckCustomDictionary == null) {
      spellcheckCustomDictionary = List.of();
    } else {
      spellcheckCustomDictionary =
          spellcheckCustomDictionary.stream()
              .map(v -> Objects.toString(v, "").trim())
              .filter(v -> !v.isEmpty())
              .distinct()
              .toList();
    }
    spellcheckCompletionPreset = normalizeSpellcheckCompletionPreset(spellcheckCompletionPreset);
    if (spellcheckCustomMinPrefixCompletionTokenLength == null) {
      spellcheckCustomMinPrefixCompletionTokenLength = 2;
    }
    spellcheckCustomMinPrefixCompletionTokenLength =
        Math.max(2, Math.min(6, spellcheckCustomMinPrefixCompletionTokenLength));
    if (spellcheckCustomMaxPrefixCompletionExtraChars == null) {
      spellcheckCustomMaxPrefixCompletionExtraChars = 14;
    }
    spellcheckCustomMaxPrefixCompletionExtraChars =
        Math.max(4, Math.min(24, spellcheckCustomMaxPrefixCompletionExtraChars));
    if (spellcheckCustomMaxPrefixLexiconCandidates == null) {
      spellcheckCustomMaxPrefixLexiconCandidates = 96;
    }
    spellcheckCustomMaxPrefixLexiconCandidates =
        Math.max(16, Math.min(256, spellcheckCustomMaxPrefixLexiconCandidates));
    if (spellcheckCustomPrefixCompletionBonusScore == null) {
      spellcheckCustomPrefixCompletionBonusScore = 220;
    }
    spellcheckCustomPrefixCompletionBonusScore =
        Math.max(0, Math.min(400, spellcheckCustomPrefixCompletionBonusScore));
    if (spellcheckCustomSourceOrderWeight == null) {
      spellcheckCustomSourceOrderWeight = 6;
    }
    spellcheckCustomSourceOrderWeight =
        Math.max(0, Math.min(20, spellcheckCustomSourceOrderWeight));

    // CTCP request routing default: show in the currently active target.
    if (ctcpRequestsInActiveTargetEnabled == null) {
      ctcpRequestsInActiveTargetEnabled = true;
    }

    // Filter defaults.
    if (filters == null) {
      filters = new Filters(null, null, null, null, null, null, null, null, null, null);
    }

    if (notificationRules == null) {
      notificationRules = List.of();
    } else {
      notificationRules = notificationRules.stream().filter(Objects::nonNull).toList();
    }
    if (ircEventNotificationRules == null) {
      ircEventNotificationRules = IrcEventNotificationRuleProperties.defaultRules();
    } else {
      ircEventNotificationRules =
          ircEventNotificationRules.stream().filter(Objects::nonNull).toList();
    }
    if (nickColors == null) {
      nickColors = List.of();
    }
    if (nickColorOverrides == null) {
      nickColorOverrides = Map.of();
    }
  }

  static String normalizeHexOrDefault(String raw, String fallback) {
    String fb = (fallback == null || fallback.isBlank()) ? "#6AA2FF" : fallback.trim();
    if (raw == null) return fb;
    String s = raw.trim();
    if (s.isEmpty()) return fb;
    if (s.startsWith("#")) s = s.substring(1);
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
    if (s.length() != 6) return fb;
    try {
      int rgb = Integer.parseInt(s, 16);
      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = rgb & 0xFF;
      return String.format("#%02X%02X%02X", r, g, b);
    } catch (Exception ignored) {
      return fb;
    }
  }

  static String normalizeHexOrNull(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return null;
    if (s.startsWith("#")) s = s.substring(1);
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
    if (s.length() != 6) return null;
    try {
      int rgb = Integer.parseInt(s, 16);
      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = rgb & 0xFF;
      return String.format("#%02X%02X%02X", r, g, b);
    } catch (Exception ignored) {
      return null;
    }
  }

  static String normalizeTypingTreeIndicatorStyle(String raw) {
    String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return "dots";
    return switch (s) {
      case "dots", "ellipsis" -> "dots";
      case "keyboard", "kbd" -> "keyboard";
      case "glow-dot", "glowdot", "dot", "green-dot", "glowing-green-dot" -> "glow-dot";
      default -> "dots";
    };
  }

  static String normalizeEmbedCardStyle(String raw) {
    String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    return switch (s) {
      case "minimal", "min" -> "minimal";
      case "glassy", "glass" -> "glassy";
      case "denser", "dense", "compact" -> "denser";
      default -> "default";
    };
  }

  static String normalizeSpellcheckLanguageTag(String raw) {
    String s = raw == null ? "" : raw.trim();
    if (s.isEmpty()) return "en-US";
    String folded = s.replace('_', '-');
    if ("en-us".equalsIgnoreCase(folded)) return "en-US";
    if ("en-gb".equalsIgnoreCase(folded)) return "en-GB";
    return "en-US";
  }

  static String normalizeSpellcheckCompletionPreset(String raw) {
    String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return "android-like";
    return switch (s) {
      case "android-like", "standard", "conservative", "aggressive", "custom" -> s;
      default -> "android-like";
    };
  }
}

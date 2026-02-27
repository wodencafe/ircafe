package cafe.woden.ircclient.config;

import cafe.woden.ircclient.util.VirtualThreads;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized app-owned executors.
 *
 * <p>These remain workload-specific to preserve ordering and avoid cross-feature contention, while
 * giving Spring ownership of creation/shutdown.
 */
@Configuration
public class ExecutorConfig {
  public static final String TARGET_COORDINATOR_MAINTENANCE_EXECUTOR =
      "targetCoordinatorMaintenanceExecutor";
  public static final String TARGET_COORDINATOR_USERS_REFRESH_SCHEDULER =
      "targetCoordinatorUsersRefreshScheduler";
  public static final String DB_CHAT_HISTORY_INGEST_EXECUTOR = "dbChatHistoryIngestExecutor";
  public static final String DB_CHAT_HISTORY_EXECUTOR = "dbChatHistoryExecutor";
  public static final String REMOTE_CHAT_HISTORY_EXECUTOR = "remoteChatHistoryExecutor";
  public static final String CHAT_LOG_RETENTION_SCHEDULER = "chatLogRetentionScheduler";
  public static final String JOIN_MODE_BURST_SCHEDULER = "joinModeBurstScheduler";
  public static final String MONITOR_ISON_FALLBACK_SCHEDULER = "monitorIsonFallbackScheduler";
  public static final String USERHOST_QUERY_SCHEDULER = "userhostQueryScheduler";
  public static final String USER_INFO_ENRICHMENT_SCHEDULER = "userInfoEnrichmentScheduler";
  public static final String PIRCBOTX_HEARTBEAT_SCHEDULER = "pircbotxHeartbeatScheduler";
  public static final String PIRCBOTX_RECONNECT_SCHEDULER = "pircbotxReconnectScheduler";
  public static final String ASSERTJ_WATCHDOG_SCHEDULER = "assertjWatchdogScheduler";
  public static final String IRC_EVENT_SCRIPT_EXECUTOR = "ircEventScriptExecutor";
  public static final String NOTIFICATION_SOUND_EXECUTOR = "notificationSoundExecutor";
  public static final String PUSHY_NOTIFICATION_EXECUTOR = "pushyNotificationExecutor";
  public static final String OUTBOUND_DCC_EXECUTOR = "outboundDccExecutor";
  public static final String GNOME_DBUS_CLEANUP_SCHEDULER = "gnomeDbusCleanupScheduler";
  public static final String CHATHISTORY_INGEST_BUS_SCHEDULER = "chathistoryIngestBusScheduler";
  public static final String CHATHISTORY_BATCH_BUS_SCHEDULER = "chathistoryBatchBusScheduler";
  public static final String ZNC_PLAYBACK_BUS_SCHEDULER = "zncPlaybackBusScheduler";
  public static final String JFR_RUNTIME_EVENTS_SAMPLER_SCHEDULER =
      "jfrRuntimeEventsSamplerScheduler";
  public static final String INTERCEPTOR_STORE_INGEST_EXECUTOR = "interceptorStoreIngestExecutor";
  public static final String INTERCEPTOR_STORE_PERSIST_EXECUTOR = "interceptorStorePersistExecutor";
  public static final String UI_LOG_VIEWER_EXECUTOR = "uiLogViewerExecutor";
  public static final String UI_INTERCEPTOR_REFRESH_EXECUTOR = "uiInterceptorRefreshExecutor";
  public static final String PREFERENCES_PUSHY_TEST_EXECUTOR = "preferencesPushyTestExecutor";
  public static final String PREFERENCES_NOTIFICATION_RULE_TEST_EXECUTOR =
      "preferencesNotificationRuleTestExecutor";

  @Bean(name = TARGET_COORDINATOR_MAINTENANCE_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService targetCoordinatorMaintenanceExecutor() {
    return VirtualThreads.newSingleThreadExecutor("ircafe-chatlog-maintenance");
  }

  @Bean(name = TARGET_COORDINATOR_USERS_REFRESH_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService targetCoordinatorUsersRefreshScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-users-refresh");
  }

  @Bean(name = DB_CHAT_HISTORY_INGEST_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService dbChatHistoryIngestExecutor() {
    return VirtualThreads.newSingleThreadExecutor("ircafe-chathistory-ingest");
  }

  @Bean(name = DB_CHAT_HISTORY_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService dbChatHistoryExecutor() {
    return VirtualThreads.newSingleThreadExecutor("ircafe-chat-history");
  }

  @Bean(name = REMOTE_CHAT_HISTORY_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService remoteChatHistoryExecutor() {
    return VirtualThreads.newSingleThreadExecutor("ircafe-remote-history");
  }

  @Bean(name = CHAT_LOG_RETENTION_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService chatLogRetentionScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-chatlog-retention");
  }

  @Bean(name = JOIN_MODE_BURST_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService joinModeBurstScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-joinmode-burst");
  }

  @Bean(name = MONITOR_ISON_FALLBACK_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService monitorIsonFallbackScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-monitor-ison-fallback");
  }

  @Bean(name = USERHOST_QUERY_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService userhostQueryScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("userhost-query");
  }

  @Bean(name = USER_INFO_ENRICHMENT_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService userInfoEnrichmentScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("user-info-enrichment");
  }

  @Bean(name = PIRCBOTX_HEARTBEAT_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService pircbotxHeartbeatScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-heartbeat");
  }

  @Bean(name = PIRCBOTX_RECONNECT_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService pircbotxReconnectScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-reconnect");
  }

  @Bean(name = ASSERTJ_WATCHDOG_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService assertjWatchdogScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-assertj-watchdog");
  }

  @Bean(name = IRC_EVENT_SCRIPT_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService ircEventScriptExecutor() {
    return VirtualThreads.newThreadPerTaskExecutor("ircafe-event-script");
  }

  @Bean(name = NOTIFICATION_SOUND_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService notificationSoundExecutor() {
    return VirtualThreads.newSingleThreadExecutor("notification-sound-thread");
  }

  @Bean(name = PUSHY_NOTIFICATION_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService pushyNotificationExecutor() {
    return VirtualThreads.newThreadPerTaskExecutor("ircafe-pushy");
  }

  @Bean(name = OUTBOUND_DCC_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService outboundDccExecutor() {
    return VirtualThreads.newThreadPerTaskExecutor("ircafe-dcc");
  }

  @Bean(name = GNOME_DBUS_CLEANUP_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService gnomeDbusCleanupScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-dbus-notify");
  }

  @Bean(name = CHATHISTORY_INGEST_BUS_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService chathistoryIngestBusScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-chathistory-bus");
  }

  @Bean(name = CHATHISTORY_BATCH_BUS_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService chathistoryBatchBusScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-chathistory-batch-bus");
  }

  @Bean(name = ZNC_PLAYBACK_BUS_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService zncPlaybackBusScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-znc-playback-bus");
  }

  @Bean(name = JFR_RUNTIME_EVENTS_SAMPLER_SCHEDULER, destroyMethod = "shutdown")
  public ScheduledExecutorService jfrRuntimeEventsSamplerScheduler() {
    return VirtualThreads.newSingleThreadScheduledExecutor("ircafe-jfr-event-sampler");
  }

  @Bean(name = INTERCEPTOR_STORE_INGEST_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService interceptorStoreIngestExecutor() {
    return VirtualThreads.newSingleThreadExecutor("ircafe-interceptor-store");
  }

  @Bean(name = INTERCEPTOR_STORE_PERSIST_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService interceptorStorePersistExecutor() {
    return VirtualThreads.newSingleThreadExecutor("ircafe-interceptor-persist");
  }

  @Bean(name = UI_LOG_VIEWER_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService uiLogViewerExecutor() {
    return VirtualThreads.newSingleThreadExecutor("ircafe-log-viewer");
  }

  @Bean(name = UI_INTERCEPTOR_REFRESH_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService uiInterceptorRefreshExecutor() {
    return VirtualThreads.newSingleThreadExecutor("ircafe-interceptor-panel-refresh");
  }

  @Bean(name = PREFERENCES_PUSHY_TEST_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService preferencesPushyTestExecutor() {
    return VirtualThreads.newSingleThreadExecutor("ircafe-pushy-test");
  }

  @Bean(name = PREFERENCES_NOTIFICATION_RULE_TEST_EXECUTOR, destroyMethod = "shutdown")
  public ExecutorService preferencesNotificationRuleTestExecutor() {
    return VirtualThreads.newSingleThreadExecutor("ircafe-notification-rule-test");
  }
}

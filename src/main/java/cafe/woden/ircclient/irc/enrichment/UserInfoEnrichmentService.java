package cafe.woden.ircclient.irc.enrichment;

import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.IrcRuntimeSettings;
import cafe.woden.ircclient.irc.IrcRuntimeSettingsProvider;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.disposables.Disposable;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Executes rate-limited user info enrichment probes (USERHOST + optional WHOIS fallback).
 *
 * <p>This service is intentionally conservative:
 *
 * <ul>
 *   <li>Does nothing unless enabled in preferences (User info enrichment)
 *   <li>Runs at most one probe per scheduler tick
 *   <li>WHOIS fallback remains OFF by default and is gated by settings
 * </ul>
 *
 * <p>Probe planning is delegated to {@link UserInfoEnrichmentPlanner}.
 */
@Component
public class UserInfoEnrichmentService {
  private static final Logger log = LoggerFactory.getLogger(UserInfoEnrichmentService.class);

  private static final String IRCafe_WHOX_TOKEN = "1";
  private static final String IRCafe_WHOX_FIELDS = "%tcuhnaf," + IRCafe_WHOX_TOKEN;
  private static final long NO_WAKE_NEEDED_MS = Long.MAX_VALUE;

  private final IrcClientService irc;
  private final ObjectProvider<IrcRuntimeSettingsProvider> settingsProvider;

  private final UserInfoEnrichmentPlanner planner;
  private final Set<String> knownServers = ConcurrentHashMap.newKeySet();

  /** Guard to ensure we only do one self-WHOIS capability probe per server connection. */
  private final Set<String> selfWhoisProbeDone = ConcurrentHashMap.newKeySet();

  /** Observed WHOX support via RPL_ISUPPORT (005). */
  private final ConcurrentHashMap<String, Boolean> whoxSupportedByServer =
      new ConcurrentHashMap<>();

  /**
   * Observed WHOX schema compatibility for the IRCafe-issued channel scan WHOX fields.
   *
   * <p>Some networks advertise WHOX but return 354 fields in an unexpected layout (or omit
   * account/flags). When we detect that, enrichment should fall back to plain WHO/USERHOST instead
   * of silently "working" without producing account updates.
   */
  private final ConcurrentHashMap<String, Boolean> whoxSchemaCompatibleByServer =
      new ConcurrentHashMap<>();

  /**
   * Best-effort recent activity index (per server), used to prioritize WHOIS fallback for users
   * that the user is actually seeing/interacting with (e.g., recent speakers).
   */
  private static final int MAX_TRACKED_ACTIVE_NICKS_PER_SERVER = 2048;

  private final ConcurrentHashMap<String, Map<String, Instant>> lastActiveAtByNickLowerByServer =
      new ConcurrentHashMap<>();

  private final Disposable eventsSub;

  private final ScheduledExecutorService exec;
  private final Object scheduleLock = new Object();
  private ScheduledFuture<?> scheduledTick;
  private long scheduledAtEpochMs = Long.MAX_VALUE;

  public UserInfoEnrichmentService(
      IrcClientService irc,
      ObjectProvider<IrcRuntimeSettingsProvider> settingsProvider,
      UserInfoEnrichmentPlanner planner,
      @Qualifier(ExecutorConfig.USER_INFO_ENRICHMENT_SCHEDULER) ScheduledExecutorService exec) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.settingsProvider = Objects.requireNonNull(settingsProvider, "settingsProvider");
    this.planner = Objects.requireNonNull(planner, "planner");
    this.exec = Objects.requireNonNull(exec, "exec");

    this.eventsSub =
        irc.events()
            .subscribe(
                this::onEvent,
                err -> log.debug("UserInfoEnrichmentService event handler failed", err));
  }

  @PreDestroy
  void shutdown() {
    try {
      if (eventsSub != null && !eventsSub.isDisposed()) eventsSub.dispose();
    } catch (Exception ignored) {
    }
    synchronized (scheduleLock) {
      if (scheduledTick != null) {
        scheduledTick.cancel(false);
        scheduledTick = null;
      }
      scheduledAtEpochMs = Long.MAX_VALUE;
    }
  }

  public void clearServer(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    knownServers.remove(sid);
    selfWhoisProbeDone.remove(sid);
    planner.clearServer(sid);
    whoxSupportedByServer.remove(sid);
    whoxSchemaCompatibleByServer.remove(sid);
  }

  /**
   * True when WHOX can be used for channel-wide scans on this server.
   *
   * <p>We require:
   *
   * <ul>
   *   <li>User info enrichment enabled
   *   <li>WHOIS fallback enabled (because WHOX is used to learn account status)
   *   <li>Observed WHOX support via RPL_ISUPPORT
   * </ul>
   */
  public boolean shouldUseWhoxForChannelScan(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return false;
    UserInfoEnrichmentPlanner.Settings cfg = config();
    if (!cfg.enabled()) return false;
    if (!Boolean.TRUE.equals(whoxSupportedByServer.get(sid))) return false;
    if (Boolean.FALSE.equals(whoxSchemaCompatibleByServer.get(sid))) return false;
    return true;
  }

  /**
   * Provide a roster snapshot for periodic refresh (if enabled).
   *
   * <p>This does not enqueue probes by itself.
   */
  public void setRosterSnapshot(String serverId, Collection<String> nicks) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    knownServers.add(sid);
    planner.setRosterSnapshot(sid, nicks);
    requestTick(0);
  }

  /** Enqueue USERHOST probes for these nicks (best-effort). */
  public void enqueueUserhost(String serverId, Collection<String> nicks) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    knownServers.add(sid);
    planner.enqueueUserhost(sid, nicks);
    requestTick(0);
  }

  /** Enqueue WHOIS probes for these nicks (only executed if WHOIS fallback is enabled). */
  public void enqueueWhois(String serverId, Collection<String> nicks) {
    IrcRuntimeSettings s = currentSettings();
    if (!s.userInfoEnrichmentEnabled() || !s.userInfoEnrichmentWhoisFallbackEnabled()) return;

    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    knownServers.add(sid);
    planner.enqueueWhois(sid, nicks);
    requestTick(0);
  }

  /** Enqueue a channel WHO scan (best-effort, rate limited). */
  public void enqueueWhoChannel(String serverId, String channel) {
    IrcRuntimeSettings s = currentSettings();
    if (!s.userInfoEnrichmentEnabled()) return;

    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    knownServers.add(sid);
    planner.enqueueWhoChannel(sid, channel);
    requestTick(0);
  }

  /** Enqueue a channel WHO scan as high priority (promoted to the front of the queue). */
  public void enqueueWhoChannelPrioritized(String serverId, String channel) {
    IrcRuntimeSettings s = currentSettings();
    if (!s.userInfoEnrichmentEnabled()) return;

    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    knownServers.add(sid);
    planner.enqueueWhoChannelPrioritized(sid, channel);
    requestTick(0);
  }

  /** Enqueue WHOIS probes as high priority (promoted to the front of the queue). */
  public void enqueueWhoisPrioritized(String serverId, List<String> nicks) {
    IrcRuntimeSettings s = currentSettings();
    if (!s.userInfoEnrichmentEnabled() || !s.userInfoEnrichmentWhoisFallbackEnabled()) return;

    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    knownServers.add(sid);
    planner.enqueueWhoisPrioritized(sid, nicks);
    requestTick(0);
  }

  /**
   * Record that we saw a user do something (e.g., speak), so WHOIS fallback can prioritize them.
   */
  public void noteUserActivity(String serverId, String nick, Instant at) {
    String sid = norm(serverId);
    String nk = norm(nick);
    if (sid.isEmpty() || nk.isEmpty()) return;
    Instant when = at != null ? at : Instant.now();

    Map<String, Instant> m =
        lastActiveAtByNickLowerByServer.computeIfAbsent(sid, k -> newLruInstantMap());
    String key = nk.toLowerCase(Locale.ROOT);
    m.put(key, when);
  }

  /** Returns the last-seen activity time for a nick (best-effort), or null if unknown. */
  public Instant lastActiveAt(String serverId, String nick) {
    String sid = norm(serverId);
    String nk = norm(nick);
    if (sid.isEmpty() || nk.isEmpty()) return null;
    Map<String, Instant> m = lastActiveAtByNickLowerByServer.get(sid);
    if (m == null) return null;
    return m.get(nk.toLowerCase(Locale.ROOT));
  }

  private static Map<String, Instant> newLruInstantMap() {
    return Collections.synchronizedMap(
        new LinkedHashMap<>(256, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Instant> eldest) {
            return size() > MAX_TRACKED_ACTIVE_NICKS_PER_SERVER;
          }
        });
  }

  private void onEvent(ServerIrcEvent ev) {
    if (ev.event() instanceof IrcEvent.Connected c) {
      String sid = norm(ev.serverId());
      if (!sid.isEmpty()) {
        knownServers.add(sid);
        maybeEnqueueSelfWhoisProbe(sid, c.nick());
        requestTick(0);
      }
      return;
    }
    if (ev.event() instanceof IrcEvent.Disconnected) {
      String sid = norm(ev.serverId());
      if (!sid.isEmpty()) {
        selfWhoisProbeDone.remove(sid);
        requestTick(0);
      }
      return;
    }

    if (ev.event() instanceof IrcEvent.WhoxSupportObserved wso) {
      String sid = norm(ev.serverId());
      if (!sid.isEmpty() && wso.supported()) {
        whoxSupportedByServer.putIfAbsent(sid, Boolean.TRUE);
      }
      return;
    }

    if (ev.event() instanceof IrcEvent.WhoxSchemaCompatibleObserved wsc) {
      String sid = norm(ev.serverId());
      if (!sid.isEmpty()) {
        whoxSchemaCompatibleByServer.put(sid, wsc.compatible());
        if (!wsc.compatible()) {
          log.debug(
              "[{}] Disabling WHOX channel scans due to schema mismatch: {}", sid, wsc.detail());
        }
      }
      return;
    }

    if (!(ev.event() instanceof IrcEvent.WhoisProbeCompleted wpc)) return;
    UserInfoEnrichmentPlanner.Settings cfg = config();
    if (!cfg.enabled() || !cfg.whoisFallbackEnabled()) return;

    planner.noteWhoisProbeCompleted(
        ev.serverId(),
        wpc.nick(),
        wpc.at(),
        wpc.sawAccountNumeric(),
        wpc.whoisAccountNumericSupported(),
        cfg);
    requestTick(0);
  }

  /** This is only done when "User info enrichment" and "WHOIS fallback" are enabled. */
  private void maybeEnqueueSelfWhoisProbe(String serverId, String currentNick) {
    if (serverId == null || serverId.isBlank()) return;

    UserInfoEnrichmentPlanner.Settings cfg = config();
    if (!cfg.enabled() || !cfg.whoisFallbackEnabled()) return;

    String nick = norm(currentNick);
    if (nick.isEmpty()) {
      nick = irc.currentNick(serverId).orElse("");
      nick = norm(nick);
    }
    if (nick.isEmpty()) return;
    if (!selfWhoisProbeDone.add(serverId)) return;
    planner.enqueueWhois(serverId, List.of(nick));
    knownServers.add(serverId);
    requestTick(0);
    log.debug("[{}] Enqueued self WHOIS capability probe for nick {}", serverId, nick);
  }

  private void tick() {
    try {
      UserInfoEnrichmentPlanner.Settings cfg = config();
      if (!cfg.enabled()) {
        for (String sid : List.copyOf(knownServers)) {
          planner.clearServer(sid);
          whoxSupportedByServer.remove(sid);
          whoxSchemaCompatibleByServer.remove(sid);
        }
        knownServers.clear();
        return;
      }

      Instant now = Instant.now();

      if (cfg.whoisFallbackEnabled()) {
        for (String sid : List.copyOf(knownServers)) {
          if (!isConnected(sid)) continue;
          if (selfWhoisProbeDone.contains(sid)) continue;
          maybeEnqueueSelfWhoisProbe(sid, irc.currentNick(sid).orElse(""));
        }
      }
      if (cfg.periodicRefreshEnabled()) {
        for (String sid : List.copyOf(knownServers)) {
          if (!isConnected(sid)) continue;
          planner.maybeEnqueuePeriodicRefresh(sid, now, cfg);
        }
      }
      for (String sid : List.copyOf(knownServers)) {
        if (!isConnected(sid)) continue;
        Optional<UserInfoEnrichmentPlanner.PlannedCommand> next = planner.pollNext(sid, now, cfg);
        if (next.isEmpty()) continue;
        execute(next.get());
        break;
      }

      long nextDelayMs = NO_WAKE_NEEDED_MS;
      Instant after = Instant.now();
      for (String sid : List.copyOf(knownServers)) {
        if (!isConnected(sid)) continue;
        long readyDelayMs = planner.nextReadyDelayMs(sid, after, cfg);
        if (readyDelayMs < nextDelayMs) nextDelayMs = readyDelayMs;
        long periodicDelayMs = planner.nextPeriodicRefreshDelayMs(sid, after, cfg);
        if (periodicDelayMs < nextDelayMs) nextDelayMs = periodicDelayMs;
        if (nextDelayMs == 0L) break;
      }
      if (nextDelayMs != NO_WAKE_NEEDED_MS) {
        requestTick(nextDelayMs);
      }
    } catch (Exception ex) {
      log.debug("UserInfoEnrichmentService tick failed", ex);
    }
  }

  private void requestTick(long delayMs) {
    long safeDelayMs = Math.max(0L, delayMs);
    long targetEpochMs = System.currentTimeMillis() + safeDelayMs;
    synchronized (scheduleLock) {
      if (exec.isShutdown()) return;

      if (scheduledTick != null && !scheduledTick.isDone()) {
        if (targetEpochMs >= scheduledAtEpochMs) {
          return;
        }
        scheduledTick.cancel(false);
      }

      scheduledAtEpochMs = targetEpochMs;
      scheduledTick = exec.schedule(this::runScheduledTick, safeDelayMs, TimeUnit.MILLISECONDS);
    }
  }

  private void runScheduledTick() {
    synchronized (scheduleLock) {
      scheduledTick = null;
      scheduledAtEpochMs = Long.MAX_VALUE;
    }
    tick();
  }

  private void execute(UserInfoEnrichmentPlanner.PlannedCommand cmd) {
    String serverId = cmd.serverId();
    if (cmd.kind() == UserInfoEnrichmentPlanner.ProbeKind.WHO_CHANNEL) {
      String channel = cmd.nicks().isEmpty() ? "" : cmd.nicks().getFirst();
      if (channel.isBlank()) return;
      String line = cmd.rawLine();
      if (shouldUseWhoxForChannelScan(serverId)) {
        line = "WHO " + channel + " " + IRCafe_WHOX_FIELDS;
      }
      log.debug("[{}] WHO channel enrichment: {}", serverId, channel);
      Disposable d =
          irc.sendRaw(serverId, line)
              .subscribe(
                  () -> {},
                  err ->
                      log.debug(
                          "WHO channel enrichment failed for {}: {}", serverId, err.toString()));
      if (d.isDisposed()) {}
      return;
    }

    if (cmd.kind() == UserInfoEnrichmentPlanner.ProbeKind.USERHOST) {
      String line = cmd.rawLine();
      log.debug("[{}] USERHOST enrichment: {}", serverId, String.join(", ", cmd.nicks()));
      Disposable d =
          irc.sendRaw(serverId, line)
              .subscribe(
                  () -> {},
                  err ->
                      log.debug("USERHOST enrichment failed for {}: {}", serverId, err.toString()));
      if (d.isDisposed()) {}
      return;
    }
    String nick = cmd.nicks().isEmpty() ? "" : cmd.nicks().getFirst();
    if (nick.isBlank()) return;
    log.debug("[{}] WHOIS enrichment: {}", serverId, nick);
    Disposable d =
        irc.whois(serverId, nick)
            .subscribe(
                () -> {},
                err -> log.debug("WHOIS enrichment failed for {}: {}", serverId, err.toString()));
    if (d.isDisposed()) {}
  }

  private boolean isConnected(String serverId) {
    return irc.currentNick(serverId).isPresent();
  }

  private IrcRuntimeSettings currentSettings() {
    IrcRuntimeSettingsProvider provider = settingsProvider.getIfAvailable();
    return provider != null ? provider.current() : IrcRuntimeSettings.defaults();
  }

  private UserInfoEnrichmentPlanner.Settings config() {
    IrcRuntimeSettings s = currentSettings();
    boolean enabled = s.userInfoEnrichmentEnabled();
    Duration uhMinInterval = Duration.ofSeconds(15);
    int uhMaxPerMinute = 3;
    Duration uhNickCooldown = Duration.ofMinutes(60);
    int uhMaxNicksPerCmd = 5;
    boolean whoisEnabled = false;
    Duration whoisMinInterval = Duration.ofSeconds(45);
    Duration whoisNickCooldown = Duration.ofMinutes(120);
    boolean periodicEnabled = false;
    Duration periodicInterval = Duration.ofSeconds(300);
    int periodicNicksPerTick = 2;

    uhMinInterval =
        Duration.ofSeconds(Math.max(1, s.userInfoEnrichmentUserhostMinIntervalSeconds()));
    uhMaxPerMinute = Math.max(1, s.userInfoEnrichmentUserhostMaxCommandsPerMinute());
    uhNickCooldown =
        Duration.ofMinutes(Math.max(1, s.userInfoEnrichmentUserhostNickCooldownMinutes()));
    uhMaxNicksPerCmd =
        Math.max(
            1,
            Math.min(
                UserInfoEnrichmentPlanner.ABSOLUTE_MAX_USERHOST_NICKS_PER_CMD,
                s.userInfoEnrichmentUserhostMaxNicksPerCommand()));

    whoisEnabled = s.userInfoEnrichmentWhoisFallbackEnabled();
    whoisMinInterval =
        Duration.ofSeconds(Math.max(1, s.userInfoEnrichmentWhoisMinIntervalSeconds()));
    whoisNickCooldown =
        Duration.ofMinutes(Math.max(1, s.userInfoEnrichmentWhoisNickCooldownMinutes()));

    periodicEnabled = s.userInfoEnrichmentPeriodicRefreshEnabled();
    periodicInterval =
        Duration.ofSeconds(Math.max(5, s.userInfoEnrichmentPeriodicRefreshIntervalSeconds()));
    periodicNicksPerTick =
        Math.max(1, Math.min(10, s.userInfoEnrichmentPeriodicRefreshNicksPerTick()));

    return new UserInfoEnrichmentPlanner.Settings(
        enabled,
        uhMinInterval,
        uhMaxPerMinute,
        uhNickCooldown,
        uhMaxNicksPerCmd,
        whoisEnabled,
        whoisMinInterval,
        whoisNickCooldown,
        periodicEnabled,
        periodicInterval,
        periodicNicksPerTick);
  }

  private static String norm(String s) {
    return Objects.toString(s, "").trim();
  }
}

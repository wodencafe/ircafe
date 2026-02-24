package cafe.woden.ircclient.app.interceptors;

import cafe.woden.ircclient.app.notifications.IrcEventNotificationRule;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.notify.sound.BuiltInSound;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.util.VirtualThreads;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** In-memory interceptor definitions and captured hits. */
@Component
public class InterceptorStore {
  private static final Logger log = LoggerFactory.getLogger(InterceptorStore.class);

  public static final int DEFAULT_MAX_HITS_PER_INTERCEPTOR = 4000;
  private static final long SCRIPT_TIMEOUT_SECONDS = 8L;

  public record Change(String serverId, String interceptorId) {}

  private final RuntimeConfigStore runtimeConfig;
  private final NotificationSoundService notificationSoundService;
  private final TrayNotificationService trayNotificationService;
  private final ExecutorService actionScriptExecutor;

  private final int maxHitsPerInterceptor;
  private final ExecutorService ingestExecutor =
          VirtualThreads.newSingleThreadExecutor("ircafe-interceptor-store");
  private final ExecutorService persistExecutor =
          VirtualThreads.newSingleThreadExecutor("ircafe-interceptor-persist");
  private final AtomicLong persistRequestSeq = new AtomicLong(0L);
  private final AtomicReference<Map<String, List<InterceptorDefinition>>> pendingPersistSnapshot =
          new AtomicReference<>();

  private final ConcurrentHashMap<String, LinkedHashMap<String, InterceptorDefinition>> defsByServer =
          new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Map<String, List<InterceptorHit>>> hitsByServer =
          new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Pattern> patternCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Set<InterceptorEventType>> eventTypeCache = new ConcurrentHashMap<>();

  private final FlowableProcessor<Change> changes =
          PublishProcessor.<Change>create().toSerialized();

  @Autowired
  public InterceptorStore(
          RuntimeConfigStore runtimeConfig,
          NotificationSoundService notificationSoundService,
          @Lazy TrayNotificationService trayNotificationService,
          @Qualifier(ExecutorConfig.IRC_EVENT_SCRIPT_EXECUTOR) ExecutorService actionScriptExecutor
  ) {
    this(
            runtimeConfig,
            notificationSoundService,
            trayNotificationService,
            actionScriptExecutor,
            DEFAULT_MAX_HITS_PER_INTERCEPTOR,
            true);
  }

  InterceptorStore(int maxHitsPerInterceptor) {
    this(null, null, null, null, maxHitsPerInterceptor, false);
  }

  private InterceptorStore(
          RuntimeConfigStore runtimeConfig,
          NotificationSoundService notificationSoundService,
          TrayNotificationService trayNotificationService,
          ExecutorService actionScriptExecutor,
          int maxHitsPerInterceptor,
          boolean loadFromRuntimeConfig
  ) {
    this.runtimeConfig = runtimeConfig;
    this.notificationSoundService = notificationSoundService;
    this.trayNotificationService = trayNotificationService;
    this.actionScriptExecutor = actionScriptExecutor;
    this.maxHitsPerInterceptor = Math.max(200, maxHitsPerInterceptor);

    if (loadFromRuntimeConfig) {
      loadPersistedDefinitions();
    }
  }

  public Flowable<Change> changes() {
    return changes.onBackpressureBuffer();
  }

  /** Preview interceptor sound settings without waiting for a real interceptor hit. */
  public void previewSoundOverride(String soundId, boolean useCustom, String customPath) {
    if (notificationSoundService == null) return;
    try {
      if (useCustom) {
        String rel = norm(customPath);
        if (!rel.isEmpty()) {
          notificationSoundService.previewCustom(rel);
          return;
        }
      }
      notificationSoundService.preview(BuiltInSound.fromId(soundId));
    } catch (Exception ignored) {
    }
  }

  public InterceptorDefinition createInterceptor(String serverId, String requestedName) {
    String sid = norm(serverId);
    if (sid.isEmpty()) throw new IllegalArgumentException("serverId must not be blank");

    String base = sanitizeName(requestedName);
    LinkedHashMap<String, InterceptorDefinition> defs =
            defsByServer.computeIfAbsent(sid, __ -> new LinkedHashMap<>());

    InterceptorDefinition def;
    synchronized (defs) {
      String name = uniqueName(base, defs.values());
      String id = UUID.randomUUID().toString();
      def = new InterceptorDefinition(
              id,
              name,
              true,
              sid,
              InterceptorRuleMode.GLOB,
              "",
              InterceptorRuleMode.GLOB,
              "",
              false,
              false,
              false,
              "NOTIF_1",
              false,
              "",
              false,
              "",
              "",
              "",
              List.of(new InterceptorRule(
                      true,
                      "Rule 1",
                      "",
                      InterceptorRuleMode.LIKE,
                      "",
                      InterceptorRuleMode.LIKE,
                      "",
                      InterceptorRuleMode.GLOB,
                      ""))
      );
      defs.put(def.id(), def);
    }

    persistDefinitions();
    changes.onNext(new Change(sid, def.id()));
    return def;
  }

  public boolean renameInterceptor(String serverId, String interceptorId, String requestedName) {
    String sid = norm(serverId);
    String iid = norm(interceptorId);
    if (sid.isEmpty() || iid.isEmpty()) return false;

    LinkedHashMap<String, InterceptorDefinition> defs = defsByServer.get(sid);
    if (defs == null) return false;

    boolean changed = false;
    synchronized (defs) {
      InterceptorDefinition prev = defs.get(iid);
      if (prev == null) return false;
      String base = sanitizeName(requestedName);
      String next = uniqueName(base, defs.values().stream().filter(d -> !iid.equals(d.id())).toList());
      if (Objects.equals(prev.name(), next)) return false;
      defs.put(iid, new InterceptorDefinition(
              iid,
              next,
              prev.enabled(),
              prev.scopeServerId(),
              prev.channelIncludeMode(),
              prev.channelIncludes(),
              prev.channelExcludeMode(),
              prev.channelExcludes(),
              prev.actionSoundEnabled(),
              prev.actionStatusBarEnabled(),
              prev.actionToastEnabled(),
              prev.actionSoundId(),
              prev.actionSoundUseCustom(),
              prev.actionSoundCustomPath(),
              prev.actionScriptEnabled(),
              prev.actionScriptPath(),
              prev.actionScriptArgs(),
              prev.actionScriptWorkingDirectory(),
              prev.rules()));
      changed = true;
    }

    if (changed) {
      persistDefinitions();
      changes.onNext(new Change(sid, iid));
    }
    return changed;
  }

  public boolean setInterceptorEnabled(String serverId, String interceptorId, boolean enabled) {
    String sid = norm(serverId);
    String iid = norm(interceptorId);
    if (sid.isEmpty() || iid.isEmpty()) return false;

    LinkedHashMap<String, InterceptorDefinition> defs = defsByServer.get(sid);
    if (defs == null) return false;

    boolean changed = false;
    synchronized (defs) {
      InterceptorDefinition prev = defs.get(iid);
      if (prev == null) return false;
      if (prev.enabled() == enabled) return false;
      defs.put(iid, new InterceptorDefinition(
              iid,
              prev.name(),
              enabled,
              prev.scopeServerId(),
              prev.channelIncludeMode(),
              prev.channelIncludes(),
              prev.channelExcludeMode(),
              prev.channelExcludes(),
              prev.actionSoundEnabled(),
              prev.actionStatusBarEnabled(),
              prev.actionToastEnabled(),
              prev.actionSoundId(),
              prev.actionSoundUseCustom(),
              prev.actionSoundCustomPath(),
              prev.actionScriptEnabled(),
              prev.actionScriptPath(),
              prev.actionScriptArgs(),
              prev.actionScriptWorkingDirectory(),
              prev.rules()));
      changed = true;
    }

    if (changed) {
      persistDefinitions();
      changes.onNext(new Change(sid, iid));
    }
    return changed;
  }

  public boolean removeInterceptor(String serverId, String interceptorId) {
    String sid = norm(serverId);
    String iid = norm(interceptorId);
    if (sid.isEmpty() || iid.isEmpty()) return false;

    LinkedHashMap<String, InterceptorDefinition> defs = defsByServer.get(sid);
    if (defs == null) return false;

    InterceptorDefinition removed;
    synchronized (defs) {
      removed = defs.remove(iid);
    }
    if (removed == null) return false;

    Map<String, List<InterceptorHit>> perServerHits = hitsByServer.get(sid);
    if (perServerHits != null) {
      synchronized (perServerHits) {
        perServerHits.remove(iid);
      }
    }

    persistDefinitions();
    changes.onNext(new Change(sid, iid));
    return true;
  }

  public List<InterceptorDefinition> listInterceptors(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return List.of();
    LinkedHashMap<String, InterceptorDefinition> defs = defsByServer.get(sid);
    if (defs == null) return List.of();
    synchronized (defs) {
      return List.copyOf(defs.values());
    }
  }

  public InterceptorDefinition interceptor(String serverId, String interceptorId) {
    String sid = norm(serverId);
    String iid = norm(interceptorId);
    if (sid.isEmpty() || iid.isEmpty()) return null;
    LinkedHashMap<String, InterceptorDefinition> defs = defsByServer.get(sid);
    if (defs == null) return null;
    synchronized (defs) {
      return defs.get(iid);
    }
  }

  public String interceptorName(String serverId, String interceptorId) {
    InterceptorDefinition def = interceptor(serverId, interceptorId);
    return def == null ? "" : def.name();
  }

  public boolean saveInterceptor(String serverId, InterceptorDefinition updated) {
    String sid = norm(serverId);
    if (sid.isEmpty() || updated == null || updated.id().isBlank()) return false;

    LinkedHashMap<String, InterceptorDefinition> defs =
            defsByServer.computeIfAbsent(sid, __ -> new LinkedHashMap<>());

    boolean changed;
    synchronized (defs) {
      InterceptorDefinition normalized = normalizeForSave(updated);
      InterceptorDefinition prev = defs.get(normalized.id());
      if (Objects.equals(prev, normalized)) return false;
      defs.put(normalized.id(), normalized);
      changed = true;
    }

    if (changed) {
      persistDefinitions();
      changes.onNext(new Change(sid, updated.id()));
    }
    return changed;
  }

  public List<InterceptorHit> listHits(String serverId, String interceptorId, int maxRows) {
    String sid = norm(serverId);
    String iid = norm(interceptorId);
    if (sid.isEmpty() || iid.isEmpty() || maxRows <= 0) return List.of();
    Map<String, List<InterceptorHit>> perServer = hitsByServer.get(sid);
    if (perServer == null) return List.of();
    List<InterceptorHit> list = perServer.get(iid);
    if (list == null) return List.of();
    synchronized (list) {
      int n = list.size();
      int from = Math.max(0, n - maxRows);
      return List.copyOf(list.subList(from, n));
    }
  }

  public int totalHitCount(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return 0;
    Map<String, List<InterceptorHit>> perServer = hitsByServer.get(sid);
    if (perServer == null || perServer.isEmpty()) return 0;
    int total = 0;
    for (List<InterceptorHit> list : perServer.values()) {
      if (list == null) continue;
      synchronized (list) {
        total += list.size();
      }
    }
    return Math.max(0, total);
  }

  public void clearHits(String serverId, String interceptorId) {
    String sid = norm(serverId);
    String iid = norm(interceptorId);
    if (sid.isEmpty() || iid.isEmpty()) return;

    Map<String, List<InterceptorHit>> perServer = hitsByServer.get(sid);
    if (perServer == null) return;
    List<InterceptorHit> list = perServer.get(iid);
    if (list == null) return;
    synchronized (list) {
      if (list.isEmpty()) return;
      list.clear();
    }
    changes.onNext(new Change(sid, iid));
  }

  public void clearServer(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    defsByServer.remove(sid);
    hitsByServer.remove(sid);
    persistDefinitions();
    changes.onNext(new Change(sid, ""));
  }

  /** Clears only captured hits for a server while preserving persisted interceptor definitions. */
  public void clearServerHits(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    Map<String, List<InterceptorHit>> removed = hitsByServer.remove(sid);
    if (removed == null || removed.isEmpty()) return;
    changes.onNext(new Change(sid, ""));
  }

  /** Enqueue event evaluation off the EDT. */
  public void ingestEvent(
          String serverId,
          String channel,
          String fromNick,
          String fromHostmask,
          String text,
          InterceptorEventType eventType
  ) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;

    String chan = norm(channel);
    if (chan.isEmpty()) chan = "status";

    String from = norm(fromNick);
    if (from.isEmpty()) from = "server";

    String hostmask = norm(fromHostmask);
    String msg = norm(text);

    InterceptorEventType type = eventType == null ? InterceptorEventType.MESSAGE : eventType;

    final String fSid = sid;
    final String fChan = chan;
    final String fFrom = from;
    final String fHostmask = hostmask;
    final String fMsg = msg;
    final InterceptorEventType fType = type;
    ingestExecutor.execute(() -> ingestNow(fSid, fChan, fFrom, fHostmask, fMsg, fType));
  }

  /** Backward-compatible entrypoint used by the first interceptor implementation. */
  public void ingestChannelMessage(
          String serverId,
          String channel,
          String fromNick,
          String text,
          String eventType
  ) {
    ingestEvent(
            serverId,
            channel,
            fromNick,
            "",
            text,
            InterceptorEventType.fromToken(eventType));
  }

  @PreDestroy
  void shutdown() {
    flushPersistNow();
    persistExecutor.shutdownNow();
    ingestExecutor.shutdownNow();
  }

  private void ingestNow(
          String eventServerId,
          String channel,
          String fromNick,
          String fromHostmask,
          String text,
          InterceptorEventType eventType
  ) {
    if (defsByServer.isEmpty()) return;

    for (Map.Entry<String, LinkedHashMap<String, InterceptorDefinition>> entry : defsByServer.entrySet()) {
      String ownerServerId = entry.getKey();
      LinkedHashMap<String, InterceptorDefinition> defs = entry.getValue();
      if (defs == null || defs.isEmpty()) continue;

      List<InterceptorDefinition> snapshot;
      synchronized (defs) {
        snapshot = List.copyOf(defs.values());
      }

      for (InterceptorDefinition def : snapshot) {
        if (def == null || !def.enabled()) continue;
        if (!def.scopeMatchesServer(eventServerId)) continue;
        if (!matchesChannelScope(def, channel)) continue;

        String reason = null;
        for (InterceptorRule rule : def.rules()) {
          if (!matchesRule(rule, eventType, text, fromNick, fromHostmask)) continue;
          reason = rule.label();
          break;
        }
        if (reason == null) continue;

        InterceptorHit hit = new InterceptorHit(
                eventServerId,
                def.id(),
                def.name(),
                Instant.now(),
                channel,
                fromNick,
                fromHostmask,
                eventType.token(),
                reason,
                text
        );

        appendHit(ownerServerId, def.id(), hit);
        dispatchActions(def, ownerServerId, hit);
        changes.onNext(new Change(ownerServerId, def.id()));
      }
    }
  }

  private void dispatchActions(InterceptorDefinition def, String ownerServerId, InterceptorHit hit) {
    if (def == null || hit == null) return;

    if (def.actionSoundEnabled() && notificationSoundService != null) {
      try {
        notificationSoundService.playOverride(
                def.actionSoundId(),
                def.actionSoundUseCustom(),
                def.actionSoundCustomPath());
      } catch (Exception ignored) {
      }
    }

    if ((def.actionStatusBarEnabled() || def.actionToastEnabled()) && trayNotificationService != null) {
      try {
        String sid = norm(hit.serverId());
        if (sid.isEmpty()) sid = norm(ownerServerId);
        String target = norm(hit.channel());
        if (target.isEmpty()) target = "status";
        trayNotificationService.notifyCustom(
                sid,
                target,
                "Interceptor: " + def.name(),
                buildNotificationBody(hit),
                def.actionToastEnabled(),
                def.actionStatusBarEnabled(),
                IrcEventNotificationRule.FocusScope.ANY,
                false,
                null,
                false,
                null);
      } catch (Exception ignored) {
      }
    }

    if (!def.actionScriptEnabled()) return;
    if (actionScriptExecutor == null) return;
    String scriptPath = norm(def.actionScriptPath());
    if (scriptPath.isEmpty()) return;

    String owner = norm(ownerServerId);
    actionScriptExecutor.execute(() -> runActionScript(def, owner, hit));
  }

  private static String buildNotificationBody(InterceptorHit hit) {
    if (hit == null) return "Interceptor rule matched";
    String reason = norm(hit.reason());
    String from = norm(hit.fromNick());
    String channel = norm(hit.channel());
    String message = norm(hit.message());
    String type = norm(hit.eventType());

    StringBuilder out = new StringBuilder();
    if (!reason.isEmpty()) {
      out.append(reason).append(" - ");
    }
    if (!from.isEmpty()) {
      out.append(from);
      if (!channel.isEmpty()) {
        out.append(" @ ").append(channel);
      }
      out.append(": ");
    } else if (!channel.isEmpty()) {
      out.append(channel).append(": ");
    }
    if (!message.isEmpty()) {
      out.append(message);
    } else if (!type.isEmpty()) {
      out.append("Event: ").append(type);
    } else {
      out.append("Interceptor rule matched");
    }
    return out.toString();
  }

  private void runActionScript(InterceptorDefinition def, String ownerServerId, InterceptorHit hit) {
    String scriptPath = norm(def.actionScriptPath());
    if (scriptPath.isEmpty()) return;

    try {
      List<String> command = new ArrayList<>();
      command.add(scriptPath);
      command.addAll(parseCommandArgs(def.actionScriptArgs()));

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectInput(ProcessBuilder.Redirect.PIPE);
      pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      pb.redirectError(ProcessBuilder.Redirect.DISCARD);

      String scriptWorkingDirectory = norm(def.actionScriptWorkingDirectory());
      if (!scriptWorkingDirectory.isEmpty()) {
        File cwd = new File(scriptWorkingDirectory);
        if (!cwd.isDirectory()) {
          log.warn("[ircafe] Interceptor script working directory does not exist: {}", scriptWorkingDirectory);
          return;
        }
        pb.directory(cwd);
      }

      Map<String, String> env = pb.environment();
      putEnv(env, "IRCAFE_INTERCEPTOR_ID", def.id());
      putEnv(env, "IRCAFE_INTERCEPTOR_NAME", def.name());
      putEnv(env, "IRCAFE_INTERCEPTOR_OWNER_SERVER_ID", ownerServerId);
      putEnv(env, "IRCAFE_INTERCEPTOR_SCOPE_SERVER_ID", def.scopeServerId());

      putEnv(env, "IRCAFE_EVENT_SERVER_ID", hit.serverId());
      putEnv(env, "IRCAFE_EVENT_CHANNEL", hit.channel());
      putEnv(env, "IRCAFE_EVENT_NICK", hit.fromNick());
      putEnv(env, "IRCAFE_EVENT_HOSTMASK", hit.fromHostmask());
      putEnv(env, "IRCAFE_EVENT_TYPE", hit.eventType());
      putEnv(env, "IRCAFE_EVENT_REASON", hit.reason());
      putEnv(env, "IRCAFE_EVENT_MESSAGE", hit.message());
      putEnv(env, "IRCAFE_EVENT_TIMESTAMP_MS", Long.toString(System.currentTimeMillis()));

      Process p = pb.start();
      boolean exited = p.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!exited) {
        p.destroyForcibly();
        log.warn("[ircafe] Interceptor script timed out after {}s: {}", SCRIPT_TIMEOUT_SECONDS, scriptPath);
      }
    } catch (Exception ex) {
      log.warn("[ircafe] Could not run interceptor script: {}", scriptPath, ex);
    }
  }

  private static List<String> parseCommandArgs(String rawArgs) {
    String input = Objects.toString(rawArgs, "").trim();
    if (input.isEmpty()) return List.of();

    List<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingle = false;
    boolean inDouble = false;
    boolean escaping = false;

    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);

      if (escaping) {
        current.append(c);
        escaping = false;
        continue;
      }

      if (c == '\\') {
        escaping = true;
        continue;
      }

      if (c == '\'' && !inDouble) {
        inSingle = !inSingle;
        continue;
      }

      if (c == '"' && !inSingle) {
        inDouble = !inDouble;
        continue;
      }

      if (Character.isWhitespace(c) && !inSingle && !inDouble) {
        if (current.length() > 0) {
          out.add(current.toString());
          current.setLength(0);
        }
        continue;
      }

      current.append(c);
    }

    if (escaping) {
      current.append('\\');
    }

    if (inSingle || inDouble) {
      throw new IllegalArgumentException("Unterminated quoted script arguments.");
    }

    if (current.length() > 0) {
      out.add(current.toString());
    }

    return List.copyOf(out);
  }

  private static void putEnv(Map<String, String> env, String key, String value) {
    if (env == null) return;
    String k = Objects.toString(key, "").trim();
    if (k.isEmpty()) return;
    env.put(k, Objects.toString(value, ""));
  }

  private void appendHit(String ownerServerId, String interceptorId, InterceptorHit hit) {
    Map<String, List<InterceptorHit>> perServer =
            hitsByServer.computeIfAbsent(ownerServerId, __ -> new ConcurrentHashMap<>());
    List<InterceptorHit> list =
            perServer.computeIfAbsent(interceptorId, __ -> Collections.synchronizedList(new ArrayList<>()));

    synchronized (list) {
      list.add(hit);
      int overflow = list.size() - maxHitsPerInterceptor;
      if (overflow > 0) {
        list.subList(0, overflow).clear();
      }
    }
  }

  private boolean matchesChannelScope(InterceptorDefinition def, String channel) {
    if (def == null) return false;
    String chan = norm(channel);
    if (chan.isEmpty()) return false;

    List<String> includes = splitPatterns(def.channelIncludes());
    List<String> excludes = splitPatterns(def.channelExcludes());

    InterceptorRuleMode includeMode =
            def.channelIncludeMode() == null ? InterceptorRuleMode.GLOB : def.channelIncludeMode();
    boolean included = switch (includeMode) {
      case ALL -> true;
      case NONE -> false;
      case LIKE, GLOB, REGEX -> includes.isEmpty() || matchesAnyPattern(includeMode, includes, chan, true);
    };
    if (!included) return false;

    InterceptorRuleMode excludeMode =
            def.channelExcludeMode() == null ? InterceptorRuleMode.GLOB : def.channelExcludeMode();
    boolean excluded = switch (excludeMode) {
      case ALL -> true;
      case NONE -> false;
      case LIKE, GLOB, REGEX -> !excludes.isEmpty() && matchesAnyPattern(excludeMode, excludes, chan, true);
    };
    return !excluded;
  }

  private boolean matchesRule(
          InterceptorRule rule,
          InterceptorEventType eventType,
          String text,
          String fromNick,
          String fromHostmask
  ) {
    if (rule == null || !rule.enabled()) return false;
    boolean hasPattern = rule.hasAnyPattern();
    boolean hasEventSelector = !norm(rule.eventTypesCsv()).isBlank();
    if (!hasPattern && !hasEventSelector) return false;

    if (!matchesRuleEventType(rule, eventType)) return false;

    if (!matchesDimension(rule.messageMode(), rule.messagePattern(), text)) return false;
    if (!matchesDimension(rule.nickMode(), rule.nickPattern(), fromNick)) return false;
    return matchesDimension(rule.hostmaskMode(), rule.hostmaskPattern(), fromHostmask);
  }

  private boolean matchesRuleEventType(InterceptorRule rule, InterceptorEventType eventType) {
    if (rule == null) return false;
    InterceptorEventType type = eventType == null ? InterceptorEventType.MESSAGE : eventType;

    String csv = norm(rule.eventTypesCsv()).toLowerCase(Locale.ROOT);
    if (csv.isEmpty()) return true;

    Set<InterceptorEventType> allowed = eventTypeCache.computeIfAbsent(csv, __ -> {
      EnumSet<InterceptorEventType> parsed = InterceptorEventType.parseCsv(csv);
      if (parsed.isEmpty()) return Set.of();
      return Set.copyOf(parsed);
    });

    if (allowed.isEmpty()) return false;
    return allowed.contains(type);
  }

  private boolean matchesDimension(InterceptorRuleMode mode, String rawPattern, String value) {
    List<String> patterns = splitPatterns(rawPattern);
    if (patterns.isEmpty()) return true;
    String hay = Objects.toString(value, "");
    if (hay.isBlank()) return false;
    return matchesAnyPattern(mode, patterns, hay, false);
  }

  private boolean matchesAnyPattern(
          InterceptorRuleMode mode,
          List<String> patterns,
          String value,
          boolean strictText
  ) {
    InterceptorRuleMode m = mode == null ? InterceptorRuleMode.LIKE : mode;
    if (m == InterceptorRuleMode.ALL) return true;
    if (m == InterceptorRuleMode.NONE) return false;
    if (patterns == null || patterns.isEmpty()) return false;
    String hay = Objects.toString(value, "");

    for (String pattern : patterns) {
      if (pattern == null || pattern.isBlank()) continue;
      if (matchesPattern(m, pattern, hay, strictText)) return true;
    }
    return false;
  }

  private boolean matchesPattern(
          InterceptorRuleMode mode,
          String pattern,
          String value,
          boolean strictText
  ) {
    String source = Objects.toString(pattern, "");
    String hay = Objects.toString(value, "");
    if (source.isBlank() || hay.isBlank()) return false;

    return switch (mode) {
      case ALL -> true;
      case NONE -> false;
      case LIKE -> {
        if (strictText) {
          yield hay.equalsIgnoreCase(source);
        }
        yield hay.toLowerCase(Locale.ROOT).contains(source.toLowerCase(Locale.ROOT));
      }
      case GLOB -> {
        Pattern re = compileCached("glob:" + strictText, source, compileGlob(source, strictText));
        if (re == null) {
          yield false;
        }
        yield strictText ? re.matcher(hay).matches() : re.matcher(hay).find();
      }
      case REGEX -> {
        Pattern re = compileCached("regex:" + strictText, source, source);
        if (re == null) {
          yield false;
        }
        yield strictText ? re.matcher(hay).matches() : re.matcher(hay).find();
      }
    };
  }

  private Pattern compileCached(String kind, String source, String regex) {
    String key = kind + ":" + source;
    Pattern cached = patternCache.get(key);
    if (cached != null) return cached;
    try {
      Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      Pattern prev = patternCache.putIfAbsent(key, p);
      return prev != null ? prev : p;
    } catch (PatternSyntaxException ignored) {
      return null;
    }
  }

  private static String compileGlob(String glob, boolean strictText) {
    StringBuilder sb = new StringBuilder(glob.length() + 16);
    if (!strictText) sb.append(".*");
    for (int i = 0; i < glob.length(); i++) {
      char c = glob.charAt(i);
      switch (c) {
        case '*' -> sb.append(".*");
        case '?' -> sb.append('.');
        case '.', '\\', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|' -> sb.append('\\').append(c);
        default -> sb.append(c);
      }
    }
    if (!strictText) sb.append(".*");
    return sb.toString();
  }

  private static List<String> splitPatterns(String raw) {
    String text = norm(raw);
    if (text.isEmpty()) return List.of();

    String[] parts = text.split("[,\\n;]");
    ArrayList<String> out = new ArrayList<>(parts.length);
    for (String part : parts) {
      String p = norm(part);
      if (!p.isEmpty()) out.add(p);
    }
    return out;
  }

  private InterceptorDefinition normalizeForSave(InterceptorDefinition updated) {
    return new InterceptorDefinition(
            updated.id(),
            sanitizeName(updated.name()),
            updated.enabled(),
            updated.scopeServerId(),
            updated.channelIncludeMode(),
            updated.channelIncludes(),
            updated.channelExcludeMode(),
            updated.channelExcludes(),
            updated.actionSoundEnabled(),
            updated.actionStatusBarEnabled(),
            updated.actionToastEnabled(),
            updated.actionSoundId(),
            updated.actionSoundUseCustom(),
            updated.actionSoundCustomPath(),
            updated.actionScriptEnabled(),
            updated.actionScriptPath(),
            updated.actionScriptArgs(),
            updated.actionScriptWorkingDirectory(),
            updated.rules());
  }

  private void loadPersistedDefinitions() {
    if (runtimeConfig == null) return;
    try {
      Map<String, List<InterceptorDefinition>> persisted = runtimeConfig.readInterceptorDefinitions();
      if (persisted == null || persisted.isEmpty()) return;

      for (Map.Entry<String, List<InterceptorDefinition>> entry : persisted.entrySet()) {
        String sid = norm(entry.getKey());
        if (sid.isEmpty()) continue;

        LinkedHashMap<String, InterceptorDefinition> defs = new LinkedHashMap<>();
        List<InterceptorDefinition> rows = entry.getValue();
        if (rows != null) {
          for (InterceptorDefinition def : rows) {
            if (def == null) continue;
            InterceptorDefinition normalized = normalizeForSave(def);
            defs.put(normalized.id(), normalized);
          }
        }

        if (!defs.isEmpty()) {
          defsByServer.put(sid, defs);
        }
      }
    } catch (Exception e) {
      log.warn("[ircafe] Could not load interceptor definitions from runtime config", e);
    }
  }

  private void persistDefinitions() {
    if (runtimeConfig == null) return;
    Map<String, List<InterceptorDefinition>> snapshot = snapshotDefinitionsByServer();
    pendingPersistSnapshot.set(snapshot);
    long seq = persistRequestSeq.incrementAndGet();
    persistExecutor.execute(() -> persistDefinitionsNow(seq));
  }

  private void persistDefinitionsNow(long requestSeq) {
    if (runtimeConfig == null) return;
    if (requestSeq != persistRequestSeq.get()) return;
    Map<String, List<InterceptorDefinition>> snapshot = pendingPersistSnapshot.get();
    try {
      runtimeConfig.rememberInterceptorDefinitions(snapshot != null ? snapshot : Map.of());
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist interceptor definitions", e);
    }
  }

  private void flushPersistNow() {
    if (runtimeConfig == null) return;
    try {
      Map<String, List<InterceptorDefinition>> snapshot = snapshotDefinitionsByServer();
      runtimeConfig.rememberInterceptorDefinitions(snapshot);
    } catch (Exception e) {
      log.warn("[ircafe] Could not flush interceptor definitions", e);
    }
  }

  private Map<String, List<InterceptorDefinition>> snapshotDefinitionsByServer() {
    LinkedHashMap<String, List<InterceptorDefinition>> out = new LinkedHashMap<>();
    for (Map.Entry<String, LinkedHashMap<String, InterceptorDefinition>> entry : defsByServer.entrySet()) {
      String sid = norm(entry.getKey());
      if (sid.isEmpty()) continue;
      LinkedHashMap<String, InterceptorDefinition> defs = entry.getValue();
      if (defs == null || defs.isEmpty()) continue;

      List<InterceptorDefinition> snapshot;
      synchronized (defs) {
        snapshot = List.copyOf(defs.values());
      }
      if (!snapshot.isEmpty()) {
        out.put(sid, snapshot);
      }
    }
    return out;
  }

  private static String sanitizeName(String name) {
    String n = norm(name);
    if (n.isEmpty()) return "Interceptor";
    if (n.length() > 80) n = n.substring(0, 80).trim();
    return n.isEmpty() ? "Interceptor" : n;
  }

  private static String uniqueName(String base, java.util.Collection<InterceptorDefinition> existing) {
    String b = sanitizeName(base);
    if (existing == null || existing.isEmpty()) return b;

    boolean taken = existing.stream().anyMatch(d -> d != null && b.equalsIgnoreCase(d.name()));
    if (!taken) return b;

    for (int i = 2; i <= 9_999; i++) {
      String candidate = b + " " + i;
      boolean exists = existing.stream().anyMatch(d -> d != null && candidate.equalsIgnoreCase(d.name()));
      if (!exists) return candidate;
    }
    return b + " " + System.currentTimeMillis();
  }

  private static String norm(String s) {
    return Objects.toString(s, "").trim();
  }
}
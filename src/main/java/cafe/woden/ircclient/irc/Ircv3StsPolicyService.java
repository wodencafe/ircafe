package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-memory IRCv3 STS policy cache keyed by server host.
 *
 * <p>Policy learning rules: - Only learns policy from secure (TLS) connections. - Invalid policy
 * tokens are ignored. - {@code duration=0} clears a cached policy.
 *
 * <p>Policy application rules: - If a valid policy exists for a host, connects to that host are
 * upgraded to TLS. - If policy includes a port, it overrides the configured port.
 */
@Component
public class Ircv3StsPolicyService {

  private static final Logger log = LoggerFactory.getLogger(Ircv3StsPolicyService.class);

  record StsPolicy(
      String hostLower,
      long expiresAtEpochMs,
      Integer port,
      boolean preload,
      long durationSeconds,
      String rawValue) {
    boolean isExpired(long nowEpochMs) {
      return expiresAtEpochMs > 0 && nowEpochMs >= expiresAtEpochMs;
    }
  }

  private final ConcurrentMap<String, StsPolicy> byHostLower = new ConcurrentHashMap<>();
  private final RuntimeConfigStore runtimeConfig;

  @Autowired
  public Ircv3StsPolicyService(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
    loadPersistedPolicies();
  }

  Ircv3StsPolicyService() {
    this.runtimeConfig = null;
  }

  IrcProperties.Server applyPolicy(IrcProperties.Server configured) {
    if (configured == null) return null;
    String hostLower = normalizeHost(configured.host());
    if (hostLower.isEmpty()) return configured;

    StsPolicy policy = activePolicyForHost(hostLower).orElse(null);
    if (policy == null) return configured;

    int port = (policy.port() != null) ? policy.port().intValue() : configured.port();
    boolean tls = true;
    if (configured.tls() == tls && configured.port() == port) {
      return configured;
    }

    log.info(
        "[{}] applying STS policy for host={} (tls={}=>{}, port={}=>{}, preload={}, expiresAt={})",
        configured.id(),
        configured.host(),
        configured.tls(),
        tls,
        configured.port(),
        port,
        policy.preload(),
        policy.expiresAtEpochMs());

    return copyServerWithTransport(configured, port, tls);
  }

  void observeFromCapList(
      String serverId, String host, boolean secureConnection, String capListRaw) {
    String capList = Objects.toString(capListRaw, "").trim();
    if (capList.startsWith(":")) capList = capList.substring(1).trim();
    if (capList.isEmpty()) return;

    for (String token : capList.split("\\s+")) {
      String t = Objects.toString(token, "").trim();
      if (t.isEmpty()) continue;
      if (t.startsWith(":")) t = t.substring(1).trim();
      if (t.startsWith("-")) t = t.substring(1).trim();
      if (t.isEmpty()) continue;

      int eq = t.indexOf('=');
      String cap = eq >= 0 ? t.substring(0, eq).trim() : t;
      if (!"sts".equalsIgnoreCase(cap)) continue;

      String value = (eq >= 0 && eq + 1 < t.length()) ? t.substring(eq + 1).trim() : "";
      observeStsValue(serverId, host, secureConnection, value);
    }
  }

  Optional<StsPolicy> activePolicyForHost(String host) {
    String hostLower = normalizeHost(host);
    if (hostLower.isEmpty()) return Optional.empty();

    StsPolicy policy = byHostLower.get(hostLower);
    if (policy == null) return Optional.empty();

    long now = System.currentTimeMillis();
    if (policy.isExpired(now)) {
      if (byHostLower.remove(hostLower, policy)) {
        forgetPersistedPolicy(hostLower);
      }
      return Optional.empty();
    }
    return Optional.of(policy);
  }

  private void loadPersistedPolicies() {
    RuntimeConfigStore store = runtimeConfig;
    if (store == null) return;

    Map<String, RuntimeConfigStore.Ircv3StsPolicySnapshot> persisted = store.readIrcv3StsPolicies();
    if (persisted == null || persisted.isEmpty()) return;

    long now = System.currentTimeMillis();
    int loaded = 0;
    int dropped = 0;
    for (Map.Entry<String, RuntimeConfigStore.Ircv3StsPolicySnapshot> entry :
        persisted.entrySet()) {
      String hostLower = normalizeHost(entry.getKey());
      if (hostLower.isEmpty()) continue;

      RuntimeConfigStore.Ircv3StsPolicySnapshot snapshot = entry.getValue();
      if (snapshot == null) continue;

      long expiresAtEpochMs = snapshot.expiresAtEpochMs();
      if (expiresAtEpochMs <= now) {
        dropped++;
        forgetPersistedPolicy(hostLower);
        continue;
      }

      Integer port = snapshot.port();
      if (port != null && (port <= 0 || port > 65_535)) {
        port = null;
      }
      long durationSeconds = snapshot.durationSeconds();
      if (durationSeconds <= 0L) {
        long remainingMs = Math.max(1L, expiresAtEpochMs - now);
        durationSeconds = Math.max(1L, remainingMs / 1000L);
      }

      StsPolicy policy =
          new StsPolicy(
              hostLower,
              expiresAtEpochMs,
              port,
              snapshot.preload(),
              durationSeconds,
              Objects.toString(snapshot.rawValue(), ""));
      byHostLower.put(hostLower, policy);
      loaded++;
    }

    if (loaded > 0 || dropped > 0) {
      log.info("[ircafe] restored {} persisted STS policies (dropped {} expired)", loaded, dropped);
    }
  }

  private void observeStsValue(
      String serverId, String host, boolean secureConnection, String valueRaw) {
    String hostLower = normalizeHost(host);
    if (hostLower.isEmpty()) return;

    String value = Objects.toString(valueRaw, "").trim();
    if (value.isEmpty()) return;

    if (!secureConnection) {
      log.debug(
          "[{}] ignoring STS policy for host={} because connection is not secure (value={})",
          serverId,
          hostLower,
          value);
      return;
    }

    ParsedSts parsed = parseStsValue(value);
    if (parsed == null) {
      log.warn("[{}] ignoring invalid STS policy for host={}: {}", serverId, hostLower, value);
      return;
    }

    if (parsed.durationSeconds <= 0) {
      byHostLower.remove(hostLower);
      forgetPersistedPolicy(hostLower);
      log.info("[{}] cleared STS policy for host={} (duration=0)", serverId, hostLower);
      return;
    }

    long now = System.currentTimeMillis();
    long ttlMs = toMillisSaturated(parsed.durationSeconds);
    long expiresAt = addSaturated(now, ttlMs);

    StsPolicy next =
        new StsPolicy(
            hostLower, expiresAt, parsed.port, parsed.preload, parsed.durationSeconds, value);
    byHostLower.put(hostLower, next);
    persistPolicy(next);

    log.info(
        "[{}] learned STS policy host={} duration={}s port={} preload={} expiresAt={}",
        serverId,
        hostLower,
        parsed.durationSeconds,
        parsed.port,
        parsed.preload,
        expiresAt);
  }

  private void persistPolicy(StsPolicy policy) {
    RuntimeConfigStore store = runtimeConfig;
    if (store == null || policy == null) return;
    store.rememberIrcv3StsPolicy(
        policy.hostLower(),
        policy.expiresAtEpochMs(),
        policy.port(),
        policy.preload(),
        policy.durationSeconds(),
        policy.rawValue());
  }

  private void forgetPersistedPolicy(String hostLower) {
    RuntimeConfigStore store = runtimeConfig;
    if (store == null) return;
    store.forgetIrcv3StsPolicy(hostLower);
  }

  private static IrcProperties.Server copyServerWithTransport(
      IrcProperties.Server s, int port, boolean tls) {
    return new IrcProperties.Server(
        s.id(),
        s.host(),
        port,
        tls,
        s.serverPassword(),
        s.nick(),
        s.login(),
        s.realName(),
        s.sasl(),
        s.autoJoin(),
        s.perform(),
        s.proxy());
  }

  private static String normalizeHost(String host) {
    return Objects.toString(host, "").trim().toLowerCase(Locale.ROOT);
  }

  private static long toMillisSaturated(long seconds) {
    if (seconds <= 0) return 0L;
    long max = Long.MAX_VALUE / 1000L;
    if (seconds >= max) return Long.MAX_VALUE;
    return seconds * 1000L;
  }

  private static long addSaturated(long left, long right) {
    if (right <= 0) return left;
    if (left >= Long.MAX_VALUE - right) return Long.MAX_VALUE;
    return left + right;
  }

  private static ParsedSts parseStsValue(String rawValue) {
    String raw = Objects.toString(rawValue, "").trim();
    if (raw.isEmpty()) return null;

    Map<String, String> attrs = new HashMap<>();
    for (String partRaw : raw.split(",")) {
      String part = Objects.toString(partRaw, "").trim();
      if (part.isEmpty()) continue;
      int eq = part.indexOf('=');
      if (eq >= 0) {
        String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        String val = part.substring(eq + 1).trim();
        if (!key.isEmpty()) attrs.put(key, val);
      } else {
        attrs.put(part.toLowerCase(Locale.ROOT), "true");
      }
    }

    String durationRaw = attrs.get("duration");
    if (durationRaw == null || durationRaw.isBlank()) return null;
    long durationSeconds;
    try {
      durationSeconds = Long.parseLong(durationRaw);
    } catch (NumberFormatException e) {
      return null;
    }
    if (durationSeconds < 0) return null;

    Integer port = null;
    String portRaw = attrs.get("port");
    if (portRaw != null && !portRaw.isBlank()) {
      int parsedPort;
      try {
        parsedPort = Integer.parseInt(portRaw);
      } catch (NumberFormatException e) {
        return null;
      }
      if (parsedPort <= 0 || parsedPort > 65_535) return null;
      port = parsedPort;
    }

    boolean preload = attrs.containsKey("preload");
    return new ParsedSts(durationSeconds, port, preload);
  }

  private record ParsedSts(long durationSeconds, Integer port, boolean preload) {}
}

package cafe.woden.ircclient.irc.soju;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.SojuProperties;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Persisted auto-connect preferences for Soju-discovered networks.
 *
 * <p>This store keeps a mapping of:
 *
 * <pre>
 *   bouncerServerId -> networkName -> enabled
 * </pre>
 *
 * <p>Network names are canonicalized using Soju's name sanitizer and compared case-insensitively.
 */
@Component
public class SojuAutoConnectStore {

  private final RuntimeConfigStore runtimeConfig;

  // bouncerId -> (networkKey -> true)
  private final LinkedHashMap<String, LinkedHashMap<String, Boolean>> enabled =
      new LinkedHashMap<>();

  private final BehaviorProcessor<Map<String, Map<String, Boolean>>> updates =
      BehaviorProcessor.create();

  public SojuAutoConnectStore(SojuProperties props, RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;

    if (props != null) {
      Map<String, Map<String, Boolean>> seed = props.autoConnectCopy();
      for (var b : seed.entrySet()) {
        String bouncer = norm(b.getKey());
        if (bouncer == null) continue;

        LinkedHashMap<String, Boolean> nets = new LinkedHashMap<>();
        Map<String, Boolean> m = b.getValue();
        if (m != null) {
          for (var e : m.entrySet()) {
            if (e == null || !Boolean.TRUE.equals(e.getValue())) continue;
            String netKey = normNetKey(e.getKey());
            if (netKey == null) continue;
            nets.put(netKey, Boolean.TRUE);
          }
        }

        if (!nets.isEmpty()) {
          enabled.put(bouncer, nets);
        }
      }
    }

    updates.onNext(snapshot());
  }

  public Flowable<Map<String, Map<String, Boolean>>> updates() {
    return updates.onBackpressureLatest();
  }

  public synchronized Map<String, Map<String, Boolean>> snapshot() {
    Map<String, Map<String, Boolean>> out = new LinkedHashMap<>();
    for (var b : enabled.entrySet()) {
      out.put(b.getKey(), Map.copyOf(b.getValue()));
    }
    return Map.copyOf(out);
  }

  public synchronized Map<String, Boolean> networksForBouncer(String bouncerServerId) {
    String bouncer = findBouncerKey(bouncerServerId);
    if (bouncer == null) return Map.of();
    LinkedHashMap<String, Boolean> m = enabled.get(bouncer);
    if (m == null || m.isEmpty()) return Map.of();
    return Map.copyOf(m);
  }

  /** Convenience alias. */
  public synchronized Map<String, Boolean> rulesForBouncer(String bouncerServerId) {
    return networksForBouncer(bouncerServerId);
  }

  /** Convenience alias. */
  public synchronized boolean isEnabled(String bouncerServerId, String networkName) {
    return isAutoConnectEnabled(bouncerServerId, networkName);
  }

  /** Convenience alias. */
  public synchronized void setEnabled(String bouncerServerId, String networkName, boolean enable) {
    setAutoConnectEnabled(bouncerServerId, networkName, enable);
  }

  public synchronized boolean isAutoConnectEnabled(String bouncerServerId, String networkName) {
    String bouncer = findBouncerKey(bouncerServerId);
    if (bouncer == null) return false;
    String netKey = normNetKey(networkName);
    if (netKey == null) return false;
    Map<String, Boolean> m = enabled.get(bouncer);
    return m != null && Boolean.TRUE.equals(m.get(netKey));
  }

  public synchronized void setAutoConnectEnabled(
      String bouncerServerId, String networkName, boolean enable) {
    String bouncer = norm(bouncerServerId);
    String netKey = normNetKey(networkName);
    if (bouncer == null || netKey == null) return;

    if (enable) {
      enabled.computeIfAbsent(bouncer, k -> new LinkedHashMap<>()).put(netKey, Boolean.TRUE);
    } else {
      LinkedHashMap<String, Boolean> m = enabled.get(bouncer);
      if (m != null) {
        m.remove(netKey);
        if (m.isEmpty()) enabled.remove(bouncer);
      }
    }

    runtimeConfig.rememberSojuAutoConnectNetwork(bouncer, netKey, enable);
    emit();
  }

  private void emit() {
    updates.onNext(snapshot());
  }

  private String findBouncerKey(String bouncerServerId) {
    String bouncer = norm(bouncerServerId);
    if (bouncer == null) return null;
    if (enabled.containsKey(bouncer)) return bouncer;

    // Fall back to case-insensitive match if the caller differs only by case.
    for (String k : enabled.keySet()) {
      if (k.equalsIgnoreCase(bouncer)) return k;
    }

    return bouncer;
  }

  private static String norm(String s) {
    String v = Objects.toString(s, "").trim();
    return v.isEmpty() ? null : v;
  }

  private static String normNetKey(String networkName) {
    String v = PircbotxSojuParsers.sanitizeNetworkName(networkName);
    v = Objects.toString(v, "").trim();
    if (v.isEmpty()) return null;

    // Make keys stable + user-friendly: collapse runs of '_' and trim leading/trailing '_'.
    v = v.replaceAll("_+", "_");
    while (v.startsWith("_")) v = v.substring(1);
    while (v.endsWith("_")) v = v.substring(0, v.length() - 1);

    v = v.trim();
    if (v.isEmpty()) return null;
    return v.toLowerCase(Locale.ROOT);
  }
}

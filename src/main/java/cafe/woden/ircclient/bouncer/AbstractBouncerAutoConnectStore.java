package cafe.woden.ircclient.bouncer;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared state/persistence behavior for bouncer auto-connect stores. */
@ApplicationLayer
public abstract class AbstractBouncerAutoConnectStore implements BouncerAutoConnectStore {

  private final RuntimeConfigStore runtimeConfig;

  // bouncerId -> (networkKey -> true)
  private final LinkedHashMap<String, LinkedHashMap<String, Boolean>> enabled =
      new LinkedHashMap<>();

  private final BehaviorProcessor<Map<String, Map<String, Boolean>>> updates =
      BehaviorProcessor.create();

  protected AbstractBouncerAutoConnectStore(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
  }

  protected final synchronized void initialize(Map<String, Map<String, Boolean>> seed) {
    enabled.clear();
    if (seed != null) {
      for (var b : seed.entrySet()) {
        String bouncer = norm(b.getKey());
        if (bouncer == null) continue;

        LinkedHashMap<String, Boolean> nets = new LinkedHashMap<>();
        Map<String, Boolean> m = b.getValue();
        if (m != null) {
          for (var e : m.entrySet()) {
            if (e == null || !Boolean.TRUE.equals(e.getValue())) continue;
            String netKey = normalizeNetworkKey(e.getKey());
            if (netKey == null) continue;
            nets.put(netKey, Boolean.TRUE);
          }
        }

        if (!nets.isEmpty()) {
          enabled.put(bouncer, nets);
        }
      }
    }
    emit();
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

  @Override
  public synchronized boolean isEnabled(String bouncerServerId, String networkName) {
    return isAutoConnectEnabled(bouncerServerId, networkName);
  }

  @Override
  public synchronized void setEnabled(String bouncerServerId, String networkName, boolean enable) {
    setAutoConnectEnabled(bouncerServerId, networkName, enable);
  }

  public synchronized boolean isAutoConnectEnabled(String bouncerServerId, String networkName) {
    String bouncer = findBouncerKey(bouncerServerId);
    if (bouncer == null) return false;
    String netKey = normalizeNetworkKey(networkName);
    if (netKey == null) return false;
    Map<String, Boolean> m = enabled.get(bouncer);
    return m != null && Boolean.TRUE.equals(m.get(netKey));
  }

  public synchronized void setAutoConnectEnabled(
      String bouncerServerId, String networkName, boolean enable) {
    String bouncer = norm(bouncerServerId);
    String netKey = normalizeNetworkKey(networkName);
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

    persistAutoConnectRule(bouncer, netKey, enable);
    emit();
  }

  protected RuntimeConfigStore runtimeConfig() {
    return runtimeConfig;
  }

  protected abstract String normalizeNetworkKey(String networkName);

  protected abstract void persistAutoConnectRule(
      String bouncerServerId, String networkKey, boolean enable);

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
}

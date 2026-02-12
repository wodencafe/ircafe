package cafe.woden.ircclient.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ZNC bouncer integration settings.
 */
@ConfigurationProperties(prefix = "ircafe.znc")
public record ZncProperties(
    Map<String, Map<String, Boolean>> autoConnect,
    Discovery discovery
) {

  public ZncProperties {
    if (autoConnect == null) autoConnect = Map.of();
    if (discovery == null) discovery = new Discovery(true);
  }

  /** ZNC network discovery settings. */
  public record Discovery(boolean enabled) {
    public Discovery {
      // Defaults are handled by the parent record ctor (enabled=true).
    }
  }

  public Map<String, Boolean> autoConnectForBouncer(String bouncerServerId) {
    String id = Objects.toString(bouncerServerId, "").trim();
    if (id.isEmpty()) return Map.of();
    Map<String, Boolean> m = autoConnect.get(id);
    if (m == null || m.isEmpty()) return Map.of();
    return Map.copyOf(m);
  }

  public Map<String, Map<String, Boolean>> autoConnectCopy() {
    Map<String, Map<String, Boolean>> out = new LinkedHashMap<>();
    for (var e : autoConnect.entrySet()) {
      if (e == null) continue;
      String k = Objects.toString(e.getKey(), "").trim();
      if (k.isEmpty()) continue;
      Map<String, Boolean> v = (e.getValue() == null) ? Map.of() : Map.copyOf(e.getValue());
      out.put(k, v);
    }
    return out;
  }
}

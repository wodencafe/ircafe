package cafe.woden.ircclient.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Soju bouncer integration settings. */
@ConfigurationProperties(prefix = "ircafe.soju")
public record SojuProperties(Map<String, Map<String, Boolean>> autoConnect, Discovery discovery) {

  public SojuProperties {
    if (autoConnect == null) autoConnect = Map.of();
    if (discovery == null) discovery = new Discovery(true);
  }

  /** Soju discovery settings. */
  public record Discovery(boolean enabled) {
    public Discovery {
      // Keep defaults stable even when config sections are partially present.
      // If the user omits this value entirely, we default to true.
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

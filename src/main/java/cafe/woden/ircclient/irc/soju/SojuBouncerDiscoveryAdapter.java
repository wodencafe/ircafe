package cafe.woden.ircclient.irc.soju;

import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Translates soju protocol discovery lines into generic bouncer discovery events. */
public class SojuBouncerDiscoveryAdapter {

  public BouncerDiscoveredNetwork parseBouncerNetworkLine(String originServerId, String rawLine) {
    PircbotxSojuParsers.ParsedBouncerNetwork parsed =
        PircbotxSojuParsers.parseBouncerNetworkLine(rawLine);
    if (parsed == null) return null;

    HashMap<String, String> attrs = new HashMap<>();
    if (parsed.attrs() != null) {
      attrs.putAll(parsed.attrs());
    }
    attrs.put("source", SojuBouncerNetworkMappingStrategy.BACKEND_ID);

    String name = Objects.toString(parsed.name(), "").trim();
    if (name.isEmpty()) name = "net-" + parsed.netId();

    return new BouncerDiscoveredNetwork(
        SojuBouncerNetworkMappingStrategy.BACKEND_ID,
        originServerId,
        parsed.netId(),
        name,
        name,
        loginUserHint(attrs),
        capabilityFlags(attrs),
        Map.copyOf(attrs));
  }

  public BouncerDiscoveredNetwork fromSojuNetwork(SojuNetwork network) {
    if (network == null) return null;
    HashMap<String, String> attrs = new HashMap<>();
    if (network.attrs() != null) {
      attrs.putAll(network.attrs());
    }
    attrs.put("source", SojuBouncerNetworkMappingStrategy.BACKEND_ID);
    return new BouncerDiscoveredNetwork(
        SojuBouncerNetworkMappingStrategy.BACKEND_ID,
        network.bouncerServerId(),
        network.netId(),
        network.name(),
        network.name(),
        loginUserHint(attrs),
        capabilityFlags(attrs),
        Map.copyOf(attrs));
  }

  private static String loginUserHint(Map<String, String> attrs) {
    String hint = firstNonBlank(attrs, "loginUser", "login", "username", "user");
    return hint == null ? null : hint.trim();
  }

  private static Set<String> capabilityFlags(Map<String, String> attrs) {
    String raw = firstNonBlank(attrs, "capabilities", "caps");
    if (raw == null) return Set.of();
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String part : raw.split("[,\\s]+")) {
      String cap = normalizeLower(part);
      if (cap != null) out.add(cap);
    }
    return out.isEmpty() ? Set.of() : Set.copyOf(out);
  }

  private static String firstNonBlank(Map<String, String> attrs, String... keys) {
    if (attrs == null || attrs.isEmpty() || keys == null) return null;
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      String value = attrs.get(key);
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private static String normalizeLower(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? null : v.toLowerCase(java.util.Locale.ROOT);
  }
}

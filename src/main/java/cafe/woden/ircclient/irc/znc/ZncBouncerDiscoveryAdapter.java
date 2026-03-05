package cafe.woden.ircclient.irc.znc;

import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.irc.PircbotxZncParsers;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Translates ZNC *status ListNetworks lines into generic bouncer discovery events. */
public class ZncBouncerDiscoveryAdapter {

  public BouncerDiscoveredNetwork parseListNetworksRow(String originServerId, String messageText) {
    PircbotxZncParsers.ParsedListNetworksRow row =
        PircbotxZncParsers.parseListNetworksRow(messageText);
    if (row == null) return null;

    String name = Objects.toString(row.name, "").trim();
    if (name.isEmpty()) return null;

    String networkId = ZncEphemeralNaming.normalizeNetworkKey(name);
    if (networkId == null || networkId.isBlank()) {
      networkId = name.toLowerCase(Locale.ROOT);
    }

    HashMap<String, String> attrs = new HashMap<>();
    attrs.put("source", ZncBouncerNetworkMappingStrategy.BACKEND_ID);
    if (row.onIrc != null) {
      attrs.put("onIrc", String.valueOf(row.onIrc));
    }

    return new BouncerDiscoveredNetwork(
        ZncBouncerNetworkMappingStrategy.BACKEND_ID,
        originServerId,
        networkId,
        name,
        name,
        loginUserHint(attrs),
        capabilityFlags(attrs),
        Map.copyOf(attrs));
  }

  public boolean looksLikeListNetworksDoneLine(String messageText) {
    return PircbotxZncParsers.looksLikeListNetworksDoneLine(messageText);
  }

  public BouncerDiscoveredNetwork fromZncNetwork(ZncNetwork network) {
    if (network == null) return null;

    String name = Objects.toString(network.name(), "").trim();
    String networkId = ZncEphemeralNaming.normalizeNetworkKey(name);
    if (networkId == null || networkId.isBlank()) {
      networkId = name.toLowerCase(Locale.ROOT);
    }

    HashMap<String, String> attrs = new HashMap<>();
    attrs.put("source", ZncBouncerNetworkMappingStrategy.BACKEND_ID);
    if (network.onIrc() != null) {
      attrs.put("onIrc", String.valueOf(network.onIrc()));
    }

    return new BouncerDiscoveredNetwork(
        ZncBouncerNetworkMappingStrategy.BACKEND_ID,
        network.bouncerServerId(),
        networkId,
        name,
        name,
        loginUserHint(attrs),
        capabilityFlags(attrs),
        Map.copyOf(attrs));
  }

  private static String loginUserHint(Map<String, String> attrs) {
    return firstNonBlank(attrs, "loginUser", "login", "username", "user");
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
      if (value != null && !value.isBlank()) return value.trim();
    }
    return null;
  }

  private static String normalizeLower(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? null : v.toLowerCase(Locale.ROOT);
  }
}

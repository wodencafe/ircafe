package cafe.woden.ircclient.bouncer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;

/** Generic discovered-network event emitted by bouncer protocol adapters. */
@ValueObject
public record BouncerDiscoveredNetwork(
    String backendId,
    String originServerId,
    String networkId,
    String displayName,
    String autoConnectName,
    String loginUserHint,
    Set<String> capabilityFlags,
    Map<String, String> attributes) {

  public BouncerDiscoveredNetwork(
      String backendId,
      String originServerId,
      String networkId,
      String displayName,
      String autoConnectName,
      Map<String, String> attributes) {
    this(
        backendId,
        originServerId,
        networkId,
        displayName,
        autoConnectName,
        null,
        Set.of(),
        attributes);
  }

  public BouncerDiscoveredNetwork {
    backendId = normalizeLower(backendId);
    originServerId = normalize(originServerId);
    networkId = normalize(networkId);
    displayName = normalize(displayName);
    autoConnectName = normalize(autoConnectName);
    attributes = sanitizeAttributes(attributes);
    loginUserHint = normalize(loginUserHint);
    if (loginUserHint == null) {
      loginUserHint = firstAttribute(attributes, "loginUser", "login", "username", "user");
    }
    capabilityFlags = normalizeCapabilities(capabilityFlags);
    if (capabilityFlags.isEmpty()) {
      capabilityFlags = parseCapabilities(attributes);
    }

    if (backendId == null) throw new IllegalArgumentException("backendId is required");
    if (originServerId == null) throw new IllegalArgumentException("originServerId is required");
    if (networkId == null) throw new IllegalArgumentException("networkId is required");
    if (displayName == null) throw new IllegalArgumentException("displayName is required");
    if (autoConnectName == null) autoConnectName = displayName;
  }

  private static String normalize(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? null : v;
  }

  private static String normalizeLower(String value) {
    String v = normalize(value);
    return v == null ? null : v.toLowerCase(Locale.ROOT);
  }

  private static Map<String, String> sanitizeAttributes(Map<String, String> attributes) {
    if (attributes == null || attributes.isEmpty()) return Map.of();
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      if (entry == null) continue;
      String key = normalize(entry.getKey());
      if (key == null) continue;
      String value = normalize(entry.getValue());
      out.put(key, value == null ? "" : value);
    }
    return out.isEmpty() ? Map.of() : Map.copyOf(out);
  }

  private static Set<String> normalizeCapabilities(Set<String> flags) {
    if (flags == null || flags.isEmpty()) return Set.of();
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String flag : flags) {
      String normalized = normalizeLower(flag);
      if (normalized == null) continue;
      out.add(normalized);
    }
    return out.isEmpty() ? Set.of() : Set.copyOf(out);
  }

  private static Set<String> parseCapabilities(Map<String, String> attributes) {
    String raw = firstAttribute(attributes, "capabilities", "caps");
    if (raw == null) return Set.of();
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String part : raw.split("[,\\s]+")) {
      String flag = normalizeLower(part);
      if (flag == null) continue;
      out.add(flag);
    }
    return out.isEmpty() ? Set.of() : Set.copyOf(out);
  }

  private static String firstAttribute(Map<String, String> attrs, String... keys) {
    if (attrs == null || attrs.isEmpty() || keys == null) return null;
    for (String key : keys) {
      String normalized = normalize(key);
      if (normalized == null) continue;
      String value = normalize(attrs.get(normalized));
      if (value != null) return value;
    }
    return null;
  }

  public boolean hasCapability(String capabilityFlag) {
    String flag = normalizeLower(capabilityFlag);
    return flag != null && capabilityFlags.contains(flag);
  }
}

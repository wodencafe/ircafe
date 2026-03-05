package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.bouncer.GenericBouncerNetworkMappingStrategy;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Parses generic bouncer discovery lines into backend-agnostic discovery events.
 *
 * <p>Supported format:
 *
 * <pre>
 *   :server BOUNCER NETWORK <networkId> name=<display>;auto=<name>;k=v
 * </pre>
 */
public class GenericBouncerDiscoveryAdapter {

  public BouncerDiscoveredNetwork parseNetworkLine(String originServerId, String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return null;
    String s = rawLine.trim();

    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 1 && sp + 1 < s.length()) {
        s = s.substring(sp + 1).trim();
      }
    }

    String trailing = null;
    int idx = s.indexOf(" :");
    if (idx >= 0) {
      trailing = s.substring(idx + 2);
      s = s.substring(0, idx).trim();
    }

    if (s.isEmpty()) return null;
    String[] parts = s.split("\\s+");
    if (parts.length < 3) return null;
    if (!"BOUNCER".equalsIgnoreCase(parts[0])) return null;
    if (!"NETWORK".equalsIgnoreCase(parts[1])) return null;

    String networkId = normalize(parts[2]);
    if (networkId == null) return null;

    String attrsRaw = null;
    if (parts.length >= 4) {
      StringBuilder sb = new StringBuilder();
      for (int i = 3; i < parts.length; i++) {
        if (sb.length() > 0) sb.append(' ');
        sb.append(parts[i]);
      }
      attrsRaw = sb.toString().trim();
    }
    if ((attrsRaw == null || attrsRaw.isBlank()) && trailing != null && !trailing.isBlank()) {
      attrsRaw = trailing.trim();
    }

    Map<String, String> attrs = parseAttrs(attrsRaw);
    String backend = normalize(attrs.get("backend"));
    if (backend == null) {
      backend = GenericBouncerNetworkMappingStrategy.BACKEND_ID;
    }

    String display = normalize(attrs.get("name"));
    if (display == null) {
      display = normalize(attrs.get("display"));
    }
    if (display == null) {
      display = networkId;
    }

    String autoConnect = normalize(attrs.get("auto"));
    if (autoConnect == null) {
      autoConnect = normalize(attrs.get("autoconnect"));
    }
    if (autoConnect == null) {
      autoConnect = display;
    }
    String loginUserHint = firstNonBlank(attrs, "loginUser", "login", "username", "user");
    Set<String> capabilities = parseCapabilities(attrs);

    HashMap<String, String> outAttrs = new HashMap<>(attrs);
    outAttrs.put("source", "generic-protocol");

    return new BouncerDiscoveredNetwork(
        backend.toLowerCase(Locale.ROOT),
        originServerId,
        networkId,
        display,
        autoConnect,
        loginUserHint,
        capabilities,
        Map.copyOf(outAttrs));
  }

  private static Map<String, String> parseAttrs(String attrsRaw) {
    if (attrsRaw == null || attrsRaw.isBlank()) return Map.of();
    String s = attrsRaw.trim();
    HashMap<String, String> out = new HashMap<>();
    for (String part : s.split(";")) {
      if (part == null) continue;
      String p = part.trim();
      if (p.isEmpty()) continue;
      int eq = p.indexOf('=');
      if (eq < 0) {
        out.putIfAbsent(p, "");
        continue;
      }
      String k = p.substring(0, eq).trim();
      String v = p.substring(eq + 1).trim();
      if (!k.isEmpty()) out.put(k, v);
    }
    return out.isEmpty() ? Map.of() : out;
  }

  private static String normalize(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? null : v;
  }

  private static String firstNonBlank(Map<String, String> attrs, String... keys) {
    if (attrs == null || attrs.isEmpty() || keys == null) return null;
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      String value = normalize(attrs.get(key));
      if (value != null) return value;
    }
    return null;
  }

  private static Set<String> parseCapabilities(Map<String, String> attrs) {
    String raw = firstNonBlank(attrs, "capabilities", "caps");
    if (raw == null) return Set.of();
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String part : raw.split("[,\\s]+")) {
      String cap = normalize(part);
      if (cap == null) continue;
      out.add(cap.toLowerCase(Locale.ROOT));
    }
    return out.isEmpty() ? Set.of() : Set.copyOf(out);
  }
}

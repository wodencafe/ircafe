package cafe.woden.ircclient.config.runtime.server;

import static cafe.woden.ircclient.config.yaml.RuntimeConfigYamlSupport.containsIgnoreCase;
import static cafe.woden.ircclient.config.yaml.RuntimeConfigYamlSupport.sanitizeStringList;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.BackendDescriptorCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure mapping helpers for the top-level persisted IRC server list. */
final class RuntimeConfigServerListCodec {

  private RuntimeConfigServerListCodec() {}

  static List<Map<String, Object>> serverMaps(List<IrcProperties.Server> servers) {
    ArrayList<Map<String, Object>> out = new ArrayList<>();
    if (servers == null) return out;

    for (IrcProperties.Server server : servers) {
      if (server == null) continue;
      out.add(toServerMap(server));
    }
    return out;
  }

  static List<String> readServerIds(Object rawServers, IrcProperties defaults) {
    if (!(rawServers instanceof List<?> servers) || servers.isEmpty()) {
      return defaultServerIds(defaults);
    }

    ArrayList<String> out = new ArrayList<>();
    for (Object item : servers) {
      if (!(item instanceof Map<?, ?> server)) continue;
      String id = Objects.toString(server.get("id"), "").trim();
      if (id.isEmpty()) continue;
      if (containsIgnoreCase(out, id)) continue;
      out.add(id);
    }
    if (out.isEmpty()) return defaultServerIds(defaults);
    return List.copyOf(out);
  }

  static Map<String, List<String>> readExplicitServerAutoJoinById(Object rawServers) {
    if (!(rawServers instanceof List<?> servers) || servers.isEmpty()) return Map.of();

    LinkedHashMap<String, List<String>> out = new LinkedHashMap<>();
    for (Object item : servers) {
      if (!(item instanceof Map<?, ?> server)) continue;
      String id = Objects.toString(server.get("id"), "").trim();
      if (id.isEmpty()) continue;
      if (!server.containsKey("autoJoin")) continue;
      out.put(id, sanitizeStringList(server.get("autoJoin")));
    }
    return out.isEmpty() ? Map.of() : Map.copyOf(out);
  }

  private static List<String> defaultServerIds(IrcProperties defaults) {
    if (defaults == null || defaults.servers() == null || defaults.servers().isEmpty()) {
      return List.of();
    }
    ArrayList<String> ids = new ArrayList<>();
    for (IrcProperties.Server s : defaults.servers()) {
      if (s == null || s.id() == null || s.id().isBlank()) continue;
      if (containsIgnoreCase(ids, s.id())) continue;
      ids.add(s.id().trim());
    }
    return List.copyOf(ids);
  }

  private static Map<String, Object> toServerMap(IrcProperties.Server s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", s.id());
    m.put("host", s.host());
    m.put("port", s.port());
    m.put("tls", s.tls());
    m.put("trustAllCertificates", s.trustAllCertificates());
    String backendId = BackendDescriptorCatalog.builtIns().normalizeIdOrDefault(s.backendId());
    if (!backendId.equals(
        BackendDescriptorCatalog.builtIns().idFor(IrcProperties.Server.Backend.IRC))) {
      m.put("backend", backendId);
    }
    if (s.serverPassword() != null && !s.serverPassword().isBlank()) {
      m.put("serverPassword", s.serverPassword());
    }
    if (s.nick() != null) m.put("nick", s.nick());
    if (s.login() != null && !s.login().isBlank()) m.put("login", s.login());
    if (s.realName() != null && !s.realName().isBlank()) m.put("realName", s.realName());
    if (s.autoJoin() != null && !s.autoJoin().isEmpty()) {
      m.put("autoJoin", new ArrayList<>(s.autoJoin()));
    }
    if (s.perform() != null && !s.perform().isEmpty()) {
      m.put("perform", new ArrayList<>(s.perform()));
    }
    if (s.sasl() != null && s.sasl().enabled()) {
      Map<String, Object> sasl = new LinkedHashMap<>();
      sasl.put("enabled", true);
      sasl.put("username", s.sasl().username());
      sasl.put("password", s.sasl().password());
      if (s.sasl().mechanism() != null && !s.sasl().mechanism().isBlank()) {
        sasl.put("mechanism", s.sasl().mechanism());
      }
      // Persist only when diverging from the default strict behavior.
      // Default (when omitted) is: disconnectOnFailure = true.
      if (s.sasl().disconnectOnFailure() != null && !s.sasl().disconnectOnFailure()) {
        sasl.put("disconnectOnFailure", false);
      }
      m.put("sasl", sasl);
    }
    if (s.nickserv() != null && s.nickserv().enabled()) {
      Map<String, Object> nickserv = new LinkedHashMap<>();
      nickserv.put("enabled", true);
      nickserv.put("password", s.nickserv().password());
      if (s.nickserv().service() != null
          && !s.nickserv().service().isBlank()
          && !"NickServ".equalsIgnoreCase(s.nickserv().service().trim())) {
        nickserv.put("service", s.nickserv().service());
      }
      if (s.nickserv().delayJoinUntilIdentified() != null
          && !s.nickserv().delayJoinUntilIdentified()) {
        nickserv.put("delayJoinUntilIdentified", false);
      }
      m.put("nickserv", nickserv);
    }

    // Optional per-server SOCKS5 proxy override.
    // If present with enabled=false, this represents an explicit "disable proxy" for this server.
    if (s.proxy() != null) {
      IrcProperties.Proxy p = s.proxy();
      Map<String, Object> proxy = new LinkedHashMap<>();
      proxy.put("enabled", p.enabled());
      proxy.put("host", Objects.toString(p.host(), "").trim());
      proxy.put("port", Math.max(0, p.port()));
      proxy.put("username", Objects.toString(p.username(), "").trim());
      proxy.put("password", Objects.toString(p.password(), ""));
      proxy.put("remoteDns", p.remoteDns());
      proxy.put("connectTimeoutMs", Math.max(0L, p.connectTimeoutMs()));
      proxy.put("readTimeoutMs", Math.max(0L, p.readTimeoutMs()));
      m.put("proxy", proxy);
    }
    return m;
  }
}

package cafe.woden.ircclient.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Persists runtime state (nick + auto-join channels + server list edits) to a YAML file.
 *
 * <p>This file is also imported via spring.config.import (optional) so it overrides defaults on the next run.
 */
@Component
public class RuntimeConfigStore {

  private static final Logger log = LoggerFactory.getLogger(RuntimeConfigStore.class);

  private final Path file;
  private final IrcProperties defaults;
  private final Yaml yaml;

  public RuntimeConfigStore(
      @Value("${ircafe.runtime-config:${user.home}/.config/ircafe/ircafe.yml}") String filePath,
      IrcProperties defaults
  ) {
    this.file = Paths.get(Objects.requireNonNullElse(filePath, "").trim());
    this.defaults = defaults;

    DumperOptions opts = new DumperOptions();
    opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    opts.setPrettyFlow(true);
    opts.setIndent(2);
    // SnakeYAML requires indicatorIndent < indent.
    // With indent=2, indicatorIndent=1 keeps list indicators aligned nicely.
    opts.setIndicatorIndent(1);
    opts.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
    this.yaml = new Yaml(opts);

    ensureFileExistsWithServers();
  }

  /**
   * Ensure the file exists.
   *
   * <p>Important behavior:
   * <ul>
   *   <li>If {@code irc.servers} is missing, we seed it from defaults.</li>
   *   <li>If {@code irc.servers} exists (even empty), we treat it as authoritative and do NOT re-add defaults.</li>
   * </ul>
   */
  public synchronized void ensureFileExistsWithServers() {
    try {
      if (file.toString().isBlank()) return;

      Path parent = file.getParent();
      if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent);
      }

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();

      Map<String, Object> irc = getOrCreateMap(doc, "irc");

      // If the key exists, don't overwrite it. This is what makes removals stick.
      if (!irc.containsKey("servers")) {
        List<Map<String, Object>> seeded = new ArrayList<>();
        if (defaults != null && defaults.servers() != null) {
          for (IrcProperties.Server s : defaults.servers()) {
            if (s == null) continue;
            seeded.add(toServerMap(s));
          }
        }
        irc.put("servers", seeded);
        writeFile(doc);
      }
    } catch (Exception e) {

      log.warn("[ircafe] Could not ensure runtime config file '{}'", file, e);
    }
  }

  /** Overwrite the full {@code irc.servers} list (used by the servers UI / registry). */
  public synchronized void writeServers(List<IrcProperties.Server> servers) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> irc = getOrCreateMap(doc, "irc");

      List<Map<String, Object>> out = new ArrayList<>();
      if (servers != null) {
        for (IrcProperties.Server s : servers) {
          if (s == null) continue;
          out.add(toServerMap(s));
        }
      }

      irc.put("servers", out);
      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist servers list to '{}'", file, e);
    }
  }

  public synchronized void rememberJoinedChannel(String serverId, String channel) {
    updateServer(serverId, server -> {
      String chan = Objects.toString(channel, "").trim();
      if (chan.isEmpty()) return;

      List<String> autoJoin = getOrCreateStringList(server, "autoJoin");
      if (autoJoin.stream().noneMatch(c -> c.equalsIgnoreCase(chan))) {
        autoJoin.add(chan);
      }
    });
  }

  public synchronized void forgetJoinedChannel(String serverId, String channel) {
    updateServer(serverId, server -> {
      String chan = Objects.toString(channel, "").trim();
      if (chan.isEmpty()) return;

      Object o = server.get("autoJoin");
      if (!(o instanceof List<?> list)) return;
      @SuppressWarnings("unchecked")
      List<String> autoJoin = (List<String>) list;
      autoJoin.removeIf(c -> c != null && c.equalsIgnoreCase(chan));
    });
  }

  public synchronized void rememberNick(String serverId, String nick) {
    updateServer(serverId, server -> {
      String n = Objects.toString(nick, "").trim();
      if (!n.isEmpty()) server.put("nick", n);
    });
  }

  public synchronized void rememberUiSettings(String theme, String chatFontFamily, int chatFontSize) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      if (theme != null && !theme.isBlank()) ui.put("theme", theme);
      if (chatFontFamily != null && !chatFontFamily.isBlank()) ui.put("chatFontFamily", chatFontFamily);
      if (chatFontSize > 0) ui.put("chatFontSize", chatFontSize);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist UI config to '{}'", file, e);
    }
  }


  /** Persist whether inline image embeds are enabled (stored under ircafe.ui.imageEmbedsEnabled). */
  public synchronized void rememberImageEmbedsEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("imageEmbedsEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist image embed setting to '{}'", file, e);
    }
  }

  /** Persist whether link previews are enabled (stored under ircafe.ui.linkPreviewsEnabled). */
  public synchronized void rememberLinkPreviewsEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("linkPreviewsEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist link preview setting to '{}'", file, e);
    }
  }

  /** Persist whether chat messages should be prefixed with timestamps (stored under ircafe.ui.chatMessageTimestampsEnabled). */
  public synchronized void rememberChatMessageTimestampsEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("chatMessageTimestampsEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat message timestamp setting to '{}'", file, e);
    }
  }



  /**
   * Persist per-nick color overrides (stored under {@code ircafe.ui.nickColorOverrides}).
   *
   */
  public synchronized void rememberNickColorOverrides(Map<String, String> overrides) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      if (overrides == null || overrides.isEmpty()) {
        ui.remove("nickColorOverrides");
      } else {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : overrides.entrySet()) {
          String nick = Objects.toString(e.getKey(), "").trim();
          String color = Objects.toString(e.getValue(), "").trim();
          if (nick.isEmpty() || color.isEmpty()) continue;
          out.put(nick, color);
        }
        ui.put("nickColorOverrides", out);
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist nick color overrides to '{}'", file, e);
    }
  }

  // ---------------- internals ----------------

  private interface ServerUpdater {
    void update(Map<String, Object> serverMap);
  }

  private void updateServer(String serverId, ServerUpdater updater) {
    try {
      if (file.toString().isBlank()) return;
      String sid = Objects.toString(serverId, "").trim();
      if (sid.isEmpty()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> irc = getOrCreateMap(doc, "irc");
      List<Map<String, Object>> servers = readServerList(irc).orElseGet(ArrayList::new);

      Map<String, Object> found = null;
      for (Map<String, Object> s : servers) {
        if (sid.equalsIgnoreCase(Objects.toString(s.get("id"), "").trim())) {
          found = s;
          break;
        }
      }

      // IMPORTANT: Do not auto-create missing servers here.
      // If a user removed a server at runtime, we must not "resurrect" it
      // just because some runtime state (e.g. /join) tries to persist.
      if (found == null) {
        return;
      }

      updater.update(found);

      irc.put("servers", servers);
      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist runtime config to '{}'", file, e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadFile() throws IOException {
    try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      Object o = yaml.load(r);
      if (o instanceof Map<?, ?> m) {
        return (Map<String, Object>) m;
      }
      return new LinkedHashMap<>();
    }
  }

  private void writeFile(Map<String, Object> doc) throws IOException {
    Path parent = file.getParent();
    if (parent != null && !Files.exists(parent)) {
      Files.createDirectories(parent);
    }
    try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      yaml.dump(doc, w);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> getOrCreateMap(Map<String, Object> parent, String key) {
    Object o = parent.get(key);
    if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
    Map<String, Object> created = new LinkedHashMap<>();
    parent.put(key, created);
    return created;
  }

  @SuppressWarnings("unchecked")
  private static Optional<List<Map<String, Object>>> readServerList(Map<String, Object> irc) {
    Object o = irc.get("servers");
    if (o instanceof List<?> list) {
      // We expect a list of maps.
      return Optional.of((List<Map<String, Object>>) o);
    }
    return Optional.empty();
  }

  private static Map<String, Object> toServerMap(IrcProperties.Server s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", s.id());
    m.put("host", s.host());
    m.put("port", s.port());
    m.put("tls", s.tls());
    if (s.serverPassword() != null && !s.serverPassword().isBlank()) {
      m.put("serverPassword", s.serverPassword());
    }
    if (s.nick() != null) m.put("nick", s.nick());
    if (s.login() != null && !s.login().isBlank()) m.put("login", s.login());
    if (s.realName() != null && !s.realName().isBlank()) m.put("realName", s.realName());
    if (s.autoJoin() != null && !s.autoJoin().isEmpty()) m.put("autoJoin", new ArrayList<>(s.autoJoin()));
    if (s.sasl() != null && s.sasl().enabled()) {
      Map<String, Object> sasl = new LinkedHashMap<>();
      sasl.put("enabled", true);
      sasl.put("username", s.sasl().username());
      sasl.put("password", s.sasl().password());
      if (s.sasl().mechanism() != null && !s.sasl().mechanism().isBlank()) {
        sasl.put("mechanism", s.sasl().mechanism());
      }
      m.put("sasl", sasl);
    }
    return m;
  }

  @SuppressWarnings("unchecked")
  private static List<String> getOrCreateStringList(Map<String, Object> m, String key) {
    Object o = m.get(key);
    if (o instanceof List<?> list) {
      // Cast defensively; we only store strings.
      return (List<String>) o;
    }
    List<String> created = new ArrayList<>();
    m.put(key, created);
    return created;
  }
}

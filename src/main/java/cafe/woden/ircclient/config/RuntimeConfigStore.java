package cafe.woden.ircclient.config;

import cafe.woden.ircclient.ui.settings.NotificationRule;
import cafe.woden.ircclient.app.notifications.IrcEventNotificationRule;
import cafe.woden.ircclient.app.interceptors.InterceptorDefinition;
import cafe.woden.ircclient.app.interceptors.InterceptorRule;
import cafe.woden.ircclient.app.interceptors.InterceptorRuleMode;
import cafe.woden.ircclient.app.commands.UserCommandAlias;
import cafe.woden.ircclient.ui.filter.FilterRule;
import cafe.woden.ircclient.ui.filter.TagSpec;
import cafe.woden.ircclient.ui.filter.FilterScopeOverride;
import cafe.woden.ircclient.ui.filter.RegexSpec;
import cafe.woden.ircclient.irc.soju.PircbotxSojuParsers;

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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@Component
public class RuntimeConfigStore {

  private static final Logger log = LoggerFactory.getLogger(RuntimeConfigStore.class);

  private final Path file;
  private final IrcProperties defaults;
  private final Yaml yaml;
  /** True if the runtime config file existed before this process started creating/initializing it. */
  private final boolean fileExistedOnStartup;

  public RuntimeConfigStore(
      @Value("${ircafe.runtime-config:${user.home}/.config/ircafe/ircafe.yml}") String filePath,
      IrcProperties defaults
  ) {
    this.file = Paths.get(Objects.requireNonNullElse(filePath, "").trim());
    this.defaults = defaults;

    boolean existed = false;
    try {
      existed = !this.file.toString().isBlank() && Files.exists(this.file);
    } catch (Exception ignored) {
      existed = false;
    }
    this.fileExistedOnStartup = existed;

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
   * Returns true if the runtime config file already existed when IRCafe started.
   *
   * <p>This is used for one-time migrations where we want to preserve legacy behavior for existing installs,
   * while using new defaults for first-time installs.
   */
  public boolean runtimeConfigFileExistedOnStartup() {
    return fileExistedOnStartup;
  }

  /**
   * Reads {@code ircafe.ui.tray.closeToTray} only if it is explicitly present in the runtime config file.
   *
   * <p>If the key is absent (or the file doesn't exist), returns {@link Optional#empty()}.
   */
  public synchronized Optional<Boolean> readTrayCloseToTrayIfPresent() {
    try {
      if (file.toString().isBlank()) return Optional.empty();
      if (!Files.exists(file)) return Optional.empty();

      Map<String, Object> doc = loadFile();

      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return Optional.empty();

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return Optional.empty();

      Object trayObj = ui.get("tray");
      if (!(trayObj instanceof Map<?, ?> tray)) return Optional.empty();

      if (!tray.containsKey("closeToTray")) return Optional.empty();

      Object v = tray.get("closeToTray");
      if (v == null) return Optional.empty();

      if (v instanceof Boolean b) return Optional.of(b);
      if (v instanceof String s) {
        String t = s.trim();
        if (t.equalsIgnoreCase("true")) return Optional.of(Boolean.TRUE);
        if (t.equalsIgnoreCase("false")) return Optional.of(Boolean.FALSE);
      }

      return Optional.empty();
    } catch (Exception e) {
      log.warn("[ircafe] Could not read tray.closeToTray from '{}'", file, e);
      return Optional.empty();
    }
  }

  /**
   * Reads {@code ircafe.ui.tray.closeToTrayHintShown} from runtime config.
   *
   * <p>Returns {@code defaultValue} when the key is missing or invalid.
   */
  public synchronized boolean readTrayCloseToTrayHintShown(boolean defaultValue) {
    try {
      if (file.toString().isBlank()) return defaultValue;
      if (!Files.exists(file)) return defaultValue;

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return defaultValue;

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return defaultValue;

      Object trayObj = ui.get("tray");
      if (!(trayObj instanceof Map<?, ?> tray)) return defaultValue;

      if (!tray.containsKey("closeToTrayHintShown")) return defaultValue;
      return asBoolean(tray.get("closeToTrayHintShown")).orElse(defaultValue);
    } catch (Exception e) {
      log.warn("[ircafe] Could not read tray.closeToTrayHintShown from '{}'", file, e);
      return defaultValue;
    }
  }

  /**
   * Reads {@code ircafe.ui.invites.autoJoinOnInvite} from runtime config.
   *
   * <p>Returns {@code defaultValue} when the key is missing or invalid.
   */
  public synchronized boolean readInviteAutoJoinEnabled(boolean defaultValue) {
    try {
      if (file.toString().isBlank()) return defaultValue;
      if (!Files.exists(file)) return defaultValue;

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return defaultValue;

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return defaultValue;

      Object invitesObj = ui.get("invites");
      if (!(invitesObj instanceof Map<?, ?> invites)) return defaultValue;

      if (!invites.containsKey("autoJoinOnInvite")) return defaultValue;
      return asBoolean(invites.get("autoJoinOnInvite")).orElse(defaultValue);
    } catch (Exception e) {
      log.warn("[ircafe] Could not read invites.autoJoinOnInvite from '{}'", file, e);
      return defaultValue;
    }
  }

  public Path runtimeConfigPath() {
    return file;
  }
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

  public synchronized void rememberPrivateMessageTarget(String serverId, String nick) {
    updateServer(serverId, server -> {
      String n = Objects.toString(nick, "").trim();
      if (n.isEmpty()) return;

      List<String> autoJoin = getOrCreateStringList(server, "autoJoin");
      if (AutoJoinEntryCodec.privateMessageNicks(autoJoin).stream().anyMatch(existing -> existing.equalsIgnoreCase(n))) {
        return;
      }
      String encoded = AutoJoinEntryCodec.encodePrivateMessageNick(n);
      if (!encoded.isEmpty()) {
        autoJoin.add(encoded);
      }
    });
  }

  public synchronized void forgetPrivateMessageTarget(String serverId, String nick) {
    updateServer(serverId, server -> {
      String n = Objects.toString(nick, "").trim();
      if (n.isEmpty()) return;

      Object o = server.get("autoJoin");
      if (!(o instanceof List<?> list)) return;
      @SuppressWarnings("unchecked")
      List<String> autoJoin = (List<String>) list;
      autoJoin.removeIf(entry -> {
        String decoded = AutoJoinEntryCodec.decodePrivateMessageNick(entry);
        return !decoded.isEmpty() && decoded.equalsIgnoreCase(n);
      });
    });
  }

  public synchronized List<String> readPrivateMessageTargets(String serverId) {
    try {
      if (file.toString().isBlank()) return List.of();
      String sid = Objects.toString(serverId, "").trim();
      if (sid.isEmpty()) return List.of();

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> irc = getOrCreateMap(doc, "irc");
      List<Map<String, Object>> servers = readServerList(irc).orElse(List.of());

      for (Map<String, Object> server : servers) {
        if (server == null) continue;
        if (!sid.equalsIgnoreCase(Objects.toString(server.get("id"), "").trim())) continue;
        Object autoJoinObj = server.get("autoJoin");
        if (!(autoJoinObj instanceof List<?> rawList)) return List.of();
        @SuppressWarnings("unchecked")
        List<String> autoJoin = (List<String>) rawList;
        return List.copyOf(AutoJoinEntryCodec.privateMessageNicks(autoJoin));
      }
    } catch (Exception e) {
      log.warn("[ircafe] Could not read private-message target list from '{}'", file, e);
    }
    return List.of();
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

  public synchronized void rememberAccentColor(String accentColor) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      // Persist "disabled" explicitly as an empty string so app defaults don't re-enable the accent on restart.
      // (UiProperties treats blank as "no override".)
      String c = accentColor != null ? accentColor.trim() : "";
      ui.put("accentColor", c);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist accentColor setting to '{}'", file, e);
    }
  }

  public synchronized void rememberAccentStrength(int strength) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      int s = Math.max(0, Math.min(100, strength));
      ui.put("accentStrength", s);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist accentStrength setting to '{}'", file, e);
    }
  }

  /**
   * Persists the docking/layout widths so the user's side-dock sizing survives restart.
   *
   * <p>Stored under {@code ircafe.ui.layout}.
   */
  public synchronized void rememberDockLayoutWidths(Integer serverDockWidthPx, Integer userDockWidthPx) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> layout = getOrCreateMap(ui, "layout");

      if (serverDockWidthPx != null && serverDockWidthPx > 0) {
        layout.put("serverDockWidthPx", serverDockWidthPx);
      }
      if (userDockWidthPx != null && userDockWidthPx > 0) {
        layout.put("userDockWidthPx", userDockWidthPx);
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist dock layout widths to '{}'", file, e);
    }
  }

  public synchronized void rememberServerDockWidthPx(int serverDockWidthPx) {
    rememberDockLayoutWidths(serverDockWidthPx, null);
  }

  public synchronized void rememberUserDockWidthPx(int userDockWidthPx) {
    rememberDockLayoutWidths(null, userDockWidthPx);
  }

  public synchronized void rememberUiDensity(String density) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      String d = density != null ? density.trim().toLowerCase(java.util.Locale.ROOT) : "";
      if (d.isEmpty()) {
        ui.remove("density");
      } else if (d.equals("auto") || d.equals("compact") || d.equals("cozy") || d.equals("spacious")) {
        ui.put("density", d);
      } else {
        ui.put("density", "auto");
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ui.density setting to '{}'", file, e);
    }
  }

  public synchronized void rememberCornerRadius(int cornerRadius) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      int r = Math.max(0, Math.min(20, cornerRadius));
      ui.put("cornerRadius", r);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ui.cornerRadius setting to '{}'", file, e);
    }
  }

  public synchronized void rememberChatThemePreset(String preset) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      String p = preset != null ? preset.trim() : "";
      if (p.isEmpty()) {
        ui.remove("chatThemePreset");
      } else {
        ui.put("chatThemePreset", p);
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chatThemePreset setting to '{}'", file, e);
    }
  }

  public synchronized void rememberChatTimestampColor(String hex) {
    rememberOptionalUiHex("chatTimestampColor", hex, "chatTimestampColor");
  }

  public synchronized void rememberChatSystemColor(String hex) {
    rememberOptionalUiHex("chatSystemColor", hex, "chatSystemColor");
  }

  public synchronized void rememberChatMentionBgColor(String hex) {
    rememberOptionalUiHex("chatMentionBgColor", hex, "chatMentionBgColor");
  }

  public synchronized void rememberChatMentionStrength(int strength) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      int s = Math.max(0, Math.min(100, strength));
      ui.put("chatMentionStrength", s);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chatMentionStrength setting to '{}'", file, e);
    }
  }

  private synchronized void rememberOptionalUiHex(String key, String hex, String label) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      String c = hex != null ? hex.trim() : "";
      if (c.isEmpty()) {
        ui.remove(key);
      } else {
        ui.put(key, c);
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist {} setting to '{}'", label, file, e);
    }
  }


  public synchronized void rememberAutoConnectOnStart(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("autoConnectOnStart", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist autoConnectOnStart setting to '{}'", file, e);
    }
  }

  public synchronized void rememberInviteAutoJoinEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> invites = getOrCreateMap(ui, "invites");

      invites.put("autoJoinOnInvite", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist invites.autoJoinOnInvite setting to '{}'", file, e);
    }
  }


  public synchronized void rememberTrayEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("enabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.enabled setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayCloseToTray(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("closeToTray", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.closeToTray setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayCloseToTrayHintShown(boolean shown) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("closeToTrayHintShown", shown);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.closeToTrayHintShown setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayMinimizeToTray(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("minimizeToTray", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.minimizeToTray setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayStartMinimized(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("startMinimized", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.startMinimized setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayNotifyHighlights(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("notifyHighlights", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.notifyHighlights setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayNotifyPrivateMessages(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("notifyPrivateMessages", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.notifyPrivateMessages setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayNotifyConnectionState(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("notifyConnectionState", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.notifyConnectionState setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayNotifyOnlyWhenUnfocused(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("notifyOnlyWhenUnfocused", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.notifyOnlyWhenUnfocused setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayNotifyOnlyWhenMinimizedOrHidden(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("notifyOnlyWhenMinimizedOrHidden", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.notifyOnlyWhenMinimizedOrHidden setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayNotifySuppressWhenTargetActive(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("notifySuppressWhenTargetActive", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.notifySuppressWhenTargetActive setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayLinuxDbusActionsEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("linuxDbusActionsEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.linuxDbusActionsEnabled setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayNotificationSoundsEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("notificationSoundsEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.notificationSoundsEnabled setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayNotificationSound(String soundId) {
    try {
      if (file.toString().isBlank()) return;

      String v = Objects.toString(soundId, "").trim();
      if (v.isEmpty()) v = "NOTIF_1";

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("notificationSound", v);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.notificationSound setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayNotificationSoundUseCustom(boolean useCustom) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      tray.put("notificationSoundUseCustom", useCustom);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.notificationSoundUseCustom setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTrayNotificationSoundCustomPath(String relativePath) {
    try {
      if (file.toString().isBlank()) return;

      String v = Objects.toString(relativePath, "").trim();

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> tray = getOrCreateMap(ui, "tray");

      if (v.isEmpty()) {
        tray.remove("notificationSoundCustomPath");
      } else {
        tray.put("notificationSoundCustomPath", v);
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist tray.notificationSoundCustomPath setting to '{}'", file, e);
    }
  }

  public synchronized void rememberNotificationRuleCooldownSeconds(int seconds) {
    try {
      if (file.toString().isBlank()) return;

      int v = seconds;
      if (v < 0) v = 15;
      if (v > 3600) v = 3600;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("notificationRuleCooldownSeconds", v);
      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist notificationRuleCooldownSeconds setting to '{}'", file, e);
    }
  }

  public synchronized void rememberNotificationRules(List<NotificationRule> rules) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      List<Map<String, Object>> out = new ArrayList<>();
      if (rules != null) {
        for (NotificationRule r : rules) {
          if (r == null) continue;
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("enabled", r.enabled());
          m.put("label", Objects.toString(r.label(), "").trim());
          m.put("type", r.type() != null ? r.type().name() : "WORD");
          m.put("pattern", Objects.toString(r.pattern(), "").trim());
          m.put("caseSensitive", r.caseSensitive());
          m.put("wholeWord", r.wholeWord());
          String fg = Objects.toString(r.highlightFg(), "").trim();
          if (!fg.isEmpty()) m.put("highlightFg", fg);
          out.add(m);
        }
      }

      ui.put("notificationRules", out);
      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist notificationRules to '{}'", file, e);
    }
  }

  public synchronized List<UserCommandAlias> readUserCommandAliases() {
    try {
      if (file.toString().isBlank()) return List.of();
      if (!Files.exists(file)) return List.of();

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return List.of();

      Object commandsObj = ircafe.get("commands");
      if (!(commandsObj instanceof Map<?, ?> commands)) return List.of();

      Object aliasesObj = commands.get("aliases");
      if (!(aliasesObj instanceof List<?> raw)) return List.of();

      List<UserCommandAlias> out = new ArrayList<>();
      for (Object item : raw) {
        if (!(item instanceof Map<?, ?> m)) continue;

        boolean enabled = asBoolean(m.get("enabled")).orElse(Boolean.TRUE);

        String name = Objects.toString(m.get("name"), "").trim();

        // Accept both "template" and legacy/alternate "expansion" key names.
        String template = Objects.toString(m.get("template"), "");
        if (template.isEmpty()) template = Objects.toString(m.get("expansion"), "");

        out.add(new UserCommandAlias(enabled, name, template));
      }

      return List.copyOf(out);
    } catch (Exception e) {
      log.warn("[ircafe] Could not read user command aliases from '{}'", file, e);
      return List.of();
    }
  }

  public synchronized boolean readUnknownCommandAsRawEnabled(boolean defaultValue) {
    try {
      if (file.toString().isBlank()) return defaultValue;
      if (!Files.exists(file)) return defaultValue;

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return defaultValue;

      Object commandsObj = ircafe.get("commands");
      if (!(commandsObj instanceof Map<?, ?> commands)) return defaultValue;

      if (!commands.containsKey("unknownCommandAsRaw")) return defaultValue;
      return asBoolean(commands.get("unknownCommandAsRaw")).orElse(defaultValue);
    } catch (Exception e) {
      log.warn("[ircafe] Could not read commands.unknownCommandAsRaw from '{}'", file, e);
      return defaultValue;
    }
  }

  public synchronized boolean readAppDiagnosticsAssertjSwingEnabled(boolean defaultValue) {
    return readAppDiagnosticsAssertjSwingBoolean("enabled", defaultValue);
  }

  public synchronized boolean readAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(boolean defaultValue) {
    return readAppDiagnosticsAssertjSwingBoolean("edtFreezeWatchdogEnabled", defaultValue);
  }

  public synchronized int readAppDiagnosticsAssertjSwingFreezeThresholdMs(int defaultValue) {
    try {
      if (file.toString().isBlank()) return clampAssertjFreezeThresholdMs(defaultValue);
      if (!Files.exists(file)) return clampAssertjFreezeThresholdMs(defaultValue);

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return clampAssertjFreezeThresholdMs(defaultValue);

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return clampAssertjFreezeThresholdMs(defaultValue);

      Object appObj = ui.get("appDiagnostics");
      if (!(appObj instanceof Map<?, ?> appDiagnostics)) return clampAssertjFreezeThresholdMs(defaultValue);

      Object assertjObj = appDiagnostics.get("assertjSwing");
      if (!(assertjObj instanceof Map<?, ?> assertjSwing)) return clampAssertjFreezeThresholdMs(defaultValue);

      if (!assertjSwing.containsKey("edtFreezeThresholdMs")) return clampAssertjFreezeThresholdMs(defaultValue);
      return clampAssertjFreezeThresholdMs(asInt(assertjSwing.get("edtFreezeThresholdMs")).orElse(defaultValue));
    } catch (Exception e) {
      log.warn("[ircafe] Could not read ui.appDiagnostics.assertjSwing.edtFreezeThresholdMs from '{}'", file, e);
      return clampAssertjFreezeThresholdMs(defaultValue);
    }
  }

  public synchronized int readAppDiagnosticsAssertjSwingWatchdogPollMs(int defaultValue) {
    try {
      if (file.toString().isBlank()) return clampAssertjWatchdogPollMs(defaultValue);
      if (!Files.exists(file)) return clampAssertjWatchdogPollMs(defaultValue);

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return clampAssertjWatchdogPollMs(defaultValue);

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return clampAssertjWatchdogPollMs(defaultValue);

      Object appObj = ui.get("appDiagnostics");
      if (!(appObj instanceof Map<?, ?> appDiagnostics)) return clampAssertjWatchdogPollMs(defaultValue);

      Object assertjObj = appDiagnostics.get("assertjSwing");
      if (!(assertjObj instanceof Map<?, ?> assertjSwing)) return clampAssertjWatchdogPollMs(defaultValue);

      if (!assertjSwing.containsKey("edtWatchdogPollMs")) return clampAssertjWatchdogPollMs(defaultValue);
      return clampAssertjWatchdogPollMs(asInt(assertjSwing.get("edtWatchdogPollMs")).orElse(defaultValue));
    } catch (Exception e) {
      log.warn("[ircafe] Could not read ui.appDiagnostics.assertjSwing.edtWatchdogPollMs from '{}'", file, e);
      return clampAssertjWatchdogPollMs(defaultValue);
    }
  }

  public synchronized boolean readAppDiagnosticsJhiccupEnabled(boolean defaultValue) {
    try {
      if (file.toString().isBlank()) return defaultValue;
      if (!Files.exists(file)) return defaultValue;

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return defaultValue;

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return defaultValue;

      Object appObj = ui.get("appDiagnostics");
      if (!(appObj instanceof Map<?, ?> appDiagnostics)) return defaultValue;

      Object jhiccupObj = appDiagnostics.get("jhiccup");
      if (!(jhiccupObj instanceof Map<?, ?> jhiccup)) return defaultValue;

      if (!jhiccup.containsKey("enabled")) return defaultValue;
      return asBoolean(jhiccup.get("enabled")).orElse(defaultValue);
    } catch (Exception e) {
      log.warn("[ircafe] Could not read ui.appDiagnostics.jhiccup.enabled from '{}'", file, e);
      return defaultValue;
    }
  }

  public synchronized String readAppDiagnosticsJhiccupJarPath(String defaultValue) {
    try {
      if (file.toString().isBlank()) return Objects.toString(defaultValue, "").trim();
      if (!Files.exists(file)) return Objects.toString(defaultValue, "").trim();

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return Objects.toString(defaultValue, "").trim();

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return Objects.toString(defaultValue, "").trim();

      Object appObj = ui.get("appDiagnostics");
      if (!(appObj instanceof Map<?, ?> appDiagnostics)) return Objects.toString(defaultValue, "").trim();

      Object jhiccupObj = appDiagnostics.get("jhiccup");
      if (!(jhiccupObj instanceof Map<?, ?> jhiccup)) return Objects.toString(defaultValue, "").trim();

      if (!jhiccup.containsKey("jarPath")) return Objects.toString(defaultValue, "").trim();
      String raw = Objects.toString(jhiccup.get("jarPath"), "").trim();
      return raw.isEmpty() ? Objects.toString(defaultValue, "").trim() : raw;
    } catch (Exception e) {
      log.warn("[ircafe] Could not read ui.appDiagnostics.jhiccup.jarPath from '{}'", file, e);
      return Objects.toString(defaultValue, "").trim();
    }
  }

  public synchronized String readAppDiagnosticsJhiccupJavaCommand(String defaultValue) {
    try {
      String fallback = Objects.toString(defaultValue, "").trim();
      if (fallback.isEmpty()) fallback = "java";
      if (file.toString().isBlank()) return fallback;
      if (!Files.exists(file)) return fallback;

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return fallback;

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return fallback;

      Object appObj = ui.get("appDiagnostics");
      if (!(appObj instanceof Map<?, ?> appDiagnostics)) return fallback;

      Object jhiccupObj = appDiagnostics.get("jhiccup");
      if (!(jhiccupObj instanceof Map<?, ?> jhiccup)) return fallback;

      if (!jhiccup.containsKey("javaCommand")) return fallback;
      String raw = Objects.toString(jhiccup.get("javaCommand"), "").trim();
      return raw.isEmpty() ? fallback : raw;
    } catch (Exception e) {
      log.warn("[ircafe] Could not read ui.appDiagnostics.jhiccup.javaCommand from '{}'", file, e);
      String fallback = Objects.toString(defaultValue, "").trim();
      return fallback.isEmpty() ? "java" : fallback;
    }
  }

  public synchronized List<String> readAppDiagnosticsJhiccupArgs(List<String> defaultValue) {
    try {
      if (file.toString().isBlank()) return sanitizeArgs(defaultValue);
      if (!Files.exists(file)) return sanitizeArgs(defaultValue);

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return sanitizeArgs(defaultValue);

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return sanitizeArgs(defaultValue);

      Object appObj = ui.get("appDiagnostics");
      if (!(appObj instanceof Map<?, ?> appDiagnostics)) return sanitizeArgs(defaultValue);

      Object jhiccupObj = appDiagnostics.get("jhiccup");
      if (!(jhiccupObj instanceof Map<?, ?> jhiccup)) return sanitizeArgs(defaultValue);

      if (!jhiccup.containsKey("args")) return sanitizeArgs(defaultValue);
      Object argsObj = jhiccup.get("args");
      if (!(argsObj instanceof List<?> raw)) return sanitizeArgs(defaultValue);

      List<String> out = new ArrayList<>();
      for (Object entry : raw) {
        String a = Objects.toString(entry, "").trim();
        if (!a.isEmpty()) out.add(a);
      }
      return List.copyOf(out);
    } catch (Exception e) {
      log.warn("[ircafe] Could not read ui.appDiagnostics.jhiccup.args from '{}'", file, e);
      return sanitizeArgs(defaultValue);
    }
  }

  private boolean readAppDiagnosticsAssertjSwingBoolean(String key, boolean defaultValue) {
    try {
      if (file.toString().isBlank()) return defaultValue;
      if (!Files.exists(file)) return defaultValue;

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return defaultValue;

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return defaultValue;

      Object appObj = ui.get("appDiagnostics");
      if (!(appObj instanceof Map<?, ?> appDiagnostics)) return defaultValue;

      Object assertjObj = appDiagnostics.get("assertjSwing");
      if (!(assertjObj instanceof Map<?, ?> assertjSwing)) return defaultValue;

      if (!assertjSwing.containsKey(key)) return defaultValue;
      return asBoolean(assertjSwing.get(key)).orElse(defaultValue);
    } catch (Exception e) {
      log.warn("[ircafe] Could not read ui.appDiagnostics.assertjSwing.{} from '{}'", key, file, e);
      return defaultValue;
    }
  }

  private static int clampAssertjFreezeThresholdMs(int value) {
    if (value < 500) return 500;
    if (value > 120_000) return 120_000;
    return value;
  }

  private static int clampAssertjWatchdogPollMs(int value) {
    if (value < 100) return 100;
    if (value > 10_000) return 10_000;
    return value;
  }

  private static List<String> sanitizeArgs(List<String> args) {
    if (args == null || args.isEmpty()) return List.of();
    List<String> out = new ArrayList<>();
    for (String a : args) {
      String t = Objects.toString(a, "").trim();
      if (!t.isEmpty()) out.add(t);
    }
    return List.copyOf(out);
  }

  public synchronized boolean readCtcpAutoRepliesEnabled(boolean defaultValue) {
    return readCtcpAutoReplyValue("enabled", defaultValue);
  }

  public synchronized boolean readCtcpAutoReplyVersionEnabled(boolean defaultValue) {
    return readCtcpAutoReplyValue("version", defaultValue);
  }

  public synchronized boolean readCtcpAutoReplyPingEnabled(boolean defaultValue) {
    return readCtcpAutoReplyValue("ping", defaultValue);
  }

  public synchronized boolean readCtcpAutoReplyTimeEnabled(boolean defaultValue) {
    return readCtcpAutoReplyValue("time", defaultValue);
  }

  private boolean readCtcpAutoReplyValue(String key, boolean defaultValue) {
    try {
      if (file.toString().isBlank()) return defaultValue;
      if (!Files.exists(file)) return defaultValue;

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return defaultValue;

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return defaultValue;

      Object ctcpObj = ui.get("ctcpReplies");
      if (!(ctcpObj instanceof Map<?, ?> ctcpReplies)) return defaultValue;

      if (!ctcpReplies.containsKey(key)) return defaultValue;
      return asBoolean(ctcpReplies.get(key)).orElse(defaultValue);
    } catch (Exception e) {
      log.warn("[ircafe] Could not read ui.ctcpReplies.{} from '{}'", key, file, e);
      return defaultValue;
    }
  }

  public synchronized void rememberUserCommandAliases(List<UserCommandAlias> aliases) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> commands = getOrCreateMap(ircafe, "commands");

      List<Map<String, Object>> out = new ArrayList<>();
      if (aliases != null) {
        for (UserCommandAlias alias : aliases) {
          if (alias == null) continue;
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("enabled", alias.enabled());
          m.put("name", Objects.toString(alias.name(), "").trim());
          m.put("template", Objects.toString(alias.template(), ""));
          out.add(m);
        }
      }

      commands.put("aliases", out);
      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user command aliases to '{}'", file, e);
    }
  }

  public synchronized void rememberUnknownCommandAsRawEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> commands = getOrCreateMap(ircafe, "commands");

      commands.put("unknownCommandAsRaw", enabled);
      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist commands.unknownCommandAsRaw to '{}'", file, e);
    }
  }

  public synchronized void rememberAppDiagnosticsAssertjSwingEnabled(boolean enabled) {
    rememberAppDiagnosticsAssertjSwingBoolean("enabled", enabled, "enabled");
  }

  public synchronized void rememberAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(boolean enabled) {
    rememberAppDiagnosticsAssertjSwingBoolean(
        "edtFreezeWatchdogEnabled", enabled, "edtFreezeWatchdogEnabled");
  }

  public synchronized void rememberAppDiagnosticsAssertjSwingFreezeThresholdMs(int ms) {
    try {
      if (file.toString().isBlank()) return;

      int v = clampAssertjFreezeThresholdMs(ms);

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> appDiagnostics = getOrCreateMap(ui, "appDiagnostics");
      Map<String, Object> assertjSwing = getOrCreateMap(appDiagnostics, "assertjSwing");

      assertjSwing.put("edtFreezeThresholdMs", v);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ui.appDiagnostics.assertjSwing.edtFreezeThresholdMs to '{}'", file, e);
    }
  }

  public synchronized void rememberAppDiagnosticsAssertjSwingWatchdogPollMs(int ms) {
    try {
      if (file.toString().isBlank()) return;

      int v = clampAssertjWatchdogPollMs(ms);

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> appDiagnostics = getOrCreateMap(ui, "appDiagnostics");
      Map<String, Object> assertjSwing = getOrCreateMap(appDiagnostics, "assertjSwing");

      assertjSwing.put("edtWatchdogPollMs", v);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ui.appDiagnostics.assertjSwing.edtWatchdogPollMs to '{}'", file, e);
    }
  }

  public synchronized void rememberAppDiagnosticsJhiccupEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> appDiagnostics = getOrCreateMap(ui, "appDiagnostics");
      Map<String, Object> jhiccup = getOrCreateMap(appDiagnostics, "jhiccup");

      jhiccup.put("enabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ui.appDiagnostics.jhiccup.enabled to '{}'", file, e);
    }
  }

  public synchronized void rememberAppDiagnosticsJhiccupJarPath(String jarPath) {
    try {
      if (file.toString().isBlank()) return;

      String v = Objects.toString(jarPath, "").trim();

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> appDiagnostics = getOrCreateMap(ui, "appDiagnostics");
      Map<String, Object> jhiccup = getOrCreateMap(appDiagnostics, "jhiccup");

      if (v.isEmpty()) {
        jhiccup.remove("jarPath");
      } else {
        jhiccup.put("jarPath", v);
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ui.appDiagnostics.jhiccup.jarPath to '{}'", file, e);
    }
  }

  public synchronized void rememberAppDiagnosticsJhiccupJavaCommand(String javaCommand) {
    try {
      if (file.toString().isBlank()) return;

      String v = Objects.toString(javaCommand, "").trim();

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> appDiagnostics = getOrCreateMap(ui, "appDiagnostics");
      Map<String, Object> jhiccup = getOrCreateMap(appDiagnostics, "jhiccup");

      if (v.isEmpty()) {
        jhiccup.remove("javaCommand");
      } else {
        jhiccup.put("javaCommand", v);
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ui.appDiagnostics.jhiccup.javaCommand to '{}'", file, e);
    }
  }

  public synchronized void rememberAppDiagnosticsJhiccupArgs(List<String> args) {
    try {
      if (file.toString().isBlank()) return;

      List<String> sanitized = sanitizeArgs(args);

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> appDiagnostics = getOrCreateMap(ui, "appDiagnostics");
      Map<String, Object> jhiccup = getOrCreateMap(appDiagnostics, "jhiccup");

      if (sanitized.isEmpty()) {
        jhiccup.remove("args");
      } else {
        jhiccup.put("args", sanitized);
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ui.appDiagnostics.jhiccup.args to '{}'", file, e);
    }
  }

  private void rememberAppDiagnosticsAssertjSwingBoolean(String key, boolean value, String label) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> appDiagnostics = getOrCreateMap(ui, "appDiagnostics");
      Map<String, Object> assertjSwing = getOrCreateMap(appDiagnostics, "assertjSwing");

      assertjSwing.put(key, value);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ui.appDiagnostics.assertjSwing.{} to '{}'", label, file, e);
    }
  }

  public synchronized void rememberIrcEventNotificationRules(List<IrcEventNotificationRule> rules) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      List<Map<String, Object>> out = new ArrayList<>();
      if (rules != null) {
        for (IrcEventNotificationRule r : rules) {
          if (r == null) continue;

          Map<String, Object> m = new LinkedHashMap<>();
          m.put("enabled", r.enabled());
          m.put("eventType", r.eventType() != null ? r.eventType().name() : "INVITE_RECEIVED");
          m.put("sourceMode", r.sourceMode() != null ? r.sourceMode().name() : "ANY");
          String sourcePattern = Objects.toString(r.sourcePattern(), "").trim();
          if (!sourcePattern.isEmpty()) m.put("sourcePattern", sourcePattern);

          m.put("channelScope", r.channelScope() != null ? r.channelScope().name() : "ALL");
          String channelPatterns = Objects.toString(r.channelPatterns(), "").trim();
          if (!channelPatterns.isEmpty()) m.put("channelPatterns", channelPatterns);

          m.put("toastEnabled", r.toastEnabled());
          IrcEventNotificationRule.FocusScope focusScope =
              r.focusScope() != null ? r.focusScope() : IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY;
          m.put("focusScope", focusScope.name());
          // Legacy compatibility for older builds that only understand toastWhenFocused.
          m.put("toastWhenFocused", focusScope != IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY);
          m.put("statusBarEnabled", r.statusBarEnabled());
          m.put("notificationsNodeEnabled", r.notificationsNodeEnabled());
          m.put("soundEnabled", r.soundEnabled());
          m.put("soundId", Objects.toString(r.soundId(), "").trim().isEmpty() ? "NOTIF_1" : r.soundId().trim());
          m.put("soundUseCustom", r.soundUseCustom());

          String custom = Objects.toString(r.soundCustomPath(), "").trim();
          if (!custom.isEmpty()) m.put("soundCustomPath", custom);

          m.put("scriptEnabled", r.scriptEnabled());
          String scriptPath = Objects.toString(r.scriptPath(), "").trim();
          if (!scriptPath.isEmpty()) m.put("scriptPath", scriptPath);
          String scriptArgs = Objects.toString(r.scriptArgs(), "").trim();
          if (!scriptArgs.isEmpty()) m.put("scriptArgs", scriptArgs);
          String scriptWorkingDirectory = Objects.toString(r.scriptWorkingDirectory(), "").trim();
          if (!scriptWorkingDirectory.isEmpty()) m.put("scriptWorkingDirectory", scriptWorkingDirectory);

          out.add(m);
        }
      }

      ui.put("ircEventNotificationRules", out);
      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ircEventNotificationRules to '{}'", file, e);
    }
  }

  public synchronized Map<String, List<InterceptorDefinition>> readInterceptorDefinitions() {
    try {
      if (file.toString().isBlank()) return Map.of();
      if (!Files.exists(file)) return Map.of();

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return Map.of();

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return Map.of();

      Object interceptorsObj = ui.get("interceptors");
      if (!(interceptorsObj instanceof Map<?, ?> interceptors)) return Map.of();

      Object serversObj = interceptors.get("servers");
      if (!(serversObj instanceof Map<?, ?> servers)) return Map.of();

      LinkedHashMap<String, List<InterceptorDefinition>> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : servers.entrySet()) {
        String sid = Objects.toString(entry.getKey(), "").trim();
        if (sid.isEmpty()) continue;
        List<InterceptorDefinition> defs = parseInterceptorDefinitionsForServer(entry.getValue(), sid);
        if (!defs.isEmpty()) {
          out.put(sid, defs);
        }
      }

      if (out.isEmpty()) return Map.of();
      return Map.copyOf(out);
    } catch (Exception e) {
      log.warn("[ircafe] Could not read interceptor definitions from '{}'", file, e);
      return Map.of();
    }
  }

  public synchronized void rememberInterceptorDefinitions(Map<String, List<InterceptorDefinition>> defsByServer) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> interceptors = getOrCreateMap(ui, "interceptors");

      LinkedHashMap<String, Object> serversOut = new LinkedHashMap<>();
      if (defsByServer != null) {
        for (Map.Entry<String, List<InterceptorDefinition>> entry : defsByServer.entrySet()) {
          String sid = Objects.toString(entry.getKey(), "").trim();
          if (sid.isEmpty()) continue;

          List<Map<String, Object>> defsOut = new ArrayList<>();
          List<InterceptorDefinition> defs = entry.getValue();
          if (defs != null) {
            for (InterceptorDefinition def : defs) {
              if (def == null) continue;
              String id = Objects.toString(def.id(), "").trim();
              if (id.isEmpty()) continue;

              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", id);
              m.put("name", Objects.toString(def.name(), "").trim());
              m.put("enabled", def.enabled());

              String scopeServerId = Objects.toString(def.scopeServerId(), "").trim();
              // Keep this key even when blank so "any server" survives round-trip.
              m.put("scopeServerId", scopeServerId);

              m.put("channelIncludeMode",
                  def.channelIncludeMode() != null ? def.channelIncludeMode().name() : InterceptorRuleMode.GLOB.name());
              String channelIncludes = Objects.toString(def.channelIncludes(), "").trim();
              if (!channelIncludes.isEmpty()) m.put("channelIncludes", channelIncludes);

              m.put("channelExcludeMode",
                  def.channelExcludeMode() != null ? def.channelExcludeMode().name() : InterceptorRuleMode.GLOB.name());
              String channelExcludes = Objects.toString(def.channelExcludes(), "").trim();
              if (!channelExcludes.isEmpty()) m.put("channelExcludes", channelExcludes);

              m.put("actionSoundEnabled", def.actionSoundEnabled());
              m.put("actionStatusBarEnabled", def.actionStatusBarEnabled());
              m.put("actionToastEnabled", def.actionToastEnabled());
              String soundId = Objects.toString(def.actionSoundId(), "").trim();
              m.put("actionSoundId", soundId.isEmpty() ? "NOTIF_1" : soundId);
              m.put("actionSoundUseCustom", def.actionSoundUseCustom());
              String soundCustom = Objects.toString(def.actionSoundCustomPath(), "").trim();
              if (!soundCustom.isEmpty()) m.put("actionSoundCustomPath", soundCustom);

              m.put("actionScriptEnabled", def.actionScriptEnabled());
              String scriptPath = Objects.toString(def.actionScriptPath(), "").trim();
              if (!scriptPath.isEmpty()) m.put("actionScriptPath", scriptPath);
              String scriptArgs = Objects.toString(def.actionScriptArgs(), "").trim();
              if (!scriptArgs.isEmpty()) m.put("actionScriptArgs", scriptArgs);
              String scriptWorkingDirectory = Objects.toString(def.actionScriptWorkingDirectory(), "").trim();
              if (!scriptWorkingDirectory.isEmpty()) m.put("actionScriptWorkingDirectory", scriptWorkingDirectory);

              List<Map<String, Object>> rulesOut = new ArrayList<>();
              if (def.rules() != null) {
                for (InterceptorRule rule : def.rules()) {
                  if (rule == null) continue;
                  Map<String, Object> rm = new LinkedHashMap<>();
                  rm.put("enabled", rule.enabled());
                  rm.put("label", Objects.toString(rule.label(), "").trim());
                  String eventTypesCsv = Objects.toString(rule.eventTypesCsv(), "").trim();
                  if (!eventTypesCsv.isEmpty()) rm.put("eventTypesCsv", eventTypesCsv);

                  rm.put("messageMode",
                      rule.messageMode() != null ? rule.messageMode().name() : InterceptorRuleMode.LIKE.name());
                  String messagePattern = Objects.toString(rule.messagePattern(), "").trim();
                  if (!messagePattern.isEmpty()) rm.put("messagePattern", messagePattern);

                  rm.put("nickMode",
                      rule.nickMode() != null ? rule.nickMode().name() : InterceptorRuleMode.LIKE.name());
                  String nickPattern = Objects.toString(rule.nickPattern(), "").trim();
                  if (!nickPattern.isEmpty()) rm.put("nickPattern", nickPattern);

                  rm.put("hostmaskMode",
                      rule.hostmaskMode() != null ? rule.hostmaskMode().name() : InterceptorRuleMode.GLOB.name());
                  String hostmaskPattern = Objects.toString(rule.hostmaskPattern(), "").trim();
                  if (!hostmaskPattern.isEmpty()) rm.put("hostmaskPattern", hostmaskPattern);
                  rulesOut.add(rm);
                }
              }
              m.put("rules", rulesOut);
              defsOut.add(m);
            }
          }

          if (!defsOut.isEmpty()) {
            serversOut.put(sid, defsOut);
          }
        }
      }

      if (serversOut.isEmpty()) {
        interceptors.remove("servers");
        if (interceptors.isEmpty()) {
          ui.remove("interceptors");
        }
      } else {
        interceptors.put("servers", serversOut);
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist interceptor definitions to '{}'", file, e);
    }
  }

  // --- Chat logging / history persistence (ircafe.logging.*) ---

  public synchronized void rememberChatLoggingEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> logging = getOrCreateMap(ircafe, "logging");

      logging.put("enabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat logging enabled setting to '{}'", file, e);
    }
  }

  public synchronized void rememberChatLoggingLogSoftIgnoredLines(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> logging = getOrCreateMap(ircafe, "logging");

      logging.put("logSoftIgnoredLines", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat logging soft-ignore setting to '{}'", file, e);
    }
  }

  public synchronized void rememberChatLoggingLogPrivateMessages(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> logging = getOrCreateMap(ircafe, "logging");

      logging.put("logPrivateMessages", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat logging PM-history setting to '{}'", file, e);
    }
  }

  public synchronized void rememberChatLoggingSavePrivateMessageList(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> logging = getOrCreateMap(ircafe, "logging");

      logging.put("savePrivateMessageList", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat logging PM-list setting to '{}'", file, e);
    }
  }

  public synchronized void rememberChatLoggingDbFileBaseName(String fileBaseName) {
    try {
      if (file.toString().isBlank()) return;

      String base = Objects.toString(fileBaseName, "").trim();
      if (base.isEmpty()) base = "ircafe-chatlog";

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> logging = getOrCreateMap(ircafe, "logging");
      Map<String, Object> hsqldb = getOrCreateMap(logging, "hsqldb");

      hsqldb.put("fileBaseName", base);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat logging DB file base name to '{}'", file, e);
    }
  }

  public synchronized void rememberChatLoggingDbNextToRuntimeConfig(boolean nextToRuntimeConfig) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> logging = getOrCreateMap(ircafe, "logging");
      Map<String, Object> hsqldb = getOrCreateMap(logging, "hsqldb");

      hsqldb.put("nextToRuntimeConfig", nextToRuntimeConfig);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat logging DB location setting to '{}'", file, e);
    }
  }

  public synchronized void rememberChatLoggingKeepForever(boolean keepForever) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> logging = getOrCreateMap(ircafe, "logging");

      logging.put("keepForever", keepForever);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat logging keepForever setting to '{}'", file, e);
    }
  }

  public synchronized void rememberChatLoggingRetentionDays(int retentionDays) {
    try {
      if (file.toString().isBlank()) return;

      int days = Math.max(0, retentionDays);

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> logging = getOrCreateMap(ircafe, "logging");

      logging.put("retentionDays", days);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat logging retentionDays setting to '{}'", file, e);
    }
  }


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

  

  public synchronized void rememberImageEmbedsCollapsedByDefault(boolean collapsed) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("imageEmbedsCollapsedByDefault", collapsed);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist image embed collapse setting to '{}'", file, e);
    }
  }

  public synchronized void rememberImageEmbedsMaxWidthPx(int maxWidthPx) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("imageEmbedsMaxWidthPx", Math.max(0, maxWidthPx));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist image embed max width setting to '{}'", file, e);
    }
  }

  public synchronized void rememberImageEmbedsMaxHeightPx(int maxHeightPx) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("imageEmbedsMaxHeightPx", Math.max(0, maxHeightPx));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist image embed max height setting to '{}'", file, e);
    }
  }

  public synchronized void rememberImageEmbedsAnimateGifs(boolean animate) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("imageEmbedsAnimateGifs", animate);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist image embed GIF animation setting to '{}'", file, e);
    }
  }

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

  

  public synchronized void rememberLinkPreviewsCollapsedByDefault(boolean collapsed) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("linkPreviewsCollapsedByDefault", collapsed);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist link preview collapse setting to '{}'", file, e);
    }
  }


  public synchronized void rememberPresenceFoldsEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("presenceFoldsEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist presence folds setting to '{}'", file, e);
    }
  }

  public synchronized void rememberCtcpRequestsInActiveTargetEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("ctcpRequestsInActiveTargetEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist CTCP request routing setting to '{}'", file, e);
    }
  }

  public synchronized void rememberCtcpAutoRepliesEnabled(boolean enabled) {
    rememberCtcpAutoReplyValue("enabled", enabled);
  }

  public synchronized void rememberCtcpAutoReplyVersionEnabled(boolean enabled) {
    rememberCtcpAutoReplyValue("version", enabled);
  }

  public synchronized void rememberCtcpAutoReplyPingEnabled(boolean enabled) {
    rememberCtcpAutoReplyValue("ping", enabled);
  }

  public synchronized void rememberCtcpAutoReplyTimeEnabled(boolean enabled) {
    rememberCtcpAutoReplyValue("time", enabled);
  }

  private void rememberCtcpAutoReplyValue(String key, boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> ctcpReplies = getOrCreateMap(ui, "ctcpReplies");

      ctcpReplies.put(key, enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ui.ctcpReplies.{} to '{}'", key, file, e);
    }
  }

  public synchronized void rememberTypingIndicatorsEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("typingIndicatorsEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist typing indicators setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTypingIndicatorsReceiveEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("typingIndicatorsReceiveEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist incoming typing indicators setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTypingTreeIndicatorStyle(String style) {
    try {
      if (file.toString().isBlank()) return;

      String normalized = UiProperties.normalizeTypingTreeIndicatorStyle(style);

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("typingTreeIndicatorStyle", normalized);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist typing tree indicator style to '{}'", file, e);
    }
  }

  /**
   * Reads persisted IRCv3 capability request overrides under {@code ircafe.ui.ircv3Capabilities}.
   *
   * <p>Keys are normalized to lowercase, values are booleans. Missing/invalid entries are ignored.
   */
  public synchronized Map<String, Boolean> readIrcv3Capabilities() {
    try {
      if (file.toString().isBlank()) return Map.of();
      if (!Files.exists(file)) return Map.of();

      Map<String, Object> doc = loadFile();
      Object ircafeObj = doc.get("ircafe");
      if (!(ircafeObj instanceof Map<?, ?> ircafe)) return Map.of();

      Object uiObj = ircafe.get("ui");
      if (!(uiObj instanceof Map<?, ?> ui)) return Map.of();

      Object capsObj = ui.get("ircv3Capabilities");
      if (!(capsObj instanceof Map<?, ?> caps)) return Map.of();

      Map<String, Boolean> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : caps.entrySet()) {
        String key = normalizeCapabilityKey(Objects.toString(e.getKey(), ""));
        if (key == null) continue;
        Optional<Boolean> b = asBoolean(e.getValue());
        b.ifPresent(value -> out.put(key, value));
      }
      return out;
    } catch (Exception e) {
      log.warn("[ircafe] Could not read IRCv3 capability settings from '{}'", file, e);
      return Map.of();
    }
  }

  /**
   * Returns whether a given IRCv3 capability should be requested, falling back to {@code defaultEnabled}
   * when no explicit override is present.
   */
  public synchronized boolean isIrcv3CapabilityEnabled(String capability, boolean defaultEnabled) {
    String key = normalizeCapabilityKey(capability);
    if (key == null) return defaultEnabled;
    Map<String, Boolean> caps = readIrcv3Capabilities();
    return caps.getOrDefault(key, defaultEnabled);
  }

  /**
   * Persists an IRCv3 capability request override under {@code ircafe.ui.ircv3Capabilities}.
   *
   * <p>Default behavior is "enabled", so enabled values are removed to keep YAML concise.
   */
  public synchronized void rememberIrcv3CapabilityEnabled(String capability, boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      String key = normalizeCapabilityKey(capability);
      if (key == null) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> caps = getOrCreateMap(ui, "ircv3Capabilities");

      if (enabled) {
        caps.remove(key);
      } else {
        caps.put(key, false);
      }
      if (caps.isEmpty()) {
        ui.remove("ircv3Capabilities");
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist IRCv3 capability '{}' setting to '{}'", capability, file, e);
    }
  }

  // --- WeeChat-style filters (ircafe.ui.filters.*) ---

  public synchronized void rememberFiltersEnabledByDefault(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> filters = getOrCreateMap(ui, "filters");

      filters.put("enabledByDefault", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist filters enabledByDefault setting to '{}'", file, e);
    }
  }

  public synchronized void rememberFilterPlaceholdersEnabledByDefault(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> filters = getOrCreateMap(ui, "filters");

      filters.put("placeholdersEnabledByDefault", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist filters placeholdersEnabledByDefault setting to '{}'", file, e);
    }
  }

  public synchronized void rememberFilterPlaceholdersCollapsedByDefault(boolean collapsed) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> filters = getOrCreateMap(ui, "filters");

      filters.put("placeholdersCollapsedByDefault", collapsed);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist filters placeholdersCollapsedByDefault setting to '{}'", file, e);
    }
  }

  public synchronized void rememberFilterPlaceholderMaxPreviewLines(int maxLines) {
    try {
      if (file.toString().isBlank()) return;

      int v = maxLines;
      if (v < 0) v = 0;
      if (v > 25) v = 25;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> filters = getOrCreateMap(ui, "filters");

      filters.put("placeholderMaxPreviewLines", v);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist filters placeholderMaxPreviewLines setting to '{}'", file, e);
    }
  }

public synchronized void rememberFilterPlaceholderMaxLinesPerRun(int maxLines) {
  try {
    if (file.toString().isBlank()) return;

    int v = maxLines;
    if (v < 0) v = 0;
    if (v > 50_000) v = 50_000;

    Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
    Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
    Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
    Map<String, Object> filters = getOrCreateMap(ui, "filters");

    filters.put("placeholderMaxLinesPerRun", v);

    writeFile(doc);
  } catch (Exception e) {
    log.warn("[ircafe] Could not persist filters placeholderMaxLinesPerRun setting to '{}'", file, e);
  }
}

public synchronized void rememberFilterPlaceholderTooltipMaxTags(int maxTags) {
  try {
    if (file.toString().isBlank()) return;

    int v = maxTags;
    if (v < 0) v = 0;
    if (v > 500) v = 500;

    Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
    Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
    Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
    Map<String, Object> filters = getOrCreateMap(ui, "filters");

    filters.put("placeholderTooltipMaxTags", v);

    writeFile(doc);
  } catch (Exception e) {
    log.warn("[ircafe] Could not persist filters placeholderTooltipMaxTags setting to '{}'", file, e);
  }
}

public synchronized void rememberFilterHistoryPlaceholderMaxRunsPerBatch(int maxRuns) {
  try {
    if (file.toString().isBlank()) return;

    int v = maxRuns;
    if (v < 0) v = 0;
    if (v > 5_000) v = 5_000;

    Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
    Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
    Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
    Map<String, Object> filters = getOrCreateMap(ui, "filters");

    filters.put("historyPlaceholderMaxRunsPerBatch", v);

    writeFile(doc);
  } catch (Exception e) {
    log.warn("[ircafe] Could not persist filters historyPlaceholderMaxRunsPerBatch setting to '{}'", file, e);
  }
}

public synchronized void rememberFilterHistoryPlaceholdersEnabledByDefault(boolean enabled) {
  try {
    if (file.toString().isBlank()) return;

    Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
    Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
    Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
    Map<String, Object> filters = getOrCreateMap(ui, "filters");

    filters.put("historyPlaceholdersEnabledByDefault", enabled);

    writeFile(doc);
  } catch (Exception e) {
    log.warn("[ircafe] Could not persist filters historyPlaceholdersEnabledByDefault setting to '{}'", file, e);
  }
}


  public synchronized void rememberFilterRules(List<FilterRule> rules) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> filters = getOrCreateMap(ui, "filters");

      List<Map<String, Object>> out = new ArrayList<>();
      if (rules != null) {
        for (FilterRule r : rules) {
          if (r == null) continue;
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("name", Objects.toString(r.name(), "").trim());
          m.put("enabled", r.enabled());
          m.put("scope", Objects.toString(r.scopePattern(), "*").trim());
          m.put("action", r.action() != null ? r.action().name() : "HIDE");
          m.put("dir", r.direction() != null ? r.direction().name() : "ANY");

          if (r.kinds() != null && !r.kinds().isEmpty()) {
            m.put("kinds", r.kinds().stream().filter(Objects::nonNull).map(Enum::name).toList());
          }
          if (r.fromNickGlobs() != null && !r.fromNickGlobs().isEmpty()) {
            m.put("from", r.fromNickGlobs().stream()
                .filter(Objects::nonNull)
                .map(s -> Objects.toString(s, "").trim())
                .filter(s -> !s.isEmpty())
                .toList());
          }

          TagSpec tags = r.tags();
          if (tags != null && !tags.isEmpty()) {
            String expr = Objects.toString(tags.expr(), "").trim();
            if (!expr.isEmpty()) {
              m.put("tags", expr);
            }
          }

          RegexSpec re = r.textRegex();
          if (re != null && !re.isEmpty()) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("pattern", re.pattern());
            if (re.flags() != null && !re.flags().isEmpty()) {
              String flags = re.flags().stream().map(Enum::name).map(String::toLowerCase).sorted().reduce("", (a, b) -> a + b);
              tm.put("flags", flags);
            }
            m.put("text", tm);
          }

          out.add(m);
        }
      }

      filters.put("rules", out);
      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist filter rules to '{}'", file, e);
    }
  }



  public synchronized void rememberFilterOverrides(List<FilterScopeOverride> overrides) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> filters = getOrCreateMap(ui, "filters");

      List<Map<String, Object>> out = new ArrayList<>();
      if (overrides != null) {
        for (FilterScopeOverride o : overrides) {
          if (o == null) continue;
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("scope", Objects.toString(o.scopePattern(), "*").trim());
          if (o.filtersEnabled() != null) m.put("filtersEnabled", o.filtersEnabled());
          if (o.placeholdersEnabled() != null) m.put("placeholdersEnabled", o.placeholdersEnabled());
          if (o.placeholdersCollapsed() != null) m.put("placeholdersCollapsed", o.placeholdersCollapsed());
          out.add(m);
        }
      }

      filters.put("overrides", out);
      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist filter overrides to '{}'", file, e);
    }
  }



  public synchronized void rememberNickColoringEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("nickColoringEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist nick coloring enabled setting to '{}'", file, e);
    }
  }

  public synchronized void rememberNickColorMinContrast(double minContrast) {
    try {
      if (file.toString().isBlank()) return;

      double mc = (minContrast > 0) ? minContrast : 3.0;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("nickColorMinContrast", mc);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist nick color contrast setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTimestampsEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> timestamps = getOrCreateMap(ui, "timestamps");

      timestamps.put("enabled", enabled);
      // Clean up legacy flat key.
      ui.remove("chatMessageTimestampsEnabled");

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist timestamp enable setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTimestampFormat(String format) {
    try {
      if (file.toString().isBlank()) return;

      String fmt = (format == null || format.isBlank()) ? "HH:mm:ss" : format.trim();

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> timestamps = getOrCreateMap(ui, "timestamps");

      timestamps.put("format", fmt);
      // Clean up legacy flat key.
      ui.remove("chatMessageTimestampsEnabled");

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist timestamp format setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTimestampsIncludeChatMessages(boolean includeChatMessages) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> timestamps = getOrCreateMap(ui, "timestamps");

      timestamps.put("includeChatMessages", includeChatMessages);
      // Clean up legacy flat key.
      ui.remove("chatMessageTimestampsEnabled");

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat message timestamp setting to '{}'", file, e);
    }
  }

  public synchronized void rememberTimestampsIncludePresenceMessages(boolean includePresenceMessages) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> timestamps = getOrCreateMap(ui, "timestamps");

      timestamps.put("includePresenceMessages", includePresenceMessages);
      // Clean up legacy flat key.
      ui.remove("chatMessageTimestampsEnabled");

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist presence message timestamp setting to '{}'", file, e);
    }
  }

  @Deprecated
  public synchronized void rememberChatMessageTimestampsEnabled(boolean enabled) {
    // Back-compat alias for older callers.
    rememberTimestampsIncludeChatMessages(enabled);
  }

  public synchronized void rememberChatHistoryInitialLoadLines(int lines) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("chatHistoryInitialLoadLines", Math.max(0, lines));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat history initial load setting to '{}'", file, e);
    }
  }

  public synchronized void rememberChatHistoryPageSize(int pageSize) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("chatHistoryPageSize", Math.max(1, pageSize));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist chat history page size setting to '{}'", file, e);
    }
  }

  public synchronized void rememberCommandHistoryMaxSize(int maxSize) {
    try {
      if (file.toString().isBlank()) return;

      int v = maxSize;
      if (v <= 0) v = 500;
      if (v > 500) v = 500;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("commandHistoryMaxSize", v);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist command history max size setting to '{}'", file, e);
    }
  }

  public synchronized void rememberClientLineColorEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("clientLineColorEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist outgoing message color enabled setting to '{}'", file, e);
    }
  }

  public synchronized void rememberClientLineColor(String hex) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");

      ui.put("clientLineColor", Objects.toString(hex, "").trim());

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist outgoing message color setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserhostDiscoveryEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> hostmaskDiscovery = getOrCreateMap(ui, "hostmaskDiscovery");

      hostmaskDiscovery.put("userhostEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist USERHOST discovery enabled setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserhostMinIntervalSeconds(int seconds) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> hostmaskDiscovery = getOrCreateMap(ui, "hostmaskDiscovery");

      hostmaskDiscovery.put("userhostMinIntervalSeconds", Math.max(1, seconds));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist USERHOST min interval setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserhostMaxCommandsPerMinute(int maxPerMinute) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> hostmaskDiscovery = getOrCreateMap(ui, "hostmaskDiscovery");

      hostmaskDiscovery.put("userhostMaxCommandsPerMinute", Math.max(1, maxPerMinute));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist USERHOST max commands/min setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserhostNickCooldownMinutes(int minutes) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> hostmaskDiscovery = getOrCreateMap(ui, "hostmaskDiscovery");

      hostmaskDiscovery.put("userhostNickCooldownMinutes", Math.max(1, minutes));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist USERHOST nick cooldown setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserhostMaxNicksPerCommand(int maxNicks) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> hostmaskDiscovery = getOrCreateMap(ui, "hostmaskDiscovery");

      int capped = Math.max(1, Math.min(5, maxNicks));
      hostmaskDiscovery.put("userhostMaxNicksPerCommand", capped);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist USERHOST max nicks/command setting to '{}'", file, e);
    }
  }


  // --- User info enrichment fallback (ircafe.ui.userInfoEnrichment.*) ---

  public synchronized void rememberUserInfoEnrichmentEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> enrich = getOrCreateMap(ui, "userInfoEnrichment");

      enrich.put("enabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user info enrichment enabled setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserInfoEnrichmentWhoisFallbackEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> enrich = getOrCreateMap(ui, "userInfoEnrichment");

      enrich.put("whoisFallbackEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user info enrichment WHOIS fallback enabled setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserInfoEnrichmentUserhostMinIntervalSeconds(int seconds) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> enrich = getOrCreateMap(ui, "userInfoEnrichment");

      enrich.put("userhostMinIntervalSeconds", Math.max(1, seconds));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user info enrichment USERHOST min interval setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserInfoEnrichmentUserhostMaxCommandsPerMinute(int maxPerMinute) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> enrich = getOrCreateMap(ui, "userInfoEnrichment");

      enrich.put("userhostMaxCommandsPerMinute", Math.max(1, maxPerMinute));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user info enrichment USERHOST max commands/min setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserInfoEnrichmentUserhostNickCooldownMinutes(int minutes) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> enrich = getOrCreateMap(ui, "userInfoEnrichment");

      enrich.put("userhostNickCooldownMinutes", Math.max(1, minutes));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user info enrichment USERHOST nick cooldown setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserInfoEnrichmentUserhostMaxNicksPerCommand(int maxNicks) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> enrich = getOrCreateMap(ui, "userInfoEnrichment");

      int capped = Math.max(1, Math.min(5, maxNicks));
      enrich.put("userhostMaxNicksPerCommand", capped);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user info enrichment USERHOST max nicks/command setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserInfoEnrichmentWhoisMinIntervalSeconds(int seconds) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> enrich = getOrCreateMap(ui, "userInfoEnrichment");

      enrich.put("whoisMinIntervalSeconds", Math.max(1, seconds));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user info enrichment WHOIS min interval setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserInfoEnrichmentWhoisNickCooldownMinutes(int minutes) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> enrich = getOrCreateMap(ui, "userInfoEnrichment");

      enrich.put("whoisNickCooldownMinutes", Math.max(1, minutes));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user info enrichment WHOIS nick cooldown setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserInfoEnrichmentPeriodicRefreshEnabled(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> enrich = getOrCreateMap(ui, "userInfoEnrichment");

      enrich.put("periodicRefreshEnabled", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user info enrichment periodic refresh enabled setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserInfoEnrichmentPeriodicRefreshIntervalSeconds(int seconds) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> enrich = getOrCreateMap(ui, "userInfoEnrichment");

      enrich.put("periodicRefreshIntervalSeconds", Math.max(5, seconds));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user info enrichment periodic refresh interval setting to '{}'", file, e);
    }
  }

  public synchronized void rememberUserInfoEnrichmentPeriodicRefreshNicksPerTick(int nicksPerTick) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ui = getOrCreateMap(ircafe, "ui");
      Map<String, Object> enrich = getOrCreateMap(ui, "userInfoEnrichment");

      int capped = Math.max(1, Math.min(20, nicksPerTick));
      enrich.put("periodicRefreshNicksPerTick", capped);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist user info enrichment periodic refresh nicks/tick setting to '{}'", file, e);
    }
  }


  public synchronized void rememberClientTlsTrustAllCertificates(boolean trustAllCertificates) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> irc = getOrCreateMap(doc, "irc");
      Map<String, Object> client = getOrCreateMap(irc, "client");
      Map<String, Object> tls = getOrCreateMap(client, "tls");

      tls.put("trustAllCertificates", trustAllCertificates);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist TLS trust-all setting to '{}'", file, e);
    }
  }

  public synchronized void rememberClientHeartbeat(IrcProperties.Heartbeat heartbeat) {
    try {
      if (file.toString().isBlank()) return;

      IrcProperties.Heartbeat hb = (heartbeat != null)
          ? heartbeat
          : new IrcProperties.Heartbeat(true, 15_000, 360_000);

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> irc = getOrCreateMap(doc, "irc");
      Map<String, Object> client = getOrCreateMap(irc, "client");
      Map<String, Object> hbMap = getOrCreateMap(client, "heartbeat");

      hbMap.put("enabled", hb.enabled());
      hbMap.put("checkPeriodMs", Math.max(1_000L, hb.checkPeriodMs()));
      hbMap.put("timeoutMs", Math.max(1_000L, hb.timeoutMs()));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist heartbeat settings to '{}'", file, e);
    }
  }

  public synchronized void rememberClientProxy(IrcProperties.Proxy proxy) {
    try {
      if (file.toString().isBlank()) return;

      IrcProperties.Proxy p = (proxy != null) ? proxy : new IrcProperties.Proxy(false, "", 0, "", "", true, 20_000, 30_000);

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> irc = getOrCreateMap(doc, "irc");
      Map<String, Object> client = getOrCreateMap(irc, "client");
      Map<String, Object> proxyMap = getOrCreateMap(client, "proxy");

      proxyMap.put("enabled", p.enabled());
      proxyMap.put("host", Objects.toString(p.host(), "").trim());
      proxyMap.put("port", Math.max(0, p.port()));
      proxyMap.put("username", Objects.toString(p.username(), "").trim());
      proxyMap.put("password", Objects.toString(p.password(), ""));
      proxyMap.put("remoteDns", p.remoteDns());
      proxyMap.put("connectTimeoutMs", Math.max(0L, p.connectTimeoutMs()));
      proxyMap.put("readTimeoutMs", Math.max(0L, p.readTimeoutMs()));

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist SOCKS proxy settings to '{}'", file, e);
    }
  }

  public synchronized void rememberIgnoreMask(String serverId, String mask) {
    try {
      if (file.toString().isBlank()) return;

      String sid = Objects.toString(serverId, "").trim();
      String m = Objects.toString(mask, "").trim();
      if (sid.isEmpty() || m.isEmpty()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ignore = getOrCreateMap(ircafe, "ignore");
      Map<String, Object> servers = getOrCreateMap(ignore, "servers");

      @SuppressWarnings("unchecked")
      Map<String, Object> server = (servers.get(sid) instanceof Map<?, ?> mm) ? (Map<String, Object>) mm : new LinkedHashMap<>();
      servers.put(sid, server);

      List<String> masks = getOrCreateStringList(server, "masks");
      if (masks.stream().noneMatch(x -> x != null && x.equalsIgnoreCase(m))) {
        masks.add(m);
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist ignore mask to '{}'", file, e);
    }
  }

  public synchronized void forgetIgnoreMask(String serverId, String mask) {
    try {
      if (file.toString().isBlank()) return;

      String sid = Objects.toString(serverId, "").trim();
      String m = Objects.toString(mask, "").trim();
      if (sid.isEmpty() || m.isEmpty()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ignore = getOrCreateMap(ircafe, "ignore");
      Map<String, Object> servers = getOrCreateMap(ignore, "servers");

      Object so = servers.get(sid);
      if (!(so instanceof Map<?, ?>)) return;
      @SuppressWarnings("unchecked")
      Map<String, Object> server = (Map<String, Object>) so;

      Object o = server.get("masks");
      if (!(o instanceof List<?> list)) return;
      @SuppressWarnings("unchecked")
      List<String> masks = (List<String>) list;

      masks.removeIf(x -> x != null && x.equalsIgnoreCase(m));

      // Clean up empty structures to keep the YAML tidy.
      if (masks.isEmpty()) {
        server.remove("masks");
      }
      if (server.isEmpty()) {
        servers.remove(sid);
      }
      if (servers.isEmpty()) {
        ignore.remove("servers");
      }
      if (ignore.isEmpty()) {
        ircafe.remove("ignore");
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not remove ignore mask from '{}'", file, e);
    }
  }
  public synchronized void rememberSoftIgnoreMask(String serverId, String mask) {
    try {
      if (file.toString().isBlank()) return;

      String sid = Objects.toString(serverId, "").trim();
      String m = Objects.toString(mask, "").trim();
      if (sid.isEmpty() || m.isEmpty()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ignore = getOrCreateMap(ircafe, "ignore");
      Map<String, Object> servers = getOrCreateMap(ignore, "servers");

      @SuppressWarnings("unchecked")
      Map<String, Object> server = (servers.get(sid) instanceof Map<?, ?> mm) ? (Map<String, Object>) mm : new LinkedHashMap<>();
      servers.put(sid, server);

      List<String> masks = getOrCreateStringList(server, "softMasks");
      if (masks.stream().noneMatch(x -> x != null && x.equalsIgnoreCase(m))) {
        masks.add(m);
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist soft-ignore mask to '{}'", file, e);
    }
  }

  public synchronized void forgetSoftIgnoreMask(String serverId, String mask) {
    try {
      if (file.toString().isBlank()) return;

      String sid = Objects.toString(serverId, "").trim();
      String m = Objects.toString(mask, "").trim();
      if (sid.isEmpty() || m.isEmpty()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ignore = getOrCreateMap(ircafe, "ignore");
      Map<String, Object> servers = getOrCreateMap(ignore, "servers");

      Object so = servers.get(sid);
      if (!(so instanceof Map<?, ?>)) return;
      @SuppressWarnings("unchecked")
      Map<String, Object> server = (Map<String, Object>) so;

      Object o = server.get("softMasks");
      if (!(o instanceof List<?> list)) return;
      @SuppressWarnings("unchecked")
      List<String> masks = (List<String>) list;

      masks.removeIf(x -> x != null && x.equalsIgnoreCase(m));

      // Clean up empty structures to keep the YAML tidy.
      if (masks.isEmpty()) {
        server.remove("softMasks");
      }
      if (server.isEmpty()) {
        servers.remove(sid);
      }
      if (servers.isEmpty()) {
        ignore.remove("servers");
      }
      if (ignore.isEmpty()) {
        ircafe.remove("ignore");
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not remove soft-ignore mask from '{}'", file, e);
    }
  }

  public synchronized void rememberHardIgnoreIncludesCtcp(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ignore = getOrCreateMap(ircafe, "ignore");

      ignore.put("hardIgnoreIncludesCtcp", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist hard-ignore CTCP setting to '{}'", file, e);
    }
  }


  public synchronized void rememberSoftIgnoreIncludesCtcp(boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> ignore = getOrCreateMap(ircafe, "ignore");

      ignore.put("softIgnoreIncludesCtcp", enabled);

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist soft-ignore CTCP setting to '{}'", file, e);
    }
  }
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



  public synchronized void rememberSojuAutoConnectNetwork(String bouncerServerId, String networkName, boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      String sid = Objects.toString(bouncerServerId, "").trim();
      String net = Objects.toString(networkName, "").trim();
      if (sid.isEmpty() || net.isEmpty()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> soju = getOrCreateMap(ircafe, "soju");
      Map<String, Object> autoConnect = getOrCreateMap(soju, "autoConnect");

      @SuppressWarnings("unchecked")
      Map<String, Object> nets = (autoConnect.get(sid) instanceof Map<?, ?> mm) ? (Map<String, Object>) mm : new LinkedHashMap<>();

      if (enabled) {
        nets.put(net, true);
        autoConnect.put(sid, nets);
      } else {
        // Remove case-insensitively so users can toggle based on what the bouncer returns.
        nets.keySet().removeIf(k -> k != null && k.equalsIgnoreCase(net));
        if (nets.isEmpty()) {
          autoConnect.remove(sid);
        } else {
          autoConnect.put(sid, nets);
        }

        // Clean up empty structures to keep the YAML tidy.
        if (autoConnect.isEmpty()) {
          soju.remove("autoConnect");
        }
        if (soju.isEmpty()) {
          ircafe.remove("soju");
        }
        if (ircafe.isEmpty()) {
          doc.remove("ircafe");
        }
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist soju auto-connect setting to '{}'", file, e);
    }
  }



  public synchronized void rememberZncAutoConnectNetwork(String bouncerServerId, String networkName, boolean enabled) {
    try {
      if (file.toString().isBlank()) return;

      String sid = Objects.toString(bouncerServerId, "").trim();
      String net = Objects.toString(networkName, "").trim();
      if (sid.isEmpty() || net.isEmpty()) return;

      Map<String, Object> doc = Files.exists(file) ? loadFile() : new LinkedHashMap<>();
      Map<String, Object> ircafe = getOrCreateMap(doc, "ircafe");
      Map<String, Object> znc = getOrCreateMap(ircafe, "znc");
      Map<String, Object> autoConnect = getOrCreateMap(znc, "autoConnect");

      @SuppressWarnings("unchecked")
      Map<String, Object> nets = (autoConnect.get(sid) instanceof Map<?, ?> mm) ? (Map<String, Object>) mm : new LinkedHashMap<>();

      if (enabled) {
        nets.put(net, true);
        autoConnect.put(sid, nets);
      } else {
        // Remove case-insensitively so users can toggle based on what the bouncer returns.
        nets.keySet().removeIf(k -> k != null && k.equalsIgnoreCase(net));
        if (nets.isEmpty()) {
          autoConnect.remove(sid);
        } else {
          autoConnect.put(sid, nets);
        }

        // Clean up empty structures to keep the YAML tidy.
        if (autoConnect.isEmpty()) {
          znc.remove("autoConnect");
        }
        if (znc.isEmpty()) {
          ircafe.remove("znc");
        }
        if (ircafe.isEmpty()) {
          doc.remove("ircafe");
        }
      }

      writeFile(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist znc auto-connect setting to '{}'", file, e);
    }
  }
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
    if (s.perform() != null && !s.perform().isEmpty()) m.put("perform", new ArrayList<>(s.perform()));
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

  private static List<InterceptorDefinition> parseInterceptorDefinitionsForServer(Object rawList, String ownerServerId) {
    String owner = Objects.toString(ownerServerId, "").trim();
    if (!(rawList instanceof List<?> list) || owner.isEmpty()) return List.of();

    ArrayList<InterceptorDefinition> out = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> m)) continue;

      String id = Objects.toString(m.get("id"), "").trim();
      if (id.isEmpty()) continue;
      String name = Objects.toString(m.get("name"), "").trim();
      boolean enabled = asBoolean(m.get("enabled")).orElse(Boolean.TRUE);

      String scopeServerId = Objects.toString(m.get("scopeServerId"), "").trim();
      if (!m.containsKey("scopeServerId")) {
        // Backward compatibility: old definitions were server-local.
        scopeServerId = owner;
      }

      InterceptorRuleMode channelIncludeMode = asRuleMode(m.get("channelIncludeMode"), InterceptorRuleMode.GLOB);
      String channelIncludes = Objects.toString(m.get("channelIncludes"), "").trim();
      if (channelIncludes.isEmpty()) {
        // Backward compatibility with the old "channelsCsv" shape.
        channelIncludes = Objects.toString(m.get("channelsCsv"), "").trim();
      }

      InterceptorRuleMode channelExcludeMode = asRuleMode(m.get("channelExcludeMode"), InterceptorRuleMode.GLOB);
      String channelExcludes = Objects.toString(m.get("channelExcludes"), "").trim();

      boolean actionSoundEnabled = asBoolean(m.get("actionSoundEnabled")).orElse(Boolean.FALSE);
      boolean actionStatusBarEnabled = asBoolean(m.get("actionStatusBarEnabled")).orElse(Boolean.FALSE);
      boolean actionToastEnabled = asBoolean(m.get("actionToastEnabled")).orElse(Boolean.FALSE);
      String actionSoundId = Objects.toString(m.get("actionSoundId"), "").trim();
      if (actionSoundId.isEmpty()) actionSoundId = "NOTIF_1";
      boolean actionSoundUseCustom = asBoolean(m.get("actionSoundUseCustom")).orElse(Boolean.FALSE);
      String actionSoundCustomPath = Objects.toString(m.get("actionSoundCustomPath"), "").trim();

      boolean actionScriptEnabled = asBoolean(m.get("actionScriptEnabled")).orElse(Boolean.FALSE);
      String actionScriptPath = Objects.toString(m.get("actionScriptPath"), "").trim();
      String actionScriptArgs = Objects.toString(m.get("actionScriptArgs"), "").trim();
      String actionScriptWorkingDirectory = Objects.toString(m.get("actionScriptWorkingDirectory"), "").trim();

      List<InterceptorRule> rules = parseInterceptorRules(m.get("rules"));
      if (rules.isEmpty()) {
        // Backward compatibility with the old single-dimension rule shape.
        rules = List.of(new InterceptorRule(
            true,
            "Rule 1",
            "message,action",
            asRuleMode(m.get("mode"), InterceptorRuleMode.LIKE),
            Objects.toString(m.get("pattern"), "").trim(),
            InterceptorRuleMode.LIKE,
            "",
            InterceptorRuleMode.GLOB,
            ""));
      }

      out.add(new InterceptorDefinition(
          id,
          name,
          enabled,
          scopeServerId,
          channelIncludeMode,
          channelIncludes,
          channelExcludeMode,
          channelExcludes,
          actionSoundEnabled,
          actionStatusBarEnabled,
          actionToastEnabled,
          actionSoundId,
          actionSoundUseCustom,
          actionSoundCustomPath,
          actionScriptEnabled,
          actionScriptPath,
          actionScriptArgs,
          actionScriptWorkingDirectory,
          rules));
    }
    return List.copyOf(out);
  }

  private static List<InterceptorRule> parseInterceptorRules(Object rawRules) {
    if (!(rawRules instanceof List<?> list)) return List.of();

    ArrayList<InterceptorRule> out = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> m)) continue;

      boolean enabled = asBoolean(m.get("enabled")).orElse(Boolean.TRUE);
      String label = Objects.toString(m.get("label"), "").trim();
      String eventTypesCsv = Objects.toString(m.get("eventTypesCsv"), "").trim();
      if (eventTypesCsv.isEmpty()) {
        // Backward compatibility with key variants.
        eventTypesCsv = Objects.toString(m.get("eventTypes"), "").trim();
      }

      InterceptorRuleMode messageMode = asRuleMode(
          m.containsKey("messageMode") ? m.get("messageMode") : m.get("mode"),
          InterceptorRuleMode.LIKE);
      String messagePattern = Objects.toString(
          m.containsKey("messagePattern") ? m.get("messagePattern") : m.get("pattern"),
          "").trim();

      InterceptorRuleMode nickMode = asRuleMode(m.get("nickMode"), InterceptorRuleMode.LIKE);
      String nickPattern = Objects.toString(m.get("nickPattern"), "").trim();

      InterceptorRuleMode hostmaskMode = asRuleMode(m.get("hostmaskMode"), InterceptorRuleMode.GLOB);
      String hostmaskPattern = Objects.toString(m.get("hostmaskPattern"), "").trim();

      out.add(new InterceptorRule(
          enabled,
          label,
          eventTypesCsv,
          messageMode,
          messagePattern,
          nickMode,
          nickPattern,
          hostmaskMode,
          hostmaskPattern));
    }
    return List.copyOf(out);
  }

  private static InterceptorRuleMode asRuleMode(Object value, InterceptorRuleMode fallback) {
    if (value instanceof InterceptorRuleMode mode) return mode;
    String raw = Objects.toString(value, "").trim();
    if (raw.isEmpty()) return fallback;
    try {
      return InterceptorRuleMode.valueOf(raw.toUpperCase(Locale.ROOT));
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static String normalizeCapabilityKey(String capability) {
    String c = Objects.toString(capability, "").trim().toLowerCase(Locale.ROOT);
    return c.isEmpty() ? null : c;
  }

  private static Optional<Integer> asInt(Object value) {
    if (value instanceof Number n) return Optional.of(n.intValue());
    if (value instanceof String s) {
      String t = s.trim();
      if (t.isEmpty()) return Optional.empty();
      try {
        return Optional.of(Integer.parseInt(t));
      } catch (Exception ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private static Optional<Boolean> asBoolean(Object value) {
    if (value instanceof Boolean b) return Optional.of(b);
    if (value instanceof String s) {
      String t = s.trim();
      if (t.equalsIgnoreCase("true")) return Optional.of(Boolean.TRUE);
      if (t.equalsIgnoreCase("false")) return Optional.of(Boolean.FALSE);
    }
    if (value instanceof Number n) {
      int i = n.intValue();
      if (i == 0) return Optional.of(Boolean.FALSE);
      if (i == 1) return Optional.of(Boolean.TRUE);
    }
    return Optional.empty();
  }
}

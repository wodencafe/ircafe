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
   * Returns the resolved runtime config YAML path.
   *
   * <p>Used by optional subsystems (e.g. chat logging) that want to store
   * their own files next to the runtime config.
   */
  public Path runtimeConfigPath() {
    return file;
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

  /** Persist whether IRCafe should auto-connect on startup (stored under ircafe.ui.autoConnectOnStart). */
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


  // --- Chat logging / history persistence (ircafe.logging.*) ---

  /** Persist the master chat logging toggle (stored under ircafe.logging.enabled). */
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

  /** Persist whether soft-ignored (spoiler) lines should be logged (stored under ircafe.logging.logSoftIgnoredLines). */
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

  /** Persist the HSQLDB file base name (stored under ircafe.logging.hsqldb.fileBaseName). */
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

  /** Persist whether the HSQLDB files should live next to the runtime YAML (stored under ircafe.logging.hsqldb.nextToRuntimeConfig). */
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

  

  /** Persist whether inline image embeds should start collapsed (stored under ircafe.ui.imageEmbedsCollapsedByDefault). */
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


  /** Persist max width cap for inline image embeds (stored under ircafe.ui.imageEmbedsMaxWidthPx). */
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


  /** Persist max height cap for inline image embeds (stored under ircafe.ui.imageEmbedsMaxHeightPx). */
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

  /** Persist whether animated GIFs should play for inline image embeds (stored under ircafe.ui.imageEmbedsAnimateGifs). */
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

  

  /** Persist whether link previews should start collapsed (stored under ircafe.ui.linkPreviewsCollapsedByDefault). */
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
   * Persist how many history lines should be prefetched into a transcript when selecting a target
   * (stored under ircafe.ui.chatHistoryInitialLoadLines).
   */
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

  /**
   * Persist the paging size for "Load older messages…" inside the transcript
   * (stored under ircafe.ui.chatHistoryPageSize).
   */
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



  /** Persist whether outgoing messages should use a custom color (stored under ircafe.ui.clientLineColorEnabled). */
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

  /** Persist the outgoing message color (stored under ircafe.ui.clientLineColor). */
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

  // ---------------- hostmask discovery ----------------

  /** Persist whether USERHOST-based hostmask discovery is enabled (stored under {@code ircafe.ui.hostmaskDiscovery.userhostEnabled}). */
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

  /** Persist the minimum interval (seconds) between USERHOST commands (stored under {@code ircafe.ui.hostmaskDiscovery.userhostMinIntervalSeconds}). */
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

  /** Persist the max USERHOST commands per minute (stored under {@code ircafe.ui.hostmaskDiscovery.userhostMaxCommandsPerMinute}). */
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

  /** Persist the per-nick cooldown (minutes) before re-querying USERHOST (stored under {@code ircafe.ui.hostmaskDiscovery.userhostNickCooldownMinutes}). */
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

  /** Persist the max nicks to include per USERHOST command (stored under {@code ircafe.ui.hostmaskDiscovery.userhostMaxNicksPerCommand}). */
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

  // ---------------- ignore masks ----------------

  /** Persist an ignore mask for a server (stored under {@code ircafe.ignore.servers.<id>.masks}). */
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

  /** Remove an ignore mask for a server (stored under {@code ircafe.ignore.servers.<id>.masks}). */
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

  // ---------------- soft ignore masks ----------------

  /**
   * Persist a soft-ignore mask for a server (stored under {@code ircafe.ignore.servers.<id>.softMasks}).
   *
   * <p>Soft ignores are reserved for a future feature; they are tracked/persisted but not yet applied.
   */
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

  /** Remove a soft-ignore mask for a server (stored under {@code ircafe.ignore.servers.<id>.softMasks}). */
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


  /** Persist whether hard ignores should also apply to CTCP (stored under ircafe.ignore.hardIgnoreIncludesCtcp). */
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


  /**
   * Persist per-nick color overrides (stored under {@code ircafe.ui.nickColorOverrides}).
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

package cafe.woden.ircclient.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ignore.api.IgnoreAddMaskResult;
import cafe.woden.ircclient.ignore.api.IgnoreTextPatternMode;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class IgnoreListServiceFlowTest {

  @TempDir Path tempDir;

  @Test
  void hardAndSoftIgnoreFlowPreservesMetadataAndPrunesExpiredRules() {
    RuntimeConfigStore runtimeConfig = newRuntimeConfig();
    IgnoreListService service =
        new IgnoreListService(new IgnoreProperties(true, false, Map.of()), runtimeConfig);

    long now = System.currentTimeMillis();
    IgnoreAddMaskResult first =
        service.addMaskWithLevels(
            "libera",
            "BadNick",
            List.of("MSGS", "NOTICES"),
            List.of("#ircafe", "#ops"),
            now + 120_000L,
            "afk*",
            IgnoreTextPatternMode.GLOB,
            true);
    IgnoreAddMaskResult second =
        service.addMaskWithLevels(
            "libera",
            "tempuser",
            List.of("MSGS"),
            List.of("#ircafe"),
            now + 5_000L,
            "",
            IgnoreTextPatternMode.GLOB,
            false);
    boolean softAdded = service.addSoftMask("libera", "quietnick");

    assertEquals(IgnoreAddMaskResult.ADDED, first);
    assertEquals(IgnoreAddMaskResult.ADDED, second);
    assertTrue(softAdded);
    assertEquals(List.of("BadNick!*@*", "tempuser!*@*"), service.listMasks("libera"));
    assertEquals(List.of("MSGS", "NOTICES"), service.levelsForHardMask("libera", "BadNick!*@*"));
    assertEquals(List.of("#ircafe", "#ops"), service.channelsForHardMask("libera", "BadNick!*@*"));
    assertEquals("afk*", service.patternForHardMask("libera", "BadNick!*@*"));
    assertEquals(
        IgnoreTextPatternMode.GLOB, service.patternModeForHardMask("libera", "BadNick!*@*"));
    assertTrue(service.repliesForHardMask("libera", "BadNick!*@*"));
    assertEquals(List.of("quietnick!*@*"), service.listSoftMasks("libera"));

    int pruned = service.pruneExpiredHardMasks("libera", now + 10_000L);

    assertEquals(1, pruned);
    assertEquals(List.of("BadNick!*@*"), service.listMasks("libera"));
    assertEquals(List.of("quietnick!*@*"), service.listSoftMasks("libera"));
  }

  @Test
  void ignoreMetadataRoundTripsThroughRuntimeYamlOnRestart() throws Exception {
    Path configPath = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore runtimeConfig = newRuntimeConfig(configPath);
    IgnoreListService firstBoot =
        new IgnoreListService(new IgnoreProperties(true, false, Map.of()), runtimeConfig);

    long expiresAt = Instant.parse("2026-04-01T12:34:56Z").toEpochMilli();
    firstBoot.setHardIgnoreIncludesCtcp(false);
    firstBoot.setSoftIgnoreIncludesCtcp(true);
    firstBoot.addMaskWithLevels(
        "libera",
        "BadNick",
        List.of("MSGS", "NOTICES"),
        List.of("#ircafe", "#ops"),
        expiresAt,
        "afk|brb",
        IgnoreTextPatternMode.REGEXP,
        true);
    firstBoot.addSoftMask("libera", "quietnick");

    IgnoreProperties rebound = loadIgnoreProperties(configPath);
    IgnoreListService restarted = new IgnoreListService(rebound, newRuntimeConfig(configPath));

    assertFalse(restarted.hardIgnoreIncludesCtcp());
    assertTrue(restarted.softIgnoreIncludesCtcp());
    assertEquals(List.of("BadNick!*@*"), restarted.listMasks("libera"));
    assertEquals(List.of("MSGS", "NOTICES"), restarted.levelsForHardMask("libera", "BadNick!*@*"));
    assertEquals(
        List.of("#ircafe", "#ops"), restarted.channelsForHardMask("libera", "BadNick!*@*"));
    assertEquals(expiresAt, restarted.expiresAtEpochMsForHardMask("libera", "BadNick!*@*"));
    assertEquals("afk|brb", restarted.patternForHardMask("libera", "BadNick!*@*"));
    assertEquals(
        IgnoreTextPatternMode.REGEXP, restarted.patternModeForHardMask("libera", "BadNick!*@*"));
    assertTrue(restarted.repliesForHardMask("libera", "BadNick!*@*"));
    assertEquals(List.of("quietnick!*@*"), restarted.listSoftMasks("libera"));
  }

  private RuntimeConfigStore newRuntimeConfig() {
    return newRuntimeConfig(tempDir.resolve("ircafe.yml"));
  }

  private RuntimeConfigStore newRuntimeConfig(Path configPath) {
    return new RuntimeConfigStore(
        configPath.toString(), new IrcProperties(null, List.of(server("libera"))));
  }

  private static IgnoreProperties loadIgnoreProperties(Path configPath) throws Exception {
    Map<String, Object> doc;
    try (Reader reader = Files.newBufferedReader(configPath)) {
      Object loaded = new Yaml().load(reader);
      doc = asMap(loaded);
    }

    Map<String, Object> ircafe = asMap(doc.get("ircafe"));
    Map<String, Object> ignore = asMap(ircafe.get("ignore"));
    Boolean hardIgnoreIncludesCtcp = asBoolean(ignore.get("hardIgnoreIncludesCtcp")).orElse(true);
    Boolean softIgnoreIncludesCtcp = asBoolean(ignore.get("softIgnoreIncludesCtcp")).orElse(false);

    Map<String, IgnoreProperties.ServerIgnore> servers = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : asMap(ignore.get("servers")).entrySet()) {
      String serverId = Objects.toString(entry.getKey(), "").trim();
      if (serverId.isEmpty()) continue;
      Map<String, Object> server = asMap(entry.getValue());
      servers.put(
          serverId,
          new IgnoreProperties.ServerIgnore(
              asStringList(server.get("masks")),
              asMapOfStringList(server.get("maskLevels")),
              asMapOfStringList(server.get("maskChannels")),
              asMapOfLong(server.get("maskExpiresAt")),
              asMapOfString(server.get("maskPatterns")),
              asMapOfString(server.get("maskPatternModes")),
              asMapOfBoolean(server.get("maskReplies")),
              asStringList(server.get("softMasks"))));
    }
    return new IgnoreProperties(
        hardIgnoreIncludesCtcp, softIgnoreIncludesCtcp, Map.copyOf(servers));
  }

  private static Optional<Boolean> asBoolean(Object raw) {
    if (raw instanceof Boolean b) return Optional.of(b);
    String text = Objects.toString(raw, "").trim();
    if (text.equalsIgnoreCase("true")) return Optional.of(Boolean.TRUE);
    if (text.equalsIgnoreCase("false")) return Optional.of(Boolean.FALSE);
    return Optional.empty();
  }

  private static List<String> asStringList(Object raw) {
    if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();
    ArrayList<String> out = new ArrayList<>();
    for (Object value : list) {
      String text = Objects.toString(value, "").trim();
      if (!text.isEmpty()) out.add(text);
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  private static Map<String, List<String>> asMapOfStringList(Object raw) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : asMap(raw).entrySet()) {
      String key = Objects.toString(entry.getKey(), "").trim();
      List<String> value = asStringList(entry.getValue());
      if (!key.isEmpty() && !value.isEmpty()) {
        out.put(key, value);
      }
    }
    return out.isEmpty() ? Map.of() : Map.copyOf(out);
  }

  private static Map<String, Long> asMapOfLong(Object raw) {
    Map<String, Long> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : asMap(raw).entrySet()) {
      String key = Objects.toString(entry.getKey(), "").trim();
      if (key.isEmpty()) continue;
      Long value = asLong(entry.getValue());
      if (value != null && value > 0L) {
        out.put(key, value);
      }
    }
    return out.isEmpty() ? Map.of() : Map.copyOf(out);
  }

  private static Map<String, String> asMapOfString(Object raw) {
    Map<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : asMap(raw).entrySet()) {
      String key = Objects.toString(entry.getKey(), "").trim();
      String value = Objects.toString(entry.getValue(), "").trim();
      if (!key.isEmpty() && !value.isEmpty()) {
        out.put(key, value);
      }
    }
    return out.isEmpty() ? Map.of() : Map.copyOf(out);
  }

  private static Map<String, Boolean> asMapOfBoolean(Object raw) {
    Map<String, Boolean> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : asMap(raw).entrySet()) {
      String key = Objects.toString(entry.getKey(), "").trim();
      Optional<Boolean> value = asBoolean(entry.getValue());
      if (!key.isEmpty() && value.isPresent()) {
        out.put(key, value.get());
      }
    }
    return out.isEmpty() ? Map.of() : Map.copyOf(out);
  }

  private static Long asLong(Object raw) {
    if (raw instanceof Number number) return number.longValue();
    try {
      return Long.parseLong(Objects.toString(raw, "").trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private static Map<String, Object> asMap(Object raw) {
    if (!(raw instanceof Map<?, ?> input) || input.isEmpty()) return Map.of();
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : input.entrySet()) {
      String key = Objects.toString(entry.getKey(), "").trim();
      if (key.isEmpty()) continue;
      out.put(key, entry.getValue());
    }
    return out.isEmpty() ? Map.of() : out;
  }

  private static IrcProperties.Server server(String id) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        List.of(),
        List.of(),
        null);
  }
}

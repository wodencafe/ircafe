package cafe.woden.ircclient.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ignore.api.IgnoreAddMaskResult;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.IgnoreTextPatternMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IgnoreListPortContractTest {

  @TempDir Path tempDir;

  @Test
  void queryAndCommandPortsExposeConsistentHardMaskLifecycle() {
    IgnoreListService service = newService(tempDir.resolve("ignore-contract.yml"));
    IgnoreListQueryPort queryPort = service;
    IgnoreListCommandPort commandPort = service;

    IgnoreAddMaskResult added =
        commandPort.addMaskWithLevels(
            "libera",
            "BadNick",
            List.of("MSGS", "NOTICES"),
            List.of("#ircafe", "#ops"),
            1_772_368_496_000L,
            "afk|brb",
            IgnoreTextPatternMode.REGEXP,
            true);
    assertEquals(IgnoreAddMaskResult.ADDED, added);
    assertEquals(List.of("BadNick!*@*"), queryPort.listMasks("libera"));
    assertEquals(List.of("MSGS", "NOTICES"), queryPort.levelsForHardMask("libera", "badnick!*@*"));
    assertEquals(
        List.of("#ircafe", "#ops"), queryPort.channelsForHardMask("libera", "BadNick!*@*"));
    assertEquals(
        1_772_368_496_000L, queryPort.expiresAtEpochMsForHardMask("libera", "BadNick!*@*"));
    assertEquals("afk|brb", queryPort.patternForHardMask("libera", "BadNick!*@*"));
    assertEquals(
        IgnoreTextPatternMode.REGEXP, queryPort.patternModeForHardMask("libera", "BadNick!*@*"));
    assertTrue(queryPort.repliesForHardMask("libera", "BadNick!*@*"));

    IgnoreAddMaskResult updated =
        commandPort.addMaskWithLevels(
            "libera",
            "badnick",
            List.of("ALL"),
            List.of("#ircafe"),
            null,
            "",
            IgnoreTextPatternMode.GLOB,
            false);
    assertEquals(IgnoreAddMaskResult.UPDATED, updated);
    assertEquals(List.of("ALL"), queryPort.levelsForHardMask("libera", "BadNick!*@*"));
    assertEquals(List.of("#ircafe"), queryPort.channelsForHardMask("libera", "BadNick!*@*"));
    assertEquals(0L, queryPort.expiresAtEpochMsForHardMask("libera", "BadNick!*@*"));
    assertEquals("", queryPort.patternForHardMask("libera", "BadNick!*@*"));
    assertEquals(
        IgnoreTextPatternMode.GLOB, queryPort.patternModeForHardMask("libera", "BadNick!*@*"));
    assertFalse(queryPort.repliesForHardMask("libera", "BadNick!*@*"));

    assertTrue(commandPort.removeMask("libera", "badnick"));
    assertEquals(List.of(), queryPort.listMasks("libera"));
  }

  @Test
  void commandPortPrunesExpiredRulesVisibleViaQueryPort() {
    IgnoreListService service = newService(tempDir.resolve("ignore-prune.yml"));
    IgnoreListQueryPort queryPort = service;
    IgnoreListCommandPort commandPort = service;

    long now = 1_700_000_000_000L;
    commandPort.addMaskWithLevels(
        "libera",
        "expired",
        List.of("MSGS"),
        List.of(),
        now + 500L,
        "",
        IgnoreTextPatternMode.GLOB,
        false);
    commandPort.addMaskWithLevels(
        "libera",
        "active",
        List.of("MSGS"),
        List.of(),
        now + 60_000L,
        "",
        IgnoreTextPatternMode.GLOB,
        false);

    int removed = commandPort.pruneExpiredHardMasks("libera", now + 1_000L);
    assertEquals(1, removed);
    assertEquals(List.of("active!*@*"), queryPort.listMasks("libera"));
  }

  private static IgnoreListService newService(Path configPath) {
    RuntimeConfigStore runtimeConfig =
        new RuntimeConfigStore(
            configPath.toString(),
            new IrcProperties(
                null,
                List.of(
                    new IrcProperties.Server(
                        "libera",
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
                        null))));
    return new IgnoreListService(new IgnoreProperties(true, false, Map.of()), runtimeConfig);
  }
}

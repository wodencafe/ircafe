package cafe.woden.ircclient.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ignore.api.IgnoreAddMaskResult;
import cafe.woden.ircclient.ignore.api.IgnoreTextPatternMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

  private RuntimeConfigStore newRuntimeConfig() {
    return new RuntimeConfigStore(
        tempDir.resolve("ircafe.yml").toString(),
        new IrcProperties(null, List.of(server("libera"))));
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

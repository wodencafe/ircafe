package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.model.FilterAction;
import cafe.woden.ircclient.model.FilterDirection;
import cafe.woden.ircclient.model.FilterRule;
import cafe.woden.ircclient.model.FilterScopeOverride;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.RegexFlag;
import cafe.woden.ircclient.model.RegexSpec;
import cafe.woden.ircclient.model.TagSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreFilterSettingsTest {

  @TempDir Path tempDir;

  @Test
  void clampsPersistedFilterPlaceholderTuning() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberFilterPlaceholderMaxPreviewLines(99);
    store.rememberFilterPlaceholderMaxLinesPerRun(100_000);
    store.rememberFilterPlaceholderTooltipMaxTags(700);
    store.rememberFilterHistoryPlaceholderMaxRunsPerBatch(8_000);

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("placeholderMaxPreviewLines: 25"));
    assertTrue(yaml.contains("placeholderMaxLinesPerRun: 50000"));
    assertTrue(yaml.contains("placeholderTooltipMaxTags: 500"));
    assertTrue(yaml.contains("historyPlaceholderMaxRunsPerBatch: 5000"));
  }

  @Test
  void persistsFilterRulesAndOverridesUnderFiltersPath() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberFilterRules(
        List.of(
            new FilterRule(
                null,
                "noise",
                false,
                "libera/#chan",
                FilterAction.DIM,
                FilterDirection.IN,
                EnumSet.of(LogKind.CHAT),
                List.of("spammer"),
                new RegexSpec("buy now", EnumSet.of(RegexFlag.I)),
                TagSpec.parse("irc_privmsg"))));
    store.rememberFilterOverrides(
        List.of(new FilterScopeOverride("libera/#chan", false, true, null)));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("filters:"));
    assertTrue(yaml.contains("rules:"));
    assertTrue(yaml.contains("name: noise"));
    assertTrue(yaml.contains("action: DIM"));
    assertTrue(yaml.contains("overrides:"));
    assertTrue(yaml.contains("filtersEnabled: false"));
    assertTrue(yaml.contains("placeholdersEnabled: true"));
  }
}

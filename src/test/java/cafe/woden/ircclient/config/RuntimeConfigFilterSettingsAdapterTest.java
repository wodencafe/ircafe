package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.config.api.FilterSettingsConfigPort;
import cafe.woden.ircclient.config.properties.UiProperties;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.model.FilterAction;
import cafe.woden.ircclient.model.FilterDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.RegexFlag;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RuntimeConfigFilterSettingsAdapterTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class, RuntimeConfigFilterSettingsAdapter.class);

  @Test
  void readsFilterSettingsFromBoundUiProperties() {
    runner
        .withPropertyValues(
            "ircafe.ui.filters.enabledByDefault=false",
            "ircafe.ui.filters.placeholdersEnabledByDefault=true",
            "ircafe.ui.filters.placeholdersCollapsedByDefault=false",
            "ircafe.ui.filters.placeholderMaxPreviewLines=7",
            "ircafe.ui.filters.placeholderMaxLinesPerRun=300",
            "ircafe.ui.filters.placeholderTooltipMaxTags=12",
            "ircafe.ui.filters.historyPlaceholderMaxRunsPerBatch=80",
            "ircafe.ui.filters.historyPlaceholdersEnabledByDefault=false",
            "ircafe.ui.filters.rules[0].name=noise",
            "ircafe.ui.filters.rules[0].scope=libera/#java",
            "ircafe.ui.filters.rules[0].enabled=false",
            "ircafe.ui.filters.rules[0].action=DIM",
            "ircafe.ui.filters.rules[0].dir=IN",
            "ircafe.ui.filters.rules[0].kinds[0]=CHAT",
            "ircafe.ui.filters.rules[0].from[0]=spammer",
            "ircafe.ui.filters.rules[0].tags=irc_privmsg",
            "ircafe.ui.filters.rules[0].text.pattern=buy now",
            "ircafe.ui.filters.rules[0].text.flags=im",
            "ircafe.ui.filters.overrides[0].scope=libera/#java",
            "ircafe.ui.filters.overrides[0].filtersEnabled=false",
            "ircafe.ui.filters.overrides[0].placeholdersEnabled=true")
        .run(
            ctx -> {
              FilterSettingsConfigPort.FilterSettingsSnapshot snapshot =
                  ctx.getBean(FilterSettingsConfigPort.class).readFilterSettings();

              assertFalse(snapshot.filtersEnabledByDefault());
              assertTrue(snapshot.placeholdersEnabledByDefault());
              assertFalse(snapshot.placeholdersCollapsedByDefault());
              assertEquals(7, snapshot.placeholderMaxPreviewLines());
              assertEquals(300, snapshot.placeholderMaxLinesPerRun());
              assertEquals(12, snapshot.placeholderTooltipMaxTags());
              assertEquals(80, snapshot.historyPlaceholderMaxRunsPerBatch());
              assertFalse(snapshot.historyPlaceholdersEnabledByDefault());

              assertEquals(1, snapshot.rules().size());
              var rule = snapshot.rules().getFirst();
              assertEquals("noise", rule.name());
              assertFalse(rule.enabled());
              assertEquals("libera/#java", rule.scopePattern());
              assertEquals(FilterAction.DIM, rule.action());
              assertEquals(FilterDirection.IN, rule.direction());
              assertEquals(EnumSet.of(LogKind.CHAT), rule.kinds());
              assertEquals("spammer", rule.fromNickGlobs().getFirst());
              assertEquals("buy now", rule.textRegex().pattern());
              assertEquals(EnumSet.of(RegexFlag.I, RegexFlag.M), rule.textRegex().flags());

              assertEquals(1, snapshot.overrides().size());
              var override = snapshot.overrides().getFirst();
              assertEquals("libera/#java", override.scopePattern());
              assertEquals(Boolean.FALSE, override.filtersEnabled());
              assertEquals(Boolean.TRUE, override.placeholdersEnabled());
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(UiProperties.class)
  static class TestConfig {
    @Bean
    RuntimeConfigStore runtimeConfigStore() {
      return mock(RuntimeConfigStore.class);
    }
  }
}

package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.config.api.NotificationRule;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreNotificationRulesTest {

  @TempDir Path tempDir;

  @Test
  void notificationRulesAndCooldownArePersistedUnderUiSection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberNotificationRuleCooldownSeconds(5000);
    store.rememberNotificationRules(
        List.of(
            new NotificationRule(
                "Important ping",
                NotificationRule.Type.REGEX,
                "ping|alert",
                true,
                true,
                false,
                "#ffaa00")));

    Map<String, Object> ui = RuntimeConfigYamlTestSupport.uiSection(cfg);
    assertEquals(3600, ui.get("notificationRuleCooldownSeconds"));

    List<?> rules = (List<?>) ui.get("notificationRules");
    assertEquals(1, rules.size());

    Map<?, ?> rule = (Map<?, ?>) rules.getFirst();
    assertEquals("Important ping", rule.get("label"));
    assertEquals("REGEX", rule.get("type"));
    assertEquals("ping|alert", rule.get("pattern"));
    assertEquals(Boolean.TRUE, rule.get("caseSensitive"));
    assertEquals(Boolean.FALSE, rule.get("wholeWord"));
    assertEquals("#FFAA00", rule.get("highlightFg"));
  }
}

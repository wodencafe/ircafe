package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.notifications.IrcEventNotificationRule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreIrcEventNotificationRulesTest {

  @TempDir
  Path tempDir;

  @Test
  void eventRulesArePersistedUnderUiSection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = new RuntimeConfigStore(
        cfg.toString(),
        new IrcProperties(null, List.of()));

    store.rememberIrcEventNotificationRules(List.of(
        new IrcEventNotificationRule(
            true,
            IrcEventNotificationRule.EventType.KICKED,
            IrcEventNotificationRule.SourceFilter.OTHERS,
            true,
            true,
            "NOTIF_3",
            false,
            null,
            "#general",
            "#general-offtopic")));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("ircEventNotificationRules"));
    assertTrue(yaml.contains("eventType: KICKED"));
    assertTrue(yaml.contains("sourceFilter: OTHERS"));
    assertTrue(yaml.contains("channelWhitelist: '#general'") || yaml.contains("channelWhitelist: \"#general\"") || yaml.contains("channelWhitelist: #general"));
  }
}

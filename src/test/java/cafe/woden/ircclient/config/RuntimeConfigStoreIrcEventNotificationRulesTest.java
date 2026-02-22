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
            IrcEventNotificationRule.SourceMode.OTHERS,
            null,
            IrcEventNotificationRule.ChannelScope.ONLY,
            "#general",
            true,
            false,
            true,
            true,
            "NOTIF_3",
            false,
            null,
            true,
            "/tmp/ircafe-event-hook.sh",
            "--flag \"value with spaces\"",
            "/tmp")));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("ircEventNotificationRules"));
    assertTrue(yaml.contains("eventType: KICKED"));
    assertTrue(yaml.contains("sourceMode: OTHERS"));
    assertTrue(yaml.contains("channelScope: ONLY"));
    assertTrue(yaml.contains("channelPatterns: '#general'") || yaml.contains("channelPatterns: \"#general\"") || yaml.contains("channelPatterns: #general"));
    assertTrue(yaml.contains("toastWhenFocused: false"));
    assertTrue(yaml.contains("notificationsNodeEnabled: true"));
    assertTrue(yaml.contains("scriptEnabled: true"));
    assertTrue(yaml.contains("scriptPath: /tmp/ircafe-event-hook.sh") || yaml.contains("scriptPath: '/tmp/ircafe-event-hook.sh'") || yaml.contains("scriptPath: \"/tmp/ircafe-event-hook.sh\""));
    assertTrue(yaml.contains("scriptArgs: '--flag \"value with spaces\"'") || yaml.contains("scriptArgs: \"--flag \\\"value with spaces\\\"\"") || yaml.contains("scriptArgs: --flag \"value with spaces\""));
    assertTrue(yaml.contains("scriptWorkingDirectory: /tmp") || yaml.contains("scriptWorkingDirectory: '/tmp'") || yaml.contains("scriptWorkingDirectory: \"/tmp\""));
  }
}

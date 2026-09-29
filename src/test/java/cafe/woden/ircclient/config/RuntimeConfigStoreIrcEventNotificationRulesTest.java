package cafe.woden.ircclient.config;

import static cafe.woden.ircclient.notifications.IrcEventNotificationRuleTestFixtures.rule;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreIrcEventNotificationRulesTest {

  @TempDir Path tempDir;

  @Test
  void eventRulesArePersistedUnderUiSection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberIrcEventNotificationRules(
        List.of(
            rule()
                .enabled(true)
                .eventType(IrcEventNotificationRule.EventType.CTCP_RECEIVED)
                .sourceMode(IrcEventNotificationRule.SourceMode.OTHERS)
                .sourcePattern(null)
                .channelScope(IrcEventNotificationRule.ChannelScope.ONLY)
                .channelPatterns("#general")
                .toastEnabled(true)
                .focusScope(IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY)
                .statusBarEnabled(true)
                .notificationsNodeEnabled(true)
                .soundEnabled(true)
                .soundId("NOTIF_3")
                .soundUseCustom(false)
                .soundCustomPath(null)
                .scriptEnabled(true)
                .scriptPath("/tmp/ircafe-event-hook.sh")
                .scriptArgs("--flag \"value with spaces\"")
                .scriptWorkingDirectory("/tmp")
                .ctcpCommandMode(IrcEventNotificationRule.CtcpMatchMode.LIKE)
                .ctcpCommandPattern("VERSION")
                .ctcpValueMode(IrcEventNotificationRule.CtcpMatchMode.GLOB)
                .ctcpValuePattern("*hexchat*")
                .build()));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("ircEventNotificationRules"));
    assertTrue(yaml.contains("eventType: CTCP_RECEIVED"));
    assertTrue(yaml.contains("sourceMode: OTHERS"));
    assertTrue(yaml.contains("channelScope: ONLY"));
    assertTrue(
        yaml.contains("channelPatterns: '#general'")
            || yaml.contains("channelPatterns: \"#general\"")
            || yaml.contains("channelPatterns: #general"));
    assertTrue(yaml.contains("focusScope: BACKGROUND_ONLY"));
    assertTrue(yaml.contains("toastWhenFocused: false"));
    assertTrue(yaml.contains("statusBarEnabled: true"));
    assertTrue(yaml.contains("notificationsNodeEnabled: true"));
    assertTrue(yaml.contains("scriptEnabled: true"));
    assertTrue(
        yaml.contains("scriptPath: /tmp/ircafe-event-hook.sh")
            || yaml.contains("scriptPath: '/tmp/ircafe-event-hook.sh'")
            || yaml.contains("scriptPath: \"/tmp/ircafe-event-hook.sh\""));
    assertTrue(
        yaml.contains("scriptArgs: '--flag \"value with spaces\"'")
            || yaml.contains("scriptArgs: \"--flag \\\"value with spaces\\\"\"")
            || yaml.contains("scriptArgs: --flag \"value with spaces\""));
    assertTrue(
        yaml.contains("scriptWorkingDirectory: /tmp")
            || yaml.contains("scriptWorkingDirectory: '/tmp'")
            || yaml.contains("scriptWorkingDirectory: \"/tmp\""));
    assertTrue(yaml.contains("ctcpCommandMode: LIKE"));
    assertTrue(yaml.contains("ctcpCommandPattern: VERSION"));
    assertTrue(yaml.contains("ctcpValueMode: GLOB"));
    assertTrue(
        yaml.contains("ctcpValuePattern: '*hexchat*'")
            || yaml.contains("ctcpValuePattern: \"*hexchat*\"")
            || yaml.contains("ctcpValuePattern: *hexchat*"));
  }
}

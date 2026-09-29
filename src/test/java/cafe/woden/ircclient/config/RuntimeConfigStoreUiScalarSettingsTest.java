package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreUiScalarSettingsTest {

  @TempDir Path tempDir;

  @Test
  void persistsNormalizedUiScalarSettings() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberChatHistoryInitialLoadLines(-1);
    store.rememberChatHistoryPageSize(0);
    store.rememberChatHistoryAutoLoadWheelDebounceMs(50);
    store.rememberChatHistoryLoadOlderChunkSize(999);
    store.rememberChatHistoryLoadOlderChunkDelayMs(-1);
    store.rememberChatHistoryLoadOlderChunkEdtBudgetMs(99);
    store.rememberChatHistoryRemoteRequestTimeoutSeconds(500);
    store.rememberChatHistoryRemoteZncPlaybackTimeoutSeconds(0);
    store.rememberChatHistoryRemoteZncPlaybackWindowMinutes(2_000);
    store.rememberCommandHistoryMaxSize(0);
    store.rememberChatTranscriptMaxLinesPerTarget(500_000);
    store.rememberChatSmoothWheelScrollingEnabled(false);
    store.rememberChatHistoryLockViewportDuringLoadOlder(false);
    store.rememberClientLineColorEnabled(true);
    store.rememberClientLineColor(" #123456 ");
    store.rememberOutgoingDeliveryIndicatorsEnabled(false);
    store.rememberServerTreeNotificationBadgesEnabled(false);

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("chatHistoryInitialLoadLines: 0"));
    assertTrue(yaml.contains("chatHistoryPageSize: 1"));
    assertTrue(yaml.contains("chatHistoryAutoLoadWheelDebounceMs: 100"));
    assertTrue(yaml.contains("chatHistoryLoadOlderChunkSize: 500"));
    assertTrue(yaml.contains("chatHistoryLoadOlderChunkDelayMs: 0"));
    assertTrue(yaml.contains("chatHistoryLoadOlderChunkEdtBudgetMs: 33"));
    assertTrue(yaml.contains("chatHistoryRemoteRequestTimeoutSeconds: 120"));
    assertTrue(yaml.contains("chatHistoryRemoteZncPlaybackTimeoutSeconds: 1"));
    assertTrue(yaml.contains("chatHistoryRemoteZncPlaybackWindowMinutes: 1440"));
    assertTrue(yaml.contains("commandHistoryMaxSize: 500"));
    assertTrue(yaml.contains("chatTranscriptMaxLinesPerTarget: 200000"));
    assertTrue(yaml.contains("chatSmoothWheelScrollingEnabled: false"));
    assertTrue(yaml.contains("chatHistoryLockViewportDuringLoadOlder: false"));
    assertTrue(yaml.contains("clientLineColorEnabled: true"));
    assertTrue(yaml.contains("clientLineColor: '#123456'"));
    assertTrue(yaml.contains("outgoingDeliveryIndicatorsEnabled: false"));
    assertTrue(yaml.contains("serverTreeNotificationBadgesEnabled: false"));
  }
}

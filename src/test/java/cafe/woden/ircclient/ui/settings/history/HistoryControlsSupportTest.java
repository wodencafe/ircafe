package cafe.woden.ircclient.ui.settings.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.RuntimeConfigChatHistoryAdapter;
import cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryControlsSupportTest {

  @Test
  void readSettingsNormalizesHistoryValues() {
    HistoryControls controls =
        controls(-5, 0, 50, true, 700, -10, 40, true, false, 500, 0, 2000, 700, 300_000);

    HistoryControlsSupport.HistorySettings settings = HistoryControlsSupport.readSettings(controls);

    assertEquals(0, settings.initialLoadLines());
    assertEquals(200, settings.pageSize());
    assertEquals(100, settings.autoLoadWheelDebounceMs());
    assertTrue(settings.smoothWheelScrollingEnabled());
    assertEquals(500, settings.loadOlderChunkSize());
    assertEquals(0, settings.loadOlderChunkDelayMs());
    assertEquals(33, settings.loadOlderChunkEdtBudgetMs());
    assertTrue(settings.deferRichTextDuringBatch());
    assertEquals(120, settings.remoteRequestTimeoutSeconds());
    assertEquals(18, settings.remoteZncPlaybackTimeoutSeconds());
    assertEquals(1440, settings.remoteZncPlaybackWindowMinutes());
    assertEquals(500, settings.commandHistoryMaxSize());
    assertEquals(200_000, settings.chatTranscriptMaxLinesPerTarget());
  }

  @Test
  void rememberSettingsPersistsHistoryValues(@TempDir Path tempDir) throws Exception {
    RuntimeConfigStore runtimeConfig =
        RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));
    HistoryControlsSupport.HistorySettings settings =
        new HistoryControlsSupport.HistorySettings(
            120, 240, 750, false, 30, 10, 8, true, false, 9, 24, 180, 250, 10_000);

    HistoryControlsSupport.rememberSettings(
        new RuntimeConfigChatHistoryAdapter(runtimeConfig), settings);

    String yaml = Files.readString(tempDir.resolve("ircafe.yml"));
    assertTrue(yaml.contains("chatHistoryInitialLoadLines: 120"));
    assertTrue(yaml.contains("chatHistoryPageSize: 240"));
    assertTrue(yaml.contains("chatHistoryAutoLoadWheelDebounceMs: 750"));
    assertTrue(yaml.contains("chatSmoothWheelScrollingEnabled: false"));
    assertTrue(yaml.contains("chatHistoryLoadOlderChunkSize: 30"));
    assertTrue(yaml.contains("chatHistoryLoadOlderChunkDelayMs: 10"));
    assertTrue(yaml.contains("chatHistoryLoadOlderChunkEdtBudgetMs: 8"));
    assertTrue(yaml.contains("chatHistoryDeferRichTextDuringBatch: true"));
    assertTrue(yaml.contains("chatHistoryLockViewportDuringLoadOlder: false"));
    assertTrue(yaml.contains("chatHistoryRemoteRequestTimeoutSeconds: 9"));
    assertTrue(yaml.contains("chatHistoryRemoteZncPlaybackTimeoutSeconds: 24"));
    assertTrue(yaml.contains("chatHistoryRemoteZncPlaybackWindowMinutes: 180"));
    assertTrue(yaml.contains("commandHistoryMaxSize: 250"));
    assertTrue(yaml.contains("chatTranscriptMaxLinesPerTarget: 10000"));
  }

  private static HistoryControls controls(
      int initialLoadLines,
      int pageSize,
      int autoLoadWheelDebounceMs,
      boolean smoothWheelScrollingEnabled,
      int loadOlderChunkSize,
      int loadOlderChunkDelayMs,
      int loadOlderChunkEdtBudgetMs,
      boolean deferRichTextDuringBatch,
      boolean lockViewportDuringLoadOlder,
      int remoteRequestTimeoutSeconds,
      int remoteZncPlaybackTimeoutSeconds,
      int remoteZncPlaybackWindowMinutes,
      int commandHistoryMaxSize,
      int chatTranscriptMaxLinesPerTarget) {
    return new HistoryControls(
        spinner(initialLoadLines),
        spinner(pageSize),
        spinner(autoLoadWheelDebounceMs),
        checkbox(smoothWheelScrollingEnabled),
        spinner(loadOlderChunkSize),
        spinner(loadOlderChunkDelayMs),
        spinner(loadOlderChunkEdtBudgetMs),
        checkbox(deferRichTextDuringBatch),
        checkbox(lockViewportDuringLoadOlder),
        spinner(remoteRequestTimeoutSeconds),
        spinner(remoteZncPlaybackTimeoutSeconds),
        spinner(remoteZncPlaybackWindowMinutes),
        spinner(commandHistoryMaxSize),
        spinner(chatTranscriptMaxLinesPerTarget));
  }

  private static JCheckBox checkbox(boolean selected) {
    JCheckBox checkbox = new JCheckBox();
    checkbox.setSelected(selected);
    return checkbox;
  }

  private static JSpinner spinner(int value) {
    return new JSpinner(new SpinnerNumberModel(value, -1_000, 300_000, 1));
  }
}

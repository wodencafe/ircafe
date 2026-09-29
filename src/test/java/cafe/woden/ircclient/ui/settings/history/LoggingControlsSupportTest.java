package cafe.woden.ircclient.ui.settings.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.RuntimeConfigChatLoggingAdapter;
import cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures;
import cafe.woden.ircclient.config.api.ChatLoggingRuntimeConfigPort;
import cafe.woden.ircclient.config.properties.LogProperties;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoggingControlsSupportTest {

  @Test
  void persistedLoggingEnabledOverridesStartupLogProperties() {
    ChatLoggingRuntimeConfigPort runtimeConfig = mock(ChatLoggingRuntimeConfigPort.class);
    LogProperties logProps =
        new LogProperties(false, true, false, true, true, true, 0, 50_000, 250, null);
    when(runtimeConfig.readChatLoggingEnabled(false)).thenReturn(true);

    LoggingControls controls =
        LoggingControlsSupport.buildControls(
            runtimeConfig, logProps, new ArrayList<>(), null, null);

    assertTrue(controls.enabled.isSelected());
    assertTrue(controls.logSoftIgnored.isEnabled());
  }

  @Test
  void fallsBackToStartupLogPropertiesWhenPersistedValueUnavailable() {
    LogProperties logProps =
        new LogProperties(false, true, false, true, true, true, 0, 50_000, 250, null);

    LoggingControls controls =
        LoggingControlsSupport.buildControls(null, logProps, new ArrayList<>(), null, null);

    assertFalse(controls.enabled.isSelected());
    assertFalse(controls.logSoftIgnored.isEnabled());
  }

  @Test
  void readSettingsNormalizesLoggingValues() {
    LoggingControls controls =
        controls(true, true, false, true, false, false, -3, 5, 20_000, "  ", true);

    LoggingControlsSupport.LoggingSettings settings = LoggingControlsSupport.readSettings(controls);

    assertTrue(settings.enabled());
    assertTrue(settings.logSoftIgnored());
    assertFalse(settings.redactionAuditEnabled());
    assertTrue(settings.logPrivateMessages());
    assertFalse(settings.savePrivateMessageList());
    assertFalse(settings.keepForever());
    assertEquals(0, settings.retentionDays());
    assertEquals(100, settings.writerQueueMax());
    assertEquals(10_000, settings.writerBatchSize());
    assertEquals("ircafe-chatlog", settings.dbFileBaseName());
    assertTrue(settings.dbNextToRuntimeConfig());
  }

  @Test
  void rememberSettingsPersistsLoggingValues(@TempDir Path tempDir) throws Exception {
    RuntimeConfigStore runtimeConfig =
        RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));
    ChatLoggingRuntimeConfigPort loggingRuntimeConfig =
        new RuntimeConfigChatLoggingAdapter(runtimeConfig);
    LoggingControlsSupport.LoggingSettings settings =
        new LoggingControlsSupport.LoggingSettings(
            true, false, true, false, true, false, 30, 123_456, 777, "chat-db", false);

    LoggingControlsSupport.rememberSettings(loggingRuntimeConfig, settings);

    String yaml = Files.readString(tempDir.resolve("ircafe.yml"));
    assertTrue(yaml.contains("logging:"));
    assertTrue(yaml.contains("enabled: true"));
    assertTrue(yaml.contains("logSoftIgnoredLines: false"));
    assertTrue(yaml.contains("redactionAuditEnabled: true"));
    assertTrue(yaml.contains("logPrivateMessages: false"));
    assertTrue(yaml.contains("savePrivateMessageList: true"));
    assertTrue(yaml.contains("keepForever: false"));
    assertTrue(yaml.contains("retentionDays: 30"));
    assertTrue(yaml.contains("writerQueueMax: 123456"));
    assertTrue(yaml.contains("writerBatchSize: 777"));
    assertTrue(yaml.contains("fileBaseName: chat-db"));
    assertTrue(yaml.contains("nextToRuntimeConfig: false"));
  }

  private static LoggingControls controls(
      boolean enabled,
      boolean logSoftIgnored,
      boolean redactionAuditEnabled,
      boolean logPrivateMessages,
      boolean savePrivateMessageList,
      boolean keepForever,
      int retentionDays,
      int writerQueueMax,
      int writerBatchSize,
      String dbBaseName,
      boolean dbNextToConfig) {
    return new LoggingControls(
        checkbox(enabled),
        checkbox(logSoftIgnored),
        checkbox(redactionAuditEnabled),
        checkbox(logPrivateMessages),
        checkbox(savePrivateMessageList),
        new JButton(),
        checkbox(keepForever),
        spinner(retentionDays),
        spinner(writerQueueMax),
        spinner(writerBatchSize),
        new JTextField(dbBaseName),
        checkbox(dbNextToConfig),
        new JTextArea());
  }

  private static JCheckBox checkbox(boolean selected) {
    JCheckBox checkbox = new JCheckBox();
    checkbox.setSelected(selected);
    return checkbox;
  }

  private static JSpinner spinner(int value) {
    return new JSpinner(new SpinnerNumberModel(value, -1_000, 2_000_000, 1));
  }
}

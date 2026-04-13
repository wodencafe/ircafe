package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class LoggingControlsSupportTest {

  @Test
  void persistedLoggingEnabledOverridesStartupLogProperties() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
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
}

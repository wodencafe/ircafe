package cafe.woden.ircclient.ui.settings.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.RuntimeConfigNetworkSettingsAdapter;
import cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures;
import cafe.woden.ircclient.config.StartupTlsSettings;
import cafe.woden.ircclient.net.NetFloodProtectionContext;
import cafe.woden.ircclient.net.NetHeartbeatContext;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.ui.settings.UiSettingsTestFixtures;
import java.nio.file.Path;
import java.security.Security;
import java.util.ArrayList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyRsaTlsPreferencesFunctionalTest {
  @TempDir Path temp;

  @Test
  void checkboxAppliesPersistsAndReopensWithoutChangingCurrentTlsPolicy() throws Exception {
    Path config = temp.resolve("ircafe.yml");
    String[] startupArgs = {"--ircafe.runtime-config=" + config};
    String policyBefore = Security.getProperty("jdk.tls.disabledAlgorithms");
    var previousProxy = NetProxyContext.settings();
    var previousHeartbeat = NetHeartbeatContext.settings();
    var previousFlood = NetFloodProtectionContext.settings();
    var previousTls = NetTlsContext.settings();
    try (var store = RuntimeConfigStoreTestFixtures.store(config)) {
      store.startPersistence();
      var runtime = new RuntimeConfigNetworkSettingsAdapter(store);
      for (boolean enabled : new boolean[] {true, false}) {
        SwingUtilities.invokeAndWait(
            () -> {
              var closeables = new ArrayList<AutoCloseable>();
              try {
                var controls = build(runtime, closeables);
                assertTrue(controls.allowLegacyRsaTls().getToolTipText().contains("restart"));
                assertFalse(controls.trustAllTlsCertificates().isSelected());
                // Toggle the persisted value, then exercise the same read/save path as Apply.
                assertEquals(!enabled, controls.allowLegacyRsaTls().isSelected());
                controls.allowLegacyRsaTls().doClick();
                NetworkAdvancedControlsSupport.rememberSettings(
                    runtime, null, NetworkAdvancedControlsSupport.readSettings(controls));
                assertEquals(enabled, runtime.readClientTlsAllowLegacyRsa());
                var reopened = build(runtime, closeables);
                assertEquals(enabled, reopened.allowLegacyRsaTls().isSelected());
                assertEquals(policyBefore, Security.getProperty("jdk.tls.disabledAlgorithms"));
              } finally {
                for (var closeable : closeables) {
                  try {
                    closeable.close();
                  } catch (Exception ex) {
                    throw new AssertionError(ex);
                  }
                }
              }
            });
        // Durability and startup parsing run off the EDT.
        store.flushPendingWrites();
        assertEquals(enabled, StartupTlsSettings.readAllowLegacyRsa(startupArgs));
      }
    } finally {
      NetProxyContext.configure(previousProxy);
      NetHeartbeatContext.configure(previousHeartbeat);
      NetFloodProtectionContext.configure(previousFlood);
      NetTlsContext.configure(previousTls);
    }
  }

  private static NetworkAdvancedControls build(
      RuntimeConfigNetworkSettingsAdapter runtime, ArrayList<AutoCloseable> closeables) {
    return NetworkAdvancedControlsSupport.buildControls(
        UiSettingsTestFixtures.legacyBuilder().build(),
        closeables,
        runtime,
        false,
        false,
        "{base}/{network}");
  }
}

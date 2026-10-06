package cafe.woden.ircclient.ui.settings.network;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.NetworkSettingsRuntimeConfigPort;
import cafe.woden.ircclient.net.NetFloodProtectionContext;
import cafe.woden.ircclient.net.NetHeartbeatContext;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.net.NetTlsContext;
import java.util.ArrayList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class FloodProtectionPreferencesFunctionalTest {
  @Test
  void controlsLoadApplyAndReopenFloodPreferencesOnEdt() throws Exception {
    IrcProperties.FloodProtection previous = NetFloodProtectionContext.settings();
    var previousProxy = NetProxyContext.settings();
    var previousHeartbeat = NetHeartbeatContext.settings();
    var previousTls = NetTlsContext.settings();
    try {
      NetFloodProtectionContext.configure(null);
      SwingUtilities.invokeAndWait(
          () -> {
            var closeables = new ArrayList<AutoCloseable>();
            try {
              var controls = build(closeables);
              assertTrue(controls.floodProtection.enabled().isSelected());
              assertTrue(controls.floodProtection.commandIntervalMs().isEnabled());
              assertEquals(2000, controls.floodProtection.commandIntervalMs().getValue());
              assertEquals(5, controls.floodProtection.autoJoinDelaySeconds().getValue());
              controls.floodProtection.commandIntervalMs().setValue(2500);
              controls.floodProtection.autoJoinDelaySeconds().setValue(8);
              controls.floodProtection.enabled().doClick();
              assertFalse(controls.floodProtection.commandIntervalMs().isEnabled());
              assertTrue(controls.floodProtection.autoJoinDelaySeconds().isEnabled());
              var advanced =
                  new NetworkAdvancedControls(
                      controls.proxy,
                      null,
                      null,
                      controls.heartbeat,
                      controls.floodProtection,
                      controls.bouncer,
                      null,
                      controls.trustAllTlsCertificates,
                      controls.panel,
                      null);
              var settings = NetworkAdvancedControlsSupport.readSettings(advanced);
              var runtime = mock(NetworkSettingsRuntimeConfigPort.class);
              NetworkAdvancedControlsSupport.rememberSettings(runtime, null, settings);
              verify(runtime)
                  .rememberClientFloodProtection(
                      new IrcProperties.FloodProtection(false, 2500, 8000));
              var reopened = build(closeables);
              assertFalse(reopened.floodProtection.enabled().isSelected());
              assertFalse(reopened.floodProtection.commandIntervalMs().isEnabled());
              assertEquals(2500, reopened.floodProtection.commandIntervalMs().getValue());
              assertEquals(8, reopened.floodProtection.autoJoinDelaySeconds().getValue());
              reopened.floodProtection.enabled().doClick();
              assertTrue(reopened.floodProtection.commandIntervalMs().isEnabled());
            } finally {
              for (var closeable : closeables) {
                try {
                  closeable.close();
                } catch (Exception e) {
                  throw new AssertionError(e);
                }
              }
            }
          });
    } finally {
      NetFloodProtectionContext.configure(previous);
      NetProxyContext.configure(previousProxy);
      NetHeartbeatContext.configure(previousHeartbeat);
      NetTlsContext.configure(previousTls);
    }
  }

  private static NetworkConnectionPanelControls build(ArrayList<AutoCloseable> closeables) {
    return NetworkConnectionPanelSupport.buildControls(
        new IrcProperties.Proxy(false, "", 1080, "", "", true, 10000, 30000),
        new IrcProperties.Heartbeat(true, 15000, 360000),
        NetFloodProtectionContext.settings(),
        closeables,
        false,
        false,
        "{base}/{network}");
  }
}

package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class ServerTreeHeaderControlsTest {

  @Test
  void statusTextUpdatesConnectionTooltips() {
    ConnectButton connect = new ConnectButton();
    DisconnectButton disconnect = new DisconnectButton();
    ServerTreeHeaderControls controls =
        new ServerTreeHeaderControls(new JPanel(), connect, disconnect, null);

    controls.setStatusText("Connecting");

    assertEquals("Connect all disconnected servers. Current: Connecting", connect.getToolTipText());
    assertEquals(
        "Disconnect connected/connecting servers. Current: Connecting",
        disconnect.getToolTipText());
  }

  @Test
  void connectionControlEnablementIsDelegated() {
    ConnectButton connect = new ConnectButton();
    DisconnectButton disconnect = new DisconnectButton();
    ServerTreeHeaderControls controls =
        new ServerTreeHeaderControls(new JPanel(), connect, disconnect, null);

    controls.setConnectionControlsEnabled(false, true);

    assertFalse(connect.isEnabled());
    assertTrue(disconnect.isEnabled());
  }

  @Test
  void addServerButtonIsDisabledWhenDialogsAreUnavailable() {
    ConnectButton connect = new ConnectButton();
    DisconnectButton disconnect = new DisconnectButton();
    ServerTreeHeaderControls controls =
        new ServerTreeHeaderControls(new JPanel(), connect, disconnect, null);

    JButton addServer = (JButton) controls.panel().getComponent(0);

    assertFalse(addServer.isEnabled());
    assertEquals("Add server", addServer.getToolTipText());
  }
}

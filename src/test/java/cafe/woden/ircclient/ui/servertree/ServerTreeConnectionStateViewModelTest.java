package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import org.junit.jupiter.api.Test;

class ServerTreeConnectionStateViewModelTest {

  @Test
  void canConnectAllowsDisconnectedAndDisconnectingStates() {
    assertTrue(ServerTreeConnectionStateViewModel.canConnect(ConnectionState.DISCONNECTED));
    assertTrue(ServerTreeConnectionStateViewModel.canConnect(ConnectionState.DISCONNECTING));

    assertFalse(ServerTreeConnectionStateViewModel.canConnect(ConnectionState.CONNECTING));
    assertFalse(ServerTreeConnectionStateViewModel.canConnect(ConnectionState.CONNECTED));
    assertFalse(ServerTreeConnectionStateViewModel.canConnect(ConnectionState.RECONNECTING));
    assertTrue(ServerTreeConnectionStateViewModel.canConnect(null));
  }

  @Test
  void canDisconnectIncludesConnectingStateForConnectCancel() {
    assertTrue(ServerTreeConnectionStateViewModel.canDisconnect(ConnectionState.CONNECTING));
    assertTrue(ServerTreeConnectionStateViewModel.canDisconnect(ConnectionState.CONNECTED));
    assertTrue(ServerTreeConnectionStateViewModel.canDisconnect(ConnectionState.RECONNECTING));

    assertFalse(ServerTreeConnectionStateViewModel.canDisconnect(ConnectionState.DISCONNECTING));
    assertFalse(ServerTreeConnectionStateViewModel.canDisconnect(ConnectionState.DISCONNECTED));
    assertFalse(ServerTreeConnectionStateViewModel.canDisconnect(null));
  }
}

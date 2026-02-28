package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import org.junit.jupiter.api.Test;

class ServerTreeConnectionStateViewModelTest {

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

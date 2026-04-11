package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerRuntimeUiUpdater;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeHeaderControls;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ServerTreeRuntimeHeaderApiTest {

  @Test
  void delegatesRuntimeAndHeaderOperations() {
    ServerTreeServerRuntimeUiUpdater runtimeUiUpdater =
        mock(ServerTreeServerRuntimeUiUpdater.class);
    ServerTreeServerRuntimeUiUpdater.Context runtimeUiUpdaterContext =
        mock(ServerTreeServerRuntimeUiUpdater.Context.class);
    ServerTreeHeaderControls headerControls = mock(ServerTreeHeaderControls.class);
    ServerTreeRuntimeHeaderApi api =
        new ServerTreeRuntimeHeaderApi(runtimeUiUpdater, runtimeUiUpdaterContext, headerControls);

    Instant at = Instant.now();
    api.setServerConnectionState("libera", ConnectionState.CONNECTED);
    api.setServerDesiredOnline("libera", true);
    api.setServerConnectionDiagnostics("libera", "error", 123L);
    api.setServerConnectedIdentity("libera", "irc.libera.chat", 6697, "nick", at);
    api.setServerIrcv3Capability("libera", "draft/example", "LS", true);
    api.setServerIsupportToken("libera", "NICKLEN", "32");
    api.setServerVersionDetails("libera", "server", "v1", "i", "k");
    api.setStatusText("Connected");
    api.setConnectionControlsEnabled(true, false);
    api.setConnectedUi(true);

    verify(runtimeUiUpdater)
        .setServerConnectionState(runtimeUiUpdaterContext, "libera", ConnectionState.CONNECTED);
    verify(runtimeUiUpdater).setServerDesiredOnline(runtimeUiUpdaterContext, "libera", true);
    verify(runtimeUiUpdater)
        .setServerConnectionDiagnostics(runtimeUiUpdaterContext, "libera", "error", 123L);
    verify(runtimeUiUpdater)
        .setServerConnectedIdentity(
            runtimeUiUpdaterContext, "libera", "irc.libera.chat", 6697, "nick", at);
    verify(runtimeUiUpdater)
        .setServerIrcv3Capability(runtimeUiUpdaterContext, "libera", "draft/example", "LS", true);
    verify(runtimeUiUpdater)
        .setServerIsupportToken(runtimeUiUpdaterContext, "libera", "NICKLEN", "32");
    verify(runtimeUiUpdater)
        .setServerVersionDetails(runtimeUiUpdaterContext, "libera", "server", "v1", "i", "k");
    verify(headerControls).setStatusText("Connected");
    verify(headerControls).setConnectionControlsEnabled(true, false);
    verify(headerControls).setConnectionControlsEnabled(false, true);
  }
}

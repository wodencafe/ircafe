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
    ServerTreeRuntimeHeaderApi api = new ServerTreeRuntimeHeaderApi(runtimeUiUpdater);
    ServerTreeRuntimeHeaderApi.Context context =
        ServerTreeRuntimeHeaderApi.context(
            runtimeUiUpdaterContext,
            headerControls::setStatusText,
            headerControls::setConnectionControlsEnabled);

    Instant at = Instant.now();
    api.setServerConnectionState(context, "libera", ConnectionState.CONNECTED);
    api.setServerDesiredOnline(context, "libera", true);
    api.setServerConnectionDiagnostics(context, "libera", "error", 123L);
    api.setServerConnectedIdentity(context, "libera", "irc.libera.chat", 6697, "nick", at);
    api.setServerIrcv3Capability(context, "libera", "draft/example", "LS", true);
    api.setServerIsupportToken(context, "libera", "NICKLEN", "32");
    api.setServerVersionDetails(context, "libera", "server", "v1", "i", "k");
    api.setStatusText(context, "Connected");
    api.setConnectionControlsEnabled(context, true, false);
    api.setConnectedUi(context, true);

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

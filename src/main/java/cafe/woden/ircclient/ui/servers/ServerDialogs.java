package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerRegistry;
import java.awt.Window;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ServerDialogs {
  private final ServerRegistry serverRegistry;

  public ServerDialogs(ServerRegistry serverRegistry) {
    this.serverRegistry = serverRegistry;
  }

  public void openAddServer(Window parent) {
    runOnEdt(() -> {
      ServerEditorDialog dlg = new ServerEditorDialog(parent, "Add Server", null);
      Optional<IrcProperties.Server> result = dlg.open();
      result.ifPresent(serverRegistry::upsert);
    });
  }

  public void openManageServers(Window parent) {
    runOnEdt(() -> {
      ServersDialog dlg = new ServersDialog(parent, serverRegistry);
      dlg.open();
    });
  }

  private static void runOnEdt(Runnable r) {
    if (SwingUtilities.isEventDispatchThread()) r.run();
    else SwingUtilities.invokeLater(r);
  }
}

package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import java.awt.Window;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ServerDialogs {
  private final ServerRegistry serverRegistry;
  private final EphemeralServerRegistry ephemeralServers;
  private final RuntimeConfigStore runtimeConfig;

  public ServerDialogs(
      ServerRegistry serverRegistry,
      EphemeralServerRegistry ephemeralServers,
      RuntimeConfigStore runtimeConfig) {
    this.serverRegistry = serverRegistry;
    this.ephemeralServers = ephemeralServers;
    this.runtimeConfig = runtimeConfig;
  }

  public void openAddServer(Window parent) {
    runOnEdt(
        () -> {
          ServerEditorDialog dlg = new ServerEditorDialog(parent, "Add Server", null, true);
          Optional<IrcProperties.Server> result = dlg.open();
          result.ifPresent(
              next -> {
                serverRegistry.upsert(next);
                runtimeConfig.rememberServerAutoConnectOnStart(
                    next.id(), dlg.autoConnectOnStartSelected());
              });
        });
  }

  public void openManageServers(Window parent) {
    runOnEdt(
        () -> {
          ServersDialog dlg = new ServersDialog(parent, serverRegistry, runtimeConfig);
          dlg.open();
        });
  }

  public void openEditServer(Window parent, String serverId) {
    runOnEdt(
        () -> {
          String id = Objects.toString(serverId, "").trim();
          if (id.isEmpty()) return;

          Optional<IrcProperties.Server> curOpt = serverRegistry.find(id);
          if (curOpt.isEmpty()) {
            JOptionPane.showMessageDialog(
                parent,
                "Server '"
                    + id
                    + "' is not a persisted server and can't be edited here.\n\n"
                    + "Use Servers \u2192 Add Server\u2026 to create a persistent entry.",
                "Edit Server",
                JOptionPane.INFORMATION_MESSAGE);
            return;
          }

          IrcProperties.Server cur = curOpt.get();
          String originalId = Objects.toString(cur.id(), "").trim();
          boolean autoConnectOnStart = runtimeConfig.readServerAutoConnectOnStart(originalId, true);

          ServerEditorDialog dlg =
              new ServerEditorDialog(parent, "Edit Server", cur, autoConnectOnStart);
          Optional<IrcProperties.Server> out = dlg.open();
          if (out.isEmpty()) return;

          IrcProperties.Server next = out.get();
          String nextId = Objects.toString(next.id(), "").trim();
          if (!Objects.equals(originalId, nextId) && serverRegistry.containsId(nextId)) {
            JOptionPane.showMessageDialog(
                parent,
                "A server with id '" + nextId + "' already exists.",
                "Duplicate server id",
                JOptionPane.ERROR_MESSAGE);
            return;
          }

          if (!Objects.equals(originalId, nextId)) {
            serverRegistry.remove(originalId);
            runtimeConfig.rememberServerAutoConnectOnStart(originalId, true);
          }
          serverRegistry.upsert(next);
          runtimeConfig.rememberServerAutoConnectOnStart(nextId, dlg.autoConnectOnStartSelected());
        });
  }

  /** Persist an ephemeral server entry so it survives restarts / bouncer disconnects. */
  public void openSaveEphemeralServer(Window parent, String serverId) {
    runOnEdt(
        () -> {
          String id = Objects.toString(serverId, "").trim();
          if (id.isEmpty()) return;

          if (serverRegistry.containsId(id)) {
            JOptionPane.showMessageDialog(
                parent,
                "Server '" + id + "' is already saved.",
                "Save Server",
                JOptionPane.INFORMATION_MESSAGE);
            return;
          }

          Optional<IrcProperties.Server> ephOpt =
              (ephemeralServers == null) ? Optional.empty() : ephemeralServers.find(id);
          if (ephOpt.isEmpty()) {
            JOptionPane.showMessageDialog(
                parent,
                "Server '" + id + "' is not an ephemeral server (or is no longer available).",
                "Save Server",
                JOptionPane.INFORMATION_MESSAGE);
            return;
          }

          IrcProperties.Server seed = ephOpt.get();
          boolean autoConnectOnStart = runtimeConfig.readServerAutoConnectOnStart(id, true);

          ServerEditorDialog dlg =
              new ServerEditorDialog(parent, "Save Server", seed, autoConnectOnStart);
          Optional<IrcProperties.Server> out = dlg.open();
          if (out.isEmpty()) return;

          IrcProperties.Server next = out.get();
          String nextId = Objects.toString(next.id(), "").trim();
          if (nextId.isEmpty()) return;

          if (serverRegistry.containsId(nextId)) {
            JOptionPane.showMessageDialog(
                parent,
                "A persisted server with id '" + nextId + "' already exists.",
                "Duplicate server id",
                JOptionPane.ERROR_MESSAGE);
            return;
          }
          if (ephemeralServers != null
              && ephemeralServers.containsId(nextId)
              && !Objects.equals(id, nextId)) {
            JOptionPane.showMessageDialog(
                parent,
                "An ephemeral server with id '"
                    + nextId
                    + "' already exists.\n\n"
                    + "Tip: keep the same id when saving bouncer networks so they don't show twice.",
                "Duplicate server id",
                JOptionPane.ERROR_MESSAGE);
            return;
          }

          serverRegistry.upsert(next);
          runtimeConfig.rememberServerAutoConnectOnStart(nextId, dlg.autoConnectOnStartSelected());
          if (ephemeralServers != null) {
            // Remove the ephemeral copy (importers will also avoid re-adding if a persisted entry
            // exists).
            ephemeralServers.remove(id);
          }
        });
  }

  private static void runOnEdt(Runnable r) {
    if (SwingUtilities.isEventDispatchThread()) r.run();
    else SwingUtilities.invokeLater(r);
  }
}

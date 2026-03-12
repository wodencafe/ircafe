package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles outbound /connect, /disconnect, /reconnect, and /quit command flow. */
@Component
@ApplicationLayer
final class OutboundConnectionLifecycleCommandService {

  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final ChatCommandRuntimeConfigPort runtimeConfig;

  OutboundConnectionLifecycleCommandService(
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      ChatCommandRuntimeConfigPort runtimeConfig) {
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
  }

  void handleConnect(String target) {
    ConnectionCommandTarget cmd = parseConnectionCommandTarget(target);
    if (cmd == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(), "(connect)", "Usage: /connect [serverId|all]");
      return;
    }
    if (cmd.all()) {
      connectionCoordinator.connectAll();
      return;
    }
    connectionCoordinator.connectOne(cmd.serverId());
  }

  void handleDisconnect(String target) {
    ConnectionCommandTarget cmd = parseConnectionCommandTarget(target);
    if (cmd == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(),
          "(disconnect)",
          "Usage: /disconnect [serverId|all]");
      return;
    }
    if (cmd.all()) {
      connectionCoordinator.disconnectAll();
      return;
    }
    connectionCoordinator.disconnectOne(cmd.serverId());
  }

  void handleReconnect(String target) {
    ConnectionCommandTarget cmd = parseConnectionCommandTarget(target);
    if (cmd == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(), "(reconnect)", "Usage: /reconnect [serverId|all]");
      return;
    }
    if (cmd.all()) {
      connectionCoordinator.reconnectAll();
      return;
    }
    connectionCoordinator.reconnectOne(cmd.serverId());
  }

  void handleQuit(String reason) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef status = targetCoordinator.safeStatusTarget();
    String sid =
        (at != null && at.serverId() != null && !at.serverId().isBlank())
            ? at.serverId()
            : status.serverId();
    if (sid == null || sid.isBlank()) {
      ui.appendStatus(status, "(quit)", "No server selected.");
      return;
    }

    String msg = reason == null ? "" : reason.trim();
    if (msg.isEmpty()) {
      String configured = Objects.toString(runtimeConfig.readDefaultQuitMessage(), "").trim();
      msg = configured.isEmpty() ? ChatCommandRuntimeConfigPort.DEFAULT_QUIT_MESSAGE : configured;
    }
    if (containsCrlf(msg)) {
      ui.appendStatus(
          new TargetRef(sid, "status"), "(quit)", "Refusing to send multi-line /quit reason.");
      return;
    }

    connectionCoordinator.disconnectOne(sid, msg);
  }

  private ConnectionCommandTarget parseConnectionCommandTarget(String rawTarget) {
    String raw = Objects.toString(rawTarget, "").trim();
    if (raw.isEmpty()) {
      TargetRef at = targetCoordinator.getActiveTarget();
      if (at != null) {
        String sid = Objects.toString(at.serverId(), "").trim();
        if (!sid.isEmpty()) return new ConnectionCommandTarget(false, sid);
      }
      return new ConnectionCommandTarget(true, "");
    }

    if ("all".equalsIgnoreCase(raw) || "*".equals(raw)) {
      return new ConnectionCommandTarget(true, "");
    }

    if (raw.indexOf(' ') >= 0 || containsCrlf(raw)) {
      return null;
    }

    return new ConnectionCommandTarget(false, raw);
  }

  private record ConnectionCommandTarget(boolean all, String serverId) {}

  private static boolean containsCrlf(String s) {
    return s != null && (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0);
  }
}

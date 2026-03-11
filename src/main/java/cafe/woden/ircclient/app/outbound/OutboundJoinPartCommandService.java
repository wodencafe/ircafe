package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.irc.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles outbound /join and /part command flow. */
@Component
@ApplicationLayer
final class OutboundJoinPartCommandService {

  private final IrcTargetMembershipPort targetMembership;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final CommandTargetPolicy commandTargetPolicy;
  private final ChatCommandRuntimeConfigPort runtimeConfig;
  private final JoinRoutingPort joinRoutingState;

  OutboundJoinPartCommandService(
      @Qualifier("ircTargetMembershipPort") IrcTargetMembershipPort targetMembership,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      CommandTargetPolicy commandTargetPolicy,
      ChatCommandRuntimeConfigPort runtimeConfig,
      JoinRoutingPort joinRoutingState) {
    this.targetMembership = Objects.requireNonNull(targetMembership, "targetMembership");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.commandTargetPolicy = Objects.requireNonNull(commandTargetPolicy, "commandTargetPolicy");
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    this.joinRoutingState = Objects.requireNonNull(joinRoutingState, "joinRoutingState");
  }

  void handleJoin(CompositeDisposable disposables, String channel, String key) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(join)", "Select a server first.");
      return;
    }

    String chan = channel == null ? "" : channel.trim();
    String joinKey = key == null ? "" : key.trim();
    if (chan.isEmpty()) {
      ui.appendStatus(at, "(join)", "Usage: /join <#channel> [key]");
      return;
    }

    if (shouldPersistJoinedChannel(at.serverId())) {
      runtimeConfig.rememberJoinedChannel(at.serverId(), chan);
    }

    // Route join results back to the origin buffer; ui-only surfaces route to status.
    TargetRef joinOrigin = at.isUiOnly() ? new TargetRef(at.serverId(), "status") : at;
    joinRoutingState.rememberOrigin(at.serverId(), chan, joinOrigin);

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(conn)",
          "Not connected (join queued in config only)");
      return;
    }

    if (containsCrlf(chan) || containsCrlf(joinKey)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(join)",
          "Refusing to send multi-line /join input.");
      return;
    }

    if (!joinKey.isEmpty()) {
      String line = "JOIN " + chan + " " + joinKey;
      disposables.add(
          targetMembership
              .sendRaw(at.serverId(), line)
              .subscribe(
                  () -> {},
                  err ->
                      ui.appendError(
                          targetCoordinator.safeStatusTarget(),
                          "(join-error)",
                          String.valueOf(err))));
      return;
    }

    disposables.add(
        targetMembership
            .joinChannel(at.serverId(), chan)
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(join-error)",
                        String.valueOf(err))));
  }

  void handlePart(CompositeDisposable disposables, String channel, String reason) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(part)", "Select a server first.");
      return;
    }

    String chan = channel == null ? "" : channel.trim();
    String msg = reason == null ? "" : reason.trim();

    TargetRef target;
    if (chan.isEmpty()) {
      ParsedPartTarget explicitFromReason = parseExplicitPartTargetFromReason(at.serverId(), msg);
      if (explicitFromReason != null) {
        target = explicitFromReason.target();
        msg = explicitFromReason.reason();
      } else if (!commandTargetPolicy.isChannelLikeTarget(at)) {
        ui.appendStatus(
            at, "(part)", "Usage: /part [#channel] [reason] (or select a channel first)");
        return;
      } else {
        target = at;
      }
    } else {
      target = new TargetRef(at.serverId(), chan);
      if (!commandTargetPolicy.isChannelLikeTargetForServer(at.serverId(), target.target())) {
        ui.appendStatus(at, "(part)", "Usage: /part [#channel] [reason]");
        return;
      }
    }

    if (target.isChannel()) {
      targetCoordinator.disconnectChannel(target, msg);
      return;
    }

    TargetRef status = new TargetRef(target.serverId(), "status");
    if (!connectionCoordinator.isConnected(target.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (containsCrlf(target.target()) || containsCrlf(msg)) {
      ui.appendStatus(status, "(part)", "Refusing to send multi-line /part input.");
      return;
    }

    disposables.add(
        targetMembership
            .partChannel(target.serverId(), target.target(), msg.isEmpty() ? null : msg)
            .subscribe(
                () -> ui.appendStatus(status, "(part)", "Requested leave for " + target.target()),
                err -> ui.appendError(status, "(part-error)", String.valueOf(err))));
  }

  private ParsedPartTarget parseExplicitPartTargetFromReason(String serverId, String reason) {
    String text = reason == null ? "" : reason.trim();
    if (text.isEmpty()) return null;

    int split = text.indexOf(' ');
    String candidate = split < 0 ? text : text.substring(0, split).trim();
    if (!commandTargetPolicy.isChannelLikeTargetForServer(serverId, candidate)) {
      return null;
    }

    String trailingReason = split < 0 ? "" : text.substring(split + 1).trim();
    return new ParsedPartTarget(new TargetRef(serverId, candidate), trailingReason);
  }

  private static boolean containsCrlf(String s) {
    return s != null && (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0);
  }

  private boolean shouldPersistJoinedChannel(String serverId) {
    return commandTargetPolicy.backendForServer(serverId)
        != IrcProperties.Server.Backend.QUASSEL_CORE;
  }

  private record ParsedPartTarget(TargetRef target, String reason) {}
}

package cafe.woden.ircclient.app.outbound.channel;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundRawCommandSupport;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import lombok.NonNull;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles outbound /join and /part command flow. */
@Component
@ApplicationLayer
public final class OutboundJoinPartCommandService {

  @NonNull
  @Qualifier("ircTargetMembershipPort")
  private final IrcTargetMembershipPort targetMembership;

  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final CommandTargetPolicy commandTargetPolicy;
  @NonNull private final ChatCommandRuntimeConfigPort runtimeConfig;
  @NonNull private final JoinRoutingPort joinRoutingState;
  @NonNull private final PartCommandSupport partCommandSupport;

  public OutboundJoinPartCommandService(
      @Qualifier("ircTargetMembershipPort") IrcTargetMembershipPort targetMembership,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      CommandTargetPolicy commandTargetPolicy,
      ChatCommandRuntimeConfigPort runtimeConfig,
      JoinRoutingPort joinRoutingState) {
    this.targetMembership = targetMembership;
    this.ui = ui;
    this.connectionCoordinator = connectionCoordinator;
    this.targetCoordinator = targetCoordinator;
    this.commandTargetPolicy = commandTargetPolicy;
    this.runtimeConfig = runtimeConfig;
    this.joinRoutingState = joinRoutingState;
    this.partCommandSupport =
        new PartCommandSupport(
            targetMembership, ui, connectionCoordinator, targetCoordinator, commandTargetPolicy);
  }

  public void handleJoin(CompositeDisposable disposables, String channel, String key) {
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
      targetCoordinator.syncRuntimeAutoJoinForReconnect(at.serverId());
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

    if (OutboundRawCommandSupport.containsLineBreaks(chan)
        || OutboundRawCommandSupport.containsLineBreaks(joinKey)) {
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

  public void handlePart(CompositeDisposable disposables, String channel, String reason) {
    partCommandSupport.handlePart(disposables, channel, reason);
  }

  private boolean shouldPersistJoinedChannel(String serverId) {
    return commandTargetPolicy.backendForServer(serverId)
        != IrcProperties.Server.Backend.QUASSEL_CORE;
  }
}

package cafe.woden.ircclient.app.outbound.channel;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.OutboundConnectionStatusSupport;
import cafe.woden.ircclient.app.outbound.OutboundRawCommandSupport;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared active-target, connection, and raw-send support for target-membership commands. */
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundTargetMembershipCommandSupport {

  @NonNull private final IrcTargetMembershipPort targetMembership;
  @NonNull private final UiPort ui;
  @NonNull private final OutboundConnectionStatusSupport outboundConnectionStatusSupport;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final CommandTargetPolicy commandTargetPolicy;
  @NonNull private final OutboundRawCommandSupport rawCommandSupport;

  OutboundTargetMembershipCommandSupport(
      IrcTargetMembershipPort targetMembership,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      CommandTargetPolicy commandTargetPolicy,
      OutboundRawCommandSupport rawCommandSupport) {
    this(
        targetMembership,
        ui,
        new OutboundConnectionStatusSupport(ui, connectionCoordinator),
        targetCoordinator,
        commandTargetPolicy,
        rawCommandSupport);
  }

  TargetRef requireActiveTarget(String tag) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active != null) {
      return active;
    }
    appendStatus(targetCoordinator.safeStatusTarget(), tag, "Select a server first.");
    return null;
  }

  boolean ensureConnected(String serverId) {
    return outboundConnectionStatusSupport.ensureConnectedStatusOnly(serverId);
  }

  boolean validateSingleLine(String serverId, String tag, String commandName, String... inputs) {
    if (inputs != null) {
      for (String input : inputs) {
        if (OutboundRawCommandSupport.containsLineBreaks(input)) {
          appendStatus(
              new TargetRef(serverId, "status"),
              tag,
              "Refusing to send multi-line " + commandName + " input.");
          return false;
        }
      }
    }
    return true;
  }

  void requestNames(
      CompositeDisposable disposables, String serverId, TargetRef out, String channel) {
    TargetRef status = new TargetRef(serverId, "status");
    ensureTargetExists(out);
    appendStatus(out, "(names)", "Requesting NAMES for " + channel + "...");
    disposables.add(
        targetMembership
            .requestNames(serverId, channel)
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(names-error)", String.valueOf(err))));
  }

  TargetRef resolveWhoOutputTarget(String serverId, String args) {
    String firstToken = Objects.toString(args, "").trim();
    int split = firstToken.indexOf(' ');
    if (split >= 0) {
      firstToken = firstToken.substring(0, split).trim();
    }
    return commandTargetPolicy.isChannelLikeTargetForServer(serverId, firstToken)
        ? new TargetRef(serverId, firstToken)
        : new TargetRef(serverId, "status");
  }

  void sendRaw(
      CompositeDisposable disposables,
      String serverId,
      TargetRef out,
      String tag,
      String line,
      String errorTag) {
    sendRaw(disposables, serverId, out, tag, line, errorTag, true);
  }

  void sendRawToExistingTarget(
      CompositeDisposable disposables,
      String serverId,
      TargetRef out,
      String tag,
      String line,
      String errorTag) {
    sendRaw(disposables, serverId, out, tag, line, errorTag, false);
  }

  private void sendRaw(
      CompositeDisposable disposables,
      String serverId,
      TargetRef out,
      String tag,
      String line,
      String errorTag,
      boolean ensureTarget) {
    TargetRef status = new TargetRef(serverId, "status");
    OutboundRawCommandSupport.PreparedRawLine prepared = rawCommandSupport.prepare(out, line);
    if (ensureTarget) {
      ensureTargetExists(out);
    }
    appendStatus(out, tag, "→ " + rawCommandSupport.preview(line, prepared));
    disposables.add(
        targetMembership
            .sendRaw(serverId, prepared.line())
            .subscribe(() -> {}, err -> ui.appendError(status, errorTag, String.valueOf(err))));
  }

  void appendStatus(TargetRef target, String tag, String text) {
    ui.appendStatus(target, tag, text);
  }

  void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  void beginChannelList(String serverId, String statusText) {
    ui.beginChannelList(serverId, statusText);
  }

  void selectTarget(TargetRef target) {
    ui.selectTarget(target);
  }
}

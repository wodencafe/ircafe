package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared active-target, raw-send, and target-routing support for membership/list commands. */
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundMembershipQuerySupport {

  @NonNull private final IrcTargetMembershipPort targetMembership;
  @NonNull private final UiPort ui;
  @NonNull private final OutboundConnectionStatusSupport outboundConnectionStatusSupport;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final CommandTargetPolicy commandTargetPolicy;
  @NonNull private final OutboundRawCommandSupport rawCommandSupport;

  OutboundMembershipQuerySupport(
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

  boolean validateSingleLine(String serverId, String tag, String commandName, String input) {
    if (!OutboundRawCommandSupport.containsLineBreaks(input)) {
      return true;
    }
    appendStatus(
        new TargetRef(serverId, "status"),
        tag,
        "Refusing to send multi-line " + commandName + " input.");
    return false;
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
    TargetRef status = new TargetRef(serverId, "status");
    OutboundRawCommandSupport.PreparedRawLine prepared = rawCommandSupport.prepare(out, line);
    ensureTargetExists(out);
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

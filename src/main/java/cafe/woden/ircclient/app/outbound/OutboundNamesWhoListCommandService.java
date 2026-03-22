package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles outbound /names, /who, and /list command flow. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundNamesWhoListCommandService {

  @NonNull
  @Qualifier("ircTargetMembershipPort")
  private final IrcTargetMembershipPort targetMembership;

  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final CommandTargetPolicy commandTargetPolicy;
  @NonNull private final OutboundRawCommandSupport rawCommandSupport;

  void handleNames(CompositeDisposable disposables, String channel) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(names)", "Select a server first.");
      return;
    }

    String ch = commandTargetPolicy.resolveChannelOrNull(at, channel);
    if (ch == null) {
      ui.appendStatus(at, "(names)", "Usage: /names [#channel]");
      ui.appendStatus(at, "(names)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (OutboundRawCommandSupport.containsLineBreaks(ch)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(names)",
          "Refusing to send multi-line /names input.");
      return;
    }

    TargetRef out = new TargetRef(at.serverId(), ch);
    TargetRef status = new TargetRef(at.serverId(), "status");
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(names)", "Requesting NAMES for " + ch + "...");

    disposables.add(
        targetMembership
            .requestNames(at.serverId(), ch)
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(names-error)", String.valueOf(err))));
  }

  void handleWho(CompositeDisposable disposables, String args) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(who)", "Select a server first.");
      return;
    }

    String a = args == null ? "" : args.trim();
    if (a.isEmpty()) {
      if (at.isChannel()) {
        a = at.target();
      } else {
        ui.appendStatus(at, "(who)", "Usage: /who [mask|#channel] [flags]");
        ui.appendStatus(at, "(who)", "Tip: from a channel tab, /who defaults to that channel.");
        return;
      }
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (OutboundRawCommandSupport.containsLineBreaks(a)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(who)",
          "Refusing to send multi-line /who input.");
      return;
    }

    String line = "WHO " + a;
    String firstToken = a;
    int sp = firstToken.indexOf(' ');
    if (sp >= 0) firstToken = firstToken.substring(0, sp).trim();

    TargetRef out =
        commandTargetPolicy.isChannelLikeTargetForServer(at.serverId(), firstToken)
            ? new TargetRef(at.serverId(), firstToken)
            : new TargetRef(at.serverId(), "status");
    TargetRef status = new TargetRef(at.serverId(), "status");
    OutboundRawCommandSupport.PreparedRawLine prepared = rawCommandSupport.prepare(out, line);
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(who)", "→ " + rawCommandSupport.preview(line, prepared));

    disposables.add(
        targetMembership
            .sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(who-error)", String.valueOf(err))));
  }

  void handleList(CompositeDisposable disposables, String args) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(list)", "Select a server first.");
      return;
    }

    String a = args == null ? "" : args.trim();
    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (OutboundRawCommandSupport.containsLineBreaks(a)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(list)",
          "Refusing to send multi-line /list input.");
      return;
    }

    TargetRef channelList =
        at.hasNetworkQualifier()
            ? TargetRef.channelList(at.serverId(), at.networkQualifierToken())
            : TargetRef.channelList(at.serverId());
    ui.ensureTargetExists(channelList);
    ui.beginChannelList(
        at.serverId(),
        a.isEmpty() ? "Loading channel list..." : ("Loading channel list (" + a + ")..."));
    ui.selectTarget(channelList);

    String line = a.isEmpty() ? "LIST" : ("LIST " + a);
    TargetRef status = new TargetRef(at.serverId(), "status");
    OutboundRawCommandSupport.PreparedRawLine prepared = rawCommandSupport.prepare(status, line);
    ui.ensureTargetExists(status);
    ui.appendStatus(status, "(list)", "→ " + rawCommandSupport.preview(line, prepared));

    disposables.add(
        targetMembership
            .sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(list-error)", String.valueOf(err))));
  }
}

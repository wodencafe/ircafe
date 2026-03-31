package cafe.woden.ircclient.app.outbound.channel;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared target resolution and execution flow for outbound /part commands. */
@ApplicationLayer
@RequiredArgsConstructor
final class PartCommandSupport {

  @NonNull private final IrcTargetMembershipPort targetMembership;
  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final CommandTargetPolicy commandTargetPolicy;

  void handlePart(CompositeDisposable disposables, String channel, String reason) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(part)", "Select a server first.");
      return;
    }

    String chan = channel == null ? "" : channel.trim();
    String message = reason == null ? "" : reason.trim();

    TargetRef target;
    if (chan.isEmpty()) {
      ParsedPartTarget explicitFromReason =
          parseExplicitPartTargetFromReason(active.serverId(), message);
      if (explicitFromReason != null) {
        target = explicitFromReason.target();
        message = explicitFromReason.reason();
      } else if (!commandTargetPolicy.isChannelLikeTarget(active)) {
        ui.appendStatus(
            active, "(part)", "Usage: /part [#channel] [reason] (or select a channel first)");
        return;
      } else {
        target = active;
      }
    } else {
      target = new TargetRef(active.serverId(), chan);
      if (!commandTargetPolicy.isChannelLikeTargetForServer(active.serverId(), target.target())) {
        ui.appendStatus(active, "(part)", "Usage: /part [#channel] [reason]");
        return;
      }
    }

    if (target.isChannel()) {
      targetCoordinator.closeChannel(target, message);
      return;
    }

    TargetRef status = new TargetRef(target.serverId(), "status");
    if (!connectionCoordinator.isConnected(target.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (containsCrlf(target.target()) || containsCrlf(message)) {
      ui.appendStatus(status, "(part)", "Refusing to send multi-line /part input.");
      return;
    }

    disposables.add(
        targetMembership
            .partChannel(target.serverId(), target.target(), message.isEmpty() ? null : message)
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

  private record ParsedPartTarget(TargetRef target, String reason) {}
}

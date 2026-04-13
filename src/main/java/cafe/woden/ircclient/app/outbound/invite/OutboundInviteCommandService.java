package cafe.woden.ircclient.app.outbound.invite;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundRawCommandSupport;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles outbound invite command flow: /invite and pending invite actions. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class OutboundInviteCommandService {

  @NonNull
  @Qualifier("ircMediatorInteractionPort")
  private final IrcMediatorInteractionPort mediatorIrc;

  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final CommandTargetPolicy commandTargetPolicy;
  @NonNull private final OutboundRawCommandSupport rawCommandSupport;
  @NonNull private final PendingInviteCommandSupport pendingInviteCommandSupport;

  public void handleInvite(CompositeDisposable disposables, String nick, String channel) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(invite)", "Select a server first.");
      return;
    }

    String n = Objects.toString(nick, "").trim();
    String ch = commandTargetPolicy.resolveChannelOrNull(at, channel);

    if (n.isEmpty() || ch == null) {
      ui.appendStatus(at, "(invite)", "Usage: /invite <nick> [#channel]");
      ui.appendStatus(at, "(invite)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (OutboundRawCommandSupport.containsLineBreaks(ch)
        || OutboundRawCommandSupport.containsLineBreaks(n)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(invite)",
          "Refusing to send multi-line /invite input.");
      return;
    }

    String line = "INVITE " + n + " " + ch;
    TargetRef out = new TargetRef(at.serverId(), ch);
    TargetRef status = new TargetRef(at.serverId(), "status");
    OutboundRawCommandSupport.PreparedRawLine prepared = rawCommandSupport.prepare(out, line);
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(invite)", "→ " + rawCommandSupport.preview(line, prepared));

    disposables.add(
        mediatorIrc
            .sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(invite-error)", String.valueOf(err))));
  }

  public void handleInviteList(String serverId) {
    pendingInviteCommandSupport.handleInviteList(serverId);
  }

  public void handleInviteJoin(CompositeDisposable disposables, String inviteToken) {
    pendingInviteCommandSupport.handleInviteJoin(disposables, inviteToken);
  }

  public void handleInviteIgnore(String inviteToken) {
    pendingInviteCommandSupport.handleInviteIgnore(inviteToken);
  }

  public void handleInviteWhois(CompositeDisposable disposables, String inviteToken) {
    pendingInviteCommandSupport.handleInviteWhois(disposables, inviteToken);
  }

  public void handleInviteBlock(String inviteToken) {
    pendingInviteCommandSupport.handleInviteBlock(inviteToken);
  }

  public void handleInviteAutoJoin(String mode) {
    pendingInviteCommandSupport.handleInviteAutoJoin(mode);
  }
}

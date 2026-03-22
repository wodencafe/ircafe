package cafe.woden.ircclient.app.outbound.invite;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.OutboundRawCommandSupport;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreMaskNormalizer;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingInvitePort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared pending-invite action, resolution, and auto-join support. */
@ApplicationLayer
@RequiredArgsConstructor
final class PendingInviteCommandSupport {

  @NonNull private final IrcMediatorInteractionPort mediatorIrc;
  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final CommandTargetPolicy commandTargetPolicy;
  @NonNull private final ChatCommandRuntimeConfigPort runtimeConfig;
  @NonNull private final PendingInvitePort pendingInviteState;
  @NonNull private final WhoisRoutingPort whoisRoutingState;
  @NonNull private final IgnoreListCommandPort ignoreListService;

  void handleInviteList(String serverId) {
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef fallback = active != null ? active : targetCoordinator.safeStatusTarget();

    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) sid = Objects.toString(fallback.serverId(), "").trim();
    if (sid.isEmpty()) {
      ui.appendStatus(fallback, "(invite)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(sid, "status");
    List<PendingInvitePort.PendingInvite> invites = pendingInviteState.listForServer(sid);
    if (invites.isEmpty()) {
      ui.appendStatus(status, "(invite)", "No pending invites on " + sid + ".");
      return;
    }

    ui.appendStatus(status, "(invite)", "Pending invites on " + sid + " (" + invites.size() + "):");
    for (PendingInvitePort.PendingInvite invite : invites) {
      if (invite == null) continue;
      String from = invite.inviterNick().isBlank() ? "server" : invite.inviterNick();
      String invitee = invite.inviteeNick().isBlank() ? "you" : invite.inviteeNick();
      StringBuilder line =
          new StringBuilder()
              .append("  #")
              .append(invite.id())
              .append(" ")
              .append(from)
              .append(" invited ")
              .append(invitee)
              .append(" to ")
              .append(invite.channel());
      if (invite.repeatCount() > 1) line.append(" (x").append(invite.repeatCount()).append(")");
      if (!invite.reason().isBlank()) line.append(" - ").append(invite.reason());
      ui.appendStatus(status, "(invite)", line.toString());
    }
    ui.appendStatus(
        status,
        "(invite)",
        "Actions: /invjoin <id|last> (or /join -i), /invignore <id|last>, /invwhois <id|last>, /invblock <id|last>");
  }

  void handleInviteJoin(CompositeDisposable disposables, String inviteToken) {
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef fallback = active != null ? active : targetCoordinator.safeStatusTarget();
    PendingInvitePort.PendingInvite invite =
        resolveInviteByToken(inviteToken, fallback, "(invite)");
    if (invite == null) return;

    TargetRef status = new TargetRef(invite.serverId(), "status");
    if (!connectionCoordinator.isConnected(invite.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (OutboundRawCommandSupport.containsLineBreaks(invite.channel())) {
      ui.appendStatus(status, "(invite)", "Refusing to send multi-line /invjoin input.");
      return;
    }

    if (shouldPersistJoinedChannel(invite.serverId())) {
      runtimeConfig.rememberJoinedChannel(invite.serverId(), invite.channel());
      targetCoordinator.syncRuntimeAutoJoinForReconnect(invite.serverId());
    }
    ui.appendStatus(
        status, "(invite)", "Joining " + invite.channel() + " from invite #" + invite.id() + "...");

    disposables.add(
        mediatorIrc
            .joinChannel(invite.serverId(), invite.channel())
            .subscribe(
                () -> pendingInviteState.remove(invite.id()),
                err -> ui.appendError(status, "(invite-error)", String.valueOf(err))));
  }

  void handleInviteIgnore(String inviteToken) {
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef fallback = active != null ? active : targetCoordinator.safeStatusTarget();
    PendingInvitePort.PendingInvite invite =
        resolveInviteByToken(inviteToken, fallback, "(invite)");
    if (invite == null) return;

    pendingInviteState.remove(invite.id());
    TargetRef status = new TargetRef(invite.serverId(), "status");
    String from = invite.inviterNick().isBlank() ? "server" : invite.inviterNick();
    ui.appendStatus(
        status,
        "(invite)",
        "Ignored invite #" + invite.id() + " from " + from + " to " + invite.channel() + ".");
  }

  void handleInviteWhois(CompositeDisposable disposables, String inviteToken) {
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef fallback = active != null ? active : targetCoordinator.safeStatusTarget();
    PendingInvitePort.PendingInvite invite =
        resolveInviteByToken(inviteToken, fallback, "(invite)");
    if (invite == null) return;

    TargetRef status = new TargetRef(invite.serverId(), "status");
    String nick = Objects.toString(invite.inviterNick(), "").trim();
    if (nick.isEmpty() || "server".equalsIgnoreCase(nick)) {
      ui.appendStatus(
          status, "(invite)", "No inviter nick available for invite #" + invite.id() + ".");
      return;
    }
    if (!connectionCoordinator.isConnected(invite.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    whoisRoutingState.put(invite.serverId(), nick, status);
    ui.appendStatus(
        status, "(whois)", "Requesting WHOIS for " + nick + " (invite #" + invite.id() + ")...");
    disposables.add(
        mediatorIrc
            .whois(invite.serverId(), nick)
            .subscribe(() -> {}, err -> ui.appendError(status, "(whois)", String.valueOf(err))));
  }

  void handleInviteBlock(String inviteToken) {
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef fallback = active != null ? active : targetCoordinator.safeStatusTarget();
    PendingInvitePort.PendingInvite invite =
        resolveInviteByToken(inviteToken, fallback, "(invite)");
    if (invite == null) return;

    TargetRef status = new TargetRef(invite.serverId(), "status");
    String nick = Objects.toString(invite.inviterNick(), "").trim();
    if (nick.isEmpty() || "server".equalsIgnoreCase(nick)) {
      ui.appendStatus(
          status, "(invite)", "No inviter nick available for invite #" + invite.id() + ".");
      return;
    }

    boolean added = ignoreListService.addMask(invite.serverId(), nick);
    String stored = IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask(nick);
    if (added) {
      ui.appendStatus(status, "(invite)", "Blocked invites from " + nick + " (" + stored + ").");
    } else {
      ui.appendStatus(status, "(invite)", "Already blocking " + nick + " (" + stored + ").");
    }
    pendingInviteState.remove(invite.id());
  }

  void handleInviteAutoJoin(String mode) {
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef out = active != null ? active : targetCoordinator.safeStatusTarget();

    String raw = Objects.toString(mode, "").trim().toLowerCase(Locale.ROOT);
    if ("toggle".equals(raw)) {
      boolean enabled = !pendingInviteState.inviteAutoJoinEnabled();
      pendingInviteState.setInviteAutoJoinEnabled(enabled);
      runtimeConfig.rememberInviteAutoJoinEnabled(enabled);
      ui.appendStatus(
          out, "(invite)", "Invite auto-join is now " + (enabled ? "enabled." : "disabled."));
      return;
    }
    if (raw.isEmpty() || "status".equals(raw)) {
      ui.appendStatus(
          out,
          "(invite)",
          "Invite auto-join is "
              + (pendingInviteState.inviteAutoJoinEnabled() ? "enabled" : "disabled")
              + ". Use /inviteautojoin on|off or /ajinvite.");
      return;
    }

    Boolean enabled = parseOnOff(raw);
    if (enabled == null) {
      ui.appendStatus(
          out,
          "(invite)",
          "Usage: /inviteautojoin [on|off|status] (alias: /ajinvite [on|off|status|toggle])");
      return;
    }

    pendingInviteState.setInviteAutoJoinEnabled(enabled);
    runtimeConfig.rememberInviteAutoJoinEnabled(enabled);
    ui.appendStatus(
        out, "(invite)", "Invite auto-join is now " + (enabled ? "enabled." : "disabled."));
  }

  private PendingInvitePort.PendingInvite resolveInviteByToken(
      String rawToken, TargetRef fallback, String statusTag) {
    TargetRef out = fallback != null ? fallback : targetCoordinator.safeStatusTarget();
    String token = Objects.toString(rawToken, "").trim();
    if (token.isEmpty() || "last".equalsIgnoreCase(token)) {
      PendingInvitePort.PendingInvite invite = null;
      String sid = Objects.toString(out.serverId(), "").trim();
      if (!sid.isEmpty()) {
        invite = pendingInviteState.latestForServer(sid);
      }
      if (invite == null) {
        invite = pendingInviteState.latestAnyServer();
      }
      if (invite == null) {
        ui.appendStatus(out, statusTag, "No pending invites.");
      }
      return invite;
    }

    long id;
    try {
      id = Long.parseLong(token);
    } catch (NumberFormatException ex) {
      ui.appendStatus(out, statusTag, "Expected invite id or 'last' (example: /invjoin 12).");
      return null;
    }

    PendingInvitePort.PendingInvite invite = pendingInviteState.get(id);
    if (invite == null) {
      ui.appendStatus(out, statusTag, "Invite #" + id + " not found.");
      return null;
    }
    return invite;
  }

  private static Boolean parseOnOff(String raw) {
    return switch (Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT)) {
      case "on", "true", "1", "yes" -> Boolean.TRUE;
      case "off", "false", "0", "no" -> Boolean.FALSE;
      default -> null;
    };
  }

  private boolean shouldPersistJoinedChannel(String serverId) {
    return commandTargetPolicy.backendForServer(serverId)
        != IrcProperties.Server.Backend.QUASSEL_CORE;
  }
}

package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreMaskNormalizer;
import cafe.woden.ircclient.irc.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingInvitePort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles outbound invite command flow: /invite and pending invite actions. */
@Component
@ApplicationLayer
final class OutboundInviteCommandService {

  private final IrcMediatorInteractionPort mediatorIrc;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final CommandTargetPolicy commandTargetPolicy;
  private final OutboundRawLineCorrelationService rawLineCorrelationService;
  private final ChatCommandRuntimeConfigPort runtimeConfig;
  private final PendingInvitePort pendingInviteState;
  private final WhoisRoutingPort whoisRoutingState;
  private final IgnoreListCommandPort ignoreListService;

  OutboundInviteCommandService(
      @Qualifier("ircMediatorInteractionPort") IrcMediatorInteractionPort mediatorIrc,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      CommandTargetPolicy commandTargetPolicy,
      OutboundRawLineCorrelationService rawLineCorrelationService,
      ChatCommandRuntimeConfigPort runtimeConfig,
      PendingInvitePort pendingInviteState,
      WhoisRoutingPort whoisRoutingState,
      IgnoreListCommandPort ignoreListService) {
    this.mediatorIrc = Objects.requireNonNull(mediatorIrc, "mediatorIrc");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.commandTargetPolicy = Objects.requireNonNull(commandTargetPolicy, "commandTargetPolicy");
    this.rawLineCorrelationService =
        Objects.requireNonNull(rawLineCorrelationService, "rawLineCorrelationService");
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    this.pendingInviteState = Objects.requireNonNull(pendingInviteState, "pendingInviteState");
    this.whoisRoutingState = Objects.requireNonNull(whoisRoutingState, "whoisRoutingState");
    this.ignoreListService = Objects.requireNonNull(ignoreListService, "ignoreListService");
  }

  void handleInvite(CompositeDisposable disposables, String nick, String channel) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(invite)", "Select a server first.");
      return;
    }

    String n = Objects.toString(nick, "").trim();
    String ch = resolveChannelOrNull(at, channel);

    if (n.isEmpty() || ch == null) {
      ui.appendStatus(at, "(invite)", "Usage: /invite <nick> [#channel]");
      ui.appendStatus(at, "(invite)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(ch) || containsCrlf(n)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(invite)",
          "Refusing to send multi-line /invite input.");
      return;
    }

    String line = "INVITE " + n + " " + ch;
    TargetRef out = new TargetRef(at.serverId(), ch);
    TargetRef status = new TargetRef(at.serverId(), "status");
    PreparedRawLine prepared = prepareCorrelatedRawLine(out, line);
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(invite)", "→ " + withLabelHint(line, prepared.label()));

    disposables.add(
        mediatorIrc
            .sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(invite-error)", String.valueOf(err))));
  }

  void handleInviteList(String serverId) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef fallback = at != null ? at : targetCoordinator.safeStatusTarget();

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
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef fallback = at != null ? at : targetCoordinator.safeStatusTarget();
    PendingInvitePort.PendingInvite invite =
        resolveInviteByToken(inviteToken, fallback, "(invite)");
    if (invite == null) return;

    TargetRef status = new TargetRef(invite.serverId(), "status");
    if (!connectionCoordinator.isConnected(invite.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (containsCrlf(invite.channel())) {
      ui.appendStatus(status, "(invite)", "Refusing to send multi-line /invjoin input.");
      return;
    }

    if (shouldPersistJoinedChannel(invite.serverId())) {
      runtimeConfig.rememberJoinedChannel(invite.serverId(), invite.channel());
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
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef fallback = at != null ? at : targetCoordinator.safeStatusTarget();
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
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef fallback = at != null ? at : targetCoordinator.safeStatusTarget();
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
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef fallback = at != null ? at : targetCoordinator.safeStatusTarget();
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
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef out = at != null ? at : targetCoordinator.safeStatusTarget();

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

  private String resolveChannelOrNull(TargetRef active, String explicitChannel) {
    String ch = Objects.toString(explicitChannel, "").trim();
    if (!ch.isEmpty()) {
      String sid = active == null ? "" : active.serverId();
      if (commandTargetPolicy.isChannelLikeTargetForServer(sid, ch)) return ch;
      return null;
    }
    if (commandTargetPolicy.isChannelLikeTarget(active)) return active.target();
    return null;
  }

  private PreparedRawLine prepareCorrelatedRawLine(TargetRef origin, String rawLine) {
    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(origin, rawLine);
    return new PreparedRawLine(prepared.line(), prepared.label());
  }

  private record PreparedRawLine(String line, String label) {}

  private static String withLabelHint(String preview, String label) {
    String p = Objects.toString(preview, "").trim();
    String l = Objects.toString(label, "").trim();
    if (l.isEmpty()) return p;
    return p + " {label=" + l + "}";
  }

  private static Boolean parseOnOff(String raw) {
    return switch (Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT)) {
      case "on", "true", "1", "yes" -> Boolean.TRUE;
      case "off", "false", "0", "no" -> Boolean.FALSE;
      default -> null;
    };
  }

  private static boolean containsCrlf(String s) {
    return s != null && (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0);
  }

  private boolean shouldPersistJoinedChannel(String serverId) {
    return commandTargetPolicy.backendForServer(serverId)
        != IrcProperties.Server.Backend.QUASSEL_CORE;
  }
}

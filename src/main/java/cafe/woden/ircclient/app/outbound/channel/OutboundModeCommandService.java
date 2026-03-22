package cafe.woden.ircclient.app.outbound.channel;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundRawCommandSupport;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Handles outbound MODE-related slash commands (/mode, /op, /deop, /voice, /devoice, /ban, /unban).
 *
 * <p>This is extracted from {@code IrcMediator} to keep the mediator focused on wiring and inbound
 * routing. Behavior is intended to be preserved.
 */
@Component
@ApplicationLayer
public class OutboundModeCommandService {

  private final OutboundTargetMembershipCommandSupport targetMembershipCommandSupport;
  private final CommandTargetPolicy commandTargetPolicy;
  private final ModeRoutingPort modeRoutingState;

  public OutboundModeCommandService(
      @Qualifier("ircTargetMembershipPort") IrcTargetMembershipPort targetMembership,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      CommandTargetPolicy commandTargetPolicy,
      ModeRoutingPort modeRoutingState,
      OutboundRawCommandSupport rawCommandSupport) {
    this.commandTargetPolicy = commandTargetPolicy;
    this.modeRoutingState = modeRoutingState;
    this.targetMembershipCommandSupport =
        new OutboundTargetMembershipCommandSupport(
            targetMembership,
            ui,
            connectionCoordinator,
            targetCoordinator,
            commandTargetPolicy,
            rawCommandSupport);
  }

  public void handleMode(CompositeDisposable disposables, String first, String rest) {
    TargetRef at = targetMembershipCommandSupport.requireActiveTarget("(mode)");
    if (at == null) {
      return;
    }

    String f = first == null ? "" : first.trim();
    String r = rest == null ? "" : rest.trim();

    // Determine target channel + mode string.
    String channel;
    String modeSpec;

    if (commandTargetPolicy.isChannelLikeTargetForServer(at.serverId(), f)) {
      channel = f;
      modeSpec = r;
    } else if (commandTargetPolicy.isChannelLikeTarget(at)) {
      channel = at.target();
      modeSpec = (f + (r.isEmpty() ? "" : " " + r)).trim();
    } else {
      targetMembershipCommandSupport.appendStatus(
          at, "(mode)", "Usage: /mode <#channel> [modes] [args...]");
      targetMembershipCommandSupport.appendStatus(
          at, "(mode)", "Tip: from a channel tab you can use /mode +o nick");
      return;
    }

    if (channel == null || channel.isBlank()) {
      targetMembershipCommandSupport.appendStatus(
          at, "(mode)", "Usage: /mode <#channel> [modes] [args...]");
      return;
    }

    if (!targetMembershipCommandSupport.ensureConnected(at.serverId())) {
      return;
    }

    String line =
        "MODE " + channel + (modeSpec == null || modeSpec.isBlank() ? "" : " " + modeSpec);
    TargetRef out =
        at.isChannel()
            ? new TargetRef(at.serverId(), channel)
            : new TargetRef(at.serverId(), "status");

    if (modeSpec == null || modeSpec.isBlank()) {
      modeRoutingState.putPendingModeTarget(at.serverId(), channel, out);
    }

    targetMembershipCommandSupport.sendRaw(
        disposables, at.serverId(), out, "(mode)", line, "(mode-error)");
  }

  public void handleOp(CompositeDisposable disposables, String channel, List<String> nicks) {
    handleSimpleNickMode(
        disposables, channel, nicks, "+o", "Usage: /op [#channel] <nick> [nick...]");
  }

  public void handleDeop(CompositeDisposable disposables, String channel, List<String> nicks) {
    handleSimpleNickMode(
        disposables, channel, nicks, "-o", "Usage: /deop [#channel] <nick> [nick...]");
  }

  public void handleVoice(CompositeDisposable disposables, String channel, List<String> nicks) {
    handleSimpleNickMode(
        disposables, channel, nicks, "+v", "Usage: /voice [#channel] <nick> [nick...]");
  }

  public void handleDevoice(CompositeDisposable disposables, String channel, List<String> nicks) {
    handleSimpleNickMode(
        disposables, channel, nicks, "-v", "Usage: /devoice [#channel] <nick> [nick...]");
  }

  public void handleBan(
      CompositeDisposable disposables, String channel, List<String> masksOrNicks) {
    handleBanMode(disposables, channel, masksOrNicks, true);
  }

  public void handleUnban(
      CompositeDisposable disposables, String channel, List<String> masksOrNicks) {
    handleBanMode(disposables, channel, masksOrNicks, false);
  }

  private void handleSimpleNickMode(
      CompositeDisposable disposables,
      String channel,
      List<String> nicks,
      String mode,
      String usage) {
    TargetRef at = targetMembershipCommandSupport.requireActiveTarget("(mode)");
    if (at == null) {
      return;
    }
    if (!targetMembershipCommandSupport.ensureConnected(at.serverId())) {
      return;
    }

    String ch = commandTargetPolicy.resolveChannelOrNull(at, channel);
    if (ch == null) {
      targetMembershipCommandSupport.appendStatus(at, "(mode)", usage);
      targetMembershipCommandSupport.appendStatus(
          at, "(mode)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (nicks == null || nicks.isEmpty()) {
      targetMembershipCommandSupport.appendStatus(at, "(mode)", usage);
      return;
    }

    TargetRef out = new TargetRef(at.serverId(), ch);
    targetMembershipCommandSupport.ensureTargetExists(out);
    for (String nick : nicks) {
      String n = nick == null ? "" : nick.trim();
      if (n.isEmpty()) continue;

      String line = "MODE " + ch + " " + mode + " " + n;
      targetMembershipCommandSupport.sendRawToExistingTarget(
          disposables, at.serverId(), out, "(mode)", line, "(mode-error)");
    }
  }

  private void handleBanMode(
      CompositeDisposable disposables, String channel, List<String> masksOrNicks, boolean add) {
    TargetRef at = targetMembershipCommandSupport.requireActiveTarget("(mode)");
    if (at == null) {
      return;
    }
    if (!targetMembershipCommandSupport.ensureConnected(at.serverId())) {
      return;
    }

    String ch = commandTargetPolicy.resolveChannelOrNull(at, channel);
    if (ch == null) {
      targetMembershipCommandSupport.appendStatus(
          at,
          "(mode)",
          "Usage: " + (add ? "/ban" : "/unban") + " [#channel] <mask|nick> [mask|nick...]");
      targetMembershipCommandSupport.appendStatus(
          at, "(mode)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (masksOrNicks == null || masksOrNicks.isEmpty()) {
      targetMembershipCommandSupport.appendStatus(
          at,
          "(mode)",
          "Usage: " + (add ? "/ban" : "/unban") + " [#channel] <mask|nick> [mask|nick...]");
      return;
    }

    TargetRef out = new TargetRef(at.serverId(), ch);
    targetMembershipCommandSupport.ensureTargetExists(out);
    String mode = add ? "+b" : "-b";

    for (String item : masksOrNicks) {
      String raw = item == null ? "" : item.trim();
      if (raw.isEmpty()) continue;

      String mask = looksLikeMask(raw) ? raw : (raw + "!*@*");

      String line = "MODE " + ch + " " + mode + " " + mask;
      targetMembershipCommandSupport.sendRawToExistingTarget(
          disposables, at.serverId(), out, "(mode)", line, "(mode-error)");
    }
  }

  private static boolean looksLikeMask(String s) {
    if (s == null) return false;
    return s.indexOf('!') >= 0 || s.indexOf('@') >= 0 || s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
  }
}

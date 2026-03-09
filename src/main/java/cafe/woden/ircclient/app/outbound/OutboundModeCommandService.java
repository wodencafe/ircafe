package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Objects;
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

  private final IrcTargetMembershipPort targetMembership;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final CommandTargetPolicy commandTargetPolicy;
  private final ModeRoutingPort modeRoutingState;
  private final OutboundRawLineCorrelationService rawLineCorrelationService;

  public OutboundModeCommandService(
      @Qualifier("ircTargetMembershipPort") IrcTargetMembershipPort targetMembership,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      CommandTargetPolicy commandTargetPolicy,
      ModeRoutingPort modeRoutingState,
      OutboundRawLineCorrelationService rawLineCorrelationService) {
    this.targetMembership = targetMembership;
    this.ui = ui;
    this.connectionCoordinator = connectionCoordinator;
    this.targetCoordinator = targetCoordinator;
    this.commandTargetPolicy = commandTargetPolicy;
    this.modeRoutingState = modeRoutingState;
    this.rawLineCorrelationService = rawLineCorrelationService;
  }

  public void handleMode(CompositeDisposable disposables, String first, String rest) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(mode)", "Select a server first.");
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
      ui.appendStatus(at, "(mode)", "Usage: /mode <#channel> [modes] [args...]");
      ui.appendStatus(at, "(mode)", "Tip: from a channel tab you can use /mode +o nick");
      return;
    }

    if (channel == null || channel.isBlank()) {
      ui.appendStatus(at, "(mode)", "Usage: /mode <#channel> [modes] [args...]");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
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

    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        prepareCorrelatedRawLine(out, line);
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(mode)", "→ " + withLabelHint(line, prepared.label()));

    disposables.add(
        targetMembership
            .sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        new TargetRef(at.serverId(), "status"),
                        "(mode-error)",
                        String.valueOf(err))));
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
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(mode)", "Select a server first.");
      return;
    }
    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    String ch = resolveChannelOrNull(at, channel);
    if (ch == null) {
      ui.appendStatus(at, "(mode)", usage);
      ui.appendStatus(at, "(mode)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (nicks == null || nicks.isEmpty()) {
      ui.appendStatus(at, "(mode)", usage);
      return;
    }

    TargetRef out = new TargetRef(at.serverId(), ch);
    ui.ensureTargetExists(out);

    for (String nick : nicks) {
      String n = nick == null ? "" : nick.trim();
      if (n.isEmpty()) continue;

      String line = "MODE " + ch + " " + mode + " " + n;
      OutboundRawLineCorrelationService.PreparedRawLine prepared =
          prepareCorrelatedRawLine(out, line);
      ui.appendStatus(out, "(mode)", "→ " + withLabelHint(line, prepared.label()));

      disposables.add(
          targetMembership
              .sendRaw(at.serverId(), prepared.line())
              .subscribe(
                  () -> {},
                  err ->
                      ui.appendError(
                          new TargetRef(at.serverId(), "status"),
                          "(mode-error)",
                          String.valueOf(err))));
    }
  }

  private void handleBanMode(
      CompositeDisposable disposables, String channel, List<String> masksOrNicks, boolean add) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(mode)", "Select a server first.");
      return;
    }
    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    String ch = resolveChannelOrNull(at, channel);
    if (ch == null) {
      ui.appendStatus(
          at,
          "(mode)",
          "Usage: " + (add ? "/ban" : "/unban") + " [#channel] <mask|nick> [mask|nick...]");
      ui.appendStatus(at, "(mode)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (masksOrNicks == null || masksOrNicks.isEmpty()) {
      ui.appendStatus(
          at,
          "(mode)",
          "Usage: " + (add ? "/ban" : "/unban") + " [#channel] <mask|nick> [mask|nick...]");
      return;
    }

    TargetRef out = new TargetRef(at.serverId(), ch);
    ui.ensureTargetExists(out);

    String mode = add ? "+b" : "-b";

    for (String item : masksOrNicks) {
      String raw = item == null ? "" : item.trim();
      if (raw.isEmpty()) continue;

      String mask = looksLikeMask(raw) ? raw : (raw + "!*@*");

      String line = "MODE " + ch + " " + mode + " " + mask;
      OutboundRawLineCorrelationService.PreparedRawLine prepared =
          prepareCorrelatedRawLine(out, line);
      ui.appendStatus(out, "(mode)", "→ " + withLabelHint(line, prepared.label()));

      disposables.add(
          targetMembership
              .sendRaw(at.serverId(), prepared.line())
              .subscribe(
                  () -> {},
                  err ->
                      ui.appendError(
                          new TargetRef(at.serverId(), "status"),
                          "(mode-error)",
                          String.valueOf(err))));
    }
  }

  private OutboundRawLineCorrelationService.PreparedRawLine prepareCorrelatedRawLine(
      TargetRef origin, String rawLine) {
    return rawLineCorrelationService.prepare(origin, rawLine);
  }

  private static String withLabelHint(String preview, String label) {
    String p = Objects.toString(preview, "").trim();
    String l = Objects.toString(label, "").trim();
    if (l.isEmpty()) return p;
    return p + " {label=" + l + "}";
  }

  private static boolean looksLikeMask(String s) {
    if (s == null) return false;
    return s.indexOf('!') >= 0 || s.indexOf('@') >= 0 || s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
  }

  private String resolveChannelOrNull(TargetRef active, String explicitChannel) {
    String ch = explicitChannel == null ? "" : explicitChannel.trim();
    if (!ch.isEmpty()) {
      String sid = active == null ? "" : active.serverId();
      if (commandTargetPolicy.isChannelLikeTargetForServer(sid, ch)) return ch;
      return null;
    }
    if (commandTargetPolicy.isChannelLikeTarget(active)) return active.target();
    return null;
  }
}

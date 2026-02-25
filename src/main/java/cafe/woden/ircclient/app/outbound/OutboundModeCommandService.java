package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.state.LabeledResponseRoutingState;
import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Handles outbound MODE-related slash commands (/mode, /op, /deop, /voice, /devoice, /ban, /unban).
 *
 * <p>This is extracted from {@code IrcMediator} to keep the mediator focused on wiring and inbound
 * routing. Behavior is intended to be preserved.
 */
@Component
public class OutboundModeCommandService {

  private final IrcClientService irc;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final ModeRoutingState modeRoutingState;
  private final LabeledResponseRoutingState labeledResponseRoutingState;

  public OutboundModeCommandService(
      IrcClientService irc,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      ModeRoutingState modeRoutingState,
      LabeledResponseRoutingState labeledResponseRoutingState) {
    this.irc = irc;
    this.ui = ui;
    this.connectionCoordinator = connectionCoordinator;
    this.targetCoordinator = targetCoordinator;
    this.modeRoutingState = modeRoutingState;
    this.labeledResponseRoutingState = labeledResponseRoutingState;
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

    if (f.startsWith("#") || f.startsWith("&")) {
      channel = f;
      modeSpec = r;
    } else if (at.isChannel()) {
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

    PreparedRawLine prepared = prepareCorrelatedRawLine(out, line);
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(mode)", "→ " + withLabelHint(line, prepared.label()));

    disposables.add(
        irc.sendRaw(at.serverId(), prepared.line())
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
      PreparedRawLine prepared = prepareCorrelatedRawLine(out, line);
      ui.appendStatus(out, "(mode)", "→ " + withLabelHint(line, prepared.label()));

      disposables.add(
          irc.sendRaw(at.serverId(), prepared.line())
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
      PreparedRawLine prepared = prepareCorrelatedRawLine(out, line);
      ui.appendStatus(out, "(mode)", "→ " + withLabelHint(line, prepared.label()));

      disposables.add(
          irc.sendRaw(at.serverId(), prepared.line())
              .subscribe(
                  () -> {},
                  err ->
                      ui.appendError(
                          new TargetRef(at.serverId(), "status"),
                          "(mode-error)",
                          String.valueOf(err))));
    }
  }

  private PreparedRawLine prepareCorrelatedRawLine(TargetRef origin, String rawLine) {
    String line = rawLine == null ? "" : rawLine.trim();
    if (line.isEmpty() || origin == null) return new PreparedRawLine(line, "");
    if (!irc.isLabeledResponseAvailable(origin.serverId())) return new PreparedRawLine(line, "");

    LabeledResponseRoutingState.PreparedRawLine prepared =
        labeledResponseRoutingState.prepareOutgoingRaw(origin.serverId(), line);
    String sendLine =
        (prepared == null || prepared.line() == null || prepared.line().isBlank())
            ? line
            : prepared.line();
    String label = (prepared == null) ? "" : Objects.toString(prepared.label(), "").trim();
    if (!label.isEmpty()) {
      labeledResponseRoutingState.remember(origin.serverId(), label, origin, line, Instant.now());
    }
    return new PreparedRawLine(sendLine, label);
  }

  private static String withLabelHint(String preview, String label) {
    String p = Objects.toString(preview, "").trim();
    String l = Objects.toString(label, "").trim();
    if (l.isEmpty()) return p;
    return p + " {label=" + l + "}";
  }

  private record PreparedRawLine(String line, String label) {}

  private static boolean looksLikeMask(String s) {
    if (s == null) return false;
    return s.indexOf('!') >= 0 || s.indexOf('@') >= 0 || s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
  }

  private static String resolveChannelOrNull(TargetRef active, String explicitChannel) {
    String ch = explicitChannel == null ? "" : explicitChannel.trim();
    if (!ch.isEmpty()) return ch;
    if (active != null && active.isChannel()) return active.target();
    return null;
  }
}

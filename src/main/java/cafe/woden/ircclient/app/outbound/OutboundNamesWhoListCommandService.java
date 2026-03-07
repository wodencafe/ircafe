package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Handles outbound /names, /who, and /list command flow. */
@Component
final class OutboundNamesWhoListCommandService {

  private final IrcClientService irc;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final CommandTargetPolicy commandTargetPolicy;
  private final OutboundRawLineCorrelationService rawLineCorrelationService;

  OutboundNamesWhoListCommandService(
      IrcClientService irc,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      CommandTargetPolicy commandTargetPolicy,
      OutboundRawLineCorrelationService rawLineCorrelationService) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.commandTargetPolicy = Objects.requireNonNull(commandTargetPolicy, "commandTargetPolicy");
    this.rawLineCorrelationService =
        Objects.requireNonNull(rawLineCorrelationService, "rawLineCorrelationService");
  }

  void handleNames(CompositeDisposable disposables, String channel) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(names)", "Select a server first.");
      return;
    }

    String ch = resolveChannelOrNull(at, channel);
    if (ch == null) {
      ui.appendStatus(at, "(names)", "Usage: /names [#channel]");
      ui.appendStatus(at, "(names)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(ch)) {
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
        irc.requestNames(at.serverId(), ch)
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

    if (containsCrlf(a)) {
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
    PreparedRawLine prepared = prepareCorrelatedRawLine(out, line);
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(who)", "→ " + withLabelHint(line, prepared.label()));

    disposables.add(
        irc.sendRaw(at.serverId(), prepared.line())
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

    if (containsCrlf(a)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(list)",
          "Refusing to send multi-line /list input.");
      return;
    }

    TargetRef channelList = TargetRef.channelList(at.serverId());
    ui.ensureTargetExists(channelList);
    ui.beginChannelList(
        at.serverId(),
        a.isEmpty() ? "Loading channel list..." : ("Loading channel list (" + a + ")..."));
    ui.selectTarget(channelList);

    String line = a.isEmpty() ? "LIST" : ("LIST " + a);
    TargetRef status = new TargetRef(at.serverId(), "status");
    PreparedRawLine prepared = prepareCorrelatedRawLine(status, line);
    ui.ensureTargetExists(status);
    ui.appendStatus(status, "(list)", "→ " + withLabelHint(line, prepared.label()));

    disposables.add(
        irc.sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(list-error)", String.valueOf(err))));
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

  private static boolean containsCrlf(String s) {
    return s != null && (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0);
  }
}

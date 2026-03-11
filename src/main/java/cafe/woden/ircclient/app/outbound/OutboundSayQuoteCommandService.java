package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles outbound /say and /quote command flow. */
@Component
@ApplicationLayer
final class OutboundSayQuoteCommandService {

  private final IrcTargetMembershipPort targetMembership;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final OutboundRawLineCorrelationService rawLineCorrelationService;
  private final OutboundMessagingCommandService outboundMessagingCommandService;

  OutboundSayQuoteCommandService(
      @Qualifier("ircTargetMembershipPort") IrcTargetMembershipPort targetMembership,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      OutboundRawLineCorrelationService rawLineCorrelationService,
      OutboundMessagingCommandService outboundMessagingCommandService) {
    this.targetMembership = Objects.requireNonNull(targetMembership, "targetMembership");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.rawLineCorrelationService =
        Objects.requireNonNull(rawLineCorrelationService, "rawLineCorrelationService");
    this.outboundMessagingCommandService =
        Objects.requireNonNull(outboundMessagingCommandService, "outboundMessagingCommandService");
  }

  void handleSay(CompositeDisposable disposables, String msg) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(system)", "Select a server first.");
      return;
    }

    String m = msg == null ? "" : msg.trim();
    if (m.isEmpty()) return;

    if (at.isStatus()) {
      sendRawFromStatus(disposables, at.serverId(), m);
      return;
    }

    outboundMessagingCommandService.sendMessage(disposables, at, m);
  }

  void handleQuote(CompositeDisposable disposables, String rawLine) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(quote)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");
    ui.ensureTargetExists(status);

    String line = rawLine == null ? "" : rawLine.trim();
    if (line.isEmpty()) {
      ui.appendStatus(at, "(quote)", "Usage: /quote <RAW IRC LINE>");
      ui.appendStatus(at, "(quote)", "Example: /quote MONITOR +nick");
      ui.appendStatus(at, "(quote)", "Alias: /raw <RAW IRC LINE>");
      return;
    }

    // Prevent accidental multi-line injection.
    if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
      ui.appendStatus(status, "(quote)", "Refusing to send multi-line /quote input.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    TargetRef correlationOrigin = at.isUiOnly() ? status : at;
    PreparedRawLine prepared = prepareCorrelatedRawLine(correlationOrigin, line);

    // Echo a safe preview of what we are sending (avoid leaking secrets).
    String echo = OutboundRawLineCorrelationService.redactIfSensitive(line);
    ui.appendStatus(status, "(quote)", "→ " + withLabelHint(echo, prepared.label()));

    disposables.add(
        targetMembership
            .sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(quote-error)", String.valueOf(err))));
  }

  private void sendRawFromStatus(CompositeDisposable disposables, String serverId, String rawLine) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    TargetRef status = new TargetRef(sid, "status");
    ui.ensureTargetExists(status);

    String line = rawLine == null ? "" : rawLine.trim();
    if (line.isEmpty()) return;

    // Prevent accidental multi-line injection.
    if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
      ui.appendStatus(status, "(raw)", "Refusing to send multi-line input.");
      return;
    }

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    PreparedRawLine prepared = prepareCorrelatedRawLine(status, line);

    // Echo a safe preview of what we are sending (avoid leaking secrets).
    ui.appendStatus(
        status,
        "(raw)",
        "→ "
            + withLabelHint(
                OutboundRawLineCorrelationService.redactIfSensitive(line), prepared.label()));

    disposables.add(
        targetMembership
            .sendRaw(sid, prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(raw-error)", String.valueOf(err))));
  }

  private PreparedRawLine prepareCorrelatedRawLine(TargetRef origin, String rawLine) {
    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(origin, rawLine);
    return new PreparedRawLine(prepared.line(), prepared.label());
  }

  private static String withLabelHint(String preview, String label) {
    String p = Objects.toString(preview, "").trim();
    String l = Objects.toString(label, "").trim();
    if (l.isEmpty()) return p;
    return p + " {label=" + l + "}";
  }

  private record PreparedRawLine(String line, String label) {}
}

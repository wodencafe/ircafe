package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles outbound /say and /quote command flow. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundSayQuoteCommandService {

  @NonNull
  @Qualifier("ircTargetMembershipPort")
  private final IrcTargetMembershipPort targetMembership;

  @NonNull private final UiPort ui;
  @NonNull private final OutboundConnectionStatusSupport outboundConnectionStatusSupport;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final OutboundRawCommandSupport rawCommandSupport;
  @NonNull private final OutboundMessagingCommandService outboundMessagingCommandService;

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
    if (OutboundRawCommandSupport.containsLineBreaks(line)) {
      ui.appendStatus(status, "(quote)", "Refusing to send multi-line /quote input.");
      return;
    }

    if (!outboundConnectionStatusSupport.ensureConnectedStatusOnly(at)) {
      return;
    }

    TargetRef correlationOrigin = at.isUiOnly() ? status : at;
    OutboundRawCommandSupport.PreparedRawLine prepared =
        rawCommandSupport.prepare(correlationOrigin, line);
    ui.appendStatus(status, "(quote)", "→ " + rawCommandSupport.safePreview(line, prepared));

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
    if (OutboundRawCommandSupport.containsLineBreaks(line)) {
      ui.appendStatus(status, "(raw)", "Refusing to send multi-line input.");
      return;
    }

    if (!outboundConnectionStatusSupport.ensureConnectedStatusOnly(sid)) {
      return;
    }

    OutboundRawCommandSupport.PreparedRawLine prepared = rawCommandSupport.prepare(status, line);
    ui.appendStatus(status, "(raw)", "→ " + rawCommandSupport.safePreview(line, prepared));

    disposables.add(
        targetMembership
            .sendRaw(sid, prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(raw-error)", String.valueOf(err))));
  }
}

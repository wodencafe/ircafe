package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Map;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles semantic /upload command flow and backend translation dispatch. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundUploadCommandService implements OutboundHelpContributor {

  @NonNull
  @Qualifier("ircTargetMembershipPort")
  private final IrcTargetMembershipPort targetMembership;

  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final SemanticUploadCommandHandler semanticUploadCommandHandler;
  @NonNull private final OutboundRawLineCorrelationService rawLineCorrelationService;

  void appendUploadHelp(TargetRef out) {
    semanticUploadCommandHandler.appendUploadHelp(out);
  }

  @Override
  public void appendGeneralHelp(TargetRef out) {
    appendUploadHelp(out);
  }

  @Override
  public Map<String, Consumer<TargetRef>> topicHelpHandlers() {
    return Map.of("upload", this::appendUploadHelp);
  }

  void handleUpload(CompositeDisposable disposables, String msgType, String path, String caption) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(upload)", "Select a target first.");
      return;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");
    if (at.isStatus() || at.isUiOnly()) {
      ui.appendStatus(status, "(upload)", "Select a channel or PM first.");
      return;
    }

    SemanticUploadCommandHandler.UploadPreparation uploadPreparation =
        semanticUploadCommandHandler.prepareUpload(at, msgType, path, caption);
    if (uploadPreparation.showUsage()) {
      semanticUploadCommandHandler.appendUploadUsage(at);
      return;
    }
    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (!uploadPreparation.statusMessage().isEmpty()) {
      ui.appendStatus(status, "(upload)", uploadPreparation.statusMessage());
      return;
    }
    String line = uploadPreparation.line();
    if (containsCrlf(line)) {
      ui.appendStatus(status, "(upload)", "Refusing to send multi-line /upload input.");
      return;
    }
    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(at, line);

    disposables.add(
        targetMembership
            .sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(upload-error)",
                        String.valueOf(err))));
  }

  private static boolean containsCrlf(String input) {
    return input != null && (input.indexOf('\n') >= 0 || input.indexOf('\r') >= 0);
  }
}

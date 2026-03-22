package cafe.woden.ircclient.app.outbound.upload;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.OutboundRawCommandSupport;
import cafe.woden.ircclient.app.outbound.help.spi.OutboundHelpContributor;
import cafe.woden.ircclient.app.outbound.upload.spi.SemanticUploadCommandHandler;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
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
public final class OutboundUploadCommandService implements OutboundHelpContributor {

  @NonNull
  @Qualifier("ircTargetMembershipPort")
  private final IrcTargetMembershipPort targetMembership;

  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final SemanticUploadCommandHandler semanticUploadCommandHandler;
  @NonNull private final OutboundRawCommandSupport rawCommandSupport;

  public void appendUploadHelp(TargetRef out) {
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

  public void handleUpload(
      CompositeDisposable disposables, String msgType, String path, String caption) {
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
    if (OutboundRawCommandSupport.containsLineBreaks(line)) {
      ui.appendStatus(status, "(upload)", "Refusing to send multi-line /upload input.");
      return;
    }
    OutboundRawCommandSupport.PreparedRawLine prepared = rawCommandSupport.prepare(at, line);

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
}

package cafe.woden.ircclient.app.outbound.chathistory;

import cafe.woden.ircclient.app.api.Ircv3ChatHistoryFeatureSupport;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort.QueryMode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared chathistory target validation, request routing, and preview/error support. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundChatHistoryRequestSupport {

  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final ChatHistoryRequestRoutingPort chatHistoryRequestRoutingState;
  @NonNull private final Ircv3ChatHistoryFeatureSupport chatHistoryFeatureSupport;

  TargetRef resolveChatHistoryTargetOrNull() {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) {
      appendStatus(targetCoordinator.safeStatusTarget(), "Select a server first.");
      return null;
    }

    TargetRef status = new TargetRef(active.serverId(), "status");
    if (active.isStatus()) {
      appendStatus(status, "Select a channel or query first.");
      return null;
    }
    if (active.isUiOnly()) {
      appendStatus(status, "That view does not support history requests.");
      return null;
    }
    return active;
  }

  void appendStatus(TargetRef target, String text) {
    ui.appendStatus(target, "(chathistory)", text);
  }

  void appendHelp(TargetRef target, String text) {
    ui.appendStatus(target, "(help)", text);
  }

  void requestChatHistory(
      CompositeDisposable disposables,
      TargetRef target,
      int limit,
      String routingSelector,
      QueryMode queryMode,
      String summary,
      String preview,
      Supplier<Completable> requestSupplier) {
    TargetRef status = new TargetRef(target.serverId(), "status");
    if (!connectionCoordinator.isConnected(target.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (!chatHistoryFeatureSupport.isAvailable(target.serverId())) {
      ui.appendStatus(
          status, "(chathistory)", chatHistoryFeatureSupport.unavailableMessage(target.serverId()));
      return;
    }

    appendStatus(target, summary);
    appendStatus(target, "→ " + preview);
    chatHistoryRequestRoutingState.remember(
        target.serverId(),
        target.target(),
        target,
        limit,
        Objects.toString(routingSelector, "").trim(),
        Instant.now(),
        queryMode);
    disposables.add(
        requestSupplier
            .get()
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(chathistory-error)", String.valueOf(err))));
  }
}

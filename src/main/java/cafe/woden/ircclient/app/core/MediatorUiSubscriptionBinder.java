package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.AppSchedulers;
import cafe.woden.ircclient.app.api.UiEventPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.function.Consumer;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Wires non-connection UI subscriptions for {@link IrcMediator}. */
@Component
@ApplicationLayer
public class MediatorUiSubscriptionBinder {

  public void bind(
      UiEventPort uiEvents,
      UiPort ui,
      TargetCoordinator targetCoordinator,
      CompositeDisposable disposables,
      Consumer<UserActionRequest> onUserActionRequest,
      Consumer<String> onOutboundLine,
      Consumer<ParsedInput.BackendNamed> onBackendNamedRequest) {
    disposables.add(
        uiEvents
            .targetSelections()
            // Some UI refresh paths (e.g. LAF/theme updates) can cause the current selection
            // to be re-emitted even when it has not actually changed. De-dupe to avoid
            // re-running expensive side effects (history prefill, WHO refresh, etc.).
            .distinctUntilChanged()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                targetCoordinator::onTargetSelected,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString())));

    disposables.add(
        uiEvents
            .targetActivations()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                targetCoordinator::onTargetActivated,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString())));

    disposables.add(
        uiEvents
            .privateMessageRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                targetCoordinator::openPrivateConversation,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString())));

    disposables.add(
        uiEvents
            .userActionRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                onUserActionRequest::accept,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString())));

    disposables.add(
        uiEvents
            .outboundLines()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                onOutboundLine::accept,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString())));

    disposables.add(
        uiEvents
            .backendNamedCommandRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                onBackendNamedRequest::accept,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString())));

    disposables.add(
        uiEvents
            .closeTargetRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                targetCoordinator::closeTarget,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err))));

    disposables.add(
        uiEvents
            .joinChannelRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                targetCoordinator::joinChannel,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err))));

    disposables.add(
        uiEvents
            .disconnectChannelRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                targetCoordinator::disconnectChannel,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err))));

    disposables.add(
        uiEvents
            .bouncerDetachChannelRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                targetCoordinator::bouncerDetachChannel,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err))));

    disposables.add(
        uiEvents
            .closeChannelRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                targetCoordinator::closeChannel,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err))));

    disposables.add(
        uiEvents
            .clearLogRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                targetCoordinator::clearLog,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err))));
  }
}

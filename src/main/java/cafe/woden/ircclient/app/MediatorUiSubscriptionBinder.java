package cafe.woden.ircclient.app;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Wires non-connection UI subscriptions for {@link IrcMediator}. */
@Component
public class MediatorUiSubscriptionBinder {

  public void bind(
      UiPort ui,
      TargetCoordinator targetCoordinator,
      CompositeDisposable disposables,
      Consumer<UserActionRequest> onUserActionRequest,
      Consumer<String> onOutboundLine
  ) {
    disposables.add(
        ui.targetSelections()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::onTargetSelected,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.targetActivations()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::onTargetActivated,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.privateMessageRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::openPrivateConversation,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.userActionRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(onUserActionRequest::accept,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.outboundLines()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(onOutboundLine::accept,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.closeTargetRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::closeTarget,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    disposables.add(
        ui.clearLogRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::clearLog,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );
  }
}

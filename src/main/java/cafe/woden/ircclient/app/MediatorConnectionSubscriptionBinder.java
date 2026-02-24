package cafe.woden.ircclient.app;

import cafe.woden.ircclient.config.ServerRegistry;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.springframework.stereotype.Component;

/** Wires connection-related UI and registry subscriptions for {@link IrcMediator}. */
@Component
public class MediatorConnectionSubscriptionBinder {

  public void bind(
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      ServerRegistry serverRegistry,
      CompositeDisposable disposables) {
    disposables.add(
        ui.connectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> connectionCoordinator.connectAll()));

    disposables.add(
        ui.disconnectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> connectionCoordinator.disconnectAll()));

    disposables.add(
        ui.connectServerRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(
                connectionCoordinator::connectOne,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err))));

    disposables.add(
        ui.disconnectServerRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(
                connectionCoordinator::disconnectOne,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err))));

    disposables.add(
        serverRegistry
            .updates()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(
                latest -> {
                  connectionCoordinator.onServersUpdated(
                      latest, targetCoordinator.getActiveTarget());
                  targetCoordinator.refreshInputEnabledForActiveTarget();
                },
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(ui-error)",
                        "Server list update failed: " + err)));
  }
}

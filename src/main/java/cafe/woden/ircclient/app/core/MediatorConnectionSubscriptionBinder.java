package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.AppSchedulers;
import cafe.woden.ircclient.app.api.UiEventPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.config.ServerRegistry;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Wires connection-related UI and registry subscriptions for {@link IrcMediator}. */
@Component
@ApplicationLayer
public class MediatorConnectionSubscriptionBinder {

  public void bind(
      UiEventPort uiEvents,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      ServerRegistry serverRegistry,
      CompositeDisposable disposables) {
    disposables.add(
        uiEvents
            .connectClicks()
            .observeOn(AppSchedulers.edt())
            .subscribe(ignored -> connectionCoordinator.connectAll()));

    disposables.add(
        uiEvents
            .disconnectClicks()
            .observeOn(AppSchedulers.edt())
            .subscribe(ignored -> connectionCoordinator.disconnectAll()));

    disposables.add(
        uiEvents
            .connectServerRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                connectionCoordinator::connectOne,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err))));

    disposables.add(
        uiEvents
            .disconnectServerRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                connectionCoordinator::disconnectOne,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err))));

    disposables.add(
        serverRegistry
            .updates()
            .observeOn(AppSchedulers.edt())
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

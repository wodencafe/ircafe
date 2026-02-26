package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.ui.interceptors.InterceptorPanel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Owns interceptor-panel active-target coordination and refresh wiring for {@link ChatDockable}.
 */
final class ChatInterceptorCoordinator {

  private final InterceptorStore interceptorStore;
  private final InterceptorPanel interceptorPanel;
  private final Supplier<TargetRef> activeTargetSupplier;
  private final Runnable dockTitleRefresher;
  private final Consumer<TargetRef> targetSelectionHandler;

  ChatInterceptorCoordinator(
      InterceptorStore interceptorStore,
      InterceptorPanel interceptorPanel,
      Supplier<TargetRef> activeTargetSupplier,
      Runnable dockTitleRefresher,
      Consumer<TargetRef> targetSelectionHandler) {
    this.interceptorStore = Objects.requireNonNull(interceptorStore, "interceptorStore");
    this.interceptorPanel = Objects.requireNonNull(interceptorPanel, "interceptorPanel");
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
    this.dockTitleRefresher = Objects.requireNonNull(dockTitleRefresher, "dockTitleRefresher");
    this.targetSelectionHandler =
        Objects.requireNonNull(targetSelectionHandler, "targetSelectionHandler");
  }

  void bind(CompositeDisposable disposables) {
    Objects.requireNonNull(disposables, "disposables");

    interceptorPanel.setOnSelectTarget(
        ref -> {
          if (ref == null) return;
          targetSelectionHandler.accept(ref);
        });

    disposables.add(
        interceptorStore
            .changes()
            .subscribe(
                change -> {
                  if (change == null) return;

                  TargetRef activeTarget = activeTargetSupplier.get();
                  if (activeTarget == null || !activeTarget.isInterceptor()) return;
                  if (!Objects.equals(activeTarget.serverId(), change.serverId())) return;
                  if (!Objects.equals(activeTarget.interceptorId(), change.interceptorId())) return;

                  SwingUtilities.invokeLater(
                      () -> {
                        dockTitleRefresher.run();
                        interceptorPanel.setInterceptorTarget(
                            activeTarget.serverId(), activeTarget.interceptorId());
                      });
                },
                err -> {
                  // Keep chat UI usable even if interceptor updates fail.
                }));
  }

  void onActiveTargetChanged(TargetRef previousTarget, TargetRef nextTarget) {
    boolean leavingInterceptor = previousTarget != null && previousTarget.isInterceptor();
    boolean enteringInterceptor = nextTarget != null && nextTarget.isInterceptor();
    if (!leavingInterceptor || enteringInterceptor) return;

    try {
      interceptorPanel.setInterceptorTarget("", "");
    } catch (Exception ignored) {
    }
  }
}

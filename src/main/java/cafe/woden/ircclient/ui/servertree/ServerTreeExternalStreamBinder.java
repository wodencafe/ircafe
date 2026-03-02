package cafe.woden.ircclient.ui.servertree;

import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.SwingEdt;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Binds long-lived external store/catalog streams for the server tree. */
public final class ServerTreeExternalStreamBinder {
  private static final Logger log = LoggerFactory.getLogger(ServerTreeExternalStreamBinder.class);

  private final CompositeDisposable disposables;
  private final Consumer<List<ServerEntry>> syncServers;
  private final Consumer<String> refreshNotificationsCount;
  private final BiConsumer<String, String> refreshInterceptorNodeLabel;
  private final Consumer<String> refreshInterceptorGroupCount;
  private final Runnable refreshSojuAutoConnectBadges;
  private final Runnable refreshZncAutoConnectBadges;

  public ServerTreeExternalStreamBinder(
      CompositeDisposable disposables,
      Consumer<List<ServerEntry>> syncServers,
      Consumer<String> refreshNotificationsCount,
      BiConsumer<String, String> refreshInterceptorNodeLabel,
      Consumer<String> refreshInterceptorGroupCount,
      Runnable refreshSojuAutoConnectBadges,
      Runnable refreshZncAutoConnectBadges) {
    this.disposables = Objects.requireNonNull(disposables, "disposables");
    this.syncServers = Objects.requireNonNull(syncServers, "syncServers");
    this.refreshNotificationsCount =
        Objects.requireNonNull(refreshNotificationsCount, "refreshNotificationsCount");
    this.refreshInterceptorNodeLabel =
        Objects.requireNonNull(refreshInterceptorNodeLabel, "refreshInterceptorNodeLabel");
    this.refreshInterceptorGroupCount =
        Objects.requireNonNull(refreshInterceptorGroupCount, "refreshInterceptorGroupCount");
    this.refreshSojuAutoConnectBadges =
        Objects.requireNonNull(refreshSojuAutoConnectBadges, "refreshSojuAutoConnectBadges");
    this.refreshZncAutoConnectBadges =
        Objects.requireNonNull(refreshZncAutoConnectBadges, "refreshZncAutoConnectBadges");
  }

  public void bind(
      ServerCatalog serverCatalog,
      NotificationStore notificationStore,
      InterceptorStore interceptorStore,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect) {
    if (serverCatalog != null) {
      syncServers.accept(serverCatalog.entries());
      disposables.add(
          serverCatalog
              .updates()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  syncServers::accept, err -> log.error("[ircafe] server catalog stream error", err)));
    }

    if (notificationStore != null) {
      disposables.add(
          notificationStore
              .changes()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  ch -> refreshNotificationsCount.accept(ch.serverId()),
                  err -> log.error("[ircafe] notification store stream error", err)));
    }

    if (interceptorStore != null) {
      disposables.add(
          interceptorStore
              .changes()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  ch -> {
                    refreshInterceptorNodeLabel.accept(ch.serverId(), ch.interceptorId());
                    refreshInterceptorGroupCount.accept(ch.serverId());
                  },
                  err -> log.error("[ircafe] interceptor store stream error", err)));
    }

    if (sojuAutoConnect != null) {
      disposables.add(
          sojuAutoConnect
              .updates()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  __ -> refreshSojuAutoConnectBadges.run(),
                  err -> log.error("[ircafe] soju auto-connect store stream error", err)));
    }

    if (zncAutoConnect != null) {
      disposables.add(
          zncAutoConnect
              .updates()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  __ -> refreshZncAutoConnectBadges.run(),
                  err -> log.error("[ircafe] znc auto-connect store stream error", err)));
    }
  }
}

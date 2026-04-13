package cafe.woden.ircclient.ui.servertree;

import cafe.woden.ircclient.bouncer.GenericBouncerAutoConnectStore;
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
import org.springframework.stereotype.Component;

/** Binds long-lived external store/catalog streams for the server tree. */
@Component
public final class ServerTreeExternalStreamBinder {
  private static final Logger log = LoggerFactory.getLogger(ServerTreeExternalStreamBinder.class);

  public interface Context {
    CompositeDisposable disposables();

    void syncServers(List<ServerEntry> latest);

    void refreshNotificationsCount(String serverId);

    void refreshInterceptorNodeLabel(String serverId, String interceptorId);

    void refreshInterceptorGroupCount(String serverId);

    void refreshAutoConnectBadges(String backendId);
  }

  public static Context context(
      CompositeDisposable disposables,
      Consumer<List<ServerEntry>> syncServers,
      Consumer<String> refreshNotificationsCount,
      BiConsumer<String, String> refreshInterceptorNodeLabel,
      Consumer<String> refreshInterceptorGroupCount,
      Consumer<String> refreshAutoConnectBadges) {
    Objects.requireNonNull(disposables, "disposables");
    Objects.requireNonNull(syncServers, "syncServers");
    Objects.requireNonNull(refreshNotificationsCount, "refreshNotificationsCount");
    Objects.requireNonNull(refreshInterceptorNodeLabel, "refreshInterceptorNodeLabel");
    Objects.requireNonNull(refreshInterceptorGroupCount, "refreshInterceptorGroupCount");
    Objects.requireNonNull(refreshAutoConnectBadges, "refreshAutoConnectBadges");
    return new Context() {
      @Override
      public CompositeDisposable disposables() {
        return disposables;
      }

      @Override
      public void syncServers(List<ServerEntry> latest) {
        syncServers.accept(latest);
      }

      @Override
      public void refreshNotificationsCount(String serverId) {
        refreshNotificationsCount.accept(serverId);
      }

      @Override
      public void refreshInterceptorNodeLabel(String serverId, String interceptorId) {
        refreshInterceptorNodeLabel.accept(serverId, interceptorId);
      }

      @Override
      public void refreshInterceptorGroupCount(String serverId) {
        refreshInterceptorGroupCount.accept(serverId);
      }

      @Override
      public void refreshAutoConnectBadges(String backendId) {
        refreshAutoConnectBadges.accept(backendId);
      }
    };
  }

  public void bind(
      Context context,
      ServerCatalog serverCatalog,
      NotificationStore notificationStore,
      InterceptorStore interceptorStore,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect,
      GenericBouncerAutoConnectStore genericAutoConnect) {
    Context in = Objects.requireNonNull(context, "context");
    if (serverCatalog != null) {
      in.syncServers(serverCatalog.entries());
      in.disposables()
          .add(
              serverCatalog
                  .updates()
                  .observeOn(SwingEdt.scheduler())
                  .subscribe(
                      in::syncServers,
                      err -> log.error("[ircafe] server catalog stream error", err)));
    }

    if (notificationStore != null) {
      in.disposables()
          .add(
              notificationStore
                  .changes()
                  .observeOn(SwingEdt.scheduler())
                  .subscribe(
                      ch -> in.refreshNotificationsCount(ch.serverId()),
                      err -> log.error("[ircafe] notification store stream error", err)));
    }

    if (interceptorStore != null) {
      in.disposables()
          .add(
              interceptorStore
                  .changes()
                  .observeOn(SwingEdt.scheduler())
                  .subscribe(
                      ch -> {
                        in.refreshInterceptorNodeLabel(ch.serverId(), ch.interceptorId());
                        in.refreshInterceptorGroupCount(ch.serverId());
                      },
                      err -> log.error("[ircafe] interceptor store stream error", err)));
    }

    if (sojuAutoConnect != null) {
      in.disposables()
          .add(
              sojuAutoConnect
                  .updates()
                  .observeOn(SwingEdt.scheduler())
                  .subscribe(
                      __ -> in.refreshAutoConnectBadges(ServerTreeBouncerBackends.SOJU),
                      err -> log.error("[ircafe] soju auto-connect store stream error", err)));
    }

    if (zncAutoConnect != null) {
      in.disposables()
          .add(
              zncAutoConnect
                  .updates()
                  .observeOn(SwingEdt.scheduler())
                  .subscribe(
                      __ -> in.refreshAutoConnectBadges(ServerTreeBouncerBackends.ZNC),
                      err -> log.error("[ircafe] znc auto-connect store stream error", err)));
    }

    if (genericAutoConnect != null) {
      in.disposables()
          .add(
              genericAutoConnect
                  .updates()
                  .observeOn(SwingEdt.scheduler())
                  .subscribe(
                      __ -> in.refreshAutoConnectBadges(ServerTreeBouncerBackends.GENERIC),
                      err ->
                          log.error(
                              "[ircafe] generic bouncer auto-connect store stream error", err)));
    }
  }
}

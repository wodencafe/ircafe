package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.GenericBouncerAutoConnectStore;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.notifications.NotificationStore;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ServerTreeExternalStreamBinderTest {

  @Test
  void bindSubscribesToExternalStreamsAndDispatchesCallbacks() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    NotificationStore notificationStore = mock(NotificationStore.class);
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    SojuAutoConnectStore sojuAutoConnect = mock(SojuAutoConnectStore.class);
    ZncAutoConnectStore zncAutoConnect = mock(ZncAutoConnectStore.class);
    GenericBouncerAutoConnectStore genericAutoConnect = mock(GenericBouncerAutoConnectStore.class);

    List<ServerEntry> initialServers = List.of(serverEntry("libera"));
    PublishProcessor<List<ServerEntry>> serverUpdates = PublishProcessor.create();
    PublishProcessor<NotificationStore.Change> notificationChanges = PublishProcessor.create();
    PublishProcessor<InterceptorStore.Change> interceptorChanges = PublishProcessor.create();
    PublishProcessor<Map<String, Map<String, Boolean>>> sojuUpdates = PublishProcessor.create();
    PublishProcessor<Map<String, Map<String, Boolean>>> zncUpdates = PublishProcessor.create();
    PublishProcessor<Map<String, Map<String, Boolean>>> genericUpdates = PublishProcessor.create();

    when(serverCatalog.entries()).thenReturn(initialServers);
    when(serverCatalog.updates()).thenReturn(serverUpdates);
    when(notificationStore.changes()).thenReturn(notificationChanges);
    when(interceptorStore.changes()).thenReturn(interceptorChanges);
    when(sojuAutoConnect.updates()).thenReturn(sojuUpdates);
    when(zncAutoConnect.updates()).thenReturn(zncUpdates);
    when(genericAutoConnect.updates()).thenReturn(genericUpdates);

    CompositeDisposable disposables = new CompositeDisposable();
    List<List<ServerEntry>> syncedServers = new ArrayList<>();
    List<String> notificationRefreshes = new ArrayList<>();
    List<String> interceptorLabels = new ArrayList<>();
    List<String> interceptorGroupRefreshes = new ArrayList<>();
    List<String> autoConnectRefreshes = new ArrayList<>();

    ServerTreeExternalStreamBinder binder = new ServerTreeExternalStreamBinder();
    ServerTreeExternalStreamBinder.Context context =
        ServerTreeExternalStreamBinder.context(
            disposables,
            syncedServers::add,
            notificationRefreshes::add,
            (serverId, interceptorId) -> interceptorLabels.add(serverId + ":" + interceptorId),
            interceptorGroupRefreshes::add,
            autoConnectRefreshes::add);

    binder.bind(
        context,
        serverCatalog,
        notificationStore,
        interceptorStore,
        sojuAutoConnect,
        zncAutoConnect,
        genericAutoConnect);

    List<ServerEntry> updatedServers = List.of(serverEntry("oftc"));
    serverUpdates.onNext(updatedServers);
    notificationChanges.onNext(new NotificationStore.Change("libera"));
    interceptorChanges.onNext(new InterceptorStore.Change("libera", "word"));
    sojuUpdates.onNext(Map.of());
    zncUpdates.onNext(Map.of());
    genericUpdates.onNext(Map.of());
    flushEdt();

    assertEquals(List.of(initialServers, updatedServers), syncedServers);
    assertEquals(List.of("libera"), notificationRefreshes);
    assertEquals(List.of("libera:word"), interceptorLabels);
    assertEquals(List.of("libera"), interceptorGroupRefreshes);
    assertEquals(
        List.of(
            ServerTreeBouncerBackends.SOJU,
            ServerTreeBouncerBackends.ZNC,
            ServerTreeBouncerBackends.GENERIC),
        autoConnectRefreshes);
  }

  private static ServerEntry serverEntry(String id) {
    return ServerEntry.persistent(
        new IrcProperties.Server(
            id,
            "irc.example.net",
            6697,
            true,
            "",
            "ircafe",
            "ircafe",
            "IRCafe User",
            null,
            List.of(),
            List.of(),
            null));
  }

  private static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return;
    }
    SwingUtilities.invokeAndWait(() -> {});
  }
}

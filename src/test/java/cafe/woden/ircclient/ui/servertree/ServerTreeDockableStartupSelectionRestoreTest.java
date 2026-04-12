package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerCatalogSynchronizer;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerTreeDockableStartupSelectionRestoreTest {

  @TempDir Path tempDir;

  @Test
  void startupPrefersRememberedTargetWhenLeafExists() throws Exception {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));
    store.rememberLastSelectedTarget("libera", "status");

    ServerTreeDockable dockable = newDockable(store);

    onEdt(() -> invokeSyncServersUnchecked(dockable, List.of(serverEntry("libera"))));
    onEdt(() -> {});

    assertEquals(new TargetRef("libera", "status"), selectedTargetRef(dockable));
  }

  @Test
  void startupFallsBackToDefaultWhenRememberedTargetDoesNotExist() throws Exception {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));
    store.rememberLastSelectedTarget("libera", "#does-not-exist");

    ServerTreeDockable dockable = newDockable(store);

    onEdt(() -> invokeSyncServersUnchecked(dockable, List.of(serverEntry("libera"))));
    onEdt(() -> {});

    assertEquals(TargetRef.channelList("libera"), selectedTargetRef(dockable));
  }

  @Test
  void startupRestoresRememberedTargetWhenItBecomesSelectableLater() throws Exception {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));
    TargetRef remembered = new TargetRef("libera", "##Llamas");
    store.rememberLastSelectedTarget(remembered.serverId(), remembered.target());

    ServerTreeDockable dockable = newDockable(store);

    onEdt(() -> invokeSyncServersUnchecked(dockable, List.of(serverEntry("libera"))));
    onEdt(() -> {});
    assertEquals(TargetRef.channelList("libera"), selectedTargetRef(dockable));

    onEdt(() -> dockable.ensureNode(remembered));
    onEdt(() -> {});

    assertEquals(remembered, selectedTargetRef(dockable));
  }

  @Test
  void lateSubscriberReceivesRestoredSelectionFromReplay() throws Exception {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));
    TargetRef remembered = new TargetRef("libera", "##Llamas");
    store.rememberLastSelectedTarget(remembered.serverId(), remembered.target());

    ServerTreeDockable dockable = newDockable(store);

    onEdt(() -> invokeSyncServersUnchecked(dockable, List.of(serverEntry("libera"))));
    onEdt(() -> dockable.ensureNode(remembered));
    onEdt(() -> {});

    TestSubscriber<TargetRef> subscriber = dockable.selectionStream().test();
    subscriber.awaitDone(1, TimeUnit.SECONDS);
    subscriber.assertValue(remembered);
  }

  private static ServerTreeDockable newDockable(RuntimeConfigStore runtimeConfigStore) {
    return ServerTreeDockableTestSupport.newDockable(
        null,
        runtimeConfigStore,
        null,
        null,
        null,
        new ConnectButton(),
        new DisconnectButton(),
        null,
        null,
        null,
        null);
  }

  private static void invokeSyncServersUnchecked(
      ServerTreeDockable dockable, List<ServerEntry> entries) {
    try {
      invokeSyncServers(dockable, entries);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void invokeSyncServers(ServerTreeDockable dockable, List<ServerEntry> entries)
      throws Exception {
    Field contextField =
        ServerTreeDockable.class.getDeclaredField("serverCatalogSynchronizerContext");
    contextField.setAccessible(true);
    ServerTreeServerCatalogSynchronizer.Context context =
        (ServerTreeServerCatalogSynchronizer.Context) contextField.get(dockable);
    new ServerTreeServerCatalogSynchronizer().syncServers(context, entries);
  }

  private static TargetRef selectedTargetRef(ServerTreeDockable dockable) throws Exception {
    Method m = ServerTreeDockable.class.getDeclaredMethod("selectedTargetRef");
    m.setAccessible(true);
    return (TargetRef) m.invoke(dockable);
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

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}

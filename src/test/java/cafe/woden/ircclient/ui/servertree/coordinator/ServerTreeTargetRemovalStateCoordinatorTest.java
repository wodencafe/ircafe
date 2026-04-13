package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeChannelStateStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerTreeTargetRemovalStateCoordinatorTest {

  @TempDir Path tempDir;

  @Test
  void removingChannelTargetPrunesPersistedAutoJoinButKeepsQueryEntries() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        irc:
          servers:
            - id: libera
              autoJoin:
                - "#ircafe"
                - "##politics"
                - "query:title"
        """);

    RuntimeConfigStore runtimeConfig =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));
    ServerTreeChannelStateStore channelStateStore = new ServerTreeChannelStateStore();
    channelStateStore
        .channelAutoReattachByServer()
        .put("libera", new HashMap<>(Map.of("#ircafe", true, "##politics", true)));
    channelStateStore
        .channelPinnedByServer()
        .put("libera", new HashMap<>(Map.of("##politics", true)));
    channelStateStore
        .channelMutedByServer()
        .put("libera", new HashMap<>(Map.of("##politics", true)));
    channelStateStore
        .channelActivityRankByServer()
        .put("libera", new HashMap<>(Map.of("##politics", 42L)));
    channelStateStore
        .channelCustomOrderByServer()
        .put("libera", new ArrayList<>(List.of("#ircafe", "##politics")));

    AtomicReference<String> emittedServerId = new AtomicReference<>();
    ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore =
        new ServerTreePrivateMessageOnlineStateStore();
    ServerTreeTargetRemovalStateCoordinator coordinator =
        new ServerTreeTargetRemovalStateCoordinator();
    ServerTreeTargetRemovalStateCoordinator.Context context =
        ServerTreeTargetRemovalStateCoordinator.context(
            privateMessageOnlineStateStore,
            runtimeConfig,
            channelStateStore,
            ref -> false,
            () -> true,
            channel -> channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT),
            emittedServerId::set);

    coordinator.cleanupForRemovedTarget(context, new TargetRef("libera", "##politics"));

    assertEquals(List.of("#ircafe"), runtimeConfig.readJoinedChannels("libera"));
    String persisted = Files.readString(cfg);
    assertFalse(persisted.contains("##politics"));
    assertTrue(persisted.contains("query:title"));
    assertFalse(
        channelStateStore
            .channelAutoReattachByServer()
            .getOrDefault("libera", Map.of())
            .containsKey("##politics"));
    assertFalse(
        channelStateStore
            .channelPinnedByServer()
            .getOrDefault("libera", Map.of())
            .containsKey("##politics"));
    assertFalse(
        channelStateStore
            .channelMutedByServer()
            .getOrDefault("libera", Map.of())
            .containsKey("##politics"));
    assertFalse(
        channelStateStore
            .channelActivityRankByServer()
            .getOrDefault("libera", Map.of())
            .containsKey("##politics"));
    assertEquals(
        List.of("#ircafe"),
        channelStateStore.channelCustomOrderByServer().getOrDefault("libera", new ArrayList<>()));
    assertEquals("libera", emittedServerId.get());
  }
}

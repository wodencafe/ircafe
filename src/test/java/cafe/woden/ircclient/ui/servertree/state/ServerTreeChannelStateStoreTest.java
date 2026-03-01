package cafe.woden.ircclient.ui.servertree.state;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerTreeChannelStateStoreTest {

  @Test
  void clearServerRemovesOnlyMatchingServerState() {
    ServerTreeChannelStateStore store = new ServerTreeChannelStateStore();
    store.channelSortModeByServer().put("libera", ServerTreeDockable.ChannelSortMode.CUSTOM);
    store.channelSortModeByServer().put("oftc", ServerTreeDockable.ChannelSortMode.ALPHABETICAL);
    store.channelCustomOrderByServer().put("libera", new ArrayList<>(List.of("#a")));
    store.channelCustomOrderByServer().put("oftc", new ArrayList<>(List.of("#b")));
    store.channelAutoReattachByServer().put("libera", new HashMap<>(java.util.Map.of("#a", true)));
    store.channelAutoReattachByServer().put("oftc", new HashMap<>(java.util.Map.of("#b", false)));
    store.channelActivityRankByServer().put("libera", new HashMap<>(java.util.Map.of("#a", 1L)));
    store.channelActivityRankByServer().put("oftc", new HashMap<>(java.util.Map.of("#b", 2L)));
    store.channelPinnedByServer().put("libera", new HashMap<>(java.util.Map.of("#a", true)));
    store.channelPinnedByServer().put("oftc", new HashMap<>(java.util.Map.of("#b", true)));

    store.clearServer("libera");

    assertFalse(store.channelSortModeByServer().containsKey("libera"));
    assertFalse(store.channelCustomOrderByServer().containsKey("libera"));
    assertFalse(store.channelAutoReattachByServer().containsKey("libera"));
    assertFalse(store.channelActivityRankByServer().containsKey("libera"));
    assertFalse(store.channelPinnedByServer().containsKey("libera"));

    assertTrue(store.channelSortModeByServer().containsKey("oftc"));
    assertTrue(store.channelCustomOrderByServer().containsKey("oftc"));
    assertTrue(store.channelAutoReattachByServer().containsKey("oftc"));
    assertTrue(store.channelActivityRankByServer().containsKey("oftc"));
    assertTrue(store.channelPinnedByServer().containsKey("oftc"));
  }

  @Test
  void clearServerIgnoresBlankServerId() {
    ServerTreeChannelStateStore store = new ServerTreeChannelStateStore();
    store.channelSortModeByServer().put("libera", ServerTreeDockable.ChannelSortMode.CUSTOM);

    store.clearServer("  ");

    assertTrue(store.channelSortModeByServer().containsKey("libera"));
  }
}

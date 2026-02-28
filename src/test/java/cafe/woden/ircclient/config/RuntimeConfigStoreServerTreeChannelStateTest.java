package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreServerTreeChannelStateTest {

  @TempDir Path tempDir;

  @Test
  void channelStateReadsSortModeOrderAndAutoReattachFlags() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        irc:
          servers:
            - id: libera
              autoJoin:
                - "#alpha"
        ircafe:
          ui:
            serverTree:
              channelsByServer:
                libera:
                  sortMode: alphabetical
                  customOrder:
                    - "#beta"
                    - "#alpha"
                  channels:
                    - name: "#alpha"
                      autoReattach: true
                    - name: "#beta"
                      autoReattach: false
        """);

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    assertEquals(
        RuntimeConfigStore.ServerTreeChannelSortMode.ALPHABETICAL,
        store.readServerTreeChannelSortMode(
            "libera", RuntimeConfigStore.ServerTreeChannelSortMode.CUSTOM));
    assertEquals(List.of("#beta", "#alpha"), store.readServerTreeChannelCustomOrder("libera"));
    assertEquals(List.of("#alpha", "#beta"), store.readKnownChannels("libera"));
    assertTrue(store.readServerTreeChannelAutoReattach("libera", "#alpha", false));
    assertFalse(store.readServerTreeChannelAutoReattach("libera", "#beta", true));
  }

  @Test
  void togglingAutoReattachUpdatesAutoJoinButKeepsKnownChannel() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        irc:
          servers:
            - id: libera
              autoJoin:
                - "#alpha"
        """);

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberServerTreeChannelAutoReattach("libera", "#alpha", false);

    assertEquals(List.of(), store.readJoinedChannels("libera"));
    assertEquals(List.of("#alpha"), store.readKnownChannels("libera"));
    assertFalse(store.readServerTreeChannelAutoReattach("libera", "#alpha", true));

    store.rememberServerTreeChannelAutoReattach("libera", "#alpha", true);

    assertEquals(List.of("#alpha"), store.readJoinedChannels("libera"));
    assertEquals(List.of("#alpha"), store.readKnownChannels("libera"));
    assertTrue(store.readServerTreeChannelAutoReattach("libera", "#alpha", false));
  }

  @Test
  void channelStateReadsMostRecentActivitySortModeToken() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        irc:
          servers:
            - id: libera
        ircafe:
          ui:
            serverTree:
              channelsByServer:
                libera:
                  sortMode: most-recent-activity
                  channels:
                    - name: "#alpha"
                      autoReattach: true
        """);

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    assertEquals(
        RuntimeConfigStore.ServerTreeChannelSortMode.MOST_RECENT_ACTIVITY,
        store.readServerTreeChannelSortMode(
            "libera", RuntimeConfigStore.ServerTreeChannelSortMode.CUSTOM));
  }

  @Test
  void rememberServerTreeChannelKeepsExistingAutoReattachPreference() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        irc:
          servers:
            - id: libera
        ircafe:
          ui:
            serverTree:
              channelsByServer:
                libera:
                  channels:
                    - name: "#beta"
                      autoReattach: false
        """);

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberServerTreeChannel("libera", "#beta");

    assertFalse(store.readServerTreeChannelAutoReattach("libera", "#beta", true));
    assertEquals(List.of(), store.readJoinedChannels("libera"));
    assertEquals(List.of("#beta"), store.readKnownChannels("libera"));
  }

  @Test
  void channelSortStateIsStoredPerServer() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        irc:
          servers:
            - id: libera
            - id: oftc
        """);

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberServerTreeChannel("libera", "#beta");
    store.rememberServerTreeChannel("libera", "#alpha");
    store.rememberServerTreeChannelSortMode(
        "libera", RuntimeConfigStore.ServerTreeChannelSortMode.CUSTOM);
    store.rememberServerTreeChannelCustomOrder("libera", List.of("#beta", "#alpha"));

    store.rememberServerTreeChannel("oftc", "#beta");
    store.rememberServerTreeChannel("oftc", "#alpha");
    store.rememberServerTreeChannelSortMode(
        "oftc", RuntimeConfigStore.ServerTreeChannelSortMode.MOST_RECENT_ACTIVITY);

    assertEquals(
        RuntimeConfigStore.ServerTreeChannelSortMode.CUSTOM,
        store.readServerTreeChannelSortMode(
            "libera", RuntimeConfigStore.ServerTreeChannelSortMode.ALPHABETICAL));
    assertEquals(
        RuntimeConfigStore.ServerTreeChannelSortMode.MOST_RECENT_ACTIVITY,
        store.readServerTreeChannelSortMode(
            "oftc", RuntimeConfigStore.ServerTreeChannelSortMode.CUSTOM));
    assertEquals(List.of("#beta", "#alpha"), store.readServerTreeChannelCustomOrder("libera"));
    assertEquals(List.of("#beta", "#alpha"), store.readServerTreeChannelCustomOrder("oftc"));
  }
}

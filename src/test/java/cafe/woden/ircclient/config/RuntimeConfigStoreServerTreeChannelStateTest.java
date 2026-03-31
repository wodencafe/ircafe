package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelSortMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreServerTreeChannelStateTest {

  @TempDir Path tempDir;

  @Test
  void forgettingLastJoinedChannelKeepsExplicitEmptyAutoJoinOverrideAcrossRestart()
      throws Exception {
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
        new RuntimeConfigStore(
            cfg.toString(), new IrcProperties(null, List.of(server("libera", List.of("#alpha")))));

    store.forgetJoinedChannel("libera", "#alpha");

    assertEquals(List.of(), store.readJoinedChannels("libera"));
    assertEquals(java.util.Map.of("libera", List.of()), store.readExplicitServerAutoJoinById());

    ServerRegistry registry =
        new ServerRegistry(
            new IrcProperties(null, List.of(server("libera", List.of("#alpha")))), store);

    assertEquals(List.of(), registry.require("libera").autoJoin());
  }

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
                      pinned: true
                    - name: "#beta"
                      autoReattach: false
        """);

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    assertEquals(
        ServerTreeChannelSortMode.ALPHABETICAL,
        store.readServerTreeChannelSortMode("libera", ServerTreeChannelSortMode.CUSTOM));
    assertEquals(List.of("#beta", "#alpha"), store.readServerTreeChannelCustomOrder("libera"));
    assertEquals(List.of("#alpha", "#beta"), store.readKnownChannels("libera"));
    assertTrue(store.readServerTreeChannelAutoReattach("libera", "#alpha", false));
    assertFalse(store.readServerTreeChannelAutoReattach("libera", "#beta", true));
    assertTrue(store.readServerTreeChannelPinned("libera", "#alpha", false));
    assertFalse(store.readServerTreeChannelPinned("libera", "#beta", true));
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
        ServerTreeChannelSortMode.MOST_RECENT_ACTIVITY,
        store.readServerTreeChannelSortMode("libera", ServerTreeChannelSortMode.CUSTOM));
  }

  @Test
  void channelStateReadsMostUnreadSortModeTokens() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        irc:
          servers:
            - id: libera
            - id: oftc
        ircafe:
          ui:
            serverTree:
              channelsByServer:
                libera:
                  sortMode: most-unread-messages
                  channels:
                    - name: "#alpha"
                      autoReattach: true
                oftc:
                  sortMode: most-unread-notifications
                  channels:
                    - name: "#beta"
                      autoReattach: true
        """);

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    assertEquals(
        ServerTreeChannelSortMode.MOST_UNREAD_MESSAGES,
        store.readServerTreeChannelSortMode("libera", ServerTreeChannelSortMode.CUSTOM));
    assertEquals(
        ServerTreeChannelSortMode.MOST_UNREAD_NOTIFICATIONS,
        store.readServerTreeChannelSortMode("oftc", ServerTreeChannelSortMode.CUSTOM));
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
    store.rememberServerTreeChannelSortMode("libera", ServerTreeChannelSortMode.CUSTOM);
    store.rememberServerTreeChannelCustomOrder("libera", List.of("#beta", "#alpha"));

    store.rememberServerTreeChannel("oftc", "#beta");
    store.rememberServerTreeChannel("oftc", "#alpha");
    store.rememberServerTreeChannelSortMode("oftc", ServerTreeChannelSortMode.MOST_RECENT_ACTIVITY);

    assertEquals(
        ServerTreeChannelSortMode.CUSTOM,
        store.readServerTreeChannelSortMode("libera", ServerTreeChannelSortMode.ALPHABETICAL));
    assertEquals(
        ServerTreeChannelSortMode.MOST_RECENT_ACTIVITY,
        store.readServerTreeChannelSortMode("oftc", ServerTreeChannelSortMode.CUSTOM));
    assertEquals(List.of("#beta", "#alpha"), store.readServerTreeChannelCustomOrder("libera"));
    assertEquals(List.of("#beta", "#alpha"), store.readServerTreeChannelCustomOrder("oftc"));
  }

  @Test
  void rememberChannelPinnedPersistsAndCanToggleOff() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        irc:
          servers:
            - id: libera
        """);

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberServerTreeChannel("libera", "#alpha");
    assertFalse(store.readServerTreeChannelPinned("libera", "#alpha", true));

    store.rememberServerTreeChannelPinned("libera", "#alpha", true);
    assertTrue(store.readServerTreeChannelPinned("libera", "#alpha", false));

    store.rememberServerTreeChannelPinned("libera", "#alpha", false);
    assertFalse(store.readServerTreeChannelPinned("libera", "#alpha", true));
  }

  private static IrcProperties.Server server(String id, List<String> autoJoin) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        autoJoin,
        List.of(),
        null);
  }
}

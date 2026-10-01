package cafe.woden.ircclient.config.runtime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelSortMode;
import cafe.woden.ircclient.config.yaml.RuntimeConfigDocumentStore;
import cafe.woden.ircclient.config.yaml.RuntimeConfigServerYamlSection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

class RuntimeConfigServerTreeChannelStateStoreTest {

  @TempDir Path tempDir;

  @Test
  void reconnectBurstReusesSnapshotWithoutCopyingOrWritingTheDocument() throws Exception {
    List<String> channels = IntStream.range(0, 500).mapToObj(i -> "#channel" + i).toList();
    Path file = canonicalConfig(channels);
    try (RuntimeConfigDocumentStore document = spy(new RuntimeConfigDocumentStore(file))) {
      document.startAsyncPersistence();
      var store = new RuntimeConfigServerTreeChannelStateStore(file, document);
      var initial = store.readServerTreeChannelState("test");
      long revision = document.cachedReadRevision();
      clearInvocations(document);

      for (String channel : channels) {
        store.rememberJoinedChannel("test", channel);
        store.rememberServerTreeChannelAutoReattach("test", channel, true);
        assertTrue(store.readServerTreeChannelAutoReattach("test", channel, false));
        assertFalse(store.readServerTreeChannelPinned("test", channel, true));
        assertFalse(store.readServerTreeChannelMuted("test", channel, true));
        assertEquals(channels, store.readJoinedChannels("test"));
      }
      store.rememberServerTreeChannelSortMode("test", ServerTreeChannelSortMode.CUSTOM);
      store.rememberServerTreeChannelCustomOrder("test", channels);
      store.rememberServerTreeChannelPinned("test", channels.getFirst(), false);
      store.rememberServerTreeChannelMuted("test", channels.getFirst(), false);

      assertSame(initial, store.readServerTreeChannelState("test"));
      assertEquals(revision, document.cachedReadRevision());
      // Every document interaction must be a cheap version check; load/copy and write are absent.
      verify(document, atLeastOnce()).cachedReadRevision();
      verifyNoMoreInteractions(document);
      assertThrows(UnsupportedOperationException.class, () -> initial.channels().clear());
      assertThrows(UnsupportedOperationException.class, () -> initial.customOrder().clear());
    }
  }

  @Test
  void preferenceChangesInvalidateSnapshotAndSurviveRestart() throws Exception {
    Path file = canonicalConfig(List.of("#alpha", "#beta"));
    try (RuntimeConfigDocumentStore document = new RuntimeConfigDocumentStore(file)) {
      document.startAsyncPersistence();
      var store = new RuntimeConfigServerTreeChannelStateStore(file, document);
      var initial = store.readServerTreeChannelState("test");
      store.rememberServerTreeChannelPinned("test", "#alpha", true);
      store.rememberServerTreeChannelMuted("test", "#alpha", true);
      store.rememberServerTreeChannelAutoReattach("test", "#alpha", false);
      store.rememberServerTreeChannelCustomOrder("test", List.of("#beta", "#alpha"));
      assertNotSame(initial, store.readServerTreeChannelState("test"));
      assertEquals(List.of("#beta", "#alpha"), store.readServerTreeChannelCustomOrder("test"));
      assertEquals(List.of("#beta"), store.readJoinedChannels("test"));
      assertTrue(store.readServerTreeChannelPinned("test", "#alpha", false));
      assertTrue(store.readServerTreeChannelMuted("test", "#alpha", false));
    }
    try (RuntimeConfigDocumentStore document = new RuntimeConfigDocumentStore(file)) {
      var restored = new RuntimeConfigServerTreeChannelStateStore(file, document);
      assertEquals(List.of("#beta"), restored.readJoinedChannels("test"));
      assertEquals(List.of("#beta", "#alpha"), restored.readServerTreeChannelCustomOrder("test"));
      assertTrue(restored.readServerTreeChannelPinned("test", "#alpha", false));
      assertTrue(restored.readServerTreeChannelMuted("test", "#alpha", false));
      restored.forgetJoinedChannel("test", "#beta");
      assertEquals(List.of(), restored.readJoinedChannels("test"));
    }
  }

  @Test
  void legacyOnlyStateStillMigratesOnAnUnchangedPreference() throws Exception {
    Path file = tempDir.resolve("legacy.yml");
    Files.writeString(
        file, "irc:\n  servers:\n    - id: test\n      autoJoin: ['#alpha', 'query:Alice']\n");
    try (RuntimeConfigDocumentStore document = new RuntimeConfigDocumentStore(file)) {
      document.startAsyncPersistence();
      var store = new RuntimeConfigServerTreeChannelStateStore(file, document);
      assertEquals(List.of("#alpha"), store.readJoinedChannels("test"));
      store.rememberServerTreeChannelAutoReattach("test", "#alpha", true);
      assertTrue(document.cachedReadRevision() > 0);
      document.flushPendingWrites();
      Map<?, ?> saved = new Yaml().load(Files.readString(file));
      assertTrue(saved.containsKey("ircafe"));
      assertTrue(Files.readString(file).contains("query:Alice"));
    }
  }

  @Test
  void changesFromOtherStoresAndInsideMutationBatchesInvalidateReads() throws Exception {
    Path file = canonicalConfig(List.of("#alpha"));
    try (RuntimeConfigDocumentStore document = new RuntimeConfigDocumentStore(file)) {
      document.startAsyncPersistence();
      var store = new RuntimeConfigServerTreeChannelStateStore(file, document);
      var servers =
          new RuntimeConfigServerYamlSection(
              file, document, LoggerFactory.getLogger(getClass()), "test");
      var initial = store.readServerTreeChannelState("test");
      servers.mutateExistingServer(
          "test", server -> server.put("autoJoin", List.of("#alpha", "#beta")));
      assertEquals(List.of("#alpha", "#beta"), store.readJoinedChannels("test"));
      assertNotSame(initial, store.readServerTreeChannelState("test"));
      document.runMutationBatch(
          () -> {
            assertEquals(-1L, document.cachedReadRevision());
            store.rememberServerTreeChannelAutoReattach("test", "#beta", false);
            assertEquals(List.of("#alpha"), store.readJoinedChannels("test"));
            store.rememberJoinedChannel("test", "#gamma");
            assertEquals(List.of("#alpha", "#gamma"), store.readJoinedChannels("test"));
          });
      assertEquals(List.of("#alpha", "#gamma"), store.readJoinedChannels("test"));
      assertFalse(store.readServerTreeChannelAutoReattach("test", "#beta", true));
    }
  }

  @Test
  void unchangedPreferenceRepairsLegacyAutoJoinAndKeepsPrivateMessageTargets() throws Exception {
    Path file = canonicalConfig(List.of("#alpha"));
    try (RuntimeConfigDocumentStore document = new RuntimeConfigDocumentStore(file)) {
      document.startAsyncPersistence();
      var store = new RuntimeConfigServerTreeChannelStateStore(file, document);
      var servers =
          new RuntimeConfigServerYamlSection(
              file, document, LoggerFactory.getLogger(getClass()), "test");
      servers.mutateExistingServer(
          "test", server -> server.put("autoJoin", List.of("query:Alice")));
      store.rememberServerTreeChannelAutoReattach("test", "#alpha", true);
      assertEquals(List.of("#alpha"), store.readJoinedChannels("test"));
      document.flushPendingWrites();
      assertEquals(
          List.of("#alpha", "query:Alice"),
          servers.readExistingServer("test").orElseThrow().get("autoJoin"));
    }
  }

  @Test
  void changingChannelSpellingKeepsOtherPreferencesAndRefreshesTheSnapshot() throws Exception {
    Path file = canonicalConfig(List.of("#alpha"));
    try (RuntimeConfigDocumentStore document = new RuntimeConfigDocumentStore(file)) {
      document.startAsyncPersistence();
      var store = new RuntimeConfigServerTreeChannelStateStore(file, document);
      store.rememberServerTreeChannelPinned("test", "#alpha", true);
      store.rememberServerTreeChannelMuted("test", "#alpha", true);
      store.rememberServerTreeChannelAutoReattach("test", "#ALPHA", true);
      assertEquals(List.of("#ALPHA"), store.readJoinedChannels("test"));
      assertEquals(List.of("#ALPHA"), store.readServerTreeChannelCustomOrder("test"));
      assertTrue(store.readServerTreeChannelPinned("test", "#alpha", false));
      assertTrue(store.readServerTreeChannelMuted("test", "#alpha", false));
    }
  }

  @Test
  void readingAnotherServerEvictsThePreviousSnapshot() throws Exception {
    Path file = canonicalConfig(List.of("#alpha"));
    try (RuntimeConfigDocumentStore document = new RuntimeConfigDocumentStore(file)) {
      document.startAsyncPersistence();
      var store = new RuntimeConfigServerTreeChannelStateStore(file, document);
      var initial = store.readServerTreeChannelState("test");
      assertEquals(List.of(), store.readServerTreeChannelState("other").channels());
      var reloaded = store.readServerTreeChannelState("test");
      assertEquals(initial, reloaded);
      assertNotSame(initial, reloaded);
    }
  }

  @Test
  void diskBackedReadsContinueToObserveExternalEdits() throws Exception {
    Path file = canonicalConfig(List.of("#alpha"));
    try (RuntimeConfigDocumentStore document = new RuntimeConfigDocumentStore(file)) {
      var store = new RuntimeConfigServerTreeChannelStateStore(file, document);
      assertEquals(List.of("#alpha"), store.readJoinedChannels("test"));
      Files.writeString(file, "irc:\n  servers:\n    - id: test\n      autoJoin: ['#beta']\n");
      assertEquals(List.of("#beta"), store.readJoinedChannels("test"));
      assertEquals(List.of("#beta"), store.readKnownChannels("test"));
    }
  }

  private Path canonicalConfig(List<String> channels) throws Exception {
    Path file = tempDir.resolve("ircafe.yml");
    List<Map<String, Object>> preferences = new ArrayList<>();
    for (String channel : channels) {
      preferences.add(Map.of("name", channel, "autoReattach", true));
    }
    Map<String, Object> doc =
        Map.of(
            "irc", Map.of("servers", List.of(Map.of("id", "test", "autoJoin", channels))),
            "ircafe",
                Map.of(
                    "ui",
                    Map.of(
                        "serverTree",
                        Map.of(
                            "channelsByServer",
                            Map.of(
                                "test",
                                Map.of("customOrder", channels, "channels", preferences))))));
    Files.writeString(file, new Yaml().dump(doc));
    return file;
  }
}

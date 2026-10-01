package cafe.woden.ircclient.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class RuntimeConfigDocumentStoreTest {

  @TempDir Path tempDir;

  @Test
  void remembersWhetherConfigFileExistedAtStartup() throws Exception {
    Path existing = tempDir.resolve("existing.yml");
    Files.writeString(existing, "ircafe: {}\n");

    RuntimeConfigDocumentStore existingStore = new RuntimeConfigDocumentStore(existing);
    RuntimeConfigDocumentStore missingStore =
        new RuntimeConfigDocumentStore(tempDir.resolve("missing.yml"));

    assertTrue(existingStore.fileExistedOnStartup());
    assertFalse(missingStore.fileExistedOnStartup());
  }

  @Test
  void writesDocumentAndCreatesParentDirectories() throws Exception {
    Path config = tempDir.resolve("nested/runtime/ircafe.yml");
    RuntimeConfigDocumentStore store = new RuntimeConfigDocumentStore(config);
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("ircafe", Map.of("theme", "dark"));

    store.write(doc);

    assertTrue(Files.exists(config));
    assertEquals(Map.of("theme", "dark"), store.load().get("ircafe"));
  }

  @Test
  void mutationBatchDefersDiskWriteUntilBatchEnds() throws Exception {
    Path config = tempDir.resolve("batched.yml");
    RuntimeConfigDocumentStore store = new RuntimeConfigDocumentStore(config);
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("value", "before");

    store.beginMutationBatch();
    store.write(doc);
    assertFalse(Files.exists(config));

    store.load().put("value", "after");
    store.write(store.load());
    store.endMutationBatch();

    assertTrue(Files.exists(config));
    assertEquals("after", store.load().get("value"));
  }

  @Test
  void asyncReadsAndWritesOwnSnapshotsAndPreserveYamlAliases() throws Exception {
    Path config = tempDir.resolve("snapshots.yml");
    Files.writeString(config, "channels: &channels [alpha]\nlegacy: *channels\nfuture: keep\n");
    try (RuntimeConfigDocumentStore store = new RuntimeConfigDocumentStore(config)) {
      store.startAsyncPersistence();
      Map<String, Object> doc = store.load();
      assertSame(doc.get("channels"), doc.get("legacy"));
      @SuppressWarnings("unchecked")
      List<String> channels = (List<String>) doc.get("channels");
      channels.add("beta");
      store.write(doc);
      channels.add("unsaved");
      assertEquals(List.of("alpha", "beta"), store.load().get("channels"));
      store.load().put("future", "unsaved");
      assertEquals("keep", store.load().get("future"));
      store.flushPendingWrites();
      Map<?, ?> saved = new Yaml().load(Files.readString(config));
      assertEquals(List.of("alpha", "beta"), saved.get("channels"));
      assertSame(saved.get("channels"), saved.get("legacy"));
    }
  }

  @Test
  void asyncNestedBatchMakesFinalStateVisibleAndCloseFlushesIt() throws Exception {
    Path config = tempDir.resolve("async-batch.yml");
    RuntimeConfigDocumentStore store = new RuntimeConfigDocumentStore(config);
    store.startAsyncPersistence();
    store.runMutationBatch(
        () -> {
          try {
            store.write(new LinkedHashMap<>(Map.of("value", "before")));
            store.runMutationBatch(
                () -> {
                  try {
                    Map<String, Object> doc = store.load();
                    doc.put("value", "after");
                    store.write(doc);
                    assertFalse(Files.exists(config));
                  } catch (IOException e) {
                    throw new AssertionError(e);
                  }
                });
            assertEquals("after", store.load().get("value"));
            assertFalse(Files.exists(config));
          } catch (IOException e) {
            throw new AssertionError(e);
          }
        });
    assertEquals("after", store.load().get("value"));
    store.close();
    assertEquals("after", ((Map<?, ?>) new Yaml().load(Files.readString(config))).get("value"));
    store.close();
    assertThrows(IOException.class, () -> store.write(Map.of("value", "late")));
  }

  @Test
  void failedBackgroundSaveKeepsLastFileAndLatestStateCanBeRetried() throws Exception {
    Path config = tempDir.resolve("failure.yml");
    Files.writeString(config, "value: original\n");
    AtomicBoolean fail = new AtomicBoolean(true);
    try (RuntimeConfigDocumentStore store =
        new RuntimeConfigDocumentStore(config) {
          @Override
          void writeNow(Map<String, Object> doc) throws IOException {
            if (fail.get()) throw new IOException("simulated save failure");
            super.writeNow(doc);
          }
        }) {
      store.startAsyncPersistence();
      store.write(Map.of("value", "pending"));
      assertThrows(IOException.class, store::flushPendingWrites);
      assertEquals("value: original\n", Files.readString(config));
      assertEquals("pending", store.load().get("value"));
      fail.set(false);
      store.write(Map.of("value", "latest", "other", "preserved"));
      store.flushPendingWrites();
      Map<?, ?> saved = new Yaml().load(Files.readString(config));
      assertEquals("latest", saved.get("value"));
      assertEquals("preserved", saved.get("other"));
    }
  }

  @Test
  void failedSerializationDoesNotTruncateExistingConfig() throws Exception {
    Path config = tempDir.resolve("atomic.yml");
    Files.writeString(config, "value: original\n");
    RuntimeConfigDocumentStore store = new RuntimeConfigDocumentStore(config);
    assertThrows(Exception.class, () -> store.write(Map.of("bad", new BrokenValue())));
    assertEquals("value: original\n", Files.readString(config));
    try (var files = Files.list(tempDir)) {
      assertEquals(List.of(config), files.toList());
    }
  }

  @Test
  void shutdownStillSavesNewestSnapshotWhenAnOlderInFlightSaveFails() throws Exception {
    Path config = tempDir.resolve("superseded-failure.yml");
    CountDownLatch saving = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    RuntimeConfigDocumentStore store =
        new RuntimeConfigDocumentStore(config) {
          @Override
          void writeNow(Map<String, Object> doc) throws IOException {
            if ("old".equals(doc.get("value"))) {
              saving.countDown();
              try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("writer timed out");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
              }
              throw new IOException("superseded save failed");
            }
            super.writeNow(doc);
          }
        };
    try {
      store.startAsyncPersistence();
      store.write(Map.of("value", "old"));
      assertTrue(saving.await(2, TimeUnit.SECONDS));
      store.write(Map.of("value", "latest"));
      release.countDown();
      store.close();
      assertEquals("latest", ((Map<?, ?>) new Yaml().load(Files.readString(config))).get("value"));
    } finally {
      release.countDown();
      store.close();
    }
  }

  public static final class BrokenValue {
    public String getValue() {
      throw new IllegalStateException("simulated serialization failure");
    }

    public void setValue(String value) {}
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void atomicSavePreservesConfigSymlinkAndExistingPermissions() throws Exception {
    Path target = tempDir.resolve("profile.yml");
    Path config = tempDir.resolve("ircafe.yml");
    Files.writeString(target, "value: original\n");
    var permissions = PosixFilePermissions.fromString("rw-r-----");
    Files.setPosixFilePermissions(target, permissions);
    Files.createSymbolicLink(config, target.getFileName());
    try (RuntimeConfigDocumentStore store = new RuntimeConfigDocumentStore(config)) {
      store.startAsyncPersistence();
      store.write(Map.of("value", "latest"));
      store.flushPendingWrites();
      assertTrue(Files.isSymbolicLink(config));
      assertEquals(permissions, Files.getPosixFilePermissions(target));
      assertEquals("latest", ((Map<?, ?>) new Yaml().load(Files.readString(target))).get("value"));
    }
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void initialSaveFollowsDanglingConfigSymlink() throws Exception {
    Path target = tempDir.resolve("profile/ircafe.yml");
    Path config = tempDir.resolve("ircafe.yml");
    Files.createSymbolicLink(config, tempDir.relativize(target));
    try (RuntimeConfigDocumentStore store = new RuntimeConfigDocumentStore(config)) {
      assertFalse(store.fileExistedOnStartup());
      store.write(Map.of("value", "seeded"));
      assertTrue(Files.isSymbolicLink(config));
      assertEquals("seeded", store.load().get("value"));
      assertTrue(Files.exists(target));
    }
  }
}

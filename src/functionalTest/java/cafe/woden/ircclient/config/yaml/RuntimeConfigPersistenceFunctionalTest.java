package cafe.woden.ircclient.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.server.RuntimeConfigServerTreeChannelStateStore;
import java.awt.EventQueue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigPersistenceFunctionalTest {
  @TempDir Path tempDir;

  @Test
  void joinsAndReadsRemainResponsiveWhileAnEarlierSaveIsBlocked() throws Exception {
    Path config = tempDir.resolve("ircafe.yml");
    Files.writeString(
        config, "irc:\n  servers:\n    - id: libera\n      autoJoin: []\nfuture: keep\n");
    CountDownLatch saving = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger writes = new AtomicInteger();
    RuntimeConfigDocumentStore documents =
        new RuntimeConfigDocumentStore(config) {
          @Override
          void writeNow(Map<String, Object> doc) throws IOException {
            assertFalse(EventQueue.isDispatchThread(), "YAML serialization must run off the EDT");
            if (writes.incrementAndGet() == 1) {
              saving.countDown();
              try {
                if (!release.await(10, TimeUnit.SECONDS))
                  throw new IOException("test writer timed out");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
              }
            }
            super.writeNow(doc);
          }
        };
    try {
      documents.startAsyncPersistence();
      RuntimeConfigServerTreeChannelStateStore channels =
          new RuntimeConfigServerTreeChannelStateStore(config, documents);
      SwingUtilities.invokeAndWait(() -> channels.rememberJoinedChannel("libera", "#first"));
      assertTrue(saving.await(5, TimeUnit.SECONDS));
      CompletableFuture<Void> responsive = new CompletableFuture<>();
      SwingUtilities.invokeLater(
          () -> {
            try {
              for (int i = 0; i < 100; i++)
                channels.rememberJoinedChannel("libera", "#channel" + i);
              channels.forgetJoinedChannel("libera", "#first");
              channels.rememberServerTreeChannelAutoReattach("libera", "#channel0", false);
              assertEquals(100, channels.readKnownChannels("libera").size());
              assertEquals(99, channels.readJoinedChannels("libera").size());
              assertFalse(channels.readJoinedChannels("libera").contains("#first"));
              assertThrows(IllegalStateException.class, documents::flushPendingWrites);
              responsive.complete(null);
            } catch (Throwable e) {
              responsive.completeExceptionally(e);
            }
          });
      // The writer is still deliberately blocked. Completion proves reads/mutations do not wait
      // for its config lock, disk access, or YAML serialization.
      responsive.get(3, TimeUnit.SECONDS);
      assertEquals(1, writes.get());
      release.countDown();
      documents.flushPendingWrites();
      assertEquals(2, writes.get(), "the join burst should retain one latest pending snapshot");
      try (RuntimeConfigDocumentStore reopened = new RuntimeConfigDocumentStore(config)) {
        RuntimeConfigServerTreeChannelStateStore restored =
            new RuntimeConfigServerTreeChannelStateStore(config, reopened);
        assertEquals(channels.readJoinedChannels("libera"), restored.readJoinedChannels("libera"));
        assertEquals(
            channels.readServerTreeChannelState("libera"),
            restored.readServerTreeChannelState("libera"));
        assertEquals("keep", reopened.load().get("future"));
      }
      int beforeRepeatedJoin = writes.get();
      SwingUtilities.invokeAndWait(
          () -> {
            for (int i = 1; i < 100; i++) channels.rememberJoinedChannel("libera", "#channel" + i);
          });
      documents.flushPendingWrites();
      assertEquals(beforeRepeatedJoin, writes.get(), "unchanged joins should not save again");
    } finally {
      release.countDown();
      documents.close();
    }
  }
}

package cafe.woden.ircclient.config.yaml;

import cafe.woden.ircclient.util.VirtualThreads;
import java.awt.EventQueue;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Owns low-level runtime config document IO and write batching.
 *
 * <p>{@code RuntimeConfigStore} still owns the domain-specific document shape; this class only
 * knows how to load, write, and coalesce mutations for the YAML document backing the store.
 */
public class RuntimeConfigDocumentStore implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(RuntimeConfigDocumentStore.class);

  private final Path file;
  private final Yaml yaml;
  private final boolean fileExistedOnStartup;

  private int mutationBatchDepth = 0;
  private Map<String, Object> mutationBatchDoc = null;
  private boolean mutationBatchDirty = false;
  private Map<String, Object> cachedDoc;
  private boolean documentPresent;
  private Map<String, Object> pendingDoc;
  private ScheduledExecutorService writer;
  private ScheduledFuture<?> scheduledWrite;
  private long scheduledWriteToken;
  private boolean writing;
  private boolean closed;
  private long revision;
  private long persistedRevision;
  private IOException writeFailure;

  public RuntimeConfigDocumentStore(Path file) {
    this.file = file;
    this.fileExistedOnStartup = existsSafely(file);
    this.yaml = new Yaml(dumperOptions());
  }

  public boolean fileExistedOnStartup() {
    return fileExistedOnStartup;
  }

  /**
   * Version for derived read caches, or {@code -1} when reads cannot safely be cached. Mutation
   * batches expose a mutable document, and disk-backed reads must continue to observe external
   * edits.
   */
  public synchronized long cachedReadRevision() {
    return cachedDoc != null && mutationBatchDepth == 0 && !closed ? revision : -1L;
  }

  /** Loads the document before UI startup and enables bounded, coalesced background saves. */
  public synchronized void startAsyncPersistence() throws IOException {
    if (closed) throw new IOException("Runtime config store is closed");
    if (writer != null) return;
    if (EventQueue.isDispatchThread()) {
      throw new IllegalStateException("Runtime config persistence must start off the EDT");
    }
    documentPresent = existsSafely(file);
    cachedDoc = file.toString().isBlank() ? new LinkedHashMap<>() : copyDocument(loadOrEmpty());
    // Owned by this store, rather than the global executor teardown, so close can flush first.
    writer = VirtualThreads.newUntrackedSingleThreadScheduledExecutor("ircafe-config-writer");
  }

  public synchronized void runMutationBatch(Runnable action) {
    if (action == null) return;
    beginMutationBatch();
    try {
      action.run();
    } finally {
      endMutationBatch();
    }
  }

  public synchronized void beginMutationBatch() {
    if (mutationBatchDepth == 0) {
      try {
        mutationBatchDoc = loadOrEmpty();
      } catch (Exception e) {
        mutationBatchDoc = new LinkedHashMap<>();
        log.warn("[ircafe] Could not start mutation batch for '{}'", file, e);
      }
      mutationBatchDirty = false;
    }
    mutationBatchDepth++;
  }

  public synchronized void endMutationBatch() {
    if (mutationBatchDepth <= 0) return;
    mutationBatchDepth--;
    if (mutationBatchDepth > 0) return;
    try {
      if (mutationBatchDirty && mutationBatchDoc != null) {
        write(mutationBatchDoc);
      }
    } catch (Exception e) {
      log.warn("[ircafe] Could not flush mutation batch to '{}'", file, e);
    } finally {
      mutationBatchDoc = null;
      mutationBatchDirty = false;
    }
  }

  @SuppressWarnings("unchecked")
  synchronized Map<String, Object> load() throws IOException {
    if (mutationBatchDepth > 0 && mutationBatchDoc != null) {
      return mutationBatchDoc;
    }
    if (cachedDoc != null) return copyDocument(cachedDoc);
    try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      Object o = yaml.load(r);
      if (o instanceof Map<?, ?> m) {
        return (Map<String, Object>) m;
      }
      return new LinkedHashMap<>();
    }
  }

  synchronized Map<String, Object> loadOrEmpty() throws IOException {
    if (cachedDoc != null || mutationBatchDepth > 0) return load();
    return Files.exists(file) ? load() : new LinkedHashMap<>();
  }

  synchronized boolean documentExists() {
    return cachedDoc != null
        ? documentPresent || revision > 0 || mutationBatchDirty
        : Files.exists(file);
  }

  synchronized void write(Map<String, Object> doc) throws IOException {
    if (closed) throw new IOException("Runtime config store is closed");
    if (mutationBatchDepth > 0) {
      mutationBatchDoc = (doc == null) ? new LinkedHashMap<>() : doc;
      mutationBatchDirty = true;
      return;
    }
    if (writer != null) {
      cachedDoc = copyDocument(doc);
      pendingDoc = cachedDoc;
      revision++;
      writeFailure = null;
      scheduleWrite(100);
      return;
    }
    writeNow(doc);
  }

  void writeNow(Map<String, Object> doc) throws IOException {
    Path destination = resolveWriteTarget();
    Path parent = destination.getParent();
    if (parent != null && !Files.exists(parent)) {
      Files.createDirectories(parent);
    }
    PosixFileAttributeView attributes =
        Files.getFileAttributeView(destination, PosixFileAttributeView.class);
    Set<PosixFilePermission> permissions =
        attributes != null && Files.exists(destination)
            ? attributes.readAttributes().permissions()
            : null;
    Path temporary = Files.createTempFile(parent, ".ircafe-config-", ".yml");
    try {
      try (Writer w = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
        new Yaml(dumperOptions()).dump(doc, w);
      }
      if (permissions != null) Files.setPosixFilePermissions(temporary, permissions);
      try {
        Files.move(
            temporary,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private Path resolveWriteTarget() throws IOException {
    Path target = file.toAbsolutePath();
    // Follow file symlinks as the original streaming writer did, including dangling links for
    // newly seeded profiles. Atomic replacement must replace their destination, not the link.
    for (int links = 0; Files.isSymbolicLink(target); links++) {
      if (links >= 40) throw new IOException("Too many runtime config symbolic links");
      Path link = Files.readSymbolicLink(target);
      target = link.isAbsolute() ? link : target.getParent().resolve(link);
    }
    return target;
  }

  private void scheduleWrite(long delayMs) {
    if (pendingDoc == null || writing || scheduledWrite != null || writer.isShutdown()) return;
    long token = ++scheduledWriteToken;
    scheduledWrite =
        writer.schedule(() -> drainPendingWrite(token), delayMs, TimeUnit.MILLISECONDS);
  }

  private void drainPendingWrite(long token) {
    Map<String, Object> snapshot;
    long snapshotRevision;
    synchronized (this) {
      if (token != scheduledWriteToken) return;
      scheduledWrite = null;
      if (pendingDoc == null) return;
      snapshot = pendingDoc;
      snapshotRevision = revision;
      pendingDoc = null;
      writing = true;
    }
    IOException failure = null;
    long started = System.nanoTime();
    try {
      // No config/store monitor is held during serialization or disk IO.
      writeNow(snapshot);
    } catch (Exception e) {
      failure = e instanceof IOException io ? io : new IOException(e);
      log.warn(
          "[ircafe] Could not save runtime config '{}' (revision {})", file, snapshotRevision, e);
    } finally {
      synchronized (this) {
        writing = false;
        if (failure == null) {
          persistedRevision = snapshotRevision;
          writeFailure = null;
        } else {
          // A failed older snapshot must not prevent flushing a newer pending snapshot.
          writeFailure = snapshotRevision == revision ? failure : null;
          if (pendingDoc == null) pendingDoc = snapshot;
        }
        if (writeFailure == null || !closed) scheduleWrite(writeFailure == null ? 100 : 1000);
        notifyAll();
      }
      log.debug(
          "[ircafe] Runtime config save revision={} durationMs={} success={}",
          snapshotRevision,
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
          failure == null);
    }
  }

  /** Waits off the EDT for all changes accepted before this call to reach disk. */
  public synchronized void flushPendingWrites() throws IOException {
    if (EventQueue.isDispatchThread()) {
      throw new IllegalStateException("Runtime config flush must run off the EDT");
    }
    long target = revision;
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (persistedRevision < target) {
      if (writeFailure != null) throw new IOException("Runtime config save failed", writeFailure);
      if (!writing) {
        if (scheduledWrite != null) scheduledWrite.cancel(false);
        scheduledWrite = null;
        scheduleWrite(0);
      }
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) throw new IOException("Timed out flushing runtime config");
      try {
        TimeUnit.NANOSECONDS.timedWait(this, remaining);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted flushing runtime config", e);
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (EventQueue.isDispatchThread()) {
      throw new IllegalStateException("Runtime config shutdown must run off the EDT");
    }
    ScheduledExecutorService executor;
    synchronized (this) {
      if (closed) return;
      closed = true;
      executor = writer;
    }
    if (executor == null) return;
    boolean flushed = false;
    try {
      flushPendingWrites();
      flushed = true;
    } catch (IOException e) {
      log.warn("[ircafe] Could not flush runtime config '{}' during shutdown", file, e);
      throw e;
    } finally {
      synchronized (this) {
        if (scheduledWrite != null) scheduledWrite.cancel(false);
        scheduledWrite = null;
        if (flushed) executor.shutdown();
        else executor.shutdownNow();
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> copyDocument(Map<String, Object> doc) {
    return doc == null
        ? new LinkedHashMap<>()
        : (Map<String, Object>) copyValue(doc, new IdentityHashMap<>());
  }

  private static Object copyValue(Object value, IdentityHashMap<Object, Object> copies) {
    if (copies.containsKey(value)) return copies.get(value);
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> copy = new LinkedHashMap<>();
      copies.put(value, copy);
      map.forEach((key, item) -> copy.put(copyValue(key, copies), copyValue(item, copies)));
      return copy;
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      copies.put(value, copy);
      list.forEach(item -> copy.add(copyValue(item, copies)));
      return copy;
    }
    if (value instanceof Set<?> set) {
      Set<Object> copy = new LinkedHashSet<>();
      copies.put(value, copy);
      set.forEach(item -> copy.add(copyValue(item, copies)));
      return copy;
    }
    if (value instanceof java.util.Date date) return date.clone();
    if (value instanceof byte[] bytes) return bytes.clone();
    return value;
  }

  private static boolean existsSafely(Path file) {
    try {
      return file != null && !file.toString().isBlank() && Files.exists(file);
    } catch (Exception ignored) {
      return false;
    }
  }

  private static DumperOptions dumperOptions() {
    DumperOptions opts = new DumperOptions();
    opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    opts.setPrettyFlow(true);
    opts.setIndent(2);
    // SnakeYAML requires indicatorIndent < indent.
    // With indent=2, indicatorIndent=1 keeps list indicators aligned nicely.
    opts.setIndicatorIndent(1);
    opts.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
    return opts;
  }
}

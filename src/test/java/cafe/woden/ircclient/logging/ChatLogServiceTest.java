package cafe.woden.ircclient.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ChatLogServiceTest {

  @Test
  void closeDrainsQueuedLinesAndIgnoresPostCloseWrites() {
    RecordingRepository repo = new RecordingRepository();
    ChatLogService service = new ChatLogService(repo, txTemplate(), enabledProps());

    LogLine first = line("first");
    LogLine second = line("second");
    LogLine ignoredAfterClose = line("ignored-after-close");

    service.log(first);
    service.log(second);
    service.close();
    service.log(ignoredAfterClose);

    List<LogLine> written = repo.snapshot();
    assertTrue(written.contains(first));
    assertTrue(written.contains(second));
    assertFalse(written.contains(ignoredAfterClose));
  }

  @Test
  void closeWaitsForBlockedFlushToFinish() throws Exception {
    CountDownLatch flushStarted = new CountDownLatch(1);
    CountDownLatch releaseFlush = new CountDownLatch(1);
    BlockingRepository repo = new BlockingRepository(flushStarted, releaseFlush);
    ChatLogService service = new ChatLogService(repo, txTemplate(), enabledProps());

    service.log(line("slow-line"));
    assertTrue(flushStarted.await(3, TimeUnit.SECONDS));

    Thread closer = new Thread(service::close, "chatlog-close-test");
    closer.start();

    try {
      closer.join(2_200);
      assertTrue(closer.isAlive(), "close should keep waiting while writer flush is blocked");
    } finally {
      releaseFlush.countDown();
    }

    closer.join(3_000);
    assertFalse(closer.isAlive(), "close should complete once writer flush unblocks");
  }

  private static LogLine line(String text) {
    return new LogLine(
        "libera",
        "#ircafe",
        System.currentTimeMillis(),
        LogDirection.IN,
        LogKind.CHAT,
        "alice",
        text,
        false,
        false,
        null);
  }

  private static LogProperties enabledProps() {
    return new LogProperties(
        Boolean.TRUE,
        Boolean.TRUE,
        Boolean.TRUE,
        Boolean.TRUE,
        Boolean.TRUE,
        0,
        500,
        64,
        new LogProperties.Hsqldb("ircafe-chatlog-test", Boolean.TRUE));
  }

  private static TransactionTemplate txTemplate() {
    return new TransactionTemplate(new NoOpTransactionManager());
  }

  private static final class RecordingRepository extends ChatLogRepository {

    private final List<LogLine> written = Collections.synchronizedList(new ArrayList<>());

    private RecordingRepository() {
      super(null);
    }

    @Override
    public int[] insertBatch(List<LogLine> lines) {
      written.addAll(lines);
      return new int[lines.size()];
    }

    private List<LogLine> snapshot() {
      synchronized (written) {
        return new ArrayList<>(written);
      }
    }
  }

  private static final class BlockingRepository extends ChatLogRepository {

    private final CountDownLatch flushStarted;
    private final CountDownLatch releaseFlush;

    private BlockingRepository(CountDownLatch flushStarted, CountDownLatch releaseFlush) {
      super(null);
      this.flushStarted = flushStarted;
      this.releaseFlush = releaseFlush;
    }

    @Override
    public int[] insertBatch(List<LogLine> lines) {
      flushStarted.countDown();
      long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (System.nanoTime() < deadlineNanos) {
        try {
          if (releaseFlush.await(100, TimeUnit.MILLISECONDS)) {
            return new int[lines.size()];
          }
        } catch (InterruptedException ignored) {
          // Simulate a blocking JDBC call that does not stop immediately on interrupt.
        }
      }
      throw new IllegalStateException("timed out waiting to release blocked flush");
    }
  }

  private static final class NoOpTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      // no-op
    }

    @Override
    public void rollback(TransactionStatus status) {
      // no-op
    }
  }
}

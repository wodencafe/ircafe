package cafe.woden.ircclient.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ChatLogServiceMockVerifyTest {

  @Test
  void closeKeepsWaitingWhenFlushRunsLongerThanLegacyJoinTimeout() throws Exception {
    ChatLogRepository repo = org.mockito.Mockito.mock(ChatLogRepository.class);
    CountDownLatch flushStarted = new CountDownLatch(1);
    CountDownLatch releaseFlush = new CountDownLatch(1);
    when(repo.insertBatch(anyList()))
        .thenAnswer(
            invocation -> {
              flushStarted.countDown();
              long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
              while (System.nanoTime() < deadlineNanos) {
                try {
                  if (releaseFlush.await(100, TimeUnit.MILLISECONDS)) {
                    List<LogLine> batch = invocation.getArgument(0);
                    return new int[batch.size()];
                  }
                } catch (InterruptedException ignored) {
                  // Simulate a blocking JDBC call that does not stop immediately on interrupt.
                }
              }
              throw new IllegalStateException("timed out waiting to release blocked flush");
            });

    ChatLogService service = new ChatLogService(repo, txTemplate(), enabledProps());
    LogLine line = line("blocked-write");
    service.log(line);
    assertTrue(flushStarted.await(3, TimeUnit.SECONDS));

    Thread closer = new Thread(service::close, "chatlog-mock-close-test");
    closer.start();

    try {
      closer.join(2_200);
      assertTrue(closer.isAlive(), "close should still wait while flush is blocked");
    } finally {
      releaseFlush.countDown();
    }

    closer.join(3_000);
    assertFalse(closer.isAlive(), "close should finish after blocked flush is released");

    verify(repo, times(1)).insertBatch(argThat(batch -> batch != null && batch.contains(line)));
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

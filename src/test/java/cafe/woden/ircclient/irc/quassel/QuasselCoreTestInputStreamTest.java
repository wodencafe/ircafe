package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(10)
class QuasselCoreTestInputStreamTest {
  @Test
  void explicitEofDrainsBufferedBytesAndRejectsFurtherWrites() throws Exception {
    try (var input = new QuasselCoreTestInputStream(4)) {
      input.writeFrame(new byte[] {1, 2, (byte) 255});
      input.endInbound();
      input.endInbound();
      assertArrayEquals(new byte[] {1, 2}, input.readNBytes(2));
      assertEquals(255, input.read());
      assertEquals(-1, input.read());
      assertEquals(-1, input.read(new byte[4]));
      assertThrows(IOException.class, () -> input.writeFrame(new byte[] {3}));
    }
  }

  @Test
  void bulkReadReturnsAvailableBytesAndHandlesWrappedBufferAndOffsets() throws Exception {
    try (var input = new QuasselCoreTestInputStream(4)) {
      input.writeFrame(new byte[] {1, 2, 3});
      var bytes = new byte[6];
      assertEquals(2, input.read(bytes, 1, 2));
      assertArrayEquals(new byte[] {0, 1, 2, 0, 0, 0}, bytes);
      input.writeFrame(new byte[] {4, 5});
      assertEquals(3, input.read(bytes, 2, 4));
      assertArrayEquals(new byte[] {0, 1, 3, 4, 5, 0}, bytes);
      assertEquals(0, input.read(bytes, 0, 0));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void closeWakesBlockedSingleByteAndBulkReaders(boolean bulk) throws Exception {
    try (var workers = Executors.newSingleThreadExecutor()) {
      try (var input = new QuasselCoreTestInputStream(4)) {
        var started = new CountDownLatch(1);
        var read =
            workers.submit(
                () -> {
                  started.countDown();
                  return bulk ? input.read(new byte[4]) : input.read();
                });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> read.get(100, TimeUnit.MILLISECONDS));
        input.close();
        input.close();
        var error = assertThrows(ExecutionException.class, () -> read.get(2, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, error.getCause());
        assertThrows(IOException.class, input::read);
        assertThrows(IOException.class, () -> input.writeFrame(new byte[] {1}));
      }
    }
  }

  @Test
  void explicitEofWakesBlockedReader() throws Exception {
    try (var workers = Executors.newSingleThreadExecutor()) {
      try (var input = new QuasselCoreTestInputStream(4)) {
        var started = new CountDownLatch(1);
        var read =
            workers.submit(
                () -> {
                  started.countDown();
                  return input.read();
                });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> read.get(100, TimeUnit.MILLISECONDS));
        input.endInbound();
        assertEquals(-1, read.get(2, TimeUnit.SECONDS));
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void closeAndEofWakeWriterWaitingForBufferSpace(boolean close) throws Exception {
    try (var workers = Executors.newSingleThreadExecutor()) {
      try (var input = new QuasselCoreTestInputStream(4)) {
        input.writeFrame(new byte[] {1, 2, 3, 4});
        var started = new CountDownLatch(1);
        var write =
            workers.submit(
                () -> {
                  started.countDown();
                  input.writeFrame(new byte[] {5});
                  return null;
                });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> write.get(100, TimeUnit.MILLISECONDS));
        if (close) input.close();
        else input.endInbound();
        var error = assertThrows(ExecutionException.class, () -> write.get(2, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, error.getCause());
        if (!close) {
          assertArrayEquals(new byte[] {1, 2, 3, 4}, input.readAllBytes());
        }
      }
    }
  }

  @Test
  void largeFramesApplyBackpressureAndConcurrentFramesDoNotInterleave() throws Exception {
    try (var workers = Executors.newFixedThreadPool(2)) {
      try (var input = new QuasselCoreTestInputStream(4)) {
        var first =
            workers.submit(
                () -> {
                  input.writeFrame(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
                  return null;
                });
        assertArrayEquals(new byte[] {1, 2, 3, 4}, input.readNBytes(4));
        assertThrows(TimeoutException.class, () -> first.get(100, TimeUnit.MILLISECONDS));
        var second =
            workers.submit(
                () -> {
                  input.writeFrame(new byte[] {13, 14, 15});
                  return null;
                });
        assertArrayEquals(new byte[] {5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}, input.readNBytes(11));
        first.get(2, TimeUnit.SECONDS);
        second.get(2, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void interruptedReaderExitsAndPreservesInterruptStatus() throws Exception {
    try (var input = new QuasselCoreTestInputStream(4)) {
      var started = new CountDownLatch(1);
      var read =
          new FutureTask<Boolean>(
              () -> {
                started.countDown();
                assertThrows(InterruptedIOException.class, input::read);
                return Thread.currentThread().isInterrupted();
              });
      var reader = Thread.ofVirtual().start(read);
      try {
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> read.get(100, TimeUnit.MILLISECONDS));
        reader.interrupt();
        assertTrue(read.get(2, TimeUnit.SECONDS));
      } finally {
        input.close();
        reader.join(2_000L);
      }
    }
  }
}

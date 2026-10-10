package cafe.woden.ircclient.irc.quassel;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Objects;

/** Bounded fake socket input whose lifetime is independent of the threads supplying frames. */
final class QuasselCoreTestInputStream extends InputStream {
  private final byte[] buffer;
  private final Object writeLock = new Object();
  private int readIndex;
  private int writeIndex;
  private int size;
  private boolean ended;
  private boolean closed;

  QuasselCoreTestInputStream(int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
    buffer = new byte[capacity];
  }

  void writeFrame(byte[] frame) throws IOException {
    Objects.requireNonNull(frame, "frame");
    // Keep complete frames ordered even when a full buffer makes the writer wait.
    synchronized (writeLock) {
      synchronized (this) {
        checkWritable();
        int offset = 0;
        while (offset < frame.length) {
          while (size == buffer.length && !closed && !ended) awaitChange();
          checkWritable();
          int count =
              Math.min(
                  frame.length - offset,
                  Math.min(buffer.length - size, buffer.length - writeIndex));
          System.arraycopy(frame, offset, buffer, writeIndex, count);
          writeIndex = (writeIndex + count) % buffer.length;
          offset += count;
          size += count;
          notifyAll();
        }
      }
    }
  }

  synchronized void endInbound() {
    ended = true;
    notifyAll();
  }

  @Override
  public synchronized int read() throws IOException {
    awaitReadable();
    if (size == 0) return -1;
    int value = Byte.toUnsignedInt(buffer[readIndex]);
    readIndex = (readIndex + 1) % buffer.length;
    size--;
    notifyAll();
    return value;
  }

  @Override
  public synchronized int read(byte[] bytes, int offset, int length) throws IOException {
    Objects.checkFromIndexSize(offset, length, bytes.length);
    if (length == 0) return 0;
    awaitReadable();
    if (size == 0) return -1;
    int count = Math.min(length, size);
    int first = Math.min(count, buffer.length - readIndex);
    System.arraycopy(buffer, readIndex, bytes, offset, first);
    System.arraycopy(buffer, 0, bytes, offset + first, count - first);
    readIndex = (readIndex + count) % buffer.length;
    size -= count;
    notifyAll();
    return count;
  }

  @Override
  public synchronized void close() {
    closed = true;
    size = 0;
    notifyAll();
  }

  private void awaitReadable() throws IOException {
    while (size == 0 && !ended && !closed) awaitChange();
    if (closed) throw new IOException("Input closed");
  }

  private void checkWritable() throws IOException {
    if (closed || ended) throw new IOException("Input closed for writing");
  }

  private void awaitChange() throws InterruptedIOException {
    try {
      wait();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      var interrupted = new InterruptedIOException("Interrupted while waiting for socket input");
      interrupted.initCause(error);
      throw interrupted;
    }
  }
}

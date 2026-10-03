package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class QuasselCoreSignalProxySenderTest {
  @Test
  void concurrentOperationsProduceWholeFramesAndKeepRelatedFramesTogether() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreSignalProxySender sender =
        new QuasselCoreSerializedSignalProxySender(new QuasselCoreDatastreamSender(codec));
    ByteArrayOutputStream output =
        new ByteArrayOutputStream() {
          @Override
          public void write(int value) {
            super.write(value);
            Thread.yield();
          }

          @Override
          public void write(byte[] bytes, int offset, int length) {
            super.write(bytes, offset, length);
            Thread.yield();
          }
        };
    Socket socket = outputSocket(output);
    CountDownLatch start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(4)) {
      List<Future<?>> writes = new ArrayList<>();
      for (int worker = 0; worker < 4; worker++) {
        int base = worker * 100;
        writes.add(
            workers.submit(
                () -> {
                  start.await();
                  for (int index = 0; index < 100; index++) {
                    int id = base + index;
                    sender.send(
                        socket,
                        (writer, out) -> {
                          writer.writeSignalProxySync(out, "Test", "global", "first", List.of(id));
                          writer.writeSignalProxySync(out, "Test", "global", "second", List.of(id));
                        });
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> write : writes) write.get(10, TimeUnit.SECONDS);
    }
    ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
    HashSet<Object> ids = new HashSet<>();
    for (int operation = 0; operation < 400; operation++) {
      var first = codec.readSignalProxyMessage(input);
      var second = codec.readSignalProxyMessage(input);
      assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC, first.requestType());
      assertEquals("first", first.slotName());
      assertEquals("second", second.slotName());
      assertEquals(first.params(), second.params());
      ids.add(first.params().getFirst());
    }
    assertEquals(400, ids.size());
    assertEquals(0, input.available());
  }

  @Test
  void failedOperationIsNotRetriedAndDoesNotPreventTheNextWrite() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreSignalProxySender sender =
        new QuasselCoreSerializedSignalProxySender(new QuasselCoreDatastreamSender(codec));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Socket socket = outputSocket(output);
    IOException failure = new IOException("write failed");
    assertEquals(
        failure,
        assertThrows(
            IOException.class,
            () ->
                sender.send(
                    socket,
                    (writer, out) -> {
                      throw failure;
                    })));
    sender.send(socket, (writer, out) -> writer.writeSignalProxyRpcCall(out, "next", List.of(42)));
    ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
    assertEquals("next", codec.readSignalProxyMessage(input).slotName());
    assertEquals(0, input.available());
  }

  private static Socket outputSocket(OutputStream output) {
    return new Socket() {
      @Override
      public OutputStream getOutputStream() {
        return output;
      }
    };
  }
}

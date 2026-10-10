package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreBufferCommandSenderTest {
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final OutputStream output = mock(OutputStream.class);
  private final Socket originalSocket = new Socket();
  private final AtomicReference<Socket> socket = new AtomicReference<>(originalSocket);
  private final List<Socket> writes = new ArrayList<>();
  private final QuasselCoreBufferCommandSender commands =
      new QuasselCoreBufferCommandSender(
          socket::get,
          (captured, operation) -> {
            writes.add(captured);
            socket.set(null);
            operation.write(codec, output);
          });

  @ParameterizedTest
  @ValueSource(
      strings = {
        "  message with spacing  ",
        "/JOIN #ircafe",
        "/QUOTE @+typing=active TAGMSG #ircafe"
      })
  void inputKeepsResolvedBufferAndExactUserText(String input) throws Exception {
    var buffer = new QuasselCoreDatastreamCodec.BufferInfoValue(-1, 2, 0x02, -1, "#ircafe");
    commands.sendInput(buffer, input);
    verify(codec)
        .writeSignalProxyRpcCall(output, "2sendInput(BufferInfo,QString)", List.of(buffer, input));
    assertEquals(List.of(originalSocket), writes);
  }

  @ParameterizedTest
  @CsvSource({"-1,-1,50", "100,-1,25", "-1,200,10", "100,200,200"})
  void backlogKeepsTypedBoundsAndUnqualifiedGlobalObject(int first, int last, int limit)
      throws Exception {
    commands.requestBacklog(buffer(), first, last, limit);
    verify(codec)
        .writeSignalProxySync(
            output,
            "BacklogManager",
            "",
            "requestBacklog",
            List.of(typed("BufferId", 11), typed("MsgId", first), typed("MsgId", last), limit, 0));
    assertEquals(List.of(originalSocket), writes);
  }

  @ParameterizedTest
  @ValueSource(longs = {1L, 2147483647L, 2147483648L, Long.MAX_VALUE})
  void readMarkerClampsIdAndKeepsBothFramesInOneCapturedSocketOperation(long messageId)
      throws Exception {
    commands.updateReadMarker(0, messageId);
    var params =
        List.<Object>of(
            typed("BufferId", 0), typed("MsgId", (int) Math.min(messageId, Integer.MAX_VALUE)));
    var order = inOrder(codec);
    order
        .verify(codec)
        .writeSignalProxySync(output, "BufferSyncer", "", "requestSetMarkerLine", params);
    order
        .verify(codec)
        .writeSignalProxySync(output, "BufferSyncer", "", "requestSetLastSeenMsg", params);
    order.verifyNoMoreInteractions();
    assertEquals(List.of(originalSocket), writes);
  }

  @ParameterizedTest
  @CsvSource({"-1,100", "0,0", "0,-1", "11,-9223372036854775808"})
  void invalidMarkerIsANoopBeforeSocketLookup(int bufferId, long messageId) throws Exception {
    var noSocketLookup =
        new QuasselCoreBufferCommandSender(
            () -> {
              throw new AssertionError("socket must not be looked up");
            },
            (captured, operation) -> {
              throw new AssertionError("must not send");
            });
    noSocketLookup.updateReadMarker(bufferId, messageId);
    verifyNoInteractions(codec);
  }

  @ParameterizedTest
  @ValueSource(strings = {"input", "backlog", "marker"})
  void closedSocketRejectsBeforeBufferOrInputValidation(String operation) {
    socket.set(null);
    var failure =
        assertThrows(
            IllegalStateException.class,
            () -> {
              switch (operation) {
                case "input" -> commands.sendInput(null, null);
                case "backlog" -> commands.requestBacklog(null, -1, -1, 50);
                case "marker" -> commands.updateReadMarker(11, 100);
                default -> throw new AssertionError(operation);
              }
            });
    assertEquals("Quassel socket is closed", failure.getMessage());
    assertTrue(writes.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void backlogRejectsMissingOrSyntheticBufferBeforeSending(boolean missing) {
    var invalid =
        missing ? null : new QuasselCoreDatastreamCodec.BufferInfoValue(-1, 2, 0x02, -1, "#ircafe");
    var failure =
        assertThrows(
            IllegalArgumentException.class, () -> commands.requestBacklog(invalid, -1, -1, 50));
    assertEquals("buffer info is missing a valid buffer id", failure.getMessage());
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec);
  }

  @ParameterizedTest
  @ValueSource(strings = {"requestSetMarkerLine", "requestSetLastSeenMsg"})
  void partialMarkerFailureStopsWithoutRetryAndAllowsNextOperation(String failingSlot)
      throws Exception {
    IOException failure = new IOException("partial marker write");
    doThrow(failure)
        .when(codec)
        .writeSignalProxySync(any(), eq("BufferSyncer"), eq(""), eq(failingSlot), anyList());
    assertSame(failure, assertThrows(IOException.class, () -> commands.updateReadMarker(11, 100)));
    var params = List.<Object>of(typed("BufferId", 11), typed("MsgId", 100));
    verify(codec).writeSignalProxySync(output, "BufferSyncer", "", "requestSetMarkerLine", params);
    if ("requestSetMarkerLine".equals(failingSlot)) {
      verify(codec, never())
          .writeSignalProxySync(
              any(), anyString(), anyString(), eq("requestSetLastSeenMsg"), anyList());
    } else {
      verify(codec)
          .writeSignalProxySync(output, "BufferSyncer", "", "requestSetLastSeenMsg", params);
    }
    assertEquals(List.of(originalSocket), writes);
    socket.set(originalSocket);
    commands.sendInput(buffer(), "next");
    verify(codec)
        .writeSignalProxyRpcCall(
            output, "2sendInput(BufferInfo,QString)", List.of(buffer(), "next"));
    assertEquals(2, writes.size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"input", "backlog"})
  void writeFailureIsPropagatedOnce(String operation) throws Exception {
    IOException failure = new IOException("write failed");
    doThrow(failure).when(codec).writeSignalProxyRpcCall(any(), anyString(), anyList());
    doThrow(failure)
        .when(codec)
        .writeSignalProxySync(any(), anyString(), anyString(), anyString(), anyList());
    assertSame(
        failure,
        assertThrows(
            IOException.class,
            () -> {
              if ("input".equals(operation)) commands.sendInput(buffer(), "hello");
              else commands.requestBacklog(buffer(), -1, -1, 50);
            }));
    assertEquals(List.of(originalSocket), writes);
  }

  @Test
  void concurrentBufferAndNetworkCommandsKeepReadMarkerFramesAdjacent() throws Exception {
    var realCodec = new QuasselCoreDatastreamCodec();
    var bytes = new ByteArrayOutputStream();
    Socket transportSocket =
        new Socket() {
          @Override
          public OutputStream getOutputStream() {
            return bytes;
          }
        };
    var transport =
        new QuasselCoreSerializedSignalProxySender(new QuasselCoreDatastreamSender(realCodec));
    var buffers = new QuasselCoreBufferCommandSender(() -> transportSocket, transport);
    var networks =
        new QuasselCoreNetworkCommandSender("core", "alice", () -> transportSocket, transport);
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(4)) {
      List<Future<?>> results = new ArrayList<>();
      for (int worker = 0; worker < 4; worker++) {
        int command = worker;
        results.add(
            workers.submit(
                () -> {
                  start.await();
                  for (int i = 1; i <= 25; i++) {
                    switch (command) {
                      case 0 -> buffers.updateReadMarker(11, i);
                      case 1 -> buffers.requestBacklog(buffer(), -1, i, 50);
                      case 2 -> buffers.sendInput(buffer(), "message " + i);
                      case 3 -> networks.syncNetwork(2, "requestConnect");
                      default -> throw new AssertionError(command);
                    }
                  }
                  return null;
                }));
      }
      start.countDown();
      for (var result : results) result.get(10, TimeUnit.SECONDS);
    }
    var input = new ByteArrayInputStream(bytes.toByteArray());
    int markers = 0;
    int backlog = 0;
    int messages = 0;
    int network = 0;
    while (input.available() > 0) {
      var frame = realCodec.readSignalProxyMessage(input);
      switch (frame.slotName()) {
        case "requestSetMarkerLine" -> {
          var lastSeen = realCodec.readSignalProxyMessage(input);
          assertEquals("requestSetLastSeenMsg", lastSeen.slotName());
          assertEquals(frame.params(), lastSeen.params());
          assertEquals("BufferSyncer", lastSeen.className());
          assertEquals("", lastSeen.objectName());
          markers++;
        }
        case "requestBacklog" -> backlog++;
        case "2sendInput(BufferInfo,QString)" -> messages++;
        case "requestConnect" -> network++;
        default -> throw new AssertionError("unexpected frame: " + frame);
      }
    }
    assertEquals(25, markers);
    assertEquals(25, backlog);
    assertEquals(25, messages);
    assertEquals(25, network);
  }

  private static QuasselCoreDatastreamCodec.BufferInfoValue buffer() {
    return new QuasselCoreDatastreamCodec.BufferInfoValue(11, 2, 0x02, -1, "#ircafe");
  }

  private static QuasselCoreDatastreamCodec.UserTypeValue typed(String name, int value) {
    return new QuasselCoreDatastreamCodec.UserTypeValue(name, value);
  }
}

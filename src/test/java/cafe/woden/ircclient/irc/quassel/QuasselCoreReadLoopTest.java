package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreReadLoopTest {
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final InputStream input = mock(InputStream.class);
  private final Socket socket = mock(Socket.class);
  private final AtomicBoolean stopped = new AtomicBoolean();
  private final List<QuasselCoreReadLoop.Failure> failures = new ArrayList<>();
  private final QuasselCoreReadLoop loop = new QuasselCoreReadLoop(codec);
  private final QuasselCoreDatastreamCodec.SignalProxyMessage message =
      new QuasselCoreDatastreamCodec.SignalProxyMessage(1, "Network", "1", "sync", List.of());

  @Test
  void timeoutContinuesReadingAndEofIsReportedBeforeStreamClose() throws Exception {
    when(socket.getInputStream()).thenReturn(input);
    when(codec.readSignalProxyMessage(input))
        .thenThrow(new SocketTimeoutException())
        .thenReturn(message)
        .thenThrow(new SocketTimeoutException())
        .thenThrow(new EOFException());
    List<QuasselCoreDatastreamCodec.SignalProxyMessage> messages = new ArrayList<>();
    loop.read(
        "core",
        socket,
        stopped::get,
        messages::add,
        failure -> {
          assertDoesNotThrow(() -> verify(input, never()).close());
          failures.add(failure);
        });
    assertEquals(List.of(message), messages);
    assertEquals(
        List.of(new QuasselCoreReadLoop.Failure("Quassel Core connection closed", null)), failures);
    verify(codec, times(4)).readSignalProxyMessage(input);
    verify(input).close();
  }

  @Test
  void handlerCancellationStopsBeforeAnotherReadAndClosesStream() throws Exception {
    when(socket.getInputStream()).thenReturn(input);
    when(codec.readSignalProxyMessage(input)).thenReturn(message);
    loop.read(
        "core",
        socket,
        stopped::get,
        received -> {
          assertSame(message, received);
          stopped.set(true);
        },
        failures::add);
    verify(codec).readSignalProxyMessage(input);
    verify(input).close();
    assertTrue(failures.isEmpty());
  }

  @Test
  void alreadyStoppedSessionClosesStreamWithoutReading() throws Exception {
    when(socket.getInputStream()).thenReturn(input);
    stopped.set(true);
    loop.read("core", socket, stopped::get, received -> fail("must not handle"), failures::add);
    verifyNoInteractions(codec);
    verify(input).close();
    assertTrue(failures.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void cancellationDuringReadSuppressesEofAndErrors(boolean eof) throws Exception {
    when(socket.getInputStream()).thenReturn(input);
    when(codec.readSignalProxyMessage(input))
        .thenAnswer(
            invocation -> {
              stopped.set(true);
              if (eof) throw new EOFException();
              throw new IOException("socket closed");
            });
    loop.read("core", socket, stopped::get, received -> fail("must not handle"), failures::add);
    verify(input).close();
    assertTrue(failures.isEmpty());
  }

  @ParameterizedTest
  @CsvSource({"read,true", "handler,true", "read,false", "handler,false"})
  void eofFromHandlerRemainsAnErrorWhileWireEofIsAPlainDisconnect(String source, boolean eof)
      throws Exception {
    when(socket.getInputStream()).thenReturn(input);
    IOException error = eof ? new EOFException("unexpected end") : new IOException("broken");
    if ("read".equals(source)) when(codec.readSignalProxyMessage(input)).thenThrow(error);
    else when(codec.readSignalProxyMessage(input)).thenReturn(message);
    loop.read(
        "core",
        socket,
        stopped::get,
        received -> {
          throw error;
        },
        failures::add);
    assertEquals(1, failures.size());
    var failure = failures.getFirst();
    if (eof && "read".equals(source)) {
      assertNull(failure.cause());
      assertEquals("Quassel Core connection closed", failure.reason());
    } else {
      assertSame(error, failure.cause());
      assertEquals("Connection error: " + error.getMessage(), failure.reason());
    }
    verify(input).close();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "  broken  "})
  void errorReasonKeepsClassFallbackAndTrimmedDetail(String detail) throws Exception {
    when(socket.getInputStream()).thenReturn(input);
    IOException error = new IOException(detail);
    when(codec.readSignalProxyMessage(input)).thenThrow(error);
    loop.read("core", socket, stopped::get, received -> fail("must not handle"), failures::add);
    assertSame(error, failures.getFirst().cause());
    assertEquals(
        "Connection error: " + (detail == null || detail.isBlank() ? "IOException" : "broken"),
        failures.getFirst().reason());
  }

  @Test
  void openingStreamFailureIsReportedWithoutCallingReader() throws Exception {
    IOException error = new IOException("cannot open stream");
    when(socket.getInputStream()).thenThrow(error);
    loop.read("core", socket, stopped::get, received -> fail("must not handle"), failures::add);
    assertEquals(
        List.of(new QuasselCoreReadLoop.Failure("Connection error: cannot open stream", error)),
        failures);
    verifyNoInteractions(codec, input);
  }

  @Test
  void streamCloseFailureAfterEofKeepsBothFailureNotifications() throws Exception {
    when(socket.getInputStream()).thenReturn(input);
    when(codec.readSignalProxyMessage(input)).thenThrow(new EOFException());
    IOException closeError = new IOException("close failed");
    doThrow(closeError).when(input).close();
    loop.read("core", socket, stopped::get, received -> fail("must not handle"), failures::add);
    assertEquals(
        List.of(
            new QuasselCoreReadLoop.Failure("Quassel Core connection closed", null),
            new QuasselCoreReadLoop.Failure("Connection error: close failed", closeError)),
        failures);
  }

  @Test
  void closeFailureAfterHandlerCancellationIsSuppressed() throws Exception {
    when(socket.getInputStream()).thenReturn(input);
    when(codec.readSignalProxyMessage(input)).thenReturn(message);
    doThrow(new IOException("close failed")).when(input).close();
    loop.read("core", socket, stopped::get, received -> stopped.set(true), failures::add);
    assertTrue(failures.isEmpty());
    verify(input).close();
  }

  @Test
  void handlerFailureKeepsStreamCloseFailureAsSuppressedCause() throws Exception {
    when(socket.getInputStream()).thenReturn(input);
    when(codec.readSignalProxyMessage(input)).thenReturn(message);
    IOException handlingError = new IOException("handler failed");
    IOException closeError = new IOException("close failed");
    doThrow(closeError).when(input).close();
    loop.read(
        "core",
        socket,
        stopped::get,
        received -> {
          throw handlingError;
        },
        failures::add);
    assertSame(handlingError, failures.getFirst().cause());
    assertArrayEquals(new Throwable[] {closeError}, handlingError.getSuppressed());
  }
}

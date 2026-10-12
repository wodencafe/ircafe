package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.quassel.QuasselCoreReadLoop.Failure;
import cafe.woden.ircclient.irc.quassel.QuasselCoreReadLoop.MessageHandler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSessionReaderTest {
  private final QuasselCoreReadLoop loop = mock(QuasselCoreReadLoop.class);
  private final QuasselCoreSessionLifecycle lifecycle = mock(QuasselCoreSessionLifecycle.class);
  private final MessageHandler handler = mock(MessageHandler.class);

  @SuppressWarnings("unchecked")
  private final Function<QuasselCoreSession, MessageHandler> factory = mock(Function.class);

  private final AtomicBoolean shuttingDown = new AtomicBoolean();
  private final TestScheduler scheduler = new TestScheduler();
  private final QuasselCoreSessionReader reader =
      new QuasselCoreSessionReader(loop, factory, shuttingDown::get, lifecycle, () -> scheduler);
  private final Socket socket = mock(Socket.class);
  private final QuasselCoreSession session =
      new QuasselCoreSession(
          "core",
          "alice",
          "host",
          4242,
          (opened, operation) -> fail("reader must not send"),
          id -> {},
          event -> {});
  private final QuasselCoreDatastreamCodec.SignalProxyMessage message =
      new QuasselCoreDatastreamCodec.SignalProxyMessage(1, "Network", "1", "sync", List.of());

  @AfterEach
  void disposeTasks() {
    session.readiness.close();
    session.disposeReadLoopTask();
  }

  @Test
  void missingSocketClosesReadinessWithoutCreatingPipelineReadingOrFinishingSession() {
    var fallback = Disposable.empty();
    session.readiness.fallbackTask.set(fallback);
    reader.run(session);
    assertTrue(fallback.isDisposed());
    assertNull(session.readiness.fallbackTask.get());
    verifyNoInteractions(loop, factory, handler, lifecycle);
  }

  @Test
  void readUsesTheCurrentSocketAndPipelineAndFinishesAfterDispatch() throws Exception {
    ready();
    doAnswer(
            invocation -> {
              MessageHandler messages = invocation.getArgument(3);
              messages.handle(message);
              verify(lifecycle, never()).finishReadLoop(any());
              return null;
            })
        .when(loop)
        .read(eq("core"), eq(socket), any(), same(handler), any());
    reader.run(session);
    verify(factory).apply(session);
    verify(handler).handle(message);
    verify(lifecycle).finishReadLoop(session);
    verifyNoMoreInteractions(factory, handler, lifecycle);
  }

  @Test
  void failureCallbackIsForwardedBeforeFinalCleanup() {
    ready();
    var failure = new Failure("broken frame", new IOException("decode failed"));
    doAnswer(
            invocation -> {
              Consumer<Failure> failures = invocation.getArgument(4);
              failures.accept(failure);
              verify(lifecycle).handleReadFailure(session, failure);
              verify(lifecycle, never()).finishReadLoop(any());
              return null;
            })
        .when(loop)
        .read(eq("core"), eq(socket), any(), same(handler), any());
    reader.run(session);
    var order = inOrder(lifecycle);
    order.verify(lifecycle).handleReadFailure(session, failure);
    order.verify(lifecycle).finishReadLoop(session);
    verifyNoMoreInteractions(lifecycle);
  }

  @ParameterizedTest
  @ValueSource(strings = {"factory", "reader"})
  void cleanupRunsWhenPipelineCreationOrReadingThrows(String source) {
    ready();
    var failure = new IllegalStateException("unexpected failure");
    if ("factory".equals(source)) when(factory.apply(session)).thenThrow(failure);
    else doThrow(failure).when(loop).read(eq("core"), eq(socket), any(), same(handler), any());
    assertSame(failure, assertThrows(IllegalStateException.class, () -> reader.run(session)));
    verify(lifecycle).finishReadLoop(session);
    verify(lifecycle, never()).handleReadFailure(any(), any());
    if ("factory".equals(source)) verifyNoInteractions(loop);
  }

  @Test
  void stopPredicateReadsLiveShutdownAndCloseFlagsDuringTheRead() {
    ready();
    doAnswer(
            invocation -> {
              BooleanSupplier stopped = invocation.getArgument(2);
              assertFalse(stopped.getAsBoolean());
              session.closeRequested.set(true);
              assertTrue(stopped.getAsBoolean());
              session.closeRequested.set(false);
              shuttingDown.set(true);
              assertTrue(stopped.getAsBoolean());
              shuttingDown.set(false);
              assertFalse(stopped.getAsBoolean());
              return null;
            })
        .when(loop)
        .read(eq("core"), eq(socket), any(), same(handler), any());
    reader.run(session);
    verify(lifecycle).finishReadLoop(session);
  }

  @Test
  void startDefersReadingAndStoresTaskBeforeFallbackAndPipelineExecution() {
    session.socketRef.set(socket);
    when(factory.apply(session))
        .thenAnswer(
            invocation -> {
              assertNotNull(session.readLoopTask.get());
              assertNotNull(session.readiness.fallbackTask.get());
              return handler;
            });
    reader.start(session);
    var task = session.readLoopTask.get();
    assertNotNull(task);
    assertFalse(task.isDisposed());
    assertNotNull(session.readiness.fallbackTask.get());
    verifyNoInteractions(loop, factory, lifecycle);
    scheduler.triggerActions();
    verify(factory).apply(session);
    verify(loop).read(eq("core"), eq(socket), any(), same(handler), any());
    verify(lifecycle).finishReadLoop(session);
  }

  @Test
  void disposingTheSessionTaskBeforeQueuedReadPreventsPipelineAndReadExecution() {
    session.socketRef.set(socket);
    reader.start(session);
    var task = session.readLoopTask.get();
    session.disposeReadLoopTask();
    assertTrue(task.isDisposed());
    assertNull(session.readLoopTask.get());
    scheduler.triggerActions();
    verifyNoInteractions(loop, factory, lifecycle);
  }

  @Test
  void queuedReadResolvesSocketWhenItRunsRatherThanWhenStarted() {
    session.socketRef.set(socket);
    reader.start(session);
    session.socketRef.set(null);
    scheduler.triggerActions();
    assertNull(session.readiness.fallbackTask.get());
    verifyNoInteractions(loop, factory, lifecycle);
  }

  @Test
  void startSchedulingFailureDoesNotAttachATaskOrScheduleFallback() {
    var failure = new IllegalStateException("scheduler unavailable");
    var unavailable =
        new QuasselCoreSessionReader(
            loop,
            factory,
            shuttingDown::get,
            lifecycle,
            () -> {
              throw failure;
            });
    assertSame(
        failure, assertThrows(IllegalStateException.class, () -> unavailable.start(session)));
    assertNull(session.readLoopTask.get());
    assertNull(session.readiness.fallbackTask.get());
    verifyNoInteractions(loop, factory, lifecycle);
  }

  private void ready() {
    session.socketRef.set(socket);
    when(factory.apply(session)).thenReturn(handler);
  }
}

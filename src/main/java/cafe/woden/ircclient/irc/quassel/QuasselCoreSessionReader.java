package cafe.woden.ircclient.irc.quassel;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import java.net.Socket;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Starts and runs session reads using the supplied IO scheduler. Task handles remain on the
 * session; lifecycle coordination owns failure publication and cleanup.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreSessionReader {
  @NonNull private final QuasselCoreReadLoop readLoop;

  @NonNull
  private final Function<QuasselCoreSession, QuasselCoreReadLoop.MessageHandler>
      messageHandlerFactory;

  @NonNull private final BooleanSupplier shuttingDown;
  @NonNull private final QuasselCoreSessionLifecycle sessionLifecycle;
  @NonNull private final Supplier<Scheduler> scheduler;

  void start(QuasselCoreSession session) {
    Disposable readTask = scheduler.get().scheduleDirect(() -> run(session));
    session.readLoopTask.set(readTask);
    session.readiness.scheduleFallback();
  }

  void run(QuasselCoreSession session) {
    String sid = session.serverId;
    Socket socket = session.socketRef.get();
    if (socket == null) {
      session.readiness.close();
      return;
    }

    try {
      QuasselCoreReadLoop.MessageHandler messages = messageHandlerFactory.apply(session);
      readLoop.read(
          sid,
          socket,
          () -> shuttingDown.getAsBoolean() || session.closeRequested.get(),
          messages,
          failure -> sessionLifecycle.handleReadFailure(session, failure));
    } finally {
      sessionLifecycle.finishReadLoop(session);
    }
  }
}

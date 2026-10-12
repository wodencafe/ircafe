package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreSessionEventPublisher.normalizeDisconnectReason;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import java.net.Socket;
import java.time.Instant;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Coordinates session teardown, read-failure publication, and reconnect eligibility. The service
 * owns the shared session map and read tasks; this collaborator creates no workers or
 * subscriptions.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreSessionLifecycle {
  @NonNull private final Map<String, QuasselCoreSession> sessions;
  @NonNull private final BiConsumer<String, String> rememberAvailability;
  @NonNull private final BooleanSupplier shuttingDown;
  @NonNull private final QuasselCoreReconnectCoordinator reconnects;
  @NonNull private final QuasselCoreSessionEventPublisher sessionEvents;
  @NonNull private final Consumer<ServerIrcEvent> events;

  void closeSession(QuasselCoreSession session, String reason, boolean emitDisconnected) {
    if (session == null) return;
    session.closeRequested.set(true);
    session.closeReason.set(normalizeDisconnectReason(reason));

    session.readiness.close();

    session.disposeReadLoopTask();

    closeQuietly(session.socketRef.getAndSet(null));
    session.clearObservedState();
    rememberAvailability.accept(session.serverId, session.closeReason.get());
    if (emitDisconnected) {
      sessionEvents.emitDisconnectedOnce(session, session.closeReason.get());
    }
  }

  void finishReadLoop(QuasselCoreSession session) {
    String sid = session.serverId;
    session.readiness.close();
    closeQuietly(session.socketRef.getAndSet(null));
    sessions.remove(sid, session);
    if (session.closeRequested.get()) {
      String reason = normalizeDisconnectReason(session.closeReason.get());
      rememberAvailability.accept(sid, reason);
      sessionEvents.emitDisconnectedOnce(session, reason);
    }
  }

  void handleReadFailure(QuasselCoreSession session, QuasselCoreReadLoop.Failure failure) {
    session.readiness.close();
    String sid = session.serverId;
    String reason = failure.reason();
    rememberAvailability.accept(sid, reason);
    if (failure.cause() != null) {
      events.accept(
          new ServerIrcEvent(sid, new IrcEvent.Error(Instant.now(), reason, failure.cause())));
    }
    sessionEvents.emitDisconnectedOnce(session, reason);
    scheduleReconnectIfEligible(session, reason);
  }

  void scheduleReconnectIfEligible(QuasselCoreSession session, String reason) {
    if (session == null || shuttingDown.getAsBoolean() || session.closeRequested.get()) return;
    if (!session.reconnectScheduled.compareAndSet(false, true)) return;
    reconnects.schedule(session.serverId, reason);
  }

  static void closeQuietly(Socket socket) {
    if (socket == null) return;
    try {
      socket.close();
    } catch (Exception ignored) {
    }
  }
}

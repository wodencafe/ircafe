package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;

import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import java.net.Socket;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Schedules native heartbeat probes and reads measurements from the current session. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreLagProbeCoordinator {
  @NonNull private final BiFunction<String, String, QuasselCoreSession> establishedSessionLookup;
  @NonNull private final Function<String, QuasselCoreSession> sessionLookup;

  Completable request(String serverId) {
    return Completable.fromAction(
            () -> {
              String sid = Objects.toString(serverId, "").trim();
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              QuasselCoreSession session = establishedSessionLookup.apply(sid, "request lag probe");
              Socket socket = session.socketRef.get();
              if (socket == null) {
                throw new IllegalStateException("Quassel socket is closed");
              }

              QuasselCoreDatastreamCodec.QtDateTimeValue token = session.lag.beginProbe();

              try {
                session.outbound.send(
                    socket,
                    (codec, out) -> {
                      codec.writeSignalProxyHeartBeat(out, token);
                    });
              } catch (Exception e) {
                session.lag.cancelProbe();
                throw e;
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  OptionalLong lastMeasuredLagMs(String serverId) {
    QuasselCoreSession session = sessionLookup.apply(Objects.toString(serverId, "").trim());
    if (session == null || session.socketRef.get() == null) return OptionalLong.empty();
    return session.lag.lastMeasuredLagMs();
  }
}

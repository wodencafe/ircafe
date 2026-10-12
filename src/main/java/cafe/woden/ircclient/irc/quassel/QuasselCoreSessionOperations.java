package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.containsCrlf;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Validates and dispatches session commands on IO, resolving the current session at subscription.
 * Input planning stays eager; this collaborator owns no sessions, workers, or subscriptions.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreSessionOperations {
  @NonNull private final QuasselCoreSessionQueries sessionQueries;
  @NonNull private final QuasselCoreInputCoordinator inputs;

  @FunctionalInterface
  interface EstablishedSessionAction {
    void run(String serverId, QuasselCoreSession session) throws Exception;
  }

  QuasselCoreSession requireEstablishedSession(String serverId, String operation)
      throws BackendNotAvailableException {
    String sid = normalizeServerId(serverId);
    QuasselCoreSession session = sessionQueries.findEstablished(sid);
    if (session == null) {
      throw new BackendNotAvailableException(
          IrcProperties.Server.Backend.QUASSEL_CORE,
          operation,
          sid,
          sessionQueries.availabilityReason(sid));
    }
    return session;
  }

  Completable withEstablishedSession(
      String serverId, String operation, EstablishedSessionAction action) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) throw new IllegalArgumentException(SERVER_ID_BLANK);
              QuasselCoreSession session = requireEstablishedSession(sid, operation);
              action.run(sid, session);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  Completable sendPlannedInput(String serverId, Supplier<QuasselCoreUserInput.Plan> planner) {
    QuasselCoreUserInput.Plan plan;
    try {
      plan = planner.get();
    } catch (IllegalArgumentException error) {
      return Completable.error(error);
    }
    return sendInputWithBuffer(
        serverId, plan.operation(), plan.typeBits(), plan.target(), plan.input());
  }

  private Completable sendInputWithBuffer(
      String serverId, String operation, int typeBits, String bufferName, String input) {
    return withEstablishedSession(
        serverId,
        operation,
        (ignored, session) -> inputs.sendInput(session, operation, typeBits, bufferName, input));
  }

  Completable sendRaw(String serverId, String rawLine) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              String raw = Objects.toString(rawLine, "").strip();
              if (sid.isEmpty()) throw new IllegalArgumentException(SERVER_ID_BLANK);
              if (raw.isEmpty()) throw new IllegalArgumentException("raw line is blank");
              if (containsCrlf(raw)) throw new IllegalArgumentException("raw line contains CR/LF");

              QuasselCoreSession session = requireEstablishedSession(sid, "send raw");
              inputs.sendRaw(session, "send raw", raw);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}

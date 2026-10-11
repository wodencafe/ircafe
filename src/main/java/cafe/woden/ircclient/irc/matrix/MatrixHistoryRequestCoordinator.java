package cafe.woden.ircclient.irc.matrix;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;
import static cafe.woden.ircclient.irc.matrix.MatrixHistoryCursorCoordinator.parseHistoryMessageIdSelector;
import static cafe.woden.ircclient.irc.matrix.MatrixHistoryCursorCoordinator.parseHistoryTimestampSelector;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Coordinates history request modes using the existing session, target, and cursor collaborators.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixHistoryRequestCoordinator {
  @FunctionalInterface
  interface BatchEmitter {
    void emit(String serverId, String target, List<ChatHistoryEntry> entries);
  }

  @NonNull private final ServerCatalog serverCatalog;
  @NonNull private final MatrixRoomTargetResolver roomTargetResolver;
  @NonNull private final MatrixHistoryCursorCoordinator historyCursorCoordinator;
  @NonNull private final Function<String, MatrixSession> sessionLookup;
  @NonNull private final Function<String, String> availabilityReason;

  @NonNull
  private final Function<MatrixSession, MatrixHistoryCursorCoordinator.SessionView>
      historySessionViews;

  @NonNull private final BatchEmitter batchEmitter;

  Completable requestBefore(String serverId, String target, String selector, int limit) {
    String token = normalize(selector);
    if (token.isEmpty()) {
      return requestBefore(serverId, target, Instant.now(), limit);
    }

    Instant ts = parseHistoryTimestampSelector(token);
    if (ts != null) {
      return requestBefore(serverId, target, ts, limit);
    }

    String messageId = parseHistoryMessageIdSelector(token);
    if (!messageId.isEmpty()) {
      return requestBeforeByMessageId(serverId, target, messageId, limit, "chat-history-before");
    }

    return Completable.error(
        new IllegalArgumentException("selector must be msgid=... or timestamp=..."));
  }

  Completable requestLatest(String serverId, String target, String selector, int limit) {
    String token = normalize(selector);
    if (token.isEmpty() || "*".equals(token)) {
      return requestBefore(serverId, target, Instant.now(), limit);
    }
    Instant ts = parseHistoryTimestampSelector(token);
    if (ts != null) {
      return requestBefore(serverId, target, ts, limit);
    }
    String messageId = parseHistoryMessageIdSelector(token);
    if (!messageId.isEmpty()) {
      return requestBeforeByMessageId(serverId, target, messageId, limit, "chat-history-latest");
    }
    return Completable.error(
        new IllegalArgumentException("selector must be *, msgid=..., or timestamp=..."));
  }

  Completable requestBetween(
      String serverId, String target, String startSelector, String endSelector, int limit) {
    String startToken = normalize(startSelector);
    String endToken = normalize(endSelector);
    if (startToken.isEmpty() || endToken.isEmpty()) {
      return Completable.error(
          new IllegalArgumentException(
              "CHATHISTORY BETWEEN requires <start-selector> <end-selector>"));
    }

    return Completable.fromAction(
            () -> {
              String sid = normalize(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              String requestedTarget = normalize(target);
              if (requestedTarget.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }

              MatrixSession session = sessionLookup.apply(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "chat-history-between",
                    sid,
                    availabilityReason.apply(sid));
              }

              MatrixHistoryCursorCoordinator.SessionView historySession =
                  historySessionViews.apply(session);
              int requestedLimit = historyCursorCoordinator.normalizeHistoryLimit(limit);
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId =
                  roomTargetResolver.resolveHistoryRoomId(sid, requestedTarget, server, session);
              Instant startAt =
                  historyCursorCoordinator.resolveHistorySelectorInstant(
                      sid,
                      server,
                      historySession,
                      roomId,
                      startToken,
                      "chat-history-between",
                      "start selector");
              Instant endAt =
                  historyCursorCoordinator.resolveHistorySelectorInstant(
                      sid,
                      server,
                      historySession,
                      roomId,
                      endToken,
                      "chat-history-between",
                      "end selector");
              Instant endExclusive = endAt == null ? Instant.now() : endAt;
              if (startAt != null && !startAt.isBefore(endExclusive)) {
                throw new IllegalArgumentException(
                    "start selector must be earlier than end selector");
              }

              Instant startInclusive = startAt == null ? Instant.EPOCH : startAt;
              String forwardCursor =
                  historyCursorCoordinator.resolveForwardCursorForTimestamp(
                      sid, server, historySession, roomId, startInclusive.toEpochMilli());
              List<ChatHistoryEntry> entries =
                  historyCursorCoordinator.fetchHistoryEntriesForwardFromCursor(
                      sid,
                      server,
                      historySession,
                      requestedTarget,
                      roomId,
                      forwardCursor,
                      startInclusive,
                      endExclusive,
                      requestedLimit);
              batchEmitter.emit(sid, requestedTarget, entries);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  Completable requestAround(String serverId, String target, String selector, int limit) {
    return Completable.fromAction(
            () -> {
              String sid = normalize(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              String requestedTarget = normalize(target);
              if (requestedTarget.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }
              String token = normalize(selector);

              MatrixSession session = sessionLookup.apply(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "chat-history-around",
                    sid,
                    availabilityReason.apply(sid));
              }

              MatrixHistoryCursorCoordinator.SessionView historySession =
                  historySessionViews.apply(session);
              int requestedLimit = historyCursorCoordinator.normalizeHistoryLimit(limit);
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId =
                  roomTargetResolver.resolveHistoryRoomId(sid, requestedTarget, server, session);

              Instant centerAt;
              if (token.isEmpty() || "*".equals(token)) {
                centerAt = Instant.now();
              } else {
                centerAt =
                    historyCursorCoordinator.resolveHistorySelectorInstant(
                        sid,
                        server,
                        historySession,
                        roomId,
                        token,
                        "chat-history-around",
                        "selector");
              }

              String forwardCursor =
                  historyCursorCoordinator.resolveForwardCursorForTimestamp(
                      sid, server, historySession, roomId, centerAt.toEpochMilli());
              int scanLimit = Math.min(200, Math.max(requestedLimit * 4, requestedLimit + 20));
              List<ChatHistoryEntry> backwardCandidates =
                  historyCursorCoordinator.fetchHistoryEntriesBackwardFromCursor(
                      sid,
                      server,
                      historySession,
                      requestedTarget,
                      roomId,
                      forwardCursor,
                      null,
                      null,
                      scanLimit);
              List<ChatHistoryEntry> forwardCandidates =
                  historyCursorCoordinator.fetchHistoryEntriesForwardFromCursor(
                      sid,
                      server,
                      historySession,
                      requestedTarget,
                      roomId,
                      forwardCursor,
                      null,
                      null,
                      scanLimit);
              List<ChatHistoryEntry> candidates =
                  MatrixHistoryCursorCoordinator.mergeHistoryCandidates(
                      backwardCandidates, forwardCandidates);
              List<ChatHistoryEntry> entries =
                  MatrixHistoryCursorCoordinator.selectEntriesAroundTimestamp(
                      candidates, centerAt, requestedLimit);
              batchEmitter.emit(sid, requestedTarget, entries);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  Completable requestBefore(String serverId, String target, Instant beforeExclusive, int limit) {
    return Completable.fromAction(
            () -> {
              String sid = normalize(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              String requestedTarget = normalize(target);
              if (requestedTarget.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }

              MatrixSession session = sessionLookup.apply(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "chat-history",
                    sid,
                    availabilityReason.apply(sid));
              }

              MatrixHistoryCursorCoordinator.SessionView historySession =
                  historySessionViews.apply(session);
              int requestedLimit = historyCursorCoordinator.normalizeHistoryLimit(limit);
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId =
                  roomTargetResolver.resolveHistoryRoomId(sid, requestedTarget, server, session);
              long beforeEpochMs =
                  beforeExclusive == null
                      ? System.currentTimeMillis()
                      : beforeExclusive.toEpochMilli();
              List<ChatHistoryEntry> entries =
                  historyCursorCoordinator.fetchHistoryEntriesBefore(
                      sid,
                      server,
                      historySession,
                      requestedTarget,
                      roomId,
                      beforeEpochMs,
                      requestedLimit);
              batchEmitter.emit(sid, requestedTarget, entries);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private Completable requestBeforeByMessageId(
      String serverId, String target, String messageId, int limit, String operation) {
    return Completable.defer(
        () -> {
          String sid = normalize(serverId);
          if (sid.isEmpty()) {
            return Completable.error(new IllegalArgumentException(SERVER_ID_BLANK));
          }
          String requestedTarget = normalize(target);
          if (requestedTarget.isEmpty()) {
            return Completable.error(new IllegalArgumentException("target is blank"));
          }

          MatrixSession session = sessionLookup.apply(sid);
          if (session == null) {
            return Completable.error(
                new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    operation,
                    sid,
                    availabilityReason.apply(sid)));
          }

          IrcProperties.Server server = serverCatalog.require(sid);
          String roomId =
              roomTargetResolver.resolveHistoryRoomId(sid, requestedTarget, server, session);
          Instant marker =
              historyCursorCoordinator.resolveHistoryMessageIdInstant(
                  sid, server, historySessionViews.apply(session), roomId, messageId, operation);
          return requestBefore(sid, requestedTarget, marker, limit);
        });
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}

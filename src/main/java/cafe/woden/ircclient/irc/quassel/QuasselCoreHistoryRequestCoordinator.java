package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.sanitizeHistoryTarget;

import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryRuntimeSupport;
import cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.QualifiedTarget;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Coordinates runtime-provider history plans and native backlog requests against live sessions. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreHistoryRequestCoordinator {
  @NonNull private final QuasselIrcv3RuntimeSupport ircv3RuntimeSupport;
  @NonNull private final QuasselCoreTargetResolver targets;
  @NonNull private final BiFunction<String, String, QuasselCoreSession> sessionLookup;

  Completable requestBefore(String serverId, String target, Instant beforeExclusive, int limit) {
    return requestHistory(
        serverId,
        "request chat history",
        () ->
            ircv3RuntimeSupport.chatHistoryBefore(
                target, "", limit, beforeExclusive == null ? Instant.now() : beforeExclusive));
  }

  Completable requestBefore(String serverId, String target, String selector, int limit) {
    return requestHistory(
        serverId,
        "request chat history",
        () -> ircv3RuntimeSupport.chatHistoryBefore(target, selector, limit, Instant.now()));
  }

  Completable requestLatest(String serverId, String target, String selector, int limit) {
    return requestHistory(
        serverId,
        "request latest chat history",
        () -> ircv3RuntimeSupport.chatHistoryLatest(target, selector, limit));
  }

  Completable requestBetween(
      String serverId, String target, String startSelector, String endSelector, int limit) {
    return requestHistory(
        serverId,
        "request bounded chat history",
        () -> ircv3RuntimeSupport.chatHistoryBetween(target, startSelector, endSelector, limit));
  }

  Completable requestAround(String serverId, String target, String selector, int limit) {
    return requestHistory(
        serverId,
        "request surrounding chat history",
        () -> ircv3RuntimeSupport.chatHistoryAround(target, selector, limit));
  }

  private Completable requestHistory(
      String serverId, String operation, Supplier<Ircv3ChatHistoryRuntimeSupport.Plan> planner) {
    return Completable.fromAction(
            () -> {
              Ircv3ChatHistoryRuntimeSupport.Plan plan = planner.get();
              HistoryRequestContext ctx = prepareHistoryRequest(serverId, plan.target(), operation);
              QuasselCoreHistoryRequestPlanner.BacklogRequest request =
                  QuasselCoreHistoryRequestPlanner.plan(ctx.session().history, plan);
              ctx.session()
                  .bufferCommands
                  .requestBacklog(
                      ctx.bufferInfo(), request.firstMsgId(), request.lastMsgId(), request.limit());
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private HistoryRequestContext prepareHistoryRequest(
      String serverId, String target, String operation) throws BackendNotAvailableException {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      throw new IllegalArgumentException(SERVER_ID_BLANK);
    }
    QualifiedTarget tgt = sanitizeHistoryTarget(target);
    QuasselCoreSession session = sessionLookup.apply(sid, operation);
    QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo =
        targets.historyBuffer(session, sid, operation, tgt);
    return new HistoryRequestContext(session, bufferInfo);
  }

  private record HistoryRequestContext(
      QuasselCoreSession session, QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo) {}
}

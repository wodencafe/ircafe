package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.UNKNOWN_MSG_ID;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.clampMsgId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.parseHistorySelector;

import cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryRuntimeSupport.Plan;
import cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.HistorySelector;
import cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.HistorySelectorKind;

/** Translates validated CHATHISTORY plans into native Core backlog bounds without owning state. */
final class QuasselCoreHistoryRequestPlanner {
  private QuasselCoreHistoryRequestPlanner() {}

  static BacklogRequest plan(QuasselCoreHistorySupport history, Plan plan) {
    int limit = QuasselCoreHistorySupport.normalizeHistoryLimit(plan.limit());
    return switch (plan.operation()) {
      case CHAT_HISTORY_BEFORE ->
          new BacklogRequest(
              UNKNOWN_MSG_ID,
              boundary(
                  history,
                  plan.target(),
                  parseHistorySelector(plan.primarySelector(), false),
                  false),
              limit);
      case CHAT_HISTORY_LATEST ->
          new BacklogRequest(
              boundary(
                  history, plan.target(), parseHistorySelector(plan.primarySelector(), true), true),
              UNKNOWN_MSG_ID,
              limit);
      case CHAT_HISTORY_BETWEEN -> between(history, plan, limit);
      case CHAT_HISTORY_AROUND -> around(history, plan, limit);
      default ->
          throw new IllegalArgumentException("Not a CHATHISTORY operation: " + plan.operation());
    };
  }

  private static BacklogRequest between(QuasselCoreHistorySupport history, Plan plan, int limit) {
    HistorySelector start = parseHistorySelector(plan.primarySelector(), true);
    HistorySelector end = parseHistorySelector(plan.secondarySelector(), true);
    int startMsgId = boundary(history, plan.target(), start, false);
    int endMsgId = boundary(history, plan.target(), end, false);
    boolean reversed =
        start.kind() == HistorySelectorKind.TIMESTAMP && end.kind() == HistorySelectorKind.TIMESTAMP
            ? start.timestamp().isAfter(end.timestamp())
            : startMsgId > 0 && endMsgId > 0 && startMsgId > endMsgId;
    HistorySelector lower = reversed ? end : start;
    HistorySelector upper = reversed ? start : end;
    return new BacklogRequest(
        boundary(history, plan.target(), lower, true),
        boundary(history, plan.target(), upper, false),
        limit);
  }

  private static BacklogRequest around(QuasselCoreHistorySupport history, Plan plan, int limit) {
    HistorySelector selector = parseHistorySelector(plan.primarySelector(), false);
    long anchorMsgId =
        switch (selector.kind()) {
          case WILDCARD -> UNKNOWN_MSG_ID;
          case MSGID -> selector.msgId();
          case TIMESTAMP -> history.msgIdForTimestamp(plan.target(), selector.timestamp());
        };
    if (anchorMsgId <= 0) return new BacklogRequest(UNKNOWN_MSG_ID, UNKNOWN_MSG_ID, limit);
    int halfWindow = Math.max(1, limit / 2);
    return new BacklogRequest(
        clampMsgId(Math.max(1L, anchorMsgId - halfWindow)),
        clampMsgId(anchorMsgId + halfWindow),
        limit);
  }

  private static int boundary(
      QuasselCoreHistorySupport history,
      String target,
      HistorySelector selector,
      boolean lowerBound) {
    long boundary =
        switch (selector.kind()) {
          case WILDCARD -> UNKNOWN_MSG_ID;
          // Core uses an inclusive lower ID bound and an exclusive upper ID bound.
          case MSGID -> lowerBound ? selector.msgId() + 1L : selector.msgId();
          case TIMESTAMP ->
              lowerBound
                  ? history.firstMsgIdAfterTimestamp(target, selector.timestamp())
                  : history.firstMsgIdAtOrAfterTimestamp(target, selector.timestamp());
        };
    return clampMsgId(boundary);
  }

  record BacklogRequest(int firstMsgId, int lastMsgId, int limit) {}
}

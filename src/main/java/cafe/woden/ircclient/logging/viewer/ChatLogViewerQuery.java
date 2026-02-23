package cafe.woden.ircclient.logging.viewer;

import java.util.Objects;

/**
 * Query model for the log viewer.
 *
 * <p>Dates are optional epoch milliseconds (inclusive).
 */
public record ChatLogViewerQuery(
    String serverId,
    String nickPattern,
    ChatLogViewerMatchMode nickMode,
    String messagePattern,
    ChatLogViewerMatchMode messageMode,
    String hostmaskPattern,
    ChatLogViewerMatchMode hostmaskMode,
    String channelPattern,
    ChatLogViewerMatchMode channelMode,
    boolean includeServerEvents,
    boolean includeProtocolDetails,
    Long fromEpochMs,
    Long toEpochMs,
    int limit
) {

  public ChatLogViewerQuery {
    serverId = norm(serverId);
    nickPattern = norm(nickPattern);
    messagePattern = norm(messagePattern);
    hostmaskPattern = norm(hostmaskPattern);
    channelPattern = norm(channelPattern);
    if (nickMode == null) nickMode = ChatLogViewerMatchMode.CONTAINS;
    if (messageMode == null) messageMode = ChatLogViewerMatchMode.CONTAINS;
    if (hostmaskMode == null) hostmaskMode = ChatLogViewerMatchMode.CONTAINS;
    if (channelMode == null) channelMode = ChatLogViewerMatchMode.CONTAINS;
    if (limit <= 0) limit = 500;
    if (limit > 10_000) limit = 10_000;

    if (fromEpochMs != null && toEpochMs != null && fromEpochMs > toEpochMs) {
      long tmp = fromEpochMs;
      fromEpochMs = toEpochMs;
      toEpochMs = tmp;
    }
  }

  private static String norm(String s) {
    return Objects.toString(s, "").trim();
  }
}

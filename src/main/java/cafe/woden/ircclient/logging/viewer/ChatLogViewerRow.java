package cafe.woden.ircclient.logging.viewer;

import cafe.woden.ircclient.logging.model.LogDirection;
import cafe.woden.ircclient.logging.model.LogKind;
import java.util.Map;
import java.util.Objects;

/** A log row projected for the log viewer UI. */
public record ChatLogViewerRow(
    long id,
    String serverId,
    String target,
    long tsEpochMs,
    LogDirection direction,
    LogKind kind,
    String fromNick,
    String hostmask,
    String text,
    String messageId,
    Map<String, String> ircv3Tags,
    String metaJson) {
  public ChatLogViewerRow {
    serverId = Objects.toString(serverId, "").trim();
    target = Objects.toString(target, "").trim();
    fromNick = Objects.toString(fromNick, "").trim();
    hostmask = Objects.toString(hostmask, "").trim();
    text = Objects.toString(text, "");
    messageId = Objects.toString(messageId, "").trim();
    metaJson = Objects.toString(metaJson, "");
    if (direction == null) direction = LogDirection.SYSTEM;
    if (kind == null) kind = LogKind.STATUS;
    ircv3Tags = (ircv3Tags == null || ircv3Tags.isEmpty()) ? Map.of() : Map.copyOf(ircv3Tags);
  }
}

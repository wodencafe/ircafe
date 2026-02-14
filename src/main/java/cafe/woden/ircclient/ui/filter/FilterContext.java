package cafe.woden.ircclient.ui.filter;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.logging.model.LogDirection;
import cafe.woden.ircclient.logging.model.LogKind;
import java.util.Objects;
import java.util.Set;

/**
 * Line context passed to filter evaluation.
 */
public record FilterContext(
    TargetRef targetRef,
    LogKind kind,
    LogDirection direction,
    String fromNick,
    String text,
    Set<String> tags
) {

  public FilterContext {
    Objects.requireNonNull(targetRef, "targetRef");
    kind = Objects.requireNonNullElse(kind, LogKind.CHAT);
    direction = Objects.requireNonNullElse(direction, LogDirection.IN);
    fromNick = Objects.toString(fromNick, "");
    text = Objects.toString(text, "");
    tags = (tags == null) ? Set.of() : Set.copyOf(tags);
  }

  /** Canonical buffer key used for glob scope matching: {@code <serverId>/<targetKey>}. */
  public String bufferKey() {
    return targetRef.serverId() + "/" + targetRef.key();
  }
}

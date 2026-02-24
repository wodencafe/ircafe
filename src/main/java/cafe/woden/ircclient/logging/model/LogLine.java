package cafe.woden.ircclient.logging.model;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A single persisted transcript line.
 *
 * <p>This is intentionally UI-facing, not protocol-facing: it represents what IRCafe
 * chose to render for a given target at a point in time.
 */
@ValueObject
public record LogLine(
    String serverId,
    String target,
    long tsEpochMs,
    LogDirection direction,
    LogKind kind,
    String fromNick,
    String text,
    boolean outgoingLocalEcho,
    boolean softIgnored,
    String metaJson
) {
  public LogLine {
    Objects.requireNonNull(serverId, "serverId");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(text, "text");
    // metaJson is optional
  }
}

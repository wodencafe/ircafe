package cafe.woden.ircclient.logging.viewer;

import cafe.woden.ircclient.model.LogKind;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** Persisted audit record for a message that was later redacted from the visible transcript. */
@ValueObject
public record ChatRedactionAuditRecord(
    String serverId,
    String target,
    String messageId,
    long redactedAtEpochMs,
    String redactedBy,
    LogKind originalKind,
    String originalFromNick,
    String originalText,
    Long originalEpochMs) {
  public ChatRedactionAuditRecord {
    Objects.requireNonNull(serverId, "serverId");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(messageId, "messageId");
    Objects.requireNonNull(originalKind, "originalKind");
    Objects.requireNonNull(originalText, "originalText");
  }
}

package cafe.woden.ircclient.app.interceptors;

import java.time.Instant;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** A single event captured by an interceptor rule. */
@ValueObject
public record InterceptorHit(
    String serverId,
    String interceptorId,
    String interceptorName,
    Instant at,
    String channel,
    String fromNick,
    String fromHostmask,
    String eventType,
    String reason,
    String message) {
  public InterceptorHit {
    serverId = norm(serverId);
    interceptorId = norm(interceptorId);
    interceptorName = norm(interceptorName);
    channel = norm(channel);
    fromNick = norm(fromNick);
    fromHostmask = norm(fromHostmask);
    eventType = norm(eventType);
    reason = norm(reason);
    message = norm(message);
    if (at == null) at = Instant.now();
    if (fromNick.isEmpty()) fromNick = "?";
    if (reason.isEmpty()) reason = "(rule)";
    if (eventType.isEmpty()) eventType = "message";
  }

  private static String norm(String s) {
    return Objects.toString(s, "").trim();
  }
}

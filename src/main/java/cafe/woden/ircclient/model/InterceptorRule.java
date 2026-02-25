package cafe.woden.ircclient.model;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** Immutable interceptor rule definition. */
@ValueObject
public record InterceptorRule(
    boolean enabled,
    String label,
    String eventTypesCsv,
    InterceptorRuleMode messageMode,
    String messagePattern,
    InterceptorRuleMode nickMode,
    String nickPattern,
    InterceptorRuleMode hostmaskMode,
    String hostmaskPattern) {
  public InterceptorRule {
    label = norm(label);
    eventTypesCsv = norm(eventTypesCsv);
    messagePattern = norm(messagePattern);
    nickPattern = norm(nickPattern);
    hostmaskPattern = norm(hostmaskPattern);

    if (messageMode == null) messageMode = InterceptorRuleMode.LIKE;
    if (nickMode == null) nickMode = InterceptorRuleMode.LIKE;
    if (hostmaskMode == null) hostmaskMode = InterceptorRuleMode.GLOB;
    if (label.isEmpty()) label = "(rule)";
  }

  public boolean hasAnyPattern() {
    return !messagePattern.isBlank() || !nickPattern.isBlank() || !hostmaskPattern.isBlank();
  }

  private static String norm(String s) {
    return Objects.toString(s, "").trim();
  }
}

package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.config.api.Ircv3CapabilityNameResolverPort;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiPredicate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Shared read-marker behavior and availability support for UI and outbound flows. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class Ircv3ReadMarkerFeatureSupport implements Ircv3FeatureAvailabilitySupport {

  private static final String FEATURE_ID = "read-marker";
  private static final String REQUIREMENT_HINT =
      "requires negotiated read-marker or draft/read-marker";
  private static final String NEGOTIATION_UNAVAILABLE_MESSAGE =
      "read-marker is not negotiated on this server.";

  @Qualifier("ircReadMarkerPort")
  @NonNull
  private final IrcReadMarkerPort readMarkerPort;

  @NonNull private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  @NonNull private final Ircv3CapabilityNameResolverPort capabilityNameResolver;

  @Override
  public String featureId() {
    return FEATURE_ID;
  }

  @Override
  public boolean isAvailable(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      return false;
    }
    return backendCapabilityPolicy.supportsReadMarker(sid);
  }

  @Override
  public String requirementHint() {
    return REQUIREMENT_HINT;
  }

  public String negotiationUnavailableMessage() {
    return NEGOTIATION_UNAVAILABLE_MESSAGE;
  }

  public boolean matchesCapabilityName(String capability) {
    return FEATURE_ID.equals(capabilityNameResolver.normalizePreferenceKey(capability));
  }

  public Completable send(String serverId, String target, Instant markerAt) {
    return readMarkerPort.sendReadMarker(serverId, target, markerAt);
  }

  public boolean shouldApplyObservedSource(
      String serverId, String from, BiPredicate<String, String> isFromSelf) {
    String source = Objects.toString(from, "").trim();
    if (source.isEmpty() || "server".equalsIgnoreCase(source)) {
      return true;
    }
    return isFromSelf != null && isFromSelf.test(serverId, source);
  }

  public TargetRef resolveObservedTarget(
      String serverId, String rawTarget, String currentNick, TargetRef fallbackTarget) {
    String target = Objects.toString(rawTarget, "").trim();
    if (!target.isEmpty()) {
      String me = Objects.toString(currentNick, "").trim();
      if (me.isBlank() || !target.equalsIgnoreCase(me)) {
        return new TargetRef(serverId, target);
      }
    }
    return fallbackTarget;
  }

  public long parseObservedMarkerEpochMs(String marker, Instant fallbackAt) {
    Instant fallback = fallbackAt != null ? fallbackAt : Instant.now();
    String raw = Objects.toString(marker, "").trim();
    if (raw.isEmpty() || "*".equals(raw)) {
      return 0L;
    }

    String value = raw;
    int eq = raw.indexOf('=');
    if (eq > 0 && eq < (raw.length() - 1)) {
      String key = raw.substring(0, eq).trim();
      if ("timestamp".equalsIgnoreCase(key)) {
        value = raw.substring(eq + 1).trim();
      }
    }
    if (value.isEmpty() || "*".equals(value)) {
      return 0L;
    }

    try {
      return Instant.parse(value).toEpochMilli();
    } catch (Exception ignored) {
    }

    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0L) {
        return fallback.toEpochMilli();
      }
      if (value.length() <= 10) {
        return Math.multiplyExact(parsed, 1000L);
      }
      return parsed;
    } catch (Exception ignored) {
      return fallback.toEpochMilli();
    }
  }
}

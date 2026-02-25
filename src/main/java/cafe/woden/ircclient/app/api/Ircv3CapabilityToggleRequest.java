package cafe.woden.ircclient.app.api;

import java.util.Locale;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record Ircv3CapabilityToggleRequest(String serverId, String capability, boolean enabled) {
  public Ircv3CapabilityToggleRequest {
    serverId = Objects.requireNonNull(serverId, "serverId").trim();
    capability = Objects.requireNonNull(capability, "capability").trim().toLowerCase(Locale.ROOT);
    if (serverId.isEmpty()) throw new IllegalArgumentException("serverId is blank");
    if (capability.isEmpty()) throw new IllegalArgumentException("capability is blank");
  }
}

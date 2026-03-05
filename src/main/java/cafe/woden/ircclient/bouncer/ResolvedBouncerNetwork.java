package cafe.woden.ircclient.bouncer;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** Canonical derived data for a discovered bouncer network. */
@ValueObject
public record ResolvedBouncerNetwork(
    String serverId, String loginUser, String displayName, String autoConnectName) {

  public ResolvedBouncerNetwork {
    serverId = requireNonBlank(serverId, "serverId");
    loginUser = requireNonBlank(loginUser, "loginUser");
    displayName = requireNonBlank(displayName, "displayName");
    autoConnectName = requireNonBlank(autoConnectName, "autoConnectName");
  }

  private static String requireNonBlank(String value, String field) {
    String v = Objects.toString(value, "").trim();
    if (v.isEmpty()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return v;
  }
}

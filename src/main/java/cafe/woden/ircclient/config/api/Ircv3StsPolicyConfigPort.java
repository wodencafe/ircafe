package cafe.woden.ircclient.config.api;

import java.util.Map;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted IRCv3 STS policy state. */
@ApplicationLayer
public interface Ircv3StsPolicyConfigPort {

  /** Persisted IRCv3 STS policy snapshot under {@code ircafe.ircv3.stsPolicies.<host>}. */
  record StsPolicySnapshot(
      long expiresAtEpochMs,
      Integer port,
      boolean preload,
      long durationSeconds,
      String rawValue) {}

  Map<String, StsPolicySnapshot> readIrcv3StsPolicies();

  void rememberIrcv3StsPolicy(
      String host,
      long expiresAtEpochMs,
      Integer port,
      boolean preload,
      long durationSeconds,
      String rawValue);

  void forgetIrcv3StsPolicy(String host);
}

package cafe.woden.ircclient.irc.pircbotx.capability;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Chooses the most appropriate SASL mechanism for the current credentials and server offer. */
final class PircbotxSaslMechanismSelector {

  String choose(String configuredMechanism, String username, String secret, Set<String> offered) {
    String cfg = Objects.toString(configuredMechanism, "PLAIN").trim().toUpperCase(Locale.ROOT);
    if (cfg.isBlank()) {
      cfg = "PLAIN";
    }

    if (!"AUTO".equals(cfg)) {
      return cfg;
    }

    boolean hasUser = username != null && !username.isBlank();
    boolean hasSecret = secret != null && !secret.isBlank();
    Set<String> offeredMechanisms = offered == null ? Set.of() : offered;

    if (offeredMechanisms.isEmpty()) {
      if (!hasSecret) {
        return "EXTERNAL";
      }
      return hasUser ? "PLAIN" : null;
    }

    if (!hasSecret) {
      return offeredMechanisms.contains("EXTERNAL") ? "EXTERNAL" : null;
    }

    if (!hasUser) {
      return null;
    }

    for (String preferred : List.of("SCRAM-SHA-256", "SCRAM-SHA-1", "PLAIN")) {
      if (offeredMechanisms.contains(preferred)) {
        return preferred;
      }
    }

    return "PLAIN";
  }
}

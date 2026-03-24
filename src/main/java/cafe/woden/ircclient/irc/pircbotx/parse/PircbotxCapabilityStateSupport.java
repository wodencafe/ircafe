package cafe.woden.ircclient.irc.pircbotx.parse;

import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies tracked CAP ACK/DEL state changes to a connection. */
public final class PircbotxCapabilityStateSupport {

  private static final Logger log = LoggerFactory.getLogger(PircbotxCapabilityStateSupport.class);

  private final String serverId;
  private final PircbotxConnectionState conn;

  public PircbotxCapabilityStateSupport(String serverId, PircbotxConnectionState conn) {
    this.serverId = serverId;
    this.conn = conn;
  }

  public void apply(String capName, boolean enabled, String sourceAction) {
    String normalized = capName.toLowerCase(Locale.ROOT);
    if (conn.updateTrackedCapability(normalized, enabled)) {
      log.debug(
          "[{}] CAP {}: {} {}",
          serverId,
          sourceAction,
          normalized,
          enabled ? "enabled" : "disabled");
    }
  }
}

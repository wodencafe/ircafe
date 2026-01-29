package cafe.woden.ircclient.app;

import java.util.Objects;

/**
 * A UI-originated action targeting a specific nick (e.g., WHOIS, CTCP PING).
 *
 */
public record UserActionRequest(TargetRef contextTarget, String nick, Action action) {

  public enum Action {
    OPEN_QUERY,
    WHOIS,
    CTCP_VERSION,
    CTCP_PING
  }

  public UserActionRequest {
    Objects.requireNonNull(action, "action");
    nick = nick == null ? "" : nick.trim();
  }
}

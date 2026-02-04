package cafe.woden.ircclient.app;

import java.util.Objects;

public record UserActionRequest(TargetRef contextTarget, String nick, Action action) {

  public enum Action {
    OPEN_QUERY,
    WHOIS,
    CTCP_VERSION,
    CTCP_PING,
    CTCP_TIME
  }

  public UserActionRequest {
    Objects.requireNonNull(action, "action");
    nick = nick == null ? "" : nick.trim();
  }
}

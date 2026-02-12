package cafe.woden.ircclient.irc.znc;

import java.util.Objects;

/**
 * Parses ZNC-style usernames.
 *
 * <p>Common forms:
 * <ul>
 *   <li>{@code user}</li>
 *   <li>{@code user/network}</li>
 *   <li>{@code user@clientid/network}</li>
 * </ul>
 */
public record ZncLoginParts(String baseUser, String clientId, String network) {

  public ZncLoginParts {
    if (baseUser == null) baseUser = "";
    if (clientId == null) clientId = "";
    if (network == null) network = "";
  }

  public boolean hasNetwork() {
    return network != null && !network.isBlank();
  }

  public boolean hasClientId() {
    return clientId != null && !clientId.isBlank();
  }

  public static ZncLoginParts parse(String login) {
    String s = Objects.toString(login, "").trim();
    if (s.isBlank()) return new ZncLoginParts("", "", "");

    // Split on first '/': user[@client]/network
    String left;
    String net;
    int slash = s.indexOf('/');
    if (slash >= 0) {
      left = s.substring(0, slash).trim();
      net = s.substring(slash + 1).trim();
    } else {
      left = s.trim();
      net = "";
    }

    // Split on first '@': user@client
    String user;
    String client;
    int at = left.indexOf('@');
    if (at >= 0) {
      user = left.substring(0, at).trim();
      client = left.substring(at + 1).trim();
    } else {
      user = left.trim();
      client = "";
    }

    return new ZncLoginParts(user, client, net);
  }

  /**
   * Merge two parses, preferring non-empty fields from {@code this}.
   */
  public ZncLoginParts mergePreferThis(ZncLoginParts other) {
    if (other == null) return this;
    String u = (this.baseUser != null && !this.baseUser.isBlank()) ? this.baseUser : other.baseUser;
    String c = (this.clientId != null && !this.clientId.isBlank()) ? this.clientId : other.clientId;
    String n = (this.network != null && !this.network.isBlank()) ? this.network : other.network;
    return new ZncLoginParts(u, c, n);
  }
}

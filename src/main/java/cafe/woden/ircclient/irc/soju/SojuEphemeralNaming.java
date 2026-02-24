package cafe.woden.ircclient.irc.soju;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;

/**
 * Naming + username rules for ephemeral Soju network entries.
 *
 * <p>The configured Soju host is treated as the "Bouncer Control" session. Discovered networks are
 * represented as ephemeral servers and are not persisted.
 */
public final class SojuEphemeralNaming {

  public static final String EPHEMERAL_ID_PREFIX = "soju:";
  public static final String DEFAULT_CLIENT_SUFFIX = "ircafe";

  private SojuEphemeralNaming() {}

  /**
   * Derived identifying info for an ephemeral Soju network server.
   *
   * @param serverId deterministic ephemeral id: {@code soju:<bouncerServerId>:<netId>}
   * @param loginUser login/SASL username: {@code <baseUser>/<networkName>@ircafe}
   * @param networkName sanitized network name used in the username
   */
  public record Derived(String serverId, String loginUser, String networkName) {}

  public static Derived derive(IrcProperties.Server bouncerServer, SojuNetwork network) {
    return derive(bouncerServer, network, DEFAULT_CLIENT_SUFFIX);
  }

  public static Derived derive(
      IrcProperties.Server bouncerServer, SojuNetwork network, String clientSuffix) {
    Objects.requireNonNull(bouncerServer, "bouncerServer");
    Objects.requireNonNull(network, "network");
    String bouncerId = normalize(bouncerServer.id());
    String netId = normalize(network.netId());

    if (bouncerId == null) {
      throw new IllegalArgumentException("bouncerServer.id is required");
    }
    if (netId == null) {
      throw new IllegalArgumentException("network.netId is required");
    }

    String networkName = PircbotxSojuParsers.sanitizeNetworkName(network.name());
    if (networkName.isBlank()) {
      networkName = "net-" + netId;
    }

    String baseUser = normalizeBaseUser(pickBaseUser(bouncerServer));
    if (baseUser == null) {
      throw new IllegalArgumentException(
          "Soju requires a base username; set irc.servers[].login or irc.servers[].sasl.username for server '"
              + bouncerId
              + "'");
    }

    String suffix = normalize(clientSuffix);
    if (suffix == null) suffix = DEFAULT_CLIENT_SUFFIX;

    String loginUser = baseUser + "/" + networkName + "@" + suffix;
    String serverId = EPHEMERAL_ID_PREFIX + bouncerId + ":" + netId;
    return new Derived(serverId, loginUser, networkName);
  }

  /** Select a base username from the bouncer server config (SASL username preferred). */
  public static String pickBaseUser(IrcProperties.Server bouncerServer) {
    if (bouncerServer == null) return null;
    String saslUser = null;
    if (bouncerServer.sasl() != null) {
      saslUser = normalize(bouncerServer.sasl().username());
    }
    String login = normalize(bouncerServer.login());
    return (saslUser != null) ? saslUser : login;
  }

  /**
   * Normalize a base username by stripping any existing network selection or client suffix.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code user/libera} -> {@code user}
   *   <li>{@code user@laptop} -> {@code user}
   *   <li>{@code user/libera@laptop} -> {@code user}
   * </ul>
   */
  public static String normalizeBaseUser(String user) {
    String u = normalize(user);
    if (u == null) return null;

    int slash = u.indexOf('/');
    if (slash >= 0) {
      u = u.substring(0, slash);
    }
    int at = u.indexOf('@');
    if (at >= 0) {
      u = u.substring(0, at);
    }

    u = u.trim();
    return u.isEmpty() ? null : u;
  }

  private static String normalize(String s) {
    String v = Objects.toString(s, "").trim();
    return v.isEmpty() ? null : v;
  }
}

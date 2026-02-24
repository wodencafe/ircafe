package cafe.woden.ircclient.irc.znc;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.Locale;
import java.util.Objects;

/** Naming + username rules for ephemeral ZNC network entries. */
public final class ZncEphemeralNaming {

  public static final String EPHEMERAL_ID_PREFIX = "znc:";

  private ZncEphemeralNaming() {}

  /**
   * Derived identifying info for an ephemeral ZNC network server.
   *
   * @param serverId deterministic ephemeral id: {@code znc:<bouncerServerId>:<networkKey>}
   * @param loginUser login/SASL username: {@code <user>[@clientId]/<networkName>}
   * @param networkKey normalized stable key (lowercased, sanitized) used in ids and persistence
   */
  public record Derived(String serverId, String loginUser, String networkKey) {}

  public static Derived derive(IrcProperties.Server bouncerServer, ZncNetwork network) {
    Objects.requireNonNull(bouncerServer, "bouncerServer");
    Objects.requireNonNull(network, "network");

    String bouncerId = normalize(bouncerServer.id());
    if (bouncerId == null) {
      throw new IllegalArgumentException("bouncerServer.id is required");
    }

    ZncLoginParts base = ZncLoginParts.parse(pickBaseLoginUser(bouncerServer));
    String baseUser = normalize(base.baseUser());
    if (baseUser == null) {
      throw new IllegalArgumentException(
          "ZNC requires a base username; set irc.servers[].login or irc.servers[].sasl.username for server '"
              + bouncerId
              + "'");
    }

    String clientId = normalize(base.clientId());

    String networkSegment = sanitizeNetworkSegment(network.name());
    if (networkSegment.isBlank()) {
      networkSegment = "network";
    }

    String networkKey = normalizeNetworkKey(networkSegment);
    if (networkKey.isBlank()) {
      // Should not happen after fallback, but keep it defensive.
      networkKey = "network";
    }

    String left = baseUser;
    if (clientId != null) {
      left = left + "@" + clientId;
    }

    String loginUser = left + "/" + networkSegment;
    String serverId = EPHEMERAL_ID_PREFIX + bouncerId + ":" + networkKey;
    return new Derived(serverId, loginUser, networkKey);
  }

  /** Select the base login for ZNC connections (SASL username preferred). */
  public static String pickBaseLoginUser(IrcProperties.Server bouncerServer) {
    if (bouncerServer == null) return null;
    String saslUser = null;
    if (bouncerServer.sasl() != null) {
      saslUser = normalize(bouncerServer.sasl().username());
    }
    String login = normalize(bouncerServer.login());
    return (saslUser != null) ? saslUser : login;
  }

  /**
   * Normalize a network key used for ids/persistence.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>Trim
   *   <li>Replace non {@code [A-Za-z0-9._-]} with {@code _}
   *   <li>Lowercase
   * </ul>
   */
  public static String normalizeNetworkKey(String networkName) {
    String s = sanitizeNetworkSegment(networkName);
    return s.toLowerCase(Locale.ROOT);
  }

  /** Sanitize a ZNC network name to safe characters for usernames. */
  public static String sanitizeNetworkSegment(String networkName) {
    String s = Objects.toString(networkName, "").trim();
    if (s.isEmpty()) return "";

    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean ok =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '.'
              || c == '_'
              || c == '-';
      out.append(ok ? c : '_');
    }

    // Trim leading/trailing underscores introduced by sanitization.
    int start = 0;
    int end = out.length();
    while (start < end && out.charAt(start) == '_') start++;
    while (end > start && out.charAt(end - 1) == '_') end--;

    String v = out.substring(start, end);
    return v;
  }

  private static String normalize(String s) {
    String v = Objects.toString(s, "").trim();
    return v.isEmpty() ? null : v;
  }
}

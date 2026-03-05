package cafe.woden.ircclient.bouncer;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Generic fallback mapping strategy for bouncer protocols exposing standard discovery events. */
@Component
public class GenericBouncerNetworkMappingStrategy implements BouncerNetworkMappingStrategy {

  public static final String BACKEND_ID = "generic";
  public static final String DEFAULT_LOGIN_TEMPLATE = "{base}/{network}";
  private static final String EPHEMERAL_ID_PREFIX = "bouncer:";
  private static final boolean DEFAULT_PREFER_LOGIN_HINT = true;

  private final RuntimeConfigStore runtimeConfig;

  public GenericBouncerNetworkMappingStrategy(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  @Override
  public ResolvedBouncerNetwork resolveNetwork(
      IrcProperties.Server bouncer, BouncerDiscoveredNetwork network) {
    String originId = requireNonBlank(network.originServerId(), "originServerId");
    String networkId = sanitizeKey(requireNonBlank(network.networkId(), "networkId"));

    String baseLogin = pickBaseLoginUser(bouncer);
    String displayName = requireNonBlank(network.displayName(), "displayName");
    String autoConnectName =
        requireNonBlank(
            network.autoConnectName() == null ? network.displayName() : network.autoConnectName(),
            "autoConnectName");

    // Best effort generic login shaping: user/network when available.
    String loginUser = baseLogin;
    if (loginUser != null && !loginUser.isBlank()) {
      loginUser = loginUser + "/" + sanitizeLoginSegment(displayName);
    }

    Map<String, String> attrs = network.attributes();
    String loginTemplate = attrs == null ? null : normalize(attrs.get("loginTemplate"));
    if (loginTemplate == null) {
      loginTemplate = runtimeConfig.readGenericBouncerLoginTemplate(DEFAULT_LOGIN_TEMPLATE);
    }
    if (loginTemplate != null && baseLogin != null) {
      String templated =
          loginTemplate
              .replace("{base}", baseLogin)
              .replace("{network}", sanitizeLoginSegment(displayName));
      String normalized = normalize(templated);
      if (normalized != null) {
        loginUser = normalized;
      }
    }
    String hintedLoginUser = normalize(network.loginUserHint());
    if (runtimeConfig.readGenericBouncerPreferLoginHint(DEFAULT_PREFER_LOGIN_HINT)
        && hintedLoginUser != null) {
      loginUser = hintedLoginUser;
    }
    String explicitLoginUser = attrs == null ? null : normalize(attrs.get("loginUser"));
    if (explicitLoginUser != null) {
      loginUser = explicitLoginUser;
    }
    if (loginUser == null) {
      throw new IllegalArgumentException("generic bouncer mapping requires a login user");
    }

    String serverId = EPHEMERAL_ID_PREFIX + originId + ":" + networkId;
    return new ResolvedBouncerNetwork(serverId, loginUser, displayName, autoConnectName);
  }

  @Override
  public IrcProperties.Server buildEphemeralServer(
      IrcProperties.Server bouncer,
      ResolvedBouncerNetwork resolved,
      List<String> autoJoinChannels) {
    IrcProperties.Server.Sasl sasl = bouncer.sasl();

    IrcProperties.Server.Sasl updatedSasl =
        new IrcProperties.Server.Sasl(
            sasl.enabled(),
            resolved.loginUser(),
            sasl.password(),
            sasl.mechanism(),
            sasl.disconnectOnFailure());

    return new IrcProperties.Server(
        resolved.serverId(),
        bouncer.host(),
        bouncer.port(),
        bouncer.tls(),
        bouncer.serverPassword(),
        bouncer.nick(),
        resolved.loginUser(),
        bouncer.realName(),
        updatedSasl,
        bouncer.nickserv(),
        autoJoinChannels == null ? List.of() : List.copyOf(autoJoinChannels),
        List.of(),
        bouncer.proxy(),
        bouncer.backend());
  }

  @Override
  public String networkDebugId(BouncerDiscoveredNetwork network) {
    return "networkId=" + network.networkId();
  }

  private static String pickBaseLoginUser(IrcProperties.Server bouncerServer) {
    if (bouncerServer == null) return null;
    String saslUser = null;
    if (bouncerServer.sasl() != null) {
      saslUser = normalize(bouncerServer.sasl().username());
    }
    String login = normalize(bouncerServer.login());
    return saslUser != null ? saslUser : login;
  }

  private static String sanitizeLoginSegment(String value) {
    String raw = requireNonBlank(value, "displayName");
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      boolean ok =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '.'
              || c == '_'
              || c == '-';
      out.append(ok ? c : '_');
    }
    String v = out.toString().trim();
    return v.isEmpty() ? "network" : v;
  }

  private static String sanitizeKey(String value) {
    String raw = requireNonBlank(value, "networkId");
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      boolean ok =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '.'
              || c == '_'
              || c == '-';
      out.append(ok ? c : '_');
    }
    String v = out.toString().trim().toLowerCase(Locale.ROOT);
    return v.isEmpty() ? "network" : v;
  }

  private static String requireNonBlank(String value, String field) {
    String v = normalize(value);
    if (v == null) {
      throw new IllegalArgumentException(field + " is required");
    }
    return v;
  }

  private static String normalize(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? null : v;
  }
}

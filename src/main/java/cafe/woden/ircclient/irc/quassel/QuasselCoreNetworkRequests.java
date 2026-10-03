package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.containsCrlf;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates network requests and constructs their core-compatible wire payloads. */
final class QuasselCoreNetworkRequests {
  private QuasselCoreNetworkRequests() {}

  private static final String DEFAULT_NETWORK_CODEC = "UTF-8";

  static QuasselCoreNetworkCreateRequest normalizeCreateRequest(
      QuasselCoreNetworkCreateRequest request) {
    String networkName = Objects.toString(request.networkName(), "").trim();
    if (networkName.isEmpty()) {
      throw new IllegalArgumentException("network name is required");
    }
    if (containsCrlf(networkName)) {
      throw new IllegalArgumentException("network name contains unsupported newlines");
    }

    String serverHost = Objects.toString(request.serverHost(), "").trim();
    if (serverHost.isEmpty()) {
      throw new IllegalArgumentException("server host is required");
    }
    if (containsCrlf(serverHost)) {
      throw new IllegalArgumentException("server host contains unsupported newlines");
    }

    boolean useTls = request.useTls();
    int port = request.serverPort();
    if (port <= 0) {
      port = useTls ? 6697 : 6667;
    }
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("server port must be 1-65535");
    }

    String serverPassword = Objects.toString(request.serverPassword(), "");
    if (containsCrlf(serverPassword)) {
      throw new IllegalArgumentException("server password contains unsupported newlines");
    }

    Integer identityId = request.identityId();
    if (identityId != null && identityId.intValue() <= 0) {
      throw new IllegalArgumentException("identity id must be > 0");
    }

    List<String> autoJoin = new ArrayList<>();
    if (request.autoJoinChannels() != null) {
      for (String entry : request.autoJoinChannels()) {
        String token = Objects.toString(entry, "").trim();
        if (token.isEmpty()) continue;
        if (containsCrlf(token)) {
          throw new IllegalArgumentException("auto-join channel contains unsupported newlines");
        }
        autoJoin.add(token);
      }
    }

    return new QuasselCoreNetworkCreateRequest(
        networkName,
        serverHost,
        port,
        useTls,
        serverPassword,
        request.verifyTls(),
        identityId,
        autoJoin.isEmpty() ? List.of() : List.copyOf(autoJoin));
  }

  static QuasselCoreNetworkUpdateRequest normalizeUpdateRequest(
      QuasselCoreNetworkUpdateRequest request) {
    String networkName = Objects.toString(request.networkName(), "").trim();
    if (containsCrlf(networkName)) {
      throw new IllegalArgumentException("network name contains unsupported newlines");
    }

    String serverHost = Objects.toString(request.serverHost(), "").trim();
    if (serverHost.isEmpty()) {
      throw new IllegalArgumentException("server host is required");
    }
    if (containsCrlf(serverHost)) {
      throw new IllegalArgumentException("server host contains unsupported newlines");
    }

    boolean useTls = request.useTls();
    int port = request.serverPort();
    if (port <= 0) {
      port = useTls ? 6697 : 6667;
    }
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("server port must be 1-65535");
    }

    String serverPassword = Objects.toString(request.serverPassword(), "");
    if (containsCrlf(serverPassword)) {
      throw new IllegalArgumentException("server password contains unsupported newlines");
    }

    Integer identityId = request.identityId();
    if (identityId != null && identityId.intValue() <= 0) {
      throw new IllegalArgumentException("identity id must be > 0");
    }

    return new QuasselCoreNetworkUpdateRequest(
        networkName,
        serverHost,
        port,
        useTls,
        serverPassword,
        request.verifyTls(),
        identityId,
        request.enabled());
  }

  static Map<String, Object> networkInfoPayload(
      int networkId,
      int identityId,
      QuasselCoreNetworkCreateRequest request,
      boolean enabled,
      boolean includeLegacyAliases) {
    byte[] codecForServer = DEFAULT_NETWORK_CODEC.getBytes(StandardCharsets.UTF_8);
    byte[] codecForEncoding = DEFAULT_NETWORK_CODEC.getBytes(StandardCharsets.UTF_8);
    byte[] codecForDecoding = DEFAULT_NETWORK_CODEC.getBytes(StandardCharsets.UTF_8);
    LinkedHashMap<String, Object> info = new LinkedHashMap<>();
    info.put("NetworkId", new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", networkId));
    info.put("NetworkName", request.networkName());
    // Upstream NetworkInfo deserialization reads "Identity" as IdentityId user-type.
    info.put("Identity", new QuasselCoreDatastreamCodec.UserTypeValue("IdentityId", identityId));
    info.put("IdentityId", identityId);
    info.put("identity", identityId);
    info.put("CodecForServer", codecForServer);
    info.put("CodecForEncoding", codecForEncoding);
    info.put("CodecForDecoding", codecForDecoding);
    info.put(
        "ServerList",
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue(
                "Network::Server", buildQuasselServerPayload(request))));
    info.put("Perform", List.of());
    info.put("SkipCaps", List.of());
    info.put("AutoIdentifyService", "NickServ");
    info.put("AutoIdentifyPassword", "");
    info.put("SaslAccount", "");
    info.put("SaslPassword", "");
    info.put("SaslMechanism", "");
    info.put("MessageRateBurstSize", 5);
    info.put("MessageRateDelay", 2200);
    info.put("AutoReconnectInterval", 60);
    info.put("AutoReconnectRetries", 20);
    info.put("UseRandomServer", false);
    info.put("UseAutoIdentify", false);
    info.put("UseSasl", false);
    info.put("UseAutoReconnect", true);
    info.put("UnlimitedReconnectRetries", true);
    info.put("UseCustomMessageRate", false);
    info.put("UnlimitedMessageRate", false);
    info.put("RejoinChannels", true);
    info.put("AutoAwayActive", false);
    if (includeLegacyAliases) {
      // Preserve aliases for create flows where older cores expect lower-cased keys.
      info.put("networkId", networkId);
      info.put("networkName", request.networkName());
      info.put("identityId", identityId);
      info.put("codecForServer", DEFAULT_NETWORK_CODEC);
      info.put("codecForEncoding", DEFAULT_NETWORK_CODEC);
      info.put("codecForDecoding", DEFAULT_NETWORK_CODEC);
      info.put("perform", List.of());
      info.put("skipCaps", List.of());
      info.put("autoIdentifyService", "NickServ");
      info.put("autoIdentifyPassword", "");
      info.put("saslAccount", "");
      info.put("saslPassword", "");
      info.put("saslMechanism", "");
      info.put("msgRateBurstSize", 5);
      info.put("msgRateMessageDelay", 2200);
      info.put("autoReconnectInterval", 60);
      info.put("autoReconnectRetries", 20);
      info.put("useRandomServer", false);
      info.put("useAutoIdentify", false);
      info.put("useSasl", false);
      info.put("useAutoReconnect", true);
      info.put("unlimitedReconnectRetries", true);
      info.put("useCustomMessageRate", false);
      info.put("unlimitedMessageRate", false);
      info.put("rejoinChannels", true);
      info.put("autoAwayActive", false);
      info.put("isEnabled", enabled);
      info.put("isInitialized", true);
    }
    return Collections.unmodifiableMap(info);
  }

  static Map<String, Object> networkInfoUpdatePayload(
      int networkId,
      int identityId,
      String networkName,
      QuasselCoreNetworkUpdateRequest request,
      boolean enabled) {
    QuasselCoreNetworkCreateRequest shape =
        new QuasselCoreNetworkCreateRequest(
            networkName,
            request.serverHost(),
            request.serverPort(),
            request.useTls(),
            request.serverPassword(),
            request.verifyTls(),
            identityId,
            List.of());
    Map<String, Object> base =
        networkInfoPayload(
            networkId, identityId, shape, enabled, /* includeLegacyAliases= */ false);
    return Collections.unmodifiableMap(new LinkedHashMap<>(base));
  }

  private static Map<String, Object> buildQuasselServerPayload(
      QuasselCoreNetworkCreateRequest request) {
    LinkedHashMap<String, Object> server = new LinkedHashMap<>();
    server.put("Host", request.serverHost());
    server.put("Port", request.serverPort());
    server.put("Password", Objects.toString(request.serverPassword(), ""));
    server.put("UseSSL", request.useTls());
    server.put("SslVerify", request.verifyTls());
    server.put("SslVersion", 0);
    server.put("UseProxy", false);
    server.put("ProxyType", 0);
    server.put("ProxyHost", "localhost");
    server.put("ProxyPort", 0);
    server.put("ProxyUser", "");
    server.put("ProxyPass", "");
    server.put("sslVerify", request.verifyTls());
    server.put("sslVersion", 0);
    // Legacy aliases retained for compatibility with existing parser fallback paths.
    server.put("hostname", request.serverHost());
    server.put("server", request.serverHost());
    server.put("host", request.serverHost());
    server.put("port", request.serverPort());
    server.put("password", Objects.toString(request.serverPassword(), ""));
    server.put("useSSL", request.useTls());
    server.put("useProxy", false);
    server.put("proxyType", 0);
    server.put("proxyHost", "localhost");
    server.put("proxyPort", 0);
    server.put("proxyUser", "");
    server.put("proxyPass", "");
    return Collections.unmodifiableMap(server);
  }
}

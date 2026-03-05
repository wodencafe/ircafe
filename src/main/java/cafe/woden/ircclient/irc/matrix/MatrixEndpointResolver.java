package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Resolves canonical Matrix endpoints from current server configuration. */
final class MatrixEndpointResolver {

  private MatrixEndpointResolver() {}

  public static URI homeserverBaseUri(IrcProperties.Server server) {
    IrcProperties.Server cfg = Objects.requireNonNull(server, "server");
    String rawHost = normalize(cfg.host());
    if (rawHost.isEmpty()) {
      throw new IllegalArgumentException("host is blank");
    }

    try {
      if (looksLikeAbsoluteUri(rawHost)) {
        return fromAbsoluteUri(rawHost);
      }
      return fromHostPort(rawHost, cfg);
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("invalid host/URL: " + rawHost, ex);
    }
  }

  public static URI clientApiBaseUri(IrcProperties.Server server) {
    URI base = homeserverBaseUri(server);
    String path = appendPath(base.getPath(), "_matrix/client/v3");
    return rebuild(base, path);
  }

  public static URI versionsUri(IrcProperties.Server server) {
    URI apiBase = clientApiBaseUri(server);
    String path = appendPath(apiBase.getPath(), "versions");
    return rebuild(apiBase, path);
  }

  public static URI whoamiUri(IrcProperties.Server server) {
    URI apiBase = clientApiBaseUri(server);
    String path = appendPath(apiBase.getPath(), "account/whoami");
    return rebuild(apiBase, path);
  }

  public static URI userProfileUri(IrcProperties.Server server, String userId) {
    URI apiBase = clientApiBaseUri(server);
    String uid = validatePathSegment(userId, "userId");
    String path = appendPath(apiBase.getPath(), "profile/" + uid);
    return rebuild(apiBase, path);
  }

  public static URI userDisplayNameUri(IrcProperties.Server server, String userId) {
    URI apiBase = clientApiBaseUri(server);
    String uid = validatePathSegment(userId, "userId");
    String path = appendPath(apiBase.getPath(), "profile/" + uid + "/displayname");
    return rebuild(apiBase, path);
  }

  public static URI roomSendMessageUri(
      IrcProperties.Server server, String roomId, String transactionId) {
    URI apiBase = clientApiBaseUri(server);
    String rid = validatePathSegment(roomId, "roomId");
    String tid = validatePathSegment(transactionId, "transactionId");
    String path = appendPath(apiBase.getPath(), "rooms/" + rid + "/send/m.room.message/" + tid);
    return rebuild(apiBase, path);
  }

  public static URI syncUri(IrcProperties.Server server, String sinceToken, int timeoutMs) {
    URI apiBase = clientApiBaseUri(server);
    String path = appendPath(apiBase.getPath(), "sync");
    String since = normalize(sinceToken);
    int timeout = Math.max(0, timeoutMs);
    StringBuilder query = new StringBuilder("timeout=").append(timeout);
    if (!since.isEmpty()) {
      query.append("&since=").append(encodeQueryParam(since));
    }
    return rebuild(apiBase, path, query.toString());
  }

  public static URI createRoomUri(IrcProperties.Server server) {
    URI apiBase = clientApiBaseUri(server);
    String path = appendPath(apiBase.getPath(), "createRoom");
    return rebuild(apiBase, path);
  }

  public static URI joinRoomUri(IrcProperties.Server server, String roomIdOrAlias) {
    URI apiBase = clientApiBaseUri(server);
    String target = validateJoinTarget(roomIdOrAlias);
    String path = appendPath(apiBase.getPath(), "join/" + target);
    return rebuild(apiBase, path);
  }

  public static URI leaveRoomUri(IrcProperties.Server server, String roomId) {
    URI apiBase = clientApiBaseUri(server);
    String rid = validatePathSegment(roomId, "roomId");
    String path = appendPath(apiBase.getPath(), "rooms/" + rid + "/leave");
    return rebuild(apiBase, path);
  }

  public static URI roomJoinedMembersUri(IrcProperties.Server server, String roomId) {
    URI apiBase = clientApiBaseUri(server);
    String rid = validatePathSegment(roomId, "roomId");
    String path = appendPath(apiBase.getPath(), "rooms/" + rid + "/joined_members");
    return rebuild(apiBase, path);
  }

  public static URI userPresenceStatusUri(IrcProperties.Server server, String userId) {
    URI apiBase = clientApiBaseUri(server);
    String uid = validatePathSegment(userId, "userId");
    String path = appendPath(apiBase.getPath(), "presence/" + uid + "/status");
    return rebuild(apiBase, path);
  }

  public static URI roomTypingUri(IrcProperties.Server server, String roomId, String userId) {
    URI apiBase = clientApiBaseUri(server);
    String rid = validatePathSegment(roomId, "roomId");
    String uid = validatePathSegment(userId, "userId");
    String path = appendPath(apiBase.getPath(), "rooms/" + rid + "/typing/" + uid);
    return rebuild(apiBase, path);
  }

  public static URI roomReadMarkersUri(IrcProperties.Server server, String roomId) {
    URI apiBase = clientApiBaseUri(server);
    String rid = validatePathSegment(roomId, "roomId");
    String path = appendPath(apiBase.getPath(), "rooms/" + rid + "/read_markers");
    return rebuild(apiBase, path);
  }

  public static URI roomAliasDirectoryUri(IrcProperties.Server server, String roomAlias) {
    URI apiBase = clientApiBaseUri(server);
    String alias = validateJoinTarget(roomAlias);
    String path = appendPath(apiBase.getPath(), "directory/room/" + alias);
    return rebuild(apiBase, path);
  }

  public static URI roomMessagesUri(
      IrcProperties.Server server, String roomId, String fromToken, String toToken, int limit) {
    URI apiBase = clientApiBaseUri(server);
    String rid = validatePathSegment(roomId, "roomId");
    String from = normalize(fromToken);
    if (from.isEmpty()) {
      throw new IllegalArgumentException("fromToken is blank");
    }
    String to = normalize(toToken);
    int boundedLimit = Math.max(1, Math.min(limit, 200));

    String path = appendPath(apiBase.getPath(), "rooms/" + rid + "/messages");
    StringBuilder query = new StringBuilder();
    query.append("from=").append(encodeQueryParam(from));
    query.append("&dir=b");
    query.append("&limit=").append(boundedLimit);
    if (!to.isEmpty()) {
      query.append("&to=").append(encodeQueryParam(to));
    }
    return rebuild(apiBase, path, query.toString());
  }

  private static URI fromAbsoluteUri(String raw) throws URISyntaxException {
    URI parsed = new URI(raw);
    String scheme = normalize(parsed.getScheme()).toLowerCase(java.util.Locale.ROOT);
    if (!"https".equals(scheme) && !"http".equals(scheme)) {
      throw new IllegalArgumentException("scheme must be http or https");
    }

    String host = normalize(parsed.getHost());
    if (host.isEmpty()) {
      throw new IllegalArgumentException("host is blank");
    }

    int port = parsed.getPort();
    if (port <= 0) {
      port = defaultPortForScheme(scheme);
    }

    return new URI(
        scheme, null, stripIpv6Brackets(host), port, normalizePath(parsed.getPath()), null, null);
  }

  private static URI fromHostPort(String rawHost, IrcProperties.Server server)
      throws URISyntaxException {
    String host = rawHost;
    String path = "/";

    int slash = rawHost.indexOf('/');
    if (slash >= 0) {
      host = normalize(rawHost.substring(0, slash));
      path = rawHost.substring(slash);
    }

    if (host.isEmpty()) {
      throw new IllegalArgumentException("host is blank");
    }

    String scheme = server.tls() ? "https" : "http";
    int port = server.port();
    if (port <= 0) {
      port = defaultPortForScheme(scheme);
    }

    return new URI(scheme, null, stripIpv6Brackets(host), port, normalizePath(path), null, null);
  }

  private static URI rebuild(URI base, String path) {
    return rebuild(base, path, null);
  }

  private static URI rebuild(URI base, String path, String query) {
    try {
      String q = normalize(query);
      if (q.isEmpty()) q = null;
      return new URI(
          base.getScheme(), null, base.getHost(), base.getPort(), normalizePath(path), q, null);
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("invalid matrix endpoint path: " + path, ex);
    }
  }

  private static String appendPath(String basePath, String suffix) {
    String base = normalizePath(basePath);
    String tail = normalize(suffix);
    if (tail.startsWith("/")) {
      tail = tail.substring(1);
    }
    if (tail.isEmpty()) {
      return base;
    }
    if ("/".equals(base)) {
      return "/" + tail;
    }
    return base + "/" + tail;
  }

  private static int defaultPortForScheme(String scheme) {
    return "https".equalsIgnoreCase(scheme) ? 443 : 80;
  }

  private static boolean looksLikeAbsoluteUri(String value) {
    return value.contains("://");
  }

  private static String stripIpv6Brackets(String host) {
    String h = normalize(host);
    if (h.startsWith("[") && h.endsWith("]") && h.length() > 2) {
      return h.substring(1, h.length() - 1);
    }
    return h;
  }

  private static String normalizePath(String path) {
    String p = normalize(path);
    if (p.isEmpty()) return "/";
    if (!p.startsWith("/")) {
      p = "/" + p;
    }
    while (p.contains("//")) {
      p = p.replace("//", "/");
    }
    if (p.length() > 1 && p.endsWith("/")) {
      p = p.substring(0, p.length() - 1);
    }
    return p;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  private static String validatePathSegment(String value, String field) {
    String token = normalize(value);
    if (token.isEmpty()) {
      throw new IllegalArgumentException(field + " is blank");
    }
    if (token.contains("/") || token.contains("?") || token.contains("#")) {
      throw new IllegalArgumentException(field + " contains unsupported path separators");
    }
    return token;
  }

  private static String encodeQueryParam(String value) {
    return URLEncoder.encode(Objects.toString(value, ""), StandardCharsets.UTF_8)
        .replace("+", "%20");
  }

  private static String validateJoinTarget(String value) {
    String token = normalize(value);
    if (token.isEmpty()) {
      throw new IllegalArgumentException("roomIdOrAlias is blank");
    }
    if (token.contains("/") || token.contains("?")) {
      throw new IllegalArgumentException("roomIdOrAlias contains unsupported path separators");
    }
    return token;
  }
}

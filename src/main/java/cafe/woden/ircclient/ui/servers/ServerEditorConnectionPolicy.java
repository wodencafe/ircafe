package cafe.woden.ircclient.ui.servers;

import java.util.Objects;

/** Pure validation and parsing rules for the server editor's core connection fields. */
final class ServerEditorConnectionPolicy {
  private ServerEditorConnectionPolicy() {}

  static ConnectionValidation validation(
      ServerEditorBackendProfile profile, String id, String host, String portText, String nick) {
    boolean idBad = trim(id).isEmpty();
    boolean hostBad = trim(host).isEmpty();
    boolean portBad = parsePort(portText).isEmpty();
    boolean nickBad = profile.requiresNick() && trim(nick).isEmpty();
    return new ConnectionValidation(idBad, hostBad, portBad, nickBad);
  }

  static ServerConnection parseConnection(String id, String host, String portText) {
    String resolvedId = trim(id);
    if (resolvedId.isEmpty()) {
      throw new IllegalArgumentException("Server ID is required");
    }

    String resolvedHost = trim(host);
    if (resolvedHost.isEmpty()) {
      throw new IllegalArgumentException("Host is required");
    }

    int resolvedPort;
    try {
      resolvedPort = Integer.parseInt(trim(portText));
    } catch (Exception e) {
      throw new IllegalArgumentException("Port must be a number");
    }
    if (resolvedPort <= 0 || resolvedPort > 65_535) {
      throw new IllegalArgumentException("Port must be 1-65535");
    }

    return new ServerConnection(resolvedId, resolvedHost, resolvedPort);
  }

  static String validateAndNormalizeNick(ServerEditorBackendProfile profile, String nick) {
    String resolvedNick = trim(nick);
    if (profile.requiresNick() && resolvedNick.isEmpty()) {
      throw new IllegalArgumentException("Nick is required");
    }
    return resolvedNick;
  }

  private static java.util.OptionalInt parsePort(String portText) {
    try {
      int parsed = Integer.parseInt(trim(portText));
      return parsed > 0 && parsed <= 65_535
          ? java.util.OptionalInt.of(parsed)
          : java.util.OptionalInt.empty();
    } catch (Exception e) {
      return java.util.OptionalInt.empty();
    }
  }

  private static String trim(String value) {
    return Objects.toString(value, "").trim();
  }

  record ConnectionValidation(boolean idBad, boolean hostBad, boolean portBad, boolean nickBad) {}

  record ServerConnection(String id, String host, int port) {}
}

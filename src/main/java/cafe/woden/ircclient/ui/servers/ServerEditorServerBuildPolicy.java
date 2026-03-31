package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;

/** Pure assembly rules for building a server config from the server-editor form values. */
final class ServerEditorServerBuildPolicy {
  private ServerEditorServerBuildPolicy() {}

  static IrcProperties.Server build(ServerBuildRequest request) {
    ServerEditorConnectionPolicy.ServerConnection connection =
        ServerEditorConnectionPolicy.parseConnection(
            request.id(), request.host(), request.portText());

    String serverPassword = Objects.toString(request.serverPassword(), "");
    String matrixAuthUser = trim(request.matrixAuthUser());

    ServerEditorAuthPolicy.validateMatrixCredentials(
        request.profile(), request.matrixAuthMode(), serverPassword, matrixAuthUser);
    if (containsCrlf(serverPassword)) {
      throw new IllegalArgumentException("Server/Core password must not contain newlines");
    }

    String nick =
        ServerEditorConnectionPolicy.validateAndNormalizeNick(request.profile(), request.nick());
    String login =
        ServerEditorAuthPolicy.resolveLogin(
            request.profile(), request.login(), nick, matrixAuthUser, request.matrixAuthMode());

    String realName = trim(request.realName());
    if (realName.isEmpty()) {
      realName = nick.isEmpty() ? login : nick;
    }
    if (nick.isEmpty() && !login.isEmpty()) {
      nick = login;
    }

    ServerEditorAuthPolicy.SaslBuildResult saslConfig =
        ServerEditorAuthPolicy.buildSasl(
            request.profile(),
            request.selectedAuthMode(),
            request.matrixAuthMode(),
            serverPassword,
            matrixAuthUser,
            request.saslUser(),
            request.saslSecret(),
            request.saslMechanism(),
            request.saslContinueOnFailure());

    IrcProperties.Server.Nickserv nickserv =
        ServerEditorAuthPolicy.buildNickserv(
            saslConfig.authMode(),
            request.nickservService(),
            request.nickservPassword(),
            request.nickservDelayJoin());

    return new IrcProperties.Server(
        connection.id(),
        connection.host(),
        connection.port(),
        request.tls(),
        saslConfig.serverPassword(),
        nick,
        login,
        realName,
        saslConfig.sasl(),
        nickserv,
        ServerEditorCommandListPolicy.autoJoinEntries(
            request.autoJoinChannelsText(), request.autoJoinPrivateMessagesText()),
        ServerEditorCommandListPolicy.performCommands(request.performText()),
        ServerEditorProxyBuildPolicy.buildOverride(
            request.proxyOverrideSelected(),
            request.proxyEnabled(),
            request.proxyHost(),
            request.proxyPort(),
            request.proxyUser(),
            request.proxyPassword(),
            request.proxyRemoteDns(),
            request.proxyConnectTimeoutMs(),
            request.proxyReadTimeoutMs()),
        request.backendId());
  }

  private static String trim(String value) {
    return Objects.toString(value, "").trim();
  }

  private static boolean containsCrlf(String value) {
    String resolved = Objects.toString(value, "");
    return resolved.indexOf('\n') >= 0 || resolved.indexOf('\r') >= 0;
  }

  record ServerBuildRequest(
      ServerEditorBackendProfile profile,
      String backendId,
      String id,
      String host,
      String portText,
      boolean tls,
      String serverPassword,
      ServerEditorMatrixAuthMode matrixAuthMode,
      String matrixAuthUser,
      String nick,
      String login,
      String realName,
      ServerEditorAuthMode selectedAuthMode,
      String saslUser,
      String saslSecret,
      String saslMechanism,
      boolean saslContinueOnFailure,
      String nickservService,
      String nickservPassword,
      boolean nickservDelayJoin,
      String autoJoinChannelsText,
      String autoJoinPrivateMessagesText,
      String performText,
      boolean proxyOverrideSelected,
      boolean proxyEnabled,
      String proxyHost,
      String proxyPort,
      String proxyUser,
      String proxyPassword,
      boolean proxyRemoteDns,
      String proxyConnectTimeoutMs,
      String proxyReadTimeoutMs) {
    ServerBuildRequest {
      Objects.requireNonNull(profile, "profile");
      Objects.requireNonNull(backendId, "backendId");
    }
  }
}

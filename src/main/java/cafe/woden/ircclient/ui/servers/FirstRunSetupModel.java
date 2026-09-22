package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.ui.localization.UiMessages;
import java.util.Arrays;
import java.util.List;

/** Validation for the small, optional IRC setup form; advanced options remain in Servers. */
final class FirstRunSetupModel {
  private static final UiMessages MESSAGES = UiMessages.bundledDefaults();

  private FirstRunSetupModel() {}

  static IrcProperties.Server build(
      String id,
      String host,
      String port,
      boolean tls,
      String nick,
      String account,
      String password,
      String channels) {
    var connection = ServerEditorConnectionPolicy.parseConnection(id, host, port);
    String normalizedNick =
        ServerEditorConnectionPolicy.validateAndNormalizeNick(
            ServerEditorBackendProfiles.builtIns().profileFor(IrcProperties.Server.Backend.IRC),
            nick);
    if (invalidToken(connection.id())
        || invalidToken(connection.host())
        || invalidToken(normalizedNick)) {
      throw new IllegalArgumentException(MESSAGES.text("setup.validation.connection"));
    }
    IrcProperties.Server.Sasl sasl = account(tls, account, password);
    return new IrcProperties.Server(
        connection.id(),
        connection.host(),
        connection.port(),
        tls,
        "",
        normalizedNick,
        normalizedNick,
        normalizedNick,
        sasl,
        null,
        channels(channels),
        List.of(),
        null);
  }

  static IrcProperties.Server.Sasl account(boolean tls, String account, String password) {
    String username = account.trim();
    if (username.isEmpty() && password.isEmpty()) return null;
    if (username.isEmpty() || password.isEmpty()) {
      throw new IllegalArgumentException(MESSAGES.text("setup.validation.account"));
    }
    if (!tls) {
      throw new IllegalArgumentException(MESSAGES.text("setup.validation.tls"));
    }
    if (invalidToken(username) || password.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException(MESSAGES.text("setup.validation.credentials"));
    }
    return new IrcProperties.Server.Sasl(true, username, password, "PLAIN", true);
  }

  static List<String> channels(String text) {
    List<String> channels =
        Arrays.stream(text.trim().split("[,\\s]+"))
            .filter(channel -> !channel.isEmpty())
            .distinct()
            .toList();
    if (channels.stream()
        .anyMatch(
            channel ->
                channel.length() < 2
                    || !(channel.startsWith("#") || channel.startsWith("&"))
                    || invalidToken(channel))) {
      throw new IllegalArgumentException(MESSAGES.text("setup.validation.channels"));
    }
    return channels;
  }

  private static boolean invalidToken(String text) {
    return text.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c));
  }
}

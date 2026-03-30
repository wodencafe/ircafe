package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.AutoJoinEntryCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure parsing rules for the server-editor auto-join and perform text areas. */
final class ServerEditorCommandListPolicy {
  private ServerEditorCommandListPolicy() {}

  static List<String> autoJoinEntries(String channelsText, String privateMessagesText) {
    List<String> entries = new ArrayList<>();

    for (String line : Objects.toString(channelsText, "").split("\\R")) {
      String channel = trim(line);
      if (!channel.isEmpty()) {
        entries.add(channel);
      }
    }

    for (String line : Objects.toString(privateMessagesText, "").split("\\R")) {
      String nick = trim(line);
      if (nick.isEmpty()) {
        continue;
      }
      String encoded = AutoJoinEntryCodec.encodePrivateMessageNick(nick);
      if (!encoded.isEmpty()) {
        entries.add(encoded);
      }
    }

    return entries;
  }

  static List<String> performCommands(String performText) {
    List<String> commands = new ArrayList<>();

    for (String line : Objects.toString(performText, "").split("\\R")) {
      String command = trim(line);
      if (!command.isEmpty()) {
        commands.add(command);
      }
    }

    return commands;
  }

  private static String trim(String value) {
    return Objects.toString(value, "").trim();
  }
}

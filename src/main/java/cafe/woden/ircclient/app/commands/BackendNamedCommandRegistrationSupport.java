package cafe.woden.ircclient.app.commands;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class BackendNamedCommandRegistrationSupport {

  private static final Set<String> RESERVED_COMMAND_NAMES =
      Set.of(
          "join",
          "j",
          "part",
          "leave",
          "connect",
          "disconnect",
          "reconnect",
          "quit",
          "nick",
          "away",
          "query",
          "whois",
          "whowas",
          "wi",
          "msg",
          "notice",
          "me",
          "topic",
          "kick",
          "invite",
          "invites",
          "invjoin",
          "invitejoin",
          "invignore",
          "inviteignore",
          "invwhois",
          "invitewhois",
          "invblock",
          "inviteblock",
          "inviteautojoin",
          "invautojoin",
          "ajinvite",
          "names",
          "who",
          "list",
          "monitor",
          "mon",
          "mode",
          "op",
          "deop",
          "voice",
          "devoice",
          "ban",
          "unban",
          "ignore",
          "unignore",
          "ignorelist",
          "ignores",
          "softignore",
          "unsoftignore",
          "softignorelist",
          "softignores",
          "version",
          "ping",
          "time",
          "ctcp",
          "dcc",
          "dccmsg",
          "chathistory",
          "history",
          "markread",
          "help",
          "commands",
          "upload",
          "reply",
          "react",
          "unreact",
          "edit",
          "redact",
          "delete",
          "filter",
          "quote",
          "raw");

  private BackendNamedCommandRegistrationSupport() {}

  public static String normalizeCommandName(String commandName) {
    String name = Objects.toString(commandName, "").trim().toLowerCase(Locale.ROOT);
    if (name.startsWith("/")) name = name.substring(1).trim();
    return name;
  }

  public static boolean isReservedCommandName(String commandName) {
    return RESERVED_COMMAND_NAMES.contains(commandName);
  }
}

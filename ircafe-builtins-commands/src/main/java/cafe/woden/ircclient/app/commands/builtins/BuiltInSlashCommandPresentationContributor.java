package cafe.woden.ircclient.app.commands.builtins;

import cafe.woden.ircclient.app.commands.spi.SlashCommandDescriptor;
import cafe.woden.ircclient.app.commands.spi.SlashCommandHelpSink;
import cafe.woden.ircclient.app.commands.spi.SlashCommandPresentationContributor;
import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Shared presentation metadata for built-in slash commands handled by the typed parser path. */
@AutoService(SlashCommandPresentationContributor.class)
public final class BuiltInSlashCommandPresentationContributor
    implements SlashCommandPresentationContributor {

  private static final List<SlashCommandDescriptor> COMMANDS =
      List.of(
          new SlashCommandDescriptor("/join", "Join channel"),
          new SlashCommandDescriptor("/j", "Alias: /join"),
          new SlashCommandDescriptor("/part", "Leave channel"),
          new SlashCommandDescriptor("/leave", "Alias: /part"),
          new SlashCommandDescriptor("/connect", "Connect server/all"),
          new SlashCommandDescriptor("/disconnect", "Disconnect server/all"),
          new SlashCommandDescriptor("/reconnect", "Reconnect server/all"),
          new SlashCommandDescriptor("/quit", "Disconnect all and quit"),
          new SlashCommandDescriptor("/nick", "Change nickname"),
          new SlashCommandDescriptor("/away", "Set/remove away status"),
          new SlashCommandDescriptor("/query", "Open private message"),
          new SlashCommandDescriptor("/whois", "WHOIS lookup"),
          new SlashCommandDescriptor("/wi", "Alias: /whois"),
          new SlashCommandDescriptor("/whowas", "WHOWAS lookup"),
          new SlashCommandDescriptor("/msg", "Send private message"),
          new SlashCommandDescriptor("/cs", "Send command to ChanServ"),
          new SlashCommandDescriptor("/chanserv", "Alias: /cs"),
          new SlashCommandDescriptor("/ns", "Send command to NickServ"),
          new SlashCommandDescriptor("/nickserv", "Alias: /ns"),
          new SlashCommandDescriptor("/ms", "Send command to MemoServ"),
          new SlashCommandDescriptor("/memoserv", "Alias: /ms"),
          new SlashCommandDescriptor("/os", "Send command to OperServ"),
          new SlashCommandDescriptor("/operserv", "Alias: /os"),
          new SlashCommandDescriptor("/notice", "Send notice"),
          new SlashCommandDescriptor("/me", "Send action"),
          new SlashCommandDescriptor("/topic", "View/change topic"),
          new SlashCommandDescriptor("/kick", "Kick user"),
          new SlashCommandDescriptor("/invite", "Invite user"),
          new SlashCommandDescriptor("/invites", "List pending invites"),
          new SlashCommandDescriptor("/invjoin", "Join pending invite"),
          new SlashCommandDescriptor("/invitejoin", "Alias: /invjoin"),
          new SlashCommandDescriptor("/invignore", "Ignore pending invite"),
          new SlashCommandDescriptor("/inviteignore", "Alias: /invignore"),
          new SlashCommandDescriptor("/invwhois", "WHOIS inviter from invite"),
          new SlashCommandDescriptor("/invitewhois", "Alias: /invwhois"),
          new SlashCommandDescriptor("/invblock", "Block inviter nick"),
          new SlashCommandDescriptor("/inviteblock", "Alias: /invblock"),
          new SlashCommandDescriptor("/inviteautojoin", "Toggle invite auto-join"),
          new SlashCommandDescriptor("/invautojoin", "Alias: /inviteautojoin"),
          new SlashCommandDescriptor("/ajinvite", "Alias: /inviteautojoin (toggle)"),
          new SlashCommandDescriptor("/names", "Request NAMES"),
          new SlashCommandDescriptor("/who", "Request WHO"),
          new SlashCommandDescriptor("/list", "Request LIST"),
          new SlashCommandDescriptor("/monitor", "Manage watched nicks"),
          new SlashCommandDescriptor("/mon", "Alias: /monitor"),
          new SlashCommandDescriptor("/mode", "Set/query mode"),
          new SlashCommandDescriptor("/op", "Grant op"),
          new SlashCommandDescriptor("/deop", "Remove op"),
          new SlashCommandDescriptor("/voice", "Grant voice"),
          new SlashCommandDescriptor("/devoice", "Remove voice"),
          new SlashCommandDescriptor("/ban", "Set ban"),
          new SlashCommandDescriptor("/unban", "Remove ban"),
          new SlashCommandDescriptor("/ignore", "Add hard ignore"),
          new SlashCommandDescriptor("/unignore", "Remove hard ignore"),
          new SlashCommandDescriptor("/ignorelist", "Show hard ignores"),
          new SlashCommandDescriptor("/ignores", "Alias: /ignorelist"),
          new SlashCommandDescriptor("/softignore", "Add soft ignore"),
          new SlashCommandDescriptor("/unsoftignore", "Remove soft ignore"),
          new SlashCommandDescriptor("/softignorelist", "Show soft ignores"),
          new SlashCommandDescriptor("/softignores", "Alias: /softignorelist"),
          new SlashCommandDescriptor("/version", "CTCP VERSION"),
          new SlashCommandDescriptor("/ping", "CTCP PING"),
          new SlashCommandDescriptor("/time", "CTCP TIME"),
          new SlashCommandDescriptor("/ctcp", "Send CTCP"),
          new SlashCommandDescriptor("/dcc", "DCC command"),
          new SlashCommandDescriptor("/dccmsg", "DCC message"),
          new SlashCommandDescriptor("/chathistory", "IRCv3 CHATHISTORY"),
          new SlashCommandDescriptor("/history", "Alias: /chathistory"),
          new SlashCommandDescriptor("/markread", "Set read marker for current target"),
          new SlashCommandDescriptor("/help", "Show command help"),
          new SlashCommandDescriptor("/commands", "Alias: /help"),
          new SlashCommandDescriptor("/upload", "Upload or send media"),
          new SlashCommandDescriptor("/reply", "Reply to message-id"),
          new SlashCommandDescriptor("/react", "React to message-id"),
          new SlashCommandDescriptor("/unreact", "Remove reaction from message-id"),
          new SlashCommandDescriptor("/edit", "Edit message-id (experimental draft)"),
          new SlashCommandDescriptor("/redact", "Redact message-id"),
          new SlashCommandDescriptor("/delete", "Alias: /redact"),
          new SlashCommandDescriptor("/quote", "Send raw IRC line"),
          new SlashCommandDescriptor("/raw", "Alias: /quote"));

  @Override
  public List<SlashCommandDescriptor> autocompleteCommands() {
    return COMMANDS;
  }

  @Override
  public void appendGeneralHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine(
        "Common: /join /part /msg /cs /ns /ms /os /notice /me /query /whois /names /list /topic /monitor /chathistory /quote /dcc");
    help.appendLine(
        "Invites: /invites /invjoin (/join -i) /invignore /invwhois /invblock /inviteautojoin (/ajinvite)");
    help.appendLine("Tip: /help dcc for direct-chat/file-transfer commands.");
    help.appendLine("/reply <msgid> <message> (requires message-tags)");
    help.appendLine("/react <msgid> <reaction-token> (requires message-tags)");
    help.appendLine("/unreact <msgid> <reaction-token> (requires message-tags)");
    help.appendLine(
        "Tip: /help edit, /help redact, /help markread, or /help upload for focused details.");
  }

  @Override
  public Map<String, Consumer<SlashCommandHelpSink>> topicHelpHandlers() {
    return Map.ofEntries(
        Map.entry("join", BuiltInSlashCommandPresentationContributor::appendJoinHelp),
        Map.entry("j", BuiltInSlashCommandPresentationContributor::appendJoinHelp),
        Map.entry("part", BuiltInSlashCommandPresentationContributor::appendPartHelp),
        Map.entry("leave", BuiltInSlashCommandPresentationContributor::appendPartHelp),
        Map.entry("connect", BuiltInSlashCommandPresentationContributor::appendConnectHelp),
        Map.entry("disconnect", BuiltInSlashCommandPresentationContributor::appendDisconnectHelp),
        Map.entry("reconnect", BuiltInSlashCommandPresentationContributor::appendReconnectHelp),
        Map.entry("quit", BuiltInSlashCommandPresentationContributor::appendQuitHelp),
        Map.entry("nick", BuiltInSlashCommandPresentationContributor::appendNickHelp),
        Map.entry("away", BuiltInSlashCommandPresentationContributor::appendAwayHelp),
        Map.entry("query", BuiltInSlashCommandPresentationContributor::appendQueryHelp),
        Map.entry("msg", BuiltInSlashCommandPresentationContributor::appendMsgHelp),
        Map.entry("cs", BuiltInSlashCommandPresentationContributor::appendChanServHelp),
        Map.entry("chanserv", BuiltInSlashCommandPresentationContributor::appendChanServHelp),
        Map.entry("ns", BuiltInSlashCommandPresentationContributor::appendNickServHelp),
        Map.entry("nickserv", BuiltInSlashCommandPresentationContributor::appendNickServHelp),
        Map.entry("ms", BuiltInSlashCommandPresentationContributor::appendMemoServHelp),
        Map.entry("memoserv", BuiltInSlashCommandPresentationContributor::appendMemoServHelp),
        Map.entry("os", BuiltInSlashCommandPresentationContributor::appendOperServHelp),
        Map.entry("operserv", BuiltInSlashCommandPresentationContributor::appendOperServHelp),
        Map.entry("notice", BuiltInSlashCommandPresentationContributor::appendNoticeHelp),
        Map.entry("me", BuiltInSlashCommandPresentationContributor::appendMeHelp),
        Map.entry("whois", BuiltInSlashCommandPresentationContributor::appendWhoisHelp),
        Map.entry("wi", BuiltInSlashCommandPresentationContributor::appendWhoisHelp),
        Map.entry("whowas", BuiltInSlashCommandPresentationContributor::appendWhowasHelp),
        Map.entry("ctcp", BuiltInSlashCommandPresentationContributor::appendCtcpHelp),
        Map.entry("version", BuiltInSlashCommandPresentationContributor::appendCtcpShortcutHelp),
        Map.entry("ping", BuiltInSlashCommandPresentationContributor::appendCtcpShortcutHelp),
        Map.entry("time", BuiltInSlashCommandPresentationContributor::appendCtcpShortcutHelp),
        Map.entry("dcc", BuiltInSlashCommandPresentationContributor::appendDccHelp),
        Map.entry("dccmsg", BuiltInSlashCommandPresentationContributor::appendDccHelp),
        Map.entry("upload", BuiltInSlashCommandPresentationContributor::appendUploadHelpDetails),
        Map.entry("monitor", BuiltInSlashCommandPresentationContributor::appendMonitorHelp),
        Map.entry("mon", BuiltInSlashCommandPresentationContributor::appendMonitorHelp),
        Map.entry("ignore", BuiltInSlashCommandPresentationContributor::appendIgnoreHelp),
        Map.entry("unignore", BuiltInSlashCommandPresentationContributor::appendUnignoreHelp),
        Map.entry("ignorelist", BuiltInSlashCommandPresentationContributor::appendIgnoreListHelp),
        Map.entry("ignores", BuiltInSlashCommandPresentationContributor::appendIgnoreListHelp),
        Map.entry("softignore", BuiltInSlashCommandPresentationContributor::appendSoftIgnoreHelp),
        Map.entry(
            "unsoftignore", BuiltInSlashCommandPresentationContributor::appendUnsoftIgnoreHelp),
        Map.entry(
            "softignorelist", BuiltInSlashCommandPresentationContributor::appendSoftIgnoreListHelp),
        Map.entry(
            "softignores", BuiltInSlashCommandPresentationContributor::appendSoftIgnoreListHelp),
        Map.entry("topic", BuiltInSlashCommandPresentationContributor::appendTopicHelp),
        Map.entry("kick", BuiltInSlashCommandPresentationContributor::appendKickHelp),
        Map.entry("invite", BuiltInSlashCommandPresentationContributor::appendInviteHelp),
        Map.entry("invites", BuiltInSlashCommandPresentationContributor::appendInviteListHelp),
        Map.entry("invjoin", BuiltInSlashCommandPresentationContributor::appendInviteJoinHelp),
        Map.entry("invitejoin", BuiltInSlashCommandPresentationContributor::appendInviteJoinHelp),
        Map.entry("invignore", BuiltInSlashCommandPresentationContributor::appendInviteIgnoreHelp),
        Map.entry(
            "inviteignore", BuiltInSlashCommandPresentationContributor::appendInviteIgnoreHelp),
        Map.entry("invwhois", BuiltInSlashCommandPresentationContributor::appendInviteWhoisHelp),
        Map.entry("invitewhois", BuiltInSlashCommandPresentationContributor::appendInviteWhoisHelp),
        Map.entry("invblock", BuiltInSlashCommandPresentationContributor::appendInviteBlockHelp),
        Map.entry("inviteblock", BuiltInSlashCommandPresentationContributor::appendInviteBlockHelp),
        Map.entry(
            "inviteautojoin", BuiltInSlashCommandPresentationContributor::appendInviteAutoJoinHelp),
        Map.entry(
            "invautojoin", BuiltInSlashCommandPresentationContributor::appendInviteAutoJoinHelp),
        Map.entry("ajinvite", BuiltInSlashCommandPresentationContributor::appendInviteAutoJoinHelp),
        Map.entry("names", BuiltInSlashCommandPresentationContributor::appendNamesHelp),
        Map.entry("who", BuiltInSlashCommandPresentationContributor::appendWhoHelp),
        Map.entry("list", BuiltInSlashCommandPresentationContributor::appendListHelp),
        Map.entry("mode", BuiltInSlashCommandPresentationContributor::appendModeHelp),
        Map.entry("op", BuiltInSlashCommandPresentationContributor::appendOperatorModeHelp),
        Map.entry("deop", BuiltInSlashCommandPresentationContributor::appendOperatorModeHelp),
        Map.entry("voice", BuiltInSlashCommandPresentationContributor::appendOperatorModeHelp),
        Map.entry("devoice", BuiltInSlashCommandPresentationContributor::appendOperatorModeHelp),
        Map.entry("ban", BuiltInSlashCommandPresentationContributor::appendBanHelp),
        Map.entry("unban", BuiltInSlashCommandPresentationContributor::appendBanHelp),
        Map.entry(
            "chathistory",
            BuiltInSlashCommandPresentationContributor::appendChatHistoryHelpDetails),
        Map.entry(
            "history", BuiltInSlashCommandPresentationContributor::appendChatHistoryHelpDetails),
        Map.entry("markread", BuiltInSlashCommandPresentationContributor::appendMarkReadHelp),
        Map.entry("edit", BuiltInSlashCommandPresentationContributor::appendEditHelpDetails),
        Map.entry("redact", BuiltInSlashCommandPresentationContributor::appendRedactHelpDetails),
        Map.entry("delete", BuiltInSlashCommandPresentationContributor::appendRedactHelpDetails),
        Map.entry("help", BuiltInSlashCommandPresentationContributor::appendHelpCommandHelp),
        Map.entry("commands", BuiltInSlashCommandPresentationContributor::appendHelpCommandHelp),
        Map.entry("quote", BuiltInSlashCommandPresentationContributor::appendRawQuoteHelp),
        Map.entry("raw", BuiltInSlashCommandPresentationContributor::appendRawQuoteHelp));
  }

  private static void appendJoinHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /join <#channel> [key]");
    help.appendLine("Alias: /j <#channel> [key]");
  }

  private static void appendPartHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /part [#channel] [reason]");
    help.appendLine("Alias: /leave [#channel] [reason]");
  }

  private static void appendConnectHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /connect [server|all]");
    help.appendLine("Connects the selected configured server, or all configured servers.");
  }

  private static void appendDisconnectHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /disconnect [server|all]");
    help.appendLine("Disconnects the selected configured server, or all connected servers.");
  }

  private static void appendReconnectHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /reconnect [server|all]");
    help.appendLine("Reconnects the selected configured server, or all configured servers.");
  }

  private static void appendQuitHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /quit [message]");
    help.appendLine("Disconnects all servers and exits the client.");
  }

  private static void appendNickHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /nick <newNick>");
    help.appendLine("Changes your nickname on the active server.");
  }

  private static void appendAwayHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /away [message]");
    help.appendLine("Sets your away message, or clears away status when no message is provided.");
  }

  private static void appendQueryHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /query <nick>");
    help.appendLine("Opens or focuses a private message tab for the nick.");
  }

  private static void appendMsgHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /msg <nick> <message>");
    help.appendLine("Sends a private message without changing the active target.");
  }

  private static void appendChanServHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /cs [command [args...]]");
    help.appendLine("Alias: /chanserv [command [args...]]");
    help.appendLine(
        "Sends a private message to ChanServ on the active server, like /msg ChanServ.");
    help.appendLine("With no command, requests ChanServ HELP.");
    help.appendLine("Examples: /cs HELP, /cs OP #channel, /cs INFO #channel");
  }

  private static void appendNickServHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /ns [command [args...]]");
    help.appendLine("Alias: /nickserv [command [args...]]");
    help.appendLine(
        "Sends a private message to NickServ on the active server, like /msg NickServ.");
    help.appendLine("With no command, requests NickServ HELP.");
    help.appendLine("Examples: /ns HELP, /ns INFO nickname, /ns IDENTIFY password");
  }

  private static void appendMemoServHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /ms [command [args...]]");
    help.appendLine("Alias: /memoserv [command [args...]]");
    help.appendLine(
        "Sends a private message to MemoServ on the active server, like /msg MemoServ.");
    help.appendLine("With no command, requests MemoServ HELP.");
    help.appendLine("Examples: /ms HELP, /ms LIST, /ms READ 1, /ms SEND nickname message");
  }

  private static void appendOperServHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /os [command [args...]]");
    help.appendLine("Alias: /operserv [command [args...]]");
    help.appendLine(
        "Sends a private message to OperServ on the active server, like /msg OperServ.");
    help.appendLine("With no command, requests OperServ HELP.");
    help.appendLine("Available commands and required privileges depend on the network's services.");
    help.appendLine("Examples: /os HELP, /os HELP command");
  }

  private static void appendNoticeHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /notice <target> <message>");
    help.appendLine("Sends an IRC NOTICE to a nick or channel.");
  }

  private static void appendMeHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /me <action>");
    help.appendLine("Sends a CTCP ACTION to the active channel or private message.");
  }

  private static void appendWhoisHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /whois <nick>");
    help.appendLine("Alias: /wi <nick>");
  }

  private static void appendWhowasHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /whowas <nick> [count]");
    help.appendLine("Requests WHOWAS information for a nick that is no longer online.");
  }

  private static void appendCtcpHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /ctcp <nick> <command> [args...]");
    help.appendLine("Shortcuts: /version <nick>, /ping <nick>, /time <nick>.");
  }

  private static void appendCtcpShortcutHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /version <nick>  |  /ping <nick>  |  /time <nick>");
    help.appendLine("Sends common CTCP requests without typing /ctcp explicitly.");
  }

  private static void appendDccHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("/dcc chat <nick>");
    help.appendLine("/dcc send <nick> <file-path>");
    help.appendLine("/dcc accept <nick>");
    help.appendLine("/dcc get <nick> [save-path]");
    help.appendLine("/dcc msg <nick> <text>  (alias: /dccmsg <nick> <text>)");
    help.appendLine("/dcc close <nick>  /dcc list  /dcc panel");
    help.appendLine("UI: right-click a nick and use the DCC submenu.");
  }

  private static void appendUploadHelpDetails(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /upload <msgtype> <path> [caption]");
    help.appendLine(
        "Uploads or sends a media/file payload through the active backend when supported.");
    help.appendLine("Backend help may add supported msgtypes and shortcuts below.");
  }

  private static void appendMonitorHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /monitor <+|-|list|status|clear> [nicks]");
    help.appendLine("Aliases: /mon, /monitor +nick1 nick2, /monitor -nick1,nick2");
    help.appendLine("Examples: /monitor +alice,bob  |  /monitor list  |  /monitor clear");
  }

  private static void appendIgnoreHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /ignore [-options] [levels] <maskOrNick>");
    help.appendLine(
        "Options include -channels #a,#b, -pattern <text>, -regexp, -full, -expires <duration>, and -replies.");
  }

  private static void appendUnignoreHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /unignore <maskOrNick|index>");
    help.appendLine("Removes a hard-ignore rule by mask/nick or visible list index.");
  }

  private static void appendIgnoreListHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /ignorelist");
    help.appendLine("Alias: /ignores");
  }

  private static void appendSoftIgnoreHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /softignore <maskOrNick>");
    help.appendLine("Soft-ignored users have inbound messages rendered as spoilers.");
  }

  private static void appendUnsoftIgnoreHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /unsoftignore <maskOrNick|index>");
    help.appendLine("Removes a soft-ignore rule by mask/nick or visible list index.");
  }

  private static void appendSoftIgnoreListHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /softignorelist");
    help.appendLine("Alias: /softignores");
  }

  private static void appendTopicHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /topic [#channel] [new topic...]");
    help.appendLine("Shows or changes the topic for the active or specified channel.");
  }

  private static void appendKickHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /kick [#channel] <nick> [reason]");
    help.appendLine("Kicks a nick from the active or specified channel.");
  }

  private static void appendInviteHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /invite <nick> [#channel]");
    help.appendLine("Invites a nick to the active or specified channel.");
  }

  private static void appendInviteListHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /invites [serverId]");
    help.appendLine("Lists pending channel invites for the active or specified server.");
  }

  private static void appendInviteJoinHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /invjoin [inviteId|last]");
    help.appendLine("Aliases: /invitejoin [inviteId|last], /join -i [inviteId|last]");
  }

  private static void appendInviteIgnoreHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /invignore [inviteId|last]");
    help.appendLine("Alias: /inviteignore [inviteId|last]");
  }

  private static void appendInviteWhoisHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /invwhois [inviteId|last]");
    help.appendLine("Alias: /invitewhois [inviteId|last]");
  }

  private static void appendInviteBlockHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /invblock [inviteId|last]");
    help.appendLine("Alias: /inviteblock [inviteId|last]");
  }

  private static void appendInviteAutoJoinHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /inviteautojoin [on|off|status]");
    help.appendLine("Aliases: /invautojoin [on|off|status], /ajinvite [on|off|status|toggle]");
  }

  private static void appendNamesHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /names [#channel]");
    help.appendLine("Requests the nick list for the active or specified channel.");
  }

  private static void appendWhoHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /who [mask|#channel]");
    help.appendLine("Requests WHO information for a nick mask or channel.");
  }

  private static void appendListHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /list [pattern]");
    help.appendLine("Requests the server channel list, optionally filtered by pattern.");
  }

  private static void appendModeHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /mode <target> [mode-spec [args...]]");
    help.appendLine("Queries or changes user/channel modes.");
  }

  private static void appendOperatorModeHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /op|/deop|/voice|/devoice [#channel] <nick> [more nicks...]");
    help.appendLine("Changes common channel privilege modes for one or more nicks.");
  }

  private static void appendBanHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /ban|/unban [#channel] <mask-or-nick> [more...]");
    help.appendLine("Adds or removes channel ban masks.");
  }

  private static void appendChatHistoryHelpDetails(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("/chathistory before <msgid=...|timestamp=...> [limit]");
    help.appendLine("/chathistory latest [*|msgid=...|timestamp=...] [limit]");
    help.appendLine("/chathistory around <msgid=...|timestamp=...> [limit]");
    help.appendLine("/chathistory between <start> <end> [limit]");
  }

  private static void appendMarkReadHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /markread");
    help.appendLine(
        "Sets the read marker for the active channel or query and clears unread state.");
  }

  private static void appendEditHelpDetails(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /edit <msgid> <message>");
    help.appendLine(
        "Edits one of your recent messages when the backend supports draft/message-edit.");
  }

  private static void appendRedactHelpDetails(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /redact <msgid> [reason]");
    help.appendLine("Alias: /delete <msgid> [reason]");
    help.appendLine(
        "Redacts one of your recent messages when the backend supports message redaction.");
  }

  private static void appendHelpCommandHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /help [topic]");
    help.appendLine("Alias: /commands [topic]");
    help.appendLine("Shows general slash-command help or focused help for a command topic.");
  }

  private static void appendRawQuoteHelp(SlashCommandHelpSink help) {
    if (help == null) {
      return;
    }
    help.appendLine("Usage: /quote <RAW IRC LINE>");
    help.appendLine("Alias: /raw <RAW IRC LINE>");
    help.appendLine("Sends one raw IRC protocol line to the active server.");
  }
}

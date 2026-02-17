package cafe.woden.ircclient.app.commands;

public sealed interface ParsedInput permits
    ParsedInput.Join,
    ParsedInput.Part,
    ParsedInput.Connect,
    ParsedInput.Disconnect,
    ParsedInput.Reconnect,
    ParsedInput.Quit,
    ParsedInput.Nick,
    ParsedInput.Away,
    ParsedInput.Query,
    ParsedInput.Whois,
    ParsedInput.Whowas,
    ParsedInput.Msg,
    ParsedInput.Notice,
    ParsedInput.Me,
    ParsedInput.Topic,
    ParsedInput.Kick,
    ParsedInput.Invite,
    ParsedInput.Names,
    ParsedInput.Who,
    ParsedInput.ListCmd,
    ParsedInput.Mode,
    ParsedInput.Op,
    ParsedInput.Deop,
    ParsedInput.Voice,
    ParsedInput.Devoice,
    ParsedInput.Ban,
    ParsedInput.Unban,
    ParsedInput.CtcpVersion,
    ParsedInput.CtcpPing,
    ParsedInput.CtcpTime,
    ParsedInput.Ctcp,
    ParsedInput.Dcc,
    ParsedInput.Ignore,
    ParsedInput.Unignore,
    ParsedInput.IgnoreList,
    ParsedInput.SoftIgnore,
    ParsedInput.UnsoftIgnore,
    ParsedInput.SoftIgnoreList,
    ParsedInput.ChatHistoryBefore,
    ParsedInput.ChatHistoryLatest,
    ParsedInput.ChatHistoryBetween,
    ParsedInput.ChatHistoryAround,
    ParsedInput.Help,
    ParsedInput.ReplyMessage,
    ParsedInput.ReactMessage,
    ParsedInput.EditMessage,
    ParsedInput.RedactMessage,
    ParsedInput.Filter,
    ParsedInput.Quote,
    ParsedInput.Say,
    ParsedInput.Unknown {

  /** /join <#channel> [key] */
  record Join(String channel, String key) implements ParsedInput {
    public Join(String channel) {
      this(channel, "");
    }
  }

  /**
   * /part [#channel] [reason]
   *
   * <p>If #channel is omitted, parts the currently active channel.
   */
  record Part(String channel, String reason) implements ParsedInput {}

  /** /connect [serverId|all] */
  record Connect(String target) implements ParsedInput {}

  /** /disconnect [serverId|all] */
  record Disconnect(String target) implements ParsedInput {}

  /** /reconnect [serverId|all] */
  record Reconnect(String target) implements ParsedInput {}

  /** /quit [reason...] */
  record Quit(String reason) implements ParsedInput {}

  record Nick(String newNick) implements ParsedInput {}

  /**
   * /away [message]
   *
   * <p>If {@code message} is blank, the away status should be cleared.
   */
  record Away(String message) implements ParsedInput {}

  record Query(String nick) implements ParsedInput {}

  /** /whois <nick> */
  record Whois(String nick) implements ParsedInput {}

  /** /whowas <nick> [count] */
  record Whowas(String nick, int count) implements ParsedInput {}

  record Msg(String nick, String body) implements ParsedInput {}

  /**
   * /notice <target> <message>
   *
   * <p>Sends an IRC NOTICE to either a channel or nick.
   */
  record Notice(String target, String body) implements ParsedInput {}

  record Me(String action) implements ParsedInput {}

  /** /topic [#channel] [new topic...] */
  record Topic(String first, String rest) implements ParsedInput {}

  /** /kick [#channel] <nick> [reason...] */
  record Kick(String channel, String nick, String reason) implements ParsedInput {}

  /** /invite <nick> [#channel] */
  record Invite(String nick, String channel) implements ParsedInput {}

  /** /names [#channel] */
  record Names(String channel) implements ParsedInput {}

  /** /who [maskOrChannel [flags...]] */
  record Who(String args) implements ParsedInput {}

  /** /list [args...] */
  record ListCmd(String args) implements ParsedInput {}

  
  record Mode(String first, String rest) implements ParsedInput {}

  record Op(String channel, java.util.List<String> nicks) implements ParsedInput {}

  record Deop(String channel, java.util.List<String> nicks) implements ParsedInput {}

  record Voice(String channel, java.util.List<String> nicks) implements ParsedInput {}

  record Devoice(String channel, java.util.List<String> nicks) implements ParsedInput {}

  record Ban(String channel, java.util.List<String> masksOrNicks) implements ParsedInput {}

  record Unban(String channel, java.util.List<String> masksOrNicks) implements ParsedInput {}

  // CTCP convenience
  record CtcpVersion(String nick) implements ParsedInput {}

  record CtcpPing(String nick) implements ParsedInput {}

  record CtcpTime(String nick) implements ParsedInput {}

  record Ctcp(String nick, String command, String args) implements ParsedInput {}

  /**
   * /dcc <subcommand> [nick] [args...]
   *
   * <p>Examples:
   * /dcc chat <nick>
   * /dcc send <nick> <path>
   * /dcc accept <nick>
   * /dcc get <nick> [save-path]
   * /dcc msg <nick> <text>
   * /dcc close <nick>
   * /dcc list
   */
  record Dcc(String subcommand, String nick, String argument) implements ParsedInput {}

  record Ignore(String maskOrNick) implements ParsedInput {}

  record Unignore(String maskOrNick) implements ParsedInput {}

  
  record IgnoreList() implements ParsedInput {}

  record SoftIgnore(String maskOrNick) implements ParsedInput {}

  record UnsoftIgnore(String maskOrNick) implements ParsedInput {}

  
  record SoftIgnoreList() implements ParsedInput {}

  /**
   * /chathistory [limit]
   * /chathistory [before] <selector> [limit]
   *
   * <p>Request IRCv3 CHATHISTORY scrollback for the active target.
   *
   * <p>Selector examples:
   * {@code timestamp=2026-02-16T12:34:56.000Z}
   * {@code msgid=abc123}
   */
  record ChatHistoryBefore(int limit, String selector) implements ParsedInput {
    public ChatHistoryBefore(int limit) {
      this(limit, "");
    }
  }

  /**
   * /chathistory latest [*|selector] [limit]
   */
  record ChatHistoryLatest(int limit, String selector) implements ParsedInput {
    public ChatHistoryLatest(int limit) {
      this(limit, "*");
    }
  }

  /**
   * /chathistory between <start-selector> <end-selector> [limit]
   */
  record ChatHistoryBetween(String startSelector, String endSelector, int limit) implements ParsedInput {}

  /**
   * /chathistory around <selector> [limit]
   */
  record ChatHistoryAround(String selector, int limit) implements ParsedInput {}

  /**
   * /help [topic]
   *
   * <p>Local help output for common slash commands.
   */
  record Help(String topic) implements ParsedInput {}

  /**
   * /reply <msgid> <message>
   *
   * <p>Internal/advanced IRCv3 compose helper used by the first-class reply composer UI.
   */
  record ReplyMessage(String messageId, String body) implements ParsedInput {}

  /**
   * /react <msgid> <reaction-token>
   *
   * <p>Internal/advanced IRCv3 compose helper used by the quick reaction picker UI.
   */
  record ReactMessage(String messageId, String reaction) implements ParsedInput {}

  /**
   * /edit <msgid> <message>
   *
   * <p>IRCv3 draft/message-edit compose helper.
   */
  record EditMessage(String messageId, String body) implements ParsedInput {}

  /**
   * /redact <msgid>
   *
   * <p>IRCv3 draft/message-redaction compose helper.
   */
  record RedactMessage(String messageId) implements ParsedInput {}

  /** Local /filter ... command family. */
  record Filter(FilterCommand command) implements ParsedInput {}



  /**
   * /quote <RAW IRC LINE>
   *
   * <p>Sends a raw IRC protocol line to the server (for advanced/unsupported commands).
   */
  record Quote(String rawLine) implements ParsedInput {}

  record Say(String text) implements ParsedInput {}

  record Unknown(String raw) implements ParsedInput {}
}

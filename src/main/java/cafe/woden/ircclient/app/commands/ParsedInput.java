package cafe.woden.ircclient.app.commands;

public sealed interface ParsedInput permits
    ParsedInput.Join,
    ParsedInput.Part,
    ParsedInput.Nick,
    ParsedInput.Away,
    ParsedInput.Query,
    ParsedInput.Msg,
    ParsedInput.Me,
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
    ParsedInput.Ignore,
    ParsedInput.Unignore,
    ParsedInput.IgnoreList,
    ParsedInput.SoftIgnore,
    ParsedInput.UnsoftIgnore,
    ParsedInput.SoftIgnoreList,
    ParsedInput.ChatHistoryBefore,
    ParsedInput.Quote,
    ParsedInput.Say,
    ParsedInput.Unknown {

  record Join(String channel) implements ParsedInput {}

  /**
   * /part [#channel] [reason]
   *
   * <p>If #channel is omitted, parts the currently active channel.
   */
  record Part(String channel, String reason) implements ParsedInput {}

  record Nick(String newNick) implements ParsedInput {}

  /**
   * /away [message]
   *
   * <p>If {@code message} is blank, the away status should be cleared.
   */
  record Away(String message) implements ParsedInput {}

  record Query(String nick) implements ParsedInput {}

  record Msg(String nick, String body) implements ParsedInput {}

  record Me(String action) implements ParsedInput {}

  
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

  record Ignore(String maskOrNick) implements ParsedInput {}

  record Unignore(String maskOrNick) implements ParsedInput {}

  
  record IgnoreList() implements ParsedInput {}

  record SoftIgnore(String maskOrNick) implements ParsedInput {}

  record UnsoftIgnore(String maskOrNick) implements ParsedInput {}

  
  record SoftIgnoreList() implements ParsedInput {}

  /**
   * /chathistory [limit]
   *
   * <p>Developer/debug helper for requesting IRCv3 CHATHISTORY scrollback.
   * This will request messages BEFORE the current time for the active target.
   */
  record ChatHistoryBefore(int limit) implements ParsedInput {}



  /**
   * /quote <RAW IRC LINE>
   *
   * <p>Sends a raw IRC protocol line to the server (for advanced/unsupported commands).
   */
  record Quote(String rawLine) implements ParsedInput {}

  record Say(String text) implements ParsedInput {}

  record Unknown(String raw) implements ParsedInput {}
}

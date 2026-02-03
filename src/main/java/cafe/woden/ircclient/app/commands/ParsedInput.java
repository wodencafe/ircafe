package cafe.woden.ircclient.app.commands;

/**
 * Parsed representation of a line from the input box.
 */
public sealed interface ParsedInput permits
    ParsedInput.Join,
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
    ParsedInput.Quote,
    ParsedInput.Say,
    ParsedInput.Unknown {

  record Join(String channel) implements ParsedInput {}

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

  /** /mode [#channel] [modes] [args...] */
  record Mode(String first, String rest) implements ParsedInput {}

  /** /op [#channel] nick1 nick2 ... */
  record Op(String channel, java.util.List<String> nicks) implements ParsedInput {}

  /** /deop [#channel] nick1 nick2 ... */
  record Deop(String channel, java.util.List<String> nicks) implements ParsedInput {}

  /** /voice [#channel] nick1 nick2 ... */
  record Voice(String channel, java.util.List<String> nicks) implements ParsedInput {}

  /** /devoice [#channel] nick1 nick2 ... */
  record Devoice(String channel, java.util.List<String> nicks) implements ParsedInput {}

  /** /ban [#channel] maskOrNick1 maskOrNick2 ... */
  record Ban(String channel, java.util.List<String> masksOrNicks) implements ParsedInput {}

  /** /unban [#channel] maskOrNick1 maskOrNick2 ... */
  record Unban(String channel, java.util.List<String> masksOrNicks) implements ParsedInput {}


  // CTCP convenience
  record CtcpVersion(String nick) implements ParsedInput {}

  record CtcpPing(String nick) implements ParsedInput {}

  record CtcpTime(String nick) implements ParsedInput {}

  /** Generic CTCP: /ctcp <nick> <command> [args...] */
  record Ctcp(String nick, String command, String args) implements ParsedInput {}

  /** /ignore <maskOrNick> */
  record Ignore(String maskOrNick) implements ParsedInput {}

  /** /unignore <maskOrNick> */
  record Unignore(String maskOrNick) implements ParsedInput {}

  /** /ignorelist */
  record IgnoreList() implements ParsedInput {}

  /**
   * /quote <RAW IRC LINE>
   *
   * <p>Sends a raw IRC protocol line to the server (for advanced/unsupported commands).
   */
  record Quote(String rawLine) implements ParsedInput {}

  record Say(String text) implements ParsedInput {}

  record Unknown(String raw) implements ParsedInput {}
}

package cafe.woden.ircclient.app.commands;

/**
 * Parsed representation of a line from the input box.
 */
public sealed interface ParsedInput permits
    ParsedInput.Join,
    ParsedInput.Nick,
    ParsedInput.Query,
    ParsedInput.Msg,
    ParsedInput.Me,
    ParsedInput.Say,
    ParsedInput.Unknown {

  record Join(String channel) implements ParsedInput {}

  record Nick(String newNick) implements ParsedInput {}

  record Query(String nick) implements ParsedInput {}

  record Msg(String nick, String body) implements ParsedInput {}

  record Me(String action) implements ParsedInput {}

  record Say(String text) implements ParsedInput {}

  record Unknown(String raw) implements ParsedInput {}
}

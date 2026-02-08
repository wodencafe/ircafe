package cafe.woden.ircclient.irc;

import java.time.Instant;

/**
 * A single line returned as part of an IRCv3 {@code CHATHISTORY} batch.
 *
 */
public record ChatHistoryEntry(
    Instant at,
    Kind kind,
    String target,
    String from,
    String text
) {

  public enum Kind {
    PRIVMSG,
    ACTION,
    NOTICE
  }

  public ChatHistoryEntry {
    if (at == null) at = Instant.now();
    if (kind == null) kind = Kind.PRIVMSG;
    if (target == null) target = "";
    if (from == null) from = "";
    if (text == null) text = "";
  }
}

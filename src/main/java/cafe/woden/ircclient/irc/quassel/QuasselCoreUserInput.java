package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.containsCrlf;

import java.util.Objects;

/** Validates and renders user input without resolving sessions or writing to the transport. */
final class QuasselCoreUserInput {
  private static final int BUFFER_STATUS = 0x01;
  private static final int BUFFER_CHANNEL = 0x02;
  private static final int BUFFER_QUERY = 0x04;

  private QuasselCoreUserInput() {}

  record Plan(String operation, int typeBits, String target, String input) {}

  static Plan changeNick(String newNick) {
    String nick = singleParameter(newNick, "new nick");
    return new Plan("change nick", BUFFER_STATUS, "", "/NICK " + nick);
  }

  static Plan setAway(String awayMessage) {
    String message = normalized(awayMessage);
    rejectNewlines(message, "away message contains CR/LF");
    return new Plan(
        "set away", BUFFER_STATUS, "", message.isEmpty() ? "/AWAY" : "/AWAY " + message);
  }

  static Plan requestNames(String channel) {
    String target = singleParameter(channel, "channel");
    return new Plan("request names", BUFFER_CHANNEL, target, "/NAMES " + target);
  }

  static Plan joinChannel(String channel) {
    String target = singleParameter(channel, "channel");
    return new Plan("join channel", BUFFER_STATUS, "", "/JOIN " + target);
  }

  static Plan whois(String nick) {
    String target = singleParameter(nick, "nick");
    return new Plan("whois", BUFFER_QUERY, target, "/WHOIS " + target);
  }

  static Plan partChannel(String channel, String reason) {
    String target = normalized(channel);
    requireNonEmpty(target, "channel");
    String text = normalized(reason);
    rejectNewlines(target, "part parameters contain CR/LF");
    rejectNewlines(text, "part parameters contain CR/LF");
    String command = text.isEmpty() ? "/PART " + target : "/PART " + target + " " + text;
    return new Plan("part channel", BUFFER_CHANNEL, target, command);
  }

  static Plan sendToChannel(String channel, String message) {
    return message(channel, message, BUFFER_CHANNEL, "channel", "send message to channel", false);
  }

  static Plan sendPrivateMessage(String nick, String message) {
    return message(nick, message, BUFFER_QUERY, "nick", "send private message", false);
  }

  static Plan sendNoticeToChannel(String channel, String message) {
    return message(channel, message, BUFFER_CHANNEL, "channel", "send notice to channel", true);
  }

  static Plan sendNoticePrivate(String nick, String message) {
    return message(nick, message, BUFFER_QUERY, "nick", "send notice", true);
  }

  private static Plan message(
      String rawTarget,
      String rawText,
      int typeBits,
      String targetName,
      String operation,
      boolean notice) {
    String target = normalized(rawTarget);
    String text = normalized(rawText);
    requireNonEmpty(target, targetName);
    requireNonEmpty(text, "message");
    String newlineReason = (notice ? "notice" : "message") + " parameters contain CR/LF";
    rejectNewlines(target, newlineReason);
    rejectNewlines(text, newlineReason);
    return new Plan(operation, typeBits, target, notice ? "/NOTICE " + target + " " + text : text);
  }

  private static String singleParameter(String raw, String name) {
    String value = normalized(raw);
    requireNonEmpty(value, name);
    rejectNewlines(value, name + " contains CR/LF");
    return value;
  }

  private static String normalized(String value) {
    return Objects.toString(value, "").trim();
  }

  private static void requireNonEmpty(String value, String name) {
    if (value.isEmpty()) throw new IllegalArgumentException(name + " is blank");
  }

  private static void rejectNewlines(String value, String reason) {
    if (containsCrlf(value)) throw new IllegalArgumentException(reason);
  }
}

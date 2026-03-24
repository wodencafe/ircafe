package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.pircbotx.PircBotX;

/** Sends PRIVMSG/NOTICE payloads using IRCv3 multiline batches when negotiated. */
final class PircbotxMultilineMessageSupport {

  void send(
      PircBotX bot,
      PircbotxConnectionState connection,
      String serverId,
      String sanitizedTarget,
      String message,
      boolean notice) {
    String payload = Objects.toString(message, "");
    if (payload.isEmpty()) return;

    List<String> lines = normalizeMessageLines(payload);
    if (lines.isEmpty()) return;

    String command = notice ? "NOTICE" : "PRIVMSG";
    if (lines.size() == 1) {
      sendRawMessageLine(bot, command, sanitizedTarget, lines.get(0));
      return;
    }

    String batchType = multilineBatchType(connection);
    String concatTag = multilineConcatTag(connection);
    if (batchType.isEmpty() || concatTag.isEmpty()) {
      throw new IllegalArgumentException(
          "Message contains line breaks, but IRCv3 multiline is not negotiated: " + serverId);
    }

    long maxLines = negotiatedMaxLines(connection);
    requireWithinMaxLines(maxLines, lines, serverId);
    long maxBytes = negotiatedMaxBytes(connection);
    requireWithinMaxBytes(maxBytes, lines, serverId);

    String batchId = "ml" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
    bot.sendRaw().rawLine("BATCH +" + batchId + " " + batchType + " " + sanitizedTarget);
    for (int i = 0; i < lines.size(); i++) {
      String line = Objects.toString(lines.get(i), "");
      boolean concat = i < lines.size() - 1;
      String tagPrefix = "@batch=" + batchId;
      if (concat) {
        tagPrefix = tagPrefix + ";+" + concatTag + "=1";
      }
      bot.sendRaw().rawLine(tagPrefix + " " + command + " " + sanitizedTarget + " :" + line);
    }
    bot.sendRaw().rawLine("BATCH -" + batchId);
  }

  static long negotiatedMaxBytes(PircbotxConnectionState connection) {
    return connection == null ? 0L : connection.capabilitySnapshot().negotiatedMultilineMaxBytes();
  }

  static long negotiatedMaxLines(PircbotxConnectionState connection) {
    return connection == null ? 0L : connection.capabilitySnapshot().negotiatedMultilineMaxLines();
  }

  static long multilinePayloadUtf8Bytes(List<String> lines) {
    if (lines == null || lines.isEmpty()) return 0L;
    long total = 0L;
    for (int i = 0; i < lines.size(); i++) {
      String line = Objects.toString(lines.get(i), "");
      total = addSaturated(total, utf8Length(line));
      if (i < lines.size() - 1) {
        total = addSaturated(total, 1L);
      }
    }
    return total;
  }

  static void requireWithinMaxBytes(long maxBytes, List<String> lines, String serverId) {
    if (maxBytes <= 0L) return;
    long payloadBytes = multilinePayloadUtf8Bytes(lines);
    if (payloadBytes <= maxBytes) return;
    throw new IllegalArgumentException(
        "Message exceeds negotiated IRCv3 multiline max-bytes "
            + payloadBytes
            + " > "
            + maxBytes
            + " for "
            + Objects.toString(serverId, "").trim());
  }

  static void requireWithinMaxLines(long maxLines, List<String> lines, String serverId) {
    if (maxLines <= 0L) return;
    long lineCount = lines == null ? 0L : lines.size();
    if (lineCount <= maxLines) return;
    throw new IllegalArgumentException(
        "Message exceeds negotiated IRCv3 multiline max-lines "
            + lineCount
            + " > "
            + maxLines
            + " for "
            + Objects.toString(serverId, "").trim());
  }

  private static void sendRawMessageLine(
      PircBotX bot, String command, String sanitizedTarget, String line) {
    String normalizedCommand = Objects.toString(command, "").trim().toUpperCase(Locale.ROOT);
    if (!"PRIVMSG".equals(normalizedCommand) && !"NOTICE".equals(normalizedCommand)) {
      throw new IllegalArgumentException("Unsupported message command: " + command);
    }
    String payload = Objects.toString(line, "");
    if (payload.indexOf('\r') >= 0 || payload.indexOf('\n') >= 0) {
      throw new IllegalArgumentException("message line contains CR/LF");
    }
    bot.sendRaw().rawLine(normalizedCommand + " " + sanitizedTarget + " :" + payload);
  }

  private static List<String> normalizeMessageLines(String raw) {
    String input = Objects.toString(raw, "");
    if (input.isEmpty()) return List.of();
    String normalized = input.replace("\r\n", "\n").replace('\r', '\n');
    if (normalized.indexOf('\n') < 0) {
      return List.of(normalized);
    }
    String[] parts = normalized.split("\n", -1);
    List<String> out = new ArrayList<>(parts.length);
    for (String part : parts) {
      out.add(Objects.toString(part, ""));
    }
    return out;
  }

  private static String multilineBatchType(PircbotxConnectionState connection) {
    if (connection == null) return "";
    PircbotxConnectionState.CapabilitySnapshot caps = connection.capabilitySnapshot();
    if (caps.multilineCapAcked()) return "multiline";
    if (caps.draftMultilineCapAcked()) return "draft/multiline";
    return "";
  }

  private static String multilineConcatTag(PircbotxConnectionState connection) {
    if (connection == null) return "";
    PircbotxConnectionState.CapabilitySnapshot caps = connection.capabilitySnapshot();
    if (caps.multilineCapAcked()) return "multiline-concat";
    if (caps.draftMultilineCapAcked()) return "draft/multiline-concat";
    return "";
  }

  private static long utf8Length(String value) {
    return Objects.toString(value, "").getBytes(StandardCharsets.UTF_8).length;
  }

  private static long addSaturated(long left, long right) {
    if (right <= 0L) return left;
    if (left >= Long.MAX_VALUE - right) return Long.MAX_VALUE;
    return left + right;
  }
}

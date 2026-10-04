package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.stripLeadingColon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Decodes the IRC envelopes carried inside Core display messages. */
record QuasselCoreIrcEnvelope(
    boolean parsed,
    String rawLine,
    String command,
    String source,
    List<String> params,
    String trailing,
    Map<String, String> ircv3Tags) {
  private static final Set<String> PARSED_COMMANDS =
      Set.of("PRIVMSG", "NOTICE", "TAGMSG", "MARKREAD", "REDACT", "CAP", "FAIL", "WARN", "NOTE");

  static QuasselCoreIrcEnvelope parse(String content, QuasselIrcv3RuntimeSupport runtime) {
    String line = Objects.toString(content, "").trim();
    if (line.isEmpty()) return QuasselCoreIrcEnvelope.empty(content);

    int idx = 0;
    Map<String, String> tags = Map.of();
    if (line.charAt(idx) == '@') {
      tags = runtime.messageTags(line);
      int sp = line.indexOf(' ');
      if (sp <= 0 || sp >= line.length() - 1) {
        return QuasselCoreIrcEnvelope.empty(content);
      }
      idx = sp + 1;
      while (idx < line.length() && line.charAt(idx) == ' ') idx++;
    }

    String source = "";
    if (idx < line.length() && line.charAt(idx) == ':') {
      int sp = line.indexOf(' ', idx);
      if (sp <= idx || sp >= line.length() - 1) {
        return QuasselCoreIrcEnvelope.empty(content);
      }
      source = line.substring(idx + 1, sp).trim();
      idx = sp + 1;
      while (idx < line.length() && line.charAt(idx) == ' ') idx++;
    }
    if (idx >= line.length()) {
      return QuasselCoreIrcEnvelope.empty(content);
    }

    int cmdStart = idx;
    while (idx < line.length() && line.charAt(idx) != ' ') idx++;
    String command = line.substring(cmdStart, idx).trim().toUpperCase(Locale.ROOT);
    boolean commandOfInterest = PARSED_COMMANDS.contains(command) || !tags.isEmpty();
    if (command.isBlank() || !commandOfInterest) {
      return QuasselCoreIrcEnvelope.empty(content);
    }

    ArrayList<String> params = new ArrayList<>();
    String trailing = "";
    while (idx < line.length()) {
      while (idx < line.length() && line.charAt(idx) == ' ') idx++;
      if (idx >= line.length()) break;
      if (line.charAt(idx) == ':') {
        trailing = line.substring(idx + 1);
        break;
      }
      int start = idx;
      while (idx < line.length() && line.charAt(idx) != ' ') idx++;
      String param = line.substring(start, idx).trim();
      if (!param.isEmpty()) {
        params.add(param);
      }
    }

    return new QuasselCoreIrcEnvelope(
        true,
        line,
        command,
        source,
        List.copyOf(params),
        trailing,
        tags == null || tags.isEmpty() ? Map.of() : tags);
  }

  private static QuasselCoreIrcEnvelope empty(String rawLine) {
    return new QuasselCoreIrcEnvelope(
        false, Objects.toString(rawLine, ""), "", "", List.of(), "", Map.of());
  }

  String firstParam() {
    if (params == null || params.isEmpty()) return "";
    return Objects.toString(params.get(0), "").trim();
  }

  String secondParam() {
    if (params == null || params.size() < 2) return "";
    return Objects.toString(params.get(1), "").trim();
  }

  String payloadText(String fallbackContent) {
    if (!parsed()) {
      return Objects.toString(fallbackContent, "");
    }
    if ("PRIVMSG".equals(command()) || "NOTICE".equals(command())) {
      if (!trailing().isBlank()) {
        return trailing();
      }
    }
    if ("TAGMSG".equals(command())) {
      return "";
    }
    return Objects.toString(fallbackContent, "");
  }

  String capSubcommand() {
    String sub = stripLeadingColon(secondParam());
    if (sub.isBlank()) {
      sub = stripLeadingColon(firstParam());
    }
    String action = sub.trim().toUpperCase(Locale.ROOT);
    if ("ACK".equals(action)
        || "DEL".equals(action)
        || "NAK".equals(action)
        || "NEW".equals(action)
        || "LS".equals(action)) {
      return action;
    }
    return "";
  }

  String capList() {
    String trailing = Objects.toString(trailing(), "").trim();
    if (!trailing.isEmpty()) {
      return trailing;
    }
    List<String> params = params();
    if (params == null || params.size() <= 2) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 2; i < params.size(); i++) {
      String token = stripLeadingColon(params.get(i));
      if (token.isBlank()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(token);
    }
    return sb.toString().trim();
  }
}

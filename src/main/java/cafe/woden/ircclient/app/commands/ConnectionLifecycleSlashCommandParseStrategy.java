package cafe.woden.ircclient.app.commands;

final class ConnectionLifecycleSlashCommandParseStrategy implements SlashCommandParseStrategy {

  @Override
  public ParsedInput tryParse(String line) {
    if (SlashCommandParsingSupport.matchesCommand(line, "/join")) {
      return SlashCommandParsingSupport.parseJoinInput(
          SlashCommandParsingSupport.argAfter(line, "/join"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/j")) {
      return SlashCommandParsingSupport.parseJoinInput(
          SlashCommandParsingSupport.argAfter(line, "/j"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/part")
        || SlashCommandParsingSupport.matchesCommand(line, "/leave")) {
      String rest =
          SlashCommandParsingSupport.matchesCommand(line, "/part")
              ? SlashCommandParsingSupport.argAfter(line, "/part")
              : SlashCommandParsingSupport.argAfter(line, "/leave");
      String r = rest == null ? "" : rest.trim();
      if (r.isEmpty()) return new ParsedInput.Part("", "");
      String first;
      String tail;
      int sp = r.indexOf(' ');
      if (sp < 0) {
        first = r;
        tail = "";
      } else {
        first = r.substring(0, sp).trim();
        tail = r.substring(sp + 1).trim();
      }
      if (SlashCommandParsingSupport.looksLikePartTarget(first)) {
        return new ParsedInput.Part(first, tail);
      }
      return new ParsedInput.Part("", r);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/connect")) {
      return new ParsedInput.Connect(SlashCommandParsingSupport.argAfter(line, "/connect"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/disconnect")) {
      return new ParsedInput.Disconnect(SlashCommandParsingSupport.argAfter(line, "/disconnect"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/reconnect")) {
      return new ParsedInput.Reconnect(SlashCommandParsingSupport.argAfter(line, "/reconnect"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/quit")) {
      return new ParsedInput.Quit(SlashCommandParsingSupport.argAfter(line, "/quit"));
    }

    return null;
  }
}

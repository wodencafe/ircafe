package cafe.woden.ircclient.app.commands;

final class IdentityMessagingSlashCommandParseStrategy implements SlashCommandParseStrategy {

  @Override
  public ParsedInput tryParse(String line) {
    if (SlashCommandParsingSupport.matchesCommand(line, "/nick")) {
      return new ParsedInput.Nick(SlashCommandParsingSupport.argAfter(line, "/nick"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/away")) {
      return new ParsedInput.Away(SlashCommandParsingSupport.argAfter(line, "/away"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/query")) {
      return new ParsedInput.Query(SlashCommandParsingSupport.argAfter(line, "/query"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/whois")) {
      return new ParsedInput.Whois(SlashCommandParsingSupport.argAfter(line, "/whois"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/whowas")) {
      return SlashCommandParsingSupport.parseWhowasInput(
          SlashCommandParsingSupport.argAfter(line, "/whowas"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/wi")) {
      return new ParsedInput.Whois(SlashCommandParsingSupport.argAfter(line, "/wi"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/msg")) {
      String rest = SlashCommandParsingSupport.argAfter(line, "/msg");
      int sp = rest.indexOf(' ');
      if (sp <= 0) return new ParsedInput.Msg(rest.trim(), "");
      String nick = rest.substring(0, sp).trim();
      String body = rest.substring(sp + 1).trim();
      return new ParsedInput.Msg(nick, body);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/notice")) {
      String rest = SlashCommandParsingSupport.argAfter(line, "/notice");
      int sp = rest.indexOf(' ');
      if (sp <= 0) return new ParsedInput.Notice(rest.trim(), "");
      String target = rest.substring(0, sp).trim();
      String body = rest.substring(sp + 1).trim();
      return new ParsedInput.Notice(target, body);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/me")) {
      return new ParsedInput.Me(SlashCommandParsingSupport.argAfter(line, "/me"));
    }

    return null;
  }
}

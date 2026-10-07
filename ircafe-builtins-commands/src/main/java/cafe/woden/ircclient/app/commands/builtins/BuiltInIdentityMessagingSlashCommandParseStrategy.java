package cafe.woden.ircclient.app.commands.builtins;

import cafe.woden.ircclient.app.commands.spi.SlashCommandParseResult;
import cafe.woden.ircclient.app.commands.spi.SlashCommandParseStrategy;
import com.google.auto.service.AutoService;

/** Built-in parser for identity and direct messaging slash commands. */
@AutoService(SlashCommandParseStrategy.class)
public final class BuiltInIdentityMessagingSlashCommandParseStrategy
    implements SlashCommandParseStrategy {

  @Override
  public SlashCommandParseResult tryParse(String line) {
    if (BuiltInSlashCommandParsingSupport.matchesCommand(line, "/nick")) {
      return SlashCommandParseResult.command(
          "nick", BuiltInSlashCommandParsingSupport.argAfter(line, "/nick"));
    }

    if (BuiltInSlashCommandParsingSupport.matchesCommand(line, "/away")) {
      return SlashCommandParseResult.command(
          "away", BuiltInSlashCommandParsingSupport.argAfter(line, "/away"));
    }

    if (BuiltInSlashCommandParsingSupport.matchesCommand(line, "/query")) {
      return SlashCommandParseResult.command(
          "query", BuiltInSlashCommandParsingSupport.argAfter(line, "/query"));
    }

    if (BuiltInSlashCommandParsingSupport.matchesCommand(line, "/whois")) {
      return SlashCommandParseResult.command(
          "whois", BuiltInSlashCommandParsingSupport.argAfter(line, "/whois"));
    }

    if (BuiltInSlashCommandParsingSupport.matchesCommand(line, "/whowas")) {
      return BuiltInSlashCommandParsingSupport.parseWhowasInput(
          BuiltInSlashCommandParsingSupport.argAfter(line, "/whowas"));
    }

    if (BuiltInSlashCommandParsingSupport.matchesCommand(line, "/wi")) {
      return SlashCommandParseResult.command(
          "whois", BuiltInSlashCommandParsingSupport.argAfter(line, "/wi"));
    }

    for (String command : new String[] {"/cs", "/chanserv"}) {
      if (BuiltInSlashCommandParsingSupport.matchesCommand(line, command)) {
        String body = BuiltInSlashCommandParsingSupport.argAfter(line, command);
        return SlashCommandParseResult.command("msg", "ChanServ", body.isEmpty() ? "HELP" : body);
      }
    }

    for (String command : new String[] {"/ns", "/nickserv"}) {
      if (BuiltInSlashCommandParsingSupport.matchesCommand(line, command)) {
        String body = BuiltInSlashCommandParsingSupport.argAfter(line, command);
        return SlashCommandParseResult.command("msg", "NickServ", body.isEmpty() ? "HELP" : body);
      }
    }

    if (BuiltInSlashCommandParsingSupport.matchesCommand(line, "/msg")) {
      String rest = BuiltInSlashCommandParsingSupport.argAfter(line, "/msg");
      int sp = rest.indexOf(' ');
      if (sp <= 0) return SlashCommandParseResult.command("msg", rest.trim(), "");
      String nick = rest.substring(0, sp).trim();
      String body = rest.substring(sp + 1).trim();
      return SlashCommandParseResult.command("msg", nick, body);
    }

    if (BuiltInSlashCommandParsingSupport.matchesCommand(line, "/notice")) {
      String rest = BuiltInSlashCommandParsingSupport.argAfter(line, "/notice");
      int sp = rest.indexOf(' ');
      if (sp <= 0) return SlashCommandParseResult.command("notice", rest.trim(), "");
      String target = rest.substring(0, sp).trim();
      String body = rest.substring(sp + 1).trim();
      return SlashCommandParseResult.command("notice", target, body);
    }

    if (BuiltInSlashCommandParsingSupport.matchesCommand(line, "/me")) {
      return SlashCommandParseResult.command(
          "me", BuiltInSlashCommandParsingSupport.argAfter(line, "/me"));
    }

    return null;
  }
}

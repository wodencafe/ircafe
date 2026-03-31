package cafe.woden.ircclient.app.commands;

import java.util.Objects;

final class AdvancedFeatureSlashCommandParseStrategy implements SlashCommandParseStrategy {

  private final FilterCommandParser filterCommandParser;

  AdvancedFeatureSlashCommandParseStrategy(FilterCommandParser filterCommandParser) {
    this.filterCommandParser = Objects.requireNonNull(filterCommandParser, "filterCommandParser");
  }

  @Override
  public ParsedInput tryParse(String line) {
    if (SlashCommandParsingSupport.matchesCommand(line, "/ignore")) {
      return new ParsedInput.Ignore(SlashCommandParsingSupport.argAfter(line, "/ignore"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/unignore")) {
      return new ParsedInput.Unignore(SlashCommandParsingSupport.argAfter(line, "/unignore"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/ignorelist")
        || SlashCommandParsingSupport.matchesCommand(line, "/ignores")) {
      return new ParsedInput.IgnoreList();
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/softignore")) {
      return new ParsedInput.SoftIgnore(SlashCommandParsingSupport.argAfter(line, "/softignore"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/unsoftignore")) {
      return new ParsedInput.UnsoftIgnore(
          SlashCommandParsingSupport.argAfter(line, "/unsoftignore"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/softignorelist")
        || SlashCommandParsingSupport.matchesCommand(line, "/softignores")) {
      return new ParsedInput.SoftIgnoreList();
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/version")) {
      return new ParsedInput.CtcpVersion(SlashCommandParsingSupport.argAfter(line, "/version"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/ping")) {
      return new ParsedInput.CtcpPing(SlashCommandParsingSupport.argAfter(line, "/ping"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/time")) {
      return new ParsedInput.CtcpTime(SlashCommandParsingSupport.argAfter(line, "/time"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/ctcp")) {
      String rest = SlashCommandParsingSupport.argAfter(line, "/ctcp");
      String nick = "";
      String cmd = "";
      String args = "";

      int sp1 = rest.indexOf(' ');
      if (sp1 < 0) {
        nick = rest.trim();
      } else {
        nick = rest.substring(0, sp1).trim();
        String rest2 = rest.substring(sp1 + 1).trim();
        int sp2 = rest2.indexOf(' ');
        if (sp2 < 0) {
          cmd = rest2.trim();
        } else {
          cmd = rest2.substring(0, sp2).trim();
          args = rest2.substring(sp2 + 1).trim();
        }
      }

      return new ParsedInput.Ctcp(nick, cmd, args);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/dcc")) {
      return SlashCommandParsingSupport.parseDccInput(
          SlashCommandParsingSupport.argAfter(line, "/dcc"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/dccmsg")) {
      String rest = SlashCommandParsingSupport.argAfter(line, "/dccmsg");
      int sp = rest.indexOf(' ');
      if (sp <= 0) return new ParsedInput.Dcc("msg", rest.trim(), "");
      String nick = rest.substring(0, sp).trim();
      String text = rest.substring(sp + 1).trim();
      return new ParsedInput.Dcc("msg", nick, text);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/chathistory")
        || SlashCommandParsingSupport.matchesCommand(line, "/history")) {
      String rest =
          SlashCommandParsingSupport.matchesCommand(line, "/chathistory")
              ? SlashCommandParsingSupport.argAfter(line, "/chathistory")
              : SlashCommandParsingSupport.argAfter(line, "/history");
      return SlashCommandParsingSupport.parseChatHistoryInput(rest);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/markread")) {
      return new ParsedInput.MarkRead();
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/help")
        || SlashCommandParsingSupport.matchesCommand(line, "/commands")) {
      String topic =
          SlashCommandParsingSupport.matchesCommand(line, "/help")
              ? SlashCommandParsingSupport.argAfter(line, "/help")
              : SlashCommandParsingSupport.argAfter(line, "/commands");
      return new ParsedInput.Help(topic);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/upload")) {
      return SlashCommandParsingSupport.parseUploadInput(
          SlashCommandParsingSupport.argAfter(line, "/upload"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/reply")) {
      return SlashCommandParsingSupport.parseReplyInput(
          SlashCommandParsingSupport.argAfter(line, "/reply"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/react")) {
      return SlashCommandParsingSupport.parseReactInput(
          SlashCommandParsingSupport.argAfter(line, "/react"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/unreact")) {
      return SlashCommandParsingSupport.parseUnreactInput(
          SlashCommandParsingSupport.argAfter(line, "/unreact"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/edit")) {
      return SlashCommandParsingSupport.parseEditInput(
          SlashCommandParsingSupport.argAfter(line, "/edit"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/redact")) {
      return SlashCommandParsingSupport.parseRedactInput(
          SlashCommandParsingSupport.argAfter(line, "/redact"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/delete")) {
      return SlashCommandParsingSupport.parseRedactInput(
          SlashCommandParsingSupport.argAfter(line, "/delete"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/filter")) {
      return new ParsedInput.Filter(filterCommandParser.parse(line));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/quote")) {
      return new ParsedInput.Quote(SlashCommandParsingSupport.argAfter(line, "/quote"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/raw")) {
      return new ParsedInput.Quote(SlashCommandParsingSupport.argAfter(line, "/raw"));
    }

    return null;
  }
}

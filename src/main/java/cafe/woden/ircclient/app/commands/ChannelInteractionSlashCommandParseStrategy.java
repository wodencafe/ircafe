package cafe.woden.ircclient.app.commands;

final class ChannelInteractionSlashCommandParseStrategy implements SlashCommandParseStrategy {

  @Override
  public ParsedInput tryParse(String line) {
    if (SlashCommandParsingSupport.matchesCommand(line, "/topic")) {
      String rest = SlashCommandParsingSupport.argAfter(line, "/topic");
      if (rest.isEmpty()) return new ParsedInput.Topic("", "");
      int sp = rest.indexOf(' ');
      if (sp < 0) return new ParsedInput.Topic(rest.trim(), "");
      String first = rest.substring(0, sp).trim();
      String tail = rest.substring(sp + 1).trim();
      return new ParsedInput.Topic(first, tail);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/kick")) {
      SlashCommandParsingSupport.ParsedKick parsed =
          SlashCommandParsingSupport.parseKickArgs(
              SlashCommandParsingSupport.argAfter(line, "/kick"));
      return new ParsedInput.Kick(parsed.channel(), parsed.nick(), parsed.reason());
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/invite")) {
      String rest = SlashCommandParsingSupport.argAfter(line, "/invite");
      String r = rest == null ? "" : rest.trim();
      if (r.isEmpty()) return new ParsedInput.Invite("", "");
      String[] toks = r.split("\\s+", 3);
      String nick = toks.length > 0 ? toks[0].trim() : "";
      String channel = toks.length > 1 ? toks[1].trim() : "";
      return new ParsedInput.Invite(nick, channel);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/invites")) {
      return new ParsedInput.InviteList(SlashCommandParsingSupport.argAfter(line, "/invites"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/invjoin")
        || SlashCommandParsingSupport.matchesCommand(line, "/invitejoin")) {
      String token =
          SlashCommandParsingSupport.matchesCommand(line, "/invjoin")
              ? SlashCommandParsingSupport.argAfter(line, "/invjoin")
              : SlashCommandParsingSupport.argAfter(line, "/invitejoin");
      return new ParsedInput.InviteJoin(token);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/invignore")
        || SlashCommandParsingSupport.matchesCommand(line, "/inviteignore")) {
      String token =
          SlashCommandParsingSupport.matchesCommand(line, "/invignore")
              ? SlashCommandParsingSupport.argAfter(line, "/invignore")
              : SlashCommandParsingSupport.argAfter(line, "/inviteignore");
      return new ParsedInput.InviteIgnore(token);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/invwhois")
        || SlashCommandParsingSupport.matchesCommand(line, "/invitewhois")) {
      String token =
          SlashCommandParsingSupport.matchesCommand(line, "/invwhois")
              ? SlashCommandParsingSupport.argAfter(line, "/invwhois")
              : SlashCommandParsingSupport.argAfter(line, "/invitewhois");
      return new ParsedInput.InviteWhois(token);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/invblock")
        || SlashCommandParsingSupport.matchesCommand(line, "/inviteblock")) {
      String token =
          SlashCommandParsingSupport.matchesCommand(line, "/invblock")
              ? SlashCommandParsingSupport.argAfter(line, "/invblock")
              : SlashCommandParsingSupport.argAfter(line, "/inviteblock");
      return new ParsedInput.InviteBlock(token);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/inviteautojoin")
        || SlashCommandParsingSupport.matchesCommand(line, "/invautojoin")
        || SlashCommandParsingSupport.matchesCommand(line, "/ajinvite")) {
      String mode;
      if (SlashCommandParsingSupport.matchesCommand(line, "/inviteautojoin")) {
        mode = SlashCommandParsingSupport.argAfter(line, "/inviteautojoin");
      } else if (SlashCommandParsingSupport.matchesCommand(line, "/invautojoin")) {
        mode = SlashCommandParsingSupport.argAfter(line, "/invautojoin");
      } else {
        mode = SlashCommandParsingSupport.argAfter(line, "/ajinvite");
        if (mode.isEmpty()) mode = "toggle";
      }
      return new ParsedInput.InviteAutoJoin(mode);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/names")) {
      return new ParsedInput.Names(SlashCommandParsingSupport.argAfter(line, "/names"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/who")) {
      return new ParsedInput.Who(SlashCommandParsingSupport.argAfter(line, "/who"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/list")) {
      return new ParsedInput.ListCmd(SlashCommandParsingSupport.argAfter(line, "/list"));
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/monitor")
        || SlashCommandParsingSupport.matchesCommand(line, "/mon")) {
      String args =
          SlashCommandParsingSupport.matchesCommand(line, "/monitor")
              ? SlashCommandParsingSupport.argAfter(line, "/monitor")
              : SlashCommandParsingSupport.argAfter(line, "/mon");
      return new ParsedInput.Monitor(args);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/mode")) {
      String rest = SlashCommandParsingSupport.argAfter(line, "/mode");
      if (rest.isEmpty()) return new ParsedInput.Mode("", "");
      int sp = rest.indexOf(' ');
      if (sp < 0) return new ParsedInput.Mode(rest.trim(), "");
      String first = rest.substring(0, sp).trim();
      String tail = rest.substring(sp + 1).trim();
      return new ParsedInput.Mode(first, tail);
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/op")) {
      SlashCommandParsingSupport.ParsedTargetList parsed =
          SlashCommandParsingSupport.parseTargetList(
              SlashCommandParsingSupport.argAfter(line, "/op"));
      return new ParsedInput.Op(parsed.channel(), parsed.items());
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/deop")) {
      SlashCommandParsingSupport.ParsedTargetList parsed =
          SlashCommandParsingSupport.parseTargetList(
              SlashCommandParsingSupport.argAfter(line, "/deop"));
      return new ParsedInput.Deop(parsed.channel(), parsed.items());
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/voice")) {
      SlashCommandParsingSupport.ParsedTargetList parsed =
          SlashCommandParsingSupport.parseTargetList(
              SlashCommandParsingSupport.argAfter(line, "/voice"));
      return new ParsedInput.Voice(parsed.channel(), parsed.items());
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/devoice")) {
      SlashCommandParsingSupport.ParsedTargetList parsed =
          SlashCommandParsingSupport.parseTargetList(
              SlashCommandParsingSupport.argAfter(line, "/devoice"));
      return new ParsedInput.Devoice(parsed.channel(), parsed.items());
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/ban")) {
      SlashCommandParsingSupport.ParsedTargetList parsed =
          SlashCommandParsingSupport.parseTargetList(
              SlashCommandParsingSupport.argAfter(line, "/ban"));
      return new ParsedInput.Ban(parsed.channel(), parsed.items());
    }

    if (SlashCommandParsingSupport.matchesCommand(line, "/unban")) {
      SlashCommandParsingSupport.ParsedTargetList parsed =
          SlashCommandParsingSupport.parseTargetList(
              SlashCommandParsingSupport.argAfter(line, "/unban"));
      return new ParsedInput.Unban(parsed.channel(), parsed.items());
    }

    return null;
  }
}

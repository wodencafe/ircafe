package cafe.woden.ircclient.app.commands;

import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared presentation metadata for built-in slash commands handled by the typed parser path. */
@Component
@ApplicationLayer
public final class CoreSlashCommandPresentationContributor
    implements SlashCommandPresentationContributor {

  private static final List<SlashCommandDescriptor> COMMANDS =
      List.of(
          new SlashCommandDescriptor("/join", "Join channel"),
          new SlashCommandDescriptor("/j", "Alias: /join"),
          new SlashCommandDescriptor("/part", "Leave channel"),
          new SlashCommandDescriptor("/leave", "Alias: /part"),
          new SlashCommandDescriptor("/connect", "Connect server/all"),
          new SlashCommandDescriptor("/disconnect", "Disconnect server/all"),
          new SlashCommandDescriptor("/reconnect", "Reconnect server/all"),
          new SlashCommandDescriptor("/quit", "Disconnect all and quit"),
          new SlashCommandDescriptor("/nick", "Change nickname"),
          new SlashCommandDescriptor("/away", "Set/remove away status"),
          new SlashCommandDescriptor("/query", "Open private message"),
          new SlashCommandDescriptor("/whois", "WHOIS lookup"),
          new SlashCommandDescriptor("/wi", "Alias: /whois"),
          new SlashCommandDescriptor("/whowas", "WHOWAS lookup"),
          new SlashCommandDescriptor("/msg", "Send private message"),
          new SlashCommandDescriptor("/notice", "Send notice"),
          new SlashCommandDescriptor("/me", "Send action"),
          new SlashCommandDescriptor("/topic", "View/change topic"),
          new SlashCommandDescriptor("/kick", "Kick user"),
          new SlashCommandDescriptor("/invite", "Invite user"),
          new SlashCommandDescriptor("/invites", "List pending invites"),
          new SlashCommandDescriptor("/invjoin", "Join pending invite"),
          new SlashCommandDescriptor("/invitejoin", "Alias: /invjoin"),
          new SlashCommandDescriptor("/invignore", "Ignore pending invite"),
          new SlashCommandDescriptor("/inviteignore", "Alias: /invignore"),
          new SlashCommandDescriptor("/invwhois", "WHOIS inviter from invite"),
          new SlashCommandDescriptor("/invitewhois", "Alias: /invwhois"),
          new SlashCommandDescriptor("/invblock", "Block inviter nick"),
          new SlashCommandDescriptor("/inviteblock", "Alias: /invblock"),
          new SlashCommandDescriptor("/inviteautojoin", "Toggle invite auto-join"),
          new SlashCommandDescriptor("/invautojoin", "Alias: /inviteautojoin"),
          new SlashCommandDescriptor("/ajinvite", "Alias: /inviteautojoin (toggle)"),
          new SlashCommandDescriptor("/names", "Request NAMES"),
          new SlashCommandDescriptor("/who", "Request WHO"),
          new SlashCommandDescriptor("/list", "Request LIST"),
          new SlashCommandDescriptor("/mode", "Set/query mode"),
          new SlashCommandDescriptor("/op", "Grant op"),
          new SlashCommandDescriptor("/deop", "Remove op"),
          new SlashCommandDescriptor("/voice", "Grant voice"),
          new SlashCommandDescriptor("/devoice", "Remove voice"),
          new SlashCommandDescriptor("/ban", "Set ban"),
          new SlashCommandDescriptor("/unban", "Remove ban"),
          new SlashCommandDescriptor("/ignore", "Add hard ignore"),
          new SlashCommandDescriptor("/unignore", "Remove hard ignore"),
          new SlashCommandDescriptor("/ignorelist", "Show hard ignores"),
          new SlashCommandDescriptor("/ignores", "Alias: /ignorelist"),
          new SlashCommandDescriptor("/softignore", "Add soft ignore"),
          new SlashCommandDescriptor("/unsoftignore", "Remove soft ignore"),
          new SlashCommandDescriptor("/softignorelist", "Show soft ignores"),
          new SlashCommandDescriptor("/softignores", "Alias: /softignorelist"),
          new SlashCommandDescriptor("/version", "CTCP VERSION"),
          new SlashCommandDescriptor("/ping", "CTCP PING"),
          new SlashCommandDescriptor("/time", "CTCP TIME"),
          new SlashCommandDescriptor("/ctcp", "Send CTCP"),
          new SlashCommandDescriptor("/dcc", "DCC command"),
          new SlashCommandDescriptor("/dccmsg", "DCC message"),
          new SlashCommandDescriptor("/chathistory", "IRCv3 CHATHISTORY"),
          new SlashCommandDescriptor("/history", "Alias: /chathistory"),
          new SlashCommandDescriptor("/markread", "Set read marker for current target"),
          new SlashCommandDescriptor("/help", "Show command help"),
          new SlashCommandDescriptor("/commands", "Alias: /help"),
          new SlashCommandDescriptor("/upload", "Upload or send media"),
          new SlashCommandDescriptor("/reply", "Reply to message-id"),
          new SlashCommandDescriptor("/react", "React to message-id"),
          new SlashCommandDescriptor("/unreact", "Remove reaction from message-id"),
          new SlashCommandDescriptor("/edit", "Edit message-id"),
          new SlashCommandDescriptor("/redact", "Redact message-id"),
          new SlashCommandDescriptor("/delete", "Alias: /redact"),
          new SlashCommandDescriptor("/filter", "Local filtering controls"),
          new SlashCommandDescriptor("/quote", "Send raw IRC line"),
          new SlashCommandDescriptor("/raw", "Alias: /quote"));

  @Override
  public List<SlashCommandDescriptor> autocompleteCommands() {
    return COMMANDS;
  }
}

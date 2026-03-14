package cafe.woden.ircclient.irc.pircbotx.parse;

public record ParsedInviteLine(
    String fromNick, String inviteeNick, String channel, String reason) {}

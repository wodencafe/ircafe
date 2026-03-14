package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import org.junit.jupiter.api.Test;

class PircbotxInboundLineParsersTest {

  @Test
  void parseIrcLineExtractsPrefixParamsAndTrailing() {
    ParsedIrcLine parsed =
        PircbotxInboundLineParsers.parseIrcLine(":nick!user@example PRIVMSG #ircafe :hello world");

    assertNotNull(parsed);
    assertEquals("nick!user@example", parsed.prefix());
    assertEquals("PRIVMSG", parsed.command());
    assertEquals("#ircafe", parsed.params().getFirst());
    assertEquals("hello world", parsed.trailing());
  }

  @Test
  void rawTrailingFromIrcLineHandlesTaggedMessages() {
    String trailing =
        PircbotxInboundLineParsers.rawTrailingFromIrcLine(
            "@msgid=abc123 :nick!user@example PRIVMSG #ircafe :payload here");

    assertEquals("payload here", trailing);
  }

  @Test
  void parseInviteLineExtractsChannelAndReason() {
    ParsedIrcLine parsed =
        PircbotxInboundLineParsers.parseIrcLine(
            ":ChanServ!service@example INVITE chris #ircafe :Join us");

    ParsedInviteLine invite = PircbotxInboundLineParsers.parseInviteLine(parsed);

    assertNotNull(invite);
    assertEquals("ChanServ", invite.fromNick());
    assertEquals("chris", invite.inviteeNick());
    assertEquals("#ircafe", invite.channel());
    assertEquals("Join us", invite.reason());
  }

  @Test
  void parseWallopsLineFallsBackToServerPrefix() {
    ParsedIrcLine parsed =
        PircbotxInboundLineParsers.parseIrcLine(":server.example WALLOPS :Maintenance soon");

    ParsedWallopsLine wallops = PircbotxInboundLineParsers.parseWallopsLine(parsed);

    assertNotNull(wallops);
    assertEquals("server.example", wallops.from());
    assertEquals("Maintenance soon", wallops.message());
  }

  @Test
  void parseJoinFailureFindsChannelAndMessage() {
    ParsedJoinFailure failure =
        PircbotxInboundLineParsers.parseJoinFailure(
            ":server.example 473 chris #locked :Cannot join channel (+i)");

    assertNotNull(failure);
    assertEquals("#locked", failure.channel());
    assertEquals("Cannot join channel (+i)", failure.message());
  }

  @Test
  void parseChannelRedirectFindsSourceAndTargetChannels() {
    ParsedChannelRedirect redirect =
        PircbotxInboundLineParsers.parseChannelRedirect(
            ":server.example 470 chris #from #to :Forwarding to another channel");

    assertNotNull(redirect);
    assertEquals("#from", redirect.fromChannel());
    assertEquals("#to", redirect.toChannel());
    assertEquals("Forwarding to another channel", redirect.message());
  }
}

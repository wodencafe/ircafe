package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CommandParserTest {

  private final CommandParser parser = new CommandParser(new FilterCommandParser());

  @Test
  void parsesJoinWithOptionalKey() {
    ParsedInput in = parser.parse("/join #secret hunter2");
    assertTrue(in instanceof ParsedInput.Join);
    ParsedInput.Join join = (ParsedInput.Join) in;
    assertEquals("#secret", join.channel());
    assertEquals("hunter2", join.key());
  }

  @Test
  void parsesConnectionLifecycleCommands() {
    ParsedInput connect = parser.parse("/connect libera");
    assertTrue(connect instanceof ParsedInput.Connect);
    assertEquals("libera", ((ParsedInput.Connect) connect).target());

    ParsedInput disconnect = parser.parse("/disconnect all");
    assertTrue(disconnect instanceof ParsedInput.Disconnect);
    assertEquals("all", ((ParsedInput.Disconnect) disconnect).target());

    ParsedInput reconnect = parser.parse("/reconnect");
    assertTrue(reconnect instanceof ParsedInput.Reconnect);
    assertEquals("", ((ParsedInput.Reconnect) reconnect).target());

    ParsedInput quit = parser.parse("/quit gone for lunch");
    assertTrue(quit instanceof ParsedInput.Quit);
    assertEquals("gone for lunch", ((ParsedInput.Quit) quit).reason());
  }

  @Test
  void parsesWhowasWithOptionalCount() {
    ParsedInput in = parser.parse("/whowas oldNick 3");
    assertTrue(in instanceof ParsedInput.Whowas);
    ParsedInput.Whowas whowas = (ParsedInput.Whowas) in;
    assertEquals("oldNick", whowas.nick());
    assertEquals(3, whowas.count());
  }

  @Test
  void parsesAwayWithMessage() {
    ParsedInput in = parser.parse("/away out to lunch");
    assertTrue(in instanceof ParsedInput.Away);
    assertEquals("out to lunch", ((ParsedInput.Away) in).message());
  }

  @Test
  void parsesAwayWithoutMessage() {
    ParsedInput in = parser.parse("/away");
    assertTrue(in instanceof ParsedInput.Away);
    assertEquals("", ((ParsedInput.Away) in).message());
  }

  @Test
  void parsesAwayWithOnlyWhitespaceAsBlank() {
    ParsedInput in = parser.parse("/away   ");
    assertTrue(in instanceof ParsedInput.Away);
    assertEquals("", ((ParsedInput.Away) in).message());
  }

  @Test
  void parsesFilterCommand() {
    ParsedInput in = parser.parse("/filter help");
    assertTrue(in instanceof ParsedInput.Filter);
    assertTrue(((ParsedInput.Filter) in).command() instanceof FilterCommand.Help);
  }

  @Test
  void parsesRawAliasAsQuote() {
    ParsedInput in = parser.parse("/raw PRIVMSG #chan :hi");
    assertTrue(in instanceof ParsedInput.Quote);
    assertEquals("PRIVMSG #chan :hi", ((ParsedInput.Quote) in).rawLine());
  }

  @Test
  void parsesWhoisAliasWi() {
    ParsedInput in = parser.parse("/wi someNick");
    assertTrue(in instanceof ParsedInput.Whois);
    assertEquals("someNick", ((ParsedInput.Whois) in).nick());
  }

  @Test
  void parsesKickWithExplicitChannelAndReason() {
    ParsedInput in = parser.parse("/kick #room troublemaker too loud");
    assertTrue(in instanceof ParsedInput.Kick);
    ParsedInput.Kick kick = (ParsedInput.Kick) in;
    assertEquals("#room", kick.channel());
    assertEquals("troublemaker", kick.nick());
    assertEquals("too loud", kick.reason());
  }

  @Test
  void parsesInviteWithOptionalChannel() {
    ParsedInput in = parser.parse("/invite buddy #room");
    assertTrue(in instanceof ParsedInput.Invite);
    ParsedInput.Invite invite = (ParsedInput.Invite) in;
    assertEquals("buddy", invite.nick());
    assertEquals("#room", invite.channel());
  }

  @Test
  void parsesWhoAndListArgs() {
    ParsedInput who = parser.parse("/who #room o");
    assertTrue(who instanceof ParsedInput.Who);
    assertEquals("#room o", ((ParsedInput.Who) who).args());

    ParsedInput list = parser.parse("/list >10");
    assertTrue(list instanceof ParsedInput.ListCmd);
    assertEquals(">10", ((ParsedInput.ListCmd) list).args());
  }

  @Test
  void parsesChatHistoryLimitOnly() {
    ParsedInput in = parser.parse("/chathistory 120");
    assertTrue(in instanceof ParsedInput.ChatHistoryBefore);
    ParsedInput.ChatHistoryBefore ch = (ParsedInput.ChatHistoryBefore) in;
    assertEquals(120, ch.limit());
    assertEquals("", ch.selector());
  }

  @Test
  void parsesChatHistoryMsgidSelectorAndLimit() {
    ParsedInput in = parser.parse("/history msgid=abc123 75");
    assertTrue(in instanceof ParsedInput.ChatHistoryBefore);
    ParsedInput.ChatHistoryBefore ch = (ParsedInput.ChatHistoryBefore) in;
    assertEquals(75, ch.limit());
    assertEquals("msgid=abc123", ch.selector());
  }

  @Test
  void parsesChatHistoryBeforeTimestampSelector() {
    ParsedInput in = parser.parse("/chathistory before timestamp=2026-02-16T12:34:56.000Z 50");
    assertTrue(in instanceof ParsedInput.ChatHistoryBefore);
    ParsedInput.ChatHistoryBefore ch = (ParsedInput.ChatHistoryBefore) in;
    assertEquals(50, ch.limit());
    assertEquals("timestamp=2026-02-16T12:34:56.000Z", ch.selector());
  }

  @Test
  void rejectsChatHistoryUnknownSelector() {
    ParsedInput in = parser.parse("/chathistory cursor=abc 50");
    assertTrue(in instanceof ParsedInput.ChatHistoryBefore);
    ParsedInput.ChatHistoryBefore ch = (ParsedInput.ChatHistoryBefore) in;
    assertEquals(0, ch.limit());
  }

  @Test
  void parsesChatHistoryLatest() {
    ParsedInput in = parser.parse("/chathistory latest * 80");
    assertTrue(in instanceof ParsedInput.ChatHistoryLatest);
    ParsedInput.ChatHistoryLatest ch = (ParsedInput.ChatHistoryLatest) in;
    assertEquals(80, ch.limit());
    assertEquals("*", ch.selector());
  }

  @Test
  void parsesChatHistoryAround() {
    ParsedInput in = parser.parse("/history around msgid=abc123 40");
    assertTrue(in instanceof ParsedInput.ChatHistoryAround);
    ParsedInput.ChatHistoryAround ch = (ParsedInput.ChatHistoryAround) in;
    assertEquals(40, ch.limit());
    assertEquals("msgid=abc123", ch.selector());
  }

  @Test
  void parsesChatHistoryBetween() {
    ParsedInput in = parser.parse("/chathistory between timestamp=2026-02-16T00:00:00.000Z * 60");
    assertTrue(in instanceof ParsedInput.ChatHistoryBetween);
    ParsedInput.ChatHistoryBetween ch = (ParsedInput.ChatHistoryBetween) in;
    assertEquals("timestamp=2026-02-16T00:00:00.000Z", ch.startSelector());
    assertEquals("*", ch.endSelector());
    assertEquals(60, ch.limit());
  }

  @Test
  void rejectsChatHistoryAroundWithoutSelector() {
    ParsedInput in = parser.parse("/chathistory around 40");
    assertTrue(in instanceof ParsedInput.ChatHistoryAround);
    ParsedInput.ChatHistoryAround ch = (ParsedInput.ChatHistoryAround) in;
    assertEquals(0, ch.limit());
  }

  @Test
  void parsesReplyComposeCommand() {
    ParsedInput in = parser.parse("/reply abc123 hello there");
    assertTrue(in instanceof ParsedInput.ReplyMessage);
    ParsedInput.ReplyMessage cmd = (ParsedInput.ReplyMessage) in;
    assertEquals("abc123", cmd.messageId());
    assertEquals("hello there", cmd.body());
  }

  @Test
  void parsesReactComposeCommand() {
    ParsedInput in = parser.parse("/react abc123 :+1:");
    assertTrue(in instanceof ParsedInput.ReactMessage);
    ParsedInput.ReactMessage cmd = (ParsedInput.ReactMessage) in;
    assertEquals("abc123", cmd.messageId());
    assertEquals(":+1:", cmd.reaction());
  }
}

package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.app.commands.builtins.BuiltInQuasselBackendNamedCommandHandler;
import cafe.woden.ircclient.app.commands.spi.BuiltInBackendNamedCommandNames;
import cafe.woden.ircclient.app.commands.spi.SlashCommandParseResult;
import cafe.woden.ircclient.app.commands.spi.SlashCommandParseStrategy;
import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.config.plugins.InstalledPluginServices;
import cafe.woden.ircclient.util.CompiledPluginJarSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommandParserTest {

  private static final String PLUGIN_PARSE_STRATEGY_CLASS =
      "plugin.commands.PluginQuoteSlashCommandParseStrategy";
  private static final String SPI_PLUGIN_PARSE_STRATEGY_CLASS =
      "plugin.commands.PluginSpiQuoteSlashCommandParseStrategy";

  @TempDir Path tempDir;

  private final CommandParser parser =
      new CommandParser(
          new FilterCommandParser(),
          new BackendNamedCommandParser(List.of(new BuiltInQuasselBackendNamedCommandHandler())));

  @Test
  void includesSlashParseStrategiesLoadedThroughInstalledPluginPort() {
    CommandParser pluginParser =
        new CommandParser(
            new FilterCommandParser(),
            new BackendNamedCommandParser(List.of()),
            new RecordingInstalledPluginsPort(List.of(new PluginQuoteSlashCommandParseStrategy())));

    ParsedInput in = pluginParser.parse("/pluginquote RAW TEST");

    assertTrue(in instanceof ParsedInput.Quote);
    assertEquals("RAW TEST", ((ParsedInput.Quote) in).rawLine());
  }

  @Test
  void appOwnedFilterParserRunsBeforePluginSlashStrategies() {
    SlashCommandParseStrategy catchAllPluginStrategy =
        line ->
            line != null && line.startsWith("/") ? SlashCommandParseResult.quote("plugin") : null;
    CommandParser pluginParser =
        new CommandParser(
            new FilterCommandParser(),
            new BackendNamedCommandParser(List.of()),
            new RecordingInstalledPluginsPort(List.of(catchAllPluginStrategy)));

    ParsedInput in = pluginParser.parse("/filter help");

    assertTrue(in instanceof ParsedInput.Filter);
  }

  @Test
  void loadsServiceLoaderSlashParseStrategiesFromInstalledPlugins() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("example-slash-parser.jar"),
        PLUGIN_PARSE_STRATEGY_CLASS,
        pluginParseStrategySource(),
        SlashCommandParseStrategy.class.getName(),
        CompiledPluginJarSupport.compatibleManifest("example-slash-parser", "1.0.0"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    CommandParser pluginParser =
        new CommandParser(
            new FilterCommandParser(), new BackendNamedCommandParser(List.of()), installedPlugins);

    ParsedInput in = pluginParser.parse("/pluginquote RAW TEST");

    assertTrue(installedPlugins.pluginProblems().isEmpty());
    assertTrue(in instanceof ParsedInput.Quote);
    assertEquals("RAW TEST", ((ParsedInput.Quote) in).rawLine());
  }

  @Test
  void loadsServiceLoaderSlashParseStrategyProvidersFromInstalledPlugins() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("example-slash-parser-spi.jar"),
        SPI_PLUGIN_PARSE_STRATEGY_CLASS,
        pluginSpiParseStrategySource(),
        cafe.woden.ircclient.app.commands.spi.SlashCommandParseStrategy.class.getName(),
        CompiledPluginJarSupport.compatibleManifest("example-slash-parser-spi", "1.0.0"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    CommandParser pluginParser =
        new CommandParser(
            new FilterCommandParser(), new BackendNamedCommandParser(List.of()), installedPlugins);

    ParsedInput in = pluginParser.parse("/pluginquote RAW TEST");

    assertTrue(installedPlugins.pluginProblems().isEmpty());
    assertTrue(in instanceof ParsedInput.Quote);
    assertEquals("RAW TEST", ((ParsedInput.Quote) in).rawLine());
  }

  @Test
  void parsesJoinWithOptionalKey() {
    ParsedInput in = parser.parse("/join #secret hunter2");
    assertTrue(in instanceof ParsedInput.Join);
    ParsedInput.Join join = (ParsedInput.Join) in;
    assertEquals("#secret", join.channel());
    assertEquals("hunter2", join.key());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/cs", "/chanserv", "/CS", "/ChanServ"})
  void parsesChanServCommandsAsPrivateMessages(String command) {
    ParsedInput.Msg msg =
        assertInstanceOf(
            ParsedInput.Msg.class, parser.parse(command + "  OP #ircafe Alice  Bob  "));

    assertEquals(new ParsedInput.Msg("ChanServ", "OP #ircafe Alice  Bob"), msg);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/cs", "/chanserv", "/CS  ", "/chanserv\t"})
  void bareChanServCommandRequestsServiceHelp(String command) {
    assertEquals(new ParsedInput.Msg("ChanServ", "HELP"), parser.parse(command));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/cstuff HELP", "/chanservfoo HELP"})
  void chanServCommandRequiresExactCommandName(String line) {
    assertInstanceOf(ParsedInput.Unknown.class, parser.parse(line));
  }

  @Test
  void chanServCommandAcceptsTabSeparator() {
    assertEquals(
        new ParsedInput.Msg("ChanServ", "INFO #ircafe"), parser.parse("/cs\tINFO #ircafe"));
  }

  @Test
  void treatsDoubleSlashAsEscapedLeadingSlashMessage() {
    ParsedInput escaped = parser.parse("//hello world");
    assertTrue(escaped instanceof ParsedInput.Say);
    assertEquals("/hello world", ((ParsedInput.Say) escaped).text());

    ParsedInput justSlash = parser.parse("//");
    assertTrue(justSlash instanceof ParsedInput.Say);
    assertEquals("/", ((ParsedInput.Say) justSlash).text());
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
  void parsesPartWithMatrixRoomIdAsReasonPayload() {
    ParsedInput in = parser.parse("/part !abc123:matrix.org later");
    assertTrue(in instanceof ParsedInput.Part);
    ParsedInput.Part part = (ParsedInput.Part) in;
    assertEquals("", part.channel());
    assertEquals("!abc123:matrix.org later", part.reason());
  }

  @Test
  void parsesPartReasonStartingWithBangAsReasonWhenNotMatrixRoomId() {
    ParsedInput in = parser.parse("/part !brb");
    assertTrue(in instanceof ParsedInput.Part);
    ParsedInput.Part part = (ParsedInput.Part) in;
    assertEquals("", part.channel());
    assertEquals("!brb", part.reason());
  }

  @Test
  void parsesQuasselSetupCommandAndAlias() {
    ParsedInput setup = parser.parse("/quasselsetup quassel");
    assertTrue(setup instanceof ParsedInput.BackendNamed);
    assertEquals(
        BuiltInBackendNamedCommandNames.QUASSEL_SETUP,
        ((ParsedInput.BackendNamed) setup).command());
    assertEquals("quassel", ((ParsedInput.BackendNamed) setup).args());

    ParsedInput alias = parser.parse("/qsetup");
    assertTrue(alias instanceof ParsedInput.BackendNamed);
    assertEquals(
        BuiltInBackendNamedCommandNames.QUASSEL_SETUP,
        ((ParsedInput.BackendNamed) alias).command());
    assertEquals("", ((ParsedInput.BackendNamed) alias).args());
  }

  @Test
  void parsesQuasselNetworkCommandAndAlias() {
    ParsedInput direct = parser.parse("/quasselnet list");
    assertTrue(direct instanceof ParsedInput.BackendNamed);
    assertEquals(
        BuiltInBackendNamedCommandNames.QUASSEL_NETWORK,
        ((ParsedInput.BackendNamed) direct).command());
    assertEquals("list", ((ParsedInput.BackendNamed) direct).args());

    ParsedInput alias = parser.parse("/qnet quassel connect libera");
    assertTrue(alias instanceof ParsedInput.BackendNamed);
    assertEquals(
        BuiltInBackendNamedCommandNames.QUASSEL_NETWORK,
        ((ParsedInput.BackendNamed) alias).command());
    assertEquals("quassel connect libera", ((ParsedInput.BackendNamed) alias).args());
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
  void parsesInviteActionCommands() {
    ParsedInput invites = parser.parse("/invites libera");
    assertTrue(invites instanceof ParsedInput.InviteList);
    assertEquals("libera", ((ParsedInput.InviteList) invites).serverId());

    ParsedInput join = parser.parse("/invjoin 12");
    assertTrue(join instanceof ParsedInput.InviteJoin);
    assertEquals("12", ((ParsedInput.InviteJoin) join).inviteToken());

    ParsedInput whois = parser.parse("/invitewhois last");
    assertTrue(whois instanceof ParsedInput.InviteWhois);
    assertEquals("last", ((ParsedInput.InviteWhois) whois).inviteToken());

    ParsedInput block = parser.parse("/invblock 7");
    assertTrue(block instanceof ParsedInput.InviteBlock);
    assertEquals("7", ((ParsedInput.InviteBlock) block).inviteToken());

    ParsedInput auto = parser.parse("/inviteautojoin on");
    assertTrue(auto instanceof ParsedInput.InviteAutoJoin);
    assertEquals("on", ((ParsedInput.InviteAutoJoin) auto).mode());
  }

  @Test
  void parsesJoinInviteShortcutOptions() {
    ParsedInput joinInvite = parser.parse("/join -invite");
    assertTrue(joinInvite instanceof ParsedInput.InviteJoin);
    assertEquals("last", ((ParsedInput.InviteJoin) joinInvite).inviteToken());

    ParsedInput joinInviteAlias = parser.parse("/join -i");
    assertTrue(joinInviteAlias instanceof ParsedInput.InviteJoin);
    assertEquals("last", ((ParsedInput.InviteJoin) joinInviteAlias).inviteToken());

    ParsedInput joinInviteById = parser.parse("/join -i 42");
    assertTrue(joinInviteById instanceof ParsedInput.InviteJoin);
    assertEquals("42", ((ParsedInput.InviteJoin) joinInviteById).inviteToken());
  }

  @Test
  void parsesAjinviteAsToggleAlias() {
    ParsedInput toggle = parser.parse("/ajinvite");
    assertTrue(toggle instanceof ParsedInput.InviteAutoJoin);
    assertEquals("toggle", ((ParsedInput.InviteAutoJoin) toggle).mode());

    ParsedInput off = parser.parse("/ajinvite off");
    assertTrue(off instanceof ParsedInput.InviteAutoJoin);
    assertEquals("off", ((ParsedInput.InviteAutoJoin) off).mode());
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
  void parsesMonitorAndAliasMon() {
    ParsedInput monitor = parser.parse("/monitor +alice,bob");
    assertTrue(monitor instanceof ParsedInput.Monitor);
    assertEquals("+alice,bob", ((ParsedInput.Monitor) monitor).args());

    ParsedInput mon = parser.parse("/mon list");
    assertTrue(mon instanceof ParsedInput.Monitor);
    assertEquals("list", ((ParsedInput.Monitor) mon).args());
  }

  @Test
  void parsesDccSendWithPath() {
    ParsedInput in = parser.parse("/dcc send alice /tmp/my file.txt");
    assertTrue(in instanceof ParsedInput.Dcc);
    ParsedInput.Dcc dcc = (ParsedInput.Dcc) in;
    assertEquals("send", dcc.subcommand());
    assertEquals("alice", dcc.nick());
    assertEquals("/tmp/my file.txt", dcc.argument());
  }

  @Test
  void parsesDccListWithoutNick() {
    ParsedInput in = parser.parse("/dcc list");
    assertTrue(in instanceof ParsedInput.Dcc);
    ParsedInput.Dcc dcc = (ParsedInput.Dcc) in;
    assertEquals("list", dcc.subcommand());
    assertEquals("", dcc.nick());
    assertEquals("", dcc.argument());
  }

  @Test
  void parsesUploadWithExplicitCaption() {
    ParsedInput in = parser.parse("/upload image /tmp/photo.png hello matrix");
    assertTrue(in instanceof ParsedInput.Upload);
    ParsedInput.Upload upload = (ParsedInput.Upload) in;
    assertEquals("image", upload.msgType());
    assertEquals("/tmp/photo.png", upload.path());
    assertEquals("hello matrix", upload.caption());
  }

  @Test
  void parsesUploadWithQuotedPath() {
    ParsedInput in = parser.parse("/upload m.file \"/tmp/my file.txt\" with caption");
    assertTrue(in instanceof ParsedInput.Upload);
    ParsedInput.Upload upload = (ParsedInput.Upload) in;
    assertEquals("m.file", upload.msgType());
    assertEquals("/tmp/my file.txt", upload.path());
    assertEquals("with caption", upload.caption());
  }

  @Test
  void legacyUploadAliasesAreUnknownCommands() {
    ParsedInput legacyShort = parser.parse("/mupload m.image");
    assertTrue(legacyShort instanceof ParsedInput.Unknown);

    ParsedInput legacyLong = parser.parse("/matrixupload m.image");
    assertTrue(legacyLong instanceof ParsedInput.Unknown);
  }

  @Test
  void parsesDccMsgAlias() {
    ParsedInput in = parser.parse("/dccmsg alice hi there");
    assertTrue(in instanceof ParsedInput.Dcc);
    ParsedInput.Dcc dcc = (ParsedInput.Dcc) in;
    assertEquals("msg", dcc.subcommand());
    assertEquals("alice", dcc.nick());
    assertEquals("hi there", dcc.argument());
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
  void parsesMarkReadCommand() {
    ParsedInput in = parser.parse("/markread");
    assertTrue(in instanceof ParsedInput.MarkRead);
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

  @Test
  void parsesUnreactComposeCommand() {
    ParsedInput in = parser.parse("/unreact abc123 :+1:");
    assertTrue(in instanceof ParsedInput.UnreactMessage);
    ParsedInput.UnreactMessage cmd = (ParsedInput.UnreactMessage) in;
    assertEquals("abc123", cmd.messageId());
    assertEquals(":+1:", cmd.reaction());
  }

  @Test
  void parsesHelpCommandAndCommandsAlias() {
    ParsedInput help = parser.parse("/help redact");
    assertTrue(help instanceof ParsedInput.Help);
    assertEquals("redact", ((ParsedInput.Help) help).topic());

    ParsedInput commands = parser.parse("/commands edit");
    assertTrue(commands instanceof ParsedInput.Help);
    assertEquals("edit", ((ParsedInput.Help) commands).topic());
  }

  @Test
  void parsesEditComposeCommand() {
    ParsedInput in = parser.parse("/edit abc123 replacement text");
    assertTrue(in instanceof ParsedInput.EditMessage);
    ParsedInput.EditMessage cmd = (ParsedInput.EditMessage) in;
    assertEquals("abc123", cmd.messageId());
    assertEquals("replacement text", cmd.body());
  }

  @Test
  void parsesRedactComposeCommandAndDeleteAlias() {
    ParsedInput redact = parser.parse("/redact abc123");
    assertTrue(redact instanceof ParsedInput.RedactMessage);
    assertEquals("abc123", ((ParsedInput.RedactMessage) redact).messageId());
    assertEquals("", ((ParsedInput.RedactMessage) redact).reason());

    ParsedInput delete = parser.parse("/delete abc123");
    assertTrue(delete instanceof ParsedInput.RedactMessage);
    assertEquals("abc123", ((ParsedInput.RedactMessage) delete).messageId());
    assertEquals("", ((ParsedInput.RedactMessage) delete).reason());

    ParsedInput withReason = parser.parse("/redact abc123 cleanup old context");
    assertTrue(withReason instanceof ParsedInput.RedactMessage);
    assertEquals("abc123", ((ParsedInput.RedactMessage) withReason).messageId());
    assertEquals("cleanup old context", ((ParsedInput.RedactMessage) withReason).reason());
  }

  private static String pluginParseStrategySource() {
    return """
        package plugin.commands;

        import cafe.woden.ircclient.app.commands.spi.SlashCommandParseResult;
        import cafe.woden.ircclient.app.commands.spi.SlashCommandParseStrategy;

        public final class PluginQuoteSlashCommandParseStrategy
            implements SlashCommandParseStrategy {
          @Override
          public SlashCommandParseResult tryParse(String line) {
            if (line == null || !line.startsWith("/pluginquote")) {
              return null;
            }
            String rest = line.length() > "/pluginquote".length()
                ? line.substring("/pluginquote".length()).trim()
                : "";
            return SlashCommandParseResult.quote(rest);
          }
        }
        """;
  }

  private static String pluginSpiParseStrategySource() {
    return """
        package plugin.commands;

        import cafe.woden.ircclient.app.commands.spi.SlashCommandParseResult;
        import cafe.woden.ircclient.app.commands.spi.SlashCommandParseStrategy;

        public final class PluginSpiQuoteSlashCommandParseStrategy
            implements SlashCommandParseStrategy {
          @Override
          public SlashCommandParseResult tryParse(String line) {
            if (line == null || !line.startsWith("/pluginquote")) {
              return null;
            }
            String rest = line.length() > "/pluginquote".length()
                ? line.substring("/pluginquote".length()).trim()
                : "";
            return SlashCommandParseResult.quote(rest);
          }
        }
        """;
  }

  private static final class RecordingInstalledPluginsPort implements InstalledPluginsPort {
    private final List<SlashCommandParseStrategy> pluginStrategies;

    private RecordingInstalledPluginsPort(List<SlashCommandParseStrategy> pluginStrategies) {
      this.pluginStrategies = List.copyOf(pluginStrategies);
    }

    @Override
    public <T> List<T> loadInstalledServices(Class<T> serviceType, List<T> builtInServices) {
      ArrayList<T> services = new ArrayList<>(builtInServices);
      if (serviceType == SlashCommandParseStrategy.class) {
        for (SlashCommandParseStrategy strategy : pluginStrategies) {
          services.add(serviceType.cast(strategy));
        }
      }
      return List.copyOf(services);
    }
  }

  private static final class PluginQuoteSlashCommandParseStrategy
      implements SlashCommandParseStrategy {
    @Override
    public SlashCommandParseResult tryParse(String line) {
      if (line == null || !line.startsWith("/pluginquote")) {
        return null;
      }
      String rest =
          line.length() > "/pluginquote".length()
              ? line.substring("/pluginquote".length()).trim()
              : "";
      return SlashCommandParseResult.quote(rest);
    }
  }
}

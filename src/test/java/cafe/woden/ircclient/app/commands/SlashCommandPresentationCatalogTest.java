package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.commands.spi.BackendNamedCommandHandler;
import cafe.woden.ircclient.app.commands.spi.BackendNamedCommandParseResult;
import cafe.woden.ircclient.app.commands.spi.SlashCommandDescriptor;
import cafe.woden.ircclient.app.commands.spi.SlashCommandHelpSink;
import cafe.woden.ircclient.app.commands.spi.SlashCommandPresentationContributor;
import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.config.plugins.InstalledPluginServices;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.util.CompiledPluginJarSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SlashCommandPresentationCatalogTest {

  private static final String PLUGIN_CONTRIBUTOR_CLASS = "plugin.commands.PluginHelpContributor";
  private static final String SPI_PLUGIN_CONTRIBUTOR_CLASS =
      "plugin.commands.PluginSpiHelpContributor";

  @TempDir Path tempDir;

  @Test
  void mergesContributorAndBackendAutocompleteCommands() {
    SlashCommandPresentationContributor builtInContributor =
        new SlashCommandPresentationContributor() {
          @Override
          public List<SlashCommandDescriptor> autocompleteCommands() {
            return List.of(new SlashCommandDescriptor("/join", "Join channel"));
          }
        };
    BackendNamedCommandHandler backendHandler =
        new BackendNamedCommandHandler() {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public BackendNamedCommandParseResult parse(String line, String matchedCommandName) {
            return new BackendNamedCommandParseResult("backendping", "");
          }

          @Override
          public List<SlashCommandDescriptor> autocompleteCommands() {
            return List.of(new SlashCommandDescriptor("/backendping", "Plugin test command"));
          }
        };

    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(builtInContributor),
            BackendNamedCommandCatalog.fromHandlers(List.of(backendHandler)));

    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();

    assertTrue(commands.contains("/join"));
    assertTrue(commands.contains("/backendping"));
  }

  @Test
  void loadsChanServAutocompleteAndHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(),
            BackendNamedCommandCatalog.empty(),
            new InstalledPluginServices(runtimeConfigPathPort));
    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();
    assertTrue(commands.contains("/cs"));
    assertTrue(commands.contains("/chanserv"));

    TargetRef status = new TargetRef("libera", "status");
    ArrayList<String> rendered = new ArrayList<>();
    catalog.appendGeneralHelp(status, (target, line) -> rendered.add(line));
    assertTrue(rendered.stream().anyMatch(line -> line.contains("/cs")));

    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));
    for (String topic : List.of("cs", "chanserv")) {
      rendered.clear();
      handlers.get(topic).accept(status);
      assertTrue(rendered.contains("Usage: /cs [command [args...]]"));
      assertTrue(rendered.contains("Alias: /chanserv [command [args...]]"));
      assertTrue(rendered.contains("With no command, requests ChanServ HELP."));
    }
  }

  @Test
  void loadsNickServAutocompleteAndHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(),
            BackendNamedCommandCatalog.empty(),
            new InstalledPluginServices(runtimeConfigPathPort));
    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();
    assertTrue(commands.contains("/ns"));
    assertTrue(commands.contains("/nickserv"));

    TargetRef status = new TargetRef("libera", "status");
    ArrayList<String> rendered = new ArrayList<>();
    catalog.appendGeneralHelp(status, (target, line) -> rendered.add(line));
    assertTrue(rendered.stream().anyMatch(line -> line.contains("/ns")));

    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));
    for (String topic : List.of("ns", "nickserv")) {
      rendered.clear();
      handlers.get(topic).accept(status);
      assertTrue(rendered.contains("Usage: /ns [command [args...]]"));
      assertTrue(rendered.contains("Alias: /nickserv [command [args...]]"));
      assertTrue(rendered.contains("With no command, requests NickServ HELP."));
    }
  }

  @Test
  void loadsMemoServAutocompleteAndHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(),
            BackendNamedCommandCatalog.empty(),
            new InstalledPluginServices(runtimeConfigPathPort));
    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();
    assertTrue(commands.contains("/ms"));
    assertTrue(commands.contains("/memoserv"));

    TargetRef status = new TargetRef("libera", "status");
    ArrayList<String> rendered = new ArrayList<>();
    catalog.appendGeneralHelp(status, (target, line) -> rendered.add(line));
    assertTrue(rendered.stream().anyMatch(line -> line.contains("/ms")));

    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));
    for (String topic : List.of("ms", "memoserv")) {
      rendered.clear();
      handlers.get(topic).accept(status);
      assertTrue(rendered.contains("Usage: /ms [command [args...]]"));
      assertTrue(rendered.contains("Alias: /memoserv [command [args...]]"));
      assertTrue(rendered.contains("With no command, requests MemoServ HELP."));
    }
  }

  @Test
  void loadsOperServAutocompleteAndHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(),
            BackendNamedCommandCatalog.empty(),
            new InstalledPluginServices(runtimeConfigPathPort));
    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();
    assertTrue(commands.contains("/os"));
    assertTrue(commands.contains("/operserv"));

    TargetRef status = new TargetRef("libera", "status");
    ArrayList<String> rendered = new ArrayList<>();
    catalog.appendGeneralHelp(status, (target, line) -> rendered.add(line));
    assertTrue(rendered.stream().anyMatch(line -> line.contains("/os")));

    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));
    for (String topic : List.of("os", "operserv")) {
      rendered.clear();
      handlers.get(topic).accept(status);
      assertTrue(rendered.contains("Usage: /os [command [args...]]"));
      assertTrue(rendered.contains("Alias: /operserv [command [args...]]"));
      assertTrue(rendered.contains("With no command, requests OperServ HELP."));
    }
  }

  @Test
  void includesAppOwnedFilterAutocompleteCommandWithoutProviderContributors() {
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(List.of(), BackendNamedCommandCatalog.empty());

    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();

    assertTrue(commands.contains("/filter"));
  }

  @Test
  void appOwnedFilterHelpRendersWithoutProviderContributors() {
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(List.of(), BackendNamedCommandCatalog.empty());
    ArrayList<String> rendered = new ArrayList<>();

    catalog.appendGeneralHelp(
        new TargetRef("libera", "status"), (target, line) -> rendered.add(line));

    assertTrue(rendered.contains("Local: /filter help for local filtering controls."));
  }

  @Test
  void appOwnedFilterTopicHelpRendersWithoutProviderContributors() {
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(List.of(), BackendNamedCommandCatalog.empty());
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("filter"));
    handlers.get("filter").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /filter help"));
    assertTrue(
        rendered.contains(
            "Examples: /filter list, /filter add <name> key=value ..., /filter defaults ..."));
  }

  @Test
  void installedPluginPortConstructorLoadsApplicationClasspathContributorsWithoutSpringSeed() {
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(),
            BackendNamedCommandCatalog.empty(),
            new RecordingInstalledPluginsPort(List.of()));

    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();

    assertTrue(commands.contains("/filter"));
    assertTrue(commands.contains("/join"));
  }

  @Test
  void includesPresentationContributorsLoadedThroughInstalledPluginPort() {
    SlashCommandPresentationContributor builtInContributor =
        autocompleteContributor("/built-in", "Built-in command");
    SlashCommandPresentationContributor pluginContributor =
        new SlashCommandPresentationContributor() {
          @Override
          public List<SlashCommandDescriptor> autocompleteCommands() {
            return List.of(new SlashCommandDescriptor("/plugin-help", "Plugin command"));
          }
        };

    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(builtInContributor),
            BackendNamedCommandCatalog.empty(),
            new RecordingInstalledPluginsPort(List.of(pluginContributor)));

    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();

    assertTrue(commands.contains("/built-in"));
    assertTrue(commands.contains("/join"));
    assertTrue(commands.contains("/plugin-help"));
  }

  @Test
  void presentationContributorsCanRenderGeneralHelpThroughLineAppender() {
    SlashCommandPresentationContributor contributor =
        new SlashCommandPresentationContributor() {
          @Override
          public void appendGeneralHelp(SlashCommandHelpSink help) {
            help.appendLine("/plugin-help - plugin-provided help");
          }
        };
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(contributor), BackendNamedCommandCatalog.empty());
    ArrayList<String> rendered = new ArrayList<>();

    catalog.appendGeneralHelp(
        new TargetRef("libera", "status"),
        (target, line) -> rendered.add(target.target() + ":" + line));

    assertEquals(
        List.of(
            "status:Local: /filter help for local filtering controls.",
            "status:/plugin-help - plugin-provided help"),
        rendered);
  }

  @Test
  void presentationContributorsCanReadPortableHelpTargetView() {
    SlashCommandPresentationContributor contributor =
        new SlashCommandPresentationContributor() {
          @Override
          public void appendGeneralHelp(SlashCommandHelpSink help) {
            help.appendLine(help.target().serverId() + ":" + help.target().target());
          }
        };
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(contributor), BackendNamedCommandCatalog.empty());
    ArrayList<String> rendered = new ArrayList<>();

    catalog.appendGeneralHelp(
        new TargetRef("libera", "#java"), (target, line) -> rendered.add(line));

    assertEquals(
        List.of("Local: /filter help for local filtering controls.", "libera:#java"), rendered);
  }

  @Test
  void presentationContributorsCanRenderTopicHelpThroughLineAppender() {
    SlashCommandPresentationContributor contributor =
        new SlashCommandPresentationContributor() {
          @Override
          public Map<String, Consumer<SlashCommandHelpSink>> topicHelpHandlers() {
            return Map.of("plugin-help", help -> help.appendLine("/plugin-help <arg>"));
          }
        };
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(contributor), BackendNamedCommandCatalog.empty());
    AtomicReference<String> rendered = new AtomicReference<>();

    catalog
        .topicHelpHandlers((target, line) -> rendered.set(target.target() + ":" + line))
        .get("plugin-help")
        .accept(new TargetRef("libera", "status"));

    assertEquals("status:/plugin-help <arg>", rendered.get());
  }

  @Test
  void presentationContributorsWithSameTopicAreComposed() {
    SlashCommandPresentationContributor first =
        new SlashCommandPresentationContributor() {
          @Override
          public Map<String, Consumer<SlashCommandHelpSink>> topicHelpHandlers() {
            return Map.of("shared", help -> help.appendLine("first shared line"));
          }
        };
    SlashCommandPresentationContributor second =
        new SlashCommandPresentationContributor() {
          @Override
          public Map<String, Consumer<SlashCommandHelpSink>> topicHelpHandlers() {
            return Map.of("shared", help -> help.appendLine("second shared line"));
          }
        };
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(first, second), BackendNamedCommandCatalog.empty());
    ArrayList<String> rendered = new ArrayList<>();

    catalog
        .topicHelpHandlers((target, line) -> rendered.add(line))
        .get("shared")
        .accept(new TargetRef("libera", "status"));

    assertEquals(List.of("first shared line", "second shared line"), rendered);
  }

  @Test
  void backendTopicHelpLinesComposeWithPresentationContributors() {
    SlashCommandPresentationContributor presentationContributor =
        new SlashCommandPresentationContributor() {
          @Override
          public Map<String, Consumer<SlashCommandHelpSink>> topicHelpHandlers() {
            return Map.of("backendping", help -> help.appendLine("presentation backendping line"));
          }
        };
    BackendNamedCommandHandler backendHandler =
        new BackendNamedCommandHandler() {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public BackendNamedCommandParseResult parse(String line, String matchedCommandName) {
            return new BackendNamedCommandParseResult("backendping", "");
          }

          @Override
          public Map<String, List<String>> topicHelpLines() {
            return Map.of("backendping", List.of("/backendping <arg>"));
          }
        };
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(presentationContributor),
            BackendNamedCommandCatalog.fromHandlers(List.of(backendHandler)));
    ArrayList<String> rendered = new ArrayList<>();

    catalog
        .topicHelpHandlers((target, line) -> rendered.add(line))
        .get("backendping")
        .accept(new TargetRef("libera", "status"));

    assertEquals(List.of("presentation backendping line", "/backendping <arg>"), rendered);
  }

  @Test
  void loadsServiceLoaderPresentationContributorsFromInstalledPlugins() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("example-slash-command-presentation.jar"),
        PLUGIN_CONTRIBUTOR_CLASS,
        pluginContributorSource(),
        SlashCommandPresentationContributor.class.getName(),
        CompiledPluginJarSupport.compatibleManifest("example-slash-command-presentation", "1.0.0"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);

    assertTrue(installedPlugins.pluginProblems().isEmpty());
    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();
    assertTrue(commands.contains("/join"));
    assertTrue(commands.contains("/plugin-help"));

    ArrayList<String> generalHelp = new ArrayList<>();
    catalog.appendGeneralHelp(
        new TargetRef("libera", "status"),
        (target, line) -> generalHelp.add(target.target() + ":" + line));
    assertTrue(
        generalHelp.contains(
            "status:Common: /join /part /msg /cs /ns /ms /os /notice /me /query /whois /names /list /topic /monitor /chathistory /quote /dcc"));
    assertTrue(generalHelp.contains("status:/plugin-help - plugin jar help"));

    AtomicReference<String> topicHelp = new AtomicReference<>();
    catalog
        .topicHelpHandlers((target, line) -> topicHelp.set(target.target() + ":" + line))
        .get("plugin-help")
        .accept(new TargetRef("libera", "status"));
    assertEquals("status:/plugin-help <arg>", topicHelp.get());
  }

  @Test
  void loadsServiceLoaderPresentationContributorProvidersFromInstalledPlugins() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("example-slash-command-presentation-spi.jar"),
        SPI_PLUGIN_CONTRIBUTOR_CLASS,
        pluginSpiContributorSource(),
        cafe.woden.ircclient.app.commands.spi.SlashCommandPresentationContributor.class.getName(),
        CompiledPluginJarSupport.compatibleManifest(
            "example-slash-command-presentation-spi", "1.0.0"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);

    assertTrue(installedPlugins.pluginProblems().isEmpty());
    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();
    assertTrue(commands.contains("/join"));
    assertTrue(commands.contains("/plugin-help"));

    ArrayList<String> generalHelp = new ArrayList<>();
    catalog.appendGeneralHelp(
        new TargetRef("libera", "status"),
        (target, line) -> generalHelp.add(target.target() + ":" + line));
    assertTrue(
        generalHelp.contains(
            "status:Common: /join /part /msg /cs /ns /ms /os /notice /me /query /whois /names /list /topic /monitor /chathistory /quote /dcc"));
    assertTrue(generalHelp.contains("status:/plugin-help - plugin jar help"));

    AtomicReference<String> topicHelp = new AtomicReference<>();
    catalog
        .topicHelpHandlers((target, line) -> topicHelp.set(target.target() + ":" + line))
        .get("plugin-help")
        .accept(new TargetRef("libera", "status"));
    assertEquals("status:/plugin-help <arg>", topicHelp.get());
  }

  @Test
  void loadsCorePresentationContributorThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);

    assertTrue(installedPlugins.pluginProblems().isEmpty());
    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();
    assertTrue(commands.contains("/join"));
    assertTrue(commands.contains("/monitor"));
    assertTrue(commands.contains("/mon"));
    assertTrue(commands.contains("/raw"));
  }

  @Test
  void loadsCorePresentationContributorThroughClasspathServiceLoaderWithoutInstalledPlugins() {
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), (InstalledPluginsPort) null);

    List<String> commands =
        catalog.autocompleteCommands().stream().map(SlashCommandDescriptor::command).toList();

    assertTrue(commands.contains("/join"));
    assertTrue(commands.contains("/monitor"));
    assertTrue(commands.contains("/mon"));
    assertTrue(commands.contains("/raw"));
  }

  @Test
  void loadsCoreGeneralHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();

    catalog.appendGeneralHelp(
        new TargetRef("libera", "status"), (target, line) -> rendered.add(line));

    assertTrue(
        rendered.contains(
            "Common: /join /part /msg /cs /ns /ms /os /notice /me /query /whois /names /list /topic /monitor /chathistory /quote /dcc"));
    assertTrue(
        rendered.contains(
            "Invites: /invites /invjoin (/join -i) /invignore /invwhois /invblock /inviteautojoin (/ajinvite)"));
    assertTrue(rendered.contains("Tip: /help dcc for direct-chat/file-transfer commands."));
    assertTrue(rendered.contains("/reply <msgid> <message> (requires message-tags)"));
    assertTrue(rendered.contains("/react <msgid> <reaction-token> (requires message-tags)"));
    assertTrue(rendered.contains("/unreact <msgid> <reaction-token> (requires message-tags)"));
    assertTrue(
        rendered.contains(
            "Tip: /help edit, /help redact, /help markread, or /help upload for focused details."));
  }

  @Test
  void loadsCoreDccTopicHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("dcc"));
    assertTrue(handlers.containsKey("dccmsg"));
    handlers.get("dccmsg").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("/dcc chat <nick>"));
    assertTrue(rendered.contains("/dcc send <nick> <file-path>"));
    assertTrue(rendered.contains("UI: right-click a nick and use the DCC submenu."));
  }

  @Test
  void loadsCoreUploadTopicHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("upload"));
    handlers.get("upload").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /upload <msgtype> <path> [caption]"));
    assertTrue(
        rendered.contains(
            "Uploads or sends a media/file payload through the active backend when supported."));
    assertTrue(rendered.contains("Backend help may add supported msgtypes and shortcuts below."));
  }

  @Test
  void loadsCoreMonitorTopicHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("monitor"));
    assertTrue(handlers.containsKey("mon"));
    handlers.get("monitor").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /monitor <+|-|list|status|clear> [nicks]"));
    assertTrue(rendered.contains("Aliases: /mon, /monitor +nick1 nick2, /monitor -nick1,nick2"));
    assertTrue(
        rendered.contains("Examples: /monitor +alice,bob  |  /monitor list  |  /monitor clear"));
  }

  @Test
  void loadsCoreChatHistoryTopicHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("chathistory"));
    assertTrue(handlers.containsKey("history"));
    handlers.get("history").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("/chathistory before <msgid=...|timestamp=...> [limit]"));
    assertTrue(rendered.contains("/chathistory latest [*|msgid=...|timestamp=...] [limit]"));
    assertTrue(rendered.contains("/chathistory around <msgid=...|timestamp=...> [limit]"));
    assertTrue(rendered.contains("/chathistory between <start> <end> [limit]"));
  }

  @Test
  void loadsCoreMarkReadTopicHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("markread"));
    handlers.get("markread").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /markread"));
    assertTrue(
        rendered.contains(
            "Sets the read marker for the active channel or query and clears unread state."));
  }

  @Test
  void loadsCoreMutationTopicHelpDetailsThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("edit"));
    assertTrue(handlers.containsKey("redact"));
    assertTrue(handlers.containsKey("delete"));

    handlers.get("edit").accept(new TargetRef("libera", "status"));
    handlers.get("delete").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /edit <msgid> <message>"));
    assertTrue(
        rendered.contains(
            "Edits one of your recent messages when the backend supports draft/message-edit."));
    assertTrue(rendered.contains("Usage: /redact <msgid> [reason]"));
    assertTrue(rendered.contains("Alias: /delete <msgid> [reason]"));
    assertTrue(
        rendered.contains(
            "Redacts one of your recent messages when the backend supports message redaction."));
  }

  @Test
  void loadsCoreRawQuoteTopicHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("quote"));
    assertTrue(handlers.containsKey("raw"));
    handlers.get("raw").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /quote <RAW IRC LINE>"));
    assertTrue(rendered.contains("Alias: /raw <RAW IRC LINE>"));
    assertTrue(rendered.contains("Sends one raw IRC protocol line to the active server."));
  }

  @Test
  void loadsCoreIdentityMessagingTopicHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("nick"));
    assertTrue(handlers.containsKey("away"));
    assertTrue(handlers.containsKey("msg"));
    assertTrue(handlers.containsKey("notice"));
    assertTrue(handlers.containsKey("ctcp"));
    assertTrue(handlers.containsKey("version"));

    handlers.get("msg").accept(new TargetRef("libera", "status"));
    handlers.get("ctcp").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /msg <nick> <message>"));
    assertTrue(rendered.contains("Sends a private message without changing the active target."));
    assertTrue(rendered.contains("Usage: /ctcp <nick> <command> [args...]"));
    assertTrue(rendered.contains("Shortcuts: /version <nick>, /ping <nick>, /time <nick>."));
  }

  @Test
  void loadsCoreIgnoreTopicHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("ignore"));
    assertTrue(handlers.containsKey("unignore"));
    assertTrue(handlers.containsKey("ignorelist"));
    assertTrue(handlers.containsKey("ignores"));
    assertTrue(handlers.containsKey("softignore"));
    assertTrue(handlers.containsKey("unsoftignore"));
    assertTrue(handlers.containsKey("softignorelist"));
    assertTrue(handlers.containsKey("softignores"));

    handlers.get("ignore").accept(new TargetRef("libera", "status"));
    handlers.get("softignore").accept(new TargetRef("libera", "status"));
    handlers.get("softignorelist").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /ignore [-options] [levels] <maskOrNick>"));
    assertTrue(
        rendered.contains(
            "Options include -channels #a,#b, -pattern <text>, -regexp, -full, -expires <duration>, and -replies."));
    assertTrue(rendered.contains("Usage: /softignore <maskOrNick>"));
    assertTrue(rendered.contains("Soft-ignored users have inbound messages rendered as spoilers."));
    assertTrue(rendered.contains("Usage: /softignorelist"));
    assertTrue(rendered.contains("Alias: /softignores"));
  }

  @Test
  void loadsCoreInviteTopicHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("invite"));
    assertTrue(handlers.containsKey("invites"));
    assertTrue(handlers.containsKey("invjoin"));
    assertTrue(handlers.containsKey("invitejoin"));
    assertTrue(handlers.containsKey("inviteautojoin"));
    assertTrue(handlers.containsKey("ajinvite"));

    handlers.get("invite").accept(new TargetRef("libera", "status"));
    handlers.get("invjoin").accept(new TargetRef("libera", "status"));
    handlers.get("inviteautojoin").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /invite <nick> [#channel]"));
    assertTrue(rendered.contains("Invites a nick to the active or specified channel."));
    assertTrue(rendered.contains("Usage: /invjoin [inviteId|last]"));
    assertTrue(rendered.contains("Aliases: /invitejoin [inviteId|last], /join -i [inviteId|last]"));
    assertTrue(rendered.contains("Usage: /inviteautojoin [on|off|status]"));
    assertTrue(
        rendered.contains(
            "Aliases: /invautojoin [on|off|status], /ajinvite [on|off|status|toggle]"));
  }

  @Test
  void loadsCoreConnectionChannelTopicHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("join"));
    assertTrue(handlers.containsKey("j"));
    assertTrue(handlers.containsKey("connect"));
    assertTrue(handlers.containsKey("topic"));
    assertTrue(handlers.containsKey("mode"));
    assertTrue(handlers.containsKey("op"));
    assertTrue(handlers.containsKey("unban"));

    handlers.get("join").accept(new TargetRef("libera", "status"));
    handlers.get("mode").accept(new TargetRef("libera", "status"));
    handlers.get("op").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /join <#channel> [key]"));
    assertTrue(rendered.contains("Alias: /j <#channel> [key]"));
    assertTrue(rendered.contains("Usage: /mode <target> [mode-spec [args...]]"));
    assertTrue(rendered.contains("Queries or changes user/channel modes."));
    assertTrue(
        rendered.contains("Usage: /op|/deop|/voice|/devoice [#channel] <nick> [more nicks...]"));
    assertTrue(rendered.contains("Changes common channel privilege modes for one or more nicks."));
  }

  @Test
  void loadsCoreHelpCommandTopicHelpThroughClasspathServiceLoader() {
    RuntimeConfigPathPort runtimeConfigPathPort = () -> tempDir.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.empty(), installedPlugins);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("help"));
    assertTrue(handlers.containsKey("commands"));
    handlers.get("commands").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /help [topic]"));
    assertTrue(rendered.contains("Alias: /commands [topic]"));
    assertTrue(
        rendered.contains("Shows general slash-command help or focused help for a command topic."));
  }

  @Test
  void loadsCoreQuasselBackendNamedTopicHelpThroughClasspathServiceLoader() {
    BackendNamedCommandCatalog backendCatalog =
        BackendNamedCommandCatalog.installed(
            (RuntimeConfigPathPort) null,
            SlashCommandPresentationCatalogTest.class.getClassLoader());
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(List.of(), backendCatalog);
    ArrayList<String> rendered = new ArrayList<>();
    Map<String, Consumer<TargetRef>> handlers =
        catalog.topicHelpHandlers((target, line) -> rendered.add(line));

    assertTrue(handlers.containsKey("quasselsetup"));
    assertTrue(handlers.containsKey("qsetup"));
    assertTrue(handlers.containsKey("quasselnet"));
    assertTrue(handlers.containsKey("qnet"));

    handlers.get("qsetup").accept(new TargetRef("libera", "status"));
    handlers.get("qnet").accept(new TargetRef("libera", "status"));

    assertTrue(rendered.contains("Usage: /quasselsetup [serverId]"));
    assertTrue(rendered.contains("Alias: /qsetup [serverId]"));
    assertTrue(
        rendered.contains(
            "Completes a pending Quassel Core setup flow for the selected or specified server."));
    assertTrue(
        rendered.contains(
            "Usage: /quasselnet [serverId] list|connect|disconnect|remove|add|edit ..."));
    assertTrue(
        rendered.contains("Alias: /qnet [serverId] list|connect|disconnect|remove|add|edit ..."));
    assertTrue(
        rendered.contains(
            "Manages Quassel Core network records for the selected or specified server."));
  }

  @Test
  void backendTopicHelpLinesAreExposedThroughCatalogHandlers() {
    BackendNamedCommandHandler backendHandler =
        new BackendNamedCommandHandler() {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public BackendNamedCommandParseResult parse(String line, String matchedCommandName) {
            return new BackendNamedCommandParseResult("backendping", "");
          }

          @Override
          public Map<String, List<String>> topicHelpLines() {
            return Map.of("backendping", List.of("/backendping <arg>"));
          }
        };
    SlashCommandPresentationCatalog catalog =
        new SlashCommandPresentationCatalog(
            List.of(), BackendNamedCommandCatalog.fromHandlers(List.of(backendHandler)));
    AtomicReference<String> rendered = new AtomicReference<>();

    catalog
        .topicHelpHandlers((target, line) -> rendered.set(target.target() + ":" + line))
        .get("backendping")
        .accept(new TargetRef("libera", "status"));

    assertEquals("status:/backendping <arg>", rendered.get());
  }

  private static SlashCommandPresentationContributor autocompleteContributor(
      String command, String summary) {
    return new SlashCommandPresentationContributor() {
      @Override
      public List<SlashCommandDescriptor> autocompleteCommands() {
        return List.of(new SlashCommandDescriptor(command, summary));
      }
    };
  }

  private static String pluginContributorSource() {
    return """
        package plugin.commands;

        import cafe.woden.ircclient.app.commands.spi.SlashCommandDescriptor;
        import cafe.woden.ircclient.app.commands.spi.SlashCommandHelpSink;
        import cafe.woden.ircclient.app.commands.spi.SlashCommandPresentationContributor;
        import java.util.List;
        import java.util.Map;
        import java.util.function.Consumer;

        public final class PluginHelpContributor implements SlashCommandPresentationContributor {
          @Override
          public List<SlashCommandDescriptor> autocompleteCommands() {
            return List.of(new SlashCommandDescriptor("/plugin-help", "Plugin help command"));
          }

          @Override
          public void appendGeneralHelp(SlashCommandHelpSink help) {
            help.appendLine("/plugin-help - plugin jar help");
          }

          @Override
          public Map<String, Consumer<SlashCommandHelpSink>> topicHelpHandlers() {
            return Map.of("plugin-help", help -> help.appendLine("/plugin-help <arg>"));
          }
        }
        """;
  }

  private static String pluginSpiContributorSource() {
    return """
        package plugin.commands;

        import cafe.woden.ircclient.app.commands.spi.SlashCommandDescriptor;
        import cafe.woden.ircclient.app.commands.spi.SlashCommandHelpSink;
        import cafe.woden.ircclient.app.commands.spi.SlashCommandPresentationContributor;
        import java.util.List;
        import java.util.Map;
        import java.util.function.Consumer;

        public final class PluginSpiHelpContributor
            implements SlashCommandPresentationContributor {
          @Override
          public List<SlashCommandDescriptor> autocompleteCommands() {
            return List.of(new SlashCommandDescriptor("/plugin-help", "Plugin help command"));
          }

          @Override
          public void appendGeneralHelp(SlashCommandHelpSink help) {
            help.appendLine("/plugin-help - plugin jar help");
          }

          @Override
          public Map<String, Consumer<SlashCommandHelpSink>> topicHelpHandlers() {
            return Map.of("plugin-help", help -> help.appendLine("/plugin-help <arg>"));
          }
        }
        """;
  }

  private static final class RecordingInstalledPluginsPort implements InstalledPluginsPort {
    private final List<SlashCommandPresentationContributor> pluginContributors;

    private RecordingInstalledPluginsPort(
        List<SlashCommandPresentationContributor> pluginContributors) {
      this.pluginContributors = List.copyOf(pluginContributors);
    }

    @Override
    public <T> List<T> loadInstalledServices(Class<T> serviceType, List<T> builtInServices) {
      ArrayList<T> services = new ArrayList<>(builtInServices);
      if (serviceType == SlashCommandPresentationContributor.class) {
        for (SlashCommandPresentationContributor contributor : pluginContributors) {
          services.add(serviceType.cast(contributor));
        }
      }
      return List.copyOf(services);
    }
  }
}

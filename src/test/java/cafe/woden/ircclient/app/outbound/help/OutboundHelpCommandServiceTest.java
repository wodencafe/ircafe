package cafe.woden.ircclient.app.outbound.help;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.Ircv3ReadMarkerFeatureSupport;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.BackendNamedCommandCatalog;
import cafe.woden.ircclient.app.commands.SlashCommandPresentationCatalog;
import cafe.woden.ircclient.app.commands.builtins.BuiltInSlashCommandPresentationContributor;
import cafe.woden.ircclient.app.commands.spi.SlashCommandHelpSink;
import cafe.woden.ircclient.app.commands.spi.SlashCommandPresentationContributor;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.*;
import cafe.woden.ircclient.app.outbound.help.spi.OutboundHelpContributor;
import cafe.woden.ircclient.app.outbound.help.spi.OutboundHelpSink;
import cafe.woden.ircclient.app.outbound.readmarker.OutboundReadMarkerCommandService;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundCommandAvailabilitySupport;
import cafe.woden.ircclient.app.outbound.support.OutboundConnectionStatusSupport;
import cafe.woden.ircclient.app.outbound.upload.OutboundUploadCommandService;
import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import cafe.woden.ircclient.config.api.Ircv3CapabilityNameResolverPort;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.config.plugins.InstalledPluginServices;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.adapter.IrcNegotiatedFeaturePortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcReadMarkerPortAdapter;
import cafe.woden.ircclient.irc.backend.IrcBackendRuntimeClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.util.CompiledPluginJarSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutboundHelpCommandServiceTest {

  private static final String PLUGIN_HELP_CONTRIBUTOR_CLASS = "plugin.help.PluginHelpContributor";

  @TempDir Path tempDir;

  private final IrcBackendRuntimeClientService irc = mock(IrcBackendRuntimeClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.commandTargetPolicy(serverCatalog);
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry =
      cafe.woden.ircclient.app.outbound.TestBackendSupport.builtInOutboundBackendFeatureRegistry();
  private final OutboundBackendCapabilityPolicy outboundBackendCapabilityPolicy =
      new OutboundBackendCapabilityPolicy(
          commandTargetPolicy,
          outboundBackendFeatureRegistry,
          new IrcNegotiatedFeaturePortAdapter(irc),
          irc,
          cafe.woden.ircclient.app.api.AvailableBackendIdsPort.builtInsOnly());
  private final OutboundCommandAvailabilitySupport outboundCommandAvailabilitySupport =
      new OutboundCommandAvailabilitySupport(outboundBackendCapabilityPolicy);
  private final OutboundConnectionStatusSupport outboundConnectionStatusSupport =
      new OutboundConnectionStatusSupport(ui, connectionCoordinator);
  private final Ircv3ReadMarkerFeatureSupport readMarkerFeatureSupport =
      new Ircv3ReadMarkerFeatureSupport(
          new IrcReadMarkerPortAdapter(irc),
          outboundBackendCapabilityPolicy,
          new Ircv3CapabilityNameResolverPort() {});
  private final OutboundUploadCommandService outboundUploadCommandService =
      mock(OutboundUploadCommandService.class);
  private final OutboundHelpContributor uploadHelpContributor =
      new OutboundHelpContributor() {
        @Override
        public void appendGeneralHelp(OutboundHelpSink help) {
          outboundUploadCommandService.appendUploadHelp(targetRef(help));
        }

        @Override
        public Map<String, Consumer<OutboundHelpSink>> topicHelpHandlers() {
          return Map.of(
              "upload", help -> outboundUploadCommandService.appendUploadHelp(targetRef(help)));
        }
      };
  private final OutboundHelpContributor messageMutationHelpContributor =
      new OutboundHelpContributor() {
        @Override
        public void appendGeneralHelp(OutboundHelpSink help) {
          appendEditHelp(targetRef(help));
          appendRedactHelp(targetRef(help));
        }

        @Override
        public Map<String, Consumer<OutboundHelpSink>> topicHelpHandlers() {
          return Map.of(
              "edit", help -> OutboundHelpCommandServiceTest.this.appendEditHelp(targetRef(help)),
              "redact",
                  help -> OutboundHelpCommandServiceTest.this.appendRedactHelp(targetRef(help)),
              "delete",
                  help -> OutboundHelpCommandServiceTest.this.appendRedactHelp(targetRef(help)));
        }
      };
  private final OutboundReadMarkerCommandService readMarkerCommandService =
      new OutboundReadMarkerCommandService(
          readMarkerFeatureSupport,
          outboundCommandAvailabilitySupport,
          outboundConnectionStatusSupport,
          ui,
          targetCoordinator);
  private final SlashCommandPresentationCatalog slashCommandPresentationCatalog =
      new SlashCommandPresentationCatalog(
          List.of(new BuiltInSlashCommandPresentationContributor()),
          BackendNamedCommandCatalog.empty());
  private final OutboundHelpCommandService service =
      new OutboundHelpCommandService(
          ui,
          targetCoordinator,
          List.of(uploadHelpContributor, messageMutationHelpContributor, readMarkerCommandService),
          slashCommandPresentationCatalog);

  private void appendEditHelp(TargetRef out) {
    TargetRef target = out != null ? out : targetCoordinator.safeStatusTarget();
    String serverId = target.serverId();
    boolean available = outboundBackendCapabilityPolicy.supportsExperimentalMessageEdit(serverId);
    ui.appendStatus(
        target,
        "(help)",
        "/edit <msgid> <message> (experimental draft/message-edit)"
            + (available
                ? ""
                : outboundCommandAvailabilitySupport.helpAvailabilitySuffix(
                    serverId, false, "requires negotiated experimental draft/message-edit")));
  }

  private void appendRedactHelp(TargetRef out) {
    TargetRef target = out != null ? out : targetCoordinator.safeStatusTarget();
    String serverId = target.serverId();
    boolean available = outboundBackendCapabilityPolicy.supportsMessageRedaction(serverId);
    ui.appendStatus(
        target,
        "(help)",
        "/redact <msgid> [reason] (alias: /delete)"
            + (available
                ? ""
                : outboundCommandAvailabilitySupport.helpAvailabilitySuffix(
                    serverId,
                    false,
                    "requires negotiated draft/message-redaction or message-redaction")));
  }

  private static TargetRef targetRef(OutboundHelpSink help) {
    return new TargetRef(help.target().serverId(), help.target().target());
  }

  @Test
  void helpAnnotatesEditAndRedactAsUnavailableWhenCapsNotNegotiated() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isExperimentalMessageEditAvailable("libera")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(false);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleHelp("");

    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains("/edit <msgid> <message> (experimental draft/message-edit) (unavailable:"));
    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains("/redact <msgid> [reason] (alias: /delete) (unavailable:"));
    verify(ui).appendStatus(eq(chan), eq("(help)"), contains("/markread (unavailable:"));
  }

  @Test
  void helpUsesBackendAvailabilityReasonWhenPresent() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.backendAvailabilityReason("libera"))
        .thenReturn("Quassel Core backend is not implemented yet");
    when(irc.isExperimentalMessageEditAvailable("libera")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(false);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleHelp("");

    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/edit <msgid> <message> (experimental draft/message-edit) (unavailable: Quassel Core backend is not implemented yet)"));
    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/redact <msgid> [reason] (alias: /delete) (unavailable: Quassel Core backend is not implemented yet)"));
    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains("/markread (unavailable: Quassel Core backend is not implemented yet)"));
  }

  @Test
  void helpUsesNegotiationFallbackWhenBackendHasNoSpecificReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isExperimentalMessageEditAvailable("libera")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(false);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleHelp("");

    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/edit <msgid> <message> (experimental draft/message-edit) (unavailable: requires negotiated experimental draft/message-edit)"));
    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/redact <msgid> [reason] (alias: /delete) (unavailable: requires negotiated draft/message-redaction or message-redaction)"));
    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/markread (unavailable: requires negotiated read-marker or draft/read-marker)"));
  }

  @Test
  void helpShowsEditAndRedactWithoutUnavailableSuffixWhenCapsNegotiated() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isExperimentalMessageEditAvailable("libera")).thenReturn(true);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);

    service.handleHelp("edit");
    service.handleHelp("redact");
    service.handleHelp("markread");

    verify(ui)
        .appendStatus(chan, "(help)", "/edit <msgid> <message> (experimental draft/message-edit)");
    verify(ui).appendStatus(chan, "(help)", "/redact <msgid> [reason] (alias: /delete)");
    verify(ui).appendStatus(chan, "(help)", "/markread");
  }

  @Test
  void helpDccShowsCommandsAndUiHint() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);

    service.handleHelp("dcc");

    verify(ui).appendStatus(chan, "(help)", "/dcc chat <nick>");
    verify(ui).appendStatus(chan, "(help)", "/dcc send <nick> <file-path>");
    verify(ui).appendStatus(chan, "(help)", "/dcc accept <nick>");
    verify(ui).appendStatus(chan, "(help)", "/dcc get <nick> [save-path]");
    verify(ui)
        .appendStatus(chan, "(help)", "/dcc msg <nick> <text>  (alias: /dccmsg <nick> <text>)");
    verify(ui).appendStatus(chan, "(help)", "/dcc close <nick>  /dcc list  /dcc panel");
    verify(ui).appendStatus(chan, "(help)", "UI: right-click a nick and use the DCC submenu.");
  }

  @Test
  void helpUploadDelegatesToUploadCommandService() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);

    service.handleHelp("upload");

    verify(outboundUploadCommandService).appendUploadHelp(chan);
  }

  @Test
  void topicHelpCombinesOutboundAndPresentationContributors() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    OutboundHelpContributor outboundContributor =
        new OutboundHelpContributor() {
          @Override
          public Map<String, Consumer<OutboundHelpSink>> topicHelpHandlers() {
            return Map.of("shared", help -> help.appendLine("outbound shared help"));
          }
        };
    SlashCommandPresentationContributor presentationContributor =
        new SlashCommandPresentationContributor() {
          @Override
          public Map<String, Consumer<SlashCommandHelpSink>> topicHelpHandlers() {
            return Map.of("shared", help -> help.appendLine("presentation shared help"));
          }
        };
    OutboundHelpCommandService composedService =
        new OutboundHelpCommandService(
            ui,
            targetCoordinator,
            List.of(outboundContributor),
            new SlashCommandPresentationCatalog(
                List.of(presentationContributor), BackendNamedCommandCatalog.empty()));

    composedService.handleHelp("shared");

    verify(ui).appendStatus(chan, "(help)", "outbound shared help");
    verify(ui).appendStatus(chan, "(help)", "presentation shared help");
  }

  @Test
  void loadsHelpContributorsFromInstalledPluginsPort() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);

    OutboundHelpCommandService serviceWithPlugin =
        new OutboundHelpCommandService(
            ui,
            targetCoordinator,
            List.of(),
            slashCommandPresentationCatalog,
            new FakeInstalledPluginsPort(List.of(new PluginHelpContributor())));

    serviceWithPlugin.handleHelp("");
    serviceWithPlugin.handleHelp("pluginhelp");

    verify(ui).appendStatus(chan, "(help)", "/pluginhelp <arg> (provided by plugin)");
    verify(ui).appendStatus(chan, "(help)", "Plugin help topic from installed plugin");
  }

  @Test
  void installedPluginHelpContributorsCanReadPortableTargetView() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    OutboundHelpContributor targetEchoContributor =
        new OutboundHelpContributor() {
          @Override
          public void appendGeneralHelp(OutboundHelpSink help) {
            help.appendLine(help.target().serverId() + ":" + help.target().target());
          }
        };

    OutboundHelpCommandService serviceWithPlugin =
        new OutboundHelpCommandService(
            ui,
            targetCoordinator,
            List.of(),
            slashCommandPresentationCatalog,
            new FakeInstalledPluginsPort(List.of(targetEchoContributor)));

    serviceWithPlugin.handleHelp("");

    verify(ui).appendStatus(chan, "(help)", "libera:#ircafe");
  }

  @Test
  void duplicateOutboundHelpProviderClassesAreRegisteredOnce() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);

    OutboundHelpCommandService serviceWithDuplicateProvider =
        new OutboundHelpCommandService(
            ui,
            targetCoordinator,
            List.of(new PluginHelpContributor()),
            slashCommandPresentationCatalog,
            new FakeInstalledPluginsPort(List.of(new PluginHelpContributor())));

    serviceWithDuplicateProvider.handleHelp("");
    serviceWithDuplicateProvider.handleHelp("pluginhelp");

    verify(ui, times(1)).appendStatus(chan, "(help)", "/pluginhelp <arg> (provided by plugin)");
    verify(ui, times(1)).appendStatus(chan, "(help)", "Plugin help topic from installed plugin");
  }

  @Test
  void loadsServiceLoaderHelpContributorsFromInstalledPlugins() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("example-help-contributor.jar"),
        PLUGIN_HELP_CONTRIBUTOR_CLASS,
        pluginHelpContributorSource(),
        OutboundHelpContributor.class.getName(),
        CompiledPluginJarSupport.compatibleManifest("example-help-contributor", "1.0.0"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");
    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);

    OutboundHelpCommandService serviceWithPlugin =
        new OutboundHelpCommandService(
            ui, targetCoordinator, List.of(), slashCommandPresentationCatalog, installedPlugins);

    serviceWithPlugin.handleHelp("");
    serviceWithPlugin.handleHelp("pluginjar");

    verify(ui).appendStatus(chan, "(help)", "/pluginjar <arg> (provided by plugin jar)");
    verify(ui).appendStatus(chan, "(help)", "Plugin jar help topic");
  }

  private static String pluginHelpContributorSource() {
    return """
        package plugin.help;

        import cafe.woden.ircclient.app.outbound.help.spi.OutboundHelpContributor;
        import cafe.woden.ircclient.app.outbound.help.spi.OutboundHelpSink;
        import java.util.Map;
        import java.util.function.Consumer;

        public final class PluginHelpContributor implements OutboundHelpContributor {
          @Override
          public void appendGeneralHelp(OutboundHelpSink help) {
            help.appendLine("/pluginjar <arg> (provided by plugin jar)");
          }

          @Override
          public Map<String, Consumer<OutboundHelpSink>> topicHelpHandlers() {
            return Map.of("pluginjar", help -> help.appendLine("Plugin jar help topic"));
          }
        }
        """;
  }

  private static final class FakeInstalledPluginsPort implements InstalledPluginsPort {
    private final List<?> pluginServices;

    private FakeInstalledPluginsPort(List<?> pluginServices) {
      this.pluginServices = List.copyOf(pluginServices);
    }

    @Override
    public <T> List<T> loadInstalledServices(Class<T> serviceType, List<T> builtInServices) {
      ArrayList<T> services = new ArrayList<>(builtInServices);
      for (Object pluginService : pluginServices) {
        if (serviceType.isInstance(pluginService)) {
          services.add(serviceType.cast(pluginService));
        }
      }
      return List.copyOf(services);
    }
  }

  private static final class PluginHelpContributor implements OutboundHelpContributor {
    @Override
    public void appendGeneralHelp(OutboundHelpSink help) {
      help.appendLine("/pluginhelp <arg> (provided by plugin)");
    }

    @Override
    public Map<String, Consumer<OutboundHelpSink>> topicHelpHandlers() {
      return Map.of(
          "pluginhelp", help -> help.appendLine("Plugin help topic from installed plugin"));
    }
  }
}

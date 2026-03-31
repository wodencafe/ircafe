package cafe.woden.ircclient.app.outbound.help;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.BackendNamedCommandCatalog;
import cafe.woden.ircclient.app.commands.SlashCommandPresentationCatalog;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.*;
import cafe.woden.ircclient.app.outbound.help.spi.OutboundHelpContributor;
import cafe.woden.ircclient.app.outbound.readmarker.OutboundReadMarkerCommandService;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundCommandAvailabilitySupport;
import cafe.woden.ircclient.app.outbound.support.OutboundConnectionStatusSupport;
import cafe.woden.ircclient.app.outbound.upload.OutboundUploadCommandService;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class OutboundHelpCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final CommandTargetPolicy commandTargetPolicy = new CommandTargetPolicy(serverCatalog);
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry =
      new OutboundBackendFeatureRegistry(
          List.of(
              new MatrixOutboundBackendFeatureAdapter(),
              new QuasselOutboundBackendFeatureAdapter()));
  private final OutboundBackendCapabilityPolicy outboundBackendCapabilityPolicy =
      new OutboundBackendCapabilityPolicy(
          commandTargetPolicy,
          outboundBackendFeatureRegistry,
          IrcNegotiatedFeaturePort.from(irc),
          irc);
  private final OutboundCommandAvailabilitySupport outboundCommandAvailabilitySupport =
      new OutboundCommandAvailabilitySupport(outboundBackendCapabilityPolicy);
  private final OutboundConnectionStatusSupport outboundConnectionStatusSupport =
      new OutboundConnectionStatusSupport(ui, connectionCoordinator);
  private final OutboundUploadCommandService outboundUploadCommandService =
      mock(OutboundUploadCommandService.class);
  private final OutboundHelpContributor uploadHelpContributor =
      new OutboundHelpContributor() {
        @Override
        public void appendGeneralHelp(TargetRef out) {
          outboundUploadCommandService.appendUploadHelp(out);
        }

        @Override
        public Map<String, Consumer<TargetRef>> topicHelpHandlers() {
          return Map.of("upload", outboundUploadCommandService::appendUploadHelp);
        }
      };
  private final OutboundHelpContributor messageMutationHelpContributor =
      new OutboundHelpContributor() {
        @Override
        public void appendGeneralHelp(TargetRef out) {
          ui.appendStatus(out, "(help)", "/reply <msgid> <message> (requires draft/reply)");
          ui.appendStatus(
              out,
              "(help)",
              "/react <msgid> <reaction-token> (requires draft/react + draft/reply)");
          ui.appendStatus(
              out,
              "(help)",
              "/unreact <msgid> <reaction-token> (requires draft/unreact + draft/reply)");
          appendEditHelp(out);
          appendRedactHelp(out);
        }

        @Override
        public Map<String, Consumer<TargetRef>> topicHelpHandlers() {
          return Map.of(
              "edit", OutboundHelpCommandServiceTest.this::appendEditHelp,
              "redact", OutboundHelpCommandServiceTest.this::appendRedactHelp,
              "delete", OutboundHelpCommandServiceTest.this::appendRedactHelp);
        }
      };
  private final OutboundReadMarkerCommandService readMarkerCommandService =
      new OutboundReadMarkerCommandService(
          IrcReadMarkerPort.from(irc),
          outboundBackendCapabilityPolicy,
          outboundCommandAvailabilitySupport,
          outboundConnectionStatusSupport,
          ui,
          targetCoordinator);
  private final SlashCommandPresentationCatalog slashCommandPresentationCatalog =
      new SlashCommandPresentationCatalog(List.of(), new BackendNamedCommandCatalog(List.of()));
  private final OutboundHelpCommandService service =
      new OutboundHelpCommandService(
          ui,
          targetCoordinator,
          List.of(uploadHelpContributor, messageMutationHelpContributor, readMarkerCommandService),
          slashCommandPresentationCatalog);

  private void appendEditHelp(TargetRef out) {
    TargetRef target = out != null ? out : targetCoordinator.safeStatusTarget();
    String serverId = target.serverId();
    boolean available = outboundBackendCapabilityPolicy.supportsMessageEdit(serverId);
    ui.appendStatus(
        target,
        "(help)",
        "/edit <msgid> <message>"
            + (available
                ? ""
                : outboundCommandAvailabilitySupport.helpAvailabilitySuffix(
                    serverId, false, "requires negotiated draft/message-edit or message-edit")));
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

  @Test
  void helpAnnotatesEditAndRedactAsUnavailableWhenCapsNotNegotiated() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isMessageEditAvailable("libera")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(false);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleHelp("");

    verify(ui)
        .appendStatus(eq(chan), eq("(help)"), contains("/edit <msgid> <message> (unavailable:"));
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
    when(irc.isMessageEditAvailable("libera")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(false);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleHelp("");

    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/edit <msgid> <message> (unavailable: Quassel Core backend is not implemented yet)"));
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
    when(irc.isMessageEditAvailable("libera")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(false);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(false);

    service.handleHelp("");

    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains(
                "/edit <msgid> <message> (unavailable: requires negotiated draft/message-edit or message-edit)"));
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
    when(irc.isMessageEditAvailable("libera")).thenReturn(true);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);

    service.handleHelp("edit");
    service.handleHelp("redact");
    service.handleHelp("markread");

    verify(ui).appendStatus(chan, "(help)", "/edit <msgid> <message>");
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
}

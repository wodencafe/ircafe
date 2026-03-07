package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.IrcBackendClientService;
import cafe.woden.ircclient.irc.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
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
  private final PendingEchoMessagePort pendingEchoMessageState = mock(PendingEchoMessagePort.class);
  private final LabeledResponseRoutingPort labeledResponseRoutingState =
      mock(LabeledResponseRoutingPort.class);
  private final OutboundRawLineCorrelationService rawLineCorrelationService =
      new OutboundRawLineCorrelationService(
          outboundBackendCapabilityPolicy, labeledResponseRoutingState);
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
  private final OutboundMessageMutationCommandService messageMutationCommandService =
      new OutboundMessageMutationCommandService(
          irc,
          outboundBackendCapabilityPolicy,
          ui,
          connectionCoordinator,
          targetCoordinator,
          pendingEchoMessageState,
          rawLineCorrelationService);
  private final OutboundReadMarkerCommandService readMarkerCommandService =
      new OutboundReadMarkerCommandService(
          irc, outboundBackendCapabilityPolicy, ui, connectionCoordinator, targetCoordinator);
  private final OutboundHelpCommandService service =
      new OutboundHelpCommandService(
          ui,
          targetCoordinator,
          List.of(uploadHelpContributor, messageMutationCommandService, readMarkerCommandService));

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

package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.irc.IrcBackendClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundChatCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ChatCommandRuntimeConfigPort runtimeConfig =
      mock(ChatCommandRuntimeConfigPort.class);
  private final AwayRoutingPort awayRoutingState = mock(AwayRoutingPort.class);
  private final LabeledResponseRoutingPort labeledResponseRoutingState =
      mock(LabeledResponseRoutingPort.class);
  private final PendingEchoMessagePort pendingEchoMessageState = mock(PendingEchoMessagePort.class);
  private final OutboundRawLineCorrelationService rawLineCorrelationService =
      new OutboundRawLineCorrelationService(irc, labeledResponseRoutingState);
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
          irc,
          ui,
          connectionCoordinator,
          targetCoordinator,
          pendingEchoMessageState,
          rawLineCorrelationService);
  private final OutboundReadMarkerCommandService readMarkerCommandService =
      new OutboundReadMarkerCommandService(irc, irc, ui, connectionCoordinator, targetCoordinator);
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final OutboundChatCommandService service =
      new OutboundChatCommandService(
          irc,
          irc,
          ui,
          connectionCoordinator,
          targetCoordinator,
          rawLineCorrelationService,
          List.of(uploadHelpContributor, messageMutationCommandService, readMarkerCommandService),
          runtimeConfig,
          awayRoutingState,
          pendingEchoMessageState);

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void nickWhileConnectedRequestsChangeWithoutPersistingPreferredNick() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.changeNick("libera", "alice1")).thenReturn(Completable.complete());

    service.handleNick(disposables, "alice1");

    verify(irc).changeNick("libera", "alice1");
    verify(runtimeConfig, never()).rememberNick(anyString(), anyString());
  }

  @Test
  void nickWhileDisconnectedPersistsPreferredNickOnly() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);

    service.handleNick(disposables, "alice1");

    verify(runtimeConfig).rememberNick("libera", "alice1");
    verify(irc, never()).changeNick(anyString(), anyString());
    verify(ui)
        .appendStatus(
            new TargetRef("libera", "status"),
            "(nick)",
            "Not connected. Saved preferred nick for next connect.");
  }

  @Test
  void sendsLocalEchoWhenEchoMessageIsUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendMessage("libera", "#ircafe", "hello")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);

    service.handleSay(disposables, "hello");

    verify(ui).appendChat(chan, "(me)", "hello", true);
  }

  @Test
  void sendMessageOnQuasselBackendUsesRegularMessagePath() {
    TargetRef chan = new TargetRef("quassel", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("quassel")).thenReturn(true);
    when(irc.sendMessage("quassel", "#ircafe", "hello")).thenReturn(Completable.complete());
    when(irc.currentNick("quassel")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("quassel")).thenReturn(false);

    service.handleSay(disposables, "hello");

    verify(irc).sendMessage("quassel", "#ircafe", "hello");
    verify(ui).appendChat(chan, "(me)", "hello", true);
  }

  @Test
  void suppressesLocalEchoWhenEchoMessageIsAvailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    Instant createdAt = Instant.parse("2026-02-16T00:00:00Z");
    PendingEchoMessagePort.PendingOutboundChat pending =
        new PendingEchoMessagePort.PendingOutboundChat("pending-1", chan, "me", "hello", createdAt);
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendMessage("libera", "#ircafe", "hello")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);
    when(pendingEchoMessageState.register(eq(chan), eq("me"), eq("hello"), any(Instant.class)))
        .thenReturn(pending);

    service.handleSay(disposables, "hello");

    verify(ui, never()).appendChat(eq(chan), any(), eq("hello"), eq(true));
    verify(ui).appendPendingOutgoingChat(chan, "pending-1", createdAt, "me", "hello");
  }

  @Test
  void multilineSendFallsBackToSplitLinesWhenNotNegotiatedAndUserConfirms() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isMultilineAvailable("libera")).thenReturn(false);
    when(ui.confirmMultilineSplitFallback(
            chan, 2, 17L, "IRCv3 multiline is not negotiated on this server."))
        .thenReturn(true);
    when(irc.sendMessage("libera", "#ircafe", "line one")).thenReturn(Completable.complete());
    when(irc.sendMessage("libera", "#ircafe", "line two")).thenReturn(Completable.complete());

    service.handleSay(disposables, "line one\nline two");

    verify(irc).sendMessage("libera", "#ircafe", "line one");
    verify(irc).sendMessage("libera", "#ircafe", "line two");
    verify(irc, never()).sendMessage("libera", "#ircafe", "line one\nline two");
    verify(ui).appendStatus(eq(chan), eq("(send)"), contains("Sending as 2 separate lines."));
  }

  @Test
  void multilineSendCancelsWhenNotNegotiatedAndUserDeclinesFallback() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isMultilineAvailable("libera")).thenReturn(false);
    when(ui.confirmMultilineSplitFallback(
            chan, 2, 17L, "IRCv3 multiline is not negotiated on this server."))
        .thenReturn(false);

    service.handleSay(disposables, "line one\nline two");

    verify(irc, never()).sendMessage(eq("libera"), eq("#ircafe"), any());
    verify(ui).appendStatus(chan, "(send)", "Send canceled.");
  }

  @Test
  void multilineSendUsesBackendAvailabilityReasonWhenProvided() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.backendAvailabilityReason("libera"))
        .thenReturn("Quassel Core backend is not implemented yet");
    when(ui.confirmMultilineSplitFallback(
            chan, 2, 17L, "Quassel Core backend is not implemented yet."))
        .thenReturn(false);

    service.handleSay(disposables, "line one\nline two");

    verify(ui)
        .confirmMultilineSplitFallback(
            chan, 2, 17L, "Quassel Core backend is not implemented yet.");
    verify(irc, never()).sendMessage(eq("libera"), eq("#ircafe"), any());
  }

  @Test
  void multilineSendFallsBackToSplitLinesWhenOverNegotiatedMaxLines() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isMultilineAvailable("libera")).thenReturn(true);
    when(irc.negotiatedMultilineMaxLines("libera")).thenReturn(1);
    when(ui.confirmMultilineSplitFallback(eq(chan), eq(2), eq(17L), contains("max-lines is 1")))
        .thenReturn(true);
    when(irc.sendMessage("libera", "#ircafe", "line one")).thenReturn(Completable.complete());
    when(irc.sendMessage("libera", "#ircafe", "line two")).thenReturn(Completable.complete());

    service.handleSay(disposables, "line one\nline two");

    verify(irc).sendMessage("libera", "#ircafe", "line one");
    verify(irc).sendMessage("libera", "#ircafe", "line two");
    verify(irc, never()).sendMessage("libera", "#ircafe", "line one\nline two");
  }

  @Test
  void multilineSendUsesBatchPathWhenNegotiatedAndWithinLimits() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isMultilineAvailable("libera")).thenReturn(true);
    when(irc.negotiatedMultilineMaxLines("libera")).thenReturn(5);
    when(irc.negotiatedMultilineMaxBytes("libera")).thenReturn(4096L);
    when(irc.sendMessage("libera", "#ircafe", "line one\nline two"))
        .thenReturn(Completable.complete());

    service.handleSay(disposables, "line one\nline two");

    verify(irc).sendMessage("libera", "#ircafe", "line one\nline two");
    verify(ui, never()).confirmMultilineSplitFallback(any(), anyInt(), anyLong(), any());
  }

  @Test
  void marksPendingMessageFailedWhenSendErrors() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    Instant createdAt = Instant.parse("2026-02-16T00:00:00Z");
    PendingEchoMessagePort.PendingOutboundChat pending =
        new PendingEchoMessagePort.PendingOutboundChat("pending-2", chan, "me", "hello", createdAt);
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendMessage("libera", "#ircafe", "hello"))
        .thenReturn(Completable.error(new RuntimeException("boom")));
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);
    when(pendingEchoMessageState.register(eq(chan), eq("me"), eq("hello"), any(Instant.class)))
        .thenReturn(pending);

    service.handleSay(disposables, "hello");

    verify(pendingEchoMessageState).removeById("pending-2");
    verify(ui)
        .failPendingOutgoingChat(
            eq(chan), eq("pending-2"), any(Instant.class), eq("me"), eq("hello"), contains("boom"));
  }

  @Test
  void quoteInjectsLabelWhenLabeledResponseIsAvailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isLabeledResponseAvailable("libera")).thenReturn(true);
    when(labeledResponseRoutingState.prepareOutgoingRaw("libera", "MONITOR +nick"))
        .thenReturn(
            new LabeledResponseRoutingPort.PreparedRawLine(
                "@label=req-1 MONITOR +nick", "req-1", true));
    when(irc.sendRaw("libera", "@label=req-1 MONITOR +nick")).thenReturn(Completable.complete());

    service.handleQuote(disposables, "MONITOR +nick");

    verify(irc).sendRaw("libera", "@label=req-1 MONITOR +nick");
    verify(labeledResponseRoutingState)
        .remember(eq("libera"), eq("req-1"), eq(chan), eq("MONITOR +nick"), any(Instant.class));
    verify(ui)
        .appendStatus(
            eq(status), eq("(quote)"), argThat(s -> s != null && s.contains("{label=req-1}")));
  }

  @Test
  void quoteSendsOriginalRawLineWhenLabeledResponseIsUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isLabeledResponseAvailable("libera")).thenReturn(false);
    when(irc.sendRaw("libera", "MONITOR +nick")).thenReturn(Completable.complete());

    service.handleQuote(disposables, "MONITOR +nick");

    verify(irc).sendRaw("libera", "MONITOR +nick");
    verify(labeledResponseRoutingState, never()).prepareOutgoingRaw(any(), any());
    verify(labeledResponseRoutingState, never()).remember(any(), any(), any(), any(), any());
  }

  @Test
  void quoteOnMatrixBackendForwardsRawToBackend() {
    TargetRef chan = new TargetRef("matrix", "#room:example.org");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(irc.sendRaw("matrix", "MONITOR +nick")).thenReturn(Completable.complete());

    service.handleQuote(disposables, "MONITOR +nick");

    verify(irc).sendRaw("matrix", "MONITOR +nick");
  }

  @Test
  void statusRawSendOnMatrixBackendForwardsRawToBackend() {
    TargetRef status = new TargetRef("matrix", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("matrix")).thenReturn(true);
    when(irc.sendRaw("matrix", "WHO #room:example.org")).thenReturn(Completable.complete());

    service.handleSay(disposables, "WHO #room:example.org");

    verify(irc).sendRaw("matrix", "WHO #room:example.org");
  }

  @Test
  void statusRawSendCanInjectAndTrackLabel() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isLabeledResponseAvailable("libera")).thenReturn(true);
    when(labeledResponseRoutingState.prepareOutgoingRaw("libera", "WHO #ircafe"))
        .thenReturn(
            new LabeledResponseRoutingPort.PreparedRawLine(
                "@label=req-2 WHO #ircafe", "req-2", true));
    when(irc.sendRaw("libera", "@label=req-2 WHO #ircafe")).thenReturn(Completable.complete());

    service.handleSay(disposables, "WHO #ircafe");

    verify(irc).sendRaw("libera", "@label=req-2 WHO #ircafe");
    verify(labeledResponseRoutingState)
        .remember(eq("libera"), eq("req-2"), eq(status), eq("WHO #ircafe"), any(Instant.class));
    verify(ui)
        .appendStatus(
            eq(status), eq("(raw)"), argThat(s -> s != null && s.contains("{label=req-2}")));
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

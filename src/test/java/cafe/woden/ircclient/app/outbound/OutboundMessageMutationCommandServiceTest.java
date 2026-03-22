package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.*;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.port.IrcEchoCapabilityPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundMessageMutationCommandServiceTest {

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
  private final PendingEchoMessagePort pendingEchoMessageState = mock(PendingEchoMessagePort.class);
  private final LabeledResponseRoutingPort labeledResponseRoutingState =
      mock(LabeledResponseRoutingPort.class);
  private final OutboundRawLineCorrelationService rawLineCorrelationService =
      new OutboundRawLineCorrelationService(
          outboundBackendCapabilityPolicy, labeledResponseRoutingState);
  private final MessageMutationOutboundCommandsRouter messageMutationOutboundCommandsRouter =
      new MessageMutationOutboundCommandsRouter(
          List.of(
              new IrcMessageMutationOutboundCommands(),
              new MatrixMessageMutationOutboundCommands(),
              new QuasselMessageMutationOutboundCommands()));
  private final OutboundMessageMutationSendSupport outboundMessageMutationSendSupport =
      new OutboundMessageMutationSendSupport(
          IrcTargetMembershipPort.from(irc),
          IrcEchoCapabilityPort.from(irc),
          outboundBackendCapabilityPolicy,
          messageMutationOutboundCommandsRouter,
          ui,
          outboundConnectionStatusSupport,
          targetCoordinator,
          pendingEchoMessageState,
          rawLineCorrelationService);
  private final OutboundMessageMutationCommandService service =
      new OutboundMessageMutationCommandService(
          outboundBackendCapabilityPolicy,
          outboundCommandAvailabilitySupport,
          ui,
          targetCoordinator,
          outboundMessageMutationSendSupport);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void replyCommandSendsTaggedPrivmsgWithoutQuotePrefill() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.sendRaw("libera", "@+draft/reply=abc123 PRIVMSG #ircafe :hello there"))
        .thenReturn(Completable.complete());

    service.handleReplyMessage(disposables, "abc123", "hello there");

    verify(irc).sendRaw("libera", "@+draft/reply=abc123 PRIVMSG #ircafe :hello there");
    verify(ui).appendChat(chan, "(me)", "hello there", true);
  }

  @Test
  void replyCommandUsesPendingStateWhenEchoMessageAvailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    Instant createdAt = Instant.parse("2026-02-16T00:00:00Z");
    PendingEchoMessagePort.PendingOutboundChat pending =
        new PendingEchoMessagePort.PendingOutboundChat(
            "pending-reply", chan, "me", "hello", createdAt);
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(pendingEchoMessageState.register(eq(chan), eq("me"), eq("hello"), any(Instant.class)))
        .thenReturn(pending);
    when(irc.sendRaw("libera", "@+draft/reply=abc123 PRIVMSG #ircafe :hello"))
        .thenReturn(Completable.complete());

    service.handleReplyMessage(disposables, "abc123", "hello");

    verify(ui).appendPendingOutgoingChat(chan, "pending-reply", createdAt, "me", "hello");
    verify(ui, never()).appendChat(eq(chan), any(), eq("hello"), eq(true));
  }

  @Test
  void reactCommandSendsTaggedTagmsgAndAppliesLocalReactionWhenEchoUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isDraftReactAvailable("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.sendRaw("libera", "@+draft/react=:+1:;+draft/reply=abc123 TAGMSG #ircafe"))
        .thenReturn(Completable.complete());

    service.handleReactMessage(disposables, "abc123", ":+1:");

    verify(irc).sendRaw("libera", "@+draft/react=:+1:;+draft/reply=abc123 TAGMSG #ircafe");
    verify(ui)
        .applyMessageReaction(eq(chan), any(Instant.class), eq("me"), eq("abc123"), eq(":+1:"));
  }

  @Test
  void unreactCommandSendsTaggedTagmsgAndRemovesLocalReactionWhenEchoUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isDraftUnreactAvailable("libera")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.sendRaw("libera", "@+draft/unreact=:+1:;+draft/reply=abc123 TAGMSG #ircafe"))
        .thenReturn(Completable.complete());

    service.handleUnreactMessage(disposables, "abc123", ":+1:");

    verify(irc).sendRaw("libera", "@+draft/unreact=:+1:;+draft/reply=abc123 TAGMSG #ircafe");
    verify(ui)
        .removeMessageReaction(eq(chan), any(Instant.class), eq("me"), eq("abc123"), eq(":+1:"));
  }

  @Test
  void editCommandSendsTaggedPrivmsgAndAppliesLocalEditWhenEchoUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isMessageEditAvailable("libera")).thenReturn(true);
    when(ui.isOwnMessage(chan, "abc123")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.sendRaw("libera", "@+draft/edit=abc123 PRIVMSG #ircafe :fixed text"))
        .thenReturn(Completable.complete());

    service.handleEditMessage(disposables, "abc123", "fixed text");

    verify(irc).sendRaw("libera", "@+draft/edit=abc123 PRIVMSG #ircafe :fixed text");
    verify(ui)
        .applyMessageEdit(
            eq(chan),
            any(Instant.class),
            eq("me"),
            eq("abc123"),
            eq("fixed text"),
            eq(""),
            eq(java.util.Map.of("draft/edit", "abc123")));
  }

  @Test
  void editCommandRejectsNonOwnedMessageBeforeSending() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isMessageEditAvailable("libera")).thenReturn(true);
    when(ui.isOwnMessage(chan, "abc123")).thenReturn(false);

    service.handleEditMessage(disposables, "abc123", "fixed text");

    verify(ui).appendStatus(chan, "(edit)", "Can only edit your own messages in this buffer.");
    verify(irc, never()).sendRaw(eq("libera"), any());
    verify(ui, never()).applyMessageEdit(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void redactCommandSendsRedactAndAppliesLocalRedactionWhenEchoUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(true);
    when(ui.isOwnMessage(chan, "abc123")).thenReturn(true);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.sendRaw("libera", "REDACT #ircafe abc123")).thenReturn(Completable.complete());

    service.handleRedactMessage(disposables, "abc123", "");

    verify(irc).sendRaw("libera", "REDACT #ircafe abc123");
    verify(ui)
        .applyMessageRedaction(
            eq(chan),
            any(Instant.class),
            eq("me"),
            eq("abc123"),
            eq(""),
            eq(java.util.Map.of("draft/delete", "abc123")));
  }

  @Test
  void redactCommandWithReasonSendsTrailingReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(true);
    when(ui.isOwnMessage(chan, "abc123")).thenReturn(true);
    when(irc.sendRaw("libera", "REDACT #ircafe abc123 :cleanup old context"))
        .thenReturn(Completable.complete());

    service.handleRedactMessage(disposables, "abc123", "cleanup old context");

    verify(irc).sendRaw("libera", "REDACT #ircafe abc123 :cleanup old context");
  }

  @Test
  void redactCommandRejectsNonOwnedMessageBeforeSending() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(true);
    when(ui.isOwnMessage(chan, "abc123")).thenReturn(false);

    service.handleRedactMessage(disposables, "abc123", "");

    verify(ui).appendStatus(chan, "(redact)", "Can only redact your own messages in this buffer.");
    verify(irc, never()).sendRaw(eq("libera"), any());
    verify(ui, never()).applyMessageRedaction(any(), any(), any(), any(), any(), any());
  }
}

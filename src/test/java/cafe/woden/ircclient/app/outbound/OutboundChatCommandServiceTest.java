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

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.state.AwayRoutingState;
import cafe.woden.ircclient.app.state.ChatHistoryRequestRoutingState;
import cafe.woden.ircclient.app.state.ChatHistoryRequestRoutingState.QueryMode;
import cafe.woden.ircclient.app.state.JoinRoutingState;
import cafe.woden.ircclient.app.state.LabeledResponseRoutingState;
import cafe.woden.ircclient.app.state.PendingEchoMessageState;
import cafe.woden.ircclient.app.state.PendingInviteState;
import cafe.woden.ircclient.app.state.WhoisRoutingState;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundChatCommandServiceTest {

  private final IrcClientService irc = mock(IrcClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
  private final AwayRoutingState awayRoutingState = mock(AwayRoutingState.class);
  private final ChatHistoryRequestRoutingState chatHistoryRequestRoutingState =
      mock(ChatHistoryRequestRoutingState.class);
  private final JoinRoutingState joinRoutingState = mock(JoinRoutingState.class);
  private final LabeledResponseRoutingState labeledResponseRoutingState =
      mock(LabeledResponseRoutingState.class);
  private final PendingEchoMessageState pendingEchoMessageState =
      mock(PendingEchoMessageState.class);
  private final PendingInviteState pendingInviteState = mock(PendingInviteState.class);
  private final WhoisRoutingState whoisRoutingState = mock(WhoisRoutingState.class);
  private final IgnoreListService ignoreListService = mock(IgnoreListService.class);
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final OutboundChatCommandService service =
      new OutboundChatCommandService(
          irc,
          ui,
          connectionCoordinator,
          targetCoordinator,
          runtimeConfig,
          awayRoutingState,
          chatHistoryRequestRoutingState,
          joinRoutingState,
          labeledResponseRoutingState,
          pendingEchoMessageState,
          pendingInviteState,
          whoisRoutingState,
          ignoreListService);

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void joinWithKeySendsRawJoinLine() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "JOIN #secret hunter2")).thenReturn(Completable.complete());

    service.handleJoin(disposables, "#secret", "hunter2");

    verify(runtimeConfig).rememberJoinedChannel("libera", "#secret");
    verify(joinRoutingState).rememberOrigin("libera", "#secret", status);
    verify(irc).sendRaw("libera", "JOIN #secret hunter2");
  }

  @Test
  void joinFromUiOnlyTargetRoutesJoinOriginToStatus() {
    TargetRef listTarget = TargetRef.channelList("libera");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(listTarget);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.joinChannel("libera", "#ircafe")).thenReturn(Completable.complete());

    service.handleJoin(disposables, "#ircafe", "");

    verify(joinRoutingState).rememberOrigin("libera", "#ircafe", status);
    verify(irc).joinChannel("libera", "#ircafe");
  }

  @Test
  void partWithoutActiveTargetPromptsToSelectServer() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(null);
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);

    service.handlePart(disposables, "", "");

    verify(ui).appendStatus(status, "(part)", "Select a server first.");
    verify(targetCoordinator, never()).detachChannel(any(TargetRef.class), anyString());
  }

  @Test
  void partWithoutExplicitChannelDetachesActiveChannelWithTrimmedReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);

    service.handlePart(disposables, "", "  be right back  ");

    verify(targetCoordinator).detachChannel(chan, "be right back");
  }

  @Test
  void partWithoutExplicitChannelRejectsNonChannelSelection() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "", "bye");

    verify(ui)
        .appendStatus(
            status, "(part)", "Usage: /part [#channel] [reason] (or select a channel first)");
    verify(targetCoordinator, never()).detachChannel(any(TargetRef.class), anyString());
  }

  @Test
  void partWithExplicitChannelDetachesChannelOnActiveServer() {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef expected = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "  #ircafe ", "  later ");

    verify(targetCoordinator).detachChannel(expected, "later");
  }

  @Test
  void partWithExplicitNonChannelShowsUsageAndDoesNotDetach() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handlePart(disposables, "alice", "bye");

    verify(ui).appendStatus(status, "(part)", "Usage: /part [#channel] [reason]");
    verify(targetCoordinator, never()).detachChannel(any(TargetRef.class), anyString());
  }

  @Test
  void connectWithoutArgUsesActiveServerContext() {
    TargetRef pm = new TargetRef("libera", "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(pm);

    service.handleConnect("");

    verify(connectionCoordinator).connectOne("libera");
  }

  @Test
  void connectAllKeywordConnectsAllServers() {
    when(targetCoordinator.getActiveTarget()).thenReturn(null);

    service.handleConnect("all");

    verify(connectionCoordinator).connectAll();
  }

  @Test
  void reconnectWithExplicitServerRoutesToCoordinator() {
    service.handleReconnect("oftc");

    verify(connectionCoordinator).reconnectOne("oftc");
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
  void quitWithReasonDisconnectsCurrentServerUsingReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(targetCoordinator.safeStatusTarget()).thenReturn(new TargetRef("libera", "status"));

    service.handleQuit("gone for lunch");

    verify(connectionCoordinator).disconnectOne("libera", "gone for lunch");
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
  void suppressesLocalEchoWhenEchoMessageIsAvailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    Instant createdAt = Instant.parse("2026-02-16T00:00:00Z");
    PendingEchoMessageState.PendingOutboundChat pending =
        new PendingEchoMessageState.PendingOutboundChat(
            "pending-1", chan, "me", "hello", createdAt);
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
    PendingEchoMessageState.PendingOutboundChat pending =
        new PendingEchoMessageState.PendingOutboundChat(
            "pending-2", chan, "me", "hello", createdAt);
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
  void chatHistorySelectorUsesSelectorOverload() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.requestChatHistoryBefore("libera", "#ircafe", "msgid=abc123", 40))
        .thenReturn(Completable.complete());

    service.handleChatHistoryBefore(disposables, 40, "msgid=abc123");

    verify(irc).requestChatHistoryBefore("libera", "#ircafe", "msgid=abc123", 40);
    verify(chatHistoryRequestRoutingState)
        .remember(
            eq("libera"),
            eq("#ircafe"),
            eq(chan),
            eq(40),
            eq("msgid=abc123"),
            any(Instant.class),
            eq(QueryMode.BEFORE));
  }

  @Test
  void chatHistoryLatestRoutesThroughLatestRequest() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.requestChatHistoryLatest("libera", "#ircafe", "*", 55))
        .thenReturn(Completable.complete());

    service.handleChatHistoryLatest(disposables, 55, "*");

    verify(irc).requestChatHistoryLatest("libera", "#ircafe", "*", 55);
    verify(chatHistoryRequestRoutingState)
        .remember(
            eq("libera"),
            eq("#ircafe"),
            eq(chan),
            eq(55),
            eq("*"),
            any(Instant.class),
            eq(QueryMode.LATEST));
  }

  @Test
  void chatHistoryBetweenRoutesThroughBetweenRequest() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.requestChatHistoryBetween("libera", "#ircafe", "msgid=a", "msgid=b", 30))
        .thenReturn(Completable.complete());

    service.handleChatHistoryBetween(disposables, "msgid=a", "msgid=b", 30);

    verify(irc).requestChatHistoryBetween("libera", "#ircafe", "msgid=a", "msgid=b", 30);
    verify(chatHistoryRequestRoutingState)
        .remember(
            eq("libera"),
            eq("#ircafe"),
            eq(chan),
            eq(30),
            eq("msgid=a .. msgid=b"),
            any(Instant.class),
            eq(QueryMode.BETWEEN));
  }

  @Test
  void chatHistoryAroundRoutesThroughAroundRequest() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.requestChatHistoryAround("libera", "#ircafe", "msgid=anchor", 45))
        .thenReturn(Completable.complete());

    service.handleChatHistoryAround(disposables, "msgid=anchor", 45);

    verify(irc).requestChatHistoryAround("libera", "#ircafe", "msgid=anchor", 45);
    verify(chatHistoryRequestRoutingState)
        .remember(
            eq("libera"),
            eq("#ircafe"),
            eq(chan),
            eq(45),
            eq("msgid=anchor"),
            any(Instant.class),
            eq(QueryMode.AROUND));
  }

  @Test
  void listOpensChannelListTargetAndSendsListRawLine() {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef channelList = TargetRef.channelList("libera");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "LIST >10")).thenReturn(Completable.complete());

    service.handleList(disposables, ">10");

    verify(ui).ensureTargetExists(channelList);
    verify(ui).beginChannelList("libera", "Loading channel list (>10)...");
    verify(ui).selectTarget(channelList);
    verify(irc).sendRaw("libera", "LIST >10");
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
            new LabeledResponseRoutingState.PreparedRawLine(
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
  void statusRawSendCanInjectAndTrackLabel() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isLabeledResponseAvailable("libera")).thenReturn(true);
    when(labeledResponseRoutingState.prepareOutgoingRaw("libera", "WHO #ircafe"))
        .thenReturn(
            new LabeledResponseRoutingState.PreparedRawLine(
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
    PendingEchoMessageState.PendingOutboundChat pending =
        new PendingEchoMessageState.PendingOutboundChat(
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

  @Test
  void helpAnnotatesEditAndRedactAsUnavailableWhenCapsNotNegotiated() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isMessageEditAvailable("libera")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(false);

    service.handleHelp("");

    verify(ui)
        .appendStatus(eq(chan), eq("(help)"), contains("/edit <msgid> <message> (unavailable:"));
    verify(ui)
        .appendStatus(
            eq(chan),
            eq("(help)"),
            contains("/redact <msgid> [reason] (alias: /delete) (unavailable:"));
  }

  @Test
  void helpShowsEditAndRedactWithoutUnavailableSuffixWhenCapsNegotiated() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.isMessageEditAvailable("libera")).thenReturn(true);
    when(irc.isMessageRedactionAvailable("libera")).thenReturn(true);

    service.handleHelp("edit");
    service.handleHelp("redact");

    verify(ui).appendStatus(chan, "(help)", "/edit <msgid> <message>");
    verify(ui).appendStatus(chan, "(help)", "/redact <msgid> [reason] (alias: /delete)");
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
  void inviteAutoJoinToggleFlipsCurrentState() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.inviteAutoJoinEnabled()).thenReturn(false);

    service.handleInviteAutoJoin("toggle");

    verify(pendingInviteState).setInviteAutoJoinEnabled(true);
    verify(runtimeConfig).rememberInviteAutoJoinEnabled(true);
    verify(ui).appendStatus(status, "(invite)", "Invite auto-join is now enabled.");
  }

  @Test
  void inviteAutoJoinStatusMentionsAjinviteAlias() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(pendingInviteState.inviteAutoJoinEnabled()).thenReturn(true);

    service.handleInviteAutoJoin("status");

    verify(ui)
        .appendStatus(
            status,
            "(invite)",
            "Invite auto-join is enabled. Use /inviteautojoin on|off or /ajinvite.");
  }
}

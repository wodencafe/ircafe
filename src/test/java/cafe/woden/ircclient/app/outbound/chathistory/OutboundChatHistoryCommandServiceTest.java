package cafe.woden.ircclient.app.outbound.chathistory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.Ircv3ChatHistoryFeatureSupport;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort.QueryMode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundChatHistoryCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ChatHistoryRequestRoutingPort chatHistoryRequestRoutingState =
      mock(ChatHistoryRequestRoutingPort.class);
  private final OutboundBackendCapabilityPolicy outboundBackendCapabilityPolicy =
      backendCapabilityPolicy();
  private final Ircv3ChatHistoryFeatureSupport chatHistoryFeatureSupport =
      new Ircv3ChatHistoryFeatureSupport(
          outboundBackendCapabilityPolicy, IrcNegotiatedFeaturePort.from(irc), irc);
  private final OutboundChatHistoryRequestSupport chatHistoryRequestSupport =
      new OutboundChatHistoryRequestSupport(
          ui,
          connectionCoordinator,
          targetCoordinator,
          chatHistoryRequestRoutingState,
          chatHistoryFeatureSupport);
  private final OutboundChatHistoryCommandService service =
      new OutboundChatHistoryCommandService(
          irc, targetCoordinator, chatHistoryFeatureSupport, chatHistoryRequestSupport);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void chatHistorySelectorUsesSelectorOverload() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isChatHistoryAvailable("libera")).thenReturn(true);
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
    when(irc.isChatHistoryAvailable("libera")).thenReturn(true);
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
    when(irc.isChatHistoryAvailable("libera")).thenReturn(true);
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
    when(irc.isChatHistoryAvailable("libera")).thenReturn(true);
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
  void chatHistoryCommandRefusesWhenCapabilityIsUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isChatHistoryAvailable("libera")).thenReturn(false);

    service.handleChatHistoryLatest(disposables, 55, "*");

    verify(ui)
        .appendStatus(status, "(chathistory)", "chathistory is not negotiated on this server.");
    verify(irc, never()).requestChatHistoryLatest(eq("libera"), eq("#ircafe"), any(), eq(55));
  }

  @Test
  void helpShowsAvailabilitySuffixWhenCapabilityIsUnavailable() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(irc.isChatHistoryAvailable("libera")).thenReturn(false);

    service.topicHelpHandlers().get("chathistory").accept(chan);

    verify(ui)
        .appendStatus(
            chan,
            "(help)",
            "/chathistory [limit] (unavailable: requires negotiated draft/chathistory or chathistory)");
  }

  private static OutboundBackendCapabilityPolicy backendCapabilityPolicy() {
    OutboundBackendCapabilityPolicy policy = mock(OutboundBackendCapabilityPolicy.class);
    when(policy.featureUnavailableMessage(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(policy.unavailableReasonForHelp(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    return policy;
  }
}

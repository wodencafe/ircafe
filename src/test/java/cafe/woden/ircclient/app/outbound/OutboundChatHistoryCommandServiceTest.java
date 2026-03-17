package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
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
  private final OutboundChatHistoryCommandService service =
      new OutboundChatHistoryCommandService(
          irc, ui, connectionCoordinator, targetCoordinator, chatHistoryRequestRoutingState);
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
}

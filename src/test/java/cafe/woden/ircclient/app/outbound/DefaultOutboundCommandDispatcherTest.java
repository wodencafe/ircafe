package cafe.woden.ircclient.app.outbound;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.commands.FilterCommand;
import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultOutboundCommandDispatcherTest {

  private final OutboundModeCommandService mode = mock(OutboundModeCommandService.class);
  private final OutboundCtcpWhoisCommandService ctcp = mock(OutboundCtcpWhoisCommandService.class);
  private final OutboundChatCommandService chat = mock(OutboundChatCommandService.class);
  private final OutboundIgnoreCommandService ignore = mock(OutboundIgnoreCommandService.class);
  private final LocalFilterCommandService filter = mock(LocalFilterCommandService.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final UiPort ui = mock(UiPort.class);
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final DefaultOutboundCommandDispatcher dispatcher =
      new DefaultOutboundCommandDispatcher(
          mode,
          ctcp,
          chat,
          ignore,
          filter,
          targetCoordinator,
          ui
      );

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void dispatchJoinRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Join("#ircafe"));
    verify(chat).handleJoin(disposables, "#ircafe");
  }

  @Test
  void dispatchWhoisRoutesToWhoisService() {
    dispatcher.dispatch(disposables, new ParsedInput.Whois("alice"));
    verify(ctcp).handleWhois(disposables, "alice");
  }

  @Test
  void dispatchTopicRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Topic("#ircafe", "new topic"));
    verify(chat).handleTopic(disposables, "#ircafe", "new topic");
  }

  @Test
  void dispatchKickRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Kick("#ircafe", "bob", "reason"));
    verify(chat).handleKick(disposables, "#ircafe", "bob", "reason");
  }

  @Test
  void dispatchInviteRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Invite("bob", "#ircafe"));
    verify(chat).handleInvite(disposables, "bob", "#ircafe");
  }

  @Test
  void dispatchNamesRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Names("#ircafe"));
    verify(chat).handleNames(disposables, "#ircafe");
  }

  @Test
  void dispatchWhoRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Who("#ircafe o"));
    verify(chat).handleWho(disposables, "#ircafe o");
  }

  @Test
  void dispatchListRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.ListCmd(">10"));
    verify(chat).handleList(disposables, ">10");
  }

  @Test
  void dispatchFilterRoutesToLocalFilterService() {
    FilterCommand.Help cmd = new FilterCommand.Help();
    dispatcher.dispatch(disposables, new ParsedInput.Filter(cmd));
    verify(filter).handle(cmd);
  }

  @Test
  void dispatchChatHistoryRoutesSelectorAndLimit() {
    dispatcher.dispatch(disposables, new ParsedInput.ChatHistoryBefore(80, "msgid=abc123"));
    verify(chat).handleChatHistoryBefore(disposables, 80, "msgid=abc123");
  }

  @Test
  void dispatchChatHistoryLatestRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.ChatHistoryLatest(70, "*"));
    verify(chat).handleChatHistoryLatest(disposables, 70, "*");
  }

  @Test
  void dispatchChatHistoryBetweenRoutesToChatService() {
    dispatcher.dispatch(
        disposables,
        new ParsedInput.ChatHistoryBetween("msgid=a", "msgid=b", 60));
    verify(chat).handleChatHistoryBetween(disposables, "msgid=a", "msgid=b", 60);
  }

  @Test
  void dispatchChatHistoryAroundRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.ChatHistoryAround("msgid=a", 50));
    verify(chat).handleChatHistoryAround(disposables, "msgid=a", 50);
  }

  @Test
  void dispatchUnknownShowsSystemMessageOnSafeTarget() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(null);
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);

    dispatcher.dispatch(disposables, new ParsedInput.Unknown("/wat"));

    verify(ui).appendStatus(status, "(system)", "Unknown command: /wat");
  }
}

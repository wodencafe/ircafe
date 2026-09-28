package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IrcOutboundCommandPortAdaptersTest {

  @Test
  void identityCommandsKeepArgumentsAndSubscribeOnlyWhenRequested() {
    IrcClientService irc = mock(IrcClientService.class);
    AtomicInteger sends = new AtomicInteger();
    Completable nickChange = Completable.fromAction(sends::incrementAndGet);
    when(irc.changeNick(" server ", " newNick ")).thenReturn(nickChange);
    var port = new IrcIdentityPortAdapter(irc);

    Completable result = port.changeNick(" server ", " newNick ");
    assertSame(nickChange, result);
    assertEquals(0, sends.get());
    result.test().assertComplete();
    assertEquals(1, sends.get());

    RuntimeException failure = new RuntimeException("away rejected");
    when(irc.setAway("server", null)).thenReturn(Completable.error(failure));
    port.setAway("server", null).test().assertError(failure);
    verify(irc).setAway("server", null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"#ircafe", "&local", "!room:server", "alice"})
  void messagingPreservesDefaultBackendTargetRouting(String target) {
    IrcClientService irc = mock(IrcClientService.class, CALLS_REAL_METHODS);
    Completable message = Completable.complete();
    boolean channel = !target.equals("alice");
    if (channel) {
      when(irc.sendToChannel("server", target, "hello")).thenReturn(message);
    } else {
      when(irc.sendPrivateMessage("server", target, "hello")).thenReturn(message);
    }
    var port = new IrcMessagingPortAdapter(irc);
    assertSame(message, port.sendMessage("server", " " + target + " ", "hello"));
    if (channel) {
      verify(irc).sendToChannel("server", target, "hello");
    } else {
      verify(irc).sendPrivateMessage("server", target, "hello");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"#ircafe", "&local", "alice"})
  void noticesAndActionsKeepBackendEncoding(String target) {
    IrcClientService irc = mock(IrcClientService.class, CALLS_REAL_METHODS);
    Completable notice = Completable.complete();
    boolean channel = !target.equals("alice");
    if (channel) {
      when(irc.sendNoticeToChannel("server", target, "heads up")).thenReturn(notice);
    } else {
      when(irc.sendNoticePrivate("server", target, "heads up")).thenReturn(notice);
    }
    var port = new IrcMessagingPortAdapter(irc);
    assertSame(notice, port.sendNotice("server", " " + target + " ", "heads up"));
    RuntimeException failure = new RuntimeException("send rejected");
    String action = "\u0001ACTION waves\u0001";
    if (channel) {
      when(irc.sendToChannel("server", target, action)).thenReturn(Completable.error(failure));
    } else {
      when(irc.sendPrivateMessage("server", target, action)).thenReturn(Completable.error(failure));
    }
    port.sendAction("server", target, "waves").test().assertError(failure);
  }

  @Test
  void messagingHonorsBackendOverridesInsteadOfReencodingCommands() {
    IrcClientService irc = mock(IrcClientService.class);
    var port = new IrcMessagingPortAdapter(irc);
    Completable message = Completable.never();
    Completable notice = Completable.never();
    Completable action = Completable.never();
    when(irc.sendMessage("server", "!room:server", "hello")).thenReturn(message);
    when(irc.sendNotice("server", "!room:server", "notice")).thenReturn(notice);
    when(irc.sendAction("server", "!room:server", "waves")).thenReturn(action);

    assertSame(message, port.sendMessage("server", "!room:server", "hello"));
    assertSame(notice, port.sendNotice("server", "!room:server", "notice"));
    assertSame(action, port.sendAction("server", "!room:server", "waves"));
  }

  @Test
  void historyRequestsKeepQueryModesSelectorsLimitsAndReactiveResults() {
    IrcClientService irc = mock(IrcClientService.class);
    var port = new IrcChatHistoryPortAdapter(irc);
    Completable before = Completable.never();
    Completable latest = Completable.never();
    Completable around = Completable.never();
    RuntimeException failure = new RuntimeException("history unavailable");
    Completable between = Completable.error(failure);
    String timestamp = "timestamp=2026-09-27T00:00:00Z";
    when(irc.requestChatHistoryBefore("server", "#ircafe", "msgid=first", 20)).thenReturn(before);
    when(irc.requestChatHistoryLatest("server", "#ircafe", "*", 30)).thenReturn(latest);
    when(irc.requestChatHistoryAround("server", "#ircafe", timestamp, 40)).thenReturn(around);
    when(irc.requestChatHistoryBetween("server", "#ircafe", "msgid=first", timestamp, 50))
        .thenReturn(between);

    assertSame(before, port.requestChatHistoryBefore("server", "#ircafe", "msgid=first", 20));
    assertSame(latest, port.requestChatHistoryLatest("server", "#ircafe", "*", 30));
    assertSame(around, port.requestChatHistoryAround("server", "#ircafe", timestamp, 40));
    port.requestChatHistoryBetween("server", "#ircafe", "msgid=first", timestamp, 50)
        .test()
        .assertError(failure);
  }

  @Test
  void monitorPreservesNegotiatedLimitAndDisposalReachesTransport() {
    IrcClientService irc = mock(IrcClientService.class);
    var port = new IrcMonitorPortAdapter(irc);
    when(irc.isMonitorAvailable(" server ")).thenReturn(true, false);
    assertTrue(port.isMonitorAvailable(" server "));
    assertFalse(port.isMonitorAvailable(" server "));
    when(irc.negotiatedMonitorLimit("server")).thenReturn(-1);
    assertEquals(-1, port.negotiatedMonitorLimit("server"));
    AtomicInteger disposals = new AtomicInteger();
    Completable send = Completable.never().doOnDispose(disposals::incrementAndGet);
    when(irc.sendRaw("server", "MONITOR + alice,bob")).thenReturn(send);

    Completable result = port.sendRaw("server", "MONITOR + alice,bob");
    assertSame(send, result);
    var observer = result.test();
    assertEquals(0, disposals.get());
    observer.dispose();
    assertEquals(1, disposals.get());
  }

  @Test
  void monitorEventsPreserveRoutedStreamErrorsAndCancellation() {
    IrcClientService irc = mock(IrcClientService.class);
    PublishProcessor<ServerIrcEvent> events = PublishProcessor.create();
    when(irc.events()).thenReturn(events);
    var port = new IrcMonitorPortAdapter(irc);
    assertSame(events, port.events());
    assertFalse(events.hasSubscribers());

    var subscriber = port.events().test();
    ServerIrcEvent ready =
        new ServerIrcEvent("server", new IrcEvent.ConnectionReady(Instant.now()));
    events.onNext(ready);
    subscriber.assertValue(ready);
    subscriber.cancel();
    assertFalse(events.hasSubscribers());

    var failingSubscriber = port.events().test();
    RuntimeException failure = new RuntimeException("event stream failed");
    events.onError(failure);
    failingSubscriber.assertError(failure);
  }

  @Test
  void historyPreservesBackendTimestampOverloadsWithoutConvertingArguments() {
    IrcClientService irc = mock(IrcClientService.class);
    var port = new IrcChatHistoryPortAdapter(irc);
    Instant cutoff = Instant.parse("2026-09-27T00:00:00.123456789Z");
    Completable instantRequest = Completable.never();
    Completable epochRequest = Completable.never();
    when(irc.requestChatHistoryBefore("server", "#ircafe", cutoff, 20)).thenReturn(instantRequest);
    when(irc.requestChatHistoryBefore("server", "#ircafe", cutoff.toEpochMilli(), 30))
        .thenReturn(epochRequest);

    assertSame(instantRequest, port.requestChatHistoryBefore("server", "#ircafe", cutoff, 20));
    assertSame(
        epochRequest,
        port.requestChatHistoryBefore("server", "#ircafe", cutoff.toEpochMilli(), 30));
  }

  @Test
  void epochHistoryRequestsRetainClientDefaultConversionAndErrors() {
    IrcClientService irc = mock(IrcClientService.class, CALLS_REAL_METHODS);
    var port = new IrcChatHistoryPortAdapter(irc);
    Instant cutoff = Instant.ofEpochMilli(1234L);
    RuntimeException failure = new RuntimeException("history unavailable");
    when(irc.requestChatHistoryBefore("server", "alice", cutoff, 40))
        .thenReturn(Completable.error(failure));

    port.requestChatHistoryBefore("server", "alice", 1234L, 40).test().assertError(failure);
    verify(irc).requestChatHistoryBefore("server", "alice", cutoff, 40);
  }
}

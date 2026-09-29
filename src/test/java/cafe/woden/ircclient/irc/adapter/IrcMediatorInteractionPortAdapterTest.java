package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IrcMediatorInteractionPortAdapterTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void adapterDelegatesMediatorInteractions(boolean nativePort) {
    IrcClientService irc = nativePort ? mock(NativeClient.class) : mock(IrcClientService.class);
    ServerIrcEvent event =
        new ServerIrcEvent(
            "libera", new IrcEvent.Connecting(Instant.EPOCH, "irc.libera.chat", 6697, "alice"));
    when(irc.events()).thenReturn(Flowable.just(event));
    when(irc.whois("libera", "alice")).thenReturn(Completable.complete());
    when(irc.whowas("libera", "alice", 5)).thenReturn(Completable.complete());
    when(irc.sendPrivateMessage("libera", "alice", "hi")).thenReturn(Completable.complete());
    when(irc.sendRaw("libera", "MODE #ircafe +o alice")).thenReturn(Completable.complete());
    when(irc.setIrcv3CapabilityEnabled("libera", "message-tags", true))
        .thenReturn(Completable.complete());
    when(irc.joinChannel("libera", "#ircafe")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(Optional.of("alice"));

    IrcMediatorInteractionPort port = new IrcMediatorInteractionPortAdapter(irc);

    port.events().test().assertResult(event);
    port.whois("libera", "alice").blockingAwait();
    port.whowas("libera", "alice", 5).blockingAwait();
    port.sendPrivateMessage("libera", "alice", "hi").blockingAwait();
    port.sendRaw("libera", "MODE #ircafe +o alice").blockingAwait();
    port.setIrcv3CapabilityEnabled("libera", "message-tags", true).blockingAwait();
    port.joinChannel("libera", "#ircafe").blockingAwait();
    assertTrue(port.currentNick("libera").isPresent());
    assertEquals("alice", port.currentNick("libera").orElse(""));
  }

  @Test
  void rejectsMissingClient() {
    assertThrows(NullPointerException.class, () -> new IrcMediatorInteractionPortAdapter(null));
  }

  @Test
  void legacyNicknameLookupTrimsServerAndSkipsBlankIds() {
    IrcClientService irc = mock(IrcClientService.class);
    IrcMediatorInteractionPort port = new IrcMediatorInteractionPortAdapter(irc);
    assertTrue(port.currentNick(null).isEmpty());
    assertTrue(port.currentNick("").isEmpty());
    assertTrue(port.currentNick("  ").isEmpty());
    verifyNoInteractions(irc);
    when(irc.currentNick("libera")).thenReturn(Optional.of("alice"));
    assertEquals(Optional.of("alice"), port.currentNick(" libera "));
    verify(irc).currentNick("libera");
    verifyNoMoreInteractions(irc);
  }

  @Test
  void nativeNicknameLookupPreservesServerId() {
    NativeClient irc = mock(NativeClient.class);
    IrcMediatorInteractionPort port = new IrcMediatorInteractionPortAdapter(irc);
    for (String serverId : new String[] {null, "", "  ", " libera "}) {
      when(irc.currentNick(serverId)).thenReturn(Optional.of("native"));
      assertEquals(Optional.of("native"), port.currentNick(serverId));
      verify(irc).currentNick(serverId);
    }
    verifyNoMoreInteractions(irc);
  }

  @Test
  void eventsRemainLazyAndCancellationReachesClient() {
    IrcClientService irc = mock(IrcClientService.class);
    var events = PublishProcessor.<ServerIrcEvent>create();
    when(irc.events()).thenReturn(events);
    Flowable<ServerIrcEvent> stream = new IrcMediatorInteractionPortAdapter(irc).events();
    assertFalse(events.hasSubscribers());
    var subscriber = stream.test();
    assertTrue(events.hasSubscribers());
    RuntimeException failure = new RuntimeException("disconnected");
    events.onError(failure);
    subscriber.assertError(failure);
    var second = PublishProcessor.<ServerIrcEvent>create();
    when(irc.events()).thenReturn(second);
    var cancelled = new IrcMediatorInteractionPortAdapter(irc).events().test();
    assertTrue(second.hasSubscribers());
    cancelled.cancel();
    assertFalse(second.hasSubscribers());
  }

  private interface NativeClient extends IrcClientService, IrcMediatorInteractionPort {
    @Override
    Flowable<ServerIrcEvent> events();

    @Override
    Completable whois(String serverId, String nick);

    @Override
    Completable whowas(String serverId, String nick, int count);

    @Override
    Completable sendPrivateMessage(String serverId, String target, String message);

    @Override
    Completable sendRaw(String serverId, String line);

    @Override
    Completable setIrcv3CapabilityEnabled(String serverId, String capability, boolean value);

    @Override
    Completable joinChannel(String serverId, String channel);

    @Override
    Optional<String> currentNick(String serverId);
  }
}

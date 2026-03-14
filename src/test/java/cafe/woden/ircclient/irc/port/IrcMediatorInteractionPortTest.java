package cafe.woden.ircclient.irc.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IrcMediatorInteractionPortTest {

  @Test
  void fromIrcClientDelegatesMediatorInteractions() {
    IrcClientService irc = mock(IrcClientService.class);
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
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("alice"));

    IrcMediatorInteractionPort port = IrcMediatorInteractionPort.from(irc);

    assertEquals(1, port.events().test().assertComplete().values().size());
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
  void fromNullIsSafeNoop() {
    IrcMediatorInteractionPort port = IrcMediatorInteractionPort.from(null);

    port.events().test().assertComplete().assertNoValues();
    port.whois("libera", "alice").blockingAwait();
    port.whowas("libera", "alice", 5).blockingAwait();
    port.sendPrivateMessage("libera", "alice", "hi").blockingAwait();
    port.sendRaw("libera", "MODE #ircafe +o alice").blockingAwait();
    port.setIrcv3CapabilityEnabled("libera", "message-tags", true).blockingAwait();
    port.joinChannel("libera", "#ircafe").blockingAwait();
    assertTrue(port.currentNick("libera").isEmpty());
  }
}

package cafe.woden.ircclient.irc.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.Test;

class IrcTargetMembershipPortTest {

  @Test
  void fromIrcClientDelegatesTargetMembershipOperations() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.joinChannel("libera", "#ircafe")).thenReturn(Completable.complete());
    when(irc.partChannel("libera", "#ircafe")).thenReturn(Completable.complete());
    when(irc.partChannel("libera", "#ircafe", "bye")).thenReturn(Completable.complete());
    when(irc.requestNames("libera", "#ircafe")).thenReturn(Completable.complete());
    when(irc.sendRaw("libera", "DETACH #ircafe")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("alice"));

    IrcTargetMembershipPort port = IrcTargetMembershipPort.from(irc);

    port.joinChannel("libera", "#ircafe").blockingAwait();
    port.partChannel("libera", "#ircafe").blockingAwait();
    port.partChannel("libera", "#ircafe", "bye").blockingAwait();
    port.requestNames("libera", "#ircafe").blockingAwait();
    port.sendRaw("libera", "DETACH #ircafe").blockingAwait();
    assertTrue(port.currentNick("libera").isPresent());
    assertEquals("alice", port.currentNick("libera").orElse(""));
  }

  @Test
  void fromNullIsSafeNoop() {
    IrcTargetMembershipPort port = IrcTargetMembershipPort.from(null);

    port.joinChannel("libera", "#ircafe").blockingAwait();
    port.partChannel("libera", "#ircafe").blockingAwait();
    port.partChannel("libera", "#ircafe", "bye").blockingAwait();
    port.requestNames("libera", "#ircafe").blockingAwait();
    port.sendRaw("libera", "DETACH #ircafe").blockingAwait();
    assertTrue(port.currentNick("libera").isEmpty());
  }
}

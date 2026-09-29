package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcTypingPort;
import io.reactivex.rxjava3.core.Completable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IrcTypingPortAdapterTest {

  @Test
  void adapterDelegatesTypingChecks() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.isTypingAvailable("libera")).thenReturn(true);

    IrcTypingPort port = new IrcTypingPortAdapter(irc);

    assertTrue(port.isTypingAvailable("libera"));
  }

  @Test
  void nullClientReportsTypingUnavailable() {
    IrcTypingPort port = new IrcTypingPortAdapter(null);

    assertFalse(port.isTypingAvailable("libera"));
    assertEquals("", port.typingAvailabilityReason("libera"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> port.sendTyping("libera", "#ircafe", "active").blockingAwait());
  }

  @Test
  void sendPreservesArgumentsLazinessErrorsAndCancellation() {
    IrcClientService irc = mock(IrcClientService.class);
    AtomicInteger subscriptions = new AtomicInteger();
    RuntimeException failure = new RuntimeException("send failed");
    Completable send =
        Completable.defer(
            () -> {
              subscriptions.incrementAndGet();
              return Completable.error(failure);
            });
    when(irc.sendTyping(" libera ", "#ircafe", "active")).thenReturn(send);
    when(irc.typingAvailabilityReason(" libera ")).thenReturn("not negotiated");
    IrcTypingPort port = new IrcTypingPortAdapter(irc);
    assertFalse(port.isTypingAvailable(" libera "));
    assertEquals("not negotiated", port.typingAvailabilityReason(" libera "));
    Completable result = port.sendTyping(" libera ", "#ircafe", "active");
    assertSame(send, result);
    assertEquals(0, subscriptions.get());
    result.test().assertError(failure);
    assertEquals(1, subscriptions.get());
    AtomicInteger disposals = new AtomicInteger();
    when(irc.sendTyping("libera", "alice", "done"))
        .thenReturn(Completable.never().doOnDispose(disposals::incrementAndGet));
    var observer = port.sendTyping("libera", "alice", "done").test();
    assertEquals(0, disposals.get());
    observer.dispose();
    assertEquals(1, disposals.get());
  }
}

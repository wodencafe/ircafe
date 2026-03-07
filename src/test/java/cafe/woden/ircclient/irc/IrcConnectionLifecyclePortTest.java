package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.Test;

class IrcConnectionLifecyclePortTest {

  @Test
  void fromIrcClientDelegatesLifecycleOperations() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.connect("libera")).thenReturn(Completable.complete());
    when(irc.disconnect("libera")).thenReturn(Completable.complete());
    when(irc.disconnect("libera", "bye")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("alice"));

    IrcConnectionLifecyclePort port = IrcConnectionLifecyclePort.from(irc);

    port.connect("libera").blockingAwait();
    port.disconnect("libera").blockingAwait();
    port.disconnect("libera", "bye").blockingAwait();
    assertTrue(port.currentNick("libera").isPresent());
    assertEquals("alice", port.currentNick("libera").orElse(""));
  }
}

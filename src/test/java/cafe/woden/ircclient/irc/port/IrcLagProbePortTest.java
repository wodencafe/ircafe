package cafe.woden.ircclient.irc.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.core.Completable;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class IrcLagProbePortTest {

  @Test
  void fromIrcClientDelegatesLagProbeOperations() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("alice"));
    when(irc.requestLagProbe("libera")).thenReturn(Completable.complete());
    when(irc.lastMeasuredLagMs("libera")).thenReturn(OptionalLong.of(123L));

    IrcLagProbePort port = IrcLagProbePort.from(irc);

    assertEquals("alice", port.currentNick("libera").orElse(""));
    port.requestLagProbe("libera").blockingAwait();
    assertTrue(port.lastMeasuredLagMs("libera").isPresent());
    assertEquals(123L, port.lastMeasuredLagMs("libera").orElse(-1L));
  }

  @Test
  void fromNullIsSafeNoop() {
    IrcLagProbePort port = IrcLagProbePort.from(null);

    assertTrue(port.currentNick("libera").isEmpty());
    port.requestLagProbe("libera").blockingAwait();
    assertTrue(port.lastMeasuredLagMs("libera").isEmpty());
  }
}

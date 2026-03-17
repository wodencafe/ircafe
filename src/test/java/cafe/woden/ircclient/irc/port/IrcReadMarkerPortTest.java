package cafe.woden.ircclient.irc.port;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IrcReadMarkerPortTest {

  @Test
  void fromIrcClientDelegatesReadMarkerChecks() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);

    IrcReadMarkerPort port = IrcReadMarkerPort.from(irc);

    assertTrue(port.isReadMarkerAvailable("libera"));
  }

  @Test
  void fromNullIsSafeNoop() {
    IrcReadMarkerPort port = IrcReadMarkerPort.from(null);

    assertFalse(port.isReadMarkerAvailable("libera"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> port.sendReadMarker("libera", "#ircafe", Instant.now()).blockingAwait());
  }
}

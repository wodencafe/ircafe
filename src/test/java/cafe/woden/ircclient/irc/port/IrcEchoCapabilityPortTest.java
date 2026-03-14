package cafe.woden.ircclient.irc.port;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import org.junit.jupiter.api.Test;

class IrcEchoCapabilityPortTest {

  @Test
  void fromIrcClientDelegatesEchoAvailability() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);

    IrcEchoCapabilityPort port = IrcEchoCapabilityPort.from(irc);

    assertTrue(port.isEchoMessageAvailable("libera"));
  }

  @Test
  void fromNullIsSafeNoop() {
    IrcEchoCapabilityPort port = IrcEchoCapabilityPort.from(null);

    assertFalse(port.isEchoMessageAvailable("libera"));
  }
}

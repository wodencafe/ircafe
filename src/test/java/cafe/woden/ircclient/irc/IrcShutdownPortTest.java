package cafe.woden.ircclient.irc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class IrcShutdownPortTest {

  @Test
  void fromIrcClientDelegatesShutdownNow() {
    IrcClientService irc = mock(IrcClientService.class);
    IrcShutdownPort port = IrcShutdownPort.from(irc);

    port.shutdownNow();

    verify(irc).shutdownNow();
  }

  @Test
  void fromNullIsSafeNoop() {
    IrcShutdownPort port = IrcShutdownPort.from(null);
    port.shutdownNow();
  }
}

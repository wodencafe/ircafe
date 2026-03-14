package cafe.woden.ircclient.irc.port;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import org.junit.jupiter.api.Test;

class IrcTypingPortTest {

  @Test
  void fromIrcClientDelegatesTypingChecks() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.isTypingAvailable("libera")).thenReturn(true);

    IrcTypingPort port = IrcTypingPort.from(irc);

    assertTrue(port.isTypingAvailable("libera"));
  }

  @Test
  void fromNullIsSafeNoop() {
    IrcTypingPort port = IrcTypingPort.from(null);

    assertFalse(port.isTypingAvailable("libera"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> port.sendTyping("libera", "#ircafe", "active").blockingAwait());
  }
}

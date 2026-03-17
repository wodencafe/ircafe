package cafe.woden.ircclient.irc.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import org.junit.jupiter.api.Test;

class IrcCurrentNickPortTest {

  @Test
  void fromIrcClientDelegatesCurrentNickLookup() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("alice"));

    IrcCurrentNickPort port = IrcCurrentNickPort.from(irc);

    assertTrue(port.currentNick("libera").isPresent());
    assertEquals("alice", port.currentNick("libera").orElse(""));
  }

  @Test
  void fromNullIsSafeNoop() {
    IrcCurrentNickPort port = IrcCurrentNickPort.from(null);

    assertTrue(port.currentNick("libera").isEmpty());
  }
}

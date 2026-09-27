package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcCurrentNickPort;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IrcCurrentNickPortAdapterTest {

  @Test
  void delegatesCurrentNickLookup() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("alice"));

    IrcCurrentNickPort port = new IrcCurrentNickPortAdapter(irc);

    assertTrue(port.currentNick("libera").isPresent());
    assertEquals("alice", port.currentNick("libera").orElse(""));
  }

  @Test
  void nullClientIsSafeNoop() {
    IrcCurrentNickPort port = new IrcCurrentNickPortAdapter(null);

    assertTrue(port.currentNick("libera").isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankLegacyServerIdDoesNotReachTheClient(String serverId) {
    IrcClientService irc = mock(IrcClientService.class);
    assertTrue(new IrcCurrentNickPortAdapter(irc).currentNick(serverId).isEmpty());
    verifyNoInteractions(irc);
  }

  @Test
  void legacyNicknameLookupTrimsServerId() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.currentNick("libera")).thenReturn(Optional.of("alice"));
    assertEquals("alice", new IrcCurrentNickPortAdapter(irc).currentNick(" libera ").orElseThrow());
    verify(irc).currentNick("libera");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", " libera "})
  void nativePortOwnsServerIdNormalization(String serverId) {
    NativeClient irc = mock(NativeClient.class);
    when(irc.currentNick(serverId)).thenReturn(Optional.of("native"));
    assertEquals("native", new IrcCurrentNickPortAdapter(irc).currentNick(serverId).orElseThrow());
    verify(irc).currentNick(serverId);
  }

  private interface NativeClient extends IrcClientService, IrcCurrentNickPort {
    @Override
    Optional<String> currentNick(String serverId);
  }
}

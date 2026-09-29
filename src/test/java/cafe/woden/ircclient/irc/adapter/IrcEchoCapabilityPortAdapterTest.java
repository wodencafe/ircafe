package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcEchoCapabilityPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IrcEchoCapabilityPortAdapterTest {

  @Test
  void delegatesEchoAvailability() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);

    IrcEchoCapabilityPort port = new IrcEchoCapabilityPortAdapter(irc);

    assertTrue(port.isEchoMessageAvailable("libera"));
  }

  @Test
  void nullClientRetainsUnsupportedDefaults() {
    IrcEchoCapabilityPort port = new IrcEchoCapabilityPortAdapter(null);

    assertFalse(port.isEchoMessageAvailable("libera"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"libera", " libera "})
  void availabilityRemainsLiveAndPreservesServerId(String serverId) {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.isEchoMessageAvailable(serverId)).thenReturn(true, false);
    IrcEchoCapabilityPort port = new IrcEchoCapabilityPortAdapter(irc);

    assertTrue(port.isEchoMessageAvailable(serverId));
    assertFalse(port.isEchoMessageAvailable(serverId));
    verify(irc, times(2)).isEchoMessageAvailable(serverId);
  }

  @Test
  void nativePortIsUsedForAvailability() {
    NativeClient irc = mock(NativeClient.class);
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);
    assertTrue(new IrcEchoCapabilityPortAdapter(irc).isEchoMessageAvailable("libera"));
  }

  private interface NativeClient extends IrcClientService, IrcEchoCapabilityPort {
    @Override
    boolean isEchoMessageAvailable(String serverId);
  }
}

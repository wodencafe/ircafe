package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class IrcNegotiatedFeaturePortTest {

  @Test
  void fromDelegatesCapabilityQueriesToIrcClientService() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);
    when(irc.isMonitorAvailable("libera")).thenReturn(true);

    IrcNegotiatedFeaturePort port = IrcNegotiatedFeaturePort.from(irc);

    assertTrue(port.isDraftReplyAvailable("libera"));
    assertTrue(port.isReadMarkerAvailable("libera"));
    assertTrue(port.isMonitorAvailable("libera"));
  }

  @Test
  void fromNullReturnsNoopPort() {
    IrcNegotiatedFeaturePort port = IrcNegotiatedFeaturePort.from(null);

    assertFalse(port.isDraftReplyAvailable("libera"));
    assertFalse(port.isReadMarkerAvailable("libera"));
    assertFalse(port.isMonitorAvailable("libera"));
  }
}

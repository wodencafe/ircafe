package cafe.woden.ircclient.irc.port;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import org.junit.jupiter.api.Test;

class IrcNegotiatedFeaturePortTest {

  @Test
  void fromDelegatesCapabilityQueriesToIrcClientService() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.isMessageTagsAvailable("libera")).thenReturn(true);
    when(irc.isDraftReplyAvailable("libera")).thenReturn(true);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);
    when(irc.isMonitorAvailable("libera")).thenReturn(true);

    IrcNegotiatedFeaturePort port = IrcNegotiatedFeaturePort.from(irc);

    assertTrue(port.isMessageTagsAvailable("libera"));
    assertTrue(port.isDraftReplyAvailable("libera"));
    assertTrue(port.isReadMarkerAvailable("libera"));
    assertTrue(port.isMonitorAvailable("libera"));
  }

  @Test
  void fromNullReturnsNoopPort() {
    IrcNegotiatedFeaturePort port = IrcNegotiatedFeaturePort.from(null);

    assertFalse(port.isMessageTagsAvailable("libera"));
    assertFalse(port.isDraftReplyAvailable("libera"));
    assertFalse(port.isReadMarkerAvailable("libera"));
    assertFalse(port.isMonitorAvailable("libera"));
  }
}

package cafe.woden.ircclient.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import org.junit.jupiter.api.Test;

class Ircv3MultilineFeatureSupportTest {

  @Test
  void reportsUnavailableWhenMultilineIsNotNegotiated() {
    OutboundBackendCapabilityPolicy backend = mock(OutboundBackendCapabilityPolicy.class);
    IrcNegotiatedFeaturePort irc = mock(IrcNegotiatedFeaturePort.class);
    when(backend.featureUnavailableMessage("libera", "")).thenReturn("");
    when(backend.supportsMultiline("libera")).thenReturn(false);

    Ircv3MultilineFeatureSupport support = new Ircv3MultilineFeatureSupport(backend, irc);

    assertFalse(support.isAvailable("libera"));
    assertEquals(
        "IRCv3 multiline is not negotiated on this server.",
        support.unavailableOrLimitReason("libera", 2, 17L));
  }

  @Test
  void reportsNegotiatedMaxLinesAndBytesLimits() {
    OutboundBackendCapabilityPolicy backend = mock(OutboundBackendCapabilityPolicy.class);
    IrcNegotiatedFeaturePort irc = mock(IrcNegotiatedFeaturePort.class);
    when(backend.featureUnavailableMessage("libera", "")).thenReturn("");
    when(backend.supportsMultiline("libera")).thenReturn(true);
    when(irc.negotiatedMultilineMaxLines("libera")).thenReturn(1);
    when(irc.negotiatedMultilineMaxBytes("libera")).thenReturn(5L);

    Ircv3MultilineFeatureSupport support = new Ircv3MultilineFeatureSupport(backend, irc);

    assertEquals(
        "Message has 2 lines; negotiated multiline max-lines is 1.",
        support.unavailableOrLimitReason("libera", 2, 4L));
    assertEquals(
        "Message is 11 UTF-8 bytes; negotiated multiline max-bytes is 5.",
        support.unavailableOrLimitReason("libera", 1, 11L));
  }

  @Test
  void usesBackendAvailabilityReasonWhenPresent() {
    OutboundBackendCapabilityPolicy backend = mock(OutboundBackendCapabilityPolicy.class);
    IrcNegotiatedFeaturePort irc = mock(IrcNegotiatedFeaturePort.class);
    when(backend.featureUnavailableMessage("quassel", ""))
        .thenReturn("Quassel Core backend is not implemented yet.");

    Ircv3MultilineFeatureSupport support = new Ircv3MultilineFeatureSupport(backend, irc);

    assertEquals(
        "Quassel Core backend is not implemented yet.",
        support.unavailableOrLimitReason("quassel", 2, 17L));
  }

  @Test
  void reportsAvailableWhenNegotiatedAndWithinLimits() {
    OutboundBackendCapabilityPolicy backend = mock(OutboundBackendCapabilityPolicy.class);
    IrcNegotiatedFeaturePort irc = mock(IrcNegotiatedFeaturePort.class);
    when(backend.featureUnavailableMessage("libera", "")).thenReturn("");
    when(backend.supportsMultiline("libera")).thenReturn(true);
    when(irc.negotiatedMultilineMaxLines("libera")).thenReturn(5);
    when(irc.negotiatedMultilineMaxBytes("libera")).thenReturn(4096L);

    Ircv3MultilineFeatureSupport support = new Ircv3MultilineFeatureSupport(backend, irc);

    assertTrue(support.isAvailable("libera"));
    assertEquals("", support.unavailableOrLimitReason("libera", 2, 17L));
  }
}

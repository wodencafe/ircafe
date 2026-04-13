package cafe.woden.ircclient.app.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import org.junit.jupiter.api.Test;

class Ircv3ChatHistoryFeatureSupportTest {

  @Test
  void usesNegotiatedChatHistoryAvailability() {
    IrcNegotiatedFeaturePort irc = mock(IrcNegotiatedFeaturePort.class);
    OutboundBackendCapabilityPolicy backendCapabilityPolicy = backendCapabilityPolicy();
    when(irc.isChatHistoryAvailable("libera")).thenReturn(true);

    Ircv3ChatHistoryFeatureSupport support =
        new Ircv3ChatHistoryFeatureSupport(
            backendCapabilityPolicy, irc, mock(IrcBouncerPlaybackPort.class));

    assertTrue(support.isAvailable("libera"));
    assertTrue(support.isRemoteHistoryAvailable("libera"));
    assertFalse(support.isAvailable(" "));
  }

  @Test
  void remoteHistoryAvailabilityFallsBackToZncPlayback() {
    IrcNegotiatedFeaturePort irc = mock(IrcNegotiatedFeaturePort.class);
    IrcBouncerPlaybackPort playback = mock(IrcBouncerPlaybackPort.class);
    OutboundBackendCapabilityPolicy backendCapabilityPolicy = backendCapabilityPolicy();
    when(irc.isChatHistoryAvailable("znc")).thenReturn(false);
    when(playback.isZncPlaybackAvailable("znc")).thenReturn(true);

    Ircv3ChatHistoryFeatureSupport support =
        new Ircv3ChatHistoryFeatureSupport(backendCapabilityPolicy, irc, playback);

    assertFalse(support.isAvailable("znc"));
    assertTrue(support.isZncPlaybackAvailable("znc"));
    assertTrue(support.isRemoteHistoryAvailable("znc"));
  }

  private static OutboundBackendCapabilityPolicy backendCapabilityPolicy() {
    OutboundBackendCapabilityPolicy policy = mock(OutboundBackendCapabilityPolicy.class);
    when(policy.featureUnavailableMessage(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(policy.unavailableReasonForHelp(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    return policy;
  }
}

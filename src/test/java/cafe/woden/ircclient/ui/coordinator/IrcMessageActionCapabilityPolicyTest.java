package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.Ircv3ChatHistoryFeatureSupport;
import cafe.woden.ircclient.app.api.Ircv3MessageRedactionFeatureSupport;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import org.junit.jupiter.api.Test;

class IrcMessageActionCapabilityPolicyTest {

  @Test
  void delegatesCapabilitiesPerServer() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    when(irc.isMessageTagsAvailable("matrix")).thenReturn(true);
    when(irc.isExperimentalMessageEditAvailable("matrix")).thenReturn(true);
    when(irc.isMessageRedactionAvailable("matrix")).thenReturn(true);
    when(irc.isChatHistoryAvailable("matrix")).thenReturn(true);
    when(irc.isZncPlaybackAvailable("matrix")).thenReturn(false);

    when(irc.isMessageTagsAvailable("irc")).thenReturn(false);
    when(irc.isExperimentalMessageEditAvailable("irc")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("irc")).thenReturn(false);
    when(irc.isChatHistoryAvailable("irc")).thenReturn(false);
    when(irc.isZncPlaybackAvailable("irc")).thenReturn(true);

    IrcMessageActionCapabilityPolicy policy = newPolicy(irc);

    assertTrue(policy.canReply("matrix"));
    assertTrue(policy.canReact("matrix"));
    assertTrue(policy.canUnreact("matrix"));
    assertTrue(policy.canEdit("matrix"));
    assertTrue(policy.canRedact("matrix"));
    assertTrue(policy.canLoadAroundMessage("matrix"));
    assertTrue(policy.canLoadNewerHistory("matrix"));

    assertFalse(policy.canReply("irc"));
    assertFalse(policy.canReact("irc"));
    assertFalse(policy.canUnreact("irc"));
    assertFalse(policy.canEdit("irc"));
    assertFalse(policy.canRedact("irc"));
    assertFalse(policy.canLoadAroundMessage("irc"));
    assertTrue(policy.canLoadNewerHistory("irc"));
  }

  @Test
  void returnsFalseWhenBackendCapabilityLookupThrows() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    when(irc.isMessageTagsAvailable("broken")).thenThrow(new RuntimeException("boom"));
    when(irc.isChatHistoryAvailable("broken")).thenThrow(new RuntimeException("boom"));
    when(irc.isZncPlaybackAvailable("broken")).thenThrow(new RuntimeException("boom"));

    IrcMessageActionCapabilityPolicy policy = newPolicy(irc);

    assertFalse(policy.canReply("broken"));
    assertFalse(policy.canLoadAroundMessage("broken"));
    assertFalse(policy.canLoadNewerHistory("broken"));
  }

  @Test
  void nullIrcServiceDisablesAllCapabilities() {
    Ircv3ChatHistoryFeatureSupport chatHistoryFeatureSupport =
        mock(Ircv3ChatHistoryFeatureSupport.class);
    Ircv3MessageRedactionFeatureSupport messageRedactionFeatureSupport =
        mock(Ircv3MessageRedactionFeatureSupport.class);
    IrcMessageActionCapabilityPolicy policy =
        new IrcMessageActionCapabilityPolicy(
            null, chatHistoryFeatureSupport, messageRedactionFeatureSupport);

    assertFalse(policy.canReply("any"));
    assertFalse(policy.canReact("any"));
    assertFalse(policy.canUnreact("any"));
    assertFalse(policy.canEdit("any"));
    assertFalse(policy.canRedact("any"));
    assertFalse(policy.canLoadAroundMessage("any"));
    assertFalse(policy.canLoadNewerHistory("any"));
  }

  private static IrcMessageActionCapabilityPolicy newPolicy(IrcBackendClientService irc) {
    IrcNegotiatedFeaturePort negotiatedFeaturePort = IrcNegotiatedFeaturePort.from(irc);
    Ircv3ChatHistoryFeatureSupport chatHistoryFeatureSupport =
        new Ircv3ChatHistoryFeatureSupport(chatHistoryPolicy(irc), negotiatedFeaturePort, irc);
    Ircv3MessageRedactionFeatureSupport messageRedactionFeatureSupport =
        new Ircv3MessageRedactionFeatureSupport(messageRedactionPolicy(irc));
    return new IrcMessageActionCapabilityPolicy(
        negotiatedFeaturePort, chatHistoryFeatureSupport, messageRedactionFeatureSupport);
  }

  private static OutboundBackendCapabilityPolicy chatHistoryPolicy(IrcBackendClientService irc) {
    OutboundBackendCapabilityPolicy policy = mock(OutboundBackendCapabilityPolicy.class);
    when(policy.featureUnavailableMessage(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(policy.unavailableReasonForHelp(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    return policy;
  }

  private static OutboundBackendCapabilityPolicy messageRedactionPolicy(
      IrcBackendClientService irc) {
    OutboundBackendCapabilityPolicy policy = mock(OutboundBackendCapabilityPolicy.class);
    when(policy.supportsMessageRedaction(anyString()))
        .thenAnswer(invocation -> irc.isMessageRedactionAvailable(invocation.getArgument(0)));
    return policy;
  }
}

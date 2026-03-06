package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcBackendClientService;
import org.junit.jupiter.api.Test;

class IrcMessageActionCapabilityPolicyTest {

  @Test
  void delegatesCapabilitiesPerServer() {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    when(irc.isDraftReplyAvailable("matrix")).thenReturn(true);
    when(irc.isDraftReactAvailable("matrix")).thenReturn(true);
    when(irc.isDraftUnreactAvailable("matrix")).thenReturn(true);
    when(irc.isMessageEditAvailable("matrix")).thenReturn(true);
    when(irc.isMessageRedactionAvailable("matrix")).thenReturn(true);
    when(irc.isChatHistoryAvailable("matrix")).thenReturn(true);
    when(irc.isZncPlaybackAvailable("matrix")).thenReturn(false);

    when(irc.isDraftReplyAvailable("irc")).thenReturn(false);
    when(irc.isDraftReactAvailable("irc")).thenReturn(false);
    when(irc.isDraftUnreactAvailable("irc")).thenReturn(false);
    when(irc.isMessageEditAvailable("irc")).thenReturn(false);
    when(irc.isMessageRedactionAvailable("irc")).thenReturn(false);
    when(irc.isChatHistoryAvailable("irc")).thenReturn(false);
    when(irc.isZncPlaybackAvailable("irc")).thenReturn(true);

    IrcMessageActionCapabilityPolicy policy = new IrcMessageActionCapabilityPolicy(irc, irc);

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
    when(irc.isDraftReplyAvailable("broken")).thenThrow(new RuntimeException("boom"));
    when(irc.isChatHistoryAvailable("broken")).thenThrow(new RuntimeException("boom"));
    when(irc.isZncPlaybackAvailable("broken")).thenThrow(new RuntimeException("boom"));

    IrcMessageActionCapabilityPolicy policy = new IrcMessageActionCapabilityPolicy(irc, irc);

    assertFalse(policy.canReply("broken"));
    assertFalse(policy.canLoadAroundMessage("broken"));
    assertFalse(policy.canLoadNewerHistory("broken"));
  }

  @Test
  void nullIrcServiceDisablesAllCapabilities() {
    IrcMessageActionCapabilityPolicy policy = new IrcMessageActionCapabilityPolicy(null, null);

    assertFalse(policy.canReply("any"));
    assertFalse(policy.canReact("any"));
    assertFalse(policy.canUnreact("any"));
    assertFalse(policy.canEdit("any"));
    assertFalse(policy.canRedact("any"));
    assertFalse(policy.canLoadAroundMessage("any"));
    assertFalse(policy.canLoadNewerHistory("any"));
  }
}

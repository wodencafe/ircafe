package cafe.woden.ircclient.ui.servertree.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.TargetRef;
import org.junit.jupiter.api.Test;

class ServerTreeTargetNodePolicyTest {

  @Test
  void leafLabelStripsNetworkQualifierForRegularTargets() {
    ServerTreeTargetNodePolicy policy = new ServerTreeTargetNodePolicy(null);

    assertEquals("#ircafe", policy.leafLabel(new TargetRef("quassel", "#ircafe{net:libera}")));
  }

  @Test
  void leafLabelKeepsBuiltInLabelForQualifiedChannelListTargets() {
    ServerTreeTargetNodePolicy policy = new ServerTreeTargetNodePolicy(null);

    assertEquals("Channel List", policy.leafLabel(TargetRef.channelList("quassel", "libera")));
  }

  @Test
  void leafLabelKeepsBuiltInLabelForQualifiedMemoServTargets() {
    ServerTreeTargetNodePolicy policy = new ServerTreeTargetNodePolicy(null);

    assertEquals("MemoServ", policy.leafLabel(TargetRef.memoServ("quassel", "libera")));
  }

  @Test
  void leafLabelUsesScopedStoreKeyForQualifiedInterceptorTargets() {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    when(interceptorStore.interceptorName("quassel{net:libera}", "audit")).thenReturn("Audit Rule");

    ServerTreeTargetNodePolicy policy = new ServerTreeTargetNodePolicy(interceptorStore);

    assertEquals(
        "Audit Rule", policy.leafLabel(TargetRef.interceptor("quassel", "audit", "libera")));
  }

  @Test
  void leafLabelSimplifiesMatrixAddressTargets() {
    ServerTreeTargetNodePolicy policy = new ServerTreeTargetNodePolicy(null);

    assertEquals(
        "#woden",
        policy.leafLabel(new TargetRef("matrix", "#irc_libera_#woden:matrix.example.org")));
    assertEquals(
        "##ircafe",
        policy.leafLabel(new TargetRef("matrix", "#libera_##ircafe:matrix.example.org")));
    assertEquals(
        "@zimmedon", policy.leafLabel(new TargetRef("matrix", "@zimmedon:matrix.example.org")));
    assertEquals(
        "!abc123", policy.leafLabel(new TargetRef("matrix", "!abc123:matrix.example.org")));
  }

  @Test
  void dccChatTargetUsesDistinctLabelAndIsNotPersistedAsPrivateMessage() {
    ServerTreeTargetNodePolicy policy = new ServerTreeTargetNodePolicy(null);
    TargetRef chat = TargetRef.dccChat("libera", "Alice");

    assertEquals("DCC: Alice", policy.leafLabel(chat));
    assertEquals(false, policy.isPrivateMessageTarget(chat));
  }
}

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
    ServerTreeTargetNodePolicy policy =
        new ServerTreeTargetNodePolicy(
            null,
            "Notifications",
            "Interceptor",
            "Log Viewer",
            "Channel List",
            "Filters",
            "Ignores",
            "DCC Transfers");

    assertEquals("#ircafe", policy.leafLabel(new TargetRef("quassel", "#ircafe{net:libera}")));
  }

  @Test
  void leafLabelKeepsBuiltInLabelForQualifiedChannelListTargets() {
    ServerTreeTargetNodePolicy policy =
        new ServerTreeTargetNodePolicy(
            null,
            "Notifications",
            "Interceptor",
            "Log Viewer",
            "Channel List",
            "Filters",
            "Ignores",
            "DCC Transfers");

    assertEquals("Channel List", policy.leafLabel(TargetRef.channelList("quassel", "libera")));
  }

  @Test
  void leafLabelUsesScopedStoreKeyForQualifiedInterceptorTargets() {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    when(interceptorStore.interceptorName("quassel{net:libera}", "audit")).thenReturn("Audit Rule");

    ServerTreeTargetNodePolicy policy =
        new ServerTreeTargetNodePolicy(
            interceptorStore,
            "Notifications",
            "Interceptor",
            "Log Viewer",
            "Channel List",
            "Filters",
            "Ignores",
            "DCC Transfers");

    assertEquals(
        "Audit Rule", policy.leafLabel(TargetRef.interceptor("quassel", "audit", "libera")));
  }

  @Test
  void leafLabelSimplifiesMatrixAddressTargets() {
    ServerTreeTargetNodePolicy policy =
        new ServerTreeTargetNodePolicy(
            null,
            "Notifications",
            "Interceptor",
            "Log Viewer",
            "Channel List",
            "Filters",
            "Ignores",
            "DCC Transfers");

    assertEquals(
        "#woden",
        policy.leafLabel(new TargetRef("matrix", "#irc_libera_#woden:matrix.example.org")));
    assertEquals(
        "@zimmedon", policy.leafLabel(new TargetRef("matrix", "@zimmedon:matrix.example.org")));
    assertEquals(
        "!abc123", policy.leafLabel(new TargetRef("matrix", "!abc123:matrix.example.org")));
  }
}

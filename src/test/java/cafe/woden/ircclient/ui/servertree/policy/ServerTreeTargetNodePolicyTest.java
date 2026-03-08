package cafe.woden.ircclient.ui.servertree.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

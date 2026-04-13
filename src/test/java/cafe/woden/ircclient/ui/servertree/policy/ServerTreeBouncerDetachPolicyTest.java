package cafe.woden.ircclient.ui.servertree.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServerTreeBouncerDetachPolicyTest {

  @Test
  void supportsDetachForEphemeralBackendWhenConnected() {
    ServerTreeBouncerDetachPolicy policy = new ServerTreeBouncerDetachPolicy();
    ServerTreeBouncerDetachPolicy.Context context =
        ServerTreeBouncerDetachPolicy.context(
            Map.of(),
            __ -> ConnectionState.CONNECTED,
            __ -> ServerTreeBouncerBackends.GENERIC,
            (serverId, capability) -> false);

    assertTrue(policy.supportsBouncerDetach(context, "bouncer:origin:network"));
  }

  @Test
  void supportsDetachForBackendControlServerWhenConnected() {
    ServerTreeBouncerDetachPolicy policy = new ServerTreeBouncerDetachPolicy();
    ServerTreeBouncerDetachPolicy.Context context =
        ServerTreeBouncerDetachPolicy.context(
            Map.of(ServerTreeBouncerBackends.GENERIC, Set.of("origin")),
            __ -> ConnectionState.CONNECTED,
            __ -> null,
            (serverId, capability) -> false);

    assertTrue(policy.supportsBouncerDetach(context, "origin"));
  }

  @Test
  void fallsBackToCapabilityChecks() {
    ServerTreeBouncerDetachPolicy policy = new ServerTreeBouncerDetachPolicy();
    ServerTreeBouncerDetachPolicy.Context context =
        ServerTreeBouncerDetachPolicy.context(
            Map.of(),
            __ -> ConnectionState.CONNECTED,
            __ -> null,
            (serverId, capability) -> "znc.in/playback".equals(capability));

    assertTrue(policy.supportsBouncerDetach(context, "libera"));
  }

  @Test
  void refusesDetachWhenDisconnected() {
    ServerTreeBouncerDetachPolicy policy = new ServerTreeBouncerDetachPolicy();
    ServerTreeBouncerDetachPolicy.Context context =
        ServerTreeBouncerDetachPolicy.context(
            Map.of(ServerTreeBouncerBackends.SOJU, Set.of("origin")),
            __ -> ConnectionState.DISCONNECTED,
            __ -> ServerTreeBouncerBackends.SOJU,
            (serverId, capability) -> true);

    assertFalse(policy.supportsBouncerDetach(context, "origin"));
  }
}

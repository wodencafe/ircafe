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
    ServerTreeBouncerDetachPolicy policy =
        new ServerTreeBouncerDetachPolicy(
            Map.of(),
            ServerTreeBouncerDetachPolicy.context(
                __ -> ConnectionState.CONNECTED,
                __ -> ServerTreeBouncerBackends.GENERIC,
                (serverId, capability) -> false));

    assertTrue(policy.supportsBouncerDetach("bouncer:origin:network"));
  }

  @Test
  void supportsDetachForBackendControlServerWhenConnected() {
    ServerTreeBouncerDetachPolicy policy =
        new ServerTreeBouncerDetachPolicy(
            Map.of(ServerTreeBouncerBackends.GENERIC, Set.of("origin")),
            ServerTreeBouncerDetachPolicy.context(
                __ -> ConnectionState.CONNECTED, __ -> null, (serverId, capability) -> false));

    assertTrue(policy.supportsBouncerDetach("origin"));
  }

  @Test
  void fallsBackToCapabilityChecks() {
    ServerTreeBouncerDetachPolicy policy =
        new ServerTreeBouncerDetachPolicy(
            Map.of(),
            ServerTreeBouncerDetachPolicy.context(
                __ -> ConnectionState.CONNECTED,
                __ -> null,
                (serverId, capability) -> "znc.in/playback".equals(capability)));

    assertTrue(policy.supportsBouncerDetach("libera"));
  }

  @Test
  void refusesDetachWhenDisconnected() {
    ServerTreeBouncerDetachPolicy policy =
        new ServerTreeBouncerDetachPolicy(
            Map.of(ServerTreeBouncerBackends.SOJU, Set.of("origin")),
            ServerTreeBouncerDetachPolicy.context(
                __ -> ConnectionState.DISCONNECTED,
                __ -> ServerTreeBouncerBackends.SOJU,
                (serverId, capability) -> true));

    assertFalse(policy.supportsBouncerDetach("origin"));
  }
}
